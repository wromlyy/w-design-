package com.wdesign.core.data

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.wdesign.core.model.DesignProject
import com.wdesign.core.render.LockRenderOptions
import com.wdesign.core.render.LockScreenRenderer
import com.wdesign.core.render.WallpaperRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class WallpaperTarget { HOME, LOCK, BOTH }

/**
 * Applies wallpapers through the public WallpaperManager API. For the lock screen W Design
 * applies the wallpaper with its decorations (shapes, glass panels, text, images) baked in but
 * WITHOUT the clock and date: the system lock screen keeps drawing its own clock. W Design never
 * touches the keyguard, PIN or biometrics.
 */
class WallpaperApplier(private val context: Context) {

    suspend fun apply(project: DesignProject, assetsDir: java.io.File?, target: WallpaperTarget, w: Int, h: Int): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val wm = WallpaperManager.getInstance(context)
            check(wm.isWallpaperSupported && wm.isSetWallpaperAllowed) { "Wallpaper changes are not allowed on this device" }
            val full = Rect(0, 0, w, h)
            if (target != WallpaperTarget.LOCK) {
                val home: Bitmap = WallpaperRenderer.render(project.spec.wallpaper, w, h, assetsDir)
                wm.setBitmap(home, full, true, WallpaperManager.FLAG_SYSTEM)
            }
            if (target != WallpaperTarget.HOME) {
                val lock = LockScreenRenderer.render(
                    project.spec, w, h, assetsDir,
                    LockRenderOptions(includeClock = false, includeDate = false, includeGhosts = false),
                )
                wm.setBitmap(lock, full, true, WallpaperManager.FLAG_LOCK)
            }
        }
    }
}
