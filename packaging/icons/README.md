# Keycloak Theme Emulator – Icons

Variante A1 „Fenster im Fenster“. Farben: Grund #172033, Emulator #334155, Akzent #3B82F6, Prüfhaken #F59E0B.

Kleine Größen sind eigens vereinfacht, nicht nur verkleinert:
16 px → `svg/icon-16.svg`, 24–32 px → `svg/icon-32.svg`, 48–64 px → `svg/icon-64.svg`, ab 128 px → `svg/icon.svg`.

## Ordner

| Ordner | Inhalt | Verwendung |
|---|---|---|
| `web/` | `favicon.ico` (16/32/48), `favicon.svg`, `favicon-16x16.png`, `favicon-32x32.png`, `apple-touch-icon.png` (180, randlos eckig – iOS rundet selbst), `icon-192.png`, `icon-512.png`, `icon-maskable-512.png`, `site.webmanifest`, `head-snippet.html` | Dateien in den Web-Root kopieren, Schnipsel in den `<head>` |
| `windows/` | `app.ico` (16, 24, 32, 48, 64, 128, 256) | Programm-/Fenstersymbol, z. B. Electron `win.icon`, Tauri `bundle.icon`, .NET `<ApplicationIcon>` |
| `macos/` | `icon.icns`, `icon.iconset/` (alle zehn Einträge), `icon-1024.png` | `icon.icns` direkt verwenden (Electron `mac.icon`, Tauri `bundle.icon`). `icon-1024.png` für Xcode-Asset-Kataloge |
| `png/` | 16, 24, 32, 48, 64, 128, 256, 512, 1024 px | Linux, Werkzeuge, die PNGs erwarten (z. B. Electron `BrowserWindow({ icon })`) |
| `svg/` | Vorlagen | zum Weiterbearbeiten |

## Hinweise

- Das macOS-Icon folgt dem Apple-Raster: Körper 824 px auf 1024 px Fläche, mit leichtem Schatten. Die übrigen Icons sind randlos.
- `icon.icns` ist mit Pillow erzeugt und enthält die Retina-Einträge 16@2x bis 512@2x sowie 128, 256 und 512 in einfacher Auflösung; die 16- und 32-px-Einträge in einfacher Auflösung fehlen dort, macOS skaliert dann herunter. Wer sie braucht, baut die Datei auf einem Mac neu: `iconutil -c icns macos/icon.iconset`.
- Weder `icon.icns` noch `app.ico` sind auf einem Mac oder unter Windows getestet.
