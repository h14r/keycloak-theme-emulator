package kcemu;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Kommandozeilen-Prüfung: rendert alle Seitenvarianten und Mails mit den Themes aus dem
 * Szenario und gibt Fehler und Warnungen aus. Exit-Code 1, wenn mindestens ein Fehler auftritt.
 */
final class CheckRun {

    private CheckRun() {
    }

    @SuppressWarnings("unchecked")
    static int run(kcemu.env.Emulator emu, Server server, String locale) {
        int errors = 0;
        int warnings = 0;
        System.out.println("Login-Theme: " + emu.scenario().loginTheme + ", E-Mail-Theme: " + emu.scenario().emailTheme
                + ", Sprache: " + (locale == null ? "automatisch" : locale));
        for (LoginPages.Page p : LoginPages.PAGES.values()) {
            for (LoginPages.Variant v : p.variants) {
                Map<String, String> q = new LinkedHashMap<>();
                q.put("variant", v.id);
                if (locale != null) {
                    q.put("locale", locale);
                }
                Map<String, Object> r = server.pageReport(p.id, q);
                int[] c = print(p.template + " [" + v.label + "]", (List<Checks.Finding>) r.get("findings"));
                errors += c[0];
                warnings += c[1];
            }
        }
        for (Emails.Mail m : Emails.MAILS.values()) {
            Map<String, String> q = new LinkedHashMap<>();
            if (locale != null) {
                q.put("locale", locale);
            }
            Map<String, Object> r = server.mailReport(m.id, q);
            int[] c = print("E-Mail " + m.template, (List<Checks.Finding>) r.get("findings"));
            errors += c[0];
            warnings += c[1];
        }
        System.out.println();
        System.out.println("Ergebnis: " + errors + " Fehler, " + warnings + " Warnungen");
        return errors > 0 ? 1 : 0;
    }

    private static int[] print(String title, List<Checks.Finding> findings) {
        int e = 0;
        int w = 0;
        for (Checks.Finding f : findings) {
            if ("info".equals(f.level)) {
                continue;
            }
            if ("error".equals(f.level)) {
                e++;
            } else {
                w++;
            }
            System.out.println(("error".equals(f.level) ? "FEHLER   " : "WARNUNG  ") + title + " – " + f.area + ": " + f.message
                    + (f.heuristic ? " (Heuristik)" : ""));
        }
        return new int[] { e, w };
    }
}
