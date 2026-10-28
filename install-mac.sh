#!/bin/bash
# Installiert die aktuelle Version des Keycloak Theme Emulators auf dem Mac.
#
#   curl -fsSL https://raw.githubusercontent.com/h14r/keycloak-theme-emulator/main/install-mac.sh | bash
#
# Lädt die passende .dmg (Apple Silicon oder Intel) aus dem neuesten Release, kopiert die App
# nach /Programme (bzw. ~/Programme ohne Schreibrecht) und startet sie. Per curl geladene
# Dateien tragen keine Quarantäne-Markierung – macOS zeigt deshalb nicht die Meldung
# „Apple konnte nicht überprüfen …“, die bei der nicht notarisierten App sonst erscheint.
set -euo pipefail

REPO="h14r/keycloak-theme-emulator"
APP="Keycloak Theme Emulator.app"

case "$(uname -m)" in
  arm64) ARCH=arm64 ;;
  x86_64) ARCH=x64 ;;
  *) echo "Nicht unterstützter Prozessor: $(uname -m)" >&2; exit 1 ;;
esac

echo "Suche neueste Version für $ARCH …"
URL="$(curl -fsSL "https://api.github.com/repos/$REPO/releases/latest" \
  | grep -o "https://[^\"]*-${ARCH}\.dmg" | head -1)"
if [[ -z "$URL" ]]; then
  echo "Keine .dmg für $ARCH im neuesten Release gefunden: https://github.com/$REPO/releases" >&2
  exit 1
fi

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
echo "Lade $(basename "$URL") …"
curl -fL --progress-bar -o "$TMP/emulator.dmg" "$URL"

MOUNT="$(hdiutil attach -nobrowse -readonly "$TMP/emulator.dmg" | tail -1 | cut -f3-)"
DEST="/Applications"
if [[ ! -w "$DEST" ]]; then
  DEST="$HOME/Applications"
  mkdir -p "$DEST"
fi
echo "Installiere nach $DEST …"
rm -rf "$DEST/$APP"
cp -R "$MOUNT/$APP" "$DEST/"
hdiutil detach -quiet "$MOUNT"

echo "Fertig. Starte den Emulator …"
open "$DEST/$APP"
