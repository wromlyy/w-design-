package com.wdesign.core.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.wdesign.core.model.ArgbColor
import com.wdesign.core.model.WallpaperSpec
import com.wdesign.core.model.WallpaperType
import java.io.File
import java.util.Random
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Renders every [WallpaperType] deterministically (same spec + seed → same pixels). */
object WallpaperRenderer {

    fun render(spec: WallpaperSpec, w: Int, h: Int, assetsDir: File?): Bitmap {
        val bmp = Bitmap.createBitmap(w.coerceAtLeast(1), h.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        draw(Canvas(bmp), spec, w, h, assetsDir)
        return if (spec.blur > 0.5f) Bitmaps.blur(bmp, spec.blur * w / 360f) else bmp
    }

    fun draw(c: Canvas, spec: WallpaperSpec, w: Int, h: Int, assetsDir: File?) {
        val colors = spec.colors.ifEmpty { listOf(ArgbColor.rgb(0x16191E)) }.map { it.int }
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val rnd = Random(spec.seed)
        val wf = w.toFloat(); val hf = h.toFloat()
        when (spec.type) {
            WallpaperType.SOLID -> c.drawColor(colors.first())
            WallpaperType.GRADIENT -> {
                val a = Math.toRadians(spec.angle.toDouble())
                val r = hypot(wf, hf) / 2f
                val cx = wf / 2; val cy = hf / 2
                val dx = (cos(a) * r).toFloat(); val dy = (sin(a) * r).toFloat()
                p.shader = LinearGradient(cx - dx, cy - dy, cx + dx, cy + dy, if (colors.size == 1) intArrayOf(colors[0], colors[0]) else colors.toIntArray(), null, Shader.TileMode.CLAMP)
                c.drawRect(0f, 0f, wf, hf, p)
            }
            WallpaperType.MESH -> {
                c.drawColor(colors.first())
                val n = 3 + (spec.complexity * 4).toInt()
                repeat(n) { i ->
                    val col = colors[(i + 1) % colors.size]
                    val x = rnd.nextFloat() * wf; val y = rnd.nextFloat() * hf
                    val rad = (0.45f + rnd.nextFloat() * 0.5f) * maxOf(wf, hf) * 0.7f
                    p.shader = RadialGradient(x, y, rad, intArrayOf(col, ArgbColor(col).withAlpha(0).int), null, Shader.TileMode.CLAMP)
                    c.drawRect(0f, 0f, wf, hf, p)
                }
            }
            WallpaperType.ABSTRACT -> {
                c.drawColor(colors.first())
                val n = 4 + (spec.complexity * 8).toInt()
                repeat(n) { i ->
                    val col = ArgbColor(colors[(i + 1) % colors.size]).withAlpha(90 + rnd.nextInt(110)).int
                    val path = Path()
                    val sx = rnd.nextFloat() * wf; val sy = rnd.nextFloat() * hf
                    path.moveTo(sx, sy)
                    repeat(3) {
                        path.cubicTo(rnd.nextFloat() * wf, rnd.nextFloat() * hf, rnd.nextFloat() * wf, rnd.nextFloat() * hf, rnd.nextFloat() * wf, rnd.nextFloat() * hf)
                    }
                    path.close()
                    p.shader = null; p.color = col; p.style = Paint.Style.FILL
                    c.drawPath(path, p)
                }
                // soft overlay so blobs blend
                p.shader = LinearGradient(0f, 0f, 0f, hf, intArrayOf(ArgbColor(colors.first()).withAlpha(0).int, ArgbColor(colors.first()).withAlpha(140).int), null, Shader.TileMode.CLAMP)
                c.drawRect(0f, 0f, wf, hf, p)
            }
            WallpaperType.GEOMETRIC -> {
                c.drawColor(colors.first())
                val cell = wf / (4 + (spec.complexity * 6).toInt())
                var y = 0f
                while (y < hf) {
                    var x = 0f
                    while (x < wf) {
                        val idx = if (colors.size > 1) 1 + rnd.nextInt(colors.size - 1) else 0
                        val col = ArgbColor(colors[idx]).withAlpha(40 + rnd.nextInt(160)).int
                        p.shader = null; p.color = col
                        when (rnd.nextInt(4)) {
                            0 -> c.drawCircle(x + cell / 2, y + cell / 2, cell * 0.36f, p)
                            1 -> Path().apply { moveTo(x, y + cell); lineTo(x + cell / 2, y); lineTo(x + cell, y + cell); close() }.let { c.drawPath(it, p) }
                            2 -> c.drawRoundRect(RectF(x + cell * 0.12f, y + cell * 0.12f, x + cell * 0.88f, y + cell * 0.88f), cell * 0.2f, cell * 0.2f, p)
                            else -> Unit
                        }
                        x += cell
                    }
                    y += cell
                }
            }
            WallpaperType.PHOTO -> {
                val file = spec.photo?.let { ref -> assetsDir?.let { File(it, ref.path) } }
                val src = file?.let { Bitmaps.decodeSampled(it, w, h) }
                if (src == null) c.drawColor(colors.first()) else Bitmaps.drawCover(c, src, RectF(0f, 0f, wf, hf), spec.crop)
            }
        }
        if (spec.grain > 0.001f && spec.type != WallpaperType.PHOTO) {
            p.shader = null
            val dots = (w * h / 900f * spec.grain * 10).toInt().coerceAtMost(60_000)
            val g = Random(spec.seed xor 0x5DEECE66DL)
            repeat(dots) {
                p.color = if (g.nextBoolean()) 0x14FFFFFF else 0x14000000
                val x = g.nextFloat() * wf; val y = g.nextFloat() * hf
                c.drawRect(x, y, x + 1.5f, y + 1.5f, p)
            }
        }
        if (spec.dim > 0.001f) c.drawColor(ArgbColor.Black.withAlpha(spec.dim.coerceIn(0f, 0.9f)).int)
    }
}
