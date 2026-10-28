# Changelog – Keycloak Theme Emulator

## [1.1.0] – 2026-10-28

Neue Themes schnell importieren und auswählen.

- Theme per Drag & Drop auf das Fenster importieren – Ordner, ZIP oder
  Theme-JAR (`META-INF/keycloak-themes.json`); auch mehrere Themes auf einmal
- Dialog „Themes“ (Knopf „＋ Theme“ neben der Theme-Auswahl): Ordner bzw.
  ZIP/JAR auswählen, Liste aller eigenen Themes mit Typen und Basis-Theme,
  „Login testen“ / „E-Mails testen“, Ordner öffnen, Entfernen
- importiertes Theme wird sofort ausgewählt und angezeigt; bei gleichem Namen
  Rückfrage vor dem Ersetzen
- Auswahl optional dauerhaft in `scenario.json` speichern
- Programmversion in der Oberfläche, im Steuerfenster und beim Start sichtbar
- Update-Anleitung: Programm wird ersetzt, eigene Themes und Szenario bleiben

## [1.0.0] – 2026-10-27

Installierbare macOS-App.

- `.dmg` für Apple Silicon (arm64) und Intel (x64) mit eigener Java-Laufzeit,
  gebaut über GitHub Actions (`.github/workflows/packages.yml`);
  Tag `v<version>` erzeugt ein GitHub-Release
- Start als App: Arbeitsordner `~/Keycloak Theme Emulator` mit Beispiel-Theme
  und Szenario (wird nie überschrieben), Steuerfenster (Browser öffnen,
  Theme-Ordner öffnen, Beenden), Browser öffnet sich automatisch, freier Port
  wird selbst gewählt, Protokoll in `emulator.log`
- Portable Fassung ohne Installation (ZIP mit Startdateien für macOS,
  Windows und Linux; benötigt Java 21), öffnet die Oberfläche im Browser;
  Teil desselben Releases (`packaging/build-portable.sh`)
- Option `--open`: Oberfläche nach dem Start im Browser öffnen
- `install-mac.sh`: Installation per `curl … | bash` ohne Gatekeeper-Warnung
  (keine Quarantäne-Markierung); Anleitung für `xattr` bei Browser-Download
- Optionales Signieren und Notarisieren im Workflow, sobald Apple-Zugangsdaten
  als Secrets hinterlegt sind – dann entfällt die Warnung für alle Nutzer
- `packaging/build-installer.sh` zum lokalen Bauen (`dmg` auf dem Mac,
  `app-image` auf jedem System)
- Option `--app`, um den App-Modus auch mit `java -jar` zu starten

## [0.1.0] – 2026-10-27

Erste Fassung.

- Rendering von Login- und E-Mail-Themes mit den Original-Klassen von
  Keycloak 26.8.1 (`DefaultThemeManager`, `DefaultFreeMarkerProvider`,
  `FreeMarkerLoginFormsProvider`, `FreeMarkerEmailTemplateProvider`,
  `DeclarativeUserProfileProvider`, `DefaultLocaleSelectorProvider`);
  simuliert werden nur Realm, Client, Authentifizierungssession,
  HTTP-Anfrage, IdP-Liste und Credentials
- 19 Formularseiten mit 41 Varianten (Fehler-, Erfolgs- und Sonderzustände
  mit denselben Meldungsschlüsseln wie die Authenticatoren im Server)
- 15 E-Mails (Betreff, Text- und HTML-Teil) über die Original-Sendemethoden,
  Versand als Mitschnitt
- Oberfläche: Seiten-/Mail-Auswahl, Varianten, Sprache, Bildschirmbreiten,
  Vergleich zweier Themes nebeneinander, Live-Neuladen bei Dateiänderungen,
  Klick-Navigation in der Vorschau, Szenario-Editor
- Prüfungen: Template-Fehler mit Zeilenangabe, fehlende/abweichende Themes
  (parent/import), fehlende Ressourcen, fehlende und möglicherweise
  unübersetzte Texte, Barrierefreiheit (axe-core 4.11.3), Mail-Tauglichkeit
- Gesamtprüfung aller Seiten und Mails mit Markdown-Bericht;
  Kommandozeilenmodus `--check` mit Exit-Code für CI
- Eigene User-Profile-Konfiguration (JSON aus der Admin-Konsole) inkl.
  Original-Validierung
- Beispiel-Theme `beispiel` (Login + E-Mail) als Ausgangspunkt
- Keycloak-Version per `-Dkeycloak.version=…` umstellbar (getestet: 26.8.1, 26.4.7)
