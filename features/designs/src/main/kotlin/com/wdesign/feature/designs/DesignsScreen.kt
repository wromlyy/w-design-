package com.wdesign.feature.designs

import com.wdesign.ds.SafeLaunchedEffect
import com.wdesign.ds.rememberSafeScope
import com.wdesign.ds.launchSafely
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wdesign.core.data.WD
import com.wdesign.core.format.BackupReader
import com.wdesign.core.format.FileMagic
import com.wdesign.core.format.ImportPreview
import com.wdesign.core.format.WDesignReader
import com.wdesign.core.format.readHead
import com.wdesign.core.model.DesignSource
import com.wdesign.core.mtz.MtzReader
import com.wdesign.ds.DesignPreview
import com.wdesign.ds.EmptyState
import com.wdesign.ds.Routes
import com.wdesign.ds.W
import com.wdesign.ds.WButton
import com.wdesign.ds.WButtonKind
import com.wdesign.ds.WCard
import com.wdesign.ds.WChip
import com.wdesign.ds.WIcons
import com.wdesign.ds.WTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A file handed to the app by ACTION_VIEW/ACTION_SEND, waiting for the import screen. */
object PendingImport { val uri: MutableState<Uri?> = mutableStateOf(null) }

private enum class Filter { ALL, FAV, IMPORTED }

@Composable
fun DesignsScreen(go: (String) -> Unit, snackbar: SnackbarHostState) {
    val scope = rememberSafeScope()
    val store = WD.services.projects
    SafeLaunchedEffect(Unit) { store.load() }
    val all by store.projects.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(Filter.ALL) }
    val copy = stringResource(R.string.dz_copy)
    val deleted = stringResource(R.string.dz_deleted)
    val undo = stringResource(R.string.dz_undo)
    val list = all.filter { p ->
        (query.isBlank() || p.name.contains(query.trim(), ignoreCase = true)) && when (filter) {
            Filter.ALL -> true; Filter.FAV -> p.meta.favorite; Filter.IMPORTED -> p.meta.source == DesignSource.IMPORTED || p.meta.source == DesignSource.MTZ_IMPORT
        }
    }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 110.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row { Text(stringResource(R.string.dz_title), style = W.type.display, color = W.colors.text, modifier = Modifier.weight(1f)); WButton(stringResource(R.string.dz_import), { go(Routes.IMPORT) }, kind = WButtonKind.SECONDARY, icon = WIcons.Upload) }
        }
        item { OutlinedTextField(query, { query = it.take(60) }, label = { Text(stringResource(R.string.dz_search)) }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WChip(stringResource(R.string.dz_all), filter == Filter.ALL, { filter = Filter.ALL })
                WChip(stringResource(R.string.dz_fav), filter == Filter.FAV, { filter = Filter.FAV })
                WChip(stringResource(R.string.dz_imported), filter == Filter.IMPORTED, { filter = Filter.IMPORTED })
            }
        }
        if (list.isEmpty()) item { EmptyState(stringResource(R.string.dz_empty), stringResource(R.string.dz_empty_body)) }
        items(list, key = { it.id }) { p ->
            WCard(Modifier.fillMaxWidth(), padding = 10.dp) {
                Row {
                    DesignPreview(p.spec, store.assetsDir(p.id), lock = true, modifier = Modifier.width(72.dp), widthPx = 180, corner = 10.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(p.name + if (p.meta.favorite) " ♥" else "", style = W.type.label, color = W.colors.text)
                        Text(p.kind.name.lowercase().replace('_', ' ') + " · " + p.meta.source.name.lowercase().replace('_', ' '), style = W.type.caption, color = W.colors.muted)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            WButton(stringResource(R.string.dz_open), { scope.launch { WD.services.settings.setActiveProject(p.id) }; go(Routes.lockscreen(p.id)) }, kind = WButtonKind.SECONDARY)
                            WButton(stringResource(R.string.dz_active), { scope.launch { WD.services.settings.setActiveProject(p.id) }; go(Routes.HOME) }, kind = WButtonKind.GHOST)
                            WButton("MTZ", { go(Routes.mtz(p.id)) }, kind = WButtonKind.GHOST, icon = WIcons.Package)
                            WButton(stringResource(R.string.dz_fav), { scope.launch { store.setFavorite(p.id, !p.meta.favorite) } }, kind = WButtonKind.GHOST, icon = WIcons.Heart)
                            WButton(stringResource(R.string.dz_dup), { scope.launch { store.duplicate(p.id, copy) } }, kind = WButtonKind.GHOST, icon = WIcons.Copy)
                            WButton(stringResource(R.string.dz_delete), {
                                scope.launch {
                                    store.delete(p.id)
                                    if (snackbar.showSnackbar(deleted, undo, duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) store.undoDelete(p.id)
                                }
                            }, kind = WButtonKind.DANGER, icon = WIcons.Trash)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ImportScreen(go: (String) -> Unit, back: () -> Unit, snackbar: SnackbarHostState) {
    val ctx = LocalContext.current
    val scope = rememberSafeScope()
    var reading by remember { mutableStateOf(false) }
    var previews by remember { mutableStateOf<List<ImportPreview>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    fun inspect(uri: Uri) {
        scope.launch {
            reading = true; error = null; previews = emptyList()
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val head = (ctx.contentResolver.openInputStream(uri) ?: error("Cannot open the file")).use { it.readHead(4) }
                    require(FileMagic.isZip(head)) { "not a ZIP archive" }
                    val name = uri.lastPathSegment.orEmpty().lowercase()
                    val staging = WD.services.stagingDir("import")
                    (ctx.contentResolver.openInputStream(uri) ?: error("Cannot open the file")).use { input ->
                        when {
                            name.endsWith(".mtz") -> listOf(MtzReader().inspect(input, staging).preview)
                            name.endsWith(".wdbackup") -> BackupReader().read(input, staging).projects
                            else -> {
                                val p = WDesignReader().inspect(input, staging)
                                // Unknown extension and not a W Design archive: try it as MTZ.
                                if (!p.ok && p.manifest == null && !p.generic) (ctx.contentResolver.openInputStream(uri) ?: error("Cannot open the file")).use { listOf(MtzReader().inspect(it, WD.services.stagingDir("import-mtz")).preview) } else listOf(p)
                            }
                        }
                    }
                }
            }
            reading = false
            result.onSuccess { previews = it }.onFailure { error = it.message ?: it.javaClass.simpleName }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) inspect(uri) }
    val pending by PendingImport.uri
    SafeLaunchedEffect(pending) { pending?.let { inspect(it); PendingImport.uri.value = null } }
    Column(Modifier.fillMaxSize()) {
        WTopBar(stringResource(R.string.im_title), back, stringResource(com.wdesign.ds.R.string.ds_back))
        Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.im_body), style = W.type.body, color = W.colors.muted)
            WButton(stringResource(R.string.im_pick), { picker.launchSafely(arrayOf("application/zip", "application/octet-stream", "*/*"), scope) }, icon = WIcons.Folder, modifier = Modifier.fillMaxWidth())
            if (reading) { CircularProgressIndicator(color = W.colors.accent); Text(stringResource(R.string.im_reading), color = W.colors.muted) }
            error?.let { Text(stringResource(R.string.im_failed, it), color = W.colors.danger, style = W.type.body) }
            for (p in previews) {
                WCard(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val proj = p.project
                        if (proj == null || !p.ok) Text(stringResource(R.string.im_failed, p.errors.joinToString { it.code.name.lowercase().replace('_', ' ') }), color = W.colors.danger)
                        else {
                            Text(proj.name, style = W.type.headline, color = W.colors.text)
                            Text(proj.kind.name.lowercase() + (proj.meta.author.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""), style = W.type.caption, color = W.colors.muted)
                            DesignPreview(proj.spec, java.io.File(p.stagedDir, "assets"), lock = true, modifier = Modifier.width(140.dp), widthPx = 270)
                            if (p.warnings.isNotEmpty()) Text(stringResource(R.string.im_warnings, p.warnings.size), style = W.type.caption, color = W.colors.warning)
                            WButton(stringResource(R.string.im_ok), {
                                scope.launch {
                                    val saved = WD.services.projects.commitImport(p)
                                    previews = previews - p
                                    if (saved != null) snackbar.showSnackbar(ctx.getString(R.string.im_done, saved.name))
                                    if (previews.isEmpty()) go(Routes.DESIGNS)
                                }
                            }, icon = WIcons.Check)
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
