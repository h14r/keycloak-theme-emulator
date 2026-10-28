package kcemu;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Verwaltung der eigenen Themes im Theme-Ordner: auflisten, importieren (ZIP, JAR oder
 * Ordner), entfernen. Der Aufbau im Ordner entspricht /opt/keycloak/themes.
 */
final class ThemeLibrary {

    /** Theme-Typen, die Keycloak kennt (Unterordner eines Themes). */
    static final Set<String> TYPES = Set.of("login", "email", "account", "admin", "welcome", "common");

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");

    private final File themesDir;

    ThemeLibrary(File themesDir) {
        this.themesDir = themesDir;
    }

    /** Ein Theme im Theme-Ordner mit seinen Typen und dem jeweiligen parent. */
    List<Map<String, Object>> list() {
        List<Map<String, Object>> out = new ArrayList<>();
        File[] dirs = themesDir.listFiles(File::isDirectory);
        if (dirs == null) {
            return out;
        }
        java.util.Arrays.sort(dirs, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        for (File d : dirs) {
            Map<String, Object> types = new LinkedHashMap<>();
            for (String t : new TreeSet<>(TYPES)) {
                File td = new File(d, t);
                if (td.isDirectory()) {
                    Properties p = new Properties();
                    File tp = new File(td, "theme.properties");
                    if (tp.isFile()) {
                        try (var in = Files.newInputStream(tp.toPath())) {
                            p.load(in);
                        } catch (IOException e) {
                            // Eigenschaften nicht lesbar – Theme trotzdem anzeigen
                        }
                    }
                    Map<String, String> info = new LinkedHashMap<>();
                    info.put("parent", p.getProperty("parent"));
                    info.put("import", p.getProperty("import"));
                    types.put(t, info);
                }
            }
            if (types.isEmpty()) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", d.getName());
            m.put("types", types);
            m.put("path", d.getAbsolutePath());
            m.put("modified", lastModified(d));
            out.add(m);
        }
        return out;
    }

    private static long lastModified(File f) {
        long max = f.lastModified();
        File[] c = f.listFiles();
        if (c != null) {
            for (File x : c) {
                max = Math.max(max, x.isDirectory() ? lastModified(x) : x.lastModified());
            }
        }
        return max;
    }

    /** Ergebnis eines Imports. */
    static final class ImportResult {
        final List<Map<String, Object>> imported = new ArrayList<>();
        final List<String> existing = new ArrayList<>();
        final List<String> warnings = new ArrayList<>();
    }

    /**
     * Importiert Themes aus der Anfrage der Oberfläche:
     * {@code {"archiveName": "x.zip", "archive": "<base64>"}} oder
     * {@code {"folderName": "x", "files": [{"path": "x/login/theme.properties", "data": "<base64>"}]}}.
     */
    ImportResult importFrom(JsonNode req, boolean overwrite) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        String fallbackName;
        if (req.hasNonNull("archive")) {
            fallbackName = baseName(req.path("archiveName").asText("theme"));
            byte[] data = Base64.getDecoder().decode(req.get("archive").asText());
            try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(data))) {
                ZipEntry e;
                while ((e = zin.getNextEntry()) != null) {
                    if (!e.isDirectory()) {
                        entries.put(e.getName(), zin.readAllBytes());
                    }
                }
            }
            if (entries.isEmpty()) {
                throw new IllegalArgumentException("Die Datei ist kein lesbares ZIP- oder JAR-Archiv.");
            }
        } else {
            fallbackName = req.path("folderName").asText("theme");
            for (JsonNode f : req.path("files")) {
                entries.put(f.path("path").asText(), Base64.getDecoder().decode(f.path("data").asText()));
            }
        }
        return importEntries(clean(entries), fallbackName, overwrite);
    }

    /** Pfade vereinheitlichen, Systemdateien (macOS, Windows) und unsichere Pfade verwerfen. */
    private static Map<String, byte[]> clean(Map<String, byte[]> in) {
        Map<String, byte[]> out = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> e : in.entrySet()) {
            String p = e.getKey().replace('\\', '/');
            while (p.startsWith("/")) {
                p = p.substring(1);
            }
            if (p.isEmpty() || p.contains("../") || p.equals("..") || p.startsWith("__MACOSX/")
                    || p.endsWith("/.DS_Store") || p.equals(".DS_Store") || p.endsWith("Thumbs.db")) {
                continue;
            }
            out.put(p, e.getValue());
        }
        return out;
    }

    ImportResult importEntries(Map<String, byte[]> entries, String fallbackName, boolean overwrite) throws IOException {
        // Theme-Wurzel → Theme-Name
        Map<String, String> roots = new LinkedHashMap<>();
        byte[] descriptor = entries.get("META-INF/keycloak-themes.json");
        if (descriptor != null) {
            // Theme-JAR wie für /opt/keycloak/providers: theme/<name>/<typ>/…
            JsonNode d = Scenario.JSON.readTree(descriptor);
            for (JsonNode t : d.path("themes")) {
                String n = t.path("name").asText();
                roots.put("theme/" + n + "/", n);
            }
        } else {
            // Ordner/ZIP: eine Theme-Wurzel liegt dort, wo <typ>/theme.properties darunter liegt
            for (String p : entries.keySet()) {
                String[] seg = p.split("/");
                if (seg.length >= 2 && seg[seg.length - 1].equals("theme.properties") && TYPES.contains(seg[seg.length - 2])) {
                    int rootLen = seg.length - 2;
                    String root = rootLen == 0 ? "" : String.join("/", java.util.Arrays.copyOf(seg, rootLen)) + "/";
                    String name = rootLen == 0 ? fallbackName : seg[rootLen - 1];
                    roots.putIfAbsent(root, name);
                }
            }
        }
        ImportResult r = new ImportResult();
        if (roots.containsKey("") && TYPES.contains(fallbackName)) {
            throw new IllegalArgumentException("Bitte den Theme-Ordner selbst wählen (der login/ bzw. email/ enthält), "
                    + "nicht den Unterordner „" + fallbackName + "“.");
        }
        if (roots.isEmpty()) {
            throw new IllegalArgumentException("Kein Keycloak-Theme gefunden. Erwartet wird z. B. <name>/login/theme.properties "
                    + "oder ein Theme-JAR mit META-INF/keycloak-themes.json.");
        }
        // erst prüfen, dann schreiben – damit kein halber Import entsteht
        for (Map.Entry<String, String> root : roots.entrySet()) {
            String name = root.getValue();
            if (!NAME.matcher(name).matches()) {
                throw new IllegalArgumentException("Ungültiger Theme-Name „" + name
                        + "“ – erlaubt sind Buchstaben, Ziffern, Punkt, Binde- und Unterstrich.");
            }
            if (new File(themesDir, name).exists() && !overwrite) {
                r.existing.add(name);
            }
        }
        if (!r.existing.isEmpty()) {
            return r;
        }
        Path base = themesDir.toPath().toAbsolutePath().normalize();
        for (Map.Entry<String, String> root : roots.entrySet()) {
            String name = root.getValue();
            Path target = base.resolve(name).normalize();
            if (!target.startsWith(base)) {
                throw new IllegalArgumentException("Ungültiger Theme-Name: " + name);
            }
            deleteRecursively(target);
            Set<String> types = new TreeSet<>();
            int files = 0;
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                if (!e.getKey().startsWith(root.getKey())) {
                    continue;
                }
                String rel = e.getKey().substring(root.getKey().length());
                Path out = target.resolve(rel).normalize();
                if (!out.startsWith(target)) {
                    continue;
                }
                String first = rel.contains("/") ? rel.substring(0, rel.indexOf('/')) : "";
                if (TYPES.contains(first)) {
                    types.add(first);
                }
                Files.createDirectories(out.getParent());
                Files.write(out, e.getValue());
                files++;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", name);
            m.put("types", types);
            m.put("files", files);
            r.imported.add(m);
            if (!types.contains("login") && !types.contains("email")) {
                r.warnings.add("„" + name + "“ enthält weder login/ noch email/ – der Emulator zeigt nur diese beiden Typen an.");
            }
        }
        return r;
    }

    /** Entfernt ein eigenes Theme aus dem Theme-Ordner. */
    boolean delete(String name) throws IOException {
        if (!NAME.matcher(name).matches()) {
            return false;
        }
        Path base = themesDir.toPath().toAbsolutePath().normalize();
        Path target = base.resolve(name).normalize();
        if (!target.startsWith(base) || target.equals(base) || !Files.isDirectory(target)) {
            return false;
        }
        deleteRecursively(target);
        return true;
    }

    File dirOf(String name) {
        return NAME.matcher(name).matches() ? new File(themesDir, name) : null;
    }

    private static void deleteRecursively(Path p) throws IOException {
        if (!Files.exists(p)) {
            return;
        }
        try (Stream<Path> s = Files.walk(p)) {
            for (Path x : s.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(x);
            }
        }
    }

    private static String baseName(String file) {
        String n = file.replace('\\', '/');
        n = n.substring(n.lastIndexOf('/') + 1);
        int dot = n.lastIndexOf('.');
        return dot > 0 ? n.substring(0, dot) : n;
    }
}
