package com.wdesign.ds

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wdesign.core.model.ThemeSpec
import com.wdesign.core.render.HomePreviewRenderer
import com.wdesign.core.render.LockRenderOptions
import com.wdesign.core.render.LockScreenRenderer
import java.io.File

/** Phone-shaped rendered preview of a design's lock or home screen (rendered off the main thread). */
@Composable
fun DesignPreview(spec: ThemeSpec, assets: File?, lock: Boolean, modifier: Modifier = Modifier, widthPx: Int = 360, corner: Dp = 20.dp) {
    val h = (widthPx * 2.1667f).toInt()
    val bmp by rememberBitmap(spec, lock, widthPx, assets?.path) {
        if (lock) LockScreenRenderer.render(spec, widthPx, h, assets, LockRenderOptions(timeMs = System.currentTimeMillis()))
        else HomePreviewRenderer.home(spec, widthPx, h, assets, emptyList())
    }
    val shape = RoundedCornerShape(corner)
    Box(modifier.aspectRatio(widthPx.toFloat() / h).clip(shape).background(W.colors.raised2).border(1.dp, W.colors.outline, shape)) {
        bmp?.let {
            Image(it.asImageBitmap(), stringResource(if (lock) R.string.ds_preview_lock else R.string.ds_preview_home), Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}
