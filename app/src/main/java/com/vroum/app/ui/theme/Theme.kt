package com.vroum.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val VroumDarkColorScheme = darkColorScheme(
    primary = IgnitionAmber,
    onPrimary = Color(0xFF0D1117),
    primaryContainer = Color(0xFF3A1E12),
    onPrimaryContainer = WarmSpark,
    secondary = AcousticCyan,
    onSecondary = Color(0xFF082026),
    secondaryContainer = Color(0xFF102A33),
    onSecondaryContainer = AcousticCyan,
    tertiary = HealthyEmerald,
    onTertiary = Color(0xFF062016),
    background = VoidObsidian,
    onBackground = CrispIvory,
    surface = CarbonSurface,
    onSurface = CrispIvory,
    surfaceVariant = SubtleSurfaceVariant,
    onSurfaceVariant = SteelMuted,
    surfaceContainer = CarbonSurface,
    surfaceContainerHigh = ElevatedPanel,
    outline = HairlineBorder,
    outlineVariant = Color(0xFF21262D),
    error = AlertRed,
    onError = Color.White,
    errorContainer = Color(0xFF3B1518),
    onErrorContainer = Color(0xFFFFB4AB)
)

val VroumShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun VroumTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = false
                    isAppearanceLightNavigationBars = false
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = VroumDarkColorScheme,
        typography = VroumTypography,
        shapes = VroumShapes,
        content = content
    )
}
