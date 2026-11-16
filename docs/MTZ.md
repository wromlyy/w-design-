# MTZ in W Design

## Package layout (Xiaomi designer guide, zhuti.designer.xiaomi.com / zhuti.designer.intl.xiaomi.com)
An `.mtz` is a ZIP: `description.xml` (`<MIUI-Theme><title/><designer/><author/><version/><uiVersion/><description/>`), `wallpaper/default_wallpaper.jpg`,
`wallpaper/default_lock_wallpaper.jpg`, `preview/*.jpg`, and modules stored as **nested ZIPs without an extension**: `icons`, `lockscreen`, `miwallpaper`,
`com.android.systemui`, `framework-res`, `com.miui.home`, `clock_2x4` …

## What W Design generates
| Entry | Content |
|---|---|
| description.xml | XML-escaped metadata; uiVersion from settings (default `1`, community value) or from a reference MTZ |
| wallpaper/ | device aspect, at least 1080 wide and 2400 tall |
| icons | `res/drawable-xxhdpi/<package or activity>.png`, 168×168; optional experimental icon_background/mask/border |
| lockscreen | `advance/manifest.xml` + images. `<Lockscreen version="2" screenWidth="1080" useVariableUpdater="DateTime.Minute">`; y positions are `#screen_height*f`; clock = `<DateTime formatExp="ifelse(#time_format,'HH:mm','hh:mm')">` or rasterised `<Time src="clock/time.png">` twins with `visibility="#time_format"` / `"!#time_format"`; elements over the fingerprint area get `visibility="#fod_enable==0"`; unlock = full-screen Button with `<ExternCommand command="unlock" condition="max(#touch_begin_y-#touch_y,0)}=300"/>` (MAML uses `}=` for `>=`) |
| preview/ | preview_lockscreen_0 / preview_launcher_0 / preview_icons_0 |
| miwallpaper (experimental, off) | `<MiWallpaper>` gradient + animated circles |
| module/theme_values.xml | only with a reference MTZ: values of **existing** `<color name>` keys are rewritten byte-for-byte; nothing is invented |

Date formats are translated to the letters MAML renders reliably (y M d E H h m a + quoted literals); dropped letters are reported.

## Not generated, and why
AOD (only `aod_description.xml` is documented) · `clock_2x4` (root tag not verified) · fonts (licensing; Themes store only) · boot animation · sounds · custom password screens (forbidden by the audit).

## Unverified
`fontFamily="mipro-*"` names come from community themes; MAML falls back to the system font when a name is unknown.

## Security of imports
Streaming limits counted on real bytes (80 MB total for MTZ), zip-slip rejection, executables skipped by magic, DOCTYPE/ENTITY rejected anywhere in a document
before parsing, and entries staged to disk instead of kept in memory.
