package com.wdesign.core.mtz

import com.wdesign.core.format.FileMagic
import com.wdesign.core.mtz.MtzXml.esc
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Metadata for description.xml. */
data class MtzMeta(
    val title: String,
    val designer: String,
    val author: String,
    val version: String = "1.0",
    val uiVersion: String = "1",
    val description: String = "",
)

/** A ready MAML lock screen: the manifest and every image it references (path → bytes). */
data class MamlPackage(val manifestXml: String, val assets: Map<String, ByteArray>)

/** Theme modules from a user-supplied reference MTZ (module name → raw nested ZIP bytes). */
data class MtzReference(val uiVersion: String?, val modules: Map<String, ByteArray>)

data class MtzInput(
    val meta: MtzMeta,
    val homeWallpaperJpg: ByteArray? = null,
    val lockWallpaperJpg: ByteArray? = null,
    /** Icon entry name (package or activity class, without extension) → 168×168 PNG. */
    val icons: Map<String, ByteArray> = emptyMap(),
    /** Experimental: icon_background / icon_mask / icon_border → PNG. */
    val iconParts: Map<String, ByteArray> = emptyMap(),
    val lockscreen: MamlPackage? = null,
    /** preview file name (e.g. preview_lockscreen_0.jpg) → JPEG. */
    val previews: Map<String, ByteArray> = emptyMap(),
    /** Experimental MAML wallpaper manifest for miwallpaper/. */
    val miWallpaperXml: String? = null,
    val reference: MtzReference? = null,
    /** module → (existing colour name → "#AARRGGBB"). Applied only to keys present in [reference]. */
    val colorOverrides: Map<String, Map<String, String>> = emptyMap(),
)

data class MtzBuildReport(
    val entries: List<String>,
    val appliedOverrides: Map<String, List<String>>,
    val ignoredOverrides: Map<String, List<String>>,
    val sizeBytes: Long,
)

/**
 * Builds an unofficial HyperOS/MIUI theme package (.mtz). The layout follows Xiaomi's theme
 * designer documentation: description.xml, wallpaper/, preview/ and module entries (icons,
 * lockscreen, miwallpaper, …) stored as nested ZIP files without an extension.
 */
class MtzBuilder {

    fun descriptionXml(m: MtzMeta): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<MIUI-Theme>\n")
        append("  <title>${esc(m.title)}</title>\n")
        append("  <designer>${esc(m.designer)}</designer>\n")
        append("  <author>${esc(m.author)}</author>\n")
        append("  <version>${esc(m.version)}</version>\n")
        append("  <uiVersion>${esc(m.uiVersion)}</uiVersion>\n")
        append("  <description>${esc(m.description)}</description>\n")
        append("</MIUI-Theme>\n")
    }

    fun build(input: MtzInput, out: OutputStream): MtzBuildReport {
        val entries = mutableListOf<String>()
        val applied = LinkedHashMap<String, List<String>>()
        val ignored = LinkedHashMap<String, List<String>>()
        val meta = input.reference?.uiVersion?.takeIf { it.isNotBlank() }?.let { input.meta.copy(uiVersion = it) } ?: input.meta
        val counting = CountingOut(out)
        ZipOutputStream(counting).use { zip ->
            fun putEntry(name: String, bytes: ByteArray) {
                zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry(); entries += name
            }
            putEntry("description.xml", descriptionXml(meta).toByteArray(Charsets.UTF_8))
            input.homeWallpaperJpg?.let { putEntry("wallpaper/default_wallpaper.jpg", it) }
            input.lockWallpaperJpg?.let { putEntry("wallpaper/default_lock_wallpaper.jpg", it) }
            for ((name, bytes) in input.previews) putEntry("preview/$name", bytes)

            if (input.icons.isNotEmpty() || input.iconParts.isNotEmpty()) {
                putEntry("icons", nestedZip(buildMap {
                    for ((n, b) in input.icons) put("res/drawable-xxhdpi/${sanitize(n)}.png", b)
                    for ((n, b) in input.iconParts) put("res/drawable-xxhdpi/$n.png", b)
                }))
            }
            input.lockscreen?.let { l ->
                putEntry("lockscreen", nestedZip(buildMap {
                    put("advance/manifest.xml", l.manifestXml.toByteArray(Charsets.UTF_8))
                    for ((n, b) in l.assets) put("advance/$n", b)
                }))
            }
            input.miWallpaperXml?.let { putEntry("miwallpaper", nestedZip(mapOf("manifest.xml" to it.toByteArray(Charsets.UTF_8)))) }

            // Colour overrides: rewrite theme_values.xml inside reference modules, only for keys that exist there.
            val ref = input.reference
            for ((module, overrides) in input.colorOverrides) {
                if (overrides.isEmpty()) continue
                val moduleBytes = ref?.modules?.get(module)
                if (moduleBytes == null) { ignored[module] = overrides.keys.toList(); continue }
                val (rewritten, done) = ThemeValues.rewriteModule(moduleBytes, overrides)
                applied[module] = done
                val skipped = overrides.keys - done.toSet()
                if (skipped.isNotEmpty()) ignored[module] = skipped.toList()
                if (done.isNotEmpty()) putEntry(module, rewritten)
            }
        }
        return MtzBuildReport(entries, applied, ignored, counting.count)
    }

    private fun sanitize(n: String) = n.filter { it.isLetterOrDigit() || it == '.' || it == '_' || it == '$' }

    private class CountingOut(private val o: OutputStream) : OutputStream() {
        var count = 0L
        override fun write(b: Int) { o.write(b); count++ }
        override fun write(b: ByteArray, off: Int, len: Int) { o.write(b, off, len); count += len }
        override fun flush() = o.flush()
        override fun close() = o.close()
    }

    companion object {
        fun nestedZip(files: Map<String, ByteArray>): ByteArray {
            val b = ByteArrayOutputStream()
            ZipOutputStream(b).use { z -> for ((n, d) in files) { z.putNextEntry(ZipEntry(n)); z.write(d); z.closeEntry() } }
            return b.toByteArray()
        }
    }
}

/** Reads and rewrites theme_values.xml (<MIUI_Theme_Values><color name="…">#…</color>…). */
object ThemeValues {
    private fun colorRegex(name: String) =
        Regex("""(<color\s+name\s*=\s*"${Regex.escape(name)}"\s*>)([^<]*)(</color>)""")

    /** Colour keys and values in a theme_values.xml document (parsed securely). */
    fun colors(xml: ByteArray): Map<String, String> {
        val doc = MtzXml.parseSecure(xml)
        val nodes = doc.getElementsByTagName("color")
        val out = LinkedHashMap<String, String>()
        for (i in 0 until nodes.length) {
            val el = nodes.item(i) as? org.w3c.dom.Element ?: continue
            val name = el.getAttribute("name")
            if (name.isNotBlank()) out[name] = el.textContent.trim()
        }
        return out
    }

    /** Rewrites values of existing keys only; everything else stays byte-identical. Returns (xml, applied keys). */
    fun rewrite(xml: ByteArray, overrides: Map<String, String>): Pair<ByteArray, List<String>> {
        val existing = colors(xml)
        var text = xml.toString(Charsets.UTF_8)
        val applied = mutableListOf<String>()
        for ((key, value) in overrides) {
            if (key !in existing) continue
            val color = com.wdesign.core.model.ArgbColor.parse(value) ?: continue
            val re = colorRegex(key)
            if (re.containsMatchIn(text)) {
                text = re.replace(text) { m -> m.groupValues[1] + color.toHex().lowercase() + m.groupValues[3] }
                applied += key
            }
        }
        return text.toByteArray(Charsets.UTF_8) to applied
    }

    /** Copies a module ZIP, rewriting every theme_values.xml inside it. */
    fun rewriteModule(module: ByteArray, overrides: Map<String, String>): Pair<ByteArray, List<String>> {
        val files = LinkedHashMap<String, ByteArray>()
        val applied = LinkedHashSet<String>()
        com.wdesign.core.format.SafeZipReader(com.wdesign.core.format.ZipLimits.Mtz).walk(ByteArrayInputStream(module), allowAnyName = true) { name, bytes ->
            if (name.substringAfterLast('/') == "theme_values.xml" && !FileMagic.isZip(bytes)) {
                val (x, a) = runCatching { rewrite(bytes, overrides) }.getOrElse { bytes to emptyList() }
                files[name] = x; applied += a
            } else files[name] = bytes
        }
        return MtzBuilder.nestedZip(files) to applied.toList()
    }
}
