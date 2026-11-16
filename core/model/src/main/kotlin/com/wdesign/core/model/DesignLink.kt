package com.wdesign.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * Compact share link for QR codes: wdesign://design?d=<base64url(deflate(json))>.
 * Only procedural parts of a design travel in the link (palette, wallpaper, clock, icon style,
 * glass); photos never do. Encoded links stay under [MAX_LENGTH] characters.
 */
object DesignLink {
    const val SCHEME_PREFIX = "wdesign://design?d="
    const val MAX_LENGTH = 600

    @Serializable
    internal data class Payload(
        @SerialName("v") val version: Int = 1,
        @SerialName("n") val name: String,
        @SerialName("p") val palette: List<Int>,
        @SerialName("wt") val wallpaperType: Int,
        @SerialName("wc") val wallpaperColors: List<Int>,
        @SerialName("wa") val angle: Int,
        @SerialName("ws") val seed: Long,
        @SerialName("wx") val complexity: Int,
        @SerialName("ls") val lockStyle: Int,
        @SerialName("cf") val clockFont: Int,
        @SerialName("cs") val clockSize: Int,
        @SerialName("cr") val clockRender: Int,
        @SerialName("cy") val clockY: Int,
        @SerialName("im") val iconMask: Int,
        @SerialName("if") val iconFilter: Int,
        @SerialName("gb") val glassBlur: Int,
        @SerialName("go") val glassOpacity: Int,
        @SerialName("r") val corner: Int,
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(name: String, spec: ThemeSpec): String {
        val w = if (spec.wallpaper.type == WallpaperType.PHOTO) spec.wallpaper.copy(type = WallpaperType.MESH) else spec.wallpaper
        val payload = Payload(
            name = name.take(40),
            palette = spec.palette.all().map { it.argb },
            wallpaperType = w.type.ordinal,
            wallpaperColors = w.colors.take(5).map { it.argb },
            angle = w.angle.toInt(),
            seed = w.seed,
            complexity = (w.complexity * 100).toInt(),
            lockStyle = spec.lockScreen.style.ordinal,
            clockFont = spec.lockScreen.clock.font.ordinal,
            clockSize = spec.lockScreen.clock.sizeSp.toInt(),
            clockRender = spec.lockScreen.clock.render.ordinal,
            clockY = (spec.lockScreen.clock.y * 1000).toInt(),
            iconMask = spec.icons.mask.ordinal,
            iconFilter = spec.icons.filter.ordinal,
            glassBlur = spec.appearance.glass.blurDp.toInt(),
            glassOpacity = (spec.appearance.glass.opacity * 100).toInt(),
            corner = spec.appearance.cornerRadiusDp.toInt(),
        )
        val raw = json.encodeToString(Payload.serializer(), payload).toByteArray()
        val deflater = Deflater(Deflater.BEST_COMPRESSION, true)
        deflater.setInput(raw); deflater.finish()
        val out = ByteArrayOutputStream()
        val buf = ByteArray(1024)
        while (!deflater.finished()) out.write(buf, 0, deflater.deflate(buf))
        deflater.end()
        return SCHEME_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray())
    }

    /** Decodes a link (or just its d= payload). Returns null for anything malformed. */
    fun decode(link: String): Pair<String, ThemeSpec>? = runCatching {
        val d = link.substringAfter("d=", link).substringBefore('&')
        if (d.length > MAX_LENGTH * 2) return null
        val bytes = Base64.getUrlDecoder().decode(d)
        val inflater = Inflater(true)
        inflater.setInput(bytes)
        val out = ByteArrayOutputStream()
        val buf = ByteArray(1024)
        while (!inflater.finished()) {
            val n = inflater.inflate(buf)
            if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
            out.write(buf, 0, n)
            if (out.size() > 16_384) return null
        }
        inflater.end()
        val p = json.decodeFromString(Payload.serializer(), out.toString(Charsets.UTF_8.name()))
        val pal = p.palette.map(::ArgbColor)
        val base = ThemeSpec()
        fun <E> List<E>.at(i: Int) = getOrElse(i) { first() }
        val spec = base.copy(
            palette = Palette(pal.at(0), pal.at(1), pal.at(2), pal.at(3), pal.at(4), pal.at(5), pal.at(6)),
            wallpaper = base.wallpaper.copy(
                type = WallpaperType.entries.at(p.wallpaperType),
                colors = p.wallpaperColors.map(::ArgbColor).ifEmpty { base.wallpaper.colors },
                angle = p.angle.toFloat(), seed = p.seed, complexity = p.complexity / 100f,
            ),
            lockScreen = base.lockScreen.copy(
                style = LockStyle.entries.at(p.lockStyle),
                clock = base.lockScreen.clock.copy(
                    font = SystemFont.entries.at(p.clockFont),
                    sizeSp = p.clockSize.toFloat().coerceIn(24f, 220f),
                    render = ClockRender.entries.at(p.clockRender),
                    y = (p.clockY / 1000f).coerceIn(0f, 0.9f),
                ),
            ),
            icons = base.icons.copy(mask = IconMask.entries.at(p.iconMask), filter = IconFilter.entries.at(p.iconFilter)),
            appearance = base.appearance.copy(
                glass = base.appearance.glass.copy(blurDp = p.glassBlur.toFloat(), opacity = p.glassOpacity / 100f),
                cornerRadiusDp = p.corner.toFloat(),
            ),
        )
        p.name to spec
    }.getOrNull()
}
