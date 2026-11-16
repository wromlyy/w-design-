package com.wdesign.core.device

import android.app.WallpaperManager
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.content.pm.ShortcutManagerCompat
import com.wdesign.core.model.DeviceSnapshot
import com.wdesign.core.model.SystemUiVersion

/** Reads what this device and its system UI actually support. Cheap; call off the main thread when possible. */
class DeviceInspector(private val context: Context) {

    /** Reads a system property via android.os.SystemProperties (reflection), falling back to `getprop`. */
    fun prop(name: String): String? {
        val viaReflection = runCatching {
            val c = Class.forName("android.os.SystemProperties")
            c.getMethod("get", String::class.java).invoke(null, name) as? String
        }.getOrNull()
        if (!viaReflection.isNullOrBlank()) return viaReflection
        return runCatching {
            val p = ProcessBuilder("getprop", name).redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().use { it.readText() }.trim()
            p.waitFor()
            out.ifBlank { null }
        }.getOrNull()
    }

    fun systemUi(): SystemUiVersion = SystemUiVersion.parse(
        osName = prop("ro.mi.os.version.name"),
        osIncremental = prop("ro.mi.os.version.incremental") ?: prop("ro.build.version.incremental"),
        miuiName = prop("ro.miui.ui.version.name"),
        osCode = prop("ro.mi.os.version.code"),
    )

    @Suppress("DEPRECATION")
    fun screenSize(): Pair<Int, Int> {
        val wm = context.getSystemService(WindowManager::class.java)
        return if (Build.VERSION.SDK_INT >= 30) {
            val b = wm.maximumWindowMetrics.bounds
            minOf(b.width(), b.height()) to maxOf(b.width(), b.height())
        } else {
            val m = DisplayMetrics()
            wm.defaultDisplay.getRealMetrics(m)
            minOf(m.widthPixels, m.heightPixels) to maxOf(m.widthPixels, m.heightPixels)
        }
    }

    @Suppress("DEPRECATION")
    fun refreshRate(): Float = runCatching {
        if (Build.VERSION.SDK_INT >= 30) context.display?.refreshRate ?: 60f
        else context.getSystemService(WindowManager::class.java).defaultDisplay.refreshRate
    }.getOrDefault(60f)

    fun themeManagerVersion(): String? = runCatching {
        context.packageManager.getPackageInfo(THEME_MANAGER, 0).versionName ?: "?"
    }.getOrNull()

    fun homePackage(): String? = runCatching {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        context.packageManager.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
    }.getOrNull()

    fun snapshot(): DeviceSnapshot {
        val pm = context.packageManager
        val wallpaper = WallpaperManager.getInstance(context)
        val (w, h) = screenSize()
        val tm = themeManagerVersion()
        return DeviceSnapshot(
            manufacturer = Build.MANUFACTURER.orEmpty(),
            brand = Build.BRAND.orEmpty(),
            model = Build.MODEL.orEmpty(),
            androidRelease = Build.VERSION.RELEASE.orEmpty(),
            sdkInt = Build.VERSION.SDK_INT,
            system = systemUi(),
            screenWidthPx = w,
            screenHeightPx = h,
            densityDpi = context.resources.displayMetrics.densityDpi,
            refreshRate = refreshRate(),
            wallpaperSupported = runCatching { wallpaper.isWallpaperSupported }.getOrDefault(false),
            setWallpaperAllowed = runCatching { wallpaper.isSetWallpaperAllowed }.getOrDefault(false),
            liveWallpaperFeature = pm.hasSystemFeature(PackageManager.FEATURE_LIVE_WALLPAPER),
            appWidgetsFeature = pm.hasSystemFeature(PackageManager.FEATURE_APP_WIDGETS),
            pinWidgetSupported = runCatching { AppWidgetManager.getInstance(context)?.isRequestPinAppWidgetSupported == true }.getOrDefault(false),
            pinShortcutSupported = runCatching { ShortcutManagerCompat.isRequestPinShortcutSupported(context) }.getOrDefault(false),
            themeManagerInstalled = tm != null,
            themeManagerVersion = tm,
            homePackage = homePackage(),
            powerSave = runCatching { context.getSystemService(PowerManager::class.java).isPowerSaveMode }.getOrDefault(false),
        )
    }

    companion object {
        const val THEME_MANAGER = "com.android.thememanager"
    }
}
