# W Design

A native Android app (Kotlin, Jetpack Compose, minSdk 26, target 35) for personalising Xiaomi HyperOS phones: it builds and checks
**MTZ theme packages on the phone**, edits lock screens, applies wallpapers and explains, per device, what HyperOS 1–4 really lets an app change.
Everything works offline. W Design also has its own validated `.wdesign` format.

## What works now
| Area | State |
|---|---|
| MTZ export (wallpapers ≥2400 px, 168 px icons for installed apps, MAML lock screen, previews, optional experimental modules, reference-MTZ colour keys) + on-device validator | built |
| MTZ import (wallpapers + icons → editable project, XXE-safe) | built |
| Lock Screen Studio: 7 templates, clock (text / stacked / dot matrix / outline / image digits, 12/24/system), date presets, glass panels / shapes / text, undo/redo, autosave, live preview with fingerprint and notification ghosts, Apply sheet that labels each part | built |
| Wallpaper apply (home / lock / both) through WallpaperManager; the lock wallpaper carries decorations, never the clock | built |
| Xiaomi Lab: device snapshot, HyperOS/MIUI version + region, support matrix with reasons and working buttons, version guide | built |
| Home, Discover (24 presets), Create, My Designs (search, filters, favourite, duplicate, delete + undo), Import (.wdesign/.mtz/.zip/.wdbackup), Settings | built |
| Theme Studio canvas editor, Widget/Icon/Wallpaper/Control Center studios, real AppWidgets, live wallpaper, launcher, QS tile, share/QR | **not yet** (see docs/STATUS.md) |

## Honest limitations
* MTZ files are **unofficial — not verified or signed by Xiaomi**. China ROMs usually offer Themes → Import; Global ROMs usually need Xiaomi's PC Theme Editor; some builds revert non-store themes. No root, no patched Themes app.
* Apps cannot restyle the system lock clock or replace Control Center (system signature). W Design shows the target values for the HyperOS editor, or exports the full clock as MAML inside an MTZ.
* W Design never touches the keyguard, PIN or biometrics; MAML lock screens use the documented `ExternCommand unlock`, so the system bouncer still runs.

## Build
```bash
./tools/setup-sandbox.sh            # fresh Linux box: JDK 17 + Android SDK 35
./gradlew :core:model:test :core:format:test :core:mtz:test
./gradlew assembleRelease           # signing: docs/SIGNING.md
```
CI (`.github/workflows/android.yml`): build + tests + apksigner/aapt2 → emulator smoke (deep-link screenshots, 800 monkey events) → on a `[release]` commit, commits `W-Design.apk` to the repo root and creates Release v1.0.0.

Docs: [MTZ](docs/MTZ.md) · [Signing](docs/SIGNING.md) · [Status](docs/STATUS.md). Licence: Apache-2.0. Fonts: Onest, JetBrains Mono (SIL OFL, docs/licenses).
