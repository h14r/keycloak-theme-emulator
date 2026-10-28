package kcemu;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.keycloak.theme.FolderThemeProvider;
import org.keycloak.theme.Theme;
import org.keycloak.theme.ThemeProvider;

import kcemu.env.Emulator;
import kcemu.env.Emulator.Env;
import kcemu.env.InstrumentedFreeMarker;

/**
 * Prüfungen für die Bewertung eines Themes. Jede Meldung sagt, ob sie ein harter Befund
 * (so verhält sich Keycloak tatsächlich) oder eine Heuristik (Erfahrungsregel) ist.
 */
public final class Checks {

    private Checks() {
    }

    public static final class Finding {
        public final String level;
        public final String area;
        public final String message;
        public final String detail;
        public final boolean heuristic;

        Finding(String level, String area, String message, String detail, boolean heuristic) {
            this.level = level;
            this.area = area;
            this.message = message;
            this.detail = detail;
            this.heuristic = heuristic;
        }
    }

    static Finding err(String area, String msg, String detail) {
        return new Finding("error", area, msg, detail, false);
    }

    static Finding warn(String area, String msg, String detail) {
        return new Finding("warning", area, msg, detail, false);
    }

    static Finding hint(String area, String msg, String detail) {
        return new Finding("info", area, msg, detail, true);
    }

    static Finding heuristicWarn(String area, String msg, String detail) {
        return new Finding("warning", area, msg, detail, true);
    }

    // ------------------------------------------------------------------ Theme-Aufbau

    /** Ein Glied der Theme-Kette, so wie DefaultThemeManager sie aufbaut. */
    public static final class ChainEntry {
        public String name;
        public String type;
        public String source;
        public String relation;
        public List<String> overriddenFiles = new ArrayList<>();
    }

    public static ThemeProvider[] providers(Emulator emu) {
        return new ThemeProvider[] { new FolderThemeProvider(emu.themesDir), emu.classpathThemes() };
    }

    static Theme find(Emulator emu, String name, Theme.Type type) throws IOException {
        for (ThemeProvider p : providers(emu)) {
            if (p.hasTheme(name, type)) {
                return p.getTheme(name, type);
            }
        }
        return null;
    }

    /**
     * Baut die Kette Theme → import → parent → … wie DefaultThemeManager#loadTheme nach
     * und meldet, was Keycloak an dieser Stelle tun würde.
     */
    public static List<ChainEntry> chain(Emulator emu, String name, Theme.Type type, List<Finding> out) {
        List<ChainEntry> list = new ArrayList<>();
        try {
            Theme t = find(emu, name, type);
            if (t == null) {
                out.add(err("Theme", "Theme „" + name + "“ (Typ " + type.name().toLowerCase() + ") nicht gefunden",
                        "Keycloak protokolliert „Failed to find " + type + " theme " + name + ", using built-in themes“ und zeigt das Standard-Theme."));
                return list;
            }
            String relation = "Theme";
            while (t != null) {
                list.add(entry(emu, t, relation));
                if (t.getImportName() != null) {
                    String[] s = t.getImportName().split("/");
                    Theme imp = s.length == 2 ? find(emu, s[1], Theme.Type.valueOf(s[0].toUpperCase())) : null;
                    if (imp == null) {
                        out.add(err("Theme", "import=" + t.getImportName() + " in „" + t.getName() + "“ nicht auflösbar",
                                "Keycloak kann das Theme dann nicht laden und fällt auf das Standard-Theme zurück."));
                    } else {
                        list.add(entry(emu, imp, "import von " + t.getName()));
                    }
                }
                String parent = t.getParentName();
                if (parent == null) {
                    break;
                }
                Theme p = find(emu, parent, type);
                if (p == null) {
                    out.add(err("Theme", "parent=" + parent + " in „" + t.getName() + "“ nicht gefunden",
                            "Keycloak: „Not found parent theme '" + parent + "' … Unable to load " + type.name().toLowerCase()
                                    + " theme '" + name + "'“ – es wird das Standard-Theme angezeigt."));
                    break;
                }
                relation = "parent von " + t.getName();
                t = p;
            }
        } catch (IOException e) {
            out.add(err("Theme", "Theme konnte nicht gelesen werden: " + e.getMessage(), null));
        }
        return list;
    }

    private static ChainEntry entry(Emulator emu, Theme t, String relation) {
        ChainEntry e = new ChainEntry();
        e.name = t.getName();
        e.type = t.getType().name().toLowerCase();
        e.relation = relation;
        File dir = new File(new File(emu.themesDir, t.getName()), e.type);
        if (dir.isDirectory() && new FolderThemeProvider(emu.themesDir).hasTheme(t.getName(), t.getType())) {
            e.source = "Ordner " + dir.getPath();
            collect(dir, dir, e.overriddenFiles);
        } else {
            e.source = "mitgeliefert (keycloak-themes " + version() + ")";
        }
        return e;
    }

    private static void collect(File root, File dir, List<String> out) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        java.util.Arrays.sort(files);
        for (File f : files) {
            if (f.isDirectory()) {
                collect(root, f, out);
            } else {
                out.add(root.toPath().relativize(f.toPath()).toString().replace('\\', '/'));
            }
        }
    }

    public static String version() {
        String v = org.keycloak.common.Version.VERSION;
        return v == null ? "?" : v;
    }

    /** Prüft styles/stylesCommon/scripts aus theme.properties gegen die tatsächlich auflösbaren Ressourcen. */
    public static void resources(Env env, Theme.Type type, List<Finding> out) {
        try {
            Theme theme = env.session.theme().getTheme(type);
            Properties p = theme.getProperties();
            for (String key : new String[] { "styles", "scripts" }) {
                for (String r : split(p.getProperty(key))) {
                    if (!exists(theme, r)) {
                        out.add(err("Ressourcen", key + "=" + r + " wird nicht gefunden",
                                "Der Browser erhält für ${url.resourcesPath}/" + r + " einen 404-Fehler."));
                    }
                }
            }
            String common = p.getProperty("common");
            if (common == null || common.isEmpty()) {
                common = "common/keycloak";
            }
            String[] c = common.split("/");
            Theme commonTheme = c.length == 2 ? env.session.theme().getTheme(c[1], Theme.Type.valueOf(c[0].toUpperCase())) : null;
            for (String key : new String[] { "stylesCommon", "scriptsCommon" }) {
                for (String r : split(p.getProperty(key))) {
                    if (commonTheme == null || !exists(commonTheme, r)) {
                        out.add(err("Ressourcen", key + "=" + r + " wird im Common-Theme „" + common + "“ nicht gefunden", null));
                    }
                }
            }
        } catch (IOException e) {
            out.add(err("Ressourcen", "Theme-Eigenschaften nicht lesbar: " + e.getMessage(), null));
        }
    }

    /** getResourceAsStream statt hasResource – gibt es in allen Keycloak-26-Versionen. */
    private static boolean exists(Theme theme, String path) throws IOException {
        try (java.io.InputStream in = theme.getResourceAsStream(path)) {
            return in != null;
        }
    }

    private static List<String> split(String s) {
        List<String> l = new ArrayList<>();
        if (s != null) {
            for (String x : s.trim().split("\\s+")) {
                if (!x.isBlank()) {
                    l.add(x);
                }
            }
        }
        return l;
    }

    /** Hat Keycloak das gewünschte Theme tatsächlich verwendet? */
    public static void themeUsed(Env env, Theme.Type type, String wanted, List<Finding> out) {
        try {
            Theme t = env.session.theme().getTheme(type);
            if (t != null && wanted != null && !wanted.equals(t.getName())) {
                out.add(err("Theme", "Keycloak verwendet „" + t.getName() + "“ statt „" + wanted + "“",
                        "Das gewünschte Theme ließ sich nicht laden (siehe Theme-Kette)."));
            }
        } catch (IOException e) {
            out.add(err("Theme", e.getMessage(), null));
        }
    }

    // ------------------------------------------------------------------ Texte

    /**
     * Fehlende Textschlüssel (Keycloak zeigt dann den Schlüssel selbst an) und mögliche
     * unübersetzte Texte (gleicher Wortlaut wie im englischen Bündel).
     */
    public static void messages(Env env, Theme.Type type, Locale locale, java.util.Set<String> referenceMissing, List<Finding> out) {
        Properties english = null;
        try {
            if (locale != null && !"en".equals(locale.getLanguage())) {
                english = env.session.theme().getTheme(type).getEnhancedMessages(env.realm, Locale.ENGLISH);
            }
        } catch (IOException | RuntimeException e) {
            // Vergleich nicht möglich – nur die harten Befunde ausgeben
        }
        Map<String, String> seen = new LinkedHashMap<>();
        for (InstrumentedFreeMarker.Run r : env.freeMarker.runs) {
            for (String k : r.missingKeys) {
                if (seen.put(k, r.template) == null) {
                    boolean upstream = referenceMissing != null && referenceMissing.contains(k);
                    out.add(new Finding(upstream ? "info" : "warning", "Texte",
                            "Textschlüssel „" + k + "“ fehlt – Keycloak zeigt den Schlüssel als Text an",
                            (upstream ? "Fehlt beim mitgelieferten Theme genauso (Verhalten von Keycloak selbst). " : "")
                                    + "Template: " + r.template + ". Abhilfe: Schlüssel in messages/messages_"
                                    + (locale == null ? "xx" : locale.toLanguageTag().replace('-', '_')) + ".properties ergänzen.",
                            false));
                }
            }
            if (english != null) {
                for (Map.Entry<String, String> u : r.usedTexts.entrySet()) {
                    String en = english.getProperty(u.getKey());
                    String txt = u.getValue();
                    if (en != null && en.equals(txt) && txt.replaceAll("[^\\p{L}]", "").length() > 3 && !txt.contains("${")) {
                        out.add(hint("Texte", "„" + u.getKey() + "“ ist möglicherweise nicht übersetzt",
                                "Gleicher Wortlaut wie im englischen Bündel: „" + abbreviate(txt, 80) + "“"));
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ Template-Fehler

    public static void templateErrors(Env env, List<Finding> out) {
        for (InstrumentedFreeMarker.Run r : env.freeMarker.runs) {
            if (r.error != null) {
                out.add(err("Template", "FreeMarker-Fehler in " + r.template,
                        String.valueOf(r.error.getMessage())));
            }
        }
    }

    // ------------------------------------------------------------------ E-Mail

    private static final Pattern IMG = Pattern.compile("<img\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern SRC = Pattern.compile("\\bsrc\\s*=\\s*[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern ALT = Pattern.compile("\\balt\\s*=", Pattern.CASE_INSENSITIVE);

    public static void email(Emulator.SentMail m, String link, List<Finding> out) {
        if (m.subject == null || m.subject.isBlank()) {
            out.add(err("Betreff", "Betreff ist leer", null));
        } else if (m.subject.length() > 78) {
            out.add(heuristicWarn("Betreff", "Betreff ist " + m.subject.length() + " Zeichen lang",
                    "Viele Postfächer kürzen ab etwa 60–78 Zeichen."));
        }
        if (m.textBody == null || m.textBody.isBlank()) {
            out.add(warn("Text-Teil", "Text-Teil ist leer",
                    "Keycloak verschickt multipart/alternative; Clients ohne HTML zeigen dann nichts an."));
        }
        if (m.htmlBody == null || m.htmlBody.isBlank()) {
            out.add(warn("HTML-Teil", "HTML-Teil ist leer", null));
            return;
        }
        String h = m.htmlBody;
        if (link != null) {
            if (!h.contains(link) && !h.contains(link.replace("&", "&amp;"))) {
                out.add(err("HTML-Teil", "Der Aktionslink fehlt im HTML-Teil", "Erwartet: " + link));
            }
            if (m.textBody != null && !m.textBody.contains(link)) {
                String masked = link.replace("&", "&amp;").replace("=", "&#61;");
                if (m.textBody.contains(masked) || m.textBody.contains(link.replace("&", "&amp;"))) {
                    out.add(err("Text-Teil", "Der Aktionslink ist im Text-Teil HTML-maskiert (&amp; / &#61;) und funktioniert dort nicht",
                            "Ursache ist meist kcSanitize(..) in einem text/*.ftl-Template. Erwartet: " + link));
                } else {
                    out.add(err("Text-Teil", "Der Aktionslink fehlt im Text-Teil", "Erwartet: " + link));
                }
            }
        }
        int bytes = h.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        if (bytes > 102 * 1024) {
            out.add(heuristicWarn("HTML-Teil", "HTML-Teil ist " + bytes / 1024 + " KB groß",
                    "Gmail kürzt Nachrichten über ca. 102 KB („Nachricht gekürzt“)."));
        }
        if (Pattern.compile("<link\\b[^>]*stylesheet", Pattern.CASE_INSENSITIVE).matcher(h).find()) {
            out.add(heuristicWarn("HTML-Teil", "Externe Stylesheets (<link rel=stylesheet>)",
                    "Werden von den meisten Mail-Clients (Gmail, Outlook) nicht geladen – Stile inline setzen."));
        }
        if (Pattern.compile("<script\\b", Pattern.CASE_INSENSITIVE).matcher(h).find()) {
            out.add(heuristicWarn("HTML-Teil", "<script> im HTML-Teil", "Wird von allen gängigen Mail-Clients entfernt."));
        }
        if (Pattern.compile("display\\s*:\\s*(flex|grid)|position\\s*:\\s*(absolute|fixed)", Pattern.CASE_INSENSITIVE).matcher(h).find()) {
            out.add(heuristicWarn("HTML-Teil", "CSS-Layout mit flex/grid/position",
                    "Outlook für Windows (Word-Engine) unterstützt das nicht – Tabellen-Layout verwenden."));
        }
        if (Pattern.compile("<style\\b", Pattern.CASE_INSENSITIVE).matcher(h).find()) {
            out.add(hint("HTML-Teil", "<style>-Block vorhanden",
                    "Wird nicht von allen Clients ausgewertet; wichtige Stile zusätzlich inline setzen."));
        }
        if (!Pattern.compile("<html[^>]*\\blang=", Pattern.CASE_INSENSITIVE).matcher(h).find()) {
            out.add(hint("HTML-Teil", "<html> ohne lang-Attribut", "Screenreader können die Sprache dann nicht erkennen."));
        }
        Matcher im = IMG.matcher(h);
        while (im.find()) {
            String tag = im.group();
            Matcher sm = SRC.matcher(tag);
            String src = sm.find() ? sm.group(1) : "";
            if (src.startsWith("data:")) {
                out.add(heuristicWarn("Bilder", "Bild als data:-URI eingebettet",
                        "Gmail und Outlook blockieren data:-Bilder häufig."));
            } else if (!(src.startsWith("https://") || src.startsWith("http://") || src.startsWith("cid:"))) {
                out.add(err("Bilder", "Bild mit relativer Adresse: " + abbreviate(src, 80),
                        "Im Postfach gibt es keine Basisadresse – absolute URL verwenden, z. B. ${url.resourcesUrl}/img/logo.png."));
            } else if (src.contains("://localhost") || src.contains("://127.0.0.1")) {
                out.add(hint("Bilder", "Bild verweist auf localhost: " + abbreviate(src, 80),
                        "Im Emulator normal; auf dem Server entspricht das der Frontend-URL von Keycloak – sie muss für Empfänger erreichbar sein."));
            }
            if (!ALT.matcher(tag).find()) {
                out.add(warn("Bilder", "Bild ohne alt-Text: " + abbreviate(src, 60), "Bei blockierten Bildern und für Screenreader nötig."));
            }
        }
    }

    static String abbreviate(String s, int n) {
        return s == null || s.length() <= n ? s : s.substring(0, n) + "…";
    }
}
