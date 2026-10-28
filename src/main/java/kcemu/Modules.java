package kcemu;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Optionale Module der Oberfläche (z. B. die Barrierefreiheitsprüfung). Welche Module es gibt,
 * steht in ui/modules/modules.json; jedes Modul liegt in ui/modules/{id}/. Ob ein Modul aktiv
 * oder entfernt ist, merkt sich der Emulator in modules-state.json neben scenario.json.
 *
 * <ul>
 * <li>deaktiviert: Code wird ausgeliefert, aber nicht geladen – jederzeit wieder einschaltbar</li>
 * <li>entfernt: Code wird gar nicht mehr ausgeliefert (HTTP 404), das Modul erscheint nur noch
 * in der Modulverwaltung zum Wiederherstellen</li>
 * <li>aus dem Quellcode gelöscht: Ordner und Eintrag in modules.json entfernen – der Emulator
 * läuft ohne das Modul weiter</li>
 * </ul>
 */
final class Modules {

    static final String MANIFEST = "ui/modules/modules.json";
    private static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9-]{0,40}");

    private final File stateFile;

    Modules(File stateFile) {
        this.stateFile = stateFile;
    }

    /** Alle Module aus dem Manifest, ergänzt um enabled/removed aus der Zustandsdatei. */
    synchronized List<Map<String, Object>> list() throws IOException {
        List<Map<String, Object>> out = new ArrayList<>();
        JsonNode manifest = manifest();
        JsonNode st = state();
        for (JsonNode m : manifest.path("modules")) {
            String id = m.path("id").asText();
            if (!ID.matcher(id).matches() || !exists("ui/modules/" + id + "/module.js")) {
                continue; // Eintrag ohne Code: Modul wurde aus dem Quellcode gelöscht
            }
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", id);
            r.put("name", m.path("name").asText(id));
            r.put("version", m.path("version").asText(""));
            r.put("description", m.path("description").asText(""));
            r.put("css", m.path("css").asBoolean(false));
            JsonNode s = st.path(id);
            r.put("enabled", s.path("enabled").asBoolean(m.path("enabledByDefault").asBoolean(true)));
            r.put("removed", s.path("removed").asBoolean(false));
            out.add(r);
        }
        return out;
    }

    /** Zustand eines Moduls ändern; nur bekannte Felder (enabled, removed) werden übernommen. */
    synchronized boolean update(String id, JsonNode req) throws IOException {
        if (list().stream().noneMatch(m -> m.get("id").equals(id))) {
            return false;
        }
        ObjectNode st = (ObjectNode) state();
        ObjectNode s = st.has(id) && st.get(id).isObject() ? (ObjectNode) st.get(id) : st.putObject(id);
        if (req.has("enabled")) {
            s.put("enabled", req.get("enabled").asBoolean());
        }
        if (req.has("removed")) {
            s.put("removed", req.get("removed").asBoolean());
        }
        File dir = stateFile.getAbsoluteFile().getParentFile();
        if (dir != null) {
            dir.mkdirs();
        }
        Scenario.JSON.writerWithDefaultPrettyPrinter().writeValue(stateFile, st);
        return true;
    }

    /** true, wenn eine Datei unter /ui/modules/{id}/… ausgeliefert werden darf. */
    synchronized boolean served(String id) throws IOException {
        return !state().path(id).path("removed").asBoolean(false);
    }

    private JsonNode manifest() throws IOException {
        File dev = new File("src/main/resources/" + MANIFEST);
        if (dev.isFile()) {
            return Scenario.JSON.readTree(dev);
        }
        try (InputStream in = Modules.class.getClassLoader().getResourceAsStream(MANIFEST)) {
            return in == null ? Scenario.JSON.createObjectNode() : Scenario.JSON.readTree(in);
        }
    }

    private JsonNode state() throws IOException {
        if (!stateFile.isFile()) {
            return Scenario.JSON.createObjectNode();
        }
        try {
            JsonNode n = Scenario.JSON.readTree(stateFile);
            return n != null && n.isObject() ? n : Scenario.JSON.createObjectNode();
        } catch (IOException e) {
            return Scenario.JSON.createObjectNode(); // beschädigte Datei: mit Voreinstellung weiter
        }
    }

    private static boolean exists(String res) {
        if (new File("src/main/resources/" + res).isFile()) {
            return true;
        }
        return Modules.class.getClassLoader().getResource(res) != null;
    }
}
