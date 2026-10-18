package com.kid.babylog.core.ui.components.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.kid.babylog.core.ui.theme.BabyLogTheme

/** Деление оси: значение в единицах графика и подпись. */
@Immutable
data class AxisTick(val value: Float, val label: String)

private val LineChartHeight = 168.dp
private val LineStroke = 2.dp
private val GridWidth = 1.dp
private val DotRadius = 3.5.dp
private val LastDotRadius = 5.dp
private val DotRing = 2.dp

/**
 * Линия с точками измерений — вес за всё время (ADR-0028). Линия 2 dp, точка — с кольцом цвета
 * карточки, чтобы не сливаться с линией; последняя точка крупнее. Сетка — по [yTicks] с подписями
 * слева, подписи [xTicks] — под графиком. [points] — пары (x, y) в единицах осей; x от 0 до [xMax].
 * Для TalkBack график — картинка с описанием [description].
 */
@Composable
fun LineChart(
    points: List<Pair<Float, Float>>,
    xMax: Float,
    yMin: Float,
    yMax: Float,
    yTicks: List<AxisTick>,
    xTicks: List<AxisTick>,
    color: Color,
    description: String,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val spacing = BabyLogTheme.spacing
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant)
    val measurer = rememberTextMeasurer()
    Canvas(
        modifier
            .fillMaxWidth()
            .height(LineChartHeight)
            .semantics { contentDescription = description },
    ) {
        val yLabels = yTicks.map { measurer.measure(it.label, labelStyle) }
        val xLabels = xTicks.map { measurer.measure(it.label, labelStyle) }
        val left = (yLabels.maxOfOrNull { it.size.width } ?: 0) + spacing.s.toPx()
        val right = size.width - spacing.s.toPx()
        val top = spacing.s.toPx()
        val bottom = size.height - (xLabels.maxOfOrNull { it.size.height } ?: 0) - spacing.xs.toPx()
        fun x(value: Float) = left + value / xMax * (right - left)
        fun y(value: Float) = bottom - (value - yMin) / (yMax - yMin) * (bottom - top)

        yTicks.forEachIndexed { i, tick ->
            val lineY = y(tick.value)
            drawLine(colors.outlineVariant, Offset(left, lineY), Offset(right, lineY), GridWidth.toPx())
            drawText(yLabels[i], topLeft = Offset(0f, lineY - yLabels[i].size.height / 2f))
        }
        xTicks.forEachIndexed { i, tick ->
            val width = xLabels[i].size.width
            val labelX = (x(tick.value) - width / 2f).coerceIn(left - width / 2f, size.width - width)
            drawText(xLabels[i], topLeft = Offset(labelX, bottom + spacing.xs.toPx()))
        }
        val path = Path()
        points.forEachIndexed { i, (px, py) ->
            if (i == 0) path.moveTo(x(px), y(py)) else path.lineTo(x(px), y(py))
        }
        drawPath(path, color, style = Stroke(width = LineStroke.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        points.forEachIndexed { i, (px, py) ->
            val radius = (if (i == points.lastIndex) LastDotRadius else DotRadius).toPx()
            val center = Offset(x(px), y(py))
            drawCircle(colors.surfaceContainerLow, radius + DotRing.toPx(), center)
            drawCircle(color, radius, center)
        }
    }
}
