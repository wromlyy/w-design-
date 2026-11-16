package com.wdesign.core.device

import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Deep links into Xiaomi and Android system screens. Every call is guarded: when the target is
 * missing (other ROM, other version) it falls back to this app's system details page and
 * returns false so the UI can explain what happened.
 */
object XiaomiIntents {

    private fun Context.tryStart(vararg intents: Intent): Boolean {
        for (i in intents) {
            try {
                startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }
        return false
    }

    fun appDetails(context: Context): Boolean = context.tryStart(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
    )

    /** HyperOS/MIUI "Other permissions" editor (home screen shortcuts, background pop-ups…). */
    fun permissionEditor(context: Context): Boolean = context.tryStart(
        Intent("miui.intent.action.APP_PERM_EDITOR")
            .setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
            .putExtra("extra_pkgname", context.packageName),
        Intent("miui.intent.action.APP_PERM_EDITOR").putExtra("extra_pkgname", context.packageName),
    ) || appDetails(context).let { false }

    fun autostart(context: Context): Boolean = context.tryStart(
        Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
    ) || appDetails(context).let { false }

    fun batteryOptimization(context: Context): Boolean = context.tryStart(
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
    ) || appDetails(context).let { false }

    /** Opens the Themes app (com.android.thememanager). */
    fun themesApp(context: Context): Boolean {
        val launch = context.packageManager.getLaunchIntentForPackage(DeviceInspector.THEME_MANAGER)
        return (launch != null && context.tryStart(launch))
    }

    fun homeSettings(context: Context): Boolean = context.tryStart(Intent(Settings.ACTION_HOME_SETTINGS), Intent(Settings.ACTION_SETTINGS))

    fun liveWallpaperPicker(context: Context): Boolean = context.tryStart(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))

    /** Wallpaper settings, where HyperOS keeps the lock screen editor and wallpaper carousel switches. */
    fun wallpaperSettings(context: Context): Boolean = context.tryStart(
        Intent().setComponent(ComponentName("com.android.thememanager", "com.android.thememanager.settings.WallpaperSettingsActivity")),
        Intent(Intent.ACTION_SET_WALLPAPER),
        Intent(Settings.ACTION_DISPLAY_SETTINGS),
    )

    /** Opens a content:// MTZ in the Themes app. Returns false when Themes cannot open it. */
    fun openMtzInThemes(context: Context, uri: Uri): Boolean = context.tryStart(
        Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/octet-stream")
            .setPackage(DeviceInspector.THEME_MANAGER).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
    )

    fun openUrl(context: Context, url: String): Boolean = context.tryStart(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}
