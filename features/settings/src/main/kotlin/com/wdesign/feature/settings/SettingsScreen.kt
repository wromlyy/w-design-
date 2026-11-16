package com.wdesign.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wdesign.core.data.AppSettings
import com.wdesign.core.data.WD
import com.wdesign.core.model.AppearanceMode
import com.wdesign.ds.Routes
import com.wdesign.ds.SectionHeader
import com.wdesign.ds.SegmentedControl
import com.wdesign.ds.ToolRow
import com.wdesign.ds.W
import com.wdesign.ds.WCard
import com.wdesign.ds.WIcons
import com.wdesign.ds.WSwitchRow
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(go: (String) -> Unit, snackbar: SnackbarHostState) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = WD.services.settings
    val s by store.settings.collectAsState(initial = AppSettings())
    val labels = mapOf(
        AppearanceMode.DARK to stringResource(R.string.st_dark),
        AppearanceMode.LIGHT to stringResource(R.string.st_light),
        AppearanceMode.SYSTEM to stringResource(R.string.st_system),
    )
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.st_title), style = W.type.display, color = W.colors.text)
        SectionHeader(stringResource(R.string.st_theme))
        SegmentedControl(AppearanceMode.entries, s.themeMode, label = { labels.getValue(it) }, onSelect = { m -> scope.launch { store.setThemeMode(m) } }, modifier = Modifier.fillMaxWidth())
        WSwitchRow(stringResource(R.string.st_motion), s.reduceMotion, { v -> scope.launch { store.setReduceMotion(v) } })
        WSwitchRow(stringResource(R.string.st_transparency), s.reduceTransparency, { v -> scope.launch { store.setReduceTransparency(v) } })
        ToolRow(stringResource(R.string.st_integrity), "", WIcons.Shield, {
            scope.launch {
                WD.services.projects.load()
                val r = WD.services.projects.integrityCheck()
                snackbar.showSnackbar(ctx.getString(R.string.st_integrity_ok, r.checked, r.broken.size))
            }
        })
        ToolRow(stringResource(R.string.st_lab), "", WIcons.Cpu, { go(Routes.XIAOMI_LAB) })
        SectionHeader(stringResource(R.string.st_licences))
        WCard(Modifier.fillMaxWidth()) { Text(stringResource(R.string.st_licences_body), style = W.type.caption, color = W.colors.muted) }
        SectionHeader(stringResource(R.string.st_about))
        WCard(Modifier.fillMaxWidth()) { Text(stringResource(R.string.st_about_body), style = W.type.caption, color = W.colors.muted) }
        Spacer(Modifier.height(110.dp))
    }
}
