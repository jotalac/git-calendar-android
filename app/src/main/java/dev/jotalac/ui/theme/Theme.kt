package dev.jotalac.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import dev.jotalac.data.ColorSource
import dev.jotalac.data.ThemeMode

/** Port of the web client's dark theme (themes.css) as a Material color scheme. */
private val GitDarkScheme = darkColorScheme(
    primary = DarkGit,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF6C1B14),
    onPrimaryContainer = Color(0xFFFFDAD5),
    secondary = DarkTextSoft,
    onSecondary = DarkBg,
    secondaryContainer = DarkBtnStrong,
    onSecondaryContainer = DarkText,
    tertiary = Color(0xFF22C55E),
    background = DarkBg,
    onBackground = DarkText,
    surface = DarkBg,
    onSurface = DarkText,
    surfaceVariant = DarkBtn,
    onSurfaceVariant = DarkTextSoft,
    surfaceContainerLowest = Color(0xFF141414),
    surfaceContainerLow = DarkSidebar,
    surfaceContainer = DarkSidebar,
    surfaceContainerHigh = DarkBtn,
    surfaceContainerHighest = DarkBtnStrong,
    outline = Color(0xFF5A5A5A),
    outlineVariant = DarkGrid,
)

/** Port of the web client's light theme (themes.css) as a Material color scheme. */
private val GitLightScheme = lightColorScheme(
    primary = LightGit,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD5),
    onPrimaryContainer = Color(0xFF410001),
    secondary = Color(0xFF4A4A4A),
    onSecondary = Color.White,
    secondaryContainer = LightBtn,
    onSecondaryContainer = LightText,
    tertiary = Color(0xFF15803D),
    background = LightBg,
    onBackground = LightText,
    surface = LightBg,
    onSurface = LightText,
    surfaceVariant = LightBtnStrong,
    onSurfaceVariant = LightTextSoft,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = LightSidebar,
    surfaceContainer = LightSidebar,
    surfaceContainerHigh = LightBtnStrong,
    surfaceContainerHighest = LightBtn,
    outline = Color(0xFF8A8A8A),
    outlineVariant = LightGrid,
)

@Composable
fun GitCalendarTheme(
    themeMode: ThemeMode = ThemeMode.System,
    colorSource: ColorSource = ColorSource.Dynamic,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val context = LocalContext.current
    val colorScheme = when (colorSource) {
        ColorSource.Dynamic -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        ColorSource.Git -> if (dark) GitDarkScheme else GitLightScheme
    }
    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
