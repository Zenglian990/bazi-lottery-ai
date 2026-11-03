package com.zeng.lottery.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeng.lottery.ui.theme.BlueBallBase
import com.zeng.lottery.ui.theme.BlueBallHighlight
import com.zeng.lottery.ui.theme.BlueBallShadow
import com.zeng.lottery.ui.theme.ElementEarth
import com.zeng.lottery.ui.theme.ElementFire
import com.zeng.lottery.ui.theme.ElementMetal
import com.zeng.lottery.ui.theme.ElementWater
import com.zeng.lottery.ui.theme.ElementWood
import com.zeng.lottery.ui.theme.GoldBallBase
import com.zeng.lottery.ui.theme.GoldBallHighlight
import com.zeng.lottery.ui.theme.GoldBallShadow
import com.zeng.lottery.ui.theme.RedBallBase
import com.zeng.lottery.ui.theme.RedBallHighlight
import com.zeng.lottery.ui.theme.RedBallShadow
import kotlinx.coroutines.delay

enum class BallColorType {
    RED,
    BLUE,
    GOLD
}

@Composable
fun LotteryBall(
    number: String,
    colorType: BallColorType = BallColorType.RED,
    wuxingElement: String? = null,
    size: Dp = 44.dp,
    delayMs: Long = 0L,
    animate: Boolean = true,
    modifier: Modifier = Modifier
) {
    val scaleAnim = remember { Animatable(if (animate) 0.2f else 1f) }
    val offsetYAnim = remember { Animatable(if (animate) -40f else 0f) }

    if (animate) {
        LaunchedEffect(number, colorType) {
            if (delayMs > 0) {
                delay(delayMs)
            }
            scaleAnim.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
            offsetYAnim.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
    }

    val (highlight, base, shadow) = when (colorType) {
        BallColorType.BLUE -> Triple(BlueBallHighlight, BlueBallBase, BlueBallShadow)
        BallColorType.GOLD -> Triple(GoldBallHighlight, GoldBallBase, GoldBallShadow)
        BallColorType.RED -> Triple(RedBallHighlight, RedBallBase, RedBallShadow)
    }

    val elementColor = when (wuxingElement) {
        "金", "metal" -> ElementMetal
        "木", "wood" -> ElementWood
        "水", "water" -> ElementWater
        "火", "fire" -> ElementFire
        "土", "earth" -> ElementEarth
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .padding(horizontal = 3.dp, vertical = 2.dp)
            .graphicsLayer {
                scaleX = scaleAnim.value
                scaleY = scaleAnim.value
                translationY = offsetYAnim.value
            }
    ) {
        Box(
            modifier = Modifier.size(size),
            contentAlignment = Alignment.Center
        ) {
            // 3D Spherical Canvas with Off-Center Radial Gradient
            Canvas(modifier = Modifier.matchParentSize()) {
                val radius = size.toPx() / 2f
                val centerOffset = Offset(radius, radius)
                // Light source from top-left (35% into sphere)
                val lightSource = Offset(radius * 0.65f, radius * 0.65f)

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(highlight, base, shadow),
                        center = lightSource,
                        radius = radius * 1.15f
                    ),
                    radius = radius,
                    center = centerOffset
                )

                // Subtle rim specular arc
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.5f), Color.Transparent),
                        center = Offset(radius * 0.6f, radius * 0.6f),
                        radius = radius * 0.45f
                    ),
                    radius = radius * 0.35f,
                    center = Offset(radius * 0.65f, radius * 0.65f)
                )
            }

            // Bold Number in Center
            Text(
                text = number,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = (size.value * 0.42f).sp
                ),
                color = Color.White
            )
        }

        if (!wuxingElement.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = wuxingElement,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.sp
                ),
                color = elementColor
            )
        }
    }
}
