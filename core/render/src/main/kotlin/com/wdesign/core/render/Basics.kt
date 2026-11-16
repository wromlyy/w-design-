package com.wdesign.core.render

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import com.wdesign.core.model.ArgbColor
import com.wdesign.core.model.CropSpec
import com.wdesign.core.model.SystemFont
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

internal val ArgbColor.int: Int get() = argb

object Typefaces {
    /** A real system typeface for a [SystemFont] (HyperOS maps "sans-serif" to MiSans). */
    fun of(font: SystemFont, weight: Int = font.weight): Typeface {
        val base = Typeface.create(font.family, Typeface.NORMAL)
        return if (Build.VERSION.SDK_INT >= 28 && font.family == "sans-serif") Typeface.create(base, weight.coerceIn(100, 900), false)
        else if (weight >= 600 && Build.VERSION.SDK_INT < 28) Typeface.create(base, Typeface.BOLD) else base
    }
}

object Bitmaps {
    /** Decodes a file sampled down so neither side greatly exceeds [maxW]×[maxH] (no OOM on huge photos). */
    fun decodeSampled(file: File, maxW: Int, maxH: Int): Bitmap? {
        if (!file.isFile) return null
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, o)
        if (o.outWidth <= 0 || o.outHeight <= 0) return null
        var s = 1
        while (o.outWidth / (s * 2) >= maxW && o.outHeight / (s * 2) >= maxH) s *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = s })
    }

    fun decodeSampled(bytes: ByteArray, maxW: Int, maxH: Int): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        if (o.outWidth <= 0) return null
        var s = 1
        while (o.outWidth / (s * 2) >= maxW && o.outHeight / (s * 2) >= maxH) s *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = s })
    }

    /** Draws [src] into [dst] covering it (centre-crop) with an optional pan/zoom [crop]. */
    fun drawCover(canvas: Canvas, src: Bitmap, dst: RectF, crop: CropSpec = CropSpec(), paint: Paint? = null) {
        val scale = max(dst.width() / src.width, dst.height() / src.height) * crop.zoom.coerceIn(1f, 5f)
        val vw = dst.width() / scale; val vh = dst.height() / scale
        val cx = (src.width * crop.cx).coerceIn(vw / 2, src.width - vw / 2)
        val cy = (src.height * crop.cy).coerceIn(vh / 2, src.height - vh / 2)
        val s = Rect((cx - vw / 2).roundToInt(), (cy - vh / 2).roundToInt(), (cx + vw / 2).roundToInt(), (cy + vh / 2).roundToInt())
        canvas.drawBitmap(src, s, dst, paint ?: Paint(Paint.FILTER_BITMAP_FLAG))
    }

    /** Fast approximate blur: downscale then upscale twice with filtering. radius in px. */
    fun blur(src: Bitmap, radiusPx: Float): Bitmap {
        if (radiusPx < 1f) return src
        val factor = (radiusPx / 2f).coerceIn(2f, 32f)
        val w = max(1, (src.width / factor).roundToInt()); val h = max(1, (src.height / factor).roundToInt())
        var small = Bitmap.createScaledBitmap(src, w, h, true)
        small = Bitmap.createScaledBitmap(small, max(1, w / 2), max(1, h / 2), true)
        small = Bitmap.createScaledBitmap(small, w, h, true)
        return Bitmap.createScaledBitmap(small, src.width, src.height, true)
    }

    fun png(b: Bitmap): ByteArray = ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    fun jpeg(b: Bitmap, q: Int = 92): ByteArray {
        val opaque = if (b.hasAlpha()) Bitmap.createBitmap(b.width, b.height, Bitmap.Config.ARGB_8888).also {
            Canvas(it).apply { drawColor(android.graphics.Color.BLACK); drawBitmap(b, 0f, 0f, null) }
        } else b
        return ByteArrayOutputStream().also { opaque.compress(Bitmap.CompressFormat.JPEG, q, it) }.toByteArray()
    }
}
