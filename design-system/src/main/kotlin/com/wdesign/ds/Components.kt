package com.wdesign.ds

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wdesign.core.model.SupportStatus
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeChild

/**
 * Glass for floating layers only (bars, sheets, toolbars). Verified Haze 1.1.1 API:
 * Modifier.hazeChild(state) { … } with HazeChildScope vars.
 * - backgroundColor is MANDATORY in 1.1.1: HazeChildNode.kt:327 does
 *   require(bg.isSpecified) on every draw, resolving scope → style → LocalHazeStyle. Leaving it
 *   unset crashed every screen with the glass bar. It must be an opaque surface colour.
 * - HazeDefaults.blurEnabled() is true only on API 32+, so below that (and with Reduce
 *   transparency) Haze draws [WColors.glassFallback] instead of blurring.
 */
fun Modifier.glass(shape: Shape): Modifier = composed {
    val t = LocalWTokens.current
    val c = t.colors
    val state = LocalHazeState.current
    this
        .clip(shape)
        .hazeChild(state = state) {
            backgroundColor = c.surface
            blurRadius = 24.dp
            tints = listOf(HazeTint(c.glassFill))
            noiseFactor = 0.08f
            fallbackTint = HazeTint(c.glassFallback)
            blurEnabled = HazeDefaults.blurEnabled() && !t.reduceTransparency
        }
        .border(1.dp, c.glassHairline, shape)
}

/** 2 dp accent focus ring at 2 dp offset, shown only for keyboard/D-pad focus. */
fun Modifier.wdFocusRing(interaction: MutableInteractionSource, shape: Shape): Modifier = composed {
    val focused by interaction.collectIsFocusedAsState()
    if (focused) this.border(2.dp, W.colors.accent, shape).padding(2.dp) else this
}

/** 0.98 press scale for primary tiles; disabled under reduce motion. */
fun Modifier.pressScale(interaction: MutableInteractionSource): Modifier = composed {
    val pressed by interaction.collectIsPressedAsState()
    val reduce = LocalWTokens.current.reduceMotion
    val s by animateFloatAsState(if (pressed && !reduce) 0.98f else 1f, tween(120), label = "press")
    this.scale(s)
}

enum class WButtonKind { PRIMARY, SECONDARY, GHOST, DANGER }

@Composable
fun WButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: WButtonKind = WButtonKind.PRIMARY,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val c = W.colors
    val shape = RoundedCornerShape(W.radii.control)
    val interaction = remember { MutableInteractionSource() }
    val (bg, fg, border) = when (kind) {
        WButtonKind.PRIMARY -> Triple(c.primaryFill, c.onPrimary, null)
        WButtonKind.SECONDARY -> Triple(c.raised2, c.text, BorderStroke(1.dp, c.outline))
        WButtonKind.GHOST -> Triple(Color.Transparent, c.text, null)
        WButtonKind.DANGER -> Triple(c.danger.copy(alpha = 0.14f), c.danger, BorderStroke(1.dp, c.danger.copy(alpha = 0.5f)))
    }
    Row(
        modifier
            .wdFocusRing(interaction, shape)
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(if (enabled) bg else bg.copy(alpha = 0.4f))
            .then(if (border != null) Modifier.border(border, shape) else Modifier)
            .clickable(interaction, indication = androidx.compose.material3.ripple(), enabled = enabled && !loading, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (loading) CircularProgressIndicator(Modifier.size(18.dp), color = fg, strokeWidth = 2.dp)
        else if (icon != null) Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp))
        if (loading || icon != null) Spacer(Modifier.width(8.dp))
        Text(text, style = W.type.label, color = if (enabled) fg else fg.copy(alpha = 0.6f), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun WCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, padding: Dp = 16.dp, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(W.radii.card)
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .wdFocusRing(interaction, shape)
            .then(if (onClick != null) Modifier.pressScale(interaction) else Modifier)
            .clip(shape)
            .background(W.colors.raised)
            .border(1.dp, W.colors.outline.copy(alpha = 0.6f), shape)
            .then(if (onClick != null) Modifier.clickable(interaction, androidx.compose.material3.ripple(), role = Role.Button, onClick = onClick) else Modifier)
            .padding(padding),
    ) { content() }
}

@Composable
fun WChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = W.colors
    val shape = CircleShape
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .wdFocusRing(interaction, shape)
            .heightIn(min = 40.dp)
            .clip(shape)
            .background(if (selected) c.accent else c.raised2)
            .selectable(selected, interaction, androidx.compose.material3.ripple(), role = Role.Tab, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = W.type.label, color = if (selected) c.onAccent else c.text, maxLines = 1) }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = W.type.headline, color = W.colors.text, modifier = Modifier.weight(1f).semantics { heading() })
        action?.invoke()
    }
}

@Composable
fun SupportBadge(status: SupportStatus, label: String, modifier: Modifier = Modifier) {
    val c = W.colors
    val color = when (status) {
        SupportStatus.SUPPORTED -> c.success
        SupportStatus.PARTIAL -> c.warning
        SupportStatus.REQUIRES_ACTION -> c.accent
        SupportStatus.UNSUPPORTED -> c.danger
    }
    Row(
        modifier.clip(CircleShape).background(color.copy(alpha = 0.14f)).border(1.dp, color.copy(alpha = 0.45f), CircleShape).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = W.type.caption, color = c.text, maxLines = 1)
    }
}

@Composable
fun WSwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, subtitle: String? = null) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp)
            .toggleable(checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = W.type.body, color = W.colors.text)
            if (subtitle != null) Text(subtitle, style = W.type.caption, color = W.colors.muted)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked, onCheckedChange = null, colors = SwitchDefaults.colors(checkedTrackColor = W.colors.accent, checkedThumbColor = W.colors.onAccent))
    }
}

@Composable
fun WSliderRow(title: String, value: Float, range: ClosedFloatingPointRange<Float>, valueText: String, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = W.type.label, color = W.colors.text, modifier = Modifier.weight(1f))
            Text(valueText, style = W.type.mono, color = W.colors.muted)
        }
        Slider(value, onChange, valueRange = range, colors = SliderDefaults.colors(thumbColor = W.colors.accent, activeTrackColor = W.colors.accent, inactiveTrackColor = W.colors.outline))
    }
}

@Composable
fun <T> SegmentedControl(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val c = W.colors
    Row(modifier.clip(RoundedCornerShape(W.radii.control)).background(c.raised2).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (o in options) {
            val sel = o == selected
            Box(
                Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(10.dp)).background(if (sel) c.raised else Color.Transparent)
                    .selectable(sel, role = Role.RadioButton) { onSelect(o) }.padding(horizontal = 8.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) { Text(label(o), style = W.type.label, color = if (sel) c.text else c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

data class BarItem(val route: String, val label: String, val icon: ImageVector)

/** Floating glass bottom bar with 5 tabs (64 dp + navigation inset). */
@Composable
fun GlassBottomBar(items: List<BarItem>, current: String?, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = W.colors
    Row(
        modifier.fillMaxWidth().glass(RoundedCornerShape(topStart = W.radii.sheet, topEnd = W.radii.sheet)).navigationBarsPadding().heightIn(min = 64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (item in items) {
            val sel = current == item.route
            Column(
                Modifier.weight(1f).heightIn(min = 64.dp).selectable(sel, role = Role.Tab) { onSelect(item.route) }.padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                Icon(item.icon, contentDescription = null, tint = if (sel) c.accent else c.muted, modifier = Modifier.size(24.dp))
                Text(item.label, style = W.type.caption, color = if (sel) c.accent else c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun WTopBar(title: String, onBack: (() -> Unit)?, backLabel: String, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().glass(RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)).statusBarsPadding().heightIn(min = 56.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) { Icon(WIcons.Back, contentDescription = backLabel, tint = W.colors.text) }
        else Spacer(Modifier.width(16.dp))
        Text(title, style = W.type.headline, color = W.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).semantics { heading() })
        actions()
    }
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(WIcons.Layers, null, tint = W.colors.muted, modifier = Modifier.size(40.dp))
        Spacer(Modifier.size(12.dp))
        Text(title, style = W.type.headline, color = W.colors.text)
        Spacer(Modifier.size(4.dp))
        Text(body, style = W.type.body, color = W.colors.muted)
        if (action != null) { Spacer(Modifier.size(16.dp)); action() }
    }
}

/** A phone outline around a rendered preview (aspect from the bitmap). */
@Composable
fun DeviceFrame(bitmap: Bitmap?, contentDescription: String?, modifier: Modifier = Modifier, aspect: Float = 9f / 19.5f) {
    val shape = RoundedCornerShape(26.dp)
    Box(
        modifier.aspectRatio(bitmap?.let { it.width.toFloat() / it.height } ?: aspect).clip(shape).background(W.colors.raised2)
            .border(3.dp, W.colors.outline, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap == null) CircularProgressIndicator(Modifier.size(24.dp), color = W.colors.muted, strokeWidth = 2.dp)
        else Image(bitmap.asImageBitmap(), contentDescription, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}


@Composable
fun ToolRow(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    WCard(modifier.fillMaxWidth(), onClick = onClick, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(W.colors.raised2), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = W.colors.accent, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = W.type.label, color = W.colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = W.type.caption, color = W.colors.muted, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            if (trailing != null) trailing() else Icon(WIcons.ChevronRight, null, tint = W.colors.muted, modifier = Modifier.size(20.dp))
        }
    }
}

/** Mono key/value line used for device specs, versions and hex values. */
@Composable
fun SpecLine(key: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(key, style = W.type.label, color = W.colors.muted, modifier = Modifier.weight(0.45f))
        Text(value, style = W.type.mono, color = W.colors.text, modifier = Modifier.weight(0.55f))
    }
}

val ScreenPadding = PaddingValues(horizontal = 20.dp)
val ContentMaxWidth = 720.dp
fun Modifier.contentWidth() = this.widthIn(max = ContentMaxWidth)
