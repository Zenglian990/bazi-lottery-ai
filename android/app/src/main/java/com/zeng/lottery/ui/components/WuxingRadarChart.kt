package com.zeng.lottery.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeng.lottery.data.model.WuxingScores
import com.zeng.lottery.ui.theme.ElementEarth
import com.zeng.lottery.ui.theme.ElementFire
import com.zeng.lottery.ui.theme.ElementMetal
import com.zeng.lottery.ui.theme.ElementWater
import com.zeng.lottery.ui.theme.ElementWood
import com.zeng.lottery.ui.theme.FortuneGold
import com.zeng.lottery.ui.theme.ImperialRed
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun WuxingRadarChart(
    scores: WuxingScores,
    xiYongShen: List<String> = emptyList(),
    dayMaster: String = "",
    strength: String = "",
    modifier: Modifier = Modifier
) {
    val labels = listOf("金", "木", "水", "火", "土")
    val rawValues = listOf(scores.metal, scores.wood, scores.water, scores.fire, scores.earth)
    val colors = listOf(ElementMetal, ElementWood, ElementWater, ElementFire, ElementEarth)

    // Normalize to 0..100 scale for radar rendering
    val maxRaw = rawValues.maxOrNull()?.coerceAtLeast(30f) ?: 100f
    val normalized = rawValues.map { (it / maxRaw * 100f).coerceIn(10f, 100f) }

    val animProgress = remember { Animatable(0.05f) }
    LaunchedEffect(scores) {
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 850, easing = FastOutSlowInEasing)
        )
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "五行命盘能量分布",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (strength.isNotBlank()) {
                    Text(
                        text = "日主【$dayMaster】· $strength",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = FortuneGold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Canvas Regular Pentagon Polar Radar
            Canvas(modifier = Modifier.size(230.dp)) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val radius = (size.minDimension / 2f) * 0.76f

                // 1. Draw 5 concentric pentagonal grid rings (20%, 40%, 60%, 80%, 100%)
                for (step in 1..5) {
                    val r = radius * (step / 5f)
                    val bgPath = Path()
                    for (i in 0 until 5) {
                        val angle = -Math.PI / 2 + i * (2 * Math.PI / 5)
                        val px = cx + (r * cos(angle)).toFloat()
                        val py = cy + (r * sin(angle)).toFloat()
                        if (i == 0) bgPath.moveTo(px, py) else bgPath.lineTo(px, py)
                    }
                    bgPath.close()
                    drawPath(
                        path = bgPath,
                        color = Color.Gray.copy(alpha = 0.22f),
                        style = Stroke(width = 1.dp.toPx())
                    )
                }

                // 2. Draw 5 radial spokes from center to vertices
                for (i in 0 until 5) {
                    val angle = -Math.PI / 2 + i * (2 * Math.PI / 5)
                    val px = cx + (radius * cos(angle)).toFloat()
                    val py = cy + (radius * sin(angle)).toFloat()
                    drawLine(
                        color = Color.Gray.copy(alpha = 0.28f),
                        start = Offset(cx, cy),
                        end = Offset(px, py),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                // 3. Draw animated Wuxing energy polygon
                val dataPath = Path()
                val dataPoints = mutableListOf<Offset>()
                for (i in 0 until 5) {
                    val angle = -Math.PI / 2 + i * (2 * Math.PI / 5)
                    val r = radius * (normalized[i] / 100f) * animProgress.value
                    val px = cx + (r * cos(angle)).toFloat()
                    val py = cy + (r * sin(angle)).toFloat()
                    dataPoints.add(Offset(px, py))
                    if (i == 0) dataPath.moveTo(px, py) else dataPath.lineTo(px, py)
                }
                dataPath.close()

                // Radial gradient fill (Gold & Crimson)
                drawPath(
                    path = dataPath,
                    brush = Brush.radialGradient(
                        colors = listOf(FortuneGold.copy(alpha = 0.65f), ImperialRed.copy(alpha = 0.25f)),
                        center = Offset(cx, cy),
                        radius = radius
                    )
                )

                // High-visibility contour stroke
                drawPath(
                    path = dataPath,
                    color = FortuneGold,
                    style = Stroke(width = 2.5.dp.toPx())
                )

                // 4. Vertex nodes
                dataPoints.forEachIndexed { i, pt ->
                    drawCircle(
                        color = colors[i],
                        radius = 5.dp.toPx(),
                        center = pt
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 2.dp.toPx(),
                        center = pt
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 5 Elements Score Chips Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                val normalizedXiYong = xiYongShen.map { com.zeng.lottery.data.local.BaziEngine.ELEMENT_ZH[it.lowercase()] ?: it }
                listOf("金", "木", "水", "火", "土").forEachIndexed { i, elem ->
                    val isXiYong = normalizedXiYong.contains(elem)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .background(
                                color = if (isXiYong) FortuneGold.copy(alpha = 0.15f) else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = elem + if (isXiYong) "★" else "",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors[i]
                        )
                        Text(
                            text = String.format("%.1f分", rawValues[i]),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
