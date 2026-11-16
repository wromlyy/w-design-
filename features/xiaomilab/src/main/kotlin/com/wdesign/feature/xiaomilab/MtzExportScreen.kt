package com.wdesign.feature.xiaomilab

import android.content.ContentValues
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.wdesign.core.data.WD
import com.wdesign.core.device.XiaomiIntents
import com.wdesign.core.model.MtzSettings
import com.wdesign.core.model.SupportStatus
import com.wdesign.core.mtz.CheckStatus
import com.wdesign.core.mtz.MamlNote
import com.wdesign.core.mtz.MtzInspection
import com.wdesign.core.mtz.MtzReader
import com.wdesign.core.render.MtzExportResult
import com.wdesign.ds.SectionHeader
import com.wdesign.ds.SupportBadge
import com.wdesign.ds.W
import com.wdesign.ds.WButton
import com.wdesign.ds.WButtonKind
import com.wdesign.ds.WCard
import com.wdesign.ds.WChip
import com.wdesign.ds.WIcons
import com.wdesign.ds.WSwitchRow
import com.wdesign.ds.WTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val ACCENT_KEY = Regex("accent|primary|highlight|checked|activated", RegexOption.IGNORE_CASE)

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value, onChange, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = W.colors.accent, unfocusedBorderColor = W.colors.inputBorder),
    )
}

@Composable
fun MtzExportScreen(projectId: String?, go: (String) -> Unit, back: () -> Unit, snackbar: SnackbarHostState) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = WD.services.projects
    LaunchedEffect(Unit) { store.load() }
    val projects by store.projects.collectAsState()
    val activeId by WD.services.settings.settings.collectAsState(initial = null)
    var selectedId by remember { mutableStateOf(projectId) }
    val selected = projects.firstOrNull { it.id == selectedId } ?: projects.firstOrNull { it.id == activeId?.activeProjectId } ?: projects.firstOrNull()
    var m by remember(selected?.id) { mutableStateOf(selected?.spec?.mtz?.let { if (it.title.isBlank()) it.copy(title = selected.name) else it } ?: MtzSettings()) }
    var reference by remember { mutableStateOf<MtzInspection?>(null) }
    var tintAccent by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var building by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<MtzExportResult?>(null) }
    var file by remember { mutableStateOf<File?>(null) }

    val savedMsg = stringResource(R.string.mx_saved)
    val dlMsg = stringResource(R.string.mx_saved_downloads)
    val themesFailed = stringResource(R.string.mx_themes_failed)
    val refFailed = stringResource(R.string.mx_reference_failed)

    val pickReference = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching { ctx.contentResolver.openInputStream(uri)!!.use { MtzReader().inspect(it, WD.services.stagingDir("reference")) } }.getOrNull()
            }
            if (r == null || !r.preview.ok) { reference = null; snackbar.showSnackbar(refFailed) } else {
                reference = r
                r.description?.uiVersion?.takeIf { it.isNotBlank() }?.let { m = m.copy(uiVersion = it) }
            }
        }
    }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val f = file ?: return@rememberLauncherForActivityResult
        if (uri != null) scope.launch {
            withContext(Dispatchers.IO) { ctx.contentResolver.openOutputStream(uri)?.use { out -> f.inputStream().use { it.copyTo(out) } } }
            snackbar.showSnackbar(savedMsg)
        }
    }

    val accentKeys = reference?.themeValues?.mapValues { (_, kv) -> kv.keys.filter { ACCENT_KEY.containsMatchIn(it) } }?.filterValues { it.isNotEmpty() }.orEmpty()

    Column(Modifier.fillMaxSize()) {
        WTopBar(stringResource(R.string.mx_title), back, stringResource(com.wdesign.ds.R.string.ds_back))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(com.wdesign.ds.R.string.ds_unofficial), style = W.type.caption, color = W.colors.warning)
            SectionHeader(stringResource(R.string.mx_project))
            if (projects.isEmpty()) Text(stringResource(R.string.mx_no_projects), style = W.type.body, color = W.colors.muted)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (p in projects) WChip(p.name, p.id == selected?.id, onClick = { selectedId = p.id; result = null })
            }
            if (selected != null) {
                SectionHeader(stringResource(R.string.mx_modules))
                WCard(Modifier.fillMaxWidth(), padding = 12.dp) {
                    Column {
                        WSwitchRow(stringResource(R.string.mx_wallpapers), m.includeWallpapers, { m = m.copy(includeWallpapers = it) })
                        WSwitchRow(stringResource(R.string.mx_icons), m.includeIcons, { m = m.copy(includeIcons = it) })
                        if (m.includeIcons) WSwitchRow(stringResource(R.string.mx_icons_all), m.iconsForAllApps, { m = m.copy(iconsForAllApps = it) }, subtitle = stringResource(R.string.mx_icons_all_body))
                        WSwitchRow(stringResource(R.string.mx_lockscreen), m.includeLockscreen, { m = m.copy(includeLockscreen = it) })
                        WSwitchRow(stringResource(R.string.mx_previews), m.includePreviews, { m = m.copy(includePreviews = it) })
                        WSwitchRow(stringResource(R.string.mx_exp_wallpaper), m.experimentalMiWallpaper, { m = m.copy(experimentalMiWallpaper = it) }, subtitle = stringResource(R.string.mx_exp_body))
                        WSwitchRow(stringResource(R.string.mx_exp_mask), m.experimentalIconMask, { m = m.copy(experimentalIconMask = it) })
                    }
                }
                SectionHeader(stringResource(R.string.mx_meta))
                Field(stringResource(R.string.mx_f_title), m.title) { m = m.copy(title = it.take(60)) }
                Field(stringResource(R.string.mx_f_designer), m.designer) { m = m.copy(designer = it.take(60)) }
                Field(stringResource(R.string.mx_f_author), m.author) { m = m.copy(author = it.take(60)) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) { Field(stringResource(R.string.mx_f_version), m.version) { m = m.copy(version = it.take(16)) } }
                    Column(Modifier.weight(1f)) { Field(stringResource(R.string.mx_f_ui), m.uiVersion) { m = m.copy(uiVersion = it.filter { c -> c.isLetterOrDigit() || c == '.' }.take(8)) } }
                }

                SectionHeader(stringResource(R.string.mx_reference))
                Text(stringResource(R.string.mx_reference_body), style = W.type.caption, color = W.colors.muted)
                WButton(stringResource(R.string.mx_reference_pick), { pickReference.launch(arrayOf("*/*")) }, kind = WButtonKind.SECONDARY, icon = WIcons.Folder)
                reference?.let { r ->
                    Text(stringResource(R.string.mx_reference_info, r.themeValues.size, r.themeValues.values.sumOf { it.size }, r.description?.uiVersion.orEmpty().ifBlank { "?" }), style = W.type.mono, color = W.colors.text)
                    WSwitchRow(stringResource(R.string.mx_tint), tintAccent, { tintAccent = it }, subtitle = stringResource(R.string.mx_tint_body, accentKeys.values.sumOf { it.size }))
                }

                WButton(
                    if (building) stringResource(R.string.mx_building) else stringResource(R.string.mx_build),
                    onClick = {
                        scope.launch {
                            building = true; result = null; progress = 0f
                            val accent = selected.spec.palette.accent.toHex()
                            val overrides = if (tintAccent) accentKeys.mapValues { (_, keys) -> keys.associateWith { accent } } else emptyMap()
                            val settings = m.copy(colorOverrides = overrides)
                            store.save(selected.copy(spec = selected.spec.copy(mtz = settings)))
                            val snap = WD.services.snapshot()
                            val res = withContext(Dispatchers.Default) {
                                runCatching {
                                    WD.services.mtz.export(
                                        selected, store.assetsDir(selected.id), settings, snap.screenWidthPx, snap.screenHeightPx,
                                        reference = reference?.asReference(overrides.keys),
                                        unlockHint = ctx.getString(R.string.mx_unlock_hint),
                                    ) { p -> progress = p }
                                }
                            }
                            res.onSuccess { r ->
                                val name = (m.title.ifBlank { selected.name }).filter { it.isLetterOrDigit() || it == ' ' || it == '-' }.trim().ifEmpty { "theme" }
                                file = withContext(Dispatchers.IO) { File(WD.services.exportsDir(), "$name.mtz").apply { writeBytes(r.bytes) } }
                                result = r
                            }.onFailure { snackbar.showSnackbar(ctx.getString(com.wdesign.ds.R.string.ds_error_generic, it.message ?: it.javaClass.simpleName)) }
                            building = false
                        }
                    },
                    enabled = !building, loading = building, icon = WIcons.Package, modifier = Modifier.fillMaxWidth(),
                )
                if (building) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = W.colors.accent, trackColor = W.colors.outline)

                result?.let { r ->
                    SectionHeader(stringResource(R.string.mx_result))
                    Text(stringResource(R.string.mx_size, "%.1f MB".format(r.bytes.size / 1048576f), r.iconCount), style = W.type.mono, color = W.colors.text)
                    WCard(Modifier.fillMaxWidth(), padding = 12.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (c in r.validation.results) {
                                val status = when (c.status) { CheckStatus.PASS -> SupportStatus.SUPPORTED; CheckStatus.WARN -> SupportStatus.PARTIAL; CheckStatus.FAIL -> SupportStatus.UNSUPPORTED; CheckStatus.SKIP -> SupportStatus.REQUIRES_ACTION }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(stringResource(checkLabel(c.item)), style = W.type.label, color = W.colors.text)
                                        if (c.detail.isNotBlank()) Text(c.detail, style = W.type.mono, color = W.colors.muted, maxLines = 2)
                                    }
                                    SupportBadge(status, stringResource(when (c.status) { CheckStatus.PASS -> R.string.mx_st_pass; CheckStatus.WARN -> R.string.mx_st_warn; CheckStatus.FAIL -> R.string.mx_st_fail; CheckStatus.SKIP -> R.string.mx_st_skip }))
                                }
                            }
                        }
                    }
                    for ((note, detail) in r.notes.distinctBy { it.first }) {
                        Text(if (note == MamlNote.DATE_PATTERN_SIMPLIFIED) stringResource(noteText(note), detail) else stringResource(noteText(note)), style = W.type.caption, color = W.colors.muted)
                    }
                    if (r.report.appliedOverrides.isNotEmpty() || r.report.ignoredOverrides.isNotEmpty()) {
                        Text(stringResource(R.string.mx_overrides, r.report.appliedOverrides.values.sumOf { it.size }, r.report.ignoredOverrides.values.sumOf { it.size }), style = W.type.caption, color = W.colors.muted)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WButton(stringResource(R.string.mx_save), { save.launch(file?.name ?: "theme.mtz") }, icon = WIcons.Download, modifier = Modifier.weight(1f))
                        WButton(stringResource(R.string.mx_open_themes), {
                            val f = file ?: return@WButton
                            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
                            if (!XiaomiIntents.openMtzInThemes(ctx, uri)) scope.launch { snackbar.showSnackbar(themesFailed) }
                        }, kind = WButtonKind.SECONDARY, modifier = Modifier.weight(1f))
                    }
                    if (Build.VERSION.SDK_INT >= 29) {
                        WButton(stringResource(R.string.mx_downloads), {
                            val f = file ?: return@WButton
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) {
                                    runCatching {
                                        val values = ContentValues().apply {
                                            put(MediaStore.Downloads.DISPLAY_NAME, f.name)
                                            put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                                            put(MediaStore.Downloads.RELATIVE_PATH, "Download/W Design")
                                        }
                                        val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)!!
                                        ctx.contentResolver.openOutputStream(uri)!!.use { out -> f.inputStream().use { it.copyTo(out) } }
                                    }.isSuccess
                                }
                                snackbar.showSnackbar(if (ok) dlMsg else ctx.getString(com.wdesign.ds.R.string.ds_error_generic, "MediaStore"))
                            }
                        }, kind = WButtonKind.GHOST, icon = WIcons.Download, modifier = Modifier.fillMaxWidth())
                    }
                }
                SectionHeader(stringResource(R.string.mx_install))
                Text(stringResource(R.string.mx_install_body), style = W.type.body, color = W.colors.muted)
            }
            Spacer(Modifier.height(24.dp).navigationBarsPadding())
        }
    }
}
