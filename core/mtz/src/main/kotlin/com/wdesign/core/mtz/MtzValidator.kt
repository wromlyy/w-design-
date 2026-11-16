package com.wdesign.core.mtz

import com.wdesign.core.format.ArchiveRejected
import com.wdesign.core.format.FileMagic
import com.wdesign.core.format.SafeZipReader
import com.wdesign.core.format.ZipLimits
import com.wdesign.core.mtz.MtzXml.childText
import java.io.ByteArrayInputStream

enum class CheckItem {
    ARCHIVE_READABLE, DESCRIPTION_WELL_FORMED, TITLE_PRESENT, HOME_WALLPAPER_SIZE, LOCK_WALLPAPER_SIZE, ICON_SIZES,
    LOCKSCREEN_WELL_FORMED, LOCK_HAS_TIME, LOCK_HAS_DATE, LOCK_HAS_UNLOCK, LOCK_ASSETS_PRESENT, NO_PASSWORD_MODULE,
    TOTAL_SIZE, OVERRIDES_EXISTING_KEYS, NESTED_MODULES_VALID,
}

enum class CheckStatus { PASS, WARN, FAIL, SKIP }

data class CheckResult(val item: CheckItem, val status: CheckStatus, val detail: String = "")

data class ValidationReport(val results: List<CheckResult>) {
    val passed: Boolean get() = results.none { it.status == CheckStatus.FAIL }
    fun status(item: CheckItem) = results.firstOrNull { it.item == item }?.status ?: CheckStatus.SKIP
}

/**
 * Re-opens a built package and checks it against the rules W Design can verify offline
 * (derived from Xiaomi's theme audit guidance). Passing does not mean Xiaomi accepted the theme.
 */
object MtzValidator {
    const val MAX_BYTES = 80L * 1024 * 1024
    const val MIN_WALLPAPER_HEIGHT = 2400
    const val MAX_ICON = 224

    fun validate(bytes: ByteArray, referenceKeys: Map<String, Set<String>> = emptyMap(), overrides: Map<String, Map<String, String>> = emptyMap()): ValidationReport {
        val r = mutableListOf<CheckResult>()
        val top = LinkedHashMap<String, ByteArray>()
        try {
            SafeZipReader(ZipLimits.Mtz).walk(ByteArrayInputStream(bytes), allowAnyName = true) { n, b -> top[n] = b }
            r += CheckResult(CheckItem.ARCHIVE_READABLE, CheckStatus.PASS, "${top.size} entries")
        } catch (ex: ArchiveRejected) {
            r += CheckResult(CheckItem.ARCHIVE_READABLE, CheckStatus.FAIL, ex.issue.code.name)
            return ValidationReport(r)
        }

        r += if (bytes.size <= MAX_BYTES) CheckResult(CheckItem.TOTAL_SIZE, CheckStatus.PASS, "${bytes.size / 1024} KB")
        else CheckResult(CheckItem.TOTAL_SIZE, CheckStatus.FAIL, "${bytes.size / 1048576} MB > 80 MB")

        // description.xml
        val desc = top["description.xml"]
        if (desc == null) {
            r += CheckResult(CheckItem.DESCRIPTION_WELL_FORMED, CheckStatus.FAIL, "missing")
        } else {
            val root = runCatching { MtzXml.parseSecure(desc).documentElement }.getOrNull()
            if (root == null || root.nodeName != "MIUI-Theme") r += CheckResult(CheckItem.DESCRIPTION_WELL_FORMED, CheckStatus.FAIL, "not <MIUI-Theme>")
            else {
                r += CheckResult(CheckItem.DESCRIPTION_WELL_FORMED, CheckStatus.PASS)
                val title = root.childText("title").orEmpty()
                r += if (title.isNotBlank()) CheckResult(CheckItem.TITLE_PRESENT, CheckStatus.PASS, title) else CheckResult(CheckItem.TITLE_PRESENT, CheckStatus.FAIL)
            }
        }

        fun wallpaperCheck(item: CheckItem, name: String) {
            val b = top[name]
            r += if (b == null) CheckResult(item, CheckStatus.SKIP, "not included")
            else {
                val size = FileMagic.imageSize(b)
                when {
                    size == null -> CheckResult(item, CheckStatus.FAIL, "unreadable image")
                    size.second >= MIN_WALLPAPER_HEIGHT -> CheckResult(item, CheckStatus.PASS, "${size.first}×${size.second}")
                    else -> CheckResult(item, CheckStatus.WARN, "${size.first}×${size.second} < $MIN_WALLPAPER_HEIGHT px tall")
                }
            }
        }
        wallpaperCheck(CheckItem.HOME_WALLPAPER_SIZE, "wallpaper/default_wallpaper.jpg")
        wallpaperCheck(CheckItem.LOCK_WALLPAPER_SIZE, "wallpaper/default_lock_wallpaper.jpg")

        val nestedProblems = mutableListOf<String>()
        fun nested(name: String): Map<String, ByteArray>? {
            val b = top[name] ?: return null
            if (!FileMagic.isZip(b)) { nestedProblems += name; return null }
            val m = LinkedHashMap<String, ByteArray>()
            return try { SafeZipReader(ZipLimits.Mtz).walk(ByteArrayInputStream(b), allowAnyName = true) { n, d -> m[n] = d }; m } catch (ex: ArchiveRejected) { nestedProblems += name; null }
        }

        // icons
        val icons = nested("icons")
        r += if (icons == null) CheckResult(CheckItem.ICON_SIZES, CheckStatus.SKIP, "not included") else {
            val bad = icons.filter { (n, b) -> n.endsWith(".png") && (FileMagic.imageSize(b)?.let { it.first > MAX_ICON || it.second > MAX_ICON } ?: true) }
            if (bad.isEmpty()) CheckResult(CheckItem.ICON_SIZES, CheckStatus.PASS, "${icons.size} icons ≤ $MAX_ICON px")
            else CheckResult(CheckItem.ICON_SIZES, CheckStatus.FAIL, bad.keys.take(3).joinToString())
        }

        // lockscreen
        val lock = nested("lockscreen")
        if (lock == null) {
            for (i in listOf(CheckItem.LOCKSCREEN_WELL_FORMED, CheckItem.LOCK_HAS_TIME, CheckItem.LOCK_HAS_DATE, CheckItem.LOCK_HAS_UNLOCK, CheckItem.LOCK_ASSETS_PRESENT))
                r += CheckResult(i, CheckStatus.SKIP, "not included")
            r += CheckResult(CheckItem.NO_PASSWORD_MODULE, CheckStatus.PASS)
        } else {
            val manifest = lock["advance/manifest.xml"]
            val doc = manifest?.let { runCatching { MtzXml.parseSecure(it) }.getOrNull() }
            if (doc == null || doc.documentElement.nodeName != "Lockscreen") {
                r += CheckResult(CheckItem.LOCKSCREEN_WELL_FORMED, CheckStatus.FAIL, if (manifest == null) "advance/manifest.xml missing" else "malformed")
            } else {
                r += CheckResult(CheckItem.LOCKSCREEN_WELL_FORMED, CheckStatus.PASS)
                val dts = doc.getElementsByTagName("DateTime")
                var hasTime = doc.getElementsByTagName("Time").length > 0
                var hasDate = false
                for (i in 0 until dts.length) {
                    val el = dts.item(i) as org.w3c.dom.Element
                    val fmt = el.getAttribute("format") + el.getAttribute("formatExp")
                    if (fmt.contains("mm") || fmt.contains("HH") || fmt.contains("hh")) hasTime = true
                    if (fmt.contains("d") || fmt.contains("E") || fmt.contains("M")) hasDate = true
                }
                r += CheckResult(CheckItem.LOCK_HAS_TIME, if (hasTime) CheckStatus.PASS else CheckStatus.FAIL)
                r += CheckResult(CheckItem.LOCK_HAS_DATE, if (hasDate) CheckStatus.PASS else CheckStatus.WARN)
                val cmds = doc.getElementsByTagName("ExternCommand")
                var unlock = doc.getElementsByTagName("Unlocker").length > 0
                for (i in 0 until cmds.length) if ((cmds.item(i) as org.w3c.dom.Element).getAttribute("command") == "unlock") unlock = true
                r += CheckResult(CheckItem.LOCK_HAS_UNLOCK, if (unlock) CheckStatus.PASS else CheckStatus.FAIL)
                // Every src referenced must exist (Time src="x.png" expands to x_0..9 + x_dot).
                val missing = mutableListOf<String>()
                val all = doc.getElementsByTagName("*")
                for (i in 0 until all.length) {
                    val el = all.item(i) as org.w3c.dom.Element
                    val src = el.getAttribute("src")
                    if (src.isBlank() || src.contains('#') || src.contains('@')) continue
                    if (el.nodeName == "Time" || el.nodeName == "ImageNumber") {
                        val base = src.substringBeforeLast('.')
                        for (d in (0..9).map { "${base}_$it.png" } + (if (el.nodeName == "Time") listOf("${base}_dot.png") else emptyList())) if ("advance/$d" !in lock) missing += d
                    } else if ("advance/$src" !in lock) missing += src
                }
                r += if (missing.isEmpty()) CheckResult(CheckItem.LOCK_ASSETS_PRESENT, CheckStatus.PASS)
                else CheckResult(CheckItem.LOCK_ASSETS_PRESENT, CheckStatus.FAIL, missing.take(3).joinToString())
            }
            val password = lock.keys.any { it.contains("password", true) || it.contains("pin_", true) }
            r += CheckResult(CheckItem.NO_PASSWORD_MODULE, if (password) CheckStatus.FAIL else CheckStatus.PASS)
        }

        // Overrides must target keys from the reference.
        if (overrides.isEmpty()) r += CheckResult(CheckItem.OVERRIDES_EXISTING_KEYS, CheckStatus.SKIP, "no colour overrides")
        else {
            val invented = overrides.flatMap { (m, kv) -> kv.keys.filter { it !in referenceKeys[m].orEmpty() }.map { "$m/$it" } }
            r += if (invented.isEmpty()) CheckResult(CheckItem.OVERRIDES_EXISTING_KEYS, CheckStatus.PASS)
            else CheckResult(CheckItem.OVERRIDES_EXISTING_KEYS, CheckStatus.WARN, "ignored: " + invented.take(3).joinToString())
        }
        for (m in listOf("miwallpaper")) nested(m)
        r += if (nestedProblems.isEmpty()) CheckResult(CheckItem.NESTED_MODULES_VALID, CheckStatus.PASS) else CheckResult(CheckItem.NESTED_MODULES_VALID, CheckStatus.FAIL, nestedProblems.joinToString())
        return ValidationReport(r)
    }
}
