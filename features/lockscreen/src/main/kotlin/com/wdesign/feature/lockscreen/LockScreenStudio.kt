package com.wdesign.feature.lockscreen

import android.content.ContentValues
import android.os.Build
import android.provider.MediaStore
import android.text.format.DateFormat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.wdesign.core.data.WD
import com.wdesign.core.data.WallpaperTarget
import com.wdesign.core.device.XiaomiIntents
import com.wdesign.core.model.ArgbColor
import com.wdesign.core.model.CanvasElement
import com.wdesign.core.model.CanvasElement.Props
import com.wdesign.core.model.ClockFormat
import com.wdesign.core.model.ClockRender
import com.wdesign.core.model.DesignKind
import com.wdesign.core.model.DesignProject
import com.wdesign.core.model.ElementType
import com.wdesign.core.model.HistoryStack
import com.wdesign.core.model.Ids
import com.wdesign.core.model.LockStyle
import com.wdesign.core.model.LockTemplates
import com.wdesign.core.model.SupportStatus
import com.wdesign.core.model.SystemFont
import com.wdesign.core.model.TextAlign
import com.wdesign.core.model.ThemeSpec
import com.wdesign.core.mtz.MamlDatePattern
import com.wdesign.core.render.LockRenderOptions
import com.wdesign.core.render.LockScreenRenderer
import com.wdesign.ds.Routes
import com.wdesign.ds.SectionHeader
import com.wdesign.ds.SegmentedControl
import com.wdesign.ds.SupportBadge
import com.wdesign.ds.W
import com.wdesign.ds.WButton
import com.wdesign.ds.WButtonKind
import com.wdesign.ds.WCard
import com.wdesign.ds.WChip
import com.wdesign.ds.WIcons
import com.wdesign.ds.WSliderRow
import com.wdesign.ds.WSwitchRow
import com.wdesign.ds.WTopBar
import com.wdesign.ds.rememberBitmap
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class Tab { TEMPLATES, CLOCK, DATE, DECOR, APPLY }

@Composable private fun styleName(s: LockStyle) = stringResource(when (s) {
    LockStyle.MINIMAL -> R.string.ls_t_MINIMAL; LockStyle.LIQUID_GLASS -> R.string.ls_t_LIQUID_GLASS; LockStyle.IOS_INSPIRED -> R.string.ls_t_IOS_INSPIRED
    LockStyle.NOTHING_INSPIRED -> R.string.ls_t_NOTHING_INSPIRED; LockStyle.AMOLED -> R.string.ls_t_AMOLED; LockStyle.CLASSIC -> R.string.ls_t_CLASSIC; LockStyle.CUSTOM -> R.string.ls_t_CUSTOM
})
@Composable private fun renderName(r: ClockRender) = stringResource(when (r) {
    ClockRender.TEXT -> R.string.ls_r_TEXT; ClockRender.STACKED -> R.string.ls_r_STACKED; ClockRender.DOT_MATRIX -> R.string.ls_r_DOT_MATRIX
    ClockRender.OUTLINE -> R.string.ls_r_OUTLINE; ClockRender.IMAGE_DIGITS -> R.string.ls_r_IMAGE_DIGITS
})
@Composable private fun alignName(a: TextAlign) = stringResource(when (a) { TextAlign.START -> R.string.ls_a_START; TextAlign.CENTER -> R.string.ls_a_CENTER; TextAlign.END -> R.string.ls_a_END })
@Composable private fun formatName(f: ClockFormat) = stringResource(when (f) { ClockFormat.FOLLOW_SYSTEM -> R.string.ls_f_FOLLOW_SYSTEM; ClockFormat.H24 -> R.string.ls_f_H24; ClockFormat.H12 -> R.string.ls_f_H12 })
@Composable private fun elementName(t: ElementType) = stringResource(when (t) {
    ElementType.TEXT -> R.string.ls_e_TEXT; ElementType.SHAPE -> R.string.ls_e_SHAPE; ElementType.IMAGE -> R.string.ls_e_IMAGE; ElementType.GLASS_PANEL -> R.string.ls_e_GLASS_PANEL
    ElementType.WIDGET_REF -> R.string.ls_e_WIDGET_REF; ElementType.ICON_GRID -> R.string.ls_e_ICON_GRID; ElementType.CLOCK -> R.string.ls_e_CLOCK; ElementType.DATE -> R.string.ls_e_DATE
})

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

@Composable
private fun Swatches(colors: List<ArgbColor>, selected: ArgbColor, onPick: (ArgbColor) -> Unit) {
    ChipRow {
        for (c in colors.distinct()) {
            val sel = c.argb == selected.argb
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(Color(c.argb))
                    .border(if (sel) 3.dp else 1.dp, if (sel) W.colors.accent else W.colors.outline, CircleShape)
                    .clickable { onPick(c) }
                    .semantics { contentDescription = c.toRgbHex() },
            )
        }
    }
}

@Composable
fun LockScreenStudio(projectId: String?, go: (String) -> Unit, back: () -> Unit, snackbar: SnackbarHostState) {
    val store = WD.services.projects
    val newName = stringResource(R.string.ls_new_name)
    var project by remember { mutableStateOf<DesignProject?>(null) }

    LaunchedEffect(projectId) {
        store.load()
        val active = runCatching { WD.services.settings.settings.first() }.getOrNull()?.activeProjectId
        project = projectId?.let(store::get) ?: active?.let(store::get) ?: store.projects.value.firstOrNull()
            ?: store.create(newName, ThemeSpec(lockScreen = LockTemplates.build(LockStyle.LIQUID_GLASS)), DesignKind.LOCK_SCREEN)
    }
    val p = project
    if (p == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = W.colors.accent) }
        return
    }
    StudioContent(p, go, back, snackbar)
}

@Composable
private fun StudioContent(initial: DesignProject, go: (String) -> Unit, back: () -> Unit, snackbar: SnackbarHostState) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = WD.services.projects
    val history = remember(initial.id) { HistoryStack(initial.spec) }
    var spec by remember(initial.id) { mutableStateOf(initial.spec) }
    var version by remember { mutableIntStateOf(0) }
    var tab by remember { mutableStateOf(Tab.TEMPLATES) }
    var selectedEl by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var busy by remember { mutableStateOf(false) }
    val is24 = DateFormat.is24HourFormat(ctx)
    val config = LocalConfiguration.current
    val wide = config.screenWidthDp >= 600

    fun edit(f: (ThemeSpec) -> ThemeSpec) { val n = f(spec); history.push(n); spec = n; version++ }

    // Ticking clock for the preview.
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000L - now % 1000L) } }
    // Autosave 1.5 s after the last edit.
    LaunchedEffect(version) { if (version > 0) { delay(1500); store.save(initial.copy(spec = spec, name = store.get(initial.id)?.name ?: initial.name)) } }

    var screen by remember { mutableStateOf(1080 to 2400) }
    LaunchedEffect(Unit) { val s = WD.services.snapshot(); screen = s.screenWidthPx to s.screenHeightPx }
    val pw = 540; val ph = (pw.toFloat() * screen.second / screen.first).roundToInt()
    val minute = now / 60_000
    val assets = store.assetsDir(initial.id)
    val preview by rememberBitmap(spec, minute, is24, pw, ph) {
        LockScreenRenderer.render(spec, pw, ph, assets, LockRenderOptions(includeGhosts = true, timeMs = System.currentTimeMillis(), systemIs24h = is24))
    }

    val appliedMsg = stringResource(R.string.ls_applied)
    val refMsg = stringResource(R.string.ls_reference_saved)

    Column(Modifier.fillMaxSize()) {
        WTopBar(stringResource(R.string.ls_title), back, stringResource(com.wdesign.ds.R.string.ds_back)) {
            IconButton(onClick = { history.undo()?.let { spec = it; version++ } }, enabled = history.canUndo) {
                Icon(WIcons.Back, stringResource(com.wdesign.ds.R.string.ds_undo), tint = if (history.canUndo) W.colors.text else W.colors.outline)
            }
            IconButton(onClick = { history.redo()?.let { spec = it; version++ } }, enabled = history.canRedo) {
                Icon(WIcons.ChevronRight, stringResource(com.wdesign.ds.R.string.ds_redo), tint = if (history.canRedo) W.colors.text else W.colors.outline)
            }
        }
        val previewBox: @Composable (Modifier) -> Unit = { mod ->
            Box(mod.padding(12.dp), contentAlignment = Alignment.Center) {
                val bmp = preview
                Box(
                    Modifier.fillMaxHeight().aspectRatio(pw.toFloat() / ph).clip(RoundedCornerShape(24.dp)).background(W.colors.raised2)
                        .border(3.dp, W.colors.outline, RoundedCornerShape(24.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (bmp != null) Image(bmp.asImageBitmap(), stringResource(R.string.ls_preview_cd), Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else CircularProgressIndicator(color = W.colors.muted)
                }
            }
        }
        val panel: @Composable (Modifier) -> Unit = { mod ->
            Column(mod.padding(horizontal = 16.dp)) {
                SegmentedControl(Tab.entries, tab, label = {
                    ctx.getString(when (it) { Tab.TEMPLATES -> R.string.ls_tab_templates; Tab.CLOCK -> R.string.ls_tab_clock; Tab.DATE -> R.string.ls_tab_date; Tab.DECOR -> R.string.ls_tab_decor; Tab.APPLY -> R.string.ls_tab_apply })
                }, onSelect = { tab = it }, modifier = Modifier.fillMaxWidth())
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when (tab) {
                        Tab.TEMPLATES -> TemplatesPanel(spec) { style -> edit { s -> s.copy(lockScreen = LockTemplates.build(style).let { t -> if (style == LockStyle.AMOLED) t else t.copy(wallpaper = s.lockScreen.wallpaper) }) } }
                        Tab.CLOCK -> ClockPanel(spec, ::edit)
                        Tab.DATE -> DatePanel(spec, ::edit)
                        Tab.DECOR -> DecorPanel(spec, selectedEl, { selectedEl = it }, ::edit, screen)
                        Tab.APPLY -> ApplyPanel(spec, busy, go, initial.id,
                            onApply = {
                                scope.launch {
                                    busy = true
                                    val saved = store.save(initial.copy(spec = spec))
                                    val r = WD.services.wallpapers.apply(saved, assets, WallpaperTarget.LOCK, screen.first, screen.second)
                                    busy = false
                                    snackbar.showSnackbar(r.fold({ appliedMsg }, { ctx.getString(com.wdesign.ds.R.string.ds_error_generic, it.message ?: "") }))
                                }
                            },
                            onReference = {
                                scope.launch {
                                    busy = true
                                    val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        runCatching {
                                            val bmp = LockScreenRenderer.render(spec, screen.first, screen.second, assets, LockRenderOptions(systemIs24h = is24))
                                            val values = ContentValues().apply {
                                                put(MediaStore.Images.Media.DISPLAY_NAME, "wdesign-lock-reference-${System.currentTimeMillis()}.png")
                                                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                                                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/W Design")
                                            }
                                            val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
                                            ctx.contentResolver.openOutputStream(uri)!!.use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                                        }.isSuccess
                                    }
                                    busy = false
                                    snackbar.showSnackbar(if (ok) refMsg else ctx.getString(com.wdesign.ds.R.string.ds_error_generic, "MediaStore"))
                                }
                            },
                        )
                    }
                    Spacer(Modifier.navigationBarsPadding())
                }
            }
        }
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                previewBox(Modifier.weight(0.45f).fillMaxHeight())
                panel(Modifier.weight(0.55f).fillMaxHeight())
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                previewBox(Modifier.fillMaxWidth().weight(0.46f))
                panel(Modifier.fillMaxWidth().weight(0.54f))
            }
        }
    }
}

@Composable
private fun TemplatesPanel(spec: ThemeSpec, onPick: (LockStyle) -> Unit) {
    for (row in LockStyle.entries.chunked(2)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (s in row) WChip(styleName(s), spec.lockScreen.style == s, { onPick(s) }, Modifier.weight(1f))
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun ClockPanel(spec: ThemeSpec, edit: ((ThemeSpec) -> ThemeSpec) -> Unit) {
    val c = spec.lockScreen.clock
    val alignLabels = TextAlign.entries.associateWith { alignName(it) }
    val formatLabels = ClockFormat.entries.associateWith { formatName(it) }
    fun set(f: (com.wdesign.core.model.ClockSpec) -> com.wdesign.core.model.ClockSpec) = edit { s -> s.copy(lockScreen = s.lockScreen.copy(clock = f(s.lockScreen.clock))) }
    Text(stringResource(R.string.ls_render), style = W.type.label, color = W.colors.text)
    ChipRow { for (r in ClockRender.entries) WChip(renderName(r), c.render == r, { set { it.copy(render = r) } }) }
    Text(stringResource(R.string.ls_font), style = W.type.label, color = W.colors.text)
    ChipRow { for (f in SystemFont.entries) WChip(f.family, c.font == f, { set { it.copy(font = f, weight = f.weight) } }) }
    Text(stringResource(R.string.ls_font_note), style = W.type.caption, color = W.colors.muted)
    if (c.font.family == "sans-serif") WSliderRow(stringResource(R.string.ls_weight), c.weight.toFloat(), 100f..900f, "${c.weight}", { v -> set { it.copy(weight = ((v / 100).roundToInt() * 100).coerceIn(100, 900)) } })
    WSliderRow(stringResource(R.string.ls_size), c.sizeSp, 40f..180f, "${c.sizeSp.roundToInt()} sp", { v -> set { it.copy(sizeSp = v.roundToInt().toFloat()) } })
    WSliderRow(stringResource(R.string.ls_top), c.y, 0.03f..0.7f, "${(c.y * 100).roundToInt()}%", { v -> set { it.copy(y = (v * 200).roundToInt() / 200f) } })
    Text(stringResource(R.string.ls_align), style = W.type.label, color = W.colors.text)
    SegmentedControl(TextAlign.entries, c.align, label = { alignLabels.getValue(it) }, onSelect = { a ->
        set { it.copy(align = a, x = when (a) { TextAlign.START -> 0.08f; TextAlign.CENTER -> 0.5f; TextAlign.END -> 0.92f }) }
    }, modifier = Modifier.fillMaxWidth())
    Text(stringResource(R.string.ls_format), style = W.type.label, color = W.colors.text)
    SegmentedControl(ClockFormat.entries, c.format, label = { formatLabels.getValue(it) }, onSelect = { f -> set { it.copy(format = f) } }, modifier = Modifier.fillMaxWidth())
    Text(stringResource(R.string.ls_color), style = W.type.label, color = W.colors.text)
    Swatches(listOf(ArgbColor.White, ArgbColor.Black) + spec.palette.all(), c.color) { col -> set { it.copy(color = col) } }
    WSwitchRow(stringResource(R.string.ls_gradient), !c.gradient.isNullOrEmpty(), { on -> set { it.copy(gradient = if (on) listOf(spec.palette.primary, spec.palette.accent) else null) } })
}


@Composable
private fun DatePanel(spec: ThemeSpec, edit: ((ThemeSpec) -> ThemeSpec) -> Unit) {
    val d = spec.lockScreen.date
    val alignLabels = TextAlign.entries.associateWith { alignName(it) }
    fun set(f: (com.wdesign.core.model.DateSpec) -> com.wdesign.core.model.DateSpec) = edit { s -> s.copy(lockScreen = s.lockScreen.copy(date = f(s.lockScreen.date))) }
    WSwitchRow(stringResource(R.string.ls_show_date), d.visible, { v -> set { it.copy(visible = v) } })
    Text(stringResource(R.string.ls_pattern), style = W.type.label, color = W.colors.text)
    val now = System.currentTimeMillis()
    for (p in MamlDatePattern.PRESETS) {
        WChip(LockScreenRenderer.dateText(d.copy(pattern = p), now), d.pattern == p, { set { it.copy(pattern = p) } }, Modifier.fillMaxWidth())
    }
    WSliderRow(stringResource(R.string.ls_size), d.sizeSp, 12f..32f, "${d.sizeSp.roundToInt()} sp", { v -> set { it.copy(sizeSp = v.roundToInt().toFloat()) } })
    WSliderRow(stringResource(R.string.ls_top), d.y, 0.02f..0.8f, "${(d.y * 100).roundToInt()}%", { v -> set { it.copy(y = (v * 200).roundToInt() / 200f) } })
    SegmentedControl(TextAlign.entries, d.align, label = { alignLabels.getValue(it) }, onSelect = { a ->
        set { it.copy(align = a, x = when (a) { TextAlign.START -> 0.08f; TextAlign.CENTER -> 0.5f; TextAlign.END -> 0.92f }) }
    }, modifier = Modifier.fillMaxWidth())
    Swatches(listOf(ArgbColor.White, ArgbColor.White.withAlpha(0.7f), ArgbColor.Black) + spec.palette.all(), d.color) { col -> set { it.copy(color = col) } }
}

@Composable
private fun DecorPanel(spec: ThemeSpec, selected: String?, select: (String?) -> Unit, edit: ((ThemeSpec) -> ThemeSpec) -> Unit, screen: Pair<Int, Int>) {
    val lock = spec.lockScreen
    val defaultText = stringResource(R.string.ls_text_default)
    fun add(e: CanvasElement) { edit { s -> s.copy(lockScreen = s.lockScreen.copy(elements = s.lockScreen.elements + e.copy(z = s.lockScreen.elements.size))) }; select(e.id) }
    fun update(id: String, f: (CanvasElement) -> CanvasElement) = edit { s -> s.copy(lockScreen = s.lockScreen.copy(elements = s.lockScreen.elements.map { if (it.id == id) f(it) else it })) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WButton(stringResource(R.string.ls_add_glass), { add(CanvasElement(Ids.next("e"), ElementType.GLASS_PANEL, 0.08f, 0.32f, 0.84f, 0.12f, props = mapOf(Props.CORNER to "28", Props.BLUR to "24", Props.TINT_ALPHA to "0.16"))) }, kind = WButtonKind.SECONDARY, modifier = Modifier.weight(1f))
        WButton(stringResource(R.string.ls_add_shape), { add(CanvasElement(Ids.next("e"), ElementType.SHAPE, 0.4f, 0.3f, 0.2f, 0.09f, props = mapOf(Props.SHAPE to "circle", Props.FILL to spec.palette.accent.withAlpha(0.8f).toHex()))) }, kind = WButtonKind.SECONDARY, modifier = Modifier.weight(1f))
        WButton(stringResource(R.string.ls_add_text), { add(CanvasElement(Ids.next("e"), ElementType.TEXT, 0.08f, 0.3f, 0.84f, 0.05f, props = mapOf(Props.TEXT to defaultText, Props.FONT_SIZE to "20", Props.COLOR to "#FFFFFFFF"))) }, kind = WButtonKind.SECONDARY, modifier = Modifier.weight(1f))
    }
    SectionHeader(stringResource(R.string.ls_layers))
    if (lock.elements.isEmpty()) Text(stringResource(R.string.ls_no_layers), style = W.type.body, color = W.colors.muted)
    for (e in lock.elements.sortedByDescending { it.z }) {
        val sel = e.id == selected
        WCard(Modifier.fillMaxWidth(), onClick = { select(if (sel) null else e.id) }, padding = 12.dp) {
            Column {
                Text(elementName(e.type) + (e.prop(Props.TEXT)?.let { " · $it" } ?: ""), style = W.type.label, color = if (sel) W.colors.accent else W.colors.text)
                if (sel) {
                    WSliderRow(stringResource(R.string.ls_top), e.y, 0f..0.95f, "${(e.y * 100).roundToInt()}%", { v -> update(e.id) { it.copy(y = v.coerceAtMost(1f - it.h)) } })
                    WSliderRow(stringResource(R.string.ls_left), e.x, 0f..0.95f, "${(e.x * 100).roundToInt()}%", { v -> update(e.id) { it.copy(x = v.coerceAtMost(1f - it.w)) } })
                    WSliderRow(stringResource(R.string.ls_width), e.w, 0.05f..1f, "${(e.w * 100).roundToInt()}%", { v -> update(e.id) { it.copy(w = v.coerceAtMost(1f - it.x)) } })
                    WSliderRow(stringResource(R.string.ls_height), e.h, 0.01f..0.6f, "${(e.h * 100).roundToInt()}%", { v -> update(e.id) { it.copy(h = v.coerceAtMost(1f - it.y)) } })
                    WSliderRow(stringResource(R.string.ls_opacity), e.opacity, 0.1f..1f, "${(e.opacity * 100).roundToInt()}%", { v -> update(e.id) { it.copy(opacity = v) } })
                    if (e.type == ElementType.TEXT) {
                        OutlinedTextField(e.prop(Props.TEXT).orEmpty(), { t -> update(e.id) { it.withProp(Props.TEXT, t.take(60)) } }, label = { Text(stringResource(R.string.ls_text_value)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                    if (e.type == ElementType.SHAPE || e.type == ElementType.TEXT) {
                        val key = if (e.type == ElementType.SHAPE) Props.FILL else Props.COLOR
                        Swatches(listOf(ArgbColor.White, ArgbColor.White.withAlpha(0.3f), ArgbColor.Black) + spec.palette.all(), e.color(key, ArgbColor.White)) { col -> update(e.id) { it.withProp(key, col.toHex()) } }
                    }
                    WButton(stringResource(R.string.ls_delete), {
                        edit { s -> s.copy(lockScreen = s.lockScreen.copy(elements = s.lockScreen.elements.filter { it.id != e.id })) }; select(null)
                    }, kind = WButtonKind.DANGER, icon = WIcons.Trash)
                }
            }
        }
    }
    WSwitchRow(stringResource(R.string.ls_fod_ghost), lock.showFodGhost, { v -> edit { s -> s.copy(lockScreen = s.lockScreen.copy(showFodGhost = v)) } })
    WSwitchRow(stringResource(R.string.ls_notif_ghost), lock.showNotificationGhost, { v -> edit { s -> s.copy(lockScreen = s.lockScreen.copy(showNotificationGhost = v)) } })
    val aspect = screen.second.toFloat() / screen.first
    val overlap = lock.elements.count { !it.hidden && it.type != ElementType.WIDGET_REF && it.overlapsCircle(lock.fodArea.x, lock.fodArea.y, lock.fodArea.r, aspect) }
    if (overlap > 0) Text(stringResource(R.string.ls_fod_warning, overlap), style = W.type.caption, color = W.colors.warning)
}

@Composable
private fun ApplyPart(title: String, body: String, status: SupportStatus, badge: String, extra: (@Composable () -> Unit)? = null) {
    WCard(Modifier.fillMaxWidth(), padding = 14.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SupportBadge(status, badge)
            Text(title, style = W.type.label, color = W.colors.text)
            Text(body, style = W.type.caption, color = W.colors.muted)
            extra?.invoke()
        }
    }
}

@Composable
private fun ApplyPanel(spec: ThemeSpec, busy: Boolean, go: (String) -> Unit, projectId: String, onApply: () -> Unit, onReference: () -> Unit) {
    val ctx = LocalContext.current
    var xiaomi by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) { xiaomi = WD.services.snapshot().isXiaomiFamily }
    val c = spec.lockScreen.clock
    Text(stringResource(R.string.ls_apply_intro), style = W.type.body, color = W.colors.text)
    ApplyPart(stringResource(R.string.ls_part_wall), stringResource(R.string.ls_part_wall_body), SupportStatus.SUPPORTED, stringResource(R.string.ls_badge_app)) {
        WButton(stringResource(R.string.ls_apply_wall), onApply, loading = busy, enabled = !busy, icon = WIcons.Lock, modifier = Modifier.fillMaxWidth())
    }
    if (xiaomi == true) {
        ApplyPart(stringResource(R.string.ls_part_clock), stringResource(R.string.ls_part_clock_body), SupportStatus.REQUIRES_ACTION, stringResource(R.string.ls_badge_system)) {
            Text(stringResource(R.string.ls_targets, renderName(c.render), c.sizeSp.roundToInt(), (c.y * 100).roundToInt(), alignName(c.align)), style = W.type.mono, color = W.colors.text)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WButton(stringResource(R.string.ls_open_editor), { XiaomiIntents.wallpaperSettings(ctx) }, kind = WButtonKind.SECONDARY, modifier = Modifier.weight(1f))
                if (Build.VERSION.SDK_INT >= 29) WButton(stringResource(R.string.ls_reference), onReference, kind = WButtonKind.GHOST, enabled = !busy, modifier = Modifier.weight(1f))
            }
        }
        ApplyPart(stringResource(R.string.ls_part_mtz), stringResource(R.string.ls_part_mtz_body), SupportStatus.PARTIAL, stringResource(R.string.ls_badge_mtz)) {
            WButton(stringResource(R.string.ls_export_mtz), { go(Routes.mtz(projectId)) }, kind = WButtonKind.SECONDARY, icon = WIcons.Package)
        }
    } else if (xiaomi == false) {
        ApplyPart(stringResource(R.string.ls_part_clock), stringResource(R.string.ls_part_clock_other), SupportStatus.UNSUPPORTED, stringResource(R.string.ls_badge_none)) {
            if (Build.VERSION.SDK_INT >= 29) WButton(stringResource(R.string.ls_reference), onReference, kind = WButtonKind.GHOST, enabled = !busy)
        }
        ApplyPart(stringResource(R.string.ls_part_mtz), stringResource(R.string.ls_part_mtz_body), SupportStatus.UNSUPPORTED, stringResource(R.string.ls_badge_none)) {
            WButton(stringResource(R.string.ls_export_mtz), { go(Routes.mtz(projectId)) }, kind = WButtonKind.SECONDARY, icon = WIcons.Package)
        }
    }
    ApplyPart(stringResource(R.string.ls_part_preview), stringResource(R.string.ls_part_preview_body), SupportStatus.PARTIAL, stringResource(R.string.ls_badge_preview))
    Text(stringResource(R.string.ls_never), style = W.type.caption, color = W.colors.muted)
}
