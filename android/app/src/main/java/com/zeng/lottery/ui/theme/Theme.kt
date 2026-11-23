package com.zeng.lottery.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * 主题自适应金色强调色：暗黑主题使用亮玄金 (#FFB300)，浅色主题自动切换高对比度深赤金 (#8C5200)。
 */
val LocalAccentGold = compositionLocalOf { FortuneGold }

private val DarkColorScheme = darkColorScheme(
    primary = FortuneGold,
    onPrimary = Color(0xFF12141B),
    primaryContainer = FortuneGoldLight.copy(alpha = 0.2f),
    onPrimaryContainer = FortuneGoldLight,
    secondary = ImperialRedBright,
    onSecondary = Color(0xFF1A0707),
    secondaryContainer = ImperialRedDark.copy(alpha = 0.35f),
    onSecondaryContainer = Color(0xFFFFCDD2),
    tertiary = MysticIndigo,
    background = DarkBackground,
    onBackground = Color(0xFFECEFF1),
    surface = DarkSurface,
    onSurface = Color(0xFFF5F5F5),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFB8C4CE),
    outline = DarkOutline
)

private val LightColorScheme = lightColorScheme(
    primary = ImperialRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD6),
    onPrimaryContainer = ImperialRedDark,
    secondary = FortuneGoldDeep,
    onSecondary = Color.White,
    secondaryContainer = FortuneGoldLight.copy(alpha = 0.45f),
    onSecondaryContainer = Color(0xFF4E342E),
    tertiary = MysticIndigo,
    background = LightBackground,
    onBackground = Color(0xFF1E1B18),
    surface = LightSurface,
    onSurface = Color(0xFF1E1B18),
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = Color(0xFF5A5248),
    outline = LightOutline
)

@Composable
fun BaziLotteryTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    val accentGold = if (darkTheme) FortuneGold else FortuneGoldDeep
    CompositionLocalProvider(LocalAccentGold provides accentGold) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
