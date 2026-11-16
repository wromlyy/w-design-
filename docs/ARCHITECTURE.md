# Architecture

```
app ──> features/{home, discover, designs, settings, xiaomilab, lockscreen}
              │            (home also uses xiaomilab's capability labels)
              ├──> design-system  (tokens, type, glass, components, rendered previews)
              └──> core/data ──api──> core/{model, format, mtz, device, render}
core/model, core/format, core/mtz: pure JVM (unit-tested without Android)
```

* **core/model**: serializable `ThemeSpec` and everything in it, history, palettes, share links, HyperOS parsing, the compatibility rules, presets and lock templates.
* **core/format**: `.wdesign` / `.wdbackup` writers and readers on top of `SafeZipReader` (byte-counted limits, zip-slip and executable rejection).
* **core/mtz**: MTZ builder, MAML generators, reader, validator. It never renders: the MAML generator returns `MamlAssetRequest`s.
* **core/render** (Android Canvas): renders wallpapers, icons, lock screens and clock digits, and fulfils those requests in `MtzExporter`.
* **core/device**: `DeviceInspector` (system properties via reflection, then `getprop`) and guarded Xiaomi intents.
* **core/data**: `ProjectStore`, `SettingsStore` (DataStore), `WallpaperApplier`, and the lazy `WD.services` locator created in `Application.onCreate` without I/O.

## Build configuration
Feature modules get the Android library + Compose setup and common dependencies from the root `subprojects {}` block. A
`gradle.projectsEvaluated` guard fails the build if any `:features:*` module lacks `com.android.library`. This catches the earlier failure,
where a `/*` inside a KDoc silently commented out the whole block. Core modules and the design system have their own `android {}` blocks.

## Deviation: file store instead of Room
The plan specified Room. Projects are instead stored as `filesDir/projects/<id>/project.json` plus an `assets/` folder:
* **Why:** a design is a single JSON document with binary assets beside it, so a file per project matches the `.wdesign` export one-to-one. Writes are
  atomic (temp file + rename) and deletion goes to a trash folder, which gives Undo. Dropping Room and KSP also cut the build cost noticeably on the
  low-memory build machine.
* **Same API:** load/observe (StateFlow), save, create, duplicate (copies assets), rename, favourite, delete/undo/purge, import commit and integrity check.
* **When Room is still needed:** the launcher layout (stage 13) and widget bindings (stage 8) are relational. They will add Room tables then,
  without moving project documents.
