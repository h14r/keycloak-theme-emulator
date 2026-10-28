# Changelog – Keycloak Theme Emulator

## [1.3.1] – 2026-10-28

Fehlerbehebungen, neue Oberfläche, eigene Symbole, Updates ohne Altlasten.

- Behoben: Barrierefreiheit zeigte trotz Ergebnis keine Markierungen auf der
  Seite. Wurde die Vorschau während einer Prüfung neu geladen (Live-Neuladen,
  ↻, Seitenwechsel), konnte die Prüfung hängen bleiben („Prüfe …“) und alle
  weiteren blockieren. Jetzt ist jede Prüfung unabhängig, veraltete Ergebnisse
  werden verworfen, jeder Schritt hat ein Zeitlimit.
- Barrierefreiheit: Fundstellen werden auf der Seite eingerahmt und
  nummeriert, mit gleichnamiger Liste im Inspektor (Hover hebt hervor, Klick
  zeigt die Referenz). Schalter „Auf der Seite markieren“ (ein/aus) und
  „nur Fundstellen“ / „alles (wie WAVE)“ ersetzen den Knopf „Symbole“.
- Hell/Dunkel getrennt einstellbar: für die Oberfläche (System/Hell/Dunkel)
  und für die Vorschau – diese simuliert die Geräteeinstellung
  (`prefers-color-scheme`), auch bei Klicks in der Vorschau und für Mails.
- Neue Oberfläche angelehnt an aktuelle Betriebssysteme: Titelleiste mit
  Werkzeugen, Seitenleiste als Quellliste, Leisten für Inhalt und Ansicht,
  Inspektor mit Reitern und Zählern, segmentierte Schalter, Kippschalter,
  Symbole; passt sich an Fensterbreiten bis unter 760 px an. Behoben:
  unlesbare Auswahllisten im Dunkelmodus (Safari).
- Neue App-Symbole: Favicon, Web-Manifest, Symbol in der Titelleiste, im
  Steuerfenster, im Mac-Programm (`.icns`) und im Windows-Installer (`.ico`).
- Updates: Es bleibt immer genau eine Version installiert. Mac: Installskript
  und erster Start der neuen App entfernen ältere bzw. doppelte Kopien;
  Windows: Installer ersetzt die alte Version, Installskript beseitigt Reste.
  Themes und Einstellungen werden übernommen; Einstellungen der Oberfläche
  zusätzlich in `ui-settings.json` gesichert (übersteht Portwechsel).
  Der Build prüft das Update von der Vorversion auf Mac und Windows.

## [1.3.0] – 2026-10-28

Barrierefreiheitsprüfung nach WCAG 2.2 AA als abschaltbares Modul.

- Neues Modul „Barrierefreiheit (WCAG 2.2 AA)“, angelehnt an WAVE:
  Symbole auf der Seite in sechs Kategorien (Fehler, Kontrastfehler,
  Hinweise, Merkmale, Struktur, ARIA), Ansichten Übersicht, Details mit
  Referenz, Reihenfolge, Struktur, Kontrast (mit Kontrastrechner) und
  Kriterien (alle 55 Erfolgskriterien A/AA mit Status), „Styles aus“,
  „Graustufen“, Bericht als Markdown
- Prüfmaschine axe-core 4.11.3 mit WCAG-2.0/2.1/2.2-A/AA- und
  Best-Practice-Regeln, Meldungen auf Deutsch
- Modulverwaltung (Knopf „Module“): aktivieren, deaktivieren, entfernen,
  wiederherstellen; Zustand in `modules-state.json`
- „Gesamtes Theme prüfen“ nimmt die Prüfungen aktiver Module auf (Spalte
  „WCAG-Verstöße“)
- Die bisherige Schaltfläche „Barrierefreiheit prüfen (axe-core)“ ist im
  Modul aufgegangen
- Windows-Installer (`.msi`, x64): Installation ohne Administratorrechte,
  Startmenü und Desktop-Verknüpfung, eigene Java-Laufzeit; neue Versionen
  ersetzen die installierte; `install-windows.ps1` installiert per
  PowerShell ohne SmartScreen-Warnung; optionales Signieren im Workflow
- Überblick beim Start: neun kurze Seiten zu den Funktionen mit Bildern aus
  der Anwendung (Beispiel-Theme); „nicht mehr zeigen“ bis zur nächsten
  Version, jederzeit über „Überblick“; Bilder werden mit
  `packaging/screenshots.js` erzeugt

## [1.2.0] – 2026-10-28

Zoom und Anpassen an die Fensterbreite.

- Zoom-Leiste neben der Breitenauswahl: **−** / **+** (25 %–300 %), Klick
  auf die Prozentzahl = 100 %, **Breite** = an die Fensterbreite anpassen;
  Tastatur `+`, `−`, `0`, `F`; Einstellung wird gemerkt
- „Breite“ ist die Voreinstellung und passt sich bei Fenstergröße und
  Vergleichsansicht automatisch an
- Korrektur: Bisher wurde z. B. die Desktop-Vorschau (1280 px) in ein
  schmaleres Fenster gestaucht, wodurch die Seite intern mit kleinerer Breite
  gerendert wurde. Jetzt rendert sie immer mit der gewählten Breite und wird
  nur skaliert dargestellt.

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
  neue Version auf `main` erzeugt automatisch das GitHub-Release `v<version>`
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
