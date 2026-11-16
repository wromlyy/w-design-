package com.wdesign.core.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.wdesign.core.model.ClockSpec
import com.wdesign.core.model.SystemFont
import com.wdesign.core.model.TextAlign
import com.wdesign.core.model.ThemeSpec
import java.io.File
import kotlin.math.ceil

/** Rasterizes clock glyphs into the MAML <Time> image set: <prefix>_0..9.png and <prefix>_dot.png. */
object DigitsRasterizer {
    fun digits(prefix: String, clock: ClockSpec, sizePx: Int): Map<String, ByteArray> {
        val style = clock.copy(align = TextAlign.START, x = 0f, y = 0f)
        val p = LockScreenRenderer.clockPaint(style, sizePx.toFloat())
        val height = ceil(sizePx * 1.05f).toInt().coerceAtLeast(1)
        val glyphs = (0..9).map { "$it" to "${prefix}_$it.png" } + (":" to "${prefix}_dot.png")
        val digitWidth = (0..9).maxOf { p.measureText("$it") }
        return glyphs.associate { (g, file) ->
            val wpx = ceil(if (g == ":") p.measureText(":") * 1.2f else digitWidth).toInt().coerceAtLeast(1) + 2
            val b = Bitmap.createBitmap(wpx, height, Bitmap.Config.ARGB_8888)
            val c = Canvas(b)
            val dx = if (g == ":") 0f else (digitWidth - p.measureText(g)) / 2f
            LockScreenRenderer.drawClockLine(c, g, 1f + dx, 0f, style, sizePx.toFloat())
            file to Bitmaps.png(b)
        }
    }
}

/** Home-screen and icon-sheet previews (also used for MTZ preview images). */
object HomePreviewRenderer {
    /** [icons] are already-styled bitmaps; when fewer than the grid needs, placeholders are drawn. */
    fun home(spec: ThemeSpec, w: Int, h: Int, assetsDir: File?, icons: List<Bitmap>, labels: List<String> = emptyList()): Bitmap {
        val out = WallpaperRenderer.render(spec.wallpaper, w, h, assetsDir)
        val c = Canvas(out)
        val u = w / 360f
        val home = spec.home
        val cols = home.columns.coerceIn(3, 6)
        val rows = home.rows.coerceIn(3, 7)
        val cellW = (w - 32 * u) / cols
        val iconPx = (home.iconSizeDp * u).coerceAtMost(cellW * 0.86f)
        val top = 64 * u
        val dockTop = h - 120 * u
        val cellH = (dockTop - top - 24 * u) / rows
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = spec.palette.onSurface.argb; textSize = 11 * u; textAlign = Paint.Align.CENTER; typeface = Typefaces.of(spec.appearance.font) }
        val placeholder = { i: Int -> IconRenderer.render(null, spec.icons, iconPx.toInt().coerceAtLeast(8), ('A' + i % 26).toString()) }
        var idx = 0
        for (r in 0 until rows - 2) for (col in 0 until cols) {
            val bmp = icons.getOrNull(idx) ?: placeholder(idx)
            val x = 16 * u + col * cellW + (cellW - iconPx) / 2
            val y = top + r * cellH
            c.drawBitmap(bmp, null, RectF(x, y, x + iconPx, y + iconPx), Paint(Paint.FILTER_BITMAP_FLAG))
            if (home.labels) c.drawText(labels.getOrNull(idx)?.take(10) ?: "App ${idx + 1}", x + iconPx / 2, y + iconPx + 14 * u, label)
            idx++
        }
        if (home.dock) {
            val dock = RectF(14 * u, dockTop, w - 14 * u, dockTop + 92 * u)
            val dp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = spec.appearance.glass.tint.withAlpha(spec.appearance.glass.opacity).argb }
            c.drawRoundRect(dock, spec.appearance.cornerRadiusDp * u * 1.4f, spec.appearance.cornerRadiusDp * u * 1.4f, dp)
            for (col in 0 until 4) {
                val bmp = icons.getOrNull(idx + col) ?: placeholder(idx + col)
                val cw = dock.width() / 4
                val x = dock.left + col * cw + (cw - iconPx) / 2
                val y = dock.centerY() - iconPx / 2
                c.drawBitmap(bmp, null, RectF(x, y, x + iconPx, y + iconPx), Paint(Paint.FILTER_BITMAP_FLAG))
            }
        }
        return out
    }

    fun iconSheet(spec: ThemeSpec, w: Int, h: Int, icons: List<Bitmap>): Bitmap {
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(spec.palette.background.argb)
        val u = w / 360f
        val cols = 4
        val cell = (w - 32 * u) / cols
        val icon = cell * 0.72f
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = spec.palette.onSurface.argb; textSize = 22 * u; typeface = Typefaces.of(SystemFont.SANS_MEDIUM, 500) }
        c.drawText("Icons", 20 * u, 56 * u, title)
        val count = ((h - 90 * u) / cell).toInt() * cols
        for (i in 0 until count) {
            val bmp = icons.getOrNull(i) ?: IconRenderer.render(null, spec.icons, icon.toInt().coerceAtLeast(8), ('A' + i % 26).toString())
            val x = 16 * u + (i % cols) * cell + (cell - icon) / 2
            val y = 84 * u + (i / cols) * cell
            c.drawBitmap(bmp, null, RectF(x, y, x + icon, y + icon), Paint(Paint.FILTER_BITMAP_FLAG))
        }
        return out
    }
}
