package com.wdesign.core.model

import kotlinx.serialization.Serializable

/* ---------------------------------------------------------------------------------------------
 * The complete, serializable description of a W Design project. Every studio edits a part of
 * [ThemeSpec]; every exporter (.wdesign, MTZ, PNG, widgets, live wallpaper) reads from it.
 * Positions on canvases are fractions of the canvas (0..1) so a design reflows across devices.
 * ------------------------------------------------------------------------------------------- */

@Serializable
enum class DesignKind { THEME, LOCK_SCREEN, WIDGET, ICON_PACK, WALLPAPER, LIVE_WALLPAPER, CONTROL_CENTER }

@Serializable
enum class DesignSource { USER, IMPORTED, PRESET, MTZ_IMPORT }

@Serializable
data class DesignMeta(
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val author: String = "",
    val source: DesignSource = DesignSource.USER,
    val presetId: String? = null,
    val draft: Boolean = true,
    val favorite: Boolean = false,
    val tags: List<String> = emptyList(),
    val formatVersion: Int = 1,
)

@Serializable
data class DesignProject(
    val id: String,
    val name: String,
    val kind: DesignKind = DesignKind.THEME,
    val spec: ThemeSpec = ThemeSpec(),
    val meta: DesignMeta = DesignMeta(),
)

@Serializable
data class Palette(
    val primary: ArgbColor = ArgbColor.rgb(0x9CC9F0),
    val secondary: ArgbColor = ArgbColor.rgb(0x5E7891),
    val accent: ArgbColor = ArgbColor.rgb(0xF0C35A),
    val background: ArgbColor = ArgbColor.rgb(0x0D0F12),
    val surface: ArgbColor = ArgbColor.rgb(0x1E2228),
    val onSurface: ArgbColor = ArgbColor.rgb(0xF1F2F4),
    val onAccent: ArgbColor = ArgbColor.rgb(0x0D0F12),
) {
    fun all(): List<ArgbColor> = listOf(primary, secondary, accent, background, surface, onSurface, onAccent)
}

@Serializable
enum class AppearanceMode { DARK, LIGHT, SYSTEM }

/**
 * System font families available on Android. HyperOS maps "sans-serif" to MiSans, so the
 * sans entries preview as MiSans on Xiaomi devices.
 */
@Serializable
enum class SystemFont(val family: String, val weight: Int) {
    SANS("sans-serif", 400),
    SANS_THIN("sans-serif-thin", 100),
    SANS_LIGHT("sans-serif-light", 300),
    SANS_MEDIUM("sans-serif-medium", 500),
    SANS_BLACK("sans-serif-black", 900),
    SANS_CONDENSED("sans-serif-condensed", 400),
    SERIF("serif", 400),
    MONO("monospace", 400),
    SERIF_MONO("serif-monospace", 400),
    CASUAL("casual", 400),
    CURSIVE("cursive", 400),
}

@Serializable
data class GlassSpec(
    val blurDp: Float = 24f,
    val opacity: Float = 0.18f,
    val tint: ArgbColor = ArgbColor.White,
    val borderAlpha: Float = 0.22f,
)

@Serializable
data class AppearanceSpec(
    val mode: AppearanceMode = AppearanceMode.DARK,
    val glass: GlassSpec = GlassSpec(),
    val cornerRadiusDp: Float = 22f,
    val font: SystemFont = SystemFont.SANS,
    val fontWeight: Int = 400,
)

@Serializable
enum class WallpaperType { SOLID, GRADIENT, MESH, ABSTRACT, GEOMETRIC, PHOTO }

@Serializable
data class AssetRef(
    /** Path relative to the project's assets directory, e.g. "wallpapers/home.jpg". */
    val path: String,
    val mime: String = "image/jpeg",
)

@Serializable
data class CropSpec(val cx: Float = 0.5f, val cy: Float = 0.5f, val zoom: Float = 1f)

@Serializable
data class WallpaperSpec(
    val type: WallpaperType = WallpaperType.GRADIENT,
    val colors: List<ArgbColor> = listOf(ArgbColor.rgb(0x1B2A3A), ArgbColor.rgb(0x0D0F12), ArgbColor.rgb(0x3D5A73)),
    val angle: Float = 135f,
    val seed: Long = 7L,
    val complexity: Float = 0.5f,
    val grain: Float = 0.04f,
    val photo: AssetRef? = null,
    val crop: CropSpec = CropSpec(),
    val dim: Float = 0f,
    val blur: Float = 0f,
)

@Serializable
enum class TextAlign { START, CENTER, END }

@Serializable
enum class ClockFormat { FOLLOW_SYSTEM, H24, H12 }

@Serializable
enum class ClockRender { TEXT, STACKED, DOT_MATRIX, OUTLINE, IMAGE_DIGITS }

@Serializable
data class ClockSpec(
    val font: SystemFont = SystemFont.SANS_LIGHT,
    val weight: Int = 300,
    val sizeSp: Float = 88f,
    val color: ArgbColor = ArgbColor.White,
    val gradient: List<ArgbColor>? = null,
    /** Anchor x as a fraction of the screen width (interpreted with [align]). */
    val x: Float = 0.5f,
    /** Top of the clock as a fraction of the screen height. */
    val y: Float = 0.14f,
    val align: TextAlign = TextAlign.CENTER,
    val format: ClockFormat = ClockFormat.FOLLOW_SYSTEM,
    val render: ClockRender = ClockRender.TEXT,
    val visible: Boolean = true,
)

@Serializable
data class DateSpec(
    val visible: Boolean = true,
    /** java.text.SimpleDateFormat pattern, e.g. "EEEE, d MMMM". */
    val pattern: String = "EEEE, d MMMM",
    val sizeSp: Float = 18f,
    val color: ArgbColor = ArgbColor.White.withAlpha(0.85f),
    val x: Float = 0.5f,
    val y: Float = 0.10f,
    val align: TextAlign = TextAlign.CENTER,
)

@Serializable
enum class LockStyle { MINIMAL, LIQUID_GLASS, IOS_INSPIRED, NOTHING_INSPIRED, AMOLED, CLASSIC, CUSTOM }

@Serializable
data class FodArea(val x: Float = 0.5f, val y: Float = 0.86f, val r: Float = 0.075f)

@Serializable
data class LockScreenSpec(
    val style: LockStyle = LockStyle.MINIMAL,
    /** Optional dedicated lock wallpaper; when null the theme wallpaper is used. */
    val wallpaper: WallpaperSpec? = null,
    val clock: ClockSpec = ClockSpec(),
    val date: DateSpec = DateSpec(),
    val elements: List<CanvasElement> = emptyList(),
    val unlockHint: String = "",
    val fodArea: FodArea = FodArea(),
    val showFodGhost: Boolean = true,
    val showNotificationGhost: Boolean = true,
)

@Serializable
data class HomeLayoutSpec(
    val columns: Int = 4,
    val rows: Int = 6,
    val iconSizeDp: Float = 56f,
    val labels: Boolean = true,
    val dock: Boolean = true,
    val elements: List<CanvasElement> = emptyList(),
)

@Serializable
enum class IconMask { CIRCLE, SQUIRCLE, ROUNDED, TEARDROP, HEXAGON, NONE }

@Serializable
enum class IconFilter { NONE, MONO, TINT, DUOTONE }

@Serializable
data class IconOverride(
    val packageName: String,
    val activity: String? = null,
    val asset: AssetRef? = null,
    /** Name of a built-in glyph (see the icon glyph set in core/render). */
    val glyph: String? = null,
    val crop: CropSpec = CropSpec(),
)

@Serializable
data class IconStyleSpec(
    val mask: IconMask = IconMask.SQUIRCLE,
    val cornerPct: Float = 0.3f,
    val paddingPct: Float = 0.12f,
    val shadow: Boolean = true,
    val filter: IconFilter = IconFilter.NONE,
    val tint: ArgbColor = ArgbColor.rgb(0x9CC9F0),
    val background: ArgbColor? = ArgbColor.rgb(0x1E2228),
    val glass: Boolean = false,
    val overrides: List<IconOverride> = emptyList(),
)

@Serializable
enum class WidgetType { CLOCK, DATE, BATTERY, CALENDAR, TEXT, IMAGE, WEATHER, COMBO }

@Serializable
enum class WidgetBackground { SOLID, GLASS, IMAGE, NONE }

@Serializable
data class WidgetSpec(
    val id: String,
    val type: WidgetType = WidgetType.CLOCK,
    /** Cells as "WxH", one of 2x1, 2x2, 4x1, 4x2, 4x4. */
    val cells: String = "2x2",
    val backgroundKind: WidgetBackground = WidgetBackground.GLASS,
    val background: ArgbColor = ArgbColor.rgb(0x1E2228),
    val opacity: Float = 0.72f,
    val cornerDp: Float = 24f,
    val paddingDp: Float = 16f,
    val font: SystemFont = SystemFont.SANS_LIGHT,
    val textColor: ArgbColor = ArgbColor.White,
    val accent: ArgbColor = ArgbColor.rgb(0x9CC9F0),
    val text: String = "",
    val timeFormat: ClockFormat = ClockFormat.FOLLOW_SYSTEM,
    val datePattern: String = "EEE, d MMM",
    val image: AssetRef? = null,
    val units: String = "metric",
    val parts: List<WidgetType> = emptyList(),
) {
    val spanX: Int get() = cells.substringBefore('x').toIntOrNull()?.coerceIn(1, 4) ?: 2
    val spanY: Int get() = cells.substringAfter('x').toIntOrNull()?.coerceIn(1, 4) ?: 2

    companion object {
        val SIZES = listOf("2x1", "2x2", "4x1", "4x2", "4x4")
    }
}

@Serializable
enum class LiveType { PARTICLES, GRADIENT_FLOW, DEPTH_LAYERS }

@Serializable
data class LiveWallpaperSpec(
    val type: LiveType = LiveType.GRADIENT_FLOW,
    val colors: List<ArgbColor> = listOf(ArgbColor.rgb(0x0D0F12), ArgbColor.rgb(0x274A66), ArgbColor.rgb(0x9CC9F0)),
    val speed: Float = 0.4f,
    val intensity: Float = 0.6f,
    val count: Int = 48,
    val parallax: Float = 0.5f,
    val fpsCap: Int = 30,
)

@Serializable
enum class CcTileKind { WIFI, BLUETOOTH, TORCH, DND, BRIGHTNESS, MEDIA, SHORTCUT, MOBILE_DATA, ROTATION, AIRPLANE }

@Serializable
data class CcTile(
    val id: String,
    val kind: CcTileKind,
    val w: Int = 1,
    val h: Int = 1,
    val color: ArgbColor = ArgbColor.rgb(0x9CC9F0),
    val iconKey: String = "",
    val label: String = "",
)

@Serializable
data class ControlCenterSpec(
    val columns: Int = 4,
    val tiles: List<CcTile> = DEFAULT_TILES,
    val glass: Boolean = true,
    val cornerDp: Float = 26f,
    val background: ArgbColor = ArgbColor.rgb(0x16191E),
    val activeColor: ArgbColor = ArgbColor.rgb(0x9CC9F0),
) {
    companion object {
        val DEFAULT_TILES = listOf(
            CcTile("t1", CcTileKind.WIFI, 2, 1),
            CcTile("t2", CcTileKind.BLUETOOTH, 2, 1),
            CcTile("t3", CcTileKind.MEDIA, 2, 2),
            CcTile("t4", CcTileKind.BRIGHTNESS, 1, 2),
            CcTile("t5", CcTileKind.TORCH, 1, 1),
            CcTile("t6", CcTileKind.DND, 1, 1),
            CcTile("t7", CcTileKind.ROTATION, 1, 1),
            CcTile("t8", CcTileKind.AIRPLANE, 1, 1),
        )
    }
}

@Serializable
data class MtzSettings(
    val title: String = "",
    val designer: String = "",
    val author: String = "",
    val version: String = "1.0",
    val uiVersion: String = "1",
    val description: String = "",
    val includeWallpapers: Boolean = true,
    val includeIcons: Boolean = true,
    val iconsForAllApps: Boolean = true,
    val includeLockscreen: Boolean = true,
    val includePreviews: Boolean = true,
    val experimentalMiWallpaper: Boolean = false,
    val experimentalIconMask: Boolean = false,
    /** module -> (resource colour name -> "#AARRGGBB"); only applied to keys of a reference MTZ. */
    val colorOverrides: Map<String, Map<String, String>> = emptyMap(),
)

@Serializable
data class ThemeSpec(
    val palette: Palette = Palette(),
    val appearance: AppearanceSpec = AppearanceSpec(),
    val wallpaper: WallpaperSpec = WallpaperSpec(),
    val lockScreen: LockScreenSpec = LockScreenSpec(),
    val home: HomeLayoutSpec = HomeLayoutSpec(),
    val icons: IconStyleSpec = IconStyleSpec(),
    val widgets: List<WidgetSpec> = listOf(WidgetSpec(id = "w1")),
    val live: LiveWallpaperSpec = LiveWallpaperSpec(),
    val controlCenter: ControlCenterSpec = ControlCenterSpec(),
    val mtz: MtzSettings = MtzSettings(),
) {
    /** The wallpaper the lock screen actually uses. */
    val lockWallpaper: WallpaperSpec get() = lockScreen.wallpaper ?: wallpaper

    /** All asset references used anywhere in the spec (for export and integrity checks). */
    fun assetRefs(): List<AssetRef> = buildList {
        wallpaper.photo?.let(::add)
        lockScreen.wallpaper?.photo?.let(::add)
        lockScreen.elements.mapNotNull { it.assetPath() }.forEach { add(AssetRef(it)) }
        home.elements.mapNotNull { it.assetPath() }.forEach { add(AssetRef(it)) }
        icons.overrides.mapNotNull { it.asset }.forEach(::add)
        widgets.mapNotNull { it.image }.forEach(::add)
    }.distinctBy { it.path }
}
