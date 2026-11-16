# Implementation status

Build machine: GitHub Actions (`.github/workflows/android.yml`). Locally, only the JVM tests run reliably:
`./gradlew --no-daemon :core:model:test :core:format:test :core:mtz:test`.

Only what is listed as built exists in the code. Nothing is shown in the app before it works.

| Stage | State |
|---|---|
| 0a Gradle skeleton (AGP 8.7.3, Gradle 8.11.1, Kotlin 2.0.21, version catalog) | done |
| 0b Signing (keystore.properties / WD_* env, loud warning when absent) + CI workflow (build / emulator smoke: screenshots, monkey 1500, tap-every-button instrumented test / `[release]` publish) | running on GitHub Actions. First run: build green, smoke red (Haze crash, now fixed) |
| 1 Design system (Onest + JetBrains Mono, dark/light tokens, Haze 1.1.1 glass with fallback tint, own line icons, components) + app shell (5-tab glass bar, deep links, en + ru) | done |
| 2 core/model | done + tests |
| 3 core/format (.wdesign, safe ZIP limits, generic image ZIP, .wdbackup reader/writer) | done + tests |
| 4a/4b core/mtz (builder, MAML lockscreen + miwallpaper, theme_values overrides, XXE-safe reader, validator) | done + tests |
| 5 Data / device / render | partial: file-based project store (not Room, see ARCHITECTURE.md), DataStore settings, DeviceInspector + XiaomiIntents, wallpaper/icon/lock/preview renderers, MTZ exporter, WallpaperApplier |
| 6 Editor kit + Theme Studio (canvas drag/resize editor) | not started |
| 7 Import / Export | partial: import of .wdesign / .mtz / image .zip / .wdbackup with preview. Not yet: .wdesign export UI, share, QR, backup creation UI |
| 8 Widget Studio + AppWidgets + weather | not started |
| 9 Icon Studio + Wallpaper Studio | not started (icons are styled for MTZ export; wallpapers apply from Home/Discover/Lock Studio) |
| 10 Lock Screen Studio | built as a property editor (templates, clock, date, decorations, undo/redo, autosave, live preview, Apply badges). Not yet: on-canvas drag/resize gestures |
| 11 Xiaomi Lab + MTZ Export UI | done |
| 12 Live wallpaper, Control Center Studio, QS tile | not started |
| 13 W Design Launcher | not started |
| 14 Home, Discover, My Designs, Settings | done (sort, rename, backup/restore and font/palette tools not yet) |
| 15 W-Design.apk in repo root | **not yet: no APK is in git.** GitHub Actions is the build machine (the local sandbox has ~470 MB RAM and cannot finish R8). The publish job commits W-Design.apk to the branch root on a `[release]` commit; it reaches main when the PR is merged |

Feature modules planned but not yet in `settings.gradle.kts`: editor-kit, theme-studio, create, importexport, launcher, widgets, livewallpaper.

## Crash safety
* Every click handler runs in `SafeScope` and every screen effect in `SafeLaunchedEffect`. Errors become a snackbar and are written to `CrashLog`; a real crash is still delegated to the system handler.
* Settings shows the last error report (Share / Clear) when one exists.
* CI runs `TapEverythingTest`, which taps every clickable element on 8 screens, plus monkey 1500 on the minified release APK.

## Verified facts used
* Haze 1.1.1 (`haze-android-1.1.1-sources.jar`): `Modifier.haze(state)`, `Modifier.hazeChild(state, style, block)`, `HazeChildScope` vars
  `blurEnabled, alpha, style, mask, backgroundColor, tints, blurRadius, noiseFactor, fallbackTint, progressive`, `@ExperimentalHazeApi inputScale`;
  the shape overload is `@Deprecated`. `HazeDefaults.blurEnabled()` is true only on **API ≥ 32**, so `fallbackTint` is always set.
  **`backgroundColor` is mandatory**: `HazeChildNode.kt:327` does `require(bg.isSpecified)` on every draw (resolved scope → style →
  `LocalHazeStyle`). Leaving it unset crashed every screen with the glass bar in CI run 43866896015. `glass()` now sets the opaque surface colour.
  **`hazeChild` may not be a descendant of `Modifier.haze`** (`HazeChildNode.kt:277`, `require(!state.contentDrawing)`): top bars inside the
  NavHost (the haze source) crashed in CI run 43898142925. Inside the source, `glass()` now draws the opaque fallback glass colour; only
  siblings of the NavHost (the bottom bar) blur.
* MTZ / MAML facts and sources: see docs/MTZ.md.
