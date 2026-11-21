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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeng.lottery.data.local.BaziEngine
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
    strengthLevel: String? = null,
    modifier: Modifier = Modifier
) {
    val labels = listOf("金", "木", "水", "火", "土")
    val rawValues = listOf(scores.metal, scores.wood, scores.water, scores.fire, scores.earth)
    val colors = listOf(ElementMetal, ElementWood, ElementWater, ElementFire, ElementEarth)
    val normalizedXiYong = xiYongShen.map { BaziEngine.ELEMENT_ZH[it.lowercase()] ?: it }

    val maxRaw = rawValues.maxOrNull()?.coerceAtLeast(30f) ?: 100f
    val normalized = rawValues.map { (it / maxRaw * 100f).coerceIn(10f, 100f) }

    val animProgress = remember { Animatable(0.15f) }
    LaunchedEffect(scores) {
        animProgress.snapTo(0.15f)
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing)
        )
    }

    val textMeasurer = rememberTextMeasurer()
    val radarDesc = "五行命盘能量雷达图：金${String.format("%.1f", scores.metal)}分，木${String.format("%.1f", scores.wood)}分，水${String.format("%.1f", scores.water)}分，火${String.format("%.1f", scores.fire)}分，土${String.format("%.1f", scores.earth)}分"

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
                    val levelSuffix = if (!strengthLevel.isNullOrBlank()) " ($strengthLevel)" else ""
                    Text(
                        text = "日主【$dayMaster】· $strength$levelSuffix",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = FortuneGold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 正五边形极坐标雷达图 + 五角外顶点中文标注
            Canvas(
                modifier = Modifier
                    .size(248.dp)
                    .semantics { contentDescription = radarDesc }
            ) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val radius = (size.minDimension / 2f) * 0.68f
                val labelRadius = radius + 24.dp.toPx()

                // 1. 5层同心五边形网格
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
                        color = Color.Gray.copy(alpha = 0.24f),
                        style = Stroke(width = 1.dp.toPx())
                    )
                }

                // 2. 5条射线与外顶点五行中文标签（喜用神加★）
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

                    val isXi = normalizedXiYong.contains(labels[i])
                    val tagText = if (isXi) "${labels[i]}★" else labels[i]
                    val layout = textMeasurer.measure(
                        text = tagText,
                        style = TextStyle(
                            color = colors[i],
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    )
                    val lx = cx + (labelRadius * cos(angle)).toFloat() - layout.size.width / 2f
                    val ly = cy + (labelRadius * sin(angle)).toFloat() - layout.size.height / 2f
                    drawText(
                        textLayoutResult = layout,
                        topLeft = Offset(lx, ly)
                    )
                }

                // 3. 动态五行能量多边形
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

                drawPath(
                    path = dataPath,
                    brush = Brush.radialGradient(
                        colors = listOf(FortuneGold.copy(alpha = 0.65f), ImperialRed.copy(alpha = 0.25f)),
                        center = Offset(cx, cy),
                        radius = radius
                    )
                )
                drawPath(
                    path = dataPath,
                    color = FortuneGold,
                    style = Stroke(width = 2.5.dp.toPx())
                )

                // 4. 顶点圆点
                dataPoints.forEachIndexed { i, pt ->
                    drawCircle(color = colors[i], radius = 5.dp.toPx(), center = pt)
                    drawCircle(color = Color.White, radius = 2.dp.toPx(), center = pt)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 底部五行数值明示栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                labels.forEachIndexed { i, elem ->
                    val isXiYong = normalizedXiYong.contains(elem)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .background(
                                color = if (isXiYong) FortuneGold.copy(alpha = 0.16f) else Color.Transparent,
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
