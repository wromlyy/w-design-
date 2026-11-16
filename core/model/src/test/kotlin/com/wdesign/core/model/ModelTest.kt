package com.wdesign.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryStackTest {
    @Test fun undoRedo() {
        val h = HistoryStack(0)
        h.push(1); h.push(2); h.push(3)
        assertEquals(2, h.undo()); assertEquals(1, h.undo()); assertEquals(0, h.undo())
        assertNull(h.undo())
        assertEquals(1, h.redo()); assertEquals(2, h.redo())
        assertEquals(2, h.present)
        h.push(9)
        assertFalse(h.canRedo)
    }

    @Test fun coalesceMakesOneStep() {
        val h = HistoryStack("a")
        h.beginCoalesce(); h.push("b"); h.push("c"); h.push("d"); h.endCoalesce()
        assertEquals(1, h.undoDepth)
        assertEquals("a", h.undo())
        assertEquals("d", h.redo())
    }

    @Test fun capacityIsBounded() {
        val h = HistoryStack(0, capacity = 100)
        repeat(250) { h.push(it + 1) }
        assertEquals(100, h.undoDepth)
    }
}

class PaletteTest {
    @Test fun contrastKnownValues() {
        assertEquals(21.0, ArgbColor.contrast(ArgbColor.Black, ArgbColor.White), 0.01)
        assertEquals(1.0, ArgbColor.contrast(ArgbColor.White, ArgbColor.White), 0.001)
    }

    @Test fun generatedPalettesAreReadable() {
        for (h in Harmony.entries) for (seed in listOf(0xFF0000L, 0x00FF00L, 0x9CC9F0L, 0x222222L, 0xFFFF00L)) for (dark in listOf(true, false)) {
            val p = PaletteGenerator.generate(ArgbColor.rgb(seed), h, dark)
            assertTrue("onSurface $h $seed $dark", ArgbColor.contrast(p.onSurface, p.surface) >= 4.5)
            assertTrue("onAccent", ArgbColor.contrast(p.onAccent, p.accent) >= 3.0)
        }
    }

    @Test fun ensureReadableReachesTarget() {
        val fg = PaletteGenerator.ensureReadable(ArgbColor.rgb(0x333344), ArgbColor.rgb(0x222222))
        assertTrue(ArgbColor.contrast(fg, ArgbColor.rgb(0x222222)) >= 4.5)
    }

    @Test fun colorParsing() {
        assertEquals("#FF9CC9F0", ArgbColor.parse("#9CC9F0")!!.toHex())
        assertEquals("#80FFFFFF", ArgbColor.parse("80ffffff")!!.toHex())
        assertEquals("#FFFFFFFF", ArgbColor.parse("#fff")!!.toHex())
        assertNull(ArgbColor.parse("#zzz"))
        assertNull(ArgbColor.parse("#12345"))
    }
}

class DesignLinkTest {
    @Test fun roundTripAndShort() {
        for (p in BuiltInPresets.all) {
            val link = DesignLink.encode(p.name, p.spec)
            assertTrue("${p.id} length ${link.length}", link.length < DesignLink.MAX_LENGTH)
            val (name, spec) = DesignLink.decode(link)!!
            assertEquals(p.name, name)
            assertEquals(p.spec.palette, spec.palette)
            assertEquals(p.spec.wallpaper.type, spec.wallpaper.type)
            assertEquals(p.spec.wallpaper.seed, spec.wallpaper.seed)
            assertEquals(p.spec.lockScreen.clock.render, spec.lockScreen.clock.render)
            assertEquals(p.spec.icons.mask, spec.icons.mask)
        }
    }

    @Test fun garbageIsNull() {
        assertNull(DesignLink.decode("wdesign://design?d=!!!"))
        assertNull(DesignLink.decode("wdesign://design?d=AAAA"))
    }
}

class HyperOsParsingTest {
    @Test fun hyperOsVersions() {
        val v1 = SystemUiVersion.parse("OS1.0", "OS1.0.3.0.UNCCNXM", "V816")
        assertEquals(SystemUiKind.HYPEROS, v1.kind); assertEquals(1, v1.major); assertEquals("CN", v1.region)
        val v2 = SystemUiVersion.parse(null, "OS2.0.212.0.VNCCNXM")
        assertEquals(2, v2.major); assertEquals("CN", v2.region)
        val v3 = SystemUiVersion.parse("OS3.0", "OS3.0.5.0.WOCMIXM")
        assertEquals(3, v3.major); assertEquals("MI", v3.region)
        val v4 = SystemUiVersion.parse("OS4.0", "OS4.0.1.0.XNCEUXM")
        assertEquals(4, v4.major); assertEquals("EU", v4.region)
        assertEquals(2, SystemUiVersion.parse(null, null, null, "2").major)
    }

    @Test fun miuiFallback() {
        val m = SystemUiVersion.parse(null, "V14.0.4.0.TMRMIXM", "V140")
        assertEquals(SystemUiKind.MIUI, m.kind); assertEquals(14, m.major); assertEquals("MI", m.region)
        assertEquals(12, SystemUiVersion.parse(null, null, "V125").major)
        assertEquals(8, SystemUiVersion.parse(null, null, "V816").major)
    }

    @Test fun nonXiaomi() {
        val o = SystemUiVersion.parse(null, "eng.build.20240101", null)
        assertEquals(SystemUiKind.OTHER, o.kind)
        assertFalse(o.isXiaomi)
    }
}

class CompatibilityTest {
    private fun of(list: List<FeatureSupport>, id: FeatureId) = list.first { it.id == id }

    @Test fun nonXiaomiDevice() {
        val m = CompatibilityMatrix.evaluate(DeviceSnapshot(manufacturer = "Google", brand = "google", sdkInt = 34, pinShortcutSupported = true, pinWidgetSupported = true))
        assertEquals(SupportStatus.UNSUPPORTED, of(m, FeatureId.MTZ_INSTALL).status)
        assertEquals(SupportStatus.SUPPORTED, of(m, FeatureId.MTZ_BUILD).status)
        assertEquals(SupportStatus.SUPPORTED, of(m, FeatureId.WALLPAPER_LOCK).status)
        assertEquals(SupportStatus.SUPPORTED, of(m, FeatureId.QS_TILE).status)
        assertEquals(SupportStatus.UNSUPPORTED, of(m, FeatureId.CONTROL_CENTER).status)
        assertEquals(SupportStatus.UNSUPPORTED, of(m, FeatureId.SYSTEM_ICONS).status)
        assertEquals(FeatureId.entries.size, m.size)
    }

    @Test fun hyperOsChina() {
        val snap = DeviceSnapshot(manufacturer = "Xiaomi", brand = "Xiaomi", sdkInt = 35,
            system = SystemUiVersion.parse("OS3.0", "OS3.0.1.0.WNCCNXM"), themeManagerInstalled = true, pinShortcutSupported = true, pinWidgetSupported = true)
        val m = CompatibilityMatrix.evaluate(snap)
        assertEquals(SupportStatus.PARTIAL, of(m, FeatureId.MTZ_INSTALL).status)
        assertEquals(Reason.CLOCK_SYSTEM_EDITOR_OS3, of(m, FeatureId.LOCK_CLOCK).reason)
        assertEquals(SupportStatus.REQUIRES_ACTION, of(m, FeatureId.PINNED_SHORTCUTS).status)
        assertEquals(SupportStatus.PARTIAL, of(m, FeatureId.WALLPAPER_LOCK).status)
    }

    @Test fun hyperOsGlobalOld() {
        val snap = DeviceSnapshot(manufacturer = "Xiaomi", brand = "Redmi", sdkInt = 32,
            system = SystemUiVersion.parse("OS1.0", "OS1.0.2.0.ULQMIXM"), themeManagerInstalled = true)
        val m = CompatibilityMatrix.evaluate(snap)
        assertEquals(SupportStatus.REQUIRES_ACTION, of(m, FeatureId.MTZ_INSTALL).status)
        assertEquals(HowTo.PC_THEME_EDITOR, of(m, FeatureId.MTZ_INSTALL).howTo)
        assertEquals(Reason.CLOCK_SYSTEM_EDITOR_OS1, of(m, FeatureId.LOCK_CLOCK).reason)
        assertEquals(SupportStatus.REQUIRES_ACTION, of(m, FeatureId.QS_TILE).status)
    }
}

class SerializationTest {
    @Test fun fullSpecRoundTrip() {
        for (p in BuiltInPresets.all) {
            val json = p.spec.toJson()
            assertEquals(p.spec, themeSpecFromJson(json))
        }
        val rich = ThemeSpec(
            lockScreen = LockTemplates.build(LockStyle.LIQUID_GLASS).copy(wallpaper = WallpaperSpec(photo = AssetRef("wallpapers/a.jpg"))),
            icons = IconStyleSpec(overrides = listOf(IconOverride("com.android.chrome", glyph = "globe"))),
            mtz = MtzSettings(colorOverrides = mapOf("framework-res" to mapOf("a" to "#FF000000"))),
        )
        assertEquals(rich, themeSpecFromJson(rich.toJson()))
        assertTrue(rich.toJson().contains("\"#FF"))
        assertNotNull(rich.assetRefs().firstOrNull { it.path == "wallpapers/a.jpg" })
    }

    @Test fun presetsCoverAllCategories() {
        assertTrue(BuiltInPresets.all.size >= 20)
        for (c in DiscoverCategory.entries) assertTrue("$c", BuiltInPresets.inCategory(c).isNotEmpty())
        assertEquals(BuiltInPresets.all.size, BuiltInPresets.all.map { it.id }.toSet().size)
    }

    @Test fun fodOverlap() {
        val e = CanvasElement("a", ElementType.SHAPE, 0.4f, 0.8f, 0.2f, 0.1f)
        assertTrue(e.overlapsCircle(0.5f, 0.86f, 0.075f, 2.2f))
        assertFalse(e.overlapsCircle(0.5f, 0.2f, 0.05f, 2.2f))
    }
}
