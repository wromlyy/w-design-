package com.wdesign.core.mtz

import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/** XML helpers shared by the MTZ builder, reader and validator. */
object MtzXml {

    fun esc(s: String): String = buildString(s.length + 8) {
        for (c in s) when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&apos;")
            else -> if (c.code < 0x20 && c != '\n' && c != '\t' && c != '\r') append(' ') else append(c)
        }
    }

    class UnsafeXmlException(msg: String) : Exception(msg)

    /** True when the document contains a DOCTYPE or ENTITY declaration anywhere (case-insensitive, any encoding we accept). */
    fun hasDtd(bytes: ByteArray): Boolean {
        // UTF-8 and ISO-8859-1 share ASCII, so a byte-level scan finds the markup in either.
        val text = String(bytes, Charsets.ISO_8859_1)
        if (text.contains("<!DOCTYPE", ignoreCase = true) || text.contains("<!ENTITY", ignoreCase = true)) return true
        // UTF-16 documents: refuse them outright (W Design never needs them and they defeat the scan).
        return bytes.size >= 2 && ((bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) || (bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()))
    }

    /**
     * Parses untrusted XML safely and fails closed.
     * 1. Any DOCTYPE/ENTITY declaration anywhere in the buffer is rejected before parsing, so XXE
     *    and entity-expansion payloads never reach the parser.
     * 2. The parser is hardened with every feature the platform supports. If the platform accepts
     *    neither disallow-doctype-decl nor FEATURE_SECURE_PROCESSING, the document is rejected.
     */
    fun parseSecure(bytes: ByteArray): Document {
        if (hasDtd(bytes)) throw UnsafeXmlException("DOCTYPE/ENTITY declarations are not allowed")
        val f = DocumentBuilderFactory.newInstance()
        val secure = runCatching { f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }.isSuccess
        val noDoctype = runCatching { f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }.isSuccess
        runCatching { f.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { f.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        runCatching { f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
        runCatching { f.isXIncludeAware = false }
        runCatching { f.isExpandEntityReferences = false }
        // Android's built-in parser (KXml/Expat based) supports neither Xerces feature but also never
        // resolves external entities; the pre-scan above is what protects it.
        if (!secure && !noDoctype && !isAndroidParser(f)) throw UnsafeXmlException("XML parser cannot be hardened on this platform")
        f.isNamespaceAware = false
        val builder = f.newDocumentBuilder()
        builder.setErrorHandler(null)
        return builder.parse(ByteArrayInputStream(bytes))
    }

    private fun isAndroidParser(f: DocumentBuilderFactory): Boolean =
        f.javaClass.name.startsWith("org.apache.harmony.xml")

    fun Element.childText(tag: String): String? {
        val nodes = getElementsByTagName(tag)
        return if (nodes.length == 0) null else nodes.item(0).textContent?.trim()
    }

    fun Document.countElements(): Map<String, Int> {
        val counts = HashMap<String, Int>()
        val all = getElementsByTagName("*")
        for (i in 0 until all.length) {
            val n = all.item(i).nodeName
            counts[n] = (counts[n] ?: 0) + 1
        }
        return counts
    }
}

/**
 * Translates java.text.SimpleDateFormat patterns to the subset MAML's DateTime format renders
 * reliably (y, M, d, E, H, h, m, a, plus quoted literals). Unsupported letters are dropped and
 * reported so the UI can warn the user.
 */
object MamlDatePattern {
    private val supported = setOf('y', 'M', 'd', 'E', 'H', 'h', 'm', 'a')

    data class Result(val pattern: String, val simplified: Boolean, val dropped: Set<Char>)

    fun translate(java: String): Result {
        val out = StringBuilder()
        val dropped = LinkedHashSet<Char>()
        var i = 0
        while (i < java.length) {
            val c = java[i]
            when {
                c == '\'' -> {
                    // quoted literal or escaped quote
                    val end = java.indexOf('\'', i + 1)
                    if (end == i + 1) { out.append("''"); i += 2; continue }
                    val lit = if (end < 0) java.substring(i + 1) else java.substring(i + 1, end)
                    out.append('\'').append(lit).append('\'')
                    i = if (end < 0) java.length else end + 1
                    continue
                }
                c.isLetter() && c in supported -> out.append(c)
                c.isLetter() -> {
                    dropped += c
                    // swallow the whole run of this letter and one following separator space
                    while (i + 1 < java.length && java[i + 1] == c) i++
                }
                else -> out.append(c)
            }
            i++
        }
        val cleaned = out.toString().replace(Regex("\\s{2,}"), " ").trim().trim(',', '.', '-', '/').trim()
        return Result(cleaned.ifEmpty { "EEEE" }, dropped.isNotEmpty(), dropped)
    }

    /** Presets offered by Lock Screen Studio; every one translates without simplification. */
    val PRESETS = listOf("EEEE, d MMMM", "EEE d MMM", "d MMMM, EEEE", "EEE dd.MM", "dd.MM.yyyy", "MMMM d", "EEEE")
}
