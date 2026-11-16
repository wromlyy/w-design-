// Generated from res/values/strings.xml by hand-run script; keep in sync with the enums.
package com.wdesign.feature.xiaomilab

import androidx.annotation.StringRes
import com.wdesign.core.model.FeatureId
import com.wdesign.core.model.HowTo
import com.wdesign.core.model.Reason
import com.wdesign.core.mtz.CheckItem
import com.wdesign.core.mtz.MamlNote

@StringRes fun featureName(v: FeatureId): Int = when (v) {
    FeatureId.WALLPAPER_HOME -> R.string.xl_f_WALLPAPER_HOME
    FeatureId.WALLPAPER_LOCK -> R.string.xl_f_WALLPAPER_LOCK
    FeatureId.LIVE_WALLPAPER -> R.string.xl_f_LIVE_WALLPAPER
    FeatureId.WIDGETS -> R.string.xl_f_WIDGETS
    FeatureId.WIDGET_PINNING -> R.string.xl_f_WIDGET_PINNING
    FeatureId.PINNED_SHORTCUTS -> R.string.xl_f_PINNED_SHORTCUTS
    FeatureId.LAUNCHER -> R.string.xl_f_LAUNCHER
    FeatureId.QS_TILE -> R.string.xl_f_QS_TILE
    FeatureId.MTZ_BUILD -> R.string.xl_f_MTZ_BUILD
    FeatureId.MTZ_INSTALL -> R.string.xl_f_MTZ_INSTALL
    FeatureId.SYSTEM_ICONS -> R.string.xl_f_SYSTEM_ICONS
    FeatureId.LOCK_CLOCK -> R.string.xl_f_LOCK_CLOCK
    FeatureId.LOCK_MAML -> R.string.xl_f_LOCK_MAML
    FeatureId.CONTROL_CENTER -> R.string.xl_f_CONTROL_CENTER
    FeatureId.SYSTEM_FONTS -> R.string.xl_f_SYSTEM_FONTS
    FeatureId.AOD -> R.string.xl_f_AOD
}

@StringRes fun reasonText(v: Reason): Int = when (v) {
    Reason.OK -> R.string.xl_r_OK
    Reason.POLICY_BLOCKS_WALLPAPER -> R.string.xl_r_POLICY_BLOCKS_WALLPAPER
    Reason.NO_WALLPAPER_SERVICE -> R.string.xl_r_NO_WALLPAPER_SERVICE
    Reason.HYPEROS_CAROUSEL_MAY_OVERRIDE -> R.string.xl_r_HYPEROS_CAROUSEL_MAY_OVERRIDE
    Reason.NO_LIVE_WALLPAPER_FEATURE -> R.string.xl_r_NO_LIVE_WALLPAPER_FEATURE
    Reason.LIVE_LOCK_VARIES -> R.string.xl_r_LIVE_LOCK_VARIES
    Reason.NO_WIDGET_FEATURE -> R.string.xl_r_NO_WIDGET_FEATURE
    Reason.PIN_NOT_SUPPORTED_BY_LAUNCHER -> R.string.xl_r_PIN_NOT_SUPPORTED_BY_LAUNCHER
    Reason.XIAOMI_SHORTCUT_PERMISSION -> R.string.xl_r_XIAOMI_SHORTCUT_PERMISSION
    Reason.USER_MUST_CHOOSE_HOME -> R.string.xl_r_USER_MUST_CHOOSE_HOME
    Reason.TILE_REQUEST_API33 -> R.string.xl_r_TILE_REQUEST_API33
    Reason.TILE_MANUAL_ADD -> R.string.xl_r_TILE_MANUAL_ADD
    Reason.MTZ_ALWAYS_BUILDS -> R.string.xl_r_MTZ_ALWAYS_BUILDS
    Reason.NOT_XIAOMI -> R.string.xl_r_NOT_XIAOMI
    Reason.NO_THEME_MANAGER -> R.string.xl_r_NO_THEME_MANAGER
    Reason.CN_IMPORT_AVAILABLE -> R.string.xl_r_CN_IMPORT_AVAILABLE
    Reason.GLOBAL_IMPORT_MISSING -> R.string.xl_r_GLOBAL_IMPORT_MISSING
    Reason.ICONS_VIA_MTZ_ONLY -> R.string.xl_r_ICONS_VIA_MTZ_ONLY
    Reason.ICONS_NEED_XIAOMI -> R.string.xl_r_ICONS_NEED_XIAOMI
    Reason.CLOCK_SYSTEM_EDITOR_OS3 -> R.string.xl_r_CLOCK_SYSTEM_EDITOR_OS3
    Reason.CLOCK_SYSTEM_EDITOR_OS1 -> R.string.xl_r_CLOCK_SYSTEM_EDITOR_OS1
    Reason.CLOCK_VIA_MTZ -> R.string.xl_r_CLOCK_VIA_MTZ
    Reason.CLOCK_NOT_XIAOMI -> R.string.xl_r_CLOCK_NOT_XIAOMI
    Reason.MAML_NEEDS_IMPORT -> R.string.xl_r_MAML_NEEDS_IMPORT
    Reason.SYSTEM_SIGNATURE_REQUIRED -> R.string.xl_r_SYSTEM_SIGNATURE_REQUIRED
    Reason.FONTS_STORE_ONLY -> R.string.xl_r_FONTS_STORE_ONLY
    Reason.AOD_UNDOCUMENTED -> R.string.xl_r_AOD_UNDOCUMENTED
}

@StringRes fun howToLabel(v: HowTo): Int = when (v) {
    HowTo.APPLY_IN_WALLPAPER_STUDIO -> R.string.xl_h_APPLY_IN_WALLPAPER_STUDIO
    HowTo.DISABLE_CAROUSEL -> R.string.xl_h_DISABLE_CAROUSEL
    HowTo.PICK_LIVE_WALLPAPER -> R.string.xl_h_PICK_LIVE_WALLPAPER
    HowTo.ADD_FROM_WIDGET_PICKER -> R.string.xl_h_ADD_FROM_WIDGET_PICKER
    HowTo.GRANT_SHORTCUT_PERMISSION -> R.string.xl_h_GRANT_SHORTCUT_PERMISSION
    HowTo.ENABLE_LAUNCHER -> R.string.xl_h_ENABLE_LAUNCHER
    HowTo.ADD_TILE -> R.string.xl_h_ADD_TILE
    HowTo.EXPORT_MTZ -> R.string.xl_h_EXPORT_MTZ
    HowTo.IMPORT_IN_THEMES -> R.string.xl_h_IMPORT_IN_THEMES
    HowTo.PC_THEME_EDITOR -> R.string.xl_h_PC_THEME_EDITOR
    HowTo.USE_W_LAUNCHER -> R.string.xl_h_USE_W_LAUNCHER
    HowTo.OPEN_LOCK_EDITOR -> R.string.xl_h_OPEN_LOCK_EDITOR
    HowTo.USE_LOCK_WALLPAPER -> R.string.xl_h_USE_LOCK_WALLPAPER
    HowTo.USE_CONTROL_WIDGET -> R.string.xl_h_USE_CONTROL_WIDGET
    HowTo.USE_THEMES_FONTS -> R.string.xl_h_USE_THEMES_FONTS
    HowTo.USE_SYSTEM_AOD -> R.string.xl_h_USE_SYSTEM_AOD
    HowTo.NONE -> R.string.xl_h_NONE
}

@StringRes fun checkLabel(v: CheckItem): Int = when (v) {
    CheckItem.ARCHIVE_READABLE -> R.string.mx_c_ARCHIVE_READABLE
    CheckItem.DESCRIPTION_WELL_FORMED -> R.string.mx_c_DESCRIPTION_WELL_FORMED
    CheckItem.TITLE_PRESENT -> R.string.mx_c_TITLE_PRESENT
    CheckItem.HOME_WALLPAPER_SIZE -> R.string.mx_c_HOME_WALLPAPER_SIZE
    CheckItem.LOCK_WALLPAPER_SIZE -> R.string.mx_c_LOCK_WALLPAPER_SIZE
    CheckItem.ICON_SIZES -> R.string.mx_c_ICON_SIZES
    CheckItem.LOCKSCREEN_WELL_FORMED -> R.string.mx_c_LOCKSCREEN_WELL_FORMED
    CheckItem.LOCK_HAS_TIME -> R.string.mx_c_LOCK_HAS_TIME
    CheckItem.LOCK_HAS_DATE -> R.string.mx_c_LOCK_HAS_DATE
    CheckItem.LOCK_HAS_UNLOCK -> R.string.mx_c_LOCK_HAS_UNLOCK
    CheckItem.LOCK_ASSETS_PRESENT -> R.string.mx_c_LOCK_ASSETS_PRESENT
    CheckItem.NO_PASSWORD_MODULE -> R.string.mx_c_NO_PASSWORD_MODULE
    CheckItem.TOTAL_SIZE -> R.string.mx_c_TOTAL_SIZE
    CheckItem.OVERRIDES_EXISTING_KEYS -> R.string.mx_c_OVERRIDES_EXISTING_KEYS
    CheckItem.NESTED_MODULES_VALID -> R.string.mx_c_NESTED_MODULES_VALID
}

@StringRes fun noteText(v: MamlNote): Int = when (v) {
    MamlNote.FOD_OVERLAP_HIDDEN -> R.string.mx_n_FOD_OVERLAP_HIDDEN
    MamlNote.WIDGET_PREVIEW_ONLY -> R.string.mx_n_WIDGET_PREVIEW_ONLY
    MamlNote.CLOCK_AS_IMAGES -> R.string.mx_n_CLOCK_AS_IMAGES
    MamlNote.ROTATION_RASTERIZED -> R.string.mx_n_ROTATION_RASTERIZED
    MamlNote.TEXT_RASTERIZED -> R.string.mx_n_TEXT_RASTERIZED
    MamlNote.GRADIENT_AS_IMAGES -> R.string.mx_n_GRADIENT_AS_IMAGES
    MamlNote.DATE_PATTERN_SIMPLIFIED -> R.string.mx_n_DATE_PATTERN_SIMPLIFIED
}
