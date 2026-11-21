package com.zeng.lottery.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = FortuneGold,
    onPrimary = DarkBackground,
    primaryContainer = FortuneGoldLight.copy(alpha = 0.2f),
    onPrimaryContainer = FortuneGoldLight,
    secondary = ImperialRed,
    onSecondary = Color.White,
    secondaryContainer = ImperialRedDark.copy(alpha = 0.3f),
    onSecondaryContainer = Color(0xFFFFCDD2),
    tertiary = MysticIndigo,
    background = DarkBackground,
    onBackground = Color(0xFFECEFF1),
    surface = DarkSurface,
    onSurface = Color(0xFFF5F5F5),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFB0BEC5),
    outline = DarkOutline
)

private val LightColorScheme = lightColorScheme(
    primary = ImperialRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFCDD2),
    onPrimaryContainer = ImperialRedDark,
    secondary = FortuneGold,
    onSecondary = DarkBackground,
    secondaryContainer = FortuneGoldLight.copy(alpha = 0.3f),
    onSecondaryContainer = Color(0xFF5D4037),
    tertiary = MysticIndigo,
    background = LightBackground,
    onBackground = Color(0xFF212121),
    surface = LightSurface,
    onSurface = Color(0xFF212121),
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = Color(0xFF546E7A),
    outline = LightOutline
)

@Composable
fun BaziLotteryTheme(
    darkTheme: Boolean = true, // Default to mystic oriental dark mode
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = colorScheme.background.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
