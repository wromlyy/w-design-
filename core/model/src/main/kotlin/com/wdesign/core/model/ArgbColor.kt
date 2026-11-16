package com.wdesign.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/** A 32-bit ARGB colour, serialized as "#AARRGGBB". Pure JVM so it can be shared by every layer. */
@Serializable(with = ArgbColorSerializer::class)
@JvmInline
value class ArgbColor(val argb: Int) {
    val alpha: Int get() = (argb ushr 24) and 0xFF
    val red: Int get() = (argb shr 16) and 0xFF
    val green: Int get() = (argb shr 8) and 0xFF
    val blue: Int get() = argb and 0xFF

    fun withAlpha(a: Int): ArgbColor = ArgbColor((a.coerceIn(0, 255) shl 24) or (argb and 0x00FFFFFF))
    fun withAlpha(f: Float): ArgbColor = withAlpha((f.coerceIn(0f, 1f) * 255).roundToInt())

    /** "#AARRGGBB" */
    fun toHex(): String = "#" + "%08X".format(argb)

    /** "#RRGGBB" (alpha dropped) */
    fun toRgbHex(): String = "#" + "%06X".format(argb and 0xFFFFFF)

    /** WCAG relative luminance 0..1 */
    fun luminance(): Double {
        fun ch(c: Int): Double {
            val s = c / 255.0
            return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * ch(red) + 0.7152 * ch(green) + 0.0722 * ch(blue)
    }

    fun toHsv(): FloatArray {
        val r = red / 255f; val g = green / 255f; val b = blue / 255f
        val mx = max(r, max(g, b)); val mn = min(r, min(g, b)); val d = mx - mn
        val h = when {
            d == 0f -> 0f
            mx == r -> 60f * (((g - b) / d) % 6f)
            mx == g -> 60f * (((b - r) / d) + 2f)
            else -> 60f * (((r - g) / d) + 4f)
        }.let { if (it < 0) it + 360f else it }
        val s = if (mx == 0f) 0f else d / mx
        return floatArrayOf(h, s, mx)
    }

    /** Linear blend towards [other] by [t] in 0..1 (alpha blended too). */
    fun blend(other: ArgbColor, t: Float): ArgbColor {
        val k = t.coerceIn(0f, 1f)
        fun mix(a: Int, b: Int) = (a + (b - a) * k).roundToInt().coerceIn(0, 255)
        return argb(mix(alpha, other.alpha), mix(red, other.red), mix(green, other.green), mix(blue, other.blue))
    }

    override fun toString(): String = toHex()

    companion object {
        val White = ArgbColor(0xFFFFFFFF.toInt())
        val Black = ArgbColor(0xFF000000.toInt())
        val Transparent = ArgbColor(0)

        fun argb(a: Int, r: Int, g: Int, b: Int) =
            ArgbColor((a.coerceIn(0, 255) shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255))

        fun rgb(rgb: Long): ArgbColor = ArgbColor((0xFF000000L or (rgb and 0xFFFFFFL)).toInt())

        fun fromHsv(h: Float, s: Float, v: Float, alpha: Int = 255): ArgbColor {
            val hh = ((h % 360f) + 360f) % 360f
            val c = v * s
            val x = c * (1 - kotlin.math.abs((hh / 60f) % 2 - 1))
            val m = v - c
            val (r, g, b) = when {
                hh < 60 -> Triple(c, x, 0f)
                hh < 120 -> Triple(x, c, 0f)
                hh < 180 -> Triple(0f, c, x)
                hh < 240 -> Triple(0f, x, c)
                hh < 300 -> Triple(x, 0f, c)
                else -> Triple(c, 0f, x)
            }
            return argb(alpha, ((r + m) * 255).roundToInt(), ((g + m) * 255).roundToInt(), ((b + m) * 255).roundToInt())
        }

        /** Parses "#RGB", "#RRGGBB" or "#AARRGGBB" (leading # optional). Returns null on bad input. */
        fun parse(text: String): ArgbColor? {
            val s = text.trim().removePrefix("#")
            if (s.isEmpty() || s.any { !isHex(it) }) return null
            return when (s.length) {
                3 -> rgb(s.map { "$it$it" }.joinToString("").toLong(16))
                6 -> rgb(s.toLong(16))
                8 -> ArgbColor(s.toLong(16).toInt())
                else -> null
            }
        }

        private fun isHex(c: Char) = c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'

        /** WCAG contrast ratio between two colours (alpha ignored). */
        fun contrast(a: ArgbColor, b: ArgbColor): Double {
            val l1 = a.luminance(); val l2 = b.luminance()
            val hi = max(l1, l2); val lo = min(l1, l2)
            return (hi + 0.05) / (lo + 0.05)
        }
    }
}

object ArgbColorSerializer : KSerializer<ArgbColor> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("com.wdesign.ArgbColor", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: ArgbColor) = encoder.encodeString(value.toHex())
    override fun deserialize(decoder: Decoder): ArgbColor {
        val raw = decoder.decodeString()
        return ArgbColor.parse(raw) ?: throw kotlinx.serialization.SerializationException("Invalid colour '$raw'")
    }
}
