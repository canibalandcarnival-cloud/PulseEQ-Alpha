package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val StudioDarkColorScheme = darkColorScheme(
    primary = SignalCoral,
    onPrimary = TextPrimary,
    primaryContainer = SignalCoralSoft,
    onPrimaryContainer = SignalCoralBright,
    secondary = AnalyzerCyan,
    onSecondary = StudioBackground,
    secondaryContainer = AnalyzerCyanSoft,
    onSecondaryContainer = AnalyzerCyan,
    tertiary = RouteEmerald,
    onTertiary = StudioBackground,
    tertiaryContainer = RouteEmeraldSoft,
    onTertiaryContainer = RouteEmerald,
    background = StudioBackground,
    onBackground = TextPrimary,
    surface = StudioSurface,
    onSurface = TextPrimary,
    surfaceVariant = StudioSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    surfaceContainerHigh = StudioSurfaceHighlight,
    outline = StudioBorder,
    outlineVariant = GridLineSubtle
)

@Composable
fun PulseEqTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = StudioDarkColorScheme,
        typography = PulseEqTypography,
        content = content
    )
}
