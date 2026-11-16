package com.wdesign.core.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import com.wdesign.core.model.ArgbColor
import com.wdesign.core.model.CanvasElement
import com.wdesign.core.model.CanvasElement.Props
import com.wdesign.core.model.ClockFormat
import com.wdesign.core.model.ClockRender
import com.wdesign.core.model.ClockSpec
import com.wdesign.core.model.DateSpec
import com.wdesign.core.model.ElementType
import com.wdesign.core.model.SystemFont
import com.wdesign.core.model.TextAlign
import com.wdesign.core.model.ThemeSpec
import com.wdesign.core.mtz.MamlDatePattern
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

data class LockRenderOptions(
    val includeWallpaper: Boolean = true,
    val includeDecorations: Boolean = true,
    val includeClock: Boolean = true,
    val includeDate: Boolean = true,
    /** Notification area, fingerprint area and widget placeholders (preview only, never applied). */
    val includeGhosts: Boolean = false,
    val timeMs: Long = System.currentTimeMillis(),
    val systemIs24h: Boolean = true,
)

/**
 * Draws a lock screen design at exact device pixels. The same code produces the in-app preview,
 * the lock wallpaper W Design applies (decorations only, no clock) and the MTZ assets.
 * Layout uses 360 design units across the width (u = width / 360).
 */
object LockScreenRenderer {

    fun render(spec: ThemeSpec, w: Int, h: Int, assetsDir: File?, opt: LockRenderOptions = LockRenderOptions()): Bitmap {
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val wallpaper = if (opt.includeWallpaper || spec.lockScreen.elements.any { it.type == ElementType.GLASS_PANEL })
            WallpaperRenderer.render(spec.lockWallpaper, w, h, assetsDir) else null
        if (opt.includeWallpaper && wallpaper != null) c.drawBitmap(wallpaper, 0f, 0f, null)
        val lock = spec.lockScreen
        val blurCache = HashMap<Int, Bitmap>()
        if (opt.includeDecorations) {
            for (e in lock.elements.filter { !it.hidden }.sortedBy { it.z }) {
                if (e.type == ElementType.WIDGET_REF && !opt.includeGhosts) continue
                drawElement(c, e, w, h, assetsDir, wallpaper, blurCache)
            }
        }
        if (opt.includeGhosts) drawGhosts(c, spec, w, h)
        if (opt.includeDate && lock.date.visible) drawDate(c, lock.date, w, h, opt.timeMs)
        if (opt.includeClock && lock.clock.visible) drawClock(c, lock.clock, w, h, opt.timeMs, opt.systemIs24h)
        return out
    }

    fun timeText(clock: ClockSpec, timeMs: Long, systemIs24h: Boolean): List<String> {
        val h24 = when (clock.format) { ClockFormat.FOLLOW_SYSTEM -> systemIs24h; ClockFormat.H24 -> true; ClockFormat.H12 -> false }
        val hh = SimpleDateFormat(if (h24) "HH" else "hh", Locale.ROOT).format(Date(timeMs))
        val mm = SimpleDateFormat("mm", Locale.ROOT).format(Date(timeMs))
        return if (clock.render == ClockRender.STACKED) listOf(hh, mm) else listOf("$hh:$mm")
    }

    fun dateText(date: DateSpec, timeMs: Long, locale: Locale = Locale.getDefault()): String =
        runCatching { SimpleDateFormat(MamlDatePattern.translate(date.pattern).pattern, locale).format(Date(timeMs)) }.getOrDefault("")

    private fun align(a: TextAlign) = when (a) { TextAlign.START -> Paint.Align.LEFT; TextAlign.CENTER -> Paint.Align.CENTER; TextAlign.END -> Paint.Align.RIGHT }

    fun clockPaint(clock: ClockSpec, sizePx: Float): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typefaces.of(clock.font, clock.weight)
        textSize = sizePx
        color = clock.color.int
        textAlign = align(clock.align)
        if (clock.render == ClockRender.OUTLINE) { style = Paint.Style.STROKE; strokeWidth = (sizePx * 0.025f).coerceAtLeast(1.5f) }
    }

    fun drawClock(c: Canvas, clock: ClockSpec, w: Int, h: Int, timeMs: Long, systemIs24h: Boolean) {
        val u = w / 360f
        val size = clock.sizeSp * u
        val lines = timeText(clock, timeMs, systemIs24h)
        val x = clock.x * w
        var top = clock.y * h
        for (line in lines) {
            drawClockLine(c, line, x, top, clock, size)
            top += size * 0.95f
        }
    }

    /** Draws one clock line with its top edge at [top]. Shared with the MTZ digit rasterizer. */
    fun drawClockLine(c: Canvas, text: String, x: Float, top: Float, clock: ClockSpec, size: Float) {
        val p = clockPaint(clock, size)
        val fm = p.fontMetrics
        val baseline = top - fm.ascent * 0.86f
        val width = p.measureText(text)
        val left = when (p.textAlign) { Paint.Align.LEFT -> x; Paint.Align.CENTER -> x - width / 2; else -> x - width }
        val gradient = clock.gradient
        if (!gradient.isNullOrEmpty() && clock.render != ClockRender.DOT_MATRIX) {
            val cols = gradient.map { it.int }.let { if (it.size == 1) it + it else it }
            p.shader = LinearGradient(left, top, left + width, baseline, cols.toIntArray(), null, Shader.TileMode.CLAMP)
        }
        if (clock.render == ClockRender.DOT_MATRIX) {
            drawDotMatrix(c, text, left, top, baseline, width, p, clock)
        } else {
            c.drawText(text, x, baseline, p)
        }
    }

    private fun drawDotMatrix(c: Canvas, text: String, left: Float, top: Float, baseline: Float, width: Float, p: Paint, clock: ClockSpec) {
        val hgt = (baseline - top + p.fontMetrics.descent).coerceAtLeast(1f)
        val step = (p.textSize / 11f).coerceAtLeast(2f)
        val gw = (width / step).roundToInt().coerceAtLeast(1) + 1
        val gh = (hgt / step).roundToInt().coerceAtLeast(1)
        // Render the glyphs into a tiny grid-sized mask and turn each set cell into a dot.
        val mask = Bitmap.createBitmap(gw, gh, Bitmap.Config.ALPHA_8)
        val mp = Paint(p).apply { textSize = p.textSize / step; textAlign = Paint.Align.LEFT; shader = null; style = Paint.Style.FILL; typeface = Typefaces.of(SystemFont.SANS_BLACK, 800) }
        Canvas(mask).drawText(text, 0f, (baseline - top) / step, mp)
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = clock.color.int }
        val gradient = clock.gradient
        for (yy in 0 until gh) for (xx in 0 until gw) {
            val a = (mask.getPixel(xx, yy) ushr 24) and 0xFF
            if (a > 110) {
                if (!gradient.isNullOrEmpty()) dot.color = gradient.first().blend(gradient.last(), xx / gw.toFloat()).int
                c.drawCircle(left + xx * step + step / 2, top + yy * step + step / 2, step * 0.38f, dot)
            }
        }
    }

    fun drawDate(c: Canvas, date: DateSpec, w: Int, h: Int, timeMs: Long) {
        val u = w / 360f
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typefaces.of(SystemFont.SANS, 400); textSize = date.sizeSp * u; color = date.color.int; textAlign = align(date.align)
        }
        c.drawText(dateText(date, timeMs), date.x * w, date.y * h - p.fontMetrics.ascent * 0.9f, p)
    }

    fun elementRect(e: CanvasElement, w: Int, h: Int) = RectF(e.x * w, e.y * h, (e.x + e.w) * w, (e.y + e.h) * h)

    /** Draws one decoration. [wallpaper] (unblurred) is needed for glass panels. */
    fun drawElement(c: Canvas, e: CanvasElement, w: Int, h: Int, assetsDir: File?, wallpaper: Bitmap?, blurCache: MutableMap<Int, Bitmap> = HashMap()) {
        val u = w / 360f
        val r = elementRect(e, w, h)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val alpha = (e.opacity.coerceIn(0f, 1f) * 255).roundToInt()
        c.save()
        if (e.rotation != 0f) c.rotate(e.rotation, r.centerX(), r.centerY())
        val corner = e.float(Props.CORNER, 0f) * u
        when (e.type) {
            ElementType.SHAPE -> {
                p.color = e.color(Props.FILL, ArgbColor.White.withAlpha(0.3f)).int
                p.alpha = (p.alpha * alpha / 255f).roundToInt()
                when (e.prop(Props.SHAPE) ?: "rect") {
                    "circle" -> c.drawCircle(r.centerX(), r.centerY(), minOf(r.width(), r.height()) / 2, p)
                    "pill" -> c.drawRoundRect(r, minOf(r.width(), r.height()) / 2, minOf(r.width(), r.height()) / 2, p)
                    else -> c.drawRoundRect(r, corner, corner, p)
                }
                e.prop(Props.STROKE)?.let(ArgbColor::parse)?.let { s ->
                    p.style = Paint.Style.STROKE; p.strokeWidth = u; p.color = s.int
                    c.drawRoundRect(r, corner, corner, p)
                }
            }
            ElementType.GLASS_PANEL -> {
                val rad = if (corner > 0) corner else 24 * u
                val blurPx = (e.float(Props.BLUR, 24f) * u).roundToInt()
                val blurred = wallpaper?.let { wp -> blurCache.getOrPut(blurPx) { Bitmaps.blur(wp, blurPx.toFloat()) } }
                val path = android.graphics.Path().apply { addRoundRect(r, rad, rad, android.graphics.Path.Direction.CW) }
                c.save(); c.clipPath(path)
                if (blurred != null) { p.alpha = alpha; c.drawBitmap(blurred, 0f, 0f, p) }
                p.color = ArgbColor.White.withAlpha(e.float(Props.TINT_ALPHA, 0.16f) * e.opacity).int
                c.drawRect(r, p)
                c.restore()
                p.style = Paint.Style.STROKE; p.strokeWidth = u.coerceAtLeast(1f); p.color = ArgbColor.White.withAlpha(0.22f * e.opacity).int
                c.drawRoundRect(r, rad, rad, p)
            }
            ElementType.TEXT -> {
                val font = e.prop(Props.FONT)?.let { runCatching { SystemFont.valueOf(it) }.getOrNull() } ?: SystemFont.SANS
                val a = e.prop(Props.ALIGN)?.let { runCatching { TextAlign.valueOf(it) }.getOrNull() } ?: TextAlign.START
                p.typeface = Typefaces.of(font, e.prop(Props.WEIGHT)?.toIntOrNull() ?: font.weight)
                p.textSize = e.float(Props.FONT_SIZE, 16f) * u
                p.color = e.color(Props.COLOR, ArgbColor.White).int; p.alpha = (p.alpha * alpha / 255f).roundToInt()
                p.textAlign = align(a)
                val x = when (a) { TextAlign.START -> r.left; TextAlign.CENTER -> r.centerX(); TextAlign.END -> r.right }
                c.drawText(e.prop(Props.TEXT).orEmpty(), x, r.top - p.fontMetrics.ascent * 0.9f, p)
            }
            ElementType.IMAGE -> {
                val file = e.prop(Props.ASSET)?.let { path -> assetsDir?.let { File(it, path) } }
                val bmp = file?.let { Bitmaps.decodeSampled(it, r.width().roundToInt().coerceAtLeast(1), r.height().roundToInt().coerceAtLeast(1)) }
                c.save()
                if (corner > 0) c.clipPath(android.graphics.Path().apply { addRoundRect(r, corner, corner, android.graphics.Path.Direction.CW) })
                if (bmp != null) { p.alpha = alpha; Bitmaps.drawCover(c, bmp, r, paint = p) } else { p.color = 0x33FFFFFF; c.drawRect(r, p) }
                c.restore()
            }
            ElementType.WIDGET_REF, ElementType.ICON_GRID -> {
                val rad = 18 * u
                p.color = 0x24FFFFFF; c.drawRoundRect(r, rad, rad, p)
                p.style = Paint.Style.STROKE; p.strokeWidth = u; p.color = 0x66FFFFFF; p.pathEffect = DashPathEffect(floatArrayOf(6 * u, 4 * u), 0f)
                c.drawRoundRect(r, rad, rad, p)
                p.pathEffect = null; p.style = Paint.Style.FILL; p.color = 0xCCFFFFFF.toInt(); p.textSize = 12 * u; p.textAlign = Paint.Align.CENTER
                p.typeface = Typefaces.of(SystemFont.SANS_MEDIUM, 500)
                c.drawText(e.prop(Props.LABEL) ?: "Widget", r.centerX(), r.centerY() + 4 * u, p)
            }
            ElementType.CLOCK, ElementType.DATE -> Unit
        }
        c.restore()
    }

    private fun drawGhosts(c: Canvas, spec: ThemeSpec, w: Int, h: Int) {
        val u = w / 360f
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val lock = spec.lockScreen
        if (lock.showNotificationGhost) {
            p.color = 0x1FFFFFFF
            val top = h * 0.58f
            for (i in 0 until 2) {
                val r = RectF(16 * u, top + i * 76 * u, w - 16 * u, top + i * 76 * u + 66 * u)
                c.drawRoundRect(r, 20 * u, 20 * u, p)
            }
        }
        if (lock.showFodGhost) {
            val f = lock.fodArea
            p.style = Paint.Style.STROKE; p.strokeWidth = 1.5f * u; p.color = 0x80FFFFFF.toInt()
            p.pathEffect = DashPathEffect(floatArrayOf(4 * u, 4 * u), 0f)
            c.drawCircle(f.x * w, f.y * h, f.r * w, p)
            p.pathEffect = null
        }
    }
}
