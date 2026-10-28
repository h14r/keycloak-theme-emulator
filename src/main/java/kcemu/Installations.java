package kcemu;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Nur eine Version auf dem Gerät: Beim Start der installierten Mac-App werden ältere (und
 * doppelte) Kopien der App in /Programme und ~/Programme entfernt – in den Papierkorb, sonst
 * gelöscht. Eigene Themes, scenario.json und die übrigen Einstellungen liegen im Arbeitsordner
 * ~/Keycloak Theme Emulator und bleiben dabei unberührt.
 *
 * Unter Windows erledigt das der Installer selbst (gleicher Upgrade-Code: die neue .msi ersetzt
 * die alte), zusätzlich prüft install-windows.ps1 auf Reste.
 */
final class Installations {

    static final String EXECUTABLE = "Keycloak Theme Emulator";
    static final String BUNDLE_ID = "de.prototypes.kcemu";

    private Installations() {
    }

    /** Ergebnis für Protokoll und Steuerfenster. */
    static final class Report {
        final List<String> removed = new ArrayList<>();
        final List<String> newer = new ArrayList<>();
        final List<String> failed = new ArrayList<>();

        boolean isEmpty() {
            return removed.isEmpty() && newer.isEmpty() && failed.isEmpty();
        }
    }

    static Report cleanUpMac() {
        Report r = new Report();
        if (!DesktopApp.isInstalledApp() || !System.getProperty("os.name", "").toLowerCase().contains("mac")) {
            return r;
        }
        Path self = ownBundle();
        String mine = Checks.appVersion();
        List<Path> dirs = List.of(Path.of("/Applications"), Path.of(System.getProperty("user.home"), "Applications"));
        // Nur aufräumen, wenn diese App selbst installiert ist – nicht beim Ausprobieren direkt aus der .dmg
        if (self == null || dirs.stream().noneMatch(d -> self.getParent() != null && sameFile(self.getParent(), d))) {
            return r;
        }
        for (Path dir : dirs) {
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "Keycloak Theme Emulator*.app")) {
                for (Path app : ds) {
                    if (self != null && sameFile(app, self)) {
                        continue;
                    }
                    String plist = readPlist(app);
                    if (plist == null || !isOurs(plist)) {
                        continue;
                    }
                    String version = plistValue(plist, "CFBundleShortVersionString");
                    if (version != null && compare(version, mine) > 0) {
                        r.newer.add(version + " – " + app);
                        continue;
                    }
                    String label = (version == null ? "?" : version) + " – " + app;
                    if (remove(app)) {
                        r.removed.add(label);
                    } else {
                        r.failed.add(label);
                    }
                }
            } catch (IOException e) {
                // Ordner nicht lesbar – nichts zu tun
            }
        }
        for (String s : r.removed) {
            System.out.println("Ältere Installation entfernt: " + s);
        }
        for (String s : r.failed) {
            System.out.println("Ältere Installation konnte nicht entfernt werden: " + s);
        }
        for (String s : r.newer) {
            System.out.println("Neuere Installation gefunden: " + s);
        }
        return r;
    }

    /** …/Keycloak Theme Emulator.app aus dem Pfad des Starters (Contents/MacOS/…). */
    static Path ownBundle() {
        String p = System.getProperty("jpackage.app-path");
        if (p == null) {
            return null;
        }
        for (Path x = Path.of(p).toAbsolutePath(); x != null; x = x.getParent()) {
            if (x.getFileName() != null && x.getFileName().toString().endsWith(".app")) {
                return x;
            }
        }
        return null;
    }

    private static boolean sameFile(Path a, Path b) {
        try {
            return Files.isSameFile(a, b);
        } catch (IOException e) {
            return a.toAbsolutePath().normalize().equals(b.toAbsolutePath().normalize());
        }
    }

    private static String readPlist(Path app) {
        try {
            return Files.readString(app.resolve("Contents/Info.plist"), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    static boolean isOurs(String plist) {
        return EXECUTABLE.equals(plistValue(plist, "CFBundleExecutable")) || BUNDLE_ID.equals(plistValue(plist, "CFBundleIdentifier"));
    }

    static String plistValue(String plist, String key) {
        Matcher m = Pattern.compile("<key>" + Pattern.quote(key) + "</key>\\s*<string>([^<]*)</string>").matcher(plist);
        return m.find() ? m.group(1).trim() : null;
    }

    /** Vergleicht Versionen wie 1.3.0 und 1.10.2 zahlenweise; „dev“ gilt als neueste. */
    static int compare(String a, String b) {
        if (a.equals(b)) {
            return 0;
        }
        if ("dev".equals(a)) {
            return 1;
        }
        if ("dev".equals(b)) {
            return -1;
        }
        String[] x = a.split("[.\\-]");
        String[] y = b.split("[.\\-]");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? num(x[i]) : 0;
            int q = i < y.length ? num(y[i]) : 0;
            if (p != q) {
                return Integer.compare(p, q);
            }
        }
        return 0;
    }

    private static int num(String s) {
        try {
            return Integer.parseInt(s.replaceAll("\\D.*$", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** In den Papierkorb; geht das nicht, löschen (im App-Paket liegen keine Nutzerdaten). */
    private static boolean remove(Path app) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.MOVE_TO_TRASH)
                    && Desktop.getDesktop().moveToTrash(app.toFile())) {
                return true;
            }
        } catch (RuntimeException e) {
            // weiter mit Löschen
        }
        try (Stream<Path> s = Files.walk(app)) {
            for (Path p : s.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(p);
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
