# Installiert die aktuelle Version des Keycloak Theme Emulators unter Windows.
#
#   powershell -ExecutionPolicy Bypass -c "irm https://raw.githubusercontent.com/h14r/keycloak-theme-emulator/main/install-windows.ps1 | iex"
#
# Laedt den Windows-Installer (.msi) aus dem neuesten Release, installiert ihn fuer den
# angemeldeten Benutzer (keine Administratorrechte noetig) und startet die App. Per PowerShell
# geladene Dateien tragen keine Internet-Markierung ("Mark of the Web") - Windows zeigt deshalb
# die SmartScreen-Meldung "Der Computer wurde durch Windows geschuetzt" nicht an, die beim
# unsignierten Installer nach einem Browser-Download sonst erscheint.
#
# Fuer Updates denselben Befehl erneut ausfuehren: Die neue Version ersetzt die alte, der
# Arbeitsordner mit den eigenen Themes bleibt erhalten.
#
# Aeltere Versionen: Der Installer ersetzt sie selbst (gleicher Upgrade-Code). Das Skript
# beendet vorher eine laufende Instanz und prueft danach, dass nur noch die neue Version
# installiert ist; uebrig gebliebene Eintraege werden deinstalliert.
#
# Optionen (beim Aufruf als Datei):  -Msi <pfad>  vorhandene .msi statt Download,  -NoStart
param(
    [string]$Msi = '',
    [switch]$NoStart
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
[Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12

$Repo = 'h14r/keycloak-theme-emulator'
$Name = 'Keycloak Theme Emulator'

if (-not $Msi) {
    Write-Host 'Suche neueste Version ...'
    $release = Invoke-RestMethod -UseBasicParsing "https://api.github.com/repos/$Repo/releases/latest"
    $asset = $release.assets | Where-Object { $_.name -like '*-windows-x64.msi' } | Select-Object -First 1
    if (-not $asset) {
        throw "Im neuesten Release ($($release.tag_name)) gibt es keinen Windows-Installer: https://github.com/$Repo/releases"
    }
    $Msi = Join-Path $env:TEMP $asset.name
    Write-Host "Lade $($asset.name) ..."
    Invoke-WebRequest -UseBasicParsing $asset.browser_download_url -OutFile $Msi
}
$Msi = (Resolve-Path $Msi).Path
$NewVersion = if ([IO.Path]::GetFileName($Msi) -match '(\d+\.\d+\.\d+)') { $Matches[1] } else { '' }

# installierte Versionen (pro Benutzer und fuer alle Benutzer) aus der Liste "Apps"
function Get-Installed {
    $keys = 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\*',
            'HKLM:\Software\Microsoft\Windows\CurrentVersion\Uninstall\*',
            'HKLM:\Software\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall\*'
    Get-ItemProperty $keys -ErrorAction SilentlyContinue |
        Where-Object { $_.DisplayName -eq $Name } |
        Select-Object DisplayName, DisplayVersion, PSChildName, UninstallString, InstallLocation
}

$before = @(Get-Installed)
foreach ($i in $before) { Write-Host "Vorhanden: Version $($i.DisplayVersion)" }

$running = Get-Process -Name $Name -ErrorAction SilentlyContinue
if ($running) {
    Write-Host 'Beende laufenden Emulator ...'
    $running | Stop-Process -Force
    Start-Sleep -Seconds 2
}

Write-Host 'Installiere fuer den aktuellen Benutzer ...'
$log = Join-Path $env:TEMP 'keycloak-theme-emulator-install.log'
$p = Start-Process msiexec.exe -ArgumentList "/i `"$Msi`" /passive /norestart /l*v `"$log`"" -Wait -PassThru
if ($p.ExitCode -ne 0 -and $p.ExitCode -ne 3010) {
    throw "Installation fehlgeschlagen (msiexec $($p.ExitCode)), Protokoll: $log"
}

# Reste aelterer Versionen entfernen, damit nur die neue installiert bleibt
foreach ($i in @(Get-Installed)) {
    if ($NewVersion -and $i.DisplayVersion -and -not $i.DisplayVersion.StartsWith($NewVersion)) {
        if ($i.PSChildName -match '^\{[0-9A-Fa-f-]+\}$') {
            Write-Host "Deinstalliere uebrig gebliebene Version $($i.DisplayVersion) ..."
            Start-Process msiexec.exe -ArgumentList "/x $($i.PSChildName) /passive /norestart" -Wait | Out-Null
        } else {
            Write-Host "Hinweis: Version $($i.DisplayVersion) bitte unter Einstellungen > Apps deinstallieren."
        }
    }
}
$after = @(Get-Installed)
Write-Host ("Installiert: " + (($after | ForEach-Object { $_.DisplayVersion }) -join ', '))
Write-Host "Eigene Themes und Einstellungen: $(Join-Path $HOME $Name) (unveraendert uebernommen)"

$exe = Get-ChildItem $env:LOCALAPPDATA, $env:ProgramFiles -Recurse -Depth 3 -Filter "$Name.exe" -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $exe) {
    throw "Installiert, aber $Name.exe nicht gefunden. Protokoll: $log"
}
Write-Host "Installiert: $($exe.FullName)"
Write-Host "Start ueber das Startmenue oder die Desktop-Verknuepfung '$Name'."

if (-not $NoStart) {
    Start-Process $exe.FullName
}
