package kcemu;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.Executors;

import org.keycloak.common.util.MimeTypeUtil;
import org.keycloak.theme.Theme;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import jakarta.ws.rs.core.Response;
import kcemu.Checks.Finding;
import kcemu.env.Emulator;
import kcemu.env.Emulator.Env;

/**
 * Lokaler Webserver: Oberfläche, gerenderte Seiten/Mails, Theme-Ressourcen wie Keycloaks
 * /resources/{version}/{typ}/{theme}/… sowie Klick-Navigation innerhalb der Vorschau.
 */
public class Server {

    private final Emulator emu;
    private final File scenarioFile;
    private final HttpServer http;

    /** zuletzt angezeigte Seite – für Sprachumschalter und Links in der Vorschau */
    private volatile String lastPage = "login";
    private volatile String lastVariant = "standard";
    private volatile String lastTheme;

    public Server(Emulator emu, File scenarioFile, String host, int port) throws IOException {
        this.emu = emu;
        this.scenarioFile = scenarioFile;
        this.http = HttpServer.create(new InetSocketAddress(host, port), 0);
        this.http.setExecutor(Executors.newFixedThreadPool(8));
        this.http.createContext("/", this::handle);
    }

    public void start() {
        http.start();
    }

    private void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        try {
            if (path.equals("/") || path.equals("/index.html")) {
                classpath(ex, "ui/index.html");
            } else if (path.startsWith("/ui/")) {
                classpath(ex, path.substring(1));
            } else if (path.equals("/vendor/axe.min.js")) {
                classpath(ex, "META-INF/resources/webjars/axe-core/4.11.3/axe.min.js");
            } else if (path.equals("/api/catalog")) {
                json(ex, 200, catalog());
            } else if (path.equals("/api/stamp")) {
                json(ex, 200, Map.of("stamp", stamp(emu.themesDir)));
            } else if (path.equals("/api/scenario")) {
                scenario(ex);
            } else if (path.equals("/api/themes") || path.startsWith("/api/themes/")) {
                themes(ex, path.substring("/api/themes".length()));
            } else if (path.startsWith("/api/page/")) {
                json(ex, 200, pageReport(path.substring("/api/page/".length()), query(ex)));
            } else if (path.startsWith("/api/mail/")) {
                json(ex, 200, mailReport(path.substring("/api/mail/".length()), query(ex)));
            } else if (path.startsWith("/api/theme/")) {
                json(ex, 200, themeReport(path.substring("/api/theme/".length()), query(ex)));
            } else if (path.startsWith("/render/page/")) {
                renderPage(ex, path.substring("/render/page/".length()), query(ex));
            } else if (path.startsWith("/render/mail/")) {
                renderMail(ex, path.substring("/render/mail/".length()), query(ex));
            } else if (path.startsWith("/resources/")) {
                resource(ex, path.substring("/resources/".length()));
            } else if (path.startsWith("/realms/")) {
                navigate(ex, path, query(ex));
            } else {
                text(ex, 404, "text/plain", "Nicht gefunden: " + path);
            }
        } catch (Throwable t) {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            text(ex, 500, "text/plain", "Interner Fehler des Emulators:\n" + sw);
        } finally {
            ex.close();
        }
    }

    // ---------------------------------------------------------------- Katalog & Szenario

    private Map<String, Object> catalog() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("keycloakVersion", Checks.version());
        m.put("appVersion", Checks.appVersion());
        m.put("themesDir", emu.themesDir.getAbsolutePath());
        List<Map<String, Object>> pages = new ArrayList<>();
        for (LoginPages.Page p : LoginPages.PAGES.values()) {
            Map<String, Object> pm = new LinkedHashMap<>();
            pm.put("id", p.id);
            pm.put("label", p.label);
            pm.put("group", p.group);
            pm.put("template", p.template);
            pm.put("trigger", p.trigger);
            List<Map<String, String>> vs = new ArrayList<>();
            for (LoginPages.Variant v : p.variants) {
                vs.add(Map.of("id", v.id, "label", v.label));
            }
            pm.put("variants", vs);
            pages.add(pm);
        }
        m.put("pages", pages);
        List<Map<String, String>> mails = new ArrayList<>();
        for (Emails.Mail ml : Emails.MAILS.values()) {
            mails.add(Map.of("id", ml.id, "label", ml.label, "template", ml.template, "trigger", ml.trigger));
        }
        m.put("mails", mails);
        Env env = emu.newEnv(new Emulator.Options());
        Map<String, Object> themes = new LinkedHashMap<>();
        themes.put("login", new TreeSet<>(env.session.theme().nameSet(Theme.Type.LOGIN)));
        themes.put("email", new TreeSet<>(env.session.theme().nameSet(Theme.Type.EMAIL)));
        m.put("themes", themes);
        m.put("scenario", emu.scenario());
        return m;
    }

    private void scenario(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        if ("GET".equals(method)) {
            json(ex, 200, emu.scenario());
            return;
        }
        if ("PUT".equals(method) || "POST".equals(method)) {
            byte[] body = ex.getRequestBody().readAllBytes();
            Scenario s;
            try {
                s = Scenario.JSON.readValue(body, Scenario.class);
            } catch (IOException e) {
                json(ex, 400, Map.of("error", "Ungültiges JSON: " + e.getMessage()));
                return;
            }
            emu.setScenario(s);
            boolean save = "1".equals(query(ex).get("save"));
            if (save && scenarioFile != null) {
                Scenario.JSON.writeValue(scenarioFile, s);
            }
            json(ex, 200, Map.of("ok", true, "saved", save && scenarioFile != null));
            return;
        }
        text(ex, 405, "text/plain", "Methode nicht erlaubt");
    }

    // ---------------------------------------------------------------- Theme-Verwaltung

    /**
     * GET  /api/themes                    eigene Themes im Theme-Ordner
     * POST /api/themes/import[?overwrite=1] ZIP/JAR oder Ordner importieren
     * POST /api/themes/select             {"login": "...", "email": "...", "save": true}
     * POST /api/themes/{name}/open        Ordner im Finder/Explorer öffnen
     * DELETE /api/themes/{name}           Theme entfernen
     */
    private void themes(HttpExchange ex, String rest) throws IOException {
        ThemeLibrary lib = new ThemeLibrary(emu.themesDir);
        String method = ex.getRequestMethod();
        if (rest.isEmpty() && "GET".equals(method)) {
            json(ex, 200, Map.of("themesDir", emu.themesDir.getAbsolutePath(), "themes", lib.list()));
            return;
        }
        if (rest.equals("/import") && "POST".equals(method)) {
            com.fasterxml.jackson.databind.JsonNode req = Scenario.JSON.readTree(ex.getRequestBody().readAllBytes());
            ThemeLibrary.ImportResult r;
            try {
                r = lib.importFrom(req, "1".equals(query(ex).get("overwrite")));
            } catch (IllegalArgumentException | IOException e) {
                json(ex, 400, Map.of("error", String.valueOf(e.getMessage())));
                return;
            }
            if (!r.existing.isEmpty()) {
                json(ex, 409, Map.of("existing", r.existing));
                return;
            }
            json(ex, 200, Map.of("imported", r.imported, "warnings", r.warnings));
            return;
        }
        if (rest.equals("/select") && "POST".equals(method)) {
            com.fasterxml.jackson.databind.JsonNode req = Scenario.JSON.readTree(ex.getRequestBody().readAllBytes());
            Scenario s = emu.scenario().copy();
            if (req.hasNonNull("login")) {
                s.loginTheme = req.get("login").asText();
            }
            if (req.hasNonNull("email")) {
                s.emailTheme = req.get("email").asText();
            }
            emu.setScenario(s);
            boolean saved = false;
            if (req.path("save").asBoolean(false) && scenarioFile != null) {
                Scenario.JSON.writeValue(scenarioFile, s);
                saved = true;
            }
            json(ex, 200, Map.of("loginTheme", s.loginTheme, "emailTheme", s.emailTheme, "saved", saved));
            return;
        }
        String name = rest.startsWith("/") ? rest.substring(1) : rest;
        if (name.endsWith("/open") && "POST".equals(method)) {
            File dir = lib.dirOf(name.substring(0, name.length() - "/open".length()));
            boolean ok = dir != null && dir.isDirectory() && !java.awt.GraphicsEnvironment.isHeadless();
            if (ok) {
                DesktopApp.openFolder(dir.toPath());
            }
            json(ex, ok ? 200 : 501, Map.of("ok", ok, "path", dir == null ? "" : dir.getAbsolutePath()));
            return;
        }
        if ("DELETE".equals(method) && !name.isEmpty() && !name.contains("/")) {
            boolean ok = lib.delete(name);
            json(ex, ok ? 200 : 404, Map.of("ok", ok));
            return;
        }
        text(ex, 404, "text/plain", "Nicht gefunden");
    }

    // ---------------------------------------------------------------- Seiten

    private static final class PageResult {
        Response response;
        Env env;
        Throwable error;
        LoginPages.Page page;
        LoginPages.Variant variant;
    }

    private PageResult runPage(String id, Map<String, String> q) {
        PageResult r = new PageResult();
        r.page = LoginPages.PAGES.get(id);
        if (r.page == null) {
            return r;
        }
        r.variant = r.page.variant(q.getOrDefault("variant", "standard"));
        List<Env> envs = new ArrayList<>();
        try {
            r.response = LoginPages.render(emu, r.page, r.variant, blank(q.get("locale")), blank(q.get("theme")), envs);
        } catch (Throwable t) {
            r.error = t;
        }
        r.env = envs.isEmpty() ? null : envs.get(0);
        return r;
    }

    private void renderPage(HttpExchange ex, String id, Map<String, String> q) throws IOException {
        PageResult r = runPage(id, q);
        if (r.page == null) {
            text(ex, 404, "text/plain", "Unbekannte Seite: " + id);
            return;
        }
        lastPage = r.page.id;
        lastVariant = r.variant.id;
        lastTheme = blank(q.get("theme"));
        sendPage(ex, r);
    }

    private void sendPage(HttpExchange ex, PageResult r) throws IOException {
        if (r.error != null) {
            text(ex, 500, "text/html; charset=utf-8", emulatorNotice("Fehler im Emulator",
                    "<pre>" + esc(stack(r.error)) + "</pre>"));
            return;
        }
        Object entity = r.response.getEntity();
        if (entity == null) {
            // Keycloak liefert bei Template-Fehlern eine leere 500-Antwort (Response.serverError())
            String detail = r.env == null ? "" : r.env.freeMarker.runs.stream()
                    .filter(x -> x.error != null).map(x -> String.valueOf(x.error.getMessage())).findFirst().orElse("");
            text(ex, r.response.getStatus(), "text/html; charset=utf-8", emulatorNotice(
                    "Keycloak würde hier eine leere Seite mit Status " + r.response.getStatus() + " ausliefern",
                    "<p>Ursache (aus dem Server-Log):</p><pre>" + esc(detail) + "</pre>"));
            return;
        }
        text(ex, r.response.getStatus(), "text/html; charset=utf-8", entity.toString());
    }

    Map<String, Object> pageReport(String id, Map<String, String> q) {
        PageResult r = runPage(id, q);
        Map<String, Object> m = new LinkedHashMap<>();
        if (r.page == null) {
            m.put("error", "Unbekannte Seite: " + id);
            return m;
        }
        List<Finding> findings = new ArrayList<>();
        m.put("page", r.page.id);
        m.put("variant", r.variant.id);
        m.put("template", r.page.template);
        m.put("trigger", r.page.trigger);
        if (r.error != null) {
            findings.add(Checks.err("Emulator", "Die Variante ließ sich nicht erzeugen", stack(r.error)));
        }
        if (r.response != null) {
            m.put("status", r.response.getStatus());
            Object lang = r.response.getLanguage();
            m.put("locale", lang == null ? null : lang.toString());
        }
        if (r.env != null) {
            String theme = r.env.sc.loginTheme;
            m.put("theme", theme);
            Checks.themeUsed(r.env, Theme.Type.LOGIN, theme, findings);
            Checks.templateErrors(r.env, findings);
            Locale loc = r.env.context.resolveLocale(r.env.user);
            Checks.messages(r.env, Theme.Type.LOGIN, loc, referenceMissing(id, q, true), findings);
            Checks.resources(r.env, Theme.Type.LOGIN, findings);
            for (String w : r.env.warnings) {
                findings.add(Checks.warn("Szenario", w, null));
            }
            m.put("chain", Checks.chain(emu, theme, Theme.Type.LOGIN, new ArrayList<>()));
            m.put("freemarker", r.env.freeMarker.summary());
        }
        m.put("findings", findings);
        return m;
    }

    // ---------------------------------------------------------------- Mails

    Map<String, Object> mailReport(String id, Map<String, String> q) {
        Map<String, Object> m = new LinkedHashMap<>();
        Emails.Mail mail = Emails.MAILS.get(id);
        if (mail == null) {
            m.put("error", "Unbekannte Mail: " + id);
            return m;
        }
        Emails.Result r = Emails.render(emu, mail, blank(q.get("locale")), blank(q.get("theme")));
        List<Finding> findings = new ArrayList<>();
        m.put("mail", mail.id);
        m.put("template", mail.template);
        m.put("trigger", mail.trigger);
        String theme = r.env.sc.emailTheme;
        m.put("theme", theme);
        if (r.error != null) {
            findings.add(Checks.err("Template", "Mail konnte nicht erzeugt werden (Keycloak: „Failed to template email“)",
                    rootMessage(r.error)));
        }
        Checks.themeUsed(r.env, Theme.Type.EMAIL, theme, findings);
        Checks.templateErrors(r.env, findings);
        Locale loc = r.env.context.resolveLocale(r.env.user);
        m.put("locale", loc.toLanguageTag());
        Checks.messages(r.env, Theme.Type.EMAIL, loc, referenceMissing(id, q, false), findings);
        if (r.mail != null) {
            m.put("to", r.mail.to);
            m.put("from", r.mail.smtpConfig == null ? null : r.mail.smtpConfig.get("from"));
            m.put("subject", r.mail.subject);
            m.put("textBody", r.mail.textBody);
            m.put("htmlSize", r.mail.htmlBody == null ? 0 : r.mail.htmlBody.getBytes(StandardCharsets.UTF_8).length);
            Object link = null;
            for (var run : r.env.freeMarker.runs) {
                if (run.dataModel.contains("link")) {
                    link = "link";
                }
            }
            Checks.email(r.mail, link == null ? null : expectedLink(r), findings);
        }
        m.put("chain", Checks.chain(emu, theme, Theme.Type.EMAIL, new ArrayList<>()));
        m.put("freemarker", r.env.freeMarker.summary());
        m.put("findings", findings);
        return m;
    }

    /** Textschlüssel, die auch beim mitgelieferten Theme (keycloak.v2 / keycloak) fehlen würden. */
    private java.util.Set<String> referenceMissing(String id, Map<String, String> q, boolean page) {
        Map<String, String> rq = new LinkedHashMap<>(q);
        rq.put("theme", page ? "keycloak.v2" : "keycloak");
        java.util.Set<String> keys = new java.util.HashSet<>();
        Env env;
        if (page) {
            env = runPage(id, rq).env;
        } else {
            env = Emails.render(emu, Emails.MAILS.get(id), blank(rq.get("locale")), rq.get("theme")).env;
        }
        if (env != null) {
            env.freeMarker.runs.forEach(run -> keys.addAll(run.missingKeys));
        }
        return keys;
    }

    /** Der Link, den Emails.link(..) für diese Mail gebaut hat. */
    private static String expectedLink(Emails.Result r) {
        return Emails.withSlash(r.env.uriInfo.getBaseUri().toString()) + "realms/" + r.env.sc.realm.name
                + "/login-actions/action-token?key=eyJhbGciOiJIUzUxMiJ9.emulator-token&client_id="
                + r.env.sc.client.clientId + "&tab_id=" + Emulator.TAB_ID;
    }

    private void renderMail(HttpExchange ex, String id, Map<String, String> q) throws IOException {
        Emails.Mail mail = Emails.MAILS.get(id);
        if (mail == null) {
            text(ex, 404, "text/plain", "Unbekannte Mail: " + id);
            return;
        }
        Emails.Result r = Emails.render(emu, mail, blank(q.get("locale")), blank(q.get("theme")));
        if (r.mail == null) {
            text(ex, 500, "text/html; charset=utf-8", emulatorNotice("Keycloak würde diese Mail nicht versenden",
                    "<pre>" + esc(r.error == null ? "" : rootMessage(r.error)) + "</pre>"));
            return;
        }
        if ("text".equals(q.get("part"))) {
            text(ex, 200, "text/plain; charset=utf-8", r.mail.textBody == null ? "" : r.mail.textBody);
        } else {
            text(ex, 200, "text/html; charset=utf-8", r.mail.htmlBody == null ? "" : r.mail.htmlBody);
        }
    }

    // ---------------------------------------------------------------- Theme-Übersicht

    private Map<String, Object> themeReport(String type, Map<String, String> q) {
        Theme.Type t = "email".equals(type) ? Theme.Type.EMAIL : Theme.Type.LOGIN;
        String name = blank(q.get("theme"));
        if (name == null) {
            name = t == Theme.Type.EMAIL ? emu.scenario().emailTheme : emu.scenario().loginTheme;
        }
        List<Finding> findings = new ArrayList<>();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("theme", name);
        m.put("chain", Checks.chain(emu, name, t, findings));
        m.put("findings", findings);
        return m;
    }

    // ---------------------------------------------------------------- Ressourcen

    /** Wie ThemeResource: /resources/{version}/{themeType}/{themeName}/{path} */
    private void resource(HttpExchange ex, String rest) throws IOException {
        String[] parts = rest.split("/", 4);
        if (parts.length < 4) {
            text(ex, 404, "text/plain", "");
            return;
        }
        Theme.Type type;
        try {
            type = Theme.Type.valueOf(parts[1].toUpperCase());
        } catch (IllegalArgumentException e) {
            text(ex, 404, "text/plain", "");
            return;
        }
        Env env = emu.newEnv(new Emulator.Options());
        Theme theme = env.session.theme().getTheme(parts[2], type);
        String p = URLDecoder.decode(parts[3], StandardCharsets.UTF_8);
        try (InputStream in = theme == null ? null : theme.getResourceAsStream(p)) {
            if (in == null) {
                text(ex, 404, "text/plain", "Ressource nicht gefunden: " + p);
                return;
            }
            byte[] data = in.readAllBytes();
            String ct = MimeTypeUtil.getContentType(p);
            ex.getResponseHeaders().set("Content-Type", ct == null ? "application/octet-stream" : ct);
            ex.getResponseHeaders().set("Cache-Control", "no-store");
            ex.sendResponseHeaders(200, data.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(data);
            }
        }
    }

    // ---------------------------------------------------------------- Navigation in der Vorschau

    /**
     * Links und Formulare der gerenderten Seiten zeigen auf echte Keycloak-Adressen
     * (/realms/{realm}/…). Bekannte Ziele werden auf die passende Seite abgebildet.
     */
    private void navigate(HttpExchange ex, String path, Map<String, String> q) throws IOException {
        String locale = blank(q.get("kc_locale"));
        if ("POST".equals(ex.getRequestMethod())) {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            StringBuilder rows = new StringBuilder();
            for (Map.Entry<String, String> e : parse(body).entrySet()) {
                String v = e.getKey().toLowerCase().contains("password") ? "••••••" : e.getValue();
                rows.append("<tr><th>").append(esc(e.getKey())).append("</th><td>").append(esc(v)).append("</td></tr>");
            }
            text(ex, 200, "text/html; charset=utf-8", emulatorNotice("Formular abgeschickt",
                    "<p>Ziel: <code>" + esc(path) + "</code></p><table>" + rows + "</table>"
                            + "<p>Im echten Server verarbeitet hier der Authenticator die Eingaben.</p>"
                            + "<p><a href=\"javascript:history.back()\">Zurück</a></p>"));
            return;
        }
        String target = null;
        String variant = "standard";
        if (path.endsWith("/registrations") || path.endsWith("/login-actions/registration")) {
            target = "register";
        } else if (path.contains("/login-actions/reset-credentials")) {
            target = "login-reset-password";
        } else if (path.endsWith("/protocol/openid-connect/auth") || path.endsWith("/login-actions/restart")) {
            target = "login";
        } else if (path.contains("/broker/")) {
            text(ex, 200, "text/html; charset=utf-8", emulatorNotice("Weiterleitung zum Identity Provider",
                    "<p>Keycloak würde jetzt zu <code>" + esc(path) + "</code> weiterleiten.</p>"
                            + "<p><a href=\"javascript:history.back()\">Zurück</a></p>"));
            return;
        }
        if (target == null) {
            // z. B. Sprachumschalter (…?kc_locale=xx) oder „Zurück zur Anmeldung“: aktuelle Seite erneut
            target = lastPage;
            variant = lastVariant;
        }
        Map<String, String> nq = new LinkedHashMap<>();
        nq.put("variant", variant);
        if (locale != null) {
            nq.put("locale", locale);
        }
        if (lastTheme != null) {
            nq.put("theme", lastTheme);
        }
        lastPage = target;
        lastVariant = variant;
        sendPage(ex, runPage(target, nq));
    }

    // ---------------------------------------------------------------- Hilfsfunktionen

    /** Jüngster Änderungszeitpunkt im Theme-Ordner – die Oberfläche lädt bei Änderungen neu. */
    private static long stamp(File dir) {
        long max = dir.lastModified();
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                max = Math.max(max, f.isDirectory() ? stamp(f) : f.lastModified());
            }
        }
        return max;
    }

    private static String emulatorNotice(String title, String body) {
        return "<!DOCTYPE html><html lang=\"de\"><head><meta charset=\"utf-8\"><title>" + esc(title) + "</title>"
                + "<style>body{font:15px/1.5 system-ui,sans-serif;margin:2rem;color:#1d1d1f;background:#fffbe6}"
                + ".tag{display:inline-block;background:#8a6100;color:#fff;border-radius:4px;padding:2px 8px;font-size:12px}"
                + "pre{white-space:pre-wrap;background:#fff;border:1px solid #e0c97a;padding:1rem;font-size:12px}"
                + "th{text-align:left;padding-right:1rem}</style></head><body>"
                + "<span class=\"tag\">Hinweis des Emulators – keine Keycloak-Ausgabe</span><h1>" + esc(title) + "</h1>"
                + body + "</body></html>";
    }

    private void classpath(HttpExchange ex, String res) throws IOException {
        if (res.contains("..")) {
            text(ex, 404, "text/plain", "");
            return;
        }
        // Während der Entwicklung direkt aus src/main/resources lesen (Änderungen ohne Neustart)
        File dev = new File("src/main/resources/" + res);
        byte[] data;
        if (dev.isFile()) {
            data = Files.readAllBytes(dev.toPath());
        } else {
            try (InputStream in = Server.class.getClassLoader().getResourceAsStream(res)) {
                if (in == null) {
                    text(ex, 404, "text/plain", "Nicht gefunden: " + res);
                    return;
                }
                data = in.readAllBytes();
            }
        }
        String ct = res.endsWith(".html") ? "text/html; charset=utf-8"
                : res.endsWith(".js") ? "text/javascript; charset=utf-8"
                : res.endsWith(".css") ? "text/css; charset=utf-8" : "application/octet-stream";
        ex.getResponseHeaders().set("Content-Type", ct);
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(200, data.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(data);
        }
    }

    private static void json(HttpExchange ex, int status, Object o) throws IOException {
        text(ex, status, "application/json; charset=utf-8", Scenario.JSON.writeValueAsString(o));
    }

    private static void text(HttpExchange ex, int status, String type, String body) throws IOException {
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", type);
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(status, data.length == 0 ? -1 : data.length);
        if (data.length > 0) {
            try (OutputStream os = ex.getResponseBody()) {
                os.write(data);
            }
        }
    }

    private static Map<String, String> query(HttpExchange ex) {
        URI u = ex.getRequestURI();
        return parse(u.getRawQuery());
    }

    private static Map<String, String> parse(String raw) {
        Map<String, String> m = new LinkedHashMap<>();
        if (raw == null || raw.isEmpty()) {
            return m;
        }
        for (String kv : raw.split("&")) {
            int i = kv.indexOf('=');
            String k = URLDecoder.decode(i < 0 ? kv : kv.substring(0, i), StandardCharsets.UTF_8);
            String v = i < 0 ? "" : URLDecoder.decode(kv.substring(i + 1), StandardCharsets.UTF_8);
            m.putIfAbsent(k, v);
        }
        return m;
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String stack(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    private static String rootMessage(Throwable t) {
        StringBuilder sb = new StringBuilder();
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (sb.length() > 0) {
                sb.append("\n→ ");
            }
            sb.append(c.getClass().getSimpleName()).append(": ").append(c.getMessage());
            if (c.getCause() == c) {
                break;
            }
        }
        return sb.toString();
    }
}
