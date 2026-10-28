# Keycloak Theme Emulator

Lokale Vorschau- und Prüfumgebung für **Login-Themes** (Formularseiten rund um
Anmeldung, Registrierung, Passwort, Profil) und **E-Mail-Themes** von Keycloak –
ohne Zugang zu einem Keycloak-Server.

Neue Designs lassen sich damit entwickeln, in allen Seitenzuständen ansehen,
mit dem Original-Theme vergleichen, prüfen (Template-Fehler, fehlende Texte,
fehlende Dateien, Barrierefreiheit, Mail-Tauglichkeit) und als Bericht
festhalten.

---

## Inhalt

- [Installation auf dem Mac (.dmg)](#installation-auf-dem-mac-dmg)
- [Ohne Installation im Browser (portables ZIP)](#ohne-installation-im-browser-portables-zip)
- [Neue Themes importieren und auswählen](#neue-themes-importieren-und-auswählen)
- [Auf eine neue Version aktualisieren](#auf-eine-neue-version-aktualisieren)
- [Schnellstart](#schnellstart)
- [So funktioniert es](#so-funktioniert-es)
- [Was echt ist und was simuliert wird](#was-echt-ist-und-was-simuliert-wird)
- [Oberfläche](#oberfläche)
- [Abgedeckte Seiten und E-Mails](#abgedeckte-seiten-und-e-mails)
- [Prüfungen](#prüfungen)
- [Modul Barrierefreiheit (WCAG 2.2 AA)](#modul-barrierefreiheit-wcag-22-aa)
- [Module aktivieren, deaktivieren, entfernen](#module-aktivieren-deaktivieren-entfernen)
- [Szenario (simulierte Realm-Einstellungen)](#szenario-simulierte-realm-einstellungen)
- [Eigenes Theme anlegen](#eigenes-theme-anlegen)
- [Prüfung auf der Kommandozeile (CI)](#prüfung-auf-der-kommandozeile-ci)
- [Keycloak-Version wechseln](#keycloak-version-wechseln)
- [Grenzen](#grenzen)

---

## Installation auf dem Mac (.dmg)

Die App bringt ihre eigene Java-Laufzeit mit – Java oder Maven sind dafür
nicht nötig.

**Herunterladen** (ohne GitHub-Anmeldung):
<https://github.com/h14r/keycloak-theme-emulator/releases/latest>

- `Keycloak-Theme-Emulator-<version>-arm64.dmg` – Apple Silicon (M1–M4)
- `Keycloak-Theme-Emulator-<version>-x64.dmg` – Intel-Mac

Die Dateien entstehen über GitHub Actions (Workflow „Pakete (.dmg und
portable ZIP)“). Sobald auf `main` eine neue Version (aus `pom.xml`)
ankommt, legt der Workflow automatisch das Release `v<version>` an.

Welcher Prozessor im Mac steckt, steht unter  → *Über diesen Mac*
(„Chip Apple …“ = arm64, „Prozessor Intel …“ = x64).

**Installieren ohne Sicherheitswarnung (empfohlen):** Im Terminal

```bash
curl -fsSL https://raw.githubusercontent.com/h14r/keycloak-theme-emulator/main/install-mac.sh | bash
```

Das Skript ([install-mac.sh](./install-mac.sh)) lädt die passende `.dmg`
(Apple Silicon oder Intel) aus dem neuesten Release, kopiert die App nach
*Programme* und startet sie. Per `curl` geladene Dateien tragen keine
Quarantäne-Markierung – macOS zeigt deshalb die Meldung „Apple konnte nicht
überprüfen, ob … frei von Schadsoftware ist“ nicht an. Für Updates denselben
Befehl erneut ausführen; der Arbeitsordner bleibt erhalten.

**Installieren per Download:** `.dmg` im Browser laden, öffnen und
*Keycloak Theme Emulator* in den Ordner *Programme* ziehen. Solange die App
nicht notarisiert ist (siehe [Signieren und notarisieren](#signieren-und-notarisieren)),
blockiert macOS sie beim ersten Öffnen. Dann entweder einmalig im Terminal

```bash
xattr -dr com.apple.quarantine "/Applications/Keycloak Theme Emulator.app"
```

ausführen (entfernt die Markierung, danach startet die App normal) oder:

- macOS 15 (Sequoia) und neuer: Meldung mit *Fertig* schließen, dann
  *Systemeinstellungen → Datenschutz & Sicherheit* → bei „Keycloak Theme
  Emulator wurde blockiert“ auf *Dennoch öffnen* klicken.
- macOS 14 und älter: im Finder mit gedrückter ctrl-Taste auf die App
  klicken → *Öffnen* → *Öffnen*.

**Benutzung:** Nach dem Start öffnet sich ein kleines Fenster und der
Browser mit der Oberfläche. Beim ersten Start legt die App den Arbeitsordner
`~/Keycloak Theme Emulator` an:

```
~/Keycloak Theme Emulator/
├── themes/beispiel/   Beispiel-Theme – eigene Themes hier ablegen
├── scenario.json      simulierte Realm-Einstellungen
└── emulator.log       Protokoll (bei Problemen hilfreich)
```

Vorhandene Dateien werden bei späteren Starts oder Updates nie
überschrieben. „Theme-Ordner öffnen“ im Fenster zeigt den Ordner im Finder;
Fenster schließen oder „Beenden“ stoppt den Emulator. Ist Port 8899 belegt,
nimmt die App automatisch den nächsten freien Port.

### Signieren und notarisieren

Damit die Warnung auch beim normalen Download aus dem Browser **für alle
Nutzer** entfällt, muss die App mit einem Apple-Zertifikat *Developer ID
Application* signiert und von Apple notarisiert sein. Dafür ist eine
Mitgliedschaft im Apple Developer Program nötig (Firma oder Person).

Der Workflow erledigt beides automatisch, sobald diese Repository-Secrets
gesetzt sind (*Settings → Secrets and variables → Actions*):

| Secret | Inhalt |
|---|---|
| `MACOS_CERTIFICATE_P12` | Zertifikat „Developer ID Application“ samt privatem Schlüssel als `.p12`, Base64-kodiert (`base64 -i zertifikat.p12 \| pbcopy`) |
| `MACOS_CERTIFICATE_PASSWORD` | Passwort der `.p12`-Datei |
| `MACOS_SIGNING_NAME` | Name aus dem Zertifikat ohne Präfix, z. B. `Beispiel GmbH (AB12CD34EF)` |
| `APPLE_ID` | Apple-ID des Entwicklerkontos |
| `APPLE_TEAM_ID` | Team-ID (10 Zeichen, im Developer-Portal unter *Membership*) |
| `APPLE_APP_PASSWORD` | App-spezifisches Passwort der Apple-ID (appleid.apple.com → *Anmeldung und Sicherheit*) |

Danach die Version in `pom.xml` erhöhen und auf `main` pushen. Der Workflow signiert App und
`.dmg` (`jpackage --mac-sign`, Hardened Runtime), reicht die `.dmg` bei Apple
ein (`notarytool`), heftet das Ergebnis an (`stapler`) und prüft mit
`spctl`, dass Gatekeeper die App akzeptiert. Ohne Secrets entsteht wie bisher
eine unsignierte `.dmg`.

**Selbst bauen** (auf einem Mac mit JDK 21 und Maven):

```bash
packaging/build-installer.sh dmg     # Ergebnis: target/installer/*.dmg
```

---

## Ohne Installation im Browser (portables ZIP)

Alternative zur Mac-App, ohne Installation und auch für Windows und Linux:
`Keycloak-Theme-Emulator-<version>-portable.zip` aus demselben Release
(<https://github.com/h14r/keycloak-theme-emulator/releases/latest>).

Voraussetzung ist **Java 21** oder neuer, z. B.
[Eclipse Temurin](https://adoptium.net/de/temurin/releases/?version=21).

1. ZIP entpacken.
2. Startdatei doppelklicken:
   - macOS: `start-mac.command` (beim ersten Mal ctrl+Klick → *Öffnen*, weil
     die Datei aus dem Internet stammt)
   - Windows: `start-windows.bat`
   - Linux: `./start-linux.sh`
3. Der Browser öffnet die Oberfläche (sonst <http://127.0.0.1:8899/>). Ist
   Port 8899 belegt, nimmt der Emulator den nächsten freien Port – die
   Adresse steht im Startfenster.

Eigene Themes kommen in den Ordner `themes/` neben den Startdateien,
`scenario.json` liegt daneben. Beenden: Startfenster schließen.

Selbst bauen: `packaging/build-portable.sh` → `target/portable/*.zip`.

Mit `java -jar target/keycloak-theme-emulator.jar --open` öffnet auch der
normale Start (siehe [Schnellstart](#schnellstart)) die Oberfläche direkt
im Browser.

---

## Neue Themes importieren und auswählen

Ein neues Theme ist in wenigen Sekunden in der Vorschau:

1. **Theme in das Fenster ziehen** – einen Theme-Ordner, ein ZIP oder ein
   Theme-JAR einfach auf die Oberfläche fallen lassen. Alternativ
   **„＋ Theme“** neben der Theme-Auswahl (oder **„Themes“** oben rechts) →
   *Ordner wählen …* bzw. *ZIP/JAR wählen …*
2. Der Emulator kopiert das Theme in den Theme-Ordner, prüft es und zeigt es
   **sofort** an (Login-Seiten, bzw. E-Mails, wenn es nur ein E-Mail-Theme ist).

Erkannt werden die üblichen Lieferformen:

| Lieferform | Beispiel |
|---|---|
| Theme-Ordner | `mein-theme/login/theme.properties`, `mein-theme/email/…` |
| ZIP mit Theme-Ordner | `mein-theme.zip` → `mein-theme/login/…` |
| ZIP ohne äußeren Ordner | `mein-theme.zip` → `login/…` (Name = Dateiname) |
| Theme-JAR (wie für `/opt/keycloak/providers`) | `META-INF/keycloak-themes.json` + `theme/<name>/<typ>/…` |
| Ordner mit mehreren Themes | `themes/a/login/…`, `themes/b/email/…` |

Gibt es ein Theme mit gleichem Namen schon, fragt der Emulator, ob es ersetzt
werden soll. Systemdateien (`.DS_Store`, `__MACOSX`) werden übersprungen.

Im Dialog **„Themes“** stehen alle eigenen Themes mit Typen und Basis-Theme:

- **Login testen** / **E-Mails testen** – Theme auswählen und anzeigen,
- **Ordner** – Theme-Ordner im Finder/Explorer öffnen (zum Bearbeiten),
- **Entfernen** – Theme aus dem Theme-Ordner löschen (mit Rückfrage).

Mit **„Auswahl auch als Standard speichern“** wird die Auswahl in
`scenario.json` übernommen und gilt auch nach einem Neustart. Ohne Haken gilt
sie bis zum Neustart. Für schnelle Wechsel genügt die Theme-Auswahl oben in
der Werkzeugleiste.

---

## Auf eine neue Version aktualisieren

Ein Update ersetzt nur das Programm. **Eigene Themes und `scenario.json`
bleiben erhalten**: Der Arbeitsordner `~/Keycloak Theme Emulator` wird beim
Start nur ergänzt, nie überschrieben.

- **Mac, per Terminal:** denselben Befehl wie bei der Installation erneut
  ausführen – er lädt die neueste Version und ersetzt die App:
  `curl -fsSL https://raw.githubusercontent.com/h14r/keycloak-theme-emulator/main/install-mac.sh | bash`
- **Mac, per `.dmg`:** Emulator beenden, neue `.dmg` öffnen und die App
  erneut nach *Programme* ziehen → *Ersetzen*.
- **Portables ZIP:** neues ZIP entpacken und die Ordner `themes/` sowie die
  Datei `scenario.json` aus dem alten Ordner hineinkopieren.

Welche Version läuft, steht oben links in der Oberfläche und im Steuerfenster der App; was sich geändert hat, in [CHANGELOG.md](./CHANGELOG.md).

---

## Schnellstart

Voraussetzungen: Java 21 und Maven 3.9 (beim ersten Bauen wird Maven Central
benötigt; danach läuft alles offline).

```bash
git clone https://github.com/h14r/keycloak-theme-emulator.git
cd keycloak-theme-emulator
mvn -q package
java -jar target/keycloak-theme-emulator.jar
```

Dann <http://127.0.0.1:8899/> öffnen.

Optionen:

| Option | Standard | Bedeutung |
|---|---|---|
| `--themes DIR` | `./themes` | Theme-Ordner im Keycloak-Layout (`<theme>/login/…`, `<theme>/email/…`) – also derselbe Aufbau wie `/opt/keycloak/themes` |
| `--scenario DATEI` | `./scenario.json` | simulierte Realm-, Nutzer- und Client-Daten |
| `--port N` | `8899` | Port der Oberfläche |
| `--host ADRESSE` | `127.0.0.1` | z. B. `0.0.0.0` für Zugriff aus dem Netz |
| `--check` | – | alle Seiten und Mails prüfen, ohne Server zu starten (Exit-Code 1 bei Fehlern) |
| `--locale xx` | automatisch | Sprache für `--check` |
| `--open` | – | Oberfläche nach dem Start im Browser öffnen (bei belegtem Port den nächsten freien nehmen) |

Themes, die als JAR verpackt sind (mit `META-INF/keycloak-themes.json`, wie
beim Deployment über `providers/`), werden über den Klassenpfad eingebunden:

```bash
java -cp target/keycloak-theme-emulator.jar:mein-theme.jar kcemu.Main
```

---

## So funktioniert es

Der Emulator baut das Verhalten von Keycloak nicht nach, sondern **führt die
Original-Klassen von Keycloak aus** (aus `keycloak-services`,
`keycloak-server-spi-private` und `keycloak-themes` der eingestellten Version)
und ersetzt nur das, was im Server eine Datenbank, Infinispan oder einen
HTTP-Request bräuchte.

```
Theme-Ordner ─┐
              ├─► DefaultThemeManager (parent/import, Nachrichten, Properties)
keycloak-     │        │
themes.jar ───┘        ▼
               FreeMarkerLoginFormsProvider ──► DefaultFreeMarkerProvider ──► HTML
               FreeMarkerEmailTemplateProvider ─┘                    (wie im Server)
                       ▲
   simuliert: Realm, Nutzer, Client, Auth-Session, HTTP-Request, IdP-Liste, Credentials
```

Eine Login-Seite entsteht also genau so wie im Server: Für „Falsche
Anmeldedaten“ ruft der Emulator z. B.
`addError(new FormMessage("username", "invalidUserMessage"))` und
`createLoginUsernamePassword()` auf – dieselben Aufrufe wie
`UsernamePasswordForm` in Keycloak. Mails entstehen über
`sendVerifyEmail(…)`, `sendPasswordReset(…)`, `sendEvent(…)` usw.; statt
SMTP wird die fertige Nachricht (Betreff, Text- und HTML-Teil) mitgeschnitten.

Konfiguration wie im Entwicklungsmodus (`kc.sh start-dev`): Theme- und
Template-Cache sind aus, Änderungen am Theme sind beim nächsten Laden sichtbar.

---

## Was echt ist und was simuliert wird

| Bereich | Echt (Original-Code von Keycloak) | Simuliert |
|---|---|---|
| Theme-Auflösung | `DefaultThemeManager`, `FolderThemeProvider`, Classpath-/JAR-Themes, `parent=`, `import=`, Zusammenführung von `theme.properties` und Nachrichten, `${env.X}`-Ersetzung in Properties | – |
| Template-Engine | `DefaultFreeMarkerProvider` mit identischer FreeMarker-Konfiguration (Version aus `keycloak-parent`, HTML-Escaping, `kcSanitize`) | – |
| Datenmodell der Seiten | `FreeMarkerLoginFormsProvider` mit allen Beans (`realm`, `url`, `login`, `message`, `messagesPerField`, `social`, `locale`, `profile`, `passwordPolicies`, `auth`, …) | – |
| Mails | `FreeMarkerEmailTemplateProvider` (Betreff, `realmName`, `user`, `linkExpirationFormatter`, `ltr`, …) | Versand (Mitschnitt statt SMTP) |
| Sprache | `DefaultLocaleSelectorProvider` (kc_locale, Nutzerattribut, Accept-Language, Realm-Standard), Realm-Übersetzungen (`localizationTexts`) | Browser-Header aus dem Szenario |
| Benutzerprofil | `DeclarativeUserProfileProvider` inkl. eigener User-Profile-JSON und echter Validierung | – |
| Passwort-Richtlinie | `PasswordPolicy.parse(…)` mit den Original-Policy-Providern | – |
| Realm, Client, Nutzer | `InMemoryUserAdapter` für den Nutzer | Realm, Client, Identity-Provider-Liste, OTP-Geräte, Authentifizierungssession |
| Ressourcen | Auslieferung über die Theme-Kette wie `/resources/{version}/{typ}/{theme}/…` | Content-Type über `MimeTypeUtil` |

---

## Oberfläche

- **Formularseiten / E-Mails** links auswählen; jede Seite hat mehrere
  **Varianten** (Standard, Fehlerzustände, Erfolgsmeldungen …).
- **Theme**: alle im Theme-Ordner und in Keycloak vorhandenen Themes.
  **Vergleich mit** zeigt ein zweites Theme daneben (z. B. das eigene gegen
  `keycloak.v2`).
- **Sprache**: automatisch (Accept-Language aus dem Szenario) oder fest –
  entspricht `?kc_locale=` bzw. bei Mails dem Profilattribut `locale`.
- **Breite**: Mobil (375 px), Tablet (768 px), Desktop (1280 px), volle Breite.
  Die Seite wird immer mit genau dieser Breite gerendert – so, wie sie ein
  Browser dieser Größe zeigt.
- **Zoom**: **−** / **+** verkleinern bzw. vergrößern die Darstellung
  (25 %–300 %), ein Klick auf die Prozentzahl setzt auf 100 % zurück,
  **Breite** passt die Vorschau an die Fensterbreite an (auch nebeneinander
  im Vergleich und beim Ändern der Fenstergröße). Tastatur: `+`, `−`, `0`,
  `F`. Zoom ändert nur die Darstellung, nicht das Layout der Seite.
- **Live-Neuladen**: Sobald sich eine Datei im Theme-Ordner ändert, wird die
  Vorschau neu erzeugt.
- **Klicken in der Vorschau**: Sprachumschalter, „Registrieren“, „Passwort
  vergessen?“ führen zur passenden Seite. Abgeschickte Formulare zeigen die
  übermittelten Felder (gelb markierter Hinweis des Emulators – keine
  Keycloak-Ausgabe).
- **Bewertung** (rechts): Befunde der Prüfungen, Theme-Kette mit den eigenen
  Dateien, verfügbare Template-Variablen und abgefragte Textschlüssel;
  darunter die Bereiche aktiver Module (z. B. Barrierefreiheit).
- **Gesamtes Theme prüfen**: alle Varianten und Mails nacheinander, optional
  mit den Prüfungen aktiver Module; Ergebnis als Markdown-Bericht
  herunterladbar.
- **Module**: Zusatzmodule aktivieren, deaktivieren oder entfernen.
- **Szenario**: Realm-Schalter (Registrierung, „Angemeldet bleiben“, E-Mail
  als Benutzername …) und das vollständige Szenario als JSON bearbeiten.

Bei Dark Mode folgt das Theme `keycloak.v2` der Einstellung des
Betriebssystems bzw. Browsers – genau wie im Original.

---

## Abgedeckte Seiten und E-Mails

**Formularseiten** (Login-Theme):

| Template | Auslöser im Server | Varianten |
|---|---|---|
| `login.ftl` | Username Password Form | Standard, falsche Anmeldedaten, Konto deaktiviert, Brute-Force-Sperre, „E-Mail gesendet“, Sitzung abgelaufen |
| `login-username.ftl` / `login-password.ftl` | Username Form / Password Form | Standard, Fehler |
| `login-otp.ftl` | OTP Form | Standard, falscher Code |
| `login-page-expired.ftl` | veralteter Tab / Zurück-Taste | Standard |
| `register.ftl` | Registrierung (Felder aus dem User Profile) | Standard, mit Nutzungsbedingungen, Fehler (echte Profil-Validierung) |
| `terms.ftl` | Required Action „Terms and Conditions“ | Standard |
| `login-reset-password.ftl` | „Passwort vergessen?“ | Standard, kein Benutzername |
| `login-update-password.ftl` | Required Action „Update Password“ | über Reset-Link, vom Admin angefordert, Passwörter ungleich, Richtlinie verletzt |
| `login-update-profile.ftl` | Required Action „Update Profile“ | Standard, neuer Nutzer ohne Namen |
| `login-verify-email.ftl` | Required Action „Verify Email“ | Standard |
| `login-config-totp.ftl` | Required Action „Configure OTP“ | Standard, falscher Code |
| `delete-account-confirm.ftl` | Required Action „Delete Account“ | Standard, aus der Anwendung ausgelöst |
| `login-idp-link-confirm.ftl` / `login-idp-link-email.ftl` | First-Broker-Login | Standard |
| `info.ftl` | Abschluss von Aktionen | Konto aktualisiert, E-Mail bestätigt, Admin-Aktionen ausführen |
| `error.ftl` | Fehler im Ablauf | Link abgelaufen, Cookie fehlt, ungültige Anfrage, Seite nicht gefunden |
| `logout-confirm.ftl` | Abmeldung ohne `id_token_hint` | Standard |
| `login-oauth2-device-verify-user-code.ftl` | Device Authorization Grant | Standard |

**E-Mails** (E-Mail-Theme, jeweils HTML- und Text-Teil plus Betreff):
`email-verification`, `password-reset`, `executeActions`,
`email-update-confirmation`, `identity-provider-link`, `org-invite`,
`email-test` sowie die Ereignis-Mails `event-login_error`,
`event-update_password`, `event-update_totp`, `event-remove_totp`,
`event-update_credential`, `event-remove_credential`,
`event-user_disabled_by_temporary_lockout`,
`event-user_disabled_by_permanent_lockout`.

---

## Prüfungen

Jeder Befund ist entweder **Keycloak-Verhalten** (so passiert es im Server)
oder als **Heuristik** markiert (Erfahrungsregel).

| Prüfung | Art |
|---|---|
| FreeMarker-Fehler mit Template, Zeile und Spalte – Keycloak würde eine leere Seite mit Status 500 liefern bzw. die Mail nicht versenden | Keycloak-Verhalten |
| Theme, `parent=` oder `import=` nicht auffindbar → Keycloak fällt auf das Standard-Theme zurück | Keycloak-Verhalten |
| `styles`, `scripts`, `stylesCommon`, `scriptsCommon` verweisen auf nicht vorhandene Dateien; im Browser zusätzlich alle eingebundenen CSS/JS/Bilder (HTTP-Status) | Keycloak-Verhalten |
| Fehlender Textschlüssel (Keycloak zeigt dann den Schlüssel selbst an); Kennzeichnung, wenn er auch im Original-Theme fehlt | Keycloak-Verhalten |
| Möglicherweise unübersetzter Text (identisch mit dem englischen Bündel) | Heuristik |
| Barrierefreiheit nach WCAG 2.2 AA – im [Modul Barrierefreiheit](#modul-barrierefreiheit-wcag-22-aa) | Prüfwerkzeug (axe-core 4.11) |
| Mail: Aktionslink fehlt im HTML- oder Text-Teil, leerer Betreff/Text-Teil, Bilder mit relativer Adresse | Keycloak-Verhalten / Mail-Clients |
| Mail: > 102 KB (Gmail kürzt), externe Stylesheets, `<script>`, flex/grid/position (Outlook), data:-Bilder, fehlende `alt`-Texte, langer Betreff | Heuristik |

---

## Modul Barrierefreiheit (WCAG 2.2 AA)

Prüft die angezeigte Seite bzw. Mail gegen **WCAG 2.2, Stufe AA** (also alle
Erfolgskriterien der Stufen A und AA) und stellt das Ergebnis ähnlich wie die
Browser-Erweiterung **WAVE** von WebAIM dar. Grundlage ist die
Prüfbibliothek **axe-core 4.11.3** (läuft lokal im Browser, Regeln mit den
Tags `wcag2a`, `wcag2aa`, `wcag21a`, `wcag21aa`, `wcag22aa` sowie
`best-practice`; deutsche Meldungstexte von axe-core).

**Symbole auf der Seite** – wie bei WAVE in sechs Kategorien, einzeln
ein-/ausblendbar. Ein Klick auf ein Symbol öffnet die Referenz dazu:

| Kategorie | Inhalt |
|---|---|
| ✕ Fehler | Verstöße gegen WCAG 2.2 A/AA, die axe-core sicher erkennt |
| ◐ Kontrastfehler | Text unter 4,5:1 bzw. 3:1 (großer Text), Kriterium 1.4.3 |
| ! Hinweise | von axe-core als „manuell prüfen“ gemeldet, Best-Practice-Regeln ohne WCAG-Zuordnung und einige eigene, als *Heuristik* gekennzeichnete Hinweise (z. B. fehlendes `autocomplete` bei Benutzername/E-Mail/Passwort → 1.3.5, Tabellen ohne Kopfzellen, sehr kleine Schrift, Links in neuem Fenster) |
| ✓ Merkmale | vorhandene Alternativtexte, beschriftete Felder, Gruppen mit Legende, `lang`, `autocomplete`, Sprunglink |
| ≡ Struktur | Überschriften, Landmarks, Listen, Tabellen |
| A ARIA | Elemente mit `role` oder `aria-*` (Live-Regionen, `aria-hidden`, `aria-invalid` …) |

**Ansichten** im Bereich „Barrierefreiheit“:

- **Übersicht** – Anzahl je Kategorie und Stand der 55 WCAG-Kriterien
  (Verstoß · Prüfen · automatisch ohne Befund · nicht anwendbar · nur manuell)
- **Details** – alle Befunde gruppiert; Klick markiert das Element in der
  Vorschau und zeigt die **Referenz**: Beschreibung, „So beheben“, HTML des
  Elements, zugehörige WCAG-Kriterien mit Link auf „Understanding WCAG 2.2“
- **Reihenfolge** – Tab-Reihenfolge mit Rolle und zugänglichem Namen,
  optional als Nummern auf der Seite (2.4.3)
- **Struktur** – Gliederung der Überschriften (übersprungene Ebenen und leere
  Überschriften markiert) und Landmarks
- **Kontrast** – alle geprüften Texte mit Farben und Verhältnis; Kontrastrechner
  mit Farbwählern und Bewertung für normalen Text (4,5:1), großen Text (3:1)
  und Bedienelemente/Grafiken (1.4.11, 3:1)
- **Kriterien** – alle Erfolgskriterien A/AA mit Stufe, Status und den
  zugeordneten axe-Regeln

Dazu wie bei WAVE **Styles aus** (Seite ohne CSS – zeigt die Lesereihenfolge)
und **Graustufen** (Information nur über Farbe? → 1.4.1), außerdem
**Bericht** (Markdown für die aktuelle Seite). Mit **automatisch** wird jede
neue Vorschau sofort geprüft. In „Gesamtes Theme prüfen“ zählt die Spalte
**WCAG-Verstöße** die verletzten Kriterien je Seite/Mail.

Bei E-Mails sind die seitenbezogenen Regeln (Seitentitel, Landmarks, H1,
`lang` am `<html>`) ausgeschaltet; der Text-Teil einer Mail wird nicht
geprüft.

> **Wichtig:** Automatische Prüfungen erkennen nur einen Teil der
> WCAG-Anforderungen – das gilt für WAVE genauso wie für axe-core. „Ohne
> Befund“ bedeutet nicht „barrierefrei“. Die
> Kriterienliste zeigt, was zusätzlich von Hand zu prüfen ist – z. B.
> Tastaturbedienung (2.1.1), sichtbarer Fokus (2.4.7), Umbruch bei 320 px
> (1.4.10), Textabstände (1.4.12), Fehlermeldungen und -vorschläge (3.3.1,
> 3.3.3) und barrierefreie Anmeldung ohne kognitiven Test (3.3.8).

Die Vorschau wird nur für die Darstellung verändert (Symbole liegen in einer
eigenen Ebene); Deaktivieren des Moduls stellt sie vollständig wieder her.

---

## Module aktivieren, deaktivieren, entfernen

Zusatzfunktionen wie die Barrierefreiheitsprüfung sind **Module**. Knopf
**Module** oben rechts:

- **aktiv** an/aus – blendet das Modul sofort ein oder aus, ohne Neuladen.
- **Entfernen** – das Modul wird nicht mehr geladen und vom Emulator nicht
  mehr ausgeliefert. **Wiederherstellen** holt es zurück.

Der Zustand steht in `modules-state.json` neben `scenario.json` (bei der
Mac-App im Arbeitsordner) und gilt auch nach einem Neustart.

Für Entwickler – Modul **endgültig aus dem Quellcode löschen**: Ordner
`src/main/resources/ui/modules/<id>/` und den Eintrag in
`src/main/resources/ui/modules/modules.json` entfernen. Der Emulator läuft
ohne das Modul unverändert weiter (geprüft). Ein neues Modul ist ein Ordner
mit `module.js` (meldet sich mit `KCEMU.register({...})` an, Schnittstelle
am Anfang des Abschnitts „Module“ in `ui/app.js` beschrieben) und optional
`module.css`.

---

## Szenario (simulierte Realm-Einstellungen)

`scenario.json` beschreibt, was im Server die Admin-Konsole festlegt:

```jsonc
{
  "loginTheme": "beispiel",            // Realm settings → Themes
  "emailTheme": "beispiel",
  "acceptLanguage": "de-DE,de;q=0.9",  // Browser des simulierten Nutzers
  "realm": {
    "name": "demo", "displayName": "Beispiel GmbH",
    "registrationAllowed": true, "resetPasswordAllowed": true, "rememberMe": true,
    "loginWithEmailAllowed": true, "registrationEmailAsUsername": false,
    "internationalizationEnabled": true, "supportedLocales": ["de", "en"], "defaultLocale": "de",
    "passwordPolicy": "length(12) and digits(1) and notUsername",
    "darkMode": true,
    "localizationTexts": { "de": { "loginAccountTitle": "Hallo!" } }   // Realm settings → Localization
  },
  "user":   { "username": "max.mustermann", "email": "…", "firstName": "Max", "lastName": "Mustermann", "otpDevices": ["Smartphone"] },
  "client": { "clientId": "portal", "name": "Kundenportal", "baseUrl": "https://portal.example.org",
              "attributes": { "login_theme": "" } },                     // Client-spezifisches Login-Theme
  "identityProviders": [ { "alias": "google", "providerId": "google", "displayName": "Google" } ],
  "userProfile": null   // oder die JSON-Konfiguration aus Realm settings → User profile → JSON editor
}
```

Die User-Profile-Konfiguration wird wie im Server geprüft und angewendet; die
Registrierungs- und Profilseiten zeigen dann genau die konfigurierten Felder,
Gruppen, Annotationen und Validierungsfehler.

---

## Eigenes Theme anlegen

Im Ordner `themes/beispiel` liegt ein Ausgangspunkt:

```
themes/beispiel/
├── login/
│   ├── theme.properties          parent=keycloak.v2, eigene CSS-Datei
│   ├── messages/messages_de.properties   nur abweichende Texte
│   └── resources/css/beispiel.css        Markenfarben über PatternFly-Variablen
└── email/
    ├── theme.properties          parent=keycloak
    ├── html/template.ftl         gemeinsames Mail-Layout (Tabellen, Inline-Stile)
    └── messages/messages_de.properties
```

Für einen neuen Entwurf den Ordner kopieren und umbenennen, im Szenario
`loginTheme`/`emailTheme` setzen (oder oben in der Oberfläche auswählen).
Hinweise:

- `styles=` ersetzt den Wert des Parents vollständig – soll dessen CSS
  weiter gelten, muss sie mit aufgeführt werden (`styles=css/styles.css css/eigen.css`).
- Templates nur dann kopieren, wenn das Markup wirklich anders sein muss;
  Texte über `messages_*.properties`, Aussehen über CSS ändern. Eigene
  Template-Kopien müssen bei jedem Keycloak-Update nachgezogen werden.
- Der fertige Ordner wird unverändert nach `/opt/keycloak/themes/<name>`
  kopiert (oder als JAR mit `META-INF/keycloak-themes.json` bereitgestellt).

---

## Prüfung auf der Kommandozeile (CI)

```bash
java -jar target/keycloak-theme-emulator.jar --check --locale de
```

rendert alle Seitenvarianten und Mails mit den Themes aus dem Szenario,
listet Fehler und Warnungen und endet mit Exit-Code 1, wenn mindestens ein
Fehler gefunden wurde.

---

## Keycloak-Version wechseln

Die Darstellung hängt von der Keycloak-Version ab (Templates, Texte,
Beans). Voreingestellt ist **26.8.1**. Für eine andere Server-Version:

```bash
mvn -q package -Dkeycloak.version=26.4.7
```

FreeMarker und alle übrigen Bibliotheken kommen dann aus dem
`keycloak-parent` derselben Version. Getestet sind **26.8.1** und **26.4.7**;
bei anderen Versionen können sich Schnittstellen geändert haben – der Build
meldet das beim Kompilieren.

Weil die mitgelieferten Themes der jeweiligen Version verwendet werden,
zeigen sich auch deren Eigenheiten. Beispiel: Unter 26.4.7 meldet die
Prüfung bei `org-invite.ftl`, dass der Link im Text-Teil HTML-maskiert ist
(`&amp;`, `&#61;`) – das Original-Template dieser Version wendet
`kcSanitize` auf den Text-Teil an; in 26.8.1 tritt das nicht auf.

---

## Grenzen

- **Account-Konsole und Admin-Konsole** sind React-Anwendungen
  (keine FreeMarker-Templates) und werden nicht emuliert. Das
  Login-Theme deckt alle Formularseiten der Nutzerverwaltung ab, die
  Nutzer im Anmeldeablauf sehen.
- Nicht im Katalog: WebAuthn/Passkeys, Auswahl des Anmeldeverfahrens,
  OAuth-Zustimmung, X.509, SAML-Post-Formular, Wiederherstellungscodes,
  „E-Mail ändern“ (Preview-Feature), IdP-Profilprüfung, Organisationsauswahl.
- Seiten laufen ohne echten Authentifizierungs-Flow (`AuthenticationFlowContext`
  ist leer). Template-Teile, die davon abhängen (z. B. `auth.showUsername()`,
  „Anderes Verfahren wählen“), erscheinen deshalb nicht.
- Eigene Java-Erweiterungen (eigene Authenticatoren, eigene
  FreeMarker-Methoden) sind nicht eingebunden.
- `${url.resourcesUrl}` und Links zeigen auf den Emulator
  (`http://127.0.0.1:8899/…`); im Server ist das die Frontend-URL von Keycloak.
- Feature-Flags entsprechen der Voreinstellung von Keycloak.

Changelog: [CHANGELOG.md](./CHANGELOG.md)
