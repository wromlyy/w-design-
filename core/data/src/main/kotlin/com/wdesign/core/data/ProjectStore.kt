package com.wdesign.core.data

import android.content.Context
import com.wdesign.core.format.ImportPreview
import com.wdesign.core.model.DesignKind
import com.wdesign.core.model.DesignMeta
import com.wdesign.core.model.DesignProject
import com.wdesign.core.model.DesignSource
import com.wdesign.core.model.Ids
import com.wdesign.core.model.Preset
import com.wdesign.core.model.ThemeSpec
import com.wdesign.core.model.WDJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Result of an integrity check: projects whose referenced assets are missing on disk. */
data class IntegrityReport(val checked: Int, val broken: Map<String, List<String>>)

/**
 * Local project library: filesDir/projects/<id>/{project.json, assets/…}. Writes are atomic
 * (temp file + rename). Deleted projects move to a trash folder so deletion can be undone.
 *
 * Note: the plan's Room database is not used yet; this file store is the storage of record and
 * keeps the same API (observe/save/duplicate/rename/favourite/delete/undo/integrity).
 */
class ProjectStore(context: Context) {
    private val root = File(context.filesDir, "projects").apply { mkdirs() }
    private val trash = File(context.filesDir, "trash").apply { mkdirs() }
    private val mutex = Mutex()
    private val _projects = MutableStateFlow<List<DesignProject>>(emptyList())
    val projects: StateFlow<List<DesignProject>> = _projects.asStateFlow()
    @Volatile private var loaded = false

    fun dir(id: String) = File(root, safe(id))
    fun assetsDir(id: String) = File(dir(id), "assets")
    private fun safe(id: String) = id.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.ifEmpty { "x" }

    suspend fun load() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!loaded) { _projects.value = readAll(); loaded = true }
        }
    }

    private fun readAll(): List<DesignProject> = (root.listFiles() ?: emptyArray()).mapNotNull { d ->
        val f = File(d, "project.json")
        if (!f.isFile) null else runCatching { WDJson.decodeFromString(DesignProject.serializer(), f.readText()) }.getOrNull()
    }.sortedByDescending { it.meta.updatedAt }

    fun get(id: String): DesignProject? = _projects.value.firstOrNull { it.id == id }

    suspend fun save(project: DesignProject, touch: Boolean = true): DesignProject = withContext(Dispatchers.IO) {
        mutex.withLock {
            val now = System.currentTimeMillis()
            val p = if (touch) project.copy(meta = project.meta.copy(updatedAt = now, createdAt = project.meta.createdAt.takeIf { it > 0 } ?: now)) else project
            val d = dir(p.id).apply { mkdirs() }
            val tmp = File(d, "project.json.tmp")
            tmp.writeText(WDJson.encodeToString(DesignProject.serializer(), p))
            if (!tmp.renameTo(File(d, "project.json"))) { File(d, "project.json").writeText(tmp.readText()); tmp.delete() }
            _projects.value = (_projects.value.filter { it.id != p.id } + p).sortedByDescending { it.meta.updatedAt }
            p
        }
    }

    suspend fun create(name: String, spec: ThemeSpec, kind: DesignKind = DesignKind.THEME, source: DesignSource = DesignSource.USER, presetId: String? = null): DesignProject =
        save(DesignProject(Ids.next("p"), name, kind, spec, DesignMeta(source = source, presetId = presetId, draft = source == DesignSource.USER)))

    suspend fun fromPreset(preset: Preset): DesignProject = create(preset.name, preset.spec, source = DesignSource.PRESET, presetId = preset.id)

    suspend fun duplicate(id: String, copySuffix: String): DesignProject? = withContext(Dispatchers.IO) {
        val src = get(id) ?: return@withContext null
        val copy = src.copy(id = Ids.next("p"), name = (src.name + " " + copySuffix).take(80), meta = src.meta.copy(createdAt = 0, favorite = false))
        assetsDir(src.id).takeIf { it.isDirectory }?.copyRecursively(assetsDir(copy.id), overwrite = true)
        save(copy)
    }

    suspend fun rename(id: String, name: String) { get(id)?.let { save(it.copy(name = name.trim().take(80).ifEmpty { it.name })) } }
    suspend fun setFavorite(id: String, fav: Boolean) { get(id)?.let { save(it.copy(meta = it.meta.copy(favorite = fav)), touch = false) } }

    /** Moves the project to trash; returns true when [undoDelete] can bring it back. */
    suspend fun delete(id: String): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            val d = dir(id)
            val t = File(trash, safe(id))
            t.deleteRecursively()
            val ok = d.renameTo(t) || (d.copyRecursively(t, true) && d.deleteRecursively())
            _projects.value = _projects.value.filter { it.id != id }
            ok
        }
    }

    suspend fun undoDelete(id: String): DesignProject? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val t = File(trash, safe(id))
            if (!t.isDirectory) return@withLock null
            val d = dir(id)
            if (!t.renameTo(d)) { t.copyRecursively(d, true); t.deleteRecursively() }
            val p = runCatching { WDJson.decodeFromString(DesignProject.serializer(), File(d, "project.json").readText()) }.getOrNull()
            if (p != null) _projects.value = (_projects.value + p).sortedByDescending { it.meta.updatedAt }
            p
        }
    }

    suspend fun purgeTrash() = withContext(Dispatchers.IO) { trash.listFiles()?.forEach { it.deleteRecursively() } }

    /** Commits an inspected import: moves staged assets into a new project folder. */
    suspend fun commitImport(preview: ImportPreview, nameOverride: String? = null): DesignProject? = withContext(Dispatchers.IO) {
        val p = preview.project ?: return@withContext null
        val project = p.copy(id = Ids.next("p"), name = nameOverride ?: p.name)
        val staged = File(preview.stagedDir, "assets")
        val target = assetsDir(project.id)
        if (staged.isDirectory) {
            target.parentFile?.mkdirs()
            if (!staged.renameTo(target)) staged.copyRecursively(target, true)
        }
        preview.stagedDir.deleteRecursively()
        save(project)
    }

    suspend fun integrityCheck(): IntegrityReport = withContext(Dispatchers.IO) {
        val broken = LinkedHashMap<String, List<String>>()
        for (p in _projects.value) {
            val missing = p.spec.assetRefs().map { it.path }.filter { !File(assetsDir(p.id), it).isFile }
            if (missing.isNotEmpty()) broken[p.id] = missing
        }
        IntegrityReport(_projects.value.size, broken)
    }
}
