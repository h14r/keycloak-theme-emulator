#!/bin/bash
# Doppelklick startet den Emulator und öffnet die Oberfläche im Browser.
cd "$(dirname "$0")"
if ! java -version 2>&1 | grep -qE 'version "(2[1-9]|[3-9][0-9])'; then
  echo "Java 21 oder neuer wird benötigt (z. B. https://adoptium.net/de/temurin/releases/?version=21)."
  read -r -p "Eingabetaste zum Schließen …"
  exit 1
fi
echo "Keycloak Theme Emulator startet – Beenden mit ctrl+C oder durch Schließen dieses Fensters."
exec java -jar app/keycloak-theme-emulator.jar --themes themes --scenario scenario.json --open
