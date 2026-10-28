package kcemu;

import java.io.File;

import kcemu.env.Emulator;

/**
 * Startet den Emulator.
 *
 * <pre>
 * java -jar target/keycloak-theme-emulator.jar [--themes DIR] [--scenario DATEI] [--port N] [--host ADRESSE]
 * </pre>
 */
public class Main {

    public static void main(String[] args) throws Exception {
        // vor jedem Logging setzen, sonst warnt JBoss LogManager
        System.setProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager");
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out), true, java.nio.charset.StandardCharsets.UTF_8));

        boolean app = DesktopApp.isInstalledApp() || java.util.Arrays.asList(args).contains("--app");
        File themes = new File("themes");
        File scenario = new File("scenario.json");
        if (app) {
            java.nio.file.Path dir = DesktopApp.workDir();
            DesktopApp.prepareWorkDir(dir);
            DesktopApp.redirectOutput(dir);
            themes = dir.resolve("themes").toFile();
            scenario = dir.resolve("scenario.json").toFile();
        }
        int port = 8899;
        boolean check = false;
        boolean open = false;
        String locale = null;
        String host = "127.0.0.1";
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--themes" -> themes = new File(args[++i]);
                case "--scenario" -> scenario = new File(args[++i]);
                case "--port" -> port = Integer.parseInt(args[++i]);
                case "--host" -> host = args[++i];
                case "--check" -> check = true;
                case "--open" -> open = true;
                case "--locale" -> locale = args[++i];
                case "--app" -> {
                    // wie die installierte App: Arbeitsordner im Benutzerverzeichnis, Fenster, Browser
                }
                case "-h", "--help" -> {
                    System.out.println("Optionen: --themes DIR (Standard ./themes)  --scenario DATEI (Standard ./scenario.json)"
                            + "  --port N (Standard 8899)  --host ADRESSE (Standard 127.0.0.1)"
                            + "  --check (alle Seiten/Mails prüfen, ohne Server; Exit-Code 1 bei Fehlern)  --locale xx (für --check)"
                            + "  --open (Oberfläche nach dem Start im Browser öffnen)"
                            + "  --app (wie die installierte App: Arbeitsordner ~/Keycloak Theme Emulator, Fenster, Browser)");
                    return;
                }
                default -> {
                    System.err.println("Unbekannte Option: " + args[i]);
                    System.exit(2);
                }
            }
        }
        if (!themes.isDirectory()) {
            System.err.println("Theme-Ordner nicht gefunden: " + themes.getAbsolutePath()
                    + " (wie /opt/keycloak/themes: <theme>/login/…, <theme>/email/…)");
            System.exit(2);
        }
        if ((app || open) && !check) {
            int free = DesktopApp.freePort(host, port);
            if (free > 0) {
                port = free;
            }
        }
        String base = "http://" + ("0.0.0.0".equals(host) ? "localhost" : host) + ":" + port + "/";
        Emulator emu = new Emulator(themes, Scenario.load(scenario), base);
        if (check) {
            System.exit(CheckRun.run(emu, new Server(emu, scenario, host, 0), locale));
        }
        new Server(emu, scenario, host, port).start();
        System.out.println("Keycloak Theme Emulator (Keycloak " + Checks.version() + ")");
        System.out.println("  Themes:   " + themes.getAbsolutePath());
        System.out.println("  Szenario: " + (scenario.isFile() ? scenario.getAbsolutePath() : "Voreinstellung (" + scenario.getName() + " fehlt)"));
        System.out.println("  Oberfläche: " + base);
        if (app) {
            DesktopApp.showWindow(base, DesktopApp.workDir());
        }
        if (app || open) {
            DesktopApp.browse(base);
        }
    }
}
