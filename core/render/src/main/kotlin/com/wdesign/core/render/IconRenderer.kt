package com.wdesign.core.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import com.wdesign.core.model.ArgbColor
import com.wdesign.core.model.IconFilter
import com.wdesign.core.model.IconMask
import com.wdesign.core.model.IconStyleSpec
import kotlin.math.cos
import kotlin.math.sin

/** Applies an [IconStyleSpec] (mask, padding, background, filter, shadow) to any app icon. */
object IconRenderer {

    fun maskPath(mask: IconMask, size: Float, cornerPct: Float, inset: Float = 0f): Path {
        val p = Path()
        val r = RectF(inset, inset, size - inset, size - inset)
        val s = r.width()
        when (mask) {
            IconMask.CIRCLE -> p.addOval(r, Path.Direction.CW)
            IconMask.ROUNDED -> p.addRoundRect(r, s * cornerPct.coerceIn(0f, 0.5f), s * cornerPct.coerceIn(0f, 0.5f), Path.Direction.CW)
            IconMask.SQUIRCLE -> {
                // superellipse |x|^4 + |y|^4 = 1
                val n = 4.0
                val cx = r.centerX(); val cy = r.centerY(); val a = s / 2
                for (i in 0..96) {
                    val t = i / 96.0 * 2 * Math.PI
                    val ct = cos(t); val st = sin(t)
                    val x = cx + a * Math.signum(ct) * Math.pow(Math.abs(ct), 2 / n)
                    val y = cy + a * Math.signum(st) * Math.pow(Math.abs(st), 2 / n)
                    if (i == 0) p.moveTo(x.toFloat(), y.toFloat()) else p.lineTo(x.toFloat(), y.toFloat())
                }
                p.close()
            }
            IconMask.TEARDROP -> {
                val rad = s / 2
                p.addRoundRect(r, floatArrayOf(rad, rad, rad, rad, s * 0.12f, s * 0.12f, rad, rad), Path.Direction.CW)
            }
            IconMask.HEXAGON -> {
                val cx = r.centerX(); val cy = r.centerY(); val a = s / 2
                for (i in 0 until 6) {
                    val t = Math.toRadians(60.0 * i - 30)
                    val x = (cx + a * cos(t)).toFloat(); val y = (cy + a * sin(t)).toFloat()
                    if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                }
                p.close()
            }
            IconMask.NONE -> p.addRect(r, Path.Direction.CW)
        }
        return p
    }

    fun drawableToBitmap(d: Drawable, size: Int): Bitmap {
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        if (Build.VERSION.SDK_INT >= 26 && d is AdaptiveIconDrawable) {
            // draw full-bleed layers so our own mask decides the shape
            val extra = (size * 0.25f).toInt()
            d.background?.let { it.setBounds(-extra, -extra, size + extra, size + extra); it.draw(c) }
            d.foreground?.let { it.setBounds(-extra, -extra, size + extra, size + extra); it.draw(c) }
        } else {
            d.setBounds(0, 0, size, size); d.draw(c)
        }
        return b
    }

    private fun filterFor(style: IconStyleSpec): ColorMatrixColorFilter? = when (style.filter) {
        IconFilter.NONE -> null
        IconFilter.MONO -> ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
        IconFilter.TINT, IconFilter.DUOTONE -> {
            val t = style.tint
            val bg = style.background ?: ArgbColor.Black
            val lo = if (style.filter == IconFilter.DUOTONE) bg else ArgbColor.Black
            // luminance → lerp(lo, tint)
            val lr = 0.299f; val lg = 0.587f; val lb = 0.114f
            fun row(l: Int, h: Int): FloatArray { val d = (h - l) / 255f; return floatArrayOf(lr * d, lg * d, lb * d, 0f, l.toFloat()) }
            ColorMatrixColorFilter(ColorMatrix(row(lo.red, t.red) + row(lo.green, t.green) + row(lo.blue, t.blue) + floatArrayOf(0f, 0f, 0f, 1f, 0f)))
        }
    }

    /**
     * @param source the app icon (already a bitmap) or null to draw a [fallbackLetter] glyph.
     */
    fun render(source: Bitmap?, style: IconStyleSpec, size: Int, fallbackLetter: String = "", whiteText: Boolean = true): Bitmap {
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val sf = size.toFloat()
        val shadowInset = if (style.shadow && style.mask != IconMask.NONE) sf * 0.04f else 0f
        val mask = maskPath(style.mask, sf, style.cornerPct, shadowInset)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        if (style.mask != IconMask.NONE) {
            val bg = when {
                style.glass -> ArgbColor.White.withAlpha(0.18f)
                style.background != null -> style.background!!
                else -> ArgbColor.Transparent
            }
            if (style.shadow) p.setShadowLayer(sf * 0.035f, 0f, sf * 0.015f, 0x55000000)
            p.color = if (bg.alpha == 0) 0x01000000 else bg.int
            c.drawPath(mask, p)
            p.clearShadowLayer()
            if (style.glass) {
                p.style = Paint.Style.STROKE; p.strokeWidth = sf * 0.012f; p.color = ArgbColor.White.withAlpha(0.35f).int
                c.drawPath(mask, p); p.style = Paint.Style.FILL
            }
        }
        c.save()
        if (style.mask != IconMask.NONE) c.clipPath(mask)
        p.colorFilter = filterFor(style)
        val pad = sf * style.paddingPct.coerceIn(0f, 0.4f) + shadowInset
        if (source != null) {
            c.drawBitmap(source, null, RectF(pad, pad, sf - pad, sf - pad), p)
        } else {
            p.colorFilter = null
            p.color = if (whiteText) 0xFFF1F2F4.toInt() else 0xFF111317.toInt()
            p.textAlign = Paint.Align.CENTER; p.textSize = sf * 0.42f; p.typeface = Typefaces.of(com.wdesign.core.model.SystemFont.SANS_MEDIUM, 500)
            val fm = p.fontMetrics
            c.drawText(fallbackLetter.take(1).uppercase(), sf / 2, sf / 2 - (fm.ascent + fm.descent) / 2, p)
        }
        c.restore()
        return out
    }
}
