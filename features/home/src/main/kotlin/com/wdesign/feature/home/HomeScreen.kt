package com.wdesign.feature.home

import com.wdesign.ds.SafeLaunchedEffect
import com.wdesign.ds.rememberSafeScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wdesign.core.data.AppSettings
import com.wdesign.core.data.WD
import com.wdesign.core.data.WallpaperTarget
import com.wdesign.core.model.BuiltInPresets
import com.wdesign.core.model.CompatibilityMatrix
import com.wdesign.core.model.DesignKind
import com.wdesign.core.model.DesignSource
import com.wdesign.core.model.FeatureId
import com.wdesign.core.model.LockStyle
import com.wdesign.core.model.LockTemplates
import com.wdesign.core.model.ThemeSpec
import com.wdesign.ds.DesignPreview
import com.wdesign.ds.Routes
import com.wdesign.ds.SectionHeader
import com.wdesign.ds.SegmentedControl
import com.wdesign.ds.SupportBadge
import com.wdesign.ds.ToolRow
import com.wdesign.ds.W
import com.wdesign.ds.WButton
import com.wdesign.ds.WButtonKind
import com.wdesign.ds.WCard
import com.wdesign.ds.WIcons
import com.wdesign.ds.statusLabel
import com.wdesign.feature.xiaomilab.featureName
import com.wdesign.feature.xiaomilab.rememberSnapshot
import com.wdesign.feature.xiaomilab.systemLabel
import kotlinx.coroutines.launch

private data class Tool(val title: Int, val body: Int, val icon: ImageVector, val route: String)

private val TOOLS = listOf(
    Tool(R.string.t_lock, R.string.t_lock_body, WIcons.Lock, Routes.lockscreen(null)),
    Tool(R.string.t_discover, R.string.t_discover_body, WIcons.Compass, Routes.DISCOVER),
    Tool(R.string.t_mtz, R.string.t_mtz_body, WIcons.Package, Routes.mtz(null)),
    Tool(R.string.t_import, R.string.t_import_body, WIcons.Upload, Routes.IMPORT),
    Tool(R.string.t_lab, R.string.t_lab_body, WIcons.Cpu, Routes.XIAOMI_LAB),
    Tool(R.string.t_designs, R.string.t_designs_body, WIcons.Layers, Routes.DESIGNS),
)

/** Two-column tools list (not a 3-up grid). */
@Composable
private fun TwoColumnTools(go: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in TOOLS.chunked(2)) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (t in row) ToolRow(stringResource(t.title), stringResource(t.body), t.icon, { go(t.route) }, Modifier.weight(1f), trailing = {})
        }
    }
}

@Composable
fun HomeScreen(go: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberSafeScope()
    val store = WD.services.projects
    val starter = stringResource(R.string.hm_starter)
    SafeLaunchedEffect(Unit) {
        store.load()
        if (store.projects.value.isEmpty()) {
            // First launch: seed one editable design so every Home action works immediately.
            val p = store.fromPreset(BuiltInPresets.byId("glass-graphite") ?: BuiltInPresets.all.first())
            store.rename(p.id, starter)
            WD.services.settings.setActiveProject(p.id)
        }
    }
    val projects by store.projects.collectAsState()
    val settings by WD.services.settings.settings.collectAsState(initial = AppSettings())
    val current = projects.firstOrNull { it.id == settings.activeProjectId } ?: projects.firstOrNull()
    val snap = rememberSnapshot()
    var target by remember { mutableStateOf(WallpaperTarget.BOTH) }
    var applying by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val applied = stringResource(R.string.hm_applied)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.hm_title), style = W.type.display, color = W.colors.text)
        Text(stringResource(R.string.hm_subtitle) + (current?.let { " · ${it.name}" } ?: ""), style = W.type.body, color = W.colors.muted)
        if (current != null) {
            val assets = store.assetsDir(current.id)
            Row(Modifier.fillMaxWidth().widthIn(max = 560.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    DesignPreview(current.spec, assets, lock = false, modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.hm_home), style = W.type.caption, color = W.colors.muted)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    DesignPreview(current.spec, assets, lock = true, modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.hm_lock), style = W.type.caption, color = W.colors.muted)
                }
            }
            val labels = mapOf(
                WallpaperTarget.HOME to stringResource(R.string.hm_apply_home),
                WallpaperTarget.LOCK to stringResource(R.string.hm_apply_lock),
                WallpaperTarget.BOTH to stringResource(R.string.hm_apply_both),
            )
            SegmentedControl(WallpaperTarget.entries, target, label = { labels.getValue(it) }, onSelect = { target = it }, modifier = Modifier.fillMaxWidth())
            WButton(stringResource(R.string.hm_apply), onClick = {
                scope.launch {
                    applying = true
                    val s = WD.services.snapshot()
                    val r = WD.services.wallpapers.apply(current, assets, target, s.screenWidthPx, s.screenHeightPx)
                    applying = false
                    message = r.fold({ applied }, { ctx.getString(com.wdesign.ds.R.string.ds_error_generic, it.message ?: "") })
                }
            }, loading = applying, enabled = !applying, icon = WIcons.Image, modifier = Modifier.fillMaxWidth())
            message?.let { Text(it, style = W.type.caption, color = W.colors.muted) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WButton(stringResource(R.string.hm_edit_lock), { go(Routes.lockscreen(current.id)) }, kind = WButtonKind.SECONDARY, icon = WIcons.Edit, modifier = Modifier.weight(1f))
                WButton(stringResource(R.string.hm_export), { go(Routes.mtz(current.id)) }, kind = WButtonKind.SECONDARY, icon = WIcons.Package, modifier = Modifier.weight(1f))
            }
        }
        val recent = projects.filter { it.id != current?.id }.take(3)
        if (recent.isNotEmpty()) {
            SectionHeader(stringResource(R.string.hm_continue))
            for (p in recent) ToolRow(p.name, p.kind.name.lowercase().replace('_', ' '), WIcons.Edit, {
                scope.launch { WD.services.settings.setActiveProject(p.id) }
                go(Routes.lockscreen(p.id))
            })
        }
        SectionHeader(stringResource(R.string.hm_tools))
        TwoColumnTools(go)
        SectionHeader(stringResource(R.string.hm_support), action = {
            WButton(stringResource(R.string.hm_support_open), { go(Routes.XIAOMI_LAB) }, kind = WButtonKind.GHOST)
        })
        if (snap != null) {
            WCard(Modifier.fillMaxWidth(), onClick = { go(Routes.XIAOMI_LAB) }) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(systemLabel(snap.system) + " · ${snap.brand} ${snap.model}", style = W.type.mono, color = W.colors.text)
                    val matrix = CompatibilityMatrix.evaluate(snap).associateBy { it.id }
                    for (id in listOf(FeatureId.WALLPAPER_LOCK, FeatureId.MTZ_INSTALL, FeatureId.LOCK_CLOCK)) {
                        val f = matrix.getValue(id)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(featureName(id)), style = W.type.label, color = W.colors.text, modifier = Modifier.weight(1f))
                            SupportBadge(f.status, statusLabel(f.status))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(96.dp))
    }
}

@Composable
fun CreateScreen(go: (String) -> Unit) {
    val scope = rememberSafeScope()
    val newName = stringResource(R.string.cr_new_name)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.cr_title), style = W.type.display, color = W.colors.text)
        Text(stringResource(R.string.cr_body), style = W.type.body, color = W.colors.muted)
        ToolRow(stringResource(R.string.cr_new_lock), stringResource(R.string.cr_new_lock_body), WIcons.Lock, {
            scope.launch {
                val p = WD.services.projects.create(newName, ThemeSpec(lockScreen = LockTemplates.build(LockStyle.MINIMAL)), DesignKind.LOCK_SCREEN, DesignSource.USER)
                WD.services.settings.setActiveProject(p.id)
                go(Routes.lockscreen(p.id))
            }
        })
        ToolRow(stringResource(R.string.cr_from_preset), stringResource(R.string.cr_from_preset_body), WIcons.Compass, { go(Routes.DISCOVER) })
        TwoColumnTools(go)
        Spacer(Modifier.height(96.dp))
    }
}
