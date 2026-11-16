package com.wdesign.feature.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wdesign.core.data.WD
import com.wdesign.core.data.WallpaperTarget
import com.wdesign.core.model.BuiltInPresets
import com.wdesign.core.model.DesignProject
import com.wdesign.core.model.DiscoverCategory
import com.wdesign.ds.DesignPreview
import com.wdesign.ds.EmptyState
import com.wdesign.ds.Routes
import com.wdesign.ds.SectionHeader
import com.wdesign.ds.W
import com.wdesign.ds.WButton
import com.wdesign.ds.WButtonKind
import com.wdesign.ds.WCard
import com.wdesign.ds.WChip
import com.wdesign.ds.WIcons
import com.wdesign.ds.WTopBar
import kotlinx.coroutines.launch

@Composable
fun categoryName(c: DiscoverCategory): String = stringResource(when (c) {
    DiscoverCategory.LIQUID_GLASS -> R.string.dc_c_LIQUID_GLASS; DiscoverCategory.IOS_STYLE -> R.string.dc_c_IOS_STYLE; DiscoverCategory.MINIMAL -> R.string.dc_c_MINIMAL
    DiscoverCategory.AMOLED -> R.string.dc_c_AMOLED; DiscoverCategory.DARK -> R.string.dc_c_DARK; DiscoverCategory.LIGHT -> R.string.dc_c_LIGHT
    DiscoverCategory.NOTHING_STYLE -> R.string.dc_c_NOTHING_STYLE; DiscoverCategory.HYPEROS -> R.string.dc_c_HYPEROS; DiscoverCategory.ABSTRACT -> R.string.dc_c_ABSTRACT
    DiscoverCategory.CUSTOM -> R.string.dc_c_CUSTOM
})

@Composable
fun DiscoverScreen(go: (String) -> Unit) {
    var category by rememberSaveable { mutableStateOf<DiscoverCategory?>(null) }
    val presets = category?.let(BuiltInPresets::inCategory) ?: BuiltInPresets.all
    LazyVerticalGrid(
        GridCells.Adaptive(150.dp), Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 110.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { Text(stringResource(R.string.dc_title), style = W.type.display, color = W.colors.text) }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WChip(stringResource(R.string.dc_all), category == null, { category = null })
                for (c in DiscoverCategory.entries) WChip(categoryName(c), category == c, { category = c })
            }
        }
        items(presets, key = { it.id }) { p ->
            WCard(onClick = { go(Routes.preset(p.id)) }, padding = 8.dp) {
                Column {
                    DesignPreview(p.spec, null, lock = true, modifier = Modifier.fillMaxWidth(), widthPx = 270, corner = 14.dp)
                    Spacer(Modifier.height(6.dp))
                    Text(p.name, style = W.type.label, color = W.colors.text, maxLines = 1)
                    Text(p.categories.take(2).map { categoryName(it) }.joinToString(" · "), style = W.type.caption, color = W.colors.muted, maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun PresetDetailScreen(id: String, go: (String) -> Unit, back: () -> Unit, snackbar: SnackbarHostState) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val preset = BuiltInPresets.byId(id)
    var busy by remember { mutableStateOf(false) }
    var savedId by remember { mutableStateOf<String?>(null) }
    val savedMsg = stringResource(R.string.dc_saved)
    val appliedMsg = stringResource(R.string.dc_applied)
    Column(Modifier.fillMaxSize()) {
        WTopBar(preset?.name ?: "", back, stringResource(com.wdesign.ds.R.string.ds_back))
        if (preset == null) { EmptyState(stringResource(R.string.dc_not_found), ""); return@Column }
        suspend fun ensureSaved(): String {
            savedId?.let { return it }
            val p = WD.services.projects.fromPreset(preset)
            savedId = p.id
            WD.services.settings.setActiveProject(p.id)
            return p.id
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DesignPreview(preset.spec, null, lock = false, modifier = Modifier.weight(1f), widthPx = 420)
                DesignPreview(preset.spec, null, lock = true, modifier = Modifier.weight(1f), widthPx = 420)
            }
            WButton(stringResource(R.string.dc_save), {
                scope.launch { busy = true; ensureSaved(); busy = false; snackbar.showSnackbar(savedMsg) }
            }, icon = WIcons.Heart, enabled = !busy, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WButton(stringResource(R.string.dc_apply), {
                    scope.launch {
                        busy = true
                        val s = WD.services.snapshot()
                        val r = WD.services.wallpapers.apply(DesignProject(preset.id, preset.name, spec = preset.spec), null, WallpaperTarget.BOTH, s.screenWidthPx, s.screenHeightPx)
                        busy = false
                        snackbar.showSnackbar(r.fold({ appliedMsg }, { ctx.getString(com.wdesign.ds.R.string.ds_error_generic, it.message ?: "") }))
                    }
                }, kind = WButtonKind.SECONDARY, icon = WIcons.Image, enabled = !busy, modifier = Modifier.weight(1f))
                WButton(stringResource(R.string.dc_edit), { scope.launch { go(Routes.lockscreen(ensureSaved())) } }, kind = WButtonKind.SECONDARY, icon = WIcons.Edit, modifier = Modifier.weight(1f))
            }
            WButton(stringResource(R.string.dc_export), { scope.launch { go(Routes.mtz(ensureSaved())) } }, kind = WButtonKind.GHOST, icon = WIcons.Package, modifier = Modifier.fillMaxWidth())
            SectionHeader(stringResource(R.string.dc_palette))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (c in preset.spec.palette.all().distinct()) Box(Modifier.size(36.dp).clip(CircleShape).background(Color(c.argb)))
            }
            Text(preset.spec.palette.all().distinct().joinToString("  ") { it.toRgbHex() }, style = W.type.mono, color = W.colors.muted)
            Spacer(Modifier.height(32.dp))
        }
    }
}
