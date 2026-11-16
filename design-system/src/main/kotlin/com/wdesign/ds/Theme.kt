@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.wdesign.ds

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.wdesign.core.model.AppearanceMode
import dev.chrisbanes.haze.HazeState

@Immutable
data class WColors(
    val isDark: Boolean,
    val surface: Color,
    val raised: Color,
    val raised2: Color,
    val glassFill: Color,
    val glassHairline: Color,
    val glassFallback: Color,
    val text: Color,
    val muted: Color,
    val outline: Color,
    val inputBorder: Color,
    val accent: Color,
    val onAccent: Color,
    val primaryFill: Color,
    val onPrimary: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
)

val DarkWColors = WColors(
    isDark = true,
    surface = Color(0xFF0D0F12), raised = Color(0xFF16191E), raised2 = Color(0xFF1E2228),
    glassFill = Color.White.copy(alpha = 0.07f), glassHairline = Color.White.copy(alpha = 0.14f), glassFallback = Color(0xEB1A1D22),
    text = Color(0xFFF1F2F4), muted = Color(0xFFA2A8B3), outline = Color(0xFF3A3F47), inputBorder = Color(0xFF6B717C),
    accent = Color(0xFF9CC9F0), onAccent = Color(0xFF0D0F12), primaryFill = Color(0xFFF1F2F4), onPrimary = Color(0xFF0D0F12),
    success = Color(0xFF6ED3A0), warning = Color(0xFFF0C35A), danger = Color(0xFFFF6B5E),
)

val LightWColors = WColors(
    isDark = false,
    surface = Color(0xFFF3F4F6), raised = Color(0xFFFFFFFF), raised2 = Color(0xFFE9EBEF),
    glassFill = Color.White.copy(alpha = 0.62f), glassHairline = Color(0xFF0D0F12).copy(alpha = 0.08f), glassFallback = Color(0xF5F7F8FA),
    text = Color(0xFF111317), muted = Color(0xFF565C66), outline = Color(0xFFD5D8DE), inputBorder = Color(0xFF7A808A),
    accent = Color(0xFF1F6FB2), onAccent = Color.White, primaryFill = Color(0xFF111317), onPrimary = Color(0xFFF3F4F6),
    success = Color(0xFF1E7F4F), warning = Color(0xFF8A6100), danger = Color(0xFFC2352B),
)

@Immutable
data class WSpacing(val xs: Dp = 4.dp, val s: Dp = 8.dp, val m: Dp = 12.dp, val l: Dp = 16.dp, val xl: Dp = 20.dp, val xxl: Dp = 24.dp, val x3: Dp = 32.dp, val x4: Dp = 40.dp, val x5: Dp = 56.dp, val screen: Dp = 20.dp)

@Immutable
data class WRadii(val sheet: Dp = 28.dp, val card: Dp = 20.dp, val control: Dp = 14.dp)

@Immutable
data class WTokens(
    val colors: WColors,
    val spacing: WSpacing = WSpacing(),
    val radii: WRadii = WRadii(),
    val reduceMotion: Boolean = false,
    val reduceTransparency: Boolean = false,
)

val LocalWTokens = staticCompositionLocalOf { WTokens(DarkWColors) }
val LocalHazeState = staticCompositionLocalOf { HazeState() }

object W {
    val colors: WColors @Composable get() = LocalWTokens.current.colors
    val spacing: WSpacing @Composable get() = LocalWTokens.current.spacing
    val radii: WRadii @Composable get() = LocalWTokens.current.radii
    val tokens: WTokens @Composable get() = LocalWTokens.current
    val type: WType @Composable get() = LocalWType.current
}

private fun onest(w: Int) = Font(R.font.onest, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
private fun mono(w: Int) = Font(R.font.jetbrains_mono, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))

val OnestFamily = FontFamily(onest(300), onest(400), onest(500), onest(600))
val MonoFamily = FontFamily(mono(400), mono(500))

@Immutable
data class WType(
    val display: TextStyle = TextStyle(fontFamily = OnestFamily, fontWeight = FontWeight(300), fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.02).em),
    val title: TextStyle = TextStyle(fontFamily = OnestFamily, fontWeight = FontWeight(600), fontSize = 24.sp, lineHeight = 30.sp),
    val headline: TextStyle = TextStyle(fontFamily = OnestFamily, fontWeight = FontWeight(600), fontSize = 20.sp, lineHeight = 26.sp),
    val body: TextStyle = TextStyle(fontFamily = OnestFamily, fontWeight = FontWeight(400), fontSize = 16.sp, lineHeight = 24.sp),
    val label: TextStyle = TextStyle(fontFamily = OnestFamily, fontWeight = FontWeight(500), fontSize = 14.sp, lineHeight = 20.sp),
    val caption: TextStyle = TextStyle(fontFamily = OnestFamily, fontWeight = FontWeight(400), fontSize = 12.sp, lineHeight = 16.sp),
    val mono: TextStyle = TextStyle(fontFamily = MonoFamily, fontWeight = FontWeight(500), fontSize = 13.sp, lineHeight = 18.sp, fontFeatureSettings = "tnum"),
)

val LocalWType = staticCompositionLocalOf { WType() }

/** True when the system animator scale is 0 (system "remove animations"). */
@Composable
fun systemReducedMotion(): Boolean {
    val ctx = LocalContext.current
    return remember { runCatching { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false) }
}

@Composable
fun WTheme(
    mode: AppearanceMode = AppearanceMode.DARK,
    reduceMotion: Boolean = false,
    reduceTransparency: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) { AppearanceMode.DARK -> true; AppearanceMode.LIGHT -> false; AppearanceMode.SYSTEM -> isSystemInDarkTheme() }
    val colors = if (dark) DarkWColors else LightWColors
    val tokens = WTokens(colors, reduceMotion = reduceMotion || systemReducedMotion(), reduceTransparency = reduceTransparency)
    val type = WType()
    val scheme = if (dark) darkColorScheme(
        primary = colors.accent, onPrimary = colors.onAccent, background = colors.surface, onBackground = colors.text,
        surface = colors.raised, onSurface = colors.text, surfaceVariant = colors.raised2, onSurfaceVariant = colors.muted,
        outline = colors.inputBorder, outlineVariant = colors.outline, error = colors.danger, surfaceContainer = colors.raised,
        surfaceContainerHigh = colors.raised2, surfaceContainerLow = colors.raised,
    ) else lightColorScheme(
        primary = colors.accent, onPrimary = colors.onAccent, background = colors.surface, onBackground = colors.text,
        surface = colors.raised, onSurface = colors.text, surfaceVariant = colors.raised2, onSurfaceVariant = colors.muted,
        outline = colors.inputBorder, outlineVariant = colors.outline, error = colors.danger, surfaceContainer = colors.raised,
        surfaceContainerHigh = colors.raised2, surfaceContainerLow = colors.raised,
    )
    val m3type = androidx.compose.material3.Typography(
        displaySmall = type.display, headlineMedium = type.title, headlineSmall = type.headline, titleLarge = type.headline,
        titleMedium = type.label.copy(fontSize = 16.sp), bodyLarge = type.body, bodyMedium = type.label.copy(fontWeight = FontWeight(400)),
        bodySmall = type.caption, labelLarge = type.label, labelMedium = type.caption.copy(fontWeight = FontWeight(500)), labelSmall = type.caption,
    )
    CompositionLocalProvider(LocalWTokens provides tokens, LocalWType provides type) {
        MaterialTheme(colorScheme = scheme, typography = m3type, content = content)
    }
}
