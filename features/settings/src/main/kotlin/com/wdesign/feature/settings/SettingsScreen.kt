package com.wdesign.feature.settings

import android.content.Intent
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import com.wdesign.core.data.CrashLog
import com.wdesign.ds.WButton
import com.wdesign.ds.WButtonKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.wdesign.ds.SafeLaunchedEffect
import com.wdesign.ds.rememberSafeScope
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
    val scope = rememberSafeScope()
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
        CrashReportRow(snackbar)
        SectionHeader(stringResource(R.string.st_licences))
        WCard(Modifier.fillMaxWidth()) { Text(stringResource(R.string.st_licences_body), style = W.type.caption, color = W.colors.muted) }
        SectionHeader(stringResource(R.string.st_about))
        WCard(Modifier.fillMaxWidth()) { Text(stringResource(R.string.st_about_body), style = W.type.caption, color = W.colors.muted) }
        Spacer(Modifier.height(110.dp))
    }
}

/** Shown only when CrashLog has a stored report. Share uses FileProvider (text/plain). */
@Composable
private fun CrashReportRow(snackbar: SnackbarHostState) {
    val ctx = LocalContext.current
    val scope = rememberSafeScope()
    var report by remember { mutableStateOf<String?>(null) }
    SafeLaunchedEffect(Unit) { report = withContext(Dispatchers.IO) { CrashLog.read(ctx) } }
    val text = report ?: return
    val cleared = stringResource(R.string.st_crash_cleared)
    WCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.st_crash), style = W.type.label, color = W.colors.text)
            Text(stringResource(R.string.st_crash_body), style = W.type.caption, color = W.colors.muted)
            Text(text.lineSequence().take(4).joinToString("\n"), style = W.type.mono, color = W.colors.muted, maxLines = 4)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WButton(stringResource(R.string.st_crash_share), {
                    scope.run {
                        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", CrashLog.file(ctx))
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_TEXT, text.take(8000))
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        ctx.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }, kind = WButtonKind.SECONDARY, icon = WIcons.Share, modifier = Modifier.weight(1f))
                WButton(stringResource(R.string.st_crash_clear), {
                    scope.launch {
                        withContext(Dispatchers.IO) { CrashLog.clear(ctx) }
                        report = null
                        snackbar.showSnackbar(cleared)
                    }
                }, kind = WButtonKind.GHOST, icon = WIcons.Trash, modifier = Modifier.weight(1f))
            }
        }
    }
}
