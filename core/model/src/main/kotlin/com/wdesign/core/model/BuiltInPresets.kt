package com.wdesign.core.model

@kotlinx.serialization.Serializable
enum class DiscoverCategory { LIQUID_GLASS, IOS_STYLE, MINIMAL, AMOLED, DARK, LIGHT, NOTHING_STYLE, HYPEROS, ABSTRACT, CUSTOM }

data class Preset(val id: String, val name: String, val categories: Set<DiscoverCategory>, val spec: ThemeSpec)

/** Lock-screen templates. Each returns a complete, editable [LockScreenSpec]. */
object LockTemplates {
    private fun c(hex: Long) = ArgbColor.rgb(hex)

    fun build(style: LockStyle, base: LockScreenSpec = LockScreenSpec()): LockScreenSpec = when (style) {
        LockStyle.MINIMAL -> base.copy(
            style = style,
            clock = ClockSpec(font = SystemFont.SANS_THIN, weight = 200, sizeSp = 96f, y = 0.13f),
            date = DateSpec(pattern = "EEEE, d MMMM", sizeSp = 17f, y = 0.10f),
            elements = emptyList(),
        )
        LockStyle.LIQUID_GLASS -> base.copy(
            style = style,
            clock = ClockSpec(font = SystemFont.SANS_MEDIUM, weight = 500, sizeSp = 104f, y = 0.12f),
            date = DateSpec(pattern = "EEE d MMM", sizeSp = 18f, y = 0.09f),
            elements = listOf(
                CanvasElement("glass1", ElementType.GLASS_PANEL, 0.08f, 0.33f, 0.84f, 0.12f,
                    props = mapOf(CanvasElement.Props.CORNER to "28", CanvasElement.Props.BLUR to "24", CanvasElement.Props.TINT_ALPHA to "0.16")),
                CanvasElement("wref1", ElementType.WIDGET_REF, 0.08f, 0.47f, 0.40f, 0.10f,
                    props = mapOf(CanvasElement.Props.LABEL to "Weather")),
            ),
        )
        LockStyle.IOS_INSPIRED -> base.copy(
            style = style,
            clock = ClockSpec(font = SystemFont.SANS_BLACK, weight = 700, sizeSp = 112f, y = 0.12f),
            date = DateSpec(pattern = "EEEE d MMMM", sizeSp = 19f, y = 0.09f, color = ArgbColor.White.withAlpha(0.9f)),
            elements = listOf(
                CanvasElement("wref1", ElementType.WIDGET_REF, 0.12f, 0.29f, 0.22f, 0.07f, props = mapOf(CanvasElement.Props.LABEL to "Battery")),
                CanvasElement("wref2", ElementType.WIDGET_REF, 0.39f, 0.29f, 0.22f, 0.07f, props = mapOf(CanvasElement.Props.LABEL to "Weather")),
                CanvasElement("wref3", ElementType.WIDGET_REF, 0.66f, 0.29f, 0.22f, 0.07f, props = mapOf(CanvasElement.Props.LABEL to "Calendar")),
            ),
        )
        LockStyle.NOTHING_INSPIRED -> base.copy(
            style = style,
            clock = ClockSpec(font = SystemFont.MONO, weight = 400, sizeSp = 92f, x = 0.08f, y = 0.16f,
                align = TextAlign.START, render = ClockRender.DOT_MATRIX, color = c(0xF2F2F2)),
            date = DateSpec(pattern = "EEE dd.MM", sizeSp = 16f, x = 0.08f, y = 0.12f, align = TextAlign.START, color = c(0xD71921)),
            elements = listOf(
                CanvasElement("dot", ElementType.SHAPE, 0.84f, 0.12f, 0.04f, 0.02f,
                    props = mapOf(CanvasElement.Props.SHAPE to "circle", CanvasElement.Props.FILL to "#FFD71921")),
            ),
        )
        LockStyle.AMOLED -> base.copy(
            style = style,
            wallpaper = WallpaperSpec(type = WallpaperType.SOLID, colors = listOf(ArgbColor.Black)),
            clock = ClockSpec(font = SystemFont.SANS_LIGHT, weight = 300, sizeSp = 84f, y = 0.38f, color = c(0x9CC9F0)),
            date = DateSpec(pattern = "EEEE, d MMMM", sizeSp = 16f, y = 0.35f, color = c(0x6B717C)),
            elements = emptyList(),
        )
        LockStyle.CLASSIC -> base.copy(
            style = style,
            clock = ClockSpec(font = SystemFont.SERIF, weight = 400, sizeSp = 80f, x = 0.08f, y = 0.11f, align = TextAlign.START),
            date = DateSpec(pattern = "d MMMM, EEEE", sizeSp = 18f, x = 0.08f, y = 0.24f, align = TextAlign.START),
            elements = listOf(
                CanvasElement("line", ElementType.SHAPE, 0.08f, 0.225f, 0.3f, 0.003f,
                    props = mapOf(CanvasElement.Props.SHAPE to "rect", CanvasElement.Props.FILL to "#B3FFFFFF")),
            ),
        )
        LockStyle.CUSTOM -> base.copy(style = style)
    }
}

/** At least 20 procedural presets (no bitmap assets), spread across all Discover categories. */
object BuiltInPresets {
    private fun c(hex: Long) = ArgbColor.rgb(hex)

    private fun theme(
        palette: Palette,
        wallpaper: WallpaperSpec,
        lock: LockStyle,
        mask: IconMask = IconMask.SQUIRCLE,
        filter: IconFilter = IconFilter.NONE,
        glass: GlassSpec = GlassSpec(),
        mode: AppearanceMode = AppearanceMode.DARK,
        font: SystemFont = SystemFont.SANS,
        clockColor: ArgbColor? = null,
        live: LiveWallpaperSpec = LiveWallpaperSpec(colors = wallpaper.colors.take(3).ifEmpty { listOf(palette.background) }),
    ): ThemeSpec {
        val lockSpec = LockTemplates.build(lock).let { l -> if (clockColor != null) l.copy(clock = l.clock.copy(color = clockColor)) else l }
        return ThemeSpec(
            palette = palette,
            appearance = AppearanceSpec(mode = mode, glass = glass, font = font),
            wallpaper = wallpaper,
            lockScreen = lockSpec,
            icons = IconStyleSpec(mask = mask, filter = filter, tint = palette.primary, background = palette.surface, glass = glass.opacity > 0.2f),
            widgets = listOf(
                WidgetSpec("w1", WidgetType.CLOCK, "2x2", background = palette.surface, textColor = palette.onSurface, accent = palette.accent),
                WidgetSpec("w2", WidgetType.COMBO, "4x2", background = palette.surface, textColor = palette.onSurface, accent = palette.accent,
                    parts = listOf(WidgetType.CLOCK, WidgetType.DATE, WidgetType.BATTERY)),
            ),
            live = live,
            controlCenter = ControlCenterSpec(activeColor = palette.primary, background = palette.surface),
        )
    }

    val all: List<Preset> by lazy {
        listOf(
            Preset("glass-graphite", "Graphite Glass", setOf(DiscoverCategory.LIQUID_GLASS, DiscoverCategory.DARK),
                theme(Palette(), WallpaperSpec(WallpaperType.MESH, listOf(c(0x1B2A3A), c(0x0D0F12), c(0x3D5A73), c(0x9CC9F0)), seed = 11), LockStyle.LIQUID_GLASS,
                    glass = GlassSpec(28f, 0.22f))),
            Preset("glass-aurora", "Aurora Glass", setOf(DiscoverCategory.LIQUID_GLASS, DiscoverCategory.ABSTRACT),
                theme(Palette(primary = c(0x8EE3C8), accent = c(0xF2D17A)), WallpaperSpec(WallpaperType.MESH, listOf(c(0x0E2A2A), c(0x1F5F5B), c(0x8EE3C8), c(0x0D1B22)), seed = 23), LockStyle.LIQUID_GLASS,
                    glass = GlassSpec(32f, 0.24f))),
            Preset("glass-dawn", "Dawn Glass", setOf(DiscoverCategory.LIQUID_GLASS, DiscoverCategory.LIGHT),
                theme(Palette(primary = c(0x1F6FB2), background = c(0xF3F4F6), surface = c(0xFFFFFF), onSurface = c(0x111317), onAccent = c(0xFFFFFF), accent = c(0xC2352B)),
                    WallpaperSpec(WallpaperType.GRADIENT, listOf(c(0xFDE6D3), c(0xD9E7F5), c(0xF3F4F6)), angle = 160f), LockStyle.LIQUID_GLASS, mode = AppearanceMode.LIGHT,
                    clockColor = c(0x111317))),
            Preset("ios-sky", "Clear Sky", setOf(DiscoverCategory.IOS_STYLE, DiscoverCategory.LIGHT),
                theme(Palette(primary = c(0x0A84FF), accent = c(0xFF9F0A), background = c(0xF2F2F7), surface = c(0xFFFFFF), onSurface = c(0x111317), onAccent = c(0xFFFFFF)),
                    WallpaperSpec(WallpaperType.GRADIENT, listOf(c(0x5AC8FA), c(0x0A84FF), c(0x1C3D7A)), angle = 180f), LockStyle.IOS_INSPIRED, mask = IconMask.SQUIRCLE)),
            Preset("ios-midnight", "Midnight", setOf(DiscoverCategory.IOS_STYLE, DiscoverCategory.DARK),
                theme(Palette(primary = c(0x64D2FF), accent = c(0xFFD60A)),
                    WallpaperSpec(WallpaperType.GRADIENT, listOf(c(0x1C1C3C), c(0x0B0B14), c(0x3A2E5C)), angle = 200f), LockStyle.IOS_INSPIRED)),
            Preset("min-paper", "Paper", setOf(DiscoverCategory.MINIMAL, DiscoverCategory.LIGHT),
                theme(Palette(primary = c(0x111317), secondary = c(0x565C66), accent = c(0x1F6FB2), background = c(0xF3F4F6), surface = c(0xFFFFFF), onSurface = c(0x111317), onAccent = c(0xFFFFFF)),
                    WallpaperSpec(WallpaperType.SOLID, listOf(c(0xEDEBE6))), LockStyle.MINIMAL, mask = IconMask.CIRCLE, filter = IconFilter.MONO,
                    mode = AppearanceMode.LIGHT, clockColor = c(0x111317))),
            Preset("min-slate", "Slate", setOf(DiscoverCategory.MINIMAL, DiscoverCategory.DARK),
                theme(Palette(), WallpaperSpec(WallpaperType.SOLID, listOf(c(0x16191E))), LockStyle.MINIMAL, mask = IconMask.ROUNDED, filter = IconFilter.MONO)),
            Preset("min-sand", "Sand", setOf(DiscoverCategory.MINIMAL),
                theme(Palette(primary = c(0xC9A66B), accent = c(0x6ED3A0)), WallpaperSpec(WallpaperType.GRADIENT, listOf(c(0x3B3328), c(0x1A1712)), angle = 90f), LockStyle.CLASSIC,
                    font = SystemFont.SERIF)),
            Preset("amoled-void", "Void", setOf(DiscoverCategory.AMOLED, DiscoverCategory.DARK),
                theme(Palette(background = ArgbColor.Black, surface = c(0x0A0A0A)), WallpaperSpec(WallpaperType.SOLID, listOf(ArgbColor.Black)), LockStyle.AMOLED,
                    mask = IconMask.CIRCLE, filter = IconFilter.TINT)),
            Preset("amoled-ember", "Ember", setOf(DiscoverCategory.AMOLED),
                theme(Palette(primary = c(0xFF6B5E), accent = c(0xF0C35A), background = ArgbColor.Black, surface = c(0x0A0A0A)),
                    WallpaperSpec(WallpaperType.ABSTRACT, listOf(ArgbColor.Black, c(0x3A0D08), c(0xFF6B5E)), seed = 5, complexity = 0.3f), LockStyle.AMOLED,
                    clockColor = c(0xFF6B5E))),
            Preset("amoled-orbit", "Orbit", setOf(DiscoverCategory.AMOLED, DiscoverCategory.ABSTRACT),
                theme(Palette(primary = c(0x9CC9F0), background = ArgbColor.Black, surface = c(0x0A0A0A)),
                    WallpaperSpec(WallpaperType.GEOMETRIC, listOf(ArgbColor.Black, c(0x16191E), c(0x9CC9F0)), seed = 42, complexity = 0.4f), LockStyle.AMOLED)),
            Preset("dark-forest", "Forest Night", setOf(DiscoverCategory.DARK),
                theme(Palette(primary = c(0x6ED3A0), accent = c(0xF0C35A)), WallpaperSpec(WallpaperType.MESH, listOf(c(0x0C1F17), c(0x183A2B), c(0x0D0F12), c(0x2E6B4F)), seed = 3), LockStyle.CLASSIC)),
            Preset("dark-ink", "Ink", setOf(DiscoverCategory.DARK, DiscoverCategory.MINIMAL),
                theme(Palette(primary = c(0xF1F2F4), accent = c(0x9CC9F0)), WallpaperSpec(WallpaperType.GRADIENT, listOf(c(0x1E2228), c(0x0D0F12)), angle = 270f), LockStyle.MINIMAL)),
            Preset("light-mist", "Mist", setOf(DiscoverCategory.LIGHT),
                theme(Palette(primary = c(0x1F6FB2), accent = c(0x2E8B57), background = c(0xF3F4F6), surface = c(0xFFFFFF), onSurface = c(0x111317), onAccent = c(0xFFFFFF)),
                    WallpaperSpec(WallpaperType.MESH, listOf(c(0xE8EEF3), c(0xD3E0EA), c(0xF7F2EC), c(0xBFD3E3)), seed = 8), LockStyle.MINIMAL, mode = AppearanceMode.LIGHT,
                    clockColor = c(0x111317))),
            Preset("light-bloom", "Bloom", setOf(DiscoverCategory.LIGHT, DiscoverCategory.ABSTRACT),
                theme(Palette(primary = c(0xB23A6F), accent = c(0x1F6FB2), background = c(0xFAF5F7), surface = c(0xFFFFFF), onSurface = c(0x111317), onAccent = c(0xFFFFFF)),
                    WallpaperSpec(WallpaperType.ABSTRACT, listOf(c(0xFBE3EC), c(0xF4C2D7), c(0xFFFFFF)), seed = 19), LockStyle.IOS_INSPIRED, mode = AppearanceMode.LIGHT,
                    clockColor = c(0x3A1424))),
            Preset("nothing-mono", "Dot Mono", setOf(DiscoverCategory.NOTHING_STYLE, DiscoverCategory.DARK),
                theme(Palette(primary = c(0xF2F2F2), accent = c(0xD71921), background = ArgbColor.Black, surface = c(0x111111)),
                    WallpaperSpec(WallpaperType.SOLID, listOf(c(0x0A0A0A))), LockStyle.NOTHING_INSPIRED, mask = IconMask.CIRCLE, filter = IconFilter.MONO, font = SystemFont.MONO)),
            Preset("nothing-white", "Dot White", setOf(DiscoverCategory.NOTHING_STYLE, DiscoverCategory.LIGHT),
                theme(Palette(primary = c(0x111111), accent = c(0xD71921), background = c(0xF2F2F2), surface = c(0xFFFFFF), onSurface = c(0x111111), onAccent = c(0xFFFFFF)),
                    WallpaperSpec(WallpaperType.SOLID, listOf(c(0xEFEFEF))), LockStyle.NOTHING_INSPIRED, mask = IconMask.CIRCLE, filter = IconFilter.MONO,
                    mode = AppearanceMode.LIGHT, font = SystemFont.MONO, clockColor = c(0x111111))),
            Preset("hyper-blue", "Hyper Blue", setOf(DiscoverCategory.HYPEROS),
                theme(Palette(primary = c(0x3482FF), accent = c(0xFFB020)), WallpaperSpec(WallpaperType.GRADIENT, listOf(c(0x0B1C3D), c(0x1D4ED8), c(0x7DB3FF)), angle = 145f), LockStyle.LIQUID_GLASS,
                    glass = GlassSpec(30f, 0.2f))),
            Preset("hyper-soft-light", "Soft Light", setOf(DiscoverCategory.HYPEROS, DiscoverCategory.LIQUID_GLASS),
                theme(Palette(primary = c(0xA9D6FF), accent = c(0xFFD38A)), WallpaperSpec(WallpaperType.MESH, listOf(c(0x20324A), c(0x4C6A8C), c(0xC9DDF0), c(0x101820)), seed = 77), LockStyle.LIQUID_GLASS,
                    glass = GlassSpec(36f, 0.26f, borderAlpha = 0.3f))),
            Preset("hyper-dusk", "Dusk", setOf(DiscoverCategory.HYPEROS, DiscoverCategory.DARK),
                theme(Palette(primary = c(0xF0A35A), accent = c(0x9CC9F0)), WallpaperSpec(WallpaperType.GRADIENT, listOf(c(0x2A1A2E), c(0x6B3A3A), c(0xF0A35A)), angle = 120f), LockStyle.CLASSIC)),
            Preset("abstract-prism", "Prism", setOf(DiscoverCategory.ABSTRACT),
                theme(Palette(primary = c(0xF0C35A), accent = c(0x9CC9F0)), WallpaperSpec(WallpaperType.GEOMETRIC, listOf(c(0x16191E), c(0x2F3A45), c(0xF0C35A), c(0x9CC9F0)), seed = 101, complexity = 0.7f), LockStyle.MINIMAL,
                    mask = IconMask.HEXAGON)),
            Preset("abstract-tide", "Tide", setOf(DiscoverCategory.ABSTRACT, DiscoverCategory.DARK),
                theme(Palette(primary = c(0x5EC2D8)), WallpaperSpec(WallpaperType.ABSTRACT, listOf(c(0x07141C), c(0x0F3B4F), c(0x5EC2D8)), seed = 64, complexity = 0.6f), LockStyle.MINIMAL,
                    mask = IconMask.TEARDROP, live = LiveWallpaperSpec(LiveType.PARTICLES, listOf(c(0x07141C), c(0x0F3B4F), c(0x5EC2D8))))),
            Preset("custom-blank", "Blank canvas", setOf(DiscoverCategory.CUSTOM),
                theme(Palette(), WallpaperSpec(WallpaperType.SOLID, listOf(c(0x16191E))), LockStyle.CUSTOM)),
            Preset("custom-duotone", "Duotone", setOf(DiscoverCategory.CUSTOM, DiscoverCategory.ABSTRACT),
                theme(Palette(primary = c(0x9CC9F0), accent = c(0xF0C35A)), WallpaperSpec(WallpaperType.GRADIENT, listOf(c(0x0D0F12), c(0x274A66)), angle = 45f), LockStyle.CUSTOM,
                    filter = IconFilter.DUOTONE)),
        )
    }

    fun byId(id: String): Preset? = all.firstOrNull { it.id == id }
    fun inCategory(c: DiscoverCategory): List<Preset> = all.filter { c in it.categories }
}
