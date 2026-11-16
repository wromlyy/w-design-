package com.wdesign.core.mtz

import com.wdesign.core.format.FileMagic
import com.wdesign.core.format.IssueCode
import com.wdesign.core.model.BuiltInPresets
import com.wdesign.core.model.CanvasElement
import com.wdesign.core.model.ClockFormat
import com.wdesign.core.model.ClockRender
import com.wdesign.core.model.ElementType
import com.wdesign.core.model.LockStyle
import com.wdesign.core.model.LockTemplates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO

/** Test-side stand-in for the Android render layer: produces real images of the right size. */
object FakeRenderer {
    fun image(w: Int, h: Int, fmt: String, argb: Int = 0xFF1B2A3A.toInt()): ByteArray {
        val img = BufferedImage(w, h, if (fmt == "png") BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics(); g.color = java.awt.Color(argb, true); g.fillRect(0, 0, w, h); g.dispose()
        return ByteArrayOutputStream().also { ImageIO.write(img, fmt, it) }.toByteArray()
    }

    fun fulfil(plan: MamlPlan): Map<String, ByteArray> = buildMap {
        for (req in plan.requests) when (req) {
            is MamlAssetRequest.Background -> put(req.file, image(req.widthPx, req.heightPx, "jpg"))
            is MamlAssetRequest.GlassPanel -> put(req.file, image(req.widthPx, req.heightPx, "png", 0x40FFFFFF))
            is MamlAssetRequest.RasterElement -> put(req.file, image(req.widthPx, req.heightPx, "png"))
            is MamlAssetRequest.DigitSet -> req.files.forEach { put(it, image(req.sizePx / 2, req.sizePx, "png", -1)) }
        }
    }

    fun demoInput(style: LockStyle = LockStyle.LIQUID_GLASS, render: ClockRender? = null): MtzInput {
        val preset = BuiltInPresets.all.first()
        var lock = LockTemplates.build(style)
        if (render != null) lock = lock.copy(clock = lock.clock.copy(render = render))
        val plan = MamlLockscreenGenerator.plan(lock, 1080, 2400)
        return MtzInput(
            meta = MtzMeta("Graphite <Glass> & \"Co\"", "W Design", "Tester"),
            homeWallpaperJpg = image(1080, 2400, "jpg"),
            lockWallpaperJpg = image(1080, 2400, "jpg"),
            icons = mapOf("com.android.chrome" to image(168, 168, "png"), "com.miui.gallery" to image(168, 168, "png")),
            lockscreen = MamlPackage(plan.manifestXml, fulfil(plan)),
            previews = mapOf("preview_lockscreen_0.jpg" to image(540, 1200, "jpg"), "preview_launcher_0.jpg" to image(540, 1200, "jpg"), "preview_icons_0.jpg" to image(540, 1200, "jpg")),
            miWallpaperXml = MiWallpaperGenerator.generate(preset.spec.live),
        )
    }

    fun build(input: MtzInput): ByteArray = ByteArrayOutputStream().also { MtzBuilder().build(input, it) }.toByteArray()
}

class MtzTest {
    private fun entries(zip: ByteArray): Map<String, ByteArray> = buildMap {
        ZipInputStream(ByteArrayInputStream(zip)).use { z -> while (true) { val e = z.nextEntry ?: break; put(e.name, z.readBytes()) } }
    }

    @Test fun packageLayoutAndNestedModules() {
        val mtz = FakeRenderer.build(FakeRenderer.demoInput())
        val top = entries(mtz)
        for (n in listOf("description.xml", "wallpaper/default_wallpaper.jpg", "wallpaper/default_lock_wallpaper.jpg", "preview/preview_lockscreen_0.jpg", "icons", "lockscreen", "miwallpaper"))
            assertTrue(n, n in top)
        for (m in listOf("icons", "lockscreen", "miwallpaper")) assertTrue(FileMagic.isZip(top[m]!!))
        val lock = entries(top["lockscreen"]!!)
        assertTrue("advance/manifest.xml" in lock)
        assertTrue("advance/bg.jpg" in lock)
        val icons = entries(top["icons"]!!)
        assertTrue("res/drawable-xxhdpi/com.android.chrome.png" in icons)
    }

    @Test fun lockscreenManifestHasTimeDateUnlock() {
        val plan = MamlLockscreenGenerator.plan(LockTemplates.build(LockStyle.MINIMAL), 1080, 2400)
        val xml = plan.manifestXml
        val doc = MtzXml.parseSecure(xml.toByteArray())
        assertEquals("Lockscreen", doc.documentElement.nodeName)
        assertTrue(xml.contains("<DateTime name=\"wd_clock0\""))
        assertTrue(xml.contains("formatExp=\"ifelse(#time_format,'HH:mm','hh:mm')\""))
        assertTrue(xml.contains("name=\"wd_date\""))
        assertTrue(xml.contains("<ExternCommand command=\"unlock\""))
        assertFalse(xml.contains("password", ignoreCase = true))
    }

    @Test fun imageDigitsHave1224Twins() {
        val lock = LockTemplates.build(LockStyle.NOTHING_INSPIRED) // dot matrix
        val plan = MamlLockscreenGenerator.plan(lock, 1080, 2400)
        assertTrue(plan.manifestXml.contains("format=\"HH:mm\" visibility=\"#time_format\""))
        assertTrue(plan.manifestXml.contains("format=\"hh:mm\" visibility=\"!#time_format\""))
        val digits = plan.requests.filterIsInstance<MamlAssetRequest.DigitSet>().single()
        assertEquals(11, digits.files.size)
        assertTrue(digits.files.contains("clock/time_dot.png"))
        val forced = MamlLockscreenGenerator.plan(lock.copy(clock = lock.clock.copy(format = ClockFormat.H24)), 1080, 2400)
        assertFalse(forced.manifestXml.contains("#time_format"))
    }

    @Test fun fodOverlapHidesElementAndWarns() {
        val lock = LockTemplates.build(LockStyle.MINIMAL).copy(elements = listOf(
            CanvasElement("over", ElementType.SHAPE, 0.4f, 0.82f, 0.2f, 0.08f, props = mapOf("shape" to "rect", "fill" to "#80FFFFFF")),
            CanvasElement("free", ElementType.SHAPE, 0.1f, 0.3f, 0.2f, 0.05f),
        ))
        val plan = MamlLockscreenGenerator.plan(lock, 1080, 2400)
        assertTrue(plan.manifestXml.contains("name=\"wd_over\"") && plan.manifestXml.contains("visibility=\"#fod_enable==0\""))
        assertEquals(listOf(MamlNote.FOD_OVERLAP_HIDDEN to "over"), plan.notes.filter { it.first == MamlNote.FOD_OVERLAP_HIDDEN })
    }

    @Test fun validatorIsGreenForDemo() {
        for (style in LockStyle.entries) {
            val report = MtzValidator.validate(FakeRenderer.build(FakeRenderer.demoInput(style)))
            assertTrue("$style ${report.results.filter { it.status == CheckStatus.FAIL }}", report.passed)
            assertEquals(CheckStatus.PASS, report.status(CheckItem.LOCK_HAS_UNLOCK))
            assertEquals(CheckStatus.PASS, report.status(CheckItem.LOCK_ASSETS_PRESENT))
            assertEquals(CheckStatus.PASS, report.status(CheckItem.HOME_WALLPAPER_SIZE))
        }
    }

    @Test fun validatorCatchesProblems() {
        val input = FakeRenderer.demoInput().copy(
            homeWallpaperJpg = FakeRenderer.image(720, 1280, "jpg"),
            icons = mapOf("big" to FakeRenderer.image(300, 300, "png")),
            lockscreen = MamlPackage("<Lockscreen version=\"2\"><Image src=\"missing.png\"/></Lockscreen>", emptyMap()),
        )
        val report = MtzValidator.validate(FakeRenderer.build(input))
        assertFalse(report.passed)
        assertEquals(CheckStatus.WARN, report.status(CheckItem.HOME_WALLPAPER_SIZE))
        assertEquals(CheckStatus.FAIL, report.status(CheckItem.ICON_SIZES))
        assertEquals(CheckStatus.FAIL, report.status(CheckItem.LOCK_HAS_TIME))
        assertEquals(CheckStatus.FAIL, report.status(CheckItem.LOCK_HAS_UNLOCK))
        assertEquals(CheckStatus.FAIL, report.status(CheckItem.LOCK_ASSETS_PRESENT))
    }

    @Test fun descriptionEscapes() {
        val xml = MtzBuilder().descriptionXml(MtzMeta("A <b> & \"c\" 'd'", "x", "y"))
        assertTrue(xml.contains("<title>A &lt;b&gt; &amp; &quot;c&quot; &apos;d&apos;</title>"))
        val doc = MtzXml.parseSecure(xml.toByteArray())
        assertEquals("A <b> & \"c\" 'd'", doc.getElementsByTagName("title").item(0).textContent)
    }

    @Test fun overridesOnlyTouchExistingKeys() {
        val values = """<?xml version="1.0" encoding="utf-8"?>
<MIUI_Theme_Values>
    <color name="status_bar_text">#ffffffff</color>
    <color name="qs_tile_active">#ff3482ff</color>
    <!-- comment kept -->
</MIUI_Theme_Values>
"""
        val module = MtzBuilder.nestedZip(mapOf("theme_values.xml" to values.toByteArray(), "res/drawable-xxhdpi/x.png" to FakeRenderer.image(4, 4, "png")))
        val ref = MtzReference("1", mapOf("com.android.systemui" to module))
        val input = FakeRenderer.demoInput().copy(reference = ref, colorOverrides = mapOf(
            "com.android.systemui" to mapOf("qs_tile_active" to "#FF9CC9F0", "invented_key" to "#FF000000"),
            "framework-res" to mapOf("x" to "#FF000000"),
        ))
        val out = ByteArrayOutputStream()
        val report = MtzBuilder().build(input, out)
        assertEquals(listOf("qs_tile_active"), report.appliedOverrides["com.android.systemui"])
        assertEquals(listOf("invented_key"), report.ignoredOverrides["com.android.systemui"])
        assertTrue("framework-res" in report.ignoredOverrides)
        val top = entries(out.toByteArray())
        assertFalse("framework-res" in top)
        val sys = entries(top["com.android.systemui"]!!)
        val rewritten = sys["theme_values.xml"]!!.decodeToString()
        assertTrue(rewritten.contains("<color name=\"qs_tile_active\">#ff9cc9f0</color>"))
        assertTrue(rewritten.contains("<color name=\"status_bar_text\">#ffffffff</color>"))
        assertTrue(rewritten.contains("<!-- comment kept -->"))
        assertFalse(rewritten.contains("invented_key"))
        assertTrue("res/drawable-xxhdpi/x.png" in sys)
        // validator flags invented keys as ignored
        val v = MtzValidator.validate(out.toByteArray(), mapOf("com.android.systemui" to setOf("status_bar_text", "qs_tile_active")),
            mapOf("com.android.systemui" to mapOf("qs_tile_active" to "#FF9CC9F0")))
        assertEquals(CheckStatus.PASS, v.status(CheckItem.OVERRIDES_EXISTING_KEYS))
    }

    @Test fun dtdPastFirst64KbIsRejected() {
        val padding = "<!--" + "x".repeat(70 * 1024) + "-->"
        val evil = "<?xml version=\"1.0\"?>$padding<!DOCTYPE a [<!ENTITY e SYSTEM \"file:///etc/passwd\">]><MIUI-Theme><title>&e;</title></MIUI-Theme>"
        assertTrue(runCatching { MtzXml.parseSecure(evil.toByteArray()) }.exceptionOrNull() is MtzXml.UnsafeXmlException)
    }

    @Test fun datePatternTranslation() {
        for (p in MamlDatePattern.PRESETS) {
            val r = MamlDatePattern.translate(p)
            assertFalse(p, r.simplified); assertEquals(p, r.pattern)
        }
        val r = MamlDatePattern.translate("EEE d MMM G 'week' w")
        assertTrue(r.simplified)
        assertEquals(setOf('G', 'w'), r.dropped)
        assertTrue(r.pattern.contains("'week'"))
        val lock = LockTemplates.build(LockStyle.MINIMAL).let { it.copy(date = it.date.copy(pattern = "EEEE d MMMM yyyy G")) }
        val plan = MamlLockscreenGenerator.plan(lock, 1080, 2400)
        assertTrue(plan.notes.any { it.first == MamlNote.DATE_PATTERN_SIMPLIFIED })
        assertFalse(plan.manifestXml.contains(" G\""))
    }

    @Test fun missingDotGlyphFails() {
        val input = FakeRenderer.demoInput(LockStyle.NOTHING_INSPIRED)
        val lock = input.lockscreen!!
        val broken = input.copy(lockscreen = lock.copy(assets = lock.assets - "clock/time_dot.png"))
        val report = MtzValidator.validate(FakeRenderer.build(broken))
        assertEquals(CheckStatus.FAIL, report.status(CheckItem.LOCK_ASSETS_PRESENT))
    }

    @Test fun xxePayloadRejected() {
        val evil = """<?xml version="1.0"?>
<!DOCTYPE foo [ <!ENTITY xxe SYSTEM "file:///etc/passwd"> ]>
<MIUI-Theme><title>&xxe;</title></MIUI-Theme>"""
        val mtz = ByteArrayOutputStream().also { b ->
            ZipOutputStream(b).use { z -> z.putNextEntry(ZipEntry("description.xml")); z.write(evil.toByteArray()); z.closeEntry() }
        }.toByteArray()
        val tmp = Files.createTempDirectory("mtz").toFile()
        val result = MtzReader().inspect(ByteArrayInputStream(mtz), tmp)
        assertFalse(result.preview.ok)
        assertEquals(IssueCode.XML_UNSAFE, result.preview.errors.first().code)
        val bomb = """<?xml version="1.0"?><!DOCTYPE lolz [<!ENTITY lol "lol"><!ENTITY lol2 "&lol;&lol;">]><MIUI-Theme><title>&lol2;</title></MIUI-Theme>"""
        assertTrue(runCatching { MtzXml.parseSecure(bomb.toByteArray()) }.exceptionOrNull() is MtzXml.UnsafeXmlException)
    }

    @Test fun importExtractsWallpapersAndIcons() {
        val values = """<MIUI_Theme_Values><color name="a">#ff000000</color></MIUI_Theme_Values>"""
        val input = FakeRenderer.demoInput().copy(
            reference = MtzReference("1", mapOf("framework-res" to MtzBuilder.nestedZip(mapOf("theme_values.xml" to values.toByteArray())))),
            colorOverrides = mapOf("framework-res" to mapOf("a" to "#FFFF0000")),
        )
        val mtz = FakeRenderer.build(input)
        val tmp = Files.createTempDirectory("mtz").toFile()
        val result = MtzReader().inspect(ByteArrayInputStream(mtz), tmp)
        assertTrue(result.preview.errors.toString(), result.preview.ok)
        val p = result.preview.project!!
        assertEquals("Graphite <Glass> & \"Co\"", p.name)
        assertEquals(com.wdesign.core.model.WallpaperType.PHOTO, p.spec.wallpaper.type)
        assertTrue(p.spec.lockScreen.wallpaper?.photo != null)
        assertEquals(setOf("com.android.chrome", "com.miui.gallery"), p.spec.icons.overrides.map { it.packageName }.toSet())
        assertTrue(File(tmp, "assets/icons/com.android.chrome.png").isFile)
        assertTrue("lockscreen" in result.modules && "icons" in result.modules)
        assertTrue((result.lockscreenElements["DateTime"] ?: 0) >= 1)
        assertEquals(mapOf("a" to "#ffff0000"), result.themeValues["framework-res"])
        assertEquals("1", result.asReference().uiVersion)
        assertEquals(com.wdesign.core.model.DesignSource.MTZ_IMPORT, p.meta.source)
    }

    @Test fun oversizePackageRejected() {
        val big = ByteArrayOutputStream().also { b ->
            ZipOutputStream(b).use { z ->
                z.setLevel(0)
                z.putNextEntry(ZipEntry("description.xml")); z.write("<MIUI-Theme><title>x</title></MIUI-Theme>".toByteArray()); z.closeEntry()
                val chunk = java.util.Random(1).let { r -> ByteArray(1024 * 1024).also(r::nextBytes) }
                repeat(3) { i -> z.putNextEntry(ZipEntry("wallpaper/w$i.bin")); repeat(30) { z.write(chunk) }; z.closeEntry() }
            }
        }.toByteArray()
        val result = MtzReader().inspect(ByteArrayInputStream(big), Files.createTempDirectory("mtz").toFile())
        assertFalse(result.preview.ok)
        assertEquals(IssueCode.TOTAL_TOO_LARGE, result.preview.errors.first().code)
        val v = MtzValidator.validate(big)
        assertFalse(v.passed)
    }
}

/** Writes build/demo.mtz for manual inspection (unzip -l, xmllint). Also runnable as a main. */
class MtzDemoTest {
    @Test fun writeDemo() { MtzDemo.main(emptyArray()) }
}

object MtzDemo {
    @JvmStatic fun main(args: Array<String>) {
        val out = File(args.firstOrNull() ?: "build/demo.mtz")
        out.parentFile?.mkdirs()
        val bytes = FakeRenderer.build(FakeRenderer.demoInput(LockStyle.LIQUID_GLASS))
        out.writeBytes(bytes)
        val report = MtzValidator.validate(bytes)
        check(report.passed) { report.toString() }
        println("Wrote ${out.absolutePath} (${bytes.size} bytes); validator: ${report.results.joinToString { "${it.item}=${it.status}" }}")
    }
}
