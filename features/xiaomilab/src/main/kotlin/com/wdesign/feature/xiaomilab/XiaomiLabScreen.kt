package com.wdesign.feature.xiaomilab

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wdesign.core.data.WD
import com.wdesign.core.device.XiaomiIntents
import com.wdesign.core.model.CompatibilityMatrix
import com.wdesign.core.model.DeviceSnapshot
import com.wdesign.core.model.FeatureSupport
import com.wdesign.core.model.HowTo
import com.wdesign.core.model.SystemUiKind
import com.wdesign.core.model.SystemUiVersion
import com.wdesign.ds.Routes
import com.wdesign.ds.SectionHeader
import com.wdesign.ds.SpecLine
import com.wdesign.ds.SupportBadge
import com.wdesign.ds.ToolRow
import com.wdesign.ds.W
import com.wdesign.ds.WButton
import com.wdesign.ds.WButtonKind
import com.wdesign.ds.WCard
import com.wdesign.ds.WIcons
import com.wdesign.ds.WTopBar
import com.wdesign.ds.statusLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Runs the action for a [HowTo]; returns false when the system screen was not available. */
fun runHowTo(context: Context, howTo: HowTo, go: (String) -> Unit): Boolean = when (howTo) {
    HowTo.APPLY_IN_WALLPAPER_STUDIO, HowTo.OPEN_LOCK_EDITOR, HowTo.USE_LOCK_WALLPAPER -> { go(Routes.lockscreen(null)); true }
    HowTo.DISABLE_CAROUSEL -> XiaomiIntents.wallpaperSettings(context)
    HowTo.PICK_LIVE_WALLPAPER -> XiaomiIntents.liveWallpaperPicker(context)
    HowTo.ADD_FROM_WIDGET_PICKER, HowTo.USE_CONTROL_WIDGET, HowTo.ENABLE_LAUNCHER, HowTo.USE_W_LAUNCHER -> XiaomiIntents.homeSettings(context)
    HowTo.GRANT_SHORTCUT_PERMISSION -> XiaomiIntents.permissionEditor(context)
    HowTo.ADD_TILE -> XiaomiIntents.appDetails(context)
    HowTo.EXPORT_MTZ -> { go(Routes.mtz(null)); true }
    HowTo.IMPORT_IN_THEMES, HowTo.USE_THEMES_FONTS -> XiaomiIntents.themesApp(context)
    HowTo.PC_THEME_EDITOR -> XiaomiIntents.openUrl(context, "https://zhuti.designer.xiaomi.com/docs/themePage/guide/")
    HowTo.USE_SYSTEM_AOD -> XiaomiIntents.wallpaperSettings(context)
    HowTo.NONE -> true
}

@Composable
fun rememberSnapshot(): DeviceSnapshot? {
    var snap by remember { mutableStateOf<DeviceSnapshot?>(null) }
    LaunchedEffect(Unit) { snap = WD.services.snapshot() }
    return snap
}

@Composable
fun systemLabel(v: SystemUiVersion): String = if (v.kind == SystemUiKind.OTHER) "Android" else v.label

@Composable
fun FeatureRow(f: FeatureSupport, go: (String) -> Unit, onFailed: () -> Unit) {
    val ctx = LocalContext.current
    WCard(Modifier.fillMaxWidth(), padding = 14.dp) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(featureName(f.id)), style = W.type.label, color = W.colors.text, modifier = Modifier.weight(1f))
                SupportBadge(f.status, statusLabel(f.status))
            }
            Spacer(Modifier.height(6.dp))
            Text(stringResource(reasonText(f.reason)), style = W.type.caption, color = W.colors.muted)
            if (f.howTo != HowTo.NONE) {
                Spacer(Modifier.height(8.dp))
                WButton(stringResource(howToLabel(f.howTo)), onClick = { if (!runHowTo(ctx, f.howTo, go)) onFailed() }, kind = WButtonKind.SECONDARY)
            }
        }
    }
}

@Composable
fun XiaomiLabScreen(go: (String) -> Unit, back: () -> Unit, snackbar: androidx.compose.material3.SnackbarHostState? = null) {
    val ctx = LocalContext.current
    val snap = rememberSnapshot()
    val scope = rememberCoroutineScope()
    val failed = stringResource(R.string.xl_open_failed)
    val onFailed: () -> Unit = { scope.launch { snackbar?.showSnackbar(failed) } }
    var probe by remember { mutableStateOf<ByteArray?>(null) }
    var probing by remember { mutableStateOf(false) }
    val saveProbe = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val bytes = probe ?: return@rememberLauncherForActivityResult
        if (uri != null) scope.launch { withContext(Dispatchers.IO) { ctx.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } } }
    }
    Column(Modifier.fillMaxSize()) {
        WTopBar(stringResource(R.string.xl_title), back, stringResource(com.wdesign.ds.R.string.ds_back))
        LazyColumn(
            Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { SectionHeader(stringResource(R.string.xl_device)) }
            item {
                WCard(Modifier.fillMaxWidth()) {
                    Column {
                        if (snap == null) Text("…", style = W.type.mono, color = W.colors.muted) else {
                            SpecLine(stringResource(R.string.xl_k_model), "${snap.brand} ${snap.model}")
                            SpecLine(stringResource(R.string.xl_k_android), "${snap.androidRelease} (API ${snap.sdkInt})")
                            SpecLine(stringResource(R.string.xl_k_system), systemLabel(snap.system) + if (snap.system.raw.isNotBlank()) "\n${snap.system.raw}" else "")
                            snap.system.region?.let { SpecLine(stringResource(R.string.xl_k_region), "$it · ${SystemUiVersion.REGIONS[it] ?: "?"}") }
                            SpecLine(stringResource(R.string.xl_k_screen), "${snap.screenWidthPx}×${snap.screenHeightPx} · ${snap.densityDpi} dpi · ${snap.refreshRate.toInt()} Hz")
                            SpecLine(stringResource(R.string.xl_k_themes), snap.themeManagerVersion ?: stringResource(R.string.xl_none))
                            SpecLine(stringResource(R.string.xl_k_home), snap.homePackage ?: "?")
                            if (!snap.isXiaomiFamily) {
                                Spacer(Modifier.height(8.dp))
                                Text(stringResource(R.string.xl_not_hyperos), style = W.type.caption, color = W.colors.warning)
                            }
                        }
                    }
                }
            }
            item { SectionHeader(stringResource(R.string.xl_mtz)) }
            item { ToolRow(stringResource(R.string.xl_export), stringResource(R.string.xl_export_body), WIcons.Package, { go(Routes.mtz(null)) }) }
            item {
                ToolRow(stringResource(R.string.xl_probe), stringResource(R.string.xl_probe_body), WIcons.Shield, {
                    if (!probing) scope.launch {
                        probing = true
                        val s = WD.services.snapshot()
                        probe = withContext(Dispatchers.Default) { runCatching { WD.services.mtz.probe(s.screenWidthPx, s.screenHeightPx).bytes }.getOrNull() }
                        probing = false
                        if (probe != null) saveProbe.launch("wdesign-probe.mtz")
                    }
                })
            }
            item { ToolRow(stringResource(R.string.xl_import), stringResource(R.string.xl_import_body), WIcons.Upload, { go(Routes.IMPORT) }) }
            item { Text(stringResource(com.wdesign.ds.R.string.ds_unofficial), style = W.type.caption, color = W.colors.muted) }

            item { SectionHeader(stringResource(R.string.xl_matrix)) }
            item { Text(stringResource(R.string.xl_matrix_body), style = W.type.caption, color = W.colors.muted) }
            if (snap != null) items(CompatibilityMatrix.evaluate(snap), key = { it.id.name }) { f -> FeatureRow(f, go, onFailed) }

            item { SectionHeader(stringResource(R.string.xl_versions)) }
            val versions = listOf(1 to (R.string.xl_v1 to R.string.xl_v1_body), 2 to (R.string.xl_v2 to R.string.xl_v2_body), 3 to (R.string.xl_v3 to R.string.xl_v3_body), 4 to (R.string.xl_v4 to R.string.xl_v4_body))
            items(versions, key = { "v${it.first}" }) { (v, res) ->
                WCard(Modifier.fillMaxWidth(), padding = 14.dp) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(res.first), style = W.type.label, color = W.colors.text, modifier = Modifier.weight(1f))
                            if (snap?.system?.isHyperOs == true && snap.system.major == v) Text(stringResource(R.string.xl_current), style = W.type.caption, color = W.colors.accent)
                        }
                        Text(stringResource(res.second), style = W.type.caption, color = W.colors.muted)
                    }
                }
            }

            item { SectionHeader(stringResource(R.string.xl_recommend)) }
            item { ToolRow(stringResource(R.string.xl_autostart), stringResource(R.string.xl_autostart_body), WIcons.Clock, { if (!XiaomiIntents.autostart(ctx)) onFailed() }) }
            item { ToolRow(stringResource(R.string.xl_battery), stringResource(R.string.xl_battery_body), WIcons.Cpu, { if (!XiaomiIntents.batteryOptimization(ctx)) onFailed() }) }
            item { ToolRow(stringResource(R.string.xl_shortcuts), stringResource(R.string.xl_shortcuts_body), WIcons.Grid, { if (!XiaomiIntents.permissionEditor(ctx)) onFailed() }) }
            item { ToolRow(stringResource(R.string.xl_themes), stringResource(R.string.xl_themes_body), WIcons.Sparkles, { if (!XiaomiIntents.themesApp(ctx)) onFailed() }) }
            item { ToolRow(stringResource(R.string.xl_wallsettings), stringResource(R.string.xl_wallsettings_body), WIcons.Image, { if (!XiaomiIntents.wallpaperSettings(ctx)) onFailed() }) }
            item { Spacer(Modifier.navigationBarsPadding()) }
        }
    }
}
