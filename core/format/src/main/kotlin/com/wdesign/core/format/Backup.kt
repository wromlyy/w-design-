package com.wdesign.core.format

import com.wdesign.core.model.DesignProject
import com.wdesign.core.model.WDJson
import com.wdesign.core.model.WDJsonPretty
import kotlinx.serialization.Serializable
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@Serializable
data class BackupManifest(
    val format: String = "wdbackup",
    val version: Int = 1,
    val createdAt: Long = 0L,
    val projects: List<String> = emptyList(),
)

/** One project to put in a backup: the project, its asset folder and optional preview. */
data class BackupItem(val project: DesignProject, val assetsDir: File?, val preview: ByteArray?)

/** .wdbackup = backup.json + projects/<id>.wdesign + settings.json */
class BackupWriter(private val writer: WDesignWriter = WDesignWriter()) {
    fun write(items: List<BackupItem>, settingsJson: String, out: OutputStream, now: Long = System.currentTimeMillis()) {
        ZipOutputStream(out).use { zip ->
            val manifest = BackupManifest(createdAt = now, projects = items.map { it.project.id })
            zip.putNextEntry(ZipEntry("backup.json"))
            zip.write(WDJsonPretty.encodeToString(BackupManifest.serializer(), manifest).toByteArray()); zip.closeEntry()
            for (item in items) {
                val buf = ByteArrayOutputStream()
                writer.write(item.project, item.assetsDir, item.preview, buf)
                zip.putNextEntry(ZipEntry("projects/${safeId(item.project.id)}.wdesign"))
                zip.write(buf.toByteArray()); zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry("settings.json"))
            zip.write(settingsJson.toByteArray()); zip.closeEntry()
        }
    }

    private fun safeId(id: String) = id.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.ifEmpty { "project" }
}

data class BackupContents(
    val manifest: BackupManifest,
    val projects: List<ImportPreview>,
    val settingsJson: String?,
    val warnings: List<ImportIssue>,
)

/** Reads a .wdbackup; every nested project goes through the full [WDesignReader] checks. */
class BackupReader(private val limits: ZipLimits = ZipLimits.Default) {
    fun read(input: InputStream, stagingRoot: File): BackupContents {
        val zip = SafeZipReader(limits.copy(maxEntryBytes = limits.maxTotalBytes))
        var manifest: BackupManifest? = null
        var settings: String? = null
        val nested = LinkedHashMap<String, ByteArray>()
        zip.walk(input) { name, bytes ->
            when {
                name == "backup.json" -> manifest = runCatching { WDJson.decodeFromString(BackupManifest.serializer(), bytes.decodeToString()) }.getOrNull()
                name == "settings.json" && bytes.size < limits.maxJsonBytes -> settings = bytes.decodeToString()
                name.startsWith("projects/") && name.endsWith(".wdesign") && FileMagic.isZip(bytes) -> nested[name] = bytes
                else -> zip.warnings += ImportIssue(IssueCode.SKIPPED_EXTENSION, name)
            }
        }
        val m = manifest?.takeIf { it.format == "wdbackup" }
            ?: throw ArchiveRejected(ImportIssue(IssueCode.NOT_A_DESIGN, "backup.json"))
        if (m.version > 1) throw ArchiveRejected(ImportIssue(IssueCode.FUTURE_VERSION, "${m.version}"))
        val reader = WDesignReader(limits)
        val previews = nested.entries.mapIndexed { i, (_, bytes) ->
            reader.inspect(ByteArrayInputStream(bytes), File(stagingRoot, "b$i"))
        }
        return BackupContents(m, previews, settings, zip.warnings.toList())
    }
}
