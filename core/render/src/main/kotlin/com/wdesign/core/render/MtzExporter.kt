package com.wdesign.core.render

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import com.wdesign.core.model.DesignProject
import com.wdesign.core.model.LockStyle
import com.wdesign.core.model.LockTemplates
import com.wdesign.core.model.MtzSettings
import com.wdesign.core.model.ThemeSpec
import com.wdesign.core.mtz.MamlAssetRequest
import com.wdesign.core.mtz.MamlLockscreenGenerator
import com.wdesign.core.mtz.MamlNote
import com.wdesign.core.mtz.MamlPackage
import com.wdesign.core.mtz.MiWallpaperGenerator
import com.wdesign.core.mtz.MtzBuildReport
import com.wdesign.core.mtz.MtzBuilder
import com.wdesign.core.mtz.MtzInput
import com.wdesign.core.mtz.MtzMeta
import com.wdesign.core.mtz.MtzReference
import com.wdesign.core.mtz.MtzValidator
import com.wdesign.core.mtz.ValidationReport
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

data class MtzExportResult(
    val bytes: ByteArray,
    val report: MtzBuildReport,
    val validation: ValidationReport,
    val notes: List<Pair<MamlNote, String>>,
    val iconCount: Int,
)

/** A launcher entry: package, activity and its (unstyled) icon. */
data class LauncherApp(val packageName: String, val activity: String, val label: String, val multiActivity: Boolean)

/**
 * Android side of MTZ export: renders every image the package needs (wallpapers at ≥2400 px,
 * 168 px icons, MAML lock assets, previews) and hands them to the pure-JVM [MtzBuilder].
 */
class MtzExporter(private val context: Context) {

    fun launcherApps(): List<LauncherApp> {
        val pm = context.packageManager
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val list = runCatching { pm.queryIntentActivities(i, PackageManager.MATCH_ALL) }.getOrDefault(emptyList())
        val perPkg = list.groupingBy { it.activityInfo.packageName }.eachCount()
        return list.map {
            LauncherApp(it.activityInfo.packageName, it.activityInfo.name, it.loadLabel(pm).toString(), (perPkg[it.activityInfo.packageName] ?: 0) > 1)
        }.sortedBy { it.label.lowercase() }
    }

    fun appIcon(app: LauncherApp, size: Int): Bitmap? = runCatching {
        val d = context.packageManager.getActivityIcon(android.content.ComponentName(app.packageName, app.activity))
        IconRenderer.drawableToBitmap(d, size)
    }.getOrNull()

    /** Target size for MTZ wallpapers: the device aspect, at least 1080 wide and 2400 tall. */
    fun targetSize(screenW: Int, screenH: Int): Pair<Int, Int> {
        val f = max(max(1f, 1080f / screenW), 2400f / screenH)
        return (screenW * f).roundToInt() to (screenH * f).roundToInt()
    }

    fun export(
        project: DesignProject,
        assetsDir: File?,
        settings: MtzSettings,
        screenW: Int,
        screenH: Int,
        reference: MtzReference? = null,
        unlockHint: String = "Swipe up to unlock",
        progress: (Float) -> Unit = {},
    ): MtzExportResult {
        val spec = project.spec
        val (w, h) = targetSize(screenW, screenH)
        progress(0.05f)
        val home = if (settings.includeWallpapers) Bitmaps.jpeg(WallpaperRenderer.render(spec.wallpaper, w, h, assetsDir)) else null
        val lockWp = if (settings.includeWallpapers) Bitmaps.jpeg(WallpaperRenderer.render(spec.lockWallpaper, w, h, assetsDir)) else null
        progress(0.2f)

        // Icons
        val icons = LinkedHashMap<String, ByteArray>()
        val styled = mutableListOf<Bitmap>()
        if (settings.includeIcons) {
            val overrides = spec.icons.overrides.associateBy { it.packageName }
            val apps = if (settings.iconsForAllApps) launcherApps().take(400) else emptyList()
            for ((i, app) in apps.withIndex()) {
                val key = if (app.multiActivity) app.activity else app.packageName
                val ov = overrides[key] ?: overrides[app.packageName]
                val src = ov?.asset?.let { a -> assetsDir?.let { Bitmaps.decodeSampled(File(it, a.path), 256, 256) } } ?: appIcon(app, 192)
                val bmp = IconRenderer.render(src, spec.icons, 168, app.label)
                icons[key] = Bitmaps.png(bmp)
                if (styled.size < 40) styled += bmp
                if (i % 20 == 0) progress(0.2f + 0.35f * i / apps.size.coerceAtLeast(1))
            }
            // Overrides for apps that are not installed here are still exported.
            for (ov in spec.icons.overrides) if (ov.packageName !in icons) {
                val src = ov.asset?.let { a -> assetsDir?.let { Bitmaps.decodeSampled(File(it, a.path), 256, 256) } }
                if (src != null || ov.glyph != null) icons[ov.packageName] = Bitmaps.png(IconRenderer.render(src, spec.icons, 168, ov.glyph ?: ov.packageName.substringAfterLast('.')))
            }
        }
        progress(0.55f)

        // Lock screen
        var notes: List<Pair<MamlNote, String>> = emptyList()
        val lockPkg = if (settings.includeLockscreen) {
            val plan = MamlLockscreenGenerator.plan(spec.lockScreen, w, h, unlockHint)
            notes = plan.notes
            MamlPackage(plan.manifestXml, fulfil(plan.requests, spec, w, h, assetsDir))
        } else null
        progress(0.75f)

        // Previews
        val previews = LinkedHashMap<String, ByteArray>()
        if (settings.includePreviews) {
            val pw = 540; val ph = (h * 540f / w).roundToInt()
            previews["preview_lockscreen_0.jpg"] = Bitmaps.jpeg(LockScreenRenderer.render(spec, pw, ph, assetsDir), 88)
            val labels = launcherApps().take(styled.size).map { it.label }
            previews["preview_launcher_0.jpg"] = Bitmaps.jpeg(HomePreviewRenderer.home(spec, pw, ph, assetsDir, styled, labels), 88)
            previews["preview_icons_0.jpg"] = Bitmaps.jpeg(HomePreviewRenderer.iconSheet(spec, pw, ph, styled), 88)
        }
        val parts = if (settings.experimentalIconMask) iconParts(spec) else emptyMap()
        progress(0.85f)
        val m = settings
        val input = MtzInput(
            meta = MtzMeta(
                title = m.title.ifBlank { project.name }, designer = m.designer.ifBlank { project.meta.author.ifBlank { "W Design" } },
                author = m.author.ifBlank { project.meta.author.ifBlank { "W Design user" } }, version = m.version.ifBlank { "1.0" },
                uiVersion = m.uiVersion.ifBlank { "1" },
                description = (m.description.ifBlank { "Created with W Design." } + " Unofficial MTZ, not verified or signed by Xiaomi."),
            ),
            homeWallpaperJpg = home, lockWallpaperJpg = lockWp, icons = icons, iconParts = parts, lockscreen = lockPkg,
            previews = previews, miWallpaperXml = if (m.experimentalMiWallpaper) MiWallpaperGenerator.generate(spec.live) else null,
            reference = reference, colorOverrides = if (reference != null) m.colorOverrides else emptyMap(),
        )
        val out = ByteArrayOutputStream()
        val report = MtzBuilder().build(input, out)
        val bytes = out.toByteArray()
        val keys = reference?.modules?.mapValues { (_, b) ->
            runCatching {
                val found = mutableSetOf<String>()
                com.wdesign.core.format.SafeZipReader(com.wdesign.core.format.ZipLimits.Mtz).walk(b.inputStream(), allowAnyName = true) { n, d ->
                    if (n.endsWith("theme_values.xml")) found += com.wdesign.core.mtz.ThemeValues.colors(d).keys
                }
                found.toSet()
            }.getOrDefault(emptySet())
        }.orEmpty()
        val validation = MtzValidator.validate(bytes, keys, input.colorOverrides)
        progress(1f)
        return MtzExportResult(bytes, report, validation, notes, icons.size)
    }

    /** Minimal package (wallpapers + a simple MAML lock) to test whether this ROM accepts imported themes. */
    fun probe(screenW: Int, screenH: Int): MtzExportResult {
        val spec = ThemeSpec(lockScreen = LockTemplates.build(LockStyle.MINIMAL))
        val project = DesignProject("probe", "W Design compatibility probe", spec = spec)
        return export(project, null, MtzSettings(title = "W Design probe", includeIcons = false, includePreviews = true), screenW, screenH)
    }

    private fun fulfil(requests: List<MamlAssetRequest>, spec: ThemeSpec, w: Int, h: Int, assetsDir: File?): Map<String, ByteArray> {
        val out = LinkedHashMap<String, ByteArray>()
        val wallpaper by lazy { WallpaperRenderer.render(spec.lockWallpaper, w, h, assetsDir) }
        val blurCache = HashMap<Int, Bitmap>()
        for (req in requests) when (req) {
            is MamlAssetRequest.Background -> out[req.file] = Bitmaps.jpeg(wallpaper)
            is MamlAssetRequest.GlassPanel -> out[req.file] = Bitmaps.png(cropElement(req.element, req.widthPx, req.heightPx, w, h) { c ->
                LockScreenRenderer.drawElement(c, req.element.copy(rotation = 0f), w, h, assetsDir, wallpaper, blurCache)
            })
            is MamlAssetRequest.RasterElement -> out[req.file] = Bitmaps.png(cropElement(req.element, req.widthPx, req.heightPx, w, h) { c ->
                LockScreenRenderer.drawElement(c, req.element, w, h, assetsDir, wallpaper, blurCache)
            })
            is MamlAssetRequest.DigitSet -> out.putAll(DigitsRasterizer.digits(req.prefix, req.clock, req.sizePx))
        }
        return out
    }

    private inline fun cropElement(e: com.wdesign.core.model.CanvasElement, pw: Int, ph: Int, w: Int, h: Int, draw: (Canvas) -> Unit): Bitmap {
        val b = Bitmap.createBitmap(pw.coerceAtLeast(1), ph.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.translate(-e.x * w, -e.y * h)
        draw(c)
        return b
    }

    private fun iconParts(spec: ThemeSpec): Map<String, ByteArray> {
        val size = 168
        fun bmp(draw: (Canvas) -> Unit) = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { draw(Canvas(it)) }
        val path = IconRenderer.maskPath(spec.icons.mask, size.toFloat(), spec.icons.cornerPct)
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        return mapOf(
            "icon_background" to Bitmaps.png(bmp { c -> p.color = (spec.icons.background ?: spec.palette.surface).argb; c.drawPath(path, p) }),
            "icon_mask" to Bitmaps.png(bmp { c -> p.color = android.graphics.Color.BLACK; c.drawPath(path, p) }),
            "icon_border" to Bitmaps.png(bmp { c ->
                p.style = android.graphics.Paint.Style.STROKE; p.strokeWidth = 3f; p.color = spec.palette.onSurface.withAlpha(0.25f).argb; c.drawPath(path, p)
            }),
        )
    }
}
