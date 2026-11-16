package com.wdesign.core.format

import com.wdesign.core.model.AssetRef
import com.wdesign.core.model.BuiltInPresets
import com.wdesign.core.model.DesignKind
import com.wdesign.core.model.DesignProject
import com.wdesign.core.model.WallpaperSpec
import com.wdesign.core.model.WallpaperType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class FormatTest {
    private val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0x0D, 0x0A, 0x1A, 0x0A,
        0, 0, 0, 13, 'I'.code.toByte(), 'H'.code.toByte(), 'D'.code.toByte(), 'R'.code.toByte(), 0, 0, 0, 4, 0, 0, 0, 3, 8, 6, 0, 0, 0)
    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0, 16, 'J'.code.toByte(), 'F'.code.toByte(), 1, 2, 3)

    private fun tmp(): File = Files.createTempDirectory("wd").toFile()

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val b = ByteArrayOutputStream()
        ZipOutputStream(b).use { z -> for ((n, d) in entries) { z.putNextEntry(ZipEntry(n)); z.write(d); z.closeEntry() } }
        return b.toByteArray()
    }

    private fun sampleProject(assets: File): DesignProject {
        File(assets, "wallpapers").mkdirs()
        File(assets, "wallpapers/home.jpg").writeBytes(jpeg)
        val preset = BuiltInPresets.all.first()
        val spec = preset.spec.copy(wallpaper = WallpaperSpec(type = WallpaperType.PHOTO, photo = AssetRef("wallpapers/home.jpg")))
        return DesignProject("p1", "Round trip", DesignKind.THEME, spec)
    }

    private fun export(project: DesignProject, assets: File?, kind: DesignKind = project.kind): ByteArray =
        ByteArrayOutputStream().also { WDesignWriter().write(project, assets, png, it, kind) }.toByteArray()

    @Test fun roundTrip() {
        val assets = tmp()
        val p = sampleProject(assets)
        val bytes = export(p, assets)
        val preview = WDesignReader().inspect(ByteArrayInputStream(bytes), tmp())
        assertTrue(preview.errors.toString(), preview.ok)
        assertEquals(p.spec, preview.project!!.spec)
        assertEquals("Round trip", preview.project!!.name)
        assertTrue(preview.assets.contains("wallpapers/home.jpg"))
        assertTrue(File(preview.stagedDir, "assets/wallpapers/home.jpg").readBytes().contentEquals(jpeg))
        assertNotNull(preview.previewPng)
        assertTrue(preview.warnings.toString(), preview.warnings.isEmpty())
    }

    @Test fun componentExportOnlyContainsItsParts() {
        val assets = tmp()
        val p = sampleProject(assets)
        val preview = WDesignReader().inspect(ByteArrayInputStream(export(p, assets, DesignKind.WIDGET)), tmp())
        assertTrue(preview.ok)
        assertEquals(DesignKind.WIDGET, preview.project!!.kind)
        assertTrue(preview.assets.isEmpty())
        assertEquals(p.spec.widgets, preview.project!!.spec.widgets)
        assertFalse(preview.manifest!!.components.contains(Component.ICONS))
    }

    @Test fun zipSlipIsRejected() {
        val assets = tmp()
        val good = export(sampleProject(assets), assets)
        // Re-pack with evil entries added.
        val entries = mutableListOf<Pair<String, ByteArray>>()
        java.util.zip.ZipInputStream(ByteArrayInputStream(good)).use { z ->
            while (true) { val e = z.nextEntry ?: break; entries += e.name to z.readBytes() }
        }
        entries += "../../evil.png" to png
        entries += "assets/../../../evil2.png" to png
        entries += "/abs.png" to png
        val staging = tmp()
        val preview = WDesignReader().inspect(ByteArrayInputStream(zipOf(*entries.toTypedArray())), staging)
        assertTrue(preview.ok)
        assertEquals(3, preview.warnings.count { it.code == IssueCode.ZIP_SLIP })
        assertFalse(File(staging.parentFile, "evil.png").exists())
        assertFalse(File(staging.parentFile?.parentFile, "evil.png").exists())
    }

    @Test fun executablesAreSkippedEvenWhenRenamed() {
        val assets = tmp()
        val good = export(sampleProject(assets), assets)
        val entries = mutableListOf<Pair<String, ByteArray>>()
        java.util.zip.ZipInputStream(ByteArrayInputStream(good)).use { z -> while (true) { val e = z.nextEntry ?: break; entries += e.name to z.readBytes() } }
        entries += "assets/images/dex.png" to "dex\n035\u0000aaaa".toByteArray()
        entries += "assets/images/elf.jpg" to byteArrayOf(0x7F, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte(), 2, 1)
        entries += "classes.dex" to "dex\n035".toByteArray()
        entries += "run.sh" to "#!/bin/sh".toByteArray()
        entries += "fake.png" to jpeg
        val preview = WDesignReader().inspect(ByteArrayInputStream(zipOf(*entries.toTypedArray())), tmp())
        assertTrue(preview.ok)
        assertEquals(4, preview.warnings.count { it.code == IssueCode.SKIPPED_EXECUTABLE })
        assertEquals(1, preview.warnings.count { it.code == IssueCode.MAGIC_MISMATCH })
        assertFalse(preview.assets.any { it.contains("dex") || it.contains("elf") })
    }

    @Test fun futureVersionIsIncompatible() {
        val manifest = """{"format":"wdesign","formatVersion":7,"minReaderVersion":5,"files":{}}"""
        val preview = WDesignReader().inspect(ByteArrayInputStream(zipOf("manifest.json" to manifest.toByteArray(), "theme.json" to "{}".toByteArray())), tmp())
        assertFalse(preview.ok)
        assertEquals(IssueCode.FUTURE_VERSION, preview.errors.first().code)
    }

    @Test fun checksumMismatch() {
        val assets = tmp()
        val good = export(sampleProject(assets), assets)
        val entries = mutableListOf<Pair<String, ByteArray>>()
        java.util.zip.ZipInputStream(ByteArrayInputStream(good)).use { z -> while (true) { val e = z.nextEntry ?: break; entries += e.name to z.readBytes() } }
        val tampered = entries.map { (n, d) -> if (n == "theme.json") n to (d.decodeToString().replace("Round", "Evil")).toByteArray() + " ".toByteArray() else n to d }
        val preview = WDesignReader().inspect(ByteArrayInputStream(zipOf(*tampered.toTypedArray())), tmp())
        assertFalse(preview.ok)
        assertEquals(IssueCode.CHECKSUM_MISMATCH, preview.errors.first().code)
        // Tampered asset only produces a warning and the reference is cleared.
        val tampered2 = entries.map { (n, d) -> if (n.endsWith("home.jpg")) n to (d + byteArrayOf(1)) else n to d }
        val p2 = WDesignReader().inspect(ByteArrayInputStream(zipOf(*tampered2.toTypedArray())), tmp())
        assertTrue(p2.ok)
        assertTrue(p2.warnings.any { it.code == IssueCode.CHECKSUM_MISMATCH })
        assertNull(p2.project!!.spec.wallpaper.photo)
    }

    @Test fun truncatedZipGivesTypedError() {
        val assets = tmp()
        val good = export(sampleProject(assets), assets)
        val cut = good.copyOf(good.size / 2)
        val preview = WDesignReader().inspect(ByteArrayInputStream(cut), tmp())
        assertFalse(preview.ok)
        assertEquals(IssueCode.CORRUPT_ZIP, preview.errors.first().code)
        val garbage = WDesignReader().inspect(ByteArrayInputStream(ByteArray(1000) { 7 }), tmp())
        assertEquals(IssueCode.CORRUPT_ZIP, garbage.errors.first().code)
    }

    @Test fun ratioBombIsRejected() {
        val bomb = zipOf("manifest.json" to "{}".toByteArray(), "assets/images/a.txt" to ByteArray(30 * 1024 * 1024))
        val preview = WDesignReader().inspect(ByteArrayInputStream(bomb), tmp())
        assertFalse(preview.ok)
        assertTrue(preview.errors.first().code in setOf(IssueCode.RATIO_BOMB, IssueCode.ENTRY_TOO_LARGE))
        val small = WDesignReader(ZipLimits(maxEntryBytes = 100L * 1024 * 1024)).inspect(ByteArrayInputStream(bomb), tmp())
        assertEquals(IssueCode.RATIO_BOMB, small.errors.first().code)
    }

    @Test fun tooManyEntries() {
        val entries = (0 until 450).map { "f$it.txt" to "x".toByteArray() }.toTypedArray()
        val preview = WDesignReader().inspect(ByteArrayInputStream(zipOf(*entries)), tmp())
        assertEquals(IssueCode.TOO_MANY_ENTRIES, preview.errors.first().code)
    }

    @Test fun genericImageZip() {
        val zip = zipOf("wallpaper_home.jpg" to jpeg, "lockscreen.png" to png, "icons/com.android.chrome.png" to png, "icons/bad name!.png" to png, "readme.txt" to "hi".toByteArray())
        val preview = WDesignReader().inspect(ByteArrayInputStream(zip), tmp())
        assertTrue(preview.errors.toString(), preview.ok)
        assertTrue(preview.generic)
        val spec = preview.project!!.spec
        assertEquals(WallpaperType.PHOTO, spec.wallpaper.type)
        assertNotNull(spec.lockScreen.wallpaper?.photo)
        assertEquals("com.android.chrome", spec.icons.overrides.single().packageName)
        assertTrue(preview.warnings.any { it.code == IssueCode.BAD_NAME })
    }

    @Test fun backupRoundTrip() {
        val assets = tmp()
        val p = sampleProject(assets)
        val p2 = p.copy(id = "p2", name = "Second")
        val out = ByteArrayOutputStream()
        BackupWriter().write(listOf(BackupItem(p, assets, png), BackupItem(p2, assets, null)), """{"theme":"dark"}""", out)
        val contents = BackupReader().read(ByteArrayInputStream(out.toByteArray()), tmp())
        assertEquals(listOf("p1", "p2"), contents.manifest.projects)
        assertEquals(2, contents.projects.size)
        assertTrue(contents.projects.all { it.ok })
        assertEquals(setOf("Round trip", "Second"), contents.projects.map { it.project!!.name }.toSet())
        assertEquals(p.spec, contents.projects.first().project!!.spec)
        assertEquals("""{"theme":"dark"}""", contents.settingsJson)
    }

    @Test fun readHeadIsPortable() {
        // A stream that returns one byte per read() call, like many content providers do.
        val slow = object : java.io.InputStream() {
            val data = byteArrayOf(1, 2, 3, 4, 5); var i = 0
            override fun read(): Int = if (i < data.size) data[i++].toInt() else -1
            override fun read(b: ByteArray, off: Int, len: Int): Int = if (i >= data.size) -1 else { b[off] = data[i++]; 1 }
        }
        assertTrue(byteArrayOf(1, 2, 3, 4).contentEquals(slow.readHead(4)))
        assertTrue(byteArrayOf(9).contentEquals(ByteArrayInputStream(byteArrayOf(9)).readHead(4)))
    }

    @Test fun imageSizes() {
        assertEquals(4 to 3, FileMagic.imageSize(png))
    }
}
