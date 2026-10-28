#!/usr/bin/env bash
# Baut das installierbare Paket mit jpackage (enthält eine eigene Java-Laufzeit).
#   packaging/build-installer.sh dmg        – macOS (nur auf einem Mac ausführbar)
#   packaging/build-installer.sh msi        – Windows (nur unter Windows, z. B. Git Bash; braucht WiX Toolset 3)
#   packaging/build-installer.sh app-image  – nur das App-Verzeichnis (zum Testen, jedes System)
# Voraussetzung: JDK 21 (mit jpackage) und Maven.
#
# Windows: Installation ohne Administratorrechte ins Benutzerprofil, Startmenü-Eintrag und
# Desktop-Verknüpfung. Die feste Upgrade-UUID sorgt dafür, dass eine neuere .msi die
# installierte Version ersetzt (Update), statt sie daneben zu installieren.
#
# Signieren (optional, nur macOS) – damit entfällt die Gatekeeper-Warnung
# „Apple konnte nicht überprüfen …“, sobald die .dmg zusätzlich notarisiert ist:
#   MAC_SIGNING_NAME="Firma GmbH (TEAMID1234)"   Name aus dem Zertifikat
#                                                „Developer ID Application: …“
#   MAC_SIGNING_KEYCHAIN=/pfad/zum.keychain-db   optional, sonst Standard-Schlüsselbund
set -euo pipefail

TYPE="${1:-dmg}"
cd "$(dirname "$0")/.."

VERSION="$(mvn -q help:evaluate -Dexpression=project.version -DforceStdout)"
mvn -q -B package

rm -rf target/dist target/installer
mkdir -p target/dist
cp target/keycloak-theme-emulator.jar target/dist/
cp -r target/lib target/dist/lib

ARGS=(
  --type "$TYPE"
  --name "Keycloak Theme Emulator"
  --app-version "$VERSION"
  --vendor "prototypes"
  --description "Vorschau und Prüfung von Keycloak-Login- und E-Mail-Themes"
  --input target/dist
  --main-jar keycloak-theme-emulator.jar
  --main-class kcemu.Main
  --java-options "-Dfile.encoding=UTF-8"
  --dest target/installer
)
if [[ "$(uname)" == "Darwin" ]]; then
  ARGS+=(--mac-package-identifier de.prototypes.kcemu --mac-package-name "KC Theme Emu")
  if [[ -n "${MAC_SIGNING_NAME:-}" ]]; then
    # jpackage signiert App und .dmg mit Hardened Runtime und den für Java nötigen Entitlements
    ARGS+=(--mac-sign --mac-signing-key-user-name "$MAC_SIGNING_NAME")
    if [[ -n "${MAC_SIGNING_KEYCHAIN:-}" ]]; then
      ARGS+=(--mac-signing-keychain "$MAC_SIGNING_KEYCHAIN")
    fi
  fi
fi

case "$(uname -s)" in
  MINGW* | MSYS* | CYGWIN*)
    ARGS+=(
      --win-per-user-install
      --win-dir-chooser
      --win-menu
      --win-menu-group "Keycloak Theme Emulator"
      --win-shortcut
      --win-upgrade-uuid "8043fcab-f3f7-40d7-b00d-5df3bd8334bc"
    )
    ;;
esac

jpackage "${ARGS[@]}"
ls -la target/installer
