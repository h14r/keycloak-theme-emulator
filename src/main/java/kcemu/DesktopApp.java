package kcemu;

import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

/**
 * Start als installierte Anwendung (macOS-App aus der .dmg): Arbeitsordner im Benutzerverzeichnis,
 * freier Port, kleines Steuerfenster, Browser öffnet sich automatisch.
 */
final class DesktopApp {

    private DesktopApp() {
    }

    /** Wird vom jpackage-Starter gesetzt – daran erkennt der Emulator die installierte App. */
    static boolean isInstalledApp() {
        return System.getProperty("jpackage.app-path") != null;
    }

    /** Sichtbarer Arbeitsordner, damit Themes direkt im Finder bearbeitet werden können. */
    static Path workDir() {
        return Path.of(System.getProperty("user.home"), "Keycloak Theme Emulator");
    }

    /**
     * Legt beim ersten Start den Arbeitsordner mit Beispiel-Theme und Szenario an.
     * Vorhandene Dateien werden nie überschrieben.
     */
    static void prepareWorkDir(Path dir) throws IOException {
        Files.createDirectories(dir.resolve("themes"));
        File jar;
        try {
            jar = new File(DesktopApp.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (Exception e) {
            return;
        }
        if (!jar.isFile()) {
            return;
        }
        try (JarFile jf = new JarFile(jar)) {
            Enumeration<JarEntry> en = jf.entries();
            while (en.hasMoreElements()) {
                JarEntry e = en.nextElement();
                if (e.isDirectory() || !e.getName().startsWith("starter/")) {
                    continue;
                }
                Path target = dir.resolve(e.getName().substring("starter/".length())).normalize();
                if (!target.startsWith(dir) || Files.exists(target)) {
                    continue;
                }
                Files.createDirectories(target.getParent());
                try (InputStream in = jf.getInputStream(e)) {
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    /** Ohne Konsole landen Ausgaben in einer Logdatei im Arbeitsordner. */
    static void redirectOutput(Path dir) {
        try {
            PrintStream log = new PrintStream(Files.newOutputStream(dir.resolve("emulator.log")), true, StandardCharsets.UTF_8);
            System.setOut(log);
            System.setErr(log);
        } catch (IOException e) {
            // Konsole bleibt erhalten
        }
    }

    /** Erster freier Port ab {@code start} – falls 8899 schon belegt ist (z. B. zweite Instanz). */
    static int freePort(String host, int start) {
        for (int p = start; p < start + 100; p++) {
            try (ServerSocket s = new ServerSocket(p, 1, InetAddress.getByName(host))) {
                return p;
            } catch (IOException e) {
                // belegt – nächster
            }
        }
        return 0;
    }

    static void showWindow(String url, Path dir) {
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("Keycloak Theme Emulator");
            f.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            JPanel p = new JPanel(new BorderLayout(0, 12));
            p.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
            p.add(new JLabel("<html><b>Emulator läuft</b> – Version " + Checks.appVersion()
                    + " (Keycloak " + Checks.version() + ")<br>"
                    + url + "<br><br>Themes bearbeiten im Ordner:<br>" + dir.resolve("themes")
                    + "<br><br>Fenster schließen beendet den Emulator.</html>"), BorderLayout.CENTER);
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            JButton open = new JButton("Im Browser öffnen");
            open.addActionListener(a -> browse(url));
            JButton folder = new JButton("Theme-Ordner öffnen");
            folder.addActionListener(a -> openFolder(dir.resolve("themes")));
            JButton quit = new JButton("Beenden");
            quit.addActionListener(a -> System.exit(0));
            buttons.add(folder);
            buttons.add(open);
            buttons.add(quit);
            p.add(buttons, BorderLayout.SOUTH);
            f.setContentPane(p);
            f.pack();
            f.setLocationRelativeTo(null);
            f.setVisible(true);
            f.getRootPane().setDefaultButton(open);
        });
    }

    static void browse(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
            }
        } catch (IOException | RuntimeException e) {
            System.err.println("Browser konnte nicht geöffnet werden: " + e.getMessage());
        }
    }

    static void openFolder(Path dir) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(dir.toFile());
            }
        } catch (IOException | RuntimeException e) {
            System.err.println("Ordner konnte nicht geöffnet werden: " + e.getMessage());
        }
    }
}
