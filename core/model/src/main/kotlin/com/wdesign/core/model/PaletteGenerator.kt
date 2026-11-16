package com.wdesign.core.model

enum class Harmony { ANALOGOUS, COMPLEMENTARY, TRIADIC, MONOCHROME }

/** Generates readable palettes from a seed colour using colour-wheel harmonies. */
object PaletteGenerator {

    fun harmony(seed: ArgbColor, harmony: Harmony): List<ArgbColor> {
        val (h, s, v) = seed.toHsv().let { Triple(it[0], it[1], it[2]) }
        return when (harmony) {
            Harmony.ANALOGOUS -> listOf(-30f, 0f, 30f).map { ArgbColor.fromHsv(h + it, s, v) }
            Harmony.COMPLEMENTARY -> listOf(ArgbColor.fromHsv(h, s, v), ArgbColor.fromHsv(h + 180f, s, v))
            Harmony.TRIADIC -> listOf(0f, 120f, 240f).map { ArgbColor.fromHsv(h + it, s, v) }
            Harmony.MONOCHROME -> listOf(0.35f, 0.6f, 0.85f, 1f).map { ArgbColor.fromHsv(h, s, (v * it).coerceIn(0.08f, 1f)) }
        }
    }

    /** Builds a complete [Palette] from a seed: dark or light surfaces, readable text and accent. */
    fun generate(seed: ArgbColor, harmony: Harmony, dark: Boolean = true): Palette {
        val hsv = seed.toHsv()
        val colors = harmony(seed, harmony)
        val primary = colors.first()
        val secondary = colors.getOrElse(1) { ArgbColor.fromHsv(hsv[0] + 20f, hsv[1] * 0.6f, hsv[2] * 0.8f) }
        val accent = colors.getOrElse(2) { ArgbColor.fromHsv(hsv[0] + 180f, hsv[1].coerceAtLeast(0.4f), 0.9f) }
        val background = if (dark) ArgbColor.fromHsv(hsv[0], (hsv[1] * 0.25f).coerceAtMost(0.25f), 0.06f)
        else ArgbColor.fromHsv(hsv[0], (hsv[1] * 0.08f).coerceAtMost(0.08f), 0.97f)
        val surface = if (dark) ArgbColor.fromHsv(hsv[0], (hsv[1] * 0.2f).coerceAtMost(0.2f), 0.13f)
        else ArgbColor.White
        val onSurface = ensureReadable(if (dark) ArgbColor.rgb(0xF1F2F4) else ArgbColor.rgb(0x111317), surface)
        val readableAccent = ensureReadable(accent, background, 3.0)
        val onAccent = if (ArgbColor.contrast(ArgbColor.Black, readableAccent) >= ArgbColor.contrast(ArgbColor.White, readableAccent))
            ArgbColor.rgb(0x0D0F12) else ArgbColor.White
        return Palette(primary, secondary, readableAccent, background, surface, onSurface, onAccent)
    }

    fun contrast(a: ArgbColor, b: ArgbColor): Double = ArgbColor.contrast(a, b)

    /**
     * Returns [fg] adjusted in brightness (keeping hue) until it reaches [min] contrast with [bg].
     * Falls back to black or white when the hue cannot reach the target.
     */
    fun ensureReadable(fg: ArgbColor, bg: ArgbColor, min: Double = 4.5): ArgbColor {
        if (contrast(fg, bg) >= min) return fg
        val hsv = fg.toHsv()
        val lighten = bg.luminance() < 0.5
        var v = hsv[2]; var s = hsv[1]
        repeat(40) {
            if (lighten) { v = (v + 0.03f).coerceAtMost(1f); s = (s - 0.02f).coerceAtLeast(0f) } else v = (v - 0.03f).coerceAtLeast(0f)
            val c = ArgbColor.fromHsv(hsv[0], s, v, fg.alpha)
            if (contrast(c, bg) >= min) return c
        }
        return if (lighten) ArgbColor.White else ArgbColor.Black
    }

    /** Short label used next to the contrast ratio. */
    fun grade(ratio: Double): String = when {
        ratio >= 7.0 -> "AAA"
        ratio >= 4.5 -> "AA"
        ratio >= 3.0 -> "AA Large"
        else -> "Fail"
    }
}
