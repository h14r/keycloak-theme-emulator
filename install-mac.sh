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
#
# Optionen:  --dmg <datei>  vorhandene .dmg statt Download (z. B. zum Testen)   --no-start  nicht starten
#
# Ältere Versionen: Eine laufende Instanz wird beendet, alle Kopien der App in /Programme und
# ~/Programme werden entfernt und durch die neue ersetzt – es bleibt genau eine Version. Eigene
# Themes und Einstellungen liegen im Arbeitsordner ~/Keycloak Theme Emulator und bleiben erhalten.

REPO="h14r/keycloak-theme-emulator"
APP="Keycloak Theme Emulator.app"
EXE="Keycloak Theme Emulator"
DMG=""
START=1
while [[ $# -gt 0 ]]; do
  case "$1" in
    --dmg) DMG="$2"; shift 2 ;;
    --no-start) START=0; shift ;;
    *) echo "Unbekannte Option: $1" >&2; exit 2 ;;
  esac
done

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

if [[ -z "$DMG" ]]; then
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
  echo "Lade $(basename "$URL") …"
  curl -fL --progress-bar -o "$TMP/emulator.dmg" "$URL"
  DMG="$TMP/emulator.dmg"
fi

MOUNT="$(hdiutil attach -nobrowse -readonly "$DMG" | tail -1 | cut -f3-)"
NEW="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleShortVersionString' "$MOUNT/$APP/Contents/Info.plist" 2>/dev/null || echo '?')"

DEST="/Applications"
if [[ ! -w "$DEST" ]]; then
  DEST="$HOME/Applications"
  mkdir -p "$DEST"
fi

# laufende Instanz beenden (sonst ersetzt man ein geöffnetes Programm)
if pgrep -f "/$APP/Contents/MacOS/" > /dev/null 2>&1; then
  echo "Beende laufenden Emulator …"
  pkill -f "/$APP/Contents/MacOS/" || true
  sleep 2
fi

# ältere Installationen suchen und entfernen
for dir in /Applications "$HOME/Applications"; do
  for old in "$dir"/Keycloak\ Theme\ Emulator*.app; do
    [[ -d "$old" ]] || continue
    exe="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleExecutable' "$old/Contents/Info.plist" 2>/dev/null || true)"
    [[ "$exe" == "$EXE" ]] || continue
    ver="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleShortVersionString' "$old/Contents/Info.plist" 2>/dev/null || echo '?')"
    if rm -rf "$old" 2>/dev/null; then
      echo "Vorhandene Version $ver entfernt: $old"
    else
      echo "Hinweis: $old (Version $ver) konnte nicht entfernt werden – bitte von Hand löschen." >&2
    fi
  done
done

echo "Installiere Version $NEW nach $DEST …"
cp -R "$MOUNT/$APP" "$DEST/"
hdiutil detach -quiet "$MOUNT"
echo "Eigene Themes und Einstellungen: $HOME/Keycloak Theme Emulator (unverändert übernommen)"

if [[ "$START" == 1 ]]; then
  echo "Fertig. Starte den Emulator …"
  open "$DEST/$APP"
else
  echo "Fertig."
fi
