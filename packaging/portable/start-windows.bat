@echo off
rem Doppelklick startet den Emulator und oeffnet die Oberflaeche im Browser.
cd /d "%~dp0"
where java >nul 2>nul
if errorlevel 1 (
  echo Java 21 oder neuer wird benoetigt, z. B. https://adoptium.net/de/temurin/releases/?version=21
  pause
  exit /b 1
)
echo Keycloak Theme Emulator startet - Beenden durch Schliessen dieses Fensters.
java -jar app\keycloak-theme-emulator.jar --themes themes --scenario scenario.json --open
if errorlevel 1 pause
