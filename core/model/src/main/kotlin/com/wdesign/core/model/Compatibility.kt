package com.wdesign.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class SystemUiKind { HYPEROS, MIUI, OTHER }

/**
 * Parsed Xiaomi system version. HyperOS reports ro.mi.os.version.name like "OS2.0" and an
 * incremental like "OS2.0.212.0.VNCCNXM"; MIUI reports ro.miui.ui.version.name like "V140".
 */
@Serializable
data class SystemUiVersion(
    val kind: SystemUiKind,
    val major: Int = 0,
    val minor: Int = 0,
    val raw: String = "",
    /** Two-letter region code from the incremental (CN, MI = global, EU, IN, RU, ID, TW, TR, JP…). */
    val region: String? = null,
) {
    val isHyperOs: Boolean get() = kind == SystemUiKind.HYPEROS
    val isXiaomi: Boolean get() = kind != SystemUiKind.OTHER
    val label: String
        get() = when (kind) {
            SystemUiKind.HYPEROS -> "HyperOS $major.$minor"
            SystemUiKind.MIUI -> "MIUI $major"
            SystemUiKind.OTHER -> raw.ifBlank { "Android" }
        }

    companion object {
        private val OS_RE = Regex("""^OS(\d+)(?:\.(\d+))?""", RegexOption.IGNORE_CASE)
        private val MIUI_RE = Regex("""^V?(\d{1,3})""", RegexOption.IGNORE_CASE)
        val REGIONS = mapOf(
            "CN" to "China", "MI" to "Global", "EU" to "Europe (EEA)", "IN" to "India", "RU" to "Russia",
            "ID" to "Indonesia", "TW" to "Taiwan", "TR" to "Türkiye", "JP" to "Japan", "KR" to "Korea",
            "LM" to "Latin America", "TH" to "Thailand", "MX" to "Mexico",
        )

        /**
         * @param osName ro.mi.os.version.name (e.g. "OS1.0" or "OS2.0")
         * @param osIncremental ro.mi.os.version.incremental / ro.build.version.incremental
         * @param miuiName ro.miui.ui.version.name (e.g. "V140", "V816")
         * @param osCode ro.mi.os.version.code (e.g. "1", "2")
         */
        fun parse(osName: String?, osIncremental: String? = null, miuiName: String? = null, osCode: String? = null): SystemUiVersion {
            val inc = osIncremental?.trim().orEmpty()
            val region = regionFromIncremental(inc)
            for (candidate in listOfNotNull(osName?.trim(), inc.takeIf { it.startsWith("OS", true) })) {
                OS_RE.find(candidate)?.let { m ->
                    val major = m.groupValues[1].toInt()
                    val minor = m.groupValues[2].toIntOrNull() ?: 0
                    return SystemUiVersion(SystemUiKind.HYPEROS, major, minor, inc.ifBlank { candidate }, region)
                }
            }
            osCode?.trim()?.toIntOrNull()?.takeIf { it in 1..20 }?.let {
                return SystemUiVersion(SystemUiKind.HYPEROS, it, 0, osCode, region)
            }
            val miui = miuiName?.trim().orEmpty()
            if (miui.isNotEmpty()) {
                MIUI_RE.find(miui)?.let { m ->
                    val digits = m.groupValues[1]
                    // "V140" -> 14, "V12" -> 12, "V816" -> 8 (MIUI 8.x used three digits)
                    val major = when {
                        digits.length == 3 && digits.startsWith("1") -> digits.take(2).toInt()
                        digits.length == 3 -> digits.take(1).toInt()
                        digits.length == 2 && digits.toInt() > 14 -> digits.take(1).toInt()
                        else -> digits.toInt()
                    }
                    return SystemUiVersion(SystemUiKind.MIUI, major, 0, miui, region)
                }
            }
            return SystemUiVersion(SystemUiKind.OTHER, raw = "")
        }

        /** "OS1.0.3.0.UNCCNXM" -> "CN"; "V14.0.4.0.TMRMIXM" -> "MI". */
        fun regionFromIncremental(inc: String): String? {
            val last = inc.substringAfterLast('.', "")
            if (last.length != 7 || !last.all { it.isLetter() }) return null
            return last.substring(3, 5).uppercase()
        }
    }
}

@Serializable
data class DeviceSnapshot(
    val manufacturer: String = "",
    val brand: String = "",
    val model: String = "",
    val androidRelease: String = "",
    val sdkInt: Int = 26,
    val system: SystemUiVersion = SystemUiVersion(SystemUiKind.OTHER),
    val screenWidthPx: Int = 1080,
    val screenHeightPx: Int = 2400,
    val densityDpi: Int = 440,
    val refreshRate: Float = 60f,
    val wallpaperSupported: Boolean = true,
    val setWallpaperAllowed: Boolean = true,
    val liveWallpaperFeature: Boolean = true,
    val appWidgetsFeature: Boolean = true,
    val pinWidgetSupported: Boolean = false,
    val pinShortcutSupported: Boolean = false,
    val themeManagerInstalled: Boolean = false,
    val themeManagerVersion: String? = null,
    val homePackage: String? = null,
    val powerSave: Boolean = false,
) {
    val isXiaomiFamily: Boolean
        get() = system.isXiaomi || manufacturer.equals("Xiaomi", true) ||
            brand.lowercase() in setOf("xiaomi", "redmi", "poco")
}

@Serializable
enum class SupportStatus { SUPPORTED, PARTIAL, REQUIRES_ACTION, UNSUPPORTED }

@Serializable
enum class FeatureId {
    WALLPAPER_HOME, WALLPAPER_LOCK, LIVE_WALLPAPER, WIDGETS, WIDGET_PINNING, PINNED_SHORTCUTS,
    LAUNCHER, QS_TILE, MTZ_BUILD, MTZ_INSTALL, SYSTEM_ICONS, LOCK_CLOCK, LOCK_MAML,
    CONTROL_CENTER, SYSTEM_FONTS, AOD,
}

/** Why a feature has its status. Each maps to a translated explanation in the UI. */
@Serializable
enum class Reason {
    OK, POLICY_BLOCKS_WALLPAPER, NO_WALLPAPER_SERVICE, HYPEROS_CAROUSEL_MAY_OVERRIDE, NO_LIVE_WALLPAPER_FEATURE,
    LIVE_LOCK_VARIES, NO_WIDGET_FEATURE, PIN_NOT_SUPPORTED_BY_LAUNCHER, XIAOMI_SHORTCUT_PERMISSION,
    USER_MUST_CHOOSE_HOME, TILE_REQUEST_API33, TILE_MANUAL_ADD, MTZ_ALWAYS_BUILDS, NOT_XIAOMI,
    NO_THEME_MANAGER, CN_IMPORT_AVAILABLE, GLOBAL_IMPORT_MISSING, ICONS_VIA_MTZ_ONLY, ICONS_NEED_XIAOMI,
    CLOCK_SYSTEM_EDITOR_OS3, CLOCK_SYSTEM_EDITOR_OS1, CLOCK_VIA_MTZ, CLOCK_NOT_XIAOMI, MAML_NEEDS_IMPORT,
    SYSTEM_SIGNATURE_REQUIRED, FONTS_STORE_ONLY, AOD_UNDOCUMENTED,
}

/** What the user can do. Each maps to a button or instruction in the UI. */
@Serializable
enum class HowTo {
    NONE, APPLY_IN_WALLPAPER_STUDIO, DISABLE_CAROUSEL, PICK_LIVE_WALLPAPER, ADD_FROM_WIDGET_PICKER,
    GRANT_SHORTCUT_PERMISSION, ENABLE_LAUNCHER, ADD_TILE, EXPORT_MTZ, IMPORT_IN_THEMES, PC_THEME_EDITOR,
    USE_W_LAUNCHER, OPEN_LOCK_EDITOR, USE_LOCK_WALLPAPER, USE_CONTROL_WIDGET, USE_THEMES_FONTS, USE_SYSTEM_AOD,
}

@Serializable
data class FeatureSupport(val id: FeatureId, val status: SupportStatus, val reason: Reason, val howTo: HowTo = HowTo.NONE)

/** Pure rules that turn a [DeviceSnapshot] into an honest support matrix. */
object CompatibilityMatrix {

    fun evaluate(s: DeviceSnapshot): List<FeatureSupport> {
        val xiaomi = s.isXiaomiFamily
        val os = s.system
        val list = mutableListOf<FeatureSupport>()

        list += when {
            !s.wallpaperSupported -> FeatureSupport(FeatureId.WALLPAPER_HOME, SupportStatus.UNSUPPORTED, Reason.NO_WALLPAPER_SERVICE)
            !s.setWallpaperAllowed -> FeatureSupport(FeatureId.WALLPAPER_HOME, SupportStatus.UNSUPPORTED, Reason.POLICY_BLOCKS_WALLPAPER)
            else -> FeatureSupport(FeatureId.WALLPAPER_HOME, SupportStatus.SUPPORTED, Reason.OK, HowTo.APPLY_IN_WALLPAPER_STUDIO)
        }
        list += when {
            !s.wallpaperSupported || !s.setWallpaperAllowed ->
                FeatureSupport(FeatureId.WALLPAPER_LOCK, SupportStatus.UNSUPPORTED, if (!s.wallpaperSupported) Reason.NO_WALLPAPER_SERVICE else Reason.POLICY_BLOCKS_WALLPAPER)
            xiaomi -> FeatureSupport(FeatureId.WALLPAPER_LOCK, SupportStatus.PARTIAL, Reason.HYPEROS_CAROUSEL_MAY_OVERRIDE, HowTo.DISABLE_CAROUSEL)
            else -> FeatureSupport(FeatureId.WALLPAPER_LOCK, SupportStatus.SUPPORTED, Reason.OK, HowTo.APPLY_IN_WALLPAPER_STUDIO)
        }
        list += when {
            !s.liveWallpaperFeature -> FeatureSupport(FeatureId.LIVE_WALLPAPER, SupportStatus.UNSUPPORTED, Reason.NO_LIVE_WALLPAPER_FEATURE)
            xiaomi -> FeatureSupport(FeatureId.LIVE_WALLPAPER, SupportStatus.PARTIAL, Reason.LIVE_LOCK_VARIES, HowTo.PICK_LIVE_WALLPAPER)
            else -> FeatureSupport(FeatureId.LIVE_WALLPAPER, SupportStatus.SUPPORTED, Reason.OK, HowTo.PICK_LIVE_WALLPAPER)
        }
        list += if (s.appWidgetsFeature) FeatureSupport(FeatureId.WIDGETS, SupportStatus.SUPPORTED, Reason.OK, HowTo.ADD_FROM_WIDGET_PICKER)
        else FeatureSupport(FeatureId.WIDGETS, SupportStatus.UNSUPPORTED, Reason.NO_WIDGET_FEATURE)
        list += when {
            !s.appWidgetsFeature -> FeatureSupport(FeatureId.WIDGET_PINNING, SupportStatus.UNSUPPORTED, Reason.NO_WIDGET_FEATURE)
            !s.pinWidgetSupported -> FeatureSupport(FeatureId.WIDGET_PINNING, SupportStatus.PARTIAL, Reason.PIN_NOT_SUPPORTED_BY_LAUNCHER, HowTo.ADD_FROM_WIDGET_PICKER)
            xiaomi -> FeatureSupport(FeatureId.WIDGET_PINNING, SupportStatus.REQUIRES_ACTION, Reason.XIAOMI_SHORTCUT_PERMISSION, HowTo.GRANT_SHORTCUT_PERMISSION)
            else -> FeatureSupport(FeatureId.WIDGET_PINNING, SupportStatus.SUPPORTED, Reason.OK)
        }
        list += when {
            !s.pinShortcutSupported -> FeatureSupport(FeatureId.PINNED_SHORTCUTS, SupportStatus.UNSUPPORTED, Reason.PIN_NOT_SUPPORTED_BY_LAUNCHER, HowTo.USE_W_LAUNCHER)
            xiaomi -> FeatureSupport(FeatureId.PINNED_SHORTCUTS, SupportStatus.REQUIRES_ACTION, Reason.XIAOMI_SHORTCUT_PERMISSION, HowTo.GRANT_SHORTCUT_PERMISSION)
            else -> FeatureSupport(FeatureId.PINNED_SHORTCUTS, SupportStatus.SUPPORTED, Reason.OK)
        }
        list += FeatureSupport(FeatureId.LAUNCHER, SupportStatus.REQUIRES_ACTION, Reason.USER_MUST_CHOOSE_HOME, HowTo.ENABLE_LAUNCHER)
        list += if (s.sdkInt >= 33) FeatureSupport(FeatureId.QS_TILE, SupportStatus.SUPPORTED, Reason.TILE_REQUEST_API33, HowTo.ADD_TILE)
        else FeatureSupport(FeatureId.QS_TILE, SupportStatus.REQUIRES_ACTION, Reason.TILE_MANUAL_ADD, HowTo.ADD_TILE)
        list += FeatureSupport(FeatureId.MTZ_BUILD, SupportStatus.SUPPORTED, Reason.MTZ_ALWAYS_BUILDS, HowTo.EXPORT_MTZ)

        val install = when {
            !xiaomi -> FeatureSupport(FeatureId.MTZ_INSTALL, SupportStatus.UNSUPPORTED, Reason.NOT_XIAOMI, HowTo.EXPORT_MTZ)
            !s.themeManagerInstalled -> FeatureSupport(FeatureId.MTZ_INSTALL, SupportStatus.UNSUPPORTED, Reason.NO_THEME_MANAGER, HowTo.PC_THEME_EDITOR)
            os.region == "CN" -> FeatureSupport(FeatureId.MTZ_INSTALL, SupportStatus.PARTIAL, Reason.CN_IMPORT_AVAILABLE, HowTo.IMPORT_IN_THEMES)
            else -> FeatureSupport(FeatureId.MTZ_INSTALL, SupportStatus.REQUIRES_ACTION, Reason.GLOBAL_IMPORT_MISSING, HowTo.PC_THEME_EDITOR)
        }
        list += install
        list += if (xiaomi) FeatureSupport(FeatureId.SYSTEM_ICONS, SupportStatus.PARTIAL, Reason.ICONS_VIA_MTZ_ONLY, HowTo.EXPORT_MTZ)
        else FeatureSupport(FeatureId.SYSTEM_ICONS, SupportStatus.UNSUPPORTED, Reason.ICONS_NEED_XIAOMI, HowTo.USE_W_LAUNCHER)
        list += when {
            os.isHyperOs && os.major >= 3 -> FeatureSupport(FeatureId.LOCK_CLOCK, SupportStatus.REQUIRES_ACTION, Reason.CLOCK_SYSTEM_EDITOR_OS3, HowTo.OPEN_LOCK_EDITOR)
            os.isHyperOs -> FeatureSupport(FeatureId.LOCK_CLOCK, SupportStatus.REQUIRES_ACTION, Reason.CLOCK_SYSTEM_EDITOR_OS1, HowTo.OPEN_LOCK_EDITOR)
            xiaomi -> FeatureSupport(FeatureId.LOCK_CLOCK, SupportStatus.PARTIAL, Reason.CLOCK_VIA_MTZ, HowTo.EXPORT_MTZ)
            else -> FeatureSupport(FeatureId.LOCK_CLOCK, SupportStatus.UNSUPPORTED, Reason.CLOCK_NOT_XIAOMI, HowTo.USE_LOCK_WALLPAPER)
        }
        list += if (xiaomi) FeatureSupport(FeatureId.LOCK_MAML, if (install.status == SupportStatus.UNSUPPORTED) SupportStatus.UNSUPPORTED else SupportStatus.PARTIAL, Reason.MAML_NEEDS_IMPORT, install.howTo)
        else FeatureSupport(FeatureId.LOCK_MAML, SupportStatus.UNSUPPORTED, Reason.NOT_XIAOMI, HowTo.USE_LOCK_WALLPAPER)
        list += FeatureSupport(FeatureId.CONTROL_CENTER, SupportStatus.UNSUPPORTED, Reason.SYSTEM_SIGNATURE_REQUIRED, HowTo.USE_CONTROL_WIDGET)
        list += FeatureSupport(FeatureId.SYSTEM_FONTS, SupportStatus.UNSUPPORTED, Reason.FONTS_STORE_ONLY, if (xiaomi) HowTo.USE_THEMES_FONTS else HowTo.NONE)
        list += FeatureSupport(FeatureId.AOD, SupportStatus.UNSUPPORTED, Reason.AOD_UNDOCUMENTED, HowTo.USE_SYSTEM_AOD)
        return list
    }
}
