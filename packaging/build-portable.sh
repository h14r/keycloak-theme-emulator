#!/usr/bin/env bash
# Baut die portable Fassung (ZIP): entpacken, Startdatei doppelklicken, im Browser nutzen.
# Läuft auf macOS, Windows und Linux; Voraussetzung beim Nutzer: Java 21.
#   packaging/build-portable.sh   → target/portable/Keycloak-Theme-Emulator-<version>-portable.zip
set -euo pipefail
cd "$(dirname "$0")/.."

VERSION="$(mvn -q help:evaluate -Dexpression=project.version -DforceStdout)"
mvn -q -B package

NAME="Keycloak-Theme-Emulator-${VERSION}"
OUT="target/portable/${NAME}"
rm -rf target/portable
mkdir -p "$OUT/app"
cp target/keycloak-theme-emulator.jar "$OUT/app/"
cp -r target/lib "$OUT/app/lib"
cp -r themes "$OUT/themes"
cp scenario.json "$OUT/"
cp packaging/portable/start-mac.command packaging/portable/start-linux.sh packaging/portable/start-windows.bat \
   packaging/portable/LIESMICH.txt "$OUT/"
chmod +x "$OUT/start-mac.command" "$OUT/start-linux.sh"

(cd target/portable && zip -qr "${NAME}-portable.zip" "${NAME}")
ls -la target/portable
