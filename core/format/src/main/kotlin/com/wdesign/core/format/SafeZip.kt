package com.wdesign.core.format

import java.io.FilterInputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

/** Hard limits for any archive W Design opens. Bytes are counted as actually read, never trusted from headers. */
data class ZipLimits(
    val maxEntries: Int = 400,
    val maxEntryBytes: Long = 20L * 1024 * 1024,
    val maxTotalBytes: Long = 150L * 1024 * 1024,
    val maxRatio: Int = 100,
    val maxJsonBytes: Long = 2L * 1024 * 1024,
    /** Uncompressed bytes allowed before the ratio check starts (tiny files compress very well). */
    val ratioSlackBytes: Long = 1L * 1024 * 1024,
) {
    companion object {
        val Default = ZipLimits()
        /** The Xiaomi theme audit limits a package to 80 MB. */
        val Mtz = ZipLimits(maxEntries = 4000, maxEntryBytes = 40L * 1024 * 1024, maxTotalBytes = 80L * 1024 * 1024)
    }
}

enum class IssueCode {
    SKIPPED_EXTENSION, SKIPPED_EXECUTABLE, MAGIC_MISMATCH, BAD_NAME, ZIP_SLIP, CHECKSUM_MISMATCH, MISSING_FILE,
    UNLISTED_FILE, TOO_MANY_ENTRIES, ENTRY_TOO_LARGE, TOTAL_TOO_LARGE, RATIO_BOMB, JSON_TOO_LARGE, CORRUPT_ZIP,
    FUTURE_VERSION, NO_MANIFEST, INVALID_JSON, NOT_A_DESIGN, DUPLICATE_ENTRY, NESTED_ARCHIVE, XML_UNSAFE, INVALID_XML,
    CANCELLED,
}

data class ImportIssue(val code: IssueCode, val detail: String = "")

/** Thrown for problems that make the whole archive unusable. */
class ArchiveRejected(val issue: ImportIssue) : Exception("${issue.code}: ${issue.detail}")

/** Counts compressed bytes pulled from the underlying stream (for the ratio check). */
internal class CountingInputStream(input: InputStream) : FilterInputStream(input) {
    var count = 0L
        private set

    override fun read(): Int = super.read().also { if (it >= 0) count++ }
    override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) count += it }
    override fun skip(n: Long): Long = super.skip(n).also { count += it }
}

object FileMagic {
    fun isPng(b: ByteArray) = b.size >= 8 && b[0] == 0x89.toByte() && b[1] == 'P'.code.toByte() && b[2] == 'N'.code.toByte() && b[3] == 'G'.code.toByte()
    fun isJpeg(b: ByteArray) = b.size >= 3 && b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte() && b[2] == 0xFF.toByte()
    fun isWebp(b: ByteArray) = b.size >= 12 && String(b, 0, 4, Charsets.ISO_8859_1) == "RIFF" && String(b, 8, 4, Charsets.ISO_8859_1) == "WEBP"
    fun isZip(b: ByteArray) = b.size >= 4 && b[0] == 'P'.code.toByte() && b[1] == 'K'.code.toByte() && (b[2].toInt() == 3 || b[2].toInt() == 5 || b[2].toInt() == 7)
    fun isDex(b: ByteArray) = b.size >= 4 && String(b, 0, 4, Charsets.ISO_8859_1) == "dex\n"
    fun isElf(b: ByteArray) = b.size >= 4 && b[0] == 0x7F.toByte() && String(b, 1, 3, Charsets.ISO_8859_1) == "ELF"
    fun isScript(b: ByteArray) = b.size >= 2 && b[0] == '#'.code.toByte() && b[1] == '!'.code.toByte()
    fun isClass(b: ByteArray) = b.size >= 4 && b[0] == 0xCA.toByte() && b[1] == 0xFE.toByte() && b[2] == 0xBA.toByte() && b[3] == 0xBE.toByte()

    /** Executable or archive content that must never be accepted, whatever its extension. */
    fun isExecutable(b: ByteArray) = isDex(b) || isElf(b) || isScript(b) || isClass(b)

    fun imageMatches(ext: String, b: ByteArray): Boolean = when (ext) {
        "png" -> isPng(b)
        "jpg", "jpeg" -> isJpeg(b)
        "webp" -> isWebp(b)
        else -> true
    }

    /** Width and height from a PNG/JPEG/WebP header without decoding (pure JVM, works on Android too). */
    fun imageSize(b: ByteArray): Pair<Int, Int>? = runCatching {
        when {
            isPng(b) && b.size >= 24 -> be32(b, 16) to be32(b, 20)
            isJpeg(b) -> jpegSize(b)
            isWebp(b) -> webpSize(b)
            else -> null
        }
    }.getOrNull()

    private fun be32(b: ByteArray, o: Int) = ((b[o].toInt() and 0xFF) shl 24) or ((b[o + 1].toInt() and 0xFF) shl 16) or ((b[o + 2].toInt() and 0xFF) shl 8) or (b[o + 3].toInt() and 0xFF)
    private fun be16(b: ByteArray, o: Int) = ((b[o].toInt() and 0xFF) shl 8) or (b[o + 1].toInt() and 0xFF)
    private fun le16(b: ByteArray, o: Int) = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)
    private fun le24(b: ByteArray, o: Int) = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8) or ((b[o + 2].toInt() and 0xFF) shl 16)

    private fun jpegSize(b: ByteArray): Pair<Int, Int>? {
        var i = 2
        while (i + 9 < b.size) {
            if (b[i] != 0xFF.toByte()) { i++; continue }
            val marker = b[i + 1].toInt() and 0xFF
            if (marker == 0xD8 || marker == 0x01 || marker in 0xD0..0xD7) { i += 2; continue }
            val len = be16(b, i + 2)
            if (marker in 0xC0..0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC) {
                return be16(b, i + 7) to be16(b, i + 5)
            }
            i += 2 + len
        }
        return null
    }

    private fun webpSize(b: ByteArray): Pair<Int, Int>? {
        val chunk = String(b, 12, 4, Charsets.ISO_8859_1)
        return when (chunk) {
            "VP8 " -> (le16(b, 26) and 0x3FFF) to (le16(b, 28) and 0x3FFF)
            "VP8L" -> {
                val bits = (b[21].toInt() and 0xFF) or ((b[22].toInt() and 0xFF) shl 8) or ((b[23].toInt() and 0xFF) shl 16) or ((b[24].toInt() and 0xFF) shl 24)
                ((bits and 0x3FFF) + 1) to (((bits shr 14) and 0x3FFF) + 1)
            }
            "VP8X" -> (le24(b, 24) + 1) to (le24(b, 27) + 1)
            else -> null
        }
    }
}

fun sha256(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

/**
 * Streaming, limit-enforcing ZIP walker. For each acceptable entry it calls [onEntry] with the
 * normalized name and the fully-read bytes. Names are validated against zip-slip and an allow-list
 * pattern. Limit violations throw [ArchiveRejected]; per-entry problems are reported as warnings.
 */
class SafeZipReader(private val limits: ZipLimits = ZipLimits.Default) {
    private val namePattern = Regex("""[A-Za-z0-9._/-]{1,160}""")

    val warnings = mutableListOf<ImportIssue>()
    var entries = 0
        private set
    var totalBytes = 0L
        private set

    /** Validates a name. Returns null when the entry must be skipped (the reason goes to [warnings]). */
    fun normalizeName(raw: String, allowAnyName: Boolean = false): String? {
        val name = raw.replace('\\', '/')
        val segments = name.split('/')
        if (name.startsWith("/") || segments.any { it == ".." } || name.contains(':') || raw.contains('\\')) {
            warnings += ImportIssue(IssueCode.ZIP_SLIP, raw.take(80)); return null
        }
        if (!allowAnyName && !namePattern.matches(name)) {
            warnings += ImportIssue(IssueCode.BAD_NAME, raw.take(80)); return null
        }
        if (name.length > 255) { warnings += ImportIssue(IssueCode.BAD_NAME, raw.take(80)); return null }
        return name.trimStart('.', '/').ifEmpty { null }?.let { name }
    }

    /**
     * @param cancelled polled between reads; returning true aborts with [IssueCode.CANCELLED].
     * @param progress called with total uncompressed bytes so far.
     */
    fun walk(
        input: InputStream,
        allowAnyName: Boolean = false,
        cancelled: () -> Boolean = { false },
        progress: (Long) -> Unit = {},
        onEntry: (name: String, bytes: ByteArray) -> Unit,
    ) {
        val counting = CountingInputStream(input)
        val seen = HashSet<String>()
        try {
            ZipInputStream(counting).use { zip ->
                while (true) {
                    val e = try { zip.nextEntry } catch (ex: ZipException) {
                        throw ArchiveRejected(ImportIssue(IssueCode.CORRUPT_ZIP, ex.message.orEmpty()))
                    } catch (ex: java.io.EOFException) {
                        throw ArchiveRejected(ImportIssue(IssueCode.CORRUPT_ZIP, "truncated"))
                    } ?: break
                    if (cancelled()) throw ArchiveRejected(ImportIssue(IssueCode.CANCELLED))
                    entries++
                    if (entries > limits.maxEntries) throw ArchiveRejected(ImportIssue(IssueCode.TOO_MANY_ENTRIES, "> ${limits.maxEntries}"))
                    if (e.isDirectory) continue
                    val name = normalizeName(e.name, allowAnyName)
                    val bytes = readEntry(zip, counting, e.name, cancelled, progress)
                    if (name == null) continue
                    if (!seen.add(name)) { warnings += ImportIssue(IssueCode.DUPLICATE_ENTRY, name); continue }
                    onEntry(name, bytes)
                }
            }
        } catch (ex: ArchiveRejected) {
            throw ex
        } catch (ex: ZipException) {
            throw ArchiveRejected(ImportIssue(IssueCode.CORRUPT_ZIP, ex.message.orEmpty()))
        } catch (ex: java.io.EOFException) {
            throw ArchiveRejected(ImportIssue(IssueCode.CORRUPT_ZIP, "truncated"))
        }
        if (entries == 0) throw ArchiveRejected(ImportIssue(IssueCode.CORRUPT_ZIP, "no entries"))
    }

    private fun readEntry(zip: ZipInputStream, counting: CountingInputStream, name: String, cancelled: () -> Boolean, progress: (Long) -> Unit): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        var entryBytes = 0L
        while (true) {
            val n = try { zip.read(buf) } catch (ex: java.io.EOFException) {
                throw ArchiveRejected(ImportIssue(IssueCode.CORRUPT_ZIP, "truncated in $name"))
            }
            if (n < 0) break
            entryBytes += n; totalBytes += n
            if (entryBytes > limits.maxEntryBytes) throw ArchiveRejected(ImportIssue(IssueCode.ENTRY_TOO_LARGE, name.take(80)))
            if (totalBytes > limits.maxTotalBytes) throw ArchiveRejected(ImportIssue(IssueCode.TOTAL_TOO_LARGE, "> ${limits.maxTotalBytes / 1048576} MB"))
            if (totalBytes > limits.ratioSlackBytes && totalBytes > limits.maxRatio.toLong() * counting.count.coerceAtLeast(1)) {
                throw ArchiveRejected(ImportIssue(IssueCode.RATIO_BOMB, name.take(80)))
            }
            out.write(buf, 0, n)
            if (cancelled()) throw ArchiveRejected(ImportIssue(IssueCode.CANCELLED))
        }
        progress(totalBytes)
        return out.toByteArray()
    }
}

/**
 * Reads up to [n] bytes from the start of a stream. Portable replacement for InputStream.readNBytes(int),
 * which Android only provides from API 33 (minSdk here is 26).
 */
fun java.io.InputStream.readHead(n: Int): ByteArray {
    val buf = ByteArray(n)
    var off = 0
    while (off < n) {
        val r = read(buf, off, n - off)
        if (r < 0) break
        off += r
    }
    return if (off == n) buf else buf.copyOf(off)
}
