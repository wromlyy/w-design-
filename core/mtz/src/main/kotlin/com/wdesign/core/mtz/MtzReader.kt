package com.wdesign.core.mtz

import com.wdesign.core.format.ArchiveRejected
import com.wdesign.core.format.FileMagic
import com.wdesign.core.format.ImportIssue
import com.wdesign.core.format.ImportPreview
import com.wdesign.core.format.IssueCode
import com.wdesign.core.format.SafeZipReader
import com.wdesign.core.format.ZipLimits
import com.wdesign.core.format.readHead
import com.wdesign.core.model.AssetRef
import com.wdesign.core.model.DesignKind
import com.wdesign.core.model.DesignMeta
import com.wdesign.core.model.DesignProject
import com.wdesign.core.model.DesignSource
import com.wdesign.core.model.IconOverride
import com.wdesign.core.model.Ids
import com.wdesign.core.model.MtzSettings
import com.wdesign.core.model.ThemeSpec
import com.wdesign.core.model.WallpaperSpec
import com.wdesign.core.model.WallpaperType
import com.wdesign.core.mtz.MtzXml.childText
import com.wdesign.core.mtz.MtzXml.countElements
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream

data class MtzDescription(
    val title: String = "",
    val designer: String = "",
    val author: String = "",
    val version: String = "",
    val uiVersion: String = "",
    val description: String = "",
)

data class MtzInspection(
    val description: MtzDescription?,
    /** Module entry names found at the top level (icons, lockscreen, com.android.systemui, …). */
    val modules: List<String>,
    /** module → colour keys/values from its theme_values.xml (reference-template mode). */
    val themeValues: Map<String, Map<String, String>>,
    /** Staged copies of modules that carry theme_values.xml (kept on disk, not in memory). */
    val referenceModules: Map<String, File>,
    /** MAML element counts of lockscreen/advance/manifest.xml (parsed only, never executed). */
    val lockscreenElements: Map<String, Int>,
    val iconCount: Int,
    val preview: ImportPreview,
) {
    /** Loads only the requested reference modules (default: all) into memory for a build. */
    fun asReference(only: Set<String>? = null): MtzReference =
        MtzReference(description?.uiVersion, referenceModules.filterKeys { only == null || it in only }.mapValues { it.value.readBytes() })
}

/**
 * Opens a .mtz with the same streaming limits as .wdesign imports (80 MB total, the Xiaomi audit
 * maximum), parses description.xml with DOCTYPE/entities disabled and converts wallpapers and
 * icons into a W Design project. Nothing inside the package is ever executed.
 */
class MtzReader(private val limits: ZipLimits = ZipLimits.Mtz) {

    fun inspect(input: InputStream, stagingDir: File, cancelled: () -> Boolean = { false }, progress: (Long) -> Unit = {}): MtzInspection {
        stagingDir.mkdirs()
        val top = SafeZipReader(limits)
        val warnings = top.warnings
        val errors = mutableListOf<ImportIssue>()
        var description: MtzDescription? = null
        val modules = mutableListOf<String>()
        val raw = File(stagingDir, "raw").apply { mkdirs() }
        var seq = 0
        fun spill(bytes: ByteArray): File = File(raw, "e${seq++}").apply { writeBytes(bytes) }
        val wallpapers = LinkedHashMap<String, File>()
        val previews = LinkedHashMap<String, File>()
        val icons = LinkedHashMap<String, File>()
        val themeValues = LinkedHashMap<String, Map<String, String>>()
        val refModules = LinkedHashMap<String, File>()
        var lockCounts: Map<String, Int> = emptyMap()

        try {
            top.walk(input, allowAnyName = true, cancelled = cancelled, progress = progress) { name, bytes ->
                when {
                    FileMagic.isExecutable(bytes) -> warnings += ImportIssue(IssueCode.SKIPPED_EXECUTABLE, name)
                    name == "description.xml" -> description = parseDescription(bytes, warnings)
                    name.startsWith("wallpaper/") && isImage(name, bytes) -> wallpapers[name.substringAfterLast('/')] = spill(bytes)
                    name.startsWith("preview/") && isImage(name, bytes) -> previews[name.substringAfterLast('/')] = spill(bytes)
                    !name.contains('/') && FileMagic.isZip(bytes) -> {
                        modules += name
                        readModule(name, bytes, warnings, icons, themeValues, refModules, ::spill) { lockCounts = it }
                    }
                    else -> Unit // other files (fonts, sounds, boots…) are listed by module only
                }
            }
        } catch (ex: ArchiveRejected) {
            errors += ex.issue
        }
        if (errors.isEmpty() && description == null) errors += ImportIssue(IssueCode.NOT_A_DESIGN, "description.xml")

        if (errors.isNotEmpty()) {
            val p = ImportPreview(null, null, stagingDir, emptyList(), null, emptyList(), warnings.toList(), errors, false)
            return MtzInspection(description, modules, themeValues, refModules, lockCounts, icons.size, p)
        }

        // Convert to a W Design project.
        val assets = File(stagingDir, "assets").apply { mkdirs() }
        val staged = mutableListOf<String>()
        fun stage(rel: String, file: File): AssetRef {
            val target = File(assets, rel).apply { parentFile?.mkdirs() }
            if (!file.renameTo(target)) { file.copyTo(target, overwrite = true); file.delete() }
            staged += rel
            val bytes = target.inputStream().use { it.readHead(16) }
            val mime = if (FileMagic.isPng(bytes)) "image/png" else if (FileMagic.isWebp(bytes)) "image/webp" else "image/jpeg"
            return AssetRef(rel, mime)
        }
        val home = (wallpapers.entries.firstOrNull { it.key.startsWith("default_wallpaper") } ?: wallpapers.entries.firstOrNull { !it.key.contains("lock") })
            ?.let { stage("wallpapers/home." + ext(it.value), it.value) }
        val lock = wallpapers.entries.firstOrNull { it.key.contains("lock") }?.let { stage("wallpapers/lock." + ext(it.value), it.value) }
        val overrides = icons.entries.map { (n, b) -> IconOverride(packageName = n, asset = stage("icons/${safe(n)}.png", b)) }
        val d = description!!
        val base = ThemeSpec()
        val spec = base.copy(
            wallpaper = home?.let { WallpaperSpec(type = WallpaperType.PHOTO, photo = it) } ?: base.wallpaper,
            lockScreen = base.lockScreen.copy(wallpaper = lock?.let { WallpaperSpec(type = WallpaperType.PHOTO, photo = it) }),
            icons = base.icons.copy(overrides = overrides),
            mtz = MtzSettings(title = d.title, designer = d.designer, author = d.author, version = d.version.ifBlank { "1.0" }, uiVersion = d.uiVersion.ifBlank { "1" }, description = d.description),
        )
        val project = DesignProject(
            id = Ids.next("p"), name = d.title.ifBlank { "Imported MTZ" }.take(80), kind = DesignKind.THEME, spec = spec,
            meta = DesignMeta(author = d.author.ifBlank { d.designer }.take(80), source = DesignSource.MTZ_IMPORT, draft = false),
        )
        val previewImg = (previews.entries.firstOrNull { it.key.contains("lockscreen") }?.value ?: previews.values.firstOrNull())?.readBytes()
        val p = ImportPreview(null, project, stagingDir, staged, previewImg, emptyList(), warnings.toList(), emptyList(), false)
        return MtzInspection(description, modules, themeValues, refModules, lockCounts, icons.size, p)
    }

    private fun ext(f: File) = ext(f.inputStream().use { it.readHead(16) })
    private fun ext(b: ByteArray) = if (FileMagic.isPng(b)) "png" else if (FileMagic.isWebp(b)) "webp" else "jpg"
    private fun safe(n: String) = n.filter { it.isLetterOrDigit() || it == '.' || it == '_' }

    private fun isImage(name: String, bytes: ByteArray): Boolean {
        val e = name.substringAfterLast('.', "").lowercase()
        return e in setOf("png", "jpg", "jpeg", "webp") && FileMagic.imageMatches(e, bytes)
    }

    private fun parseDescription(bytes: ByteArray, warnings: MutableList<ImportIssue>): MtzDescription? = try {
        val root = MtzXml.parseSecure(bytes).documentElement
        MtzDescription(
            title = root.childText("title").orEmpty().take(120),
            designer = root.childText("designer").orEmpty().take(120),
            author = root.childText("author").orEmpty().take(120),
            version = root.childText("version").orEmpty().take(40),
            uiVersion = root.childText("uiVersion").orEmpty().take(20),
            description = root.childText("description").orEmpty().take(2000),
        )
    } catch (ex: MtzXml.UnsafeXmlException) {
        throw ArchiveRejected(ImportIssue(IssueCode.XML_UNSAFE, "description.xml"))
    } catch (ex: Exception) {
        warnings += ImportIssue(IssueCode.INVALID_XML, "description.xml"); null
    }

    private fun readModule(
        module: String, bytes: ByteArray, warnings: MutableList<ImportIssue>,
        icons: MutableMap<String, File>, themeValues: MutableMap<String, Map<String, String>>,
        refModules: MutableMap<String, File>, spill: (ByteArray) -> File, onLock: (Map<String, Int>) -> Unit,
    ) {
        val nested = SafeZipReader(limits)
        try {
            nested.walk(ByteArrayInputStream(bytes), allowAnyName = true) { name, data ->
                when {
                    FileMagic.isExecutable(data) -> warnings += ImportIssue(IssueCode.SKIPPED_EXECUTABLE, "$module/$name")
                    module == "icons" && name.startsWith("res/drawable") && !name.contains("fancy_icons") && name.endsWith(".png", true) && FileMagic.isPng(data) -> {
                        val n = name.substringAfterLast('/').substringBeforeLast('.')
                        if (n !in setOf("icon_background", "icon_mask", "icon_border", "icon_pattern") && n !in icons && icons.size < 600) icons[n] = spill(data)
                    }
                    module == "lockscreen" && name == "advance/manifest.xml" -> {
                        try { onLock(MtzXml.parseSecure(data).countElements()) } catch (ex: MtzXml.UnsafeXmlException) {
                            warnings += ImportIssue(IssueCode.XML_UNSAFE, "$module/$name")
                        } catch (ex: Exception) {
                            warnings += ImportIssue(IssueCode.INVALID_XML, "$module/$name")
                        }
                    }
                    name.substringAfterLast('/') == "theme_values.xml" -> {
                        try {
                            themeValues[module] = (themeValues[module].orEmpty()) + ThemeValues.colors(data)
                            if (module !in refModules) refModules[module] = spill(bytes)
                        } catch (ex: MtzXml.UnsafeXmlException) {
                            warnings += ImportIssue(IssueCode.XML_UNSAFE, "$module/$name")
                        } catch (ex: Exception) {
                            warnings += ImportIssue(IssueCode.INVALID_XML, "$module/$name")
                        }
                    }
                }
            }
        } catch (ex: ArchiveRejected) {
            warnings += ImportIssue(ex.issue.code, "$module: ${ex.issue.detail}")
        }
        warnings += nested.warnings.map { it.copy(detail = "$module/${it.detail}") }
    }
}
