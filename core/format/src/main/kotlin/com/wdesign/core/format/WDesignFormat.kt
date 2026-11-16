package com.wdesign.core.format

import com.wdesign.core.model.AssetRef
import com.wdesign.core.model.DesignKind
import com.wdesign.core.model.DesignMeta
import com.wdesign.core.model.DesignProject
import com.wdesign.core.model.DesignSource
import com.wdesign.core.model.IconOverride
import com.wdesign.core.model.Ids
import com.wdesign.core.model.LockScreenSpec
import com.wdesign.core.model.Palette
import com.wdesign.core.model.ThemeSpec
import com.wdesign.core.model.WDJson
import com.wdesign.core.model.WDJsonPretty
import com.wdesign.core.model.WallpaperSpec
import com.wdesign.core.model.WallpaperType
import com.wdesign.core.model.WidgetSpec
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Parts of a design that can be exported on their own. */
@Serializable
enum class Component { THEME, PALETTE, LOCKSCREEN, WIDGETS, WALLPAPERS, ICONS, IMAGES, HISTORY }

@Serializable
data class WManifest(
    val format: String = WDesignFormat.FORMAT,
    val formatVersion: Int = WDesignFormat.VERSION,
    val minReaderVersion: Int = 1,
    val generator: String = "W Design",
    val kind: DesignKind = DesignKind.THEME,
    val id: String = "",
    val name: String = "",
    val author: String = "",
    val createdAt: Long = 0L,
    val components: List<Component> = emptyList(),
    val files: Map<String, String> = emptyMap(),
)

object WDesignFormat {
    const val FORMAT = "wdesign"
    const val VERSION = 1
    const val READER_VERSION = 1
    const val MANIFEST = "manifest.json"
    const val THEME = "theme.json"
    const val PALETTE = "palette.json"
    const val LOCKSCREEN = "lockscreen.json"
    const val WIDGETS = "widgets.json"
    const val HISTORY = "history.json"
    const val PREVIEW = "preview.png"
    const val ASSETS = "assets/"
    val ALLOWED_EXT = setOf("json", "png", "jpg", "jpeg", "webp", "txt")
    val IMAGE_EXT = setOf("png", "jpg", "jpeg", "webp")

    fun componentsFor(kind: DesignKind): Set<Component> = when (kind) {
        DesignKind.THEME -> Component.entries.toSet() - Component.HISTORY
        DesignKind.LOCK_SCREEN -> setOf(Component.LOCKSCREEN, Component.WALLPAPERS, Component.IMAGES, Component.PALETTE)
        DesignKind.WIDGET -> setOf(Component.WIDGETS, Component.IMAGES, Component.PALETTE)
        DesignKind.ICON_PACK -> setOf(Component.ICONS, Component.PALETTE)
        DesignKind.WALLPAPER, DesignKind.LIVE_WALLPAPER -> setOf(Component.WALLPAPERS, Component.PALETTE)
        DesignKind.CONTROL_CENTER -> setOf(Component.PALETTE)
    }

    /** Which component an asset path belongs to. */
    fun componentOf(path: String): Component = when {
        path.startsWith("wallpapers/") -> Component.WALLPAPERS
        path.startsWith("icons/") -> Component.ICONS
        else -> Component.IMAGES
    }
}

/**
 * Writes .wdesign archives: manifest.json (with a sha256 for every other file), the
 * authoritative theme.json, convenience palette/lockscreen/widgets JSON, assets and a preview.
 */
class WDesignWriter(private val generator: String = "W Design 1.0.0") {

    fun write(
        project: DesignProject,
        assetsDir: File?,
        preview: ByteArray?,
        out: OutputStream,
        kind: DesignKind = project.kind,
        history: List<ThemeSpec>? = null,
    ) {
        val comps = WDesignFormat.componentsFor(kind).toMutableSet()
        if (history != null) comps += Component.HISTORY
        val files = linkedMapOf<String, ByteArray>()
        val spec = filterSpec(project.spec, kind)
        files[WDesignFormat.THEME] = WDJsonPretty.encodeToString(ThemeSpec.serializer(), spec).toByteArray()
        if (Component.PALETTE in comps) files[WDesignFormat.PALETTE] = WDJsonPretty.encodeToString(Palette.serializer(), spec.palette).toByteArray()
        if (Component.LOCKSCREEN in comps) files[WDesignFormat.LOCKSCREEN] = WDJsonPretty.encodeToString(LockScreenSpec.serializer(), spec.lockScreen).toByteArray()
        if (Component.WIDGETS in comps) files[WDesignFormat.WIDGETS] = WDJsonPretty.encodeToString(ListSerializer(WidgetSpec.serializer()), spec.widgets).toByteArray()
        if (history != null) files[WDesignFormat.HISTORY] = WDJson.encodeToString(ListSerializer(ThemeSpec.serializer()), history.takeLast(40)).toByteArray()
        if (assetsDir != null) {
            for (ref in spec.assetRefs()) {
                if (WDesignFormat.componentOf(ref.path) !in comps) continue
                val f = File(assetsDir, ref.path)
                if (f.isFile && f.canonicalPath.startsWith(assetsDir.canonicalPath)) files[WDesignFormat.ASSETS + ref.path] = f.readBytes()
            }
        }
        if (preview != null) files[WDesignFormat.PREVIEW] = preview
        val manifest = WManifest(
            generator = generator, kind = kind, id = project.id, name = project.name, author = project.meta.author,
            createdAt = project.meta.createdAt, components = comps.sorted(), files = files.mapValues { sha256(it.value) },
        )
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(WDesignFormat.MANIFEST))
            zip.write(WDJsonPretty.encodeToString(WManifest.serializer(), manifest).toByteArray())
            zip.closeEntry()
            for ((path, bytes) in files) {
                zip.putNextEntry(ZipEntry(path)); zip.write(bytes); zip.closeEntry()
            }
        }
    }

    /** Component exports keep only the relevant part of the spec (the rest stays default). */
    private fun filterSpec(spec: ThemeSpec, kind: DesignKind): ThemeSpec {
        val d = ThemeSpec()
        return when (kind) {
            DesignKind.THEME -> spec
            DesignKind.LOCK_SCREEN -> d.copy(palette = spec.palette, wallpaper = spec.wallpaper, lockScreen = spec.lockScreen, appearance = spec.appearance)
            DesignKind.WIDGET -> d.copy(palette = spec.palette, widgets = spec.widgets, wallpaper = spec.wallpaper, appearance = spec.appearance)
            DesignKind.ICON_PACK -> d.copy(palette = spec.palette, icons = spec.icons)
            DesignKind.WALLPAPER -> d.copy(palette = spec.palette, wallpaper = spec.wallpaper, lockScreen = d.lockScreen.copy(wallpaper = spec.lockScreen.wallpaper))
            DesignKind.LIVE_WALLPAPER -> d.copy(palette = spec.palette, live = spec.live, wallpaper = spec.wallpaper)
            DesignKind.CONTROL_CENTER -> d.copy(palette = spec.palette, controlCenter = spec.controlCenter, appearance = spec.appearance)
        }
    }
}

/** Result of inspecting an archive before anything is committed to the library. */
data class ImportPreview(
    val manifest: WManifest?,
    val project: DesignProject?,
    /** Staging folder containing assets/…; move it into the project folder on import, delete on cancel. */
    val stagedDir: File,
    val assets: List<String>,
    val previewPng: ByteArray?,
    val history: List<ThemeSpec>,
    val warnings: List<ImportIssue>,
    val errors: List<ImportIssue>,
    val generic: Boolean,
) {
    val ok: Boolean get() = project != null && errors.isEmpty()
}

/**
 * Reads .wdesign archives defensively: streaming limits, zip-slip protection, extension and
 * magic checks (executables are skipped even when renamed), sha256 verification against the
 * manifest and format-version gating. Archives without a manifest fall back to [GenericZipImporter].
 */
class WDesignReader(private val limits: ZipLimits = ZipLimits.Default) {

    fun inspect(
        input: InputStream,
        stagingDir: File,
        cancelled: () -> Boolean = { false },
        progress: (Long) -> Unit = {},
    ): ImportPreview {
        stagingDir.mkdirs()
        val zip = SafeZipReader(limits)
        val json = HashMap<String, ByteArray>()
        val images = LinkedHashMap<String, ByteArray>()
        val errors = mutableListOf<ImportIssue>()
        try {
            zip.walk(input, cancelled = cancelled, progress = progress) { name, bytes ->
                val ext = name.substringAfterLast('.', "").lowercase()
                when {
                    FileMagic.isExecutable(bytes) -> zip.warnings += ImportIssue(IssueCode.SKIPPED_EXECUTABLE, name)
                    FileMagic.isZip(bytes) -> zip.warnings += ImportIssue(IssueCode.NESTED_ARCHIVE, name)
                    ext !in WDesignFormat.ALLOWED_EXT -> zip.warnings += ImportIssue(IssueCode.SKIPPED_EXTENSION, name)
                    ext in WDesignFormat.IMAGE_EXT && !FileMagic.imageMatches(ext, bytes) -> zip.warnings += ImportIssue(IssueCode.MAGIC_MISMATCH, name)
                    ext == "json" && bytes.size > limits.maxJsonBytes -> zip.warnings += ImportIssue(IssueCode.JSON_TOO_LARGE, name)
                    ext == "json" || ext == "txt" -> json[name] = bytes
                    else -> images[name] = bytes
                }
            }
        } catch (ex: ArchiveRejected) {
            errors += ex.issue
            return ImportPreview(null, null, stagingDir, emptyList(), null, emptyList(), zip.warnings.toList(), errors, false)
        }
        val warnings = zip.warnings

        val manifestBytes = json[WDesignFormat.MANIFEST]
            ?: return GenericZipImporter.fromImages(images, stagingDir, warnings)

        val manifest = runCatching { WDJson.decodeFromString(WManifest.serializer(), manifestBytes.decodeToString()) }.getOrNull()
        if (manifest == null || manifest.format != WDesignFormat.FORMAT) {
            errors += ImportIssue(if (manifest == null) IssueCode.INVALID_JSON else IssueCode.NOT_A_DESIGN, WDesignFormat.MANIFEST)
            return ImportPreview(manifest, null, stagingDir, emptyList(), null, emptyList(), warnings, errors, false)
        }
        if (manifest.minReaderVersion > WDesignFormat.READER_VERSION || manifest.formatVersion > WDesignFormat.VERSION) {
            errors += ImportIssue(IssueCode.FUTURE_VERSION, "${manifest.formatVersion}")
            return ImportPreview(manifest, null, stagingDir, emptyList(), null, emptyList(), warnings, errors, false)
        }

        // Verify checksums for everything listed; drop anything that does not match.
        val all = json + images
        val verified = HashMap<String, ByteArray>()
        for ((path, bytes) in all) {
            if (path == WDesignFormat.MANIFEST) continue
            val expected = manifest.files[path]
            when {
                expected == null -> warnings += ImportIssue(IssueCode.UNLISTED_FILE, path)
                expected.lowercase() != sha256(bytes) -> {
                    if (path == WDesignFormat.THEME) errors += ImportIssue(IssueCode.CHECKSUM_MISMATCH, path)
                    else warnings += ImportIssue(IssueCode.CHECKSUM_MISMATCH, path)
                }
                else -> verified[path] = bytes
            }
        }
        for (listed in manifest.files.keys) if (listed !in all) {
            if (listed == WDesignFormat.THEME) errors += ImportIssue(IssueCode.MISSING_FILE, listed)
            else warnings += ImportIssue(IssueCode.MISSING_FILE, listed)
        }
        if (errors.isNotEmpty()) return ImportPreview(manifest, null, stagingDir, emptyList(), null, emptyList(), warnings, errors, false)

        val themeBytes = verified[WDesignFormat.THEME]
        if (themeBytes == null) {
            errors += ImportIssue(IssueCode.MISSING_FILE, WDesignFormat.THEME)
            return ImportPreview(manifest, null, stagingDir, emptyList(), null, emptyList(), warnings, errors, false)
        }
        val spec = runCatching { WDJson.decodeFromString(ThemeSpec.serializer(), themeBytes.decodeToString()) }.getOrElse {
            errors += ImportIssue(IssueCode.INVALID_JSON, WDesignFormat.THEME)
            return ImportPreview(manifest, null, stagingDir, emptyList(), null, emptyList(), warnings, errors, false)
        }
        val history = verified[WDesignFormat.HISTORY]?.let { b ->
            runCatching { WDJson.decodeFromString(ListSerializer(ThemeSpec.serializer()), b.decodeToString()) }.getOrNull()
        }.orEmpty()

        // Stage verified assets.
        val staged = mutableListOf<String>()
        val assetsRoot = File(stagingDir, "assets").canonicalFile
        for ((path, bytes) in verified) {
            if (!path.startsWith(WDesignFormat.ASSETS)) continue
            val rel = path.removePrefix(WDesignFormat.ASSETS)
            val target = File(assetsRoot, rel).canonicalFile
            if (!target.path.startsWith(assetsRoot.path + File.separator)) { warnings += ImportIssue(IssueCode.ZIP_SLIP, path); continue }
            target.parentFile?.mkdirs()
            target.writeBytes(bytes)
            staged += rel
        }
        // Asset references that did not survive verification are cleared so the project stays consistent.
        val missing = spec.assetRefs().map { it.path }.filter { it !in staged }
        missing.forEach { warnings += ImportIssue(IssueCode.MISSING_FILE, it) }
        val cleanSpec = if (missing.isEmpty()) spec else stripAssets(spec, missing.toSet())

        val project = DesignProject(
            id = Ids.next("p"), name = manifest.name.ifBlank { "Imported design" }.take(80), kind = manifest.kind, spec = cleanSpec,
            meta = DesignMeta(createdAt = manifest.createdAt, author = manifest.author.take(80), source = DesignSource.IMPORTED, draft = false),
        )
        return ImportPreview(manifest, project, stagingDir, staged, verified[WDesignFormat.PREVIEW], history, warnings, errors, false)
    }

    private fun stripAssets(spec: ThemeSpec, missing: Set<String>): ThemeSpec {
        fun AssetRef?.keep() = this?.takeIf { it.path !in missing }
        return spec.copy(
            wallpaper = spec.wallpaper.copy(photo = spec.wallpaper.photo.keep()).let { if (it.type == WallpaperType.PHOTO && it.photo == null) it.copy(type = WallpaperType.GRADIENT) else it },
            lockScreen = spec.lockScreen.copy(
                wallpaper = spec.lockScreen.wallpaper?.let { w -> w.copy(photo = w.photo.keep()).let { if (it.type == WallpaperType.PHOTO && it.photo == null) it.copy(type = WallpaperType.GRADIENT) else it } },
                elements = spec.lockScreen.elements.filter { it.assetPath() == null || it.assetPath() !in missing },
            ),
            home = spec.home.copy(elements = spec.home.elements.filter { it.assetPath() == null || it.assetPath() !in missing }),
            icons = spec.icons.copy(overrides = spec.icons.overrides.map { if (it.asset?.path in missing) it.copy(asset = null) else it }),
            widgets = spec.widgets.map { if (it.image?.path in missing) it.copy(image = null) else it },
        )
    }
}

/**
 * Turns a plain ZIP of images (no manifest) into a new project: files named wallpaper*,
 * lock* and icons/<package>.png become the home wallpaper, lock wallpaper and icon overrides.
 */
object GenericZipImporter {
    private val pkgRe = Regex("""^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z0-9_]+)+$""")

    fun fromImages(images: Map<String, ByteArray>, stagingDir: File, warnings: MutableList<ImportIssue>): ImportPreview {
        if (images.isEmpty()) {
            return ImportPreview(null, null, stagingDir, emptyList(), null, emptyList(), warnings, listOf(ImportIssue(IssueCode.NO_MANIFEST)), true)
        }
        val assets = File(stagingDir, "assets").apply { mkdirs() }
        val staged = mutableListOf<String>()
        var home: AssetRef? = null
        var lock: AssetRef? = null
        val icons = mutableListOf<IconOverride>()
        for ((name, bytes) in images) {
            val base = name.substringAfterLast('/').lowercase()
            val ext = base.substringAfterLast('.', "png")
            val mime = if (ext == "png") "image/png" else if (ext == "webp") "image/webp" else "image/jpeg"
            when {
                name.lowercase().startsWith("icons/") || name.lowercase().contains("/icons/") -> {
                    val pkg = name.substringAfterLast('/').substringBeforeLast('.')
                    if (pkgRe.matches(pkg)) {
                        val rel = "icons/$pkg.$ext"
                        File(assets, rel).apply { parentFile?.mkdirs() }.writeBytes(bytes)
                        staged += rel
                        icons += IconOverride(pkg, asset = AssetRef(rel, mime))
                    }
                }
                base.startsWith("lock") && lock == null -> {
                    val rel = "wallpapers/lock.$ext"
                    File(assets, rel).apply { parentFile?.mkdirs() }.writeBytes(bytes); staged += rel; lock = AssetRef(rel, mime)
                }
                (base.startsWith("wallpaper") || base.startsWith("home") || base.startsWith("default_wallpaper")) && home == null -> {
                    val rel = "wallpapers/home.$ext"
                    File(assets, rel).apply { parentFile?.mkdirs() }.writeBytes(bytes); staged += rel; home = AssetRef(rel, mime)
                }
            }
        }
        if (home == null && lock == null && icons.isEmpty()) {
            // Fall back to the first image as wallpaper.
            val (name, bytes) = images.entries.first()
            val ext = name.substringAfterLast('.', "jpg").lowercase()
            val rel = "wallpapers/home.$ext"
            File(assets, rel).apply { parentFile?.mkdirs() }.writeBytes(bytes); staged += rel
            home = AssetRef(rel, if (ext == "png") "image/png" else "image/jpeg")
        }
        val base = ThemeSpec()
        val spec = base.copy(
            wallpaper = home?.let { WallpaperSpec(type = WallpaperType.PHOTO, photo = it) } ?: base.wallpaper,
            lockScreen = base.lockScreen.copy(wallpaper = lock?.let { WallpaperSpec(type = WallpaperType.PHOTO, photo = it) }),
            icons = base.icons.copy(overrides = icons),
        )
        val kind = if (home == null && lock == null) DesignKind.ICON_PACK else DesignKind.THEME
        val project = DesignProject(Ids.next("p"), "Imported images", kind, spec, DesignMeta(source = DesignSource.IMPORTED, draft = false))
        return ImportPreview(null, project, stagingDir, staged, null, emptyList(), warnings, emptyList(), true)
    }
}
