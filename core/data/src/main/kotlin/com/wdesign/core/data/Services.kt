package com.wdesign.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.wdesign.core.device.DeviceInspector
import com.wdesign.core.model.AppearanceMode
import com.wdesign.core.model.DeviceSnapshot
import com.wdesign.core.render.MtzExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

data class AppSettings(
    val themeMode: AppearanceMode = AppearanceMode.DARK,
    val reduceMotion: Boolean = false,
    val reduceTransparency: Boolean = false,
    val activeProjectId: String? = null,
)

private val Context.wdSettings: DataStore<Preferences> by preferencesDataStore(name = "wd_settings")

class SettingsStore(private val context: Context) {
    private object K {
        val mode = stringPreferencesKey("theme_mode")
        val motion = booleanPreferencesKey("reduce_motion")
        val transparency = booleanPreferencesKey("reduce_transparency")
        val active = stringPreferencesKey("active_project")
    }

    // A corrupted preferences file must never crash the app: fall back to defaults.
    val settings: Flow<AppSettings> = context.wdSettings.data.catch { e ->
        CrashLog.record(e)
        emit(androidx.datastore.preferences.core.emptyPreferences())
    }.map { p ->
        AppSettings(
            themeMode = p[K.mode]?.let { runCatching { AppearanceMode.valueOf(it) }.getOrNull() } ?: AppearanceMode.DARK,
            reduceMotion = p[K.motion] ?: false,
            reduceTransparency = p[K.transparency] ?: false,
            activeProjectId = p[K.active],
        )
    }

    suspend fun setThemeMode(m: AppearanceMode) { context.wdSettings.edit { it[K.mode] = m.name } }
    suspend fun setReduceMotion(v: Boolean) { context.wdSettings.edit { it[K.motion] = v } }
    suspend fun setReduceTransparency(v: Boolean) { context.wdSettings.edit { it[K.transparency] = v } }
    suspend fun setActiveProject(id: String?) { context.wdSettings.edit { if (id == null) it.remove(K.active) else it[K.active] = id } }
}

/** Lazily created app-wide services. Nothing heavy happens in Application.onCreate. */
class Services(val context: Context) {
    val projects by lazy { ProjectStore(context) }
    val settings by lazy { SettingsStore(context) }
    val device by lazy { DeviceInspector(context) }
    val mtz by lazy { MtzExporter(context) }
    val wallpapers by lazy { WallpaperApplier(context) }

    @Volatile private var cachedSnapshot: DeviceSnapshot? = null
    suspend fun snapshot(refresh: Boolean = false): DeviceSnapshot = withContext(Dispatchers.IO) {
        val cached = cachedSnapshot
        if (!refresh && cached != null) cached else device.snapshot().also { cachedSnapshot = it }
    }

    /** Scratch folder for import staging and exports shared through FileProvider (cache/exports). */
    fun stagingDir(name: String): File = File(context.cacheDir, "staging/$name").apply { deleteRecursively(); mkdirs() }
    fun exportsDir(): File = File(context.cacheDir, "exports").apply { mkdirs() }
}

/** Service locator, initialised by the Application. */
object WD {
    lateinit var services: Services
        private set

    fun init(context: Context) {
        if (!::services.isInitialized) services = Services(context.applicationContext)
    }
}
