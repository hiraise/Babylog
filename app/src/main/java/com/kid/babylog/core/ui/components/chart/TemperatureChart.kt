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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.kid.babylog.core.ui.theme.BabyLogTheme
import kotlin.math.ceil
import kotlin.math.floor

/** Замер: время и десятые градуса. */
@Immutable
data class TemperaturePoint(val time: Long, val tenths: Int)

/** Строка приёмов под графиком: подпись (название лекарства) и время каждого приёма. */
@Immutable
data class MarkRow(val label: String, val times: List<Long>)

/** Отметка на шкале времени — вертикальный пунктир с подписью внизу: «+ отит» в день, с которого он стоит. */
@Immutable
data class TimeMarker(val time: Long, val label: String)

private val PlotHeight = 140.dp
private val AxisHeight = 20.dp
private val RowHeight = 32.dp
private val RowDotOffset = 24.dp
private val LabelGutter = 28.dp
private val LineWidth = 2.dp
private val GridWidth = 1.dp
private val PointRadius = 3.dp
private val LastRadius = 5.dp
private val LastRing = 2.dp
private val MarkRadius = 4.dp
private val Dash = 3.dp

/** Шкала не уже этих градусов: от 36 до 39 — иначе 37,2 и 37,4 выглядят пиком. */
private const val MIN_LOW = 360
private const val MIN_HIGH = 390

/**
 * График температуры на экране болезни (ADR-0041): линия [lineColor] по замерам [points] на шкале времени
 * от [from] до [to]; сетка — по целым градусам с подписями слева, сутки — бледными вертикалями с подписями
 * [days] (начало суток → «ср 7») внизу. [markers] — пунктир с подписью («+ отит»). Под графиком — строка
 * на каждое лекарство [marks]: подпись, под ней приёмы — нейтральными точками; так видно, когда давали
 * жаропонижающее и что было с температурой после. Без порогов и «опасных» зон: только записи.
 * TalkBack читает [description] — то же словами.
 */
@Composable
fun TemperatureChart(
    points: List<TemperaturePoint>,
    marks: List<MarkRow>,
    from: Long,
    to: Long,
    days: List<Pair<Long, String>>,
    markers: List<TimeMarker>,
    lineColor: Color,
    description: String,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant)
    val markerStyle = MaterialTheme.typography.labelSmall.copy(color = colors.tertiary)
    val measurer = rememberTextMeasurer()
    val ring = colors.surfaceContainerLow
    val spacing = BabyLogTheme.spacing
    val height = PlotHeight + AxisHeight + RowHeight * marks.size
    Canvas(modifier.fillMaxWidth().height(height).semantics { contentDescription = description }) {
        val left = LabelGutter.toPx()
        val right = size.width - spacing.s.toPx()
        val top = spacing.s.toPx()
        val plotBottom = PlotHeight.toPx()
        val span = (to - from).coerceAtLeast(1)
        fun x(time: Long) = left + (time - from).toFloat() / span * (right - left)
        val low = minOf(MIN_LOW, floor((points.minOfOrNull { it.tenths } ?: MIN_LOW) / 10.0).toInt() * 10)
        val high = maxOf(MIN_HIGH, ceil((points.maxOfOrNull { it.tenths } ?: MIN_HIGH) / 10.0).toInt() * 10)
        fun y(tenths: Int) = plotBottom - (tenths - low).toFloat() / (high - low) * (plotBottom - top)

        // Сутки — бледные вертикали, подписи посередине суток под графиком
        days.forEachIndexed { index, (start, label) ->
            if (index > 0) drawLine(colors.outlineVariant, Offset(x(start), top), Offset(x(start), plotBottom), GridWidth.toPx())
            val end = days.getOrNull(index + 1)?.first ?: to
            val layout = measurer.measure(label, labelStyle)
            val center = (x(maxOf(start, from)) + x(minOf(end, to))) / 2
            drawText(layout, topLeft = Offset((center - layout.size.width / 2f).coerceIn(left, right - layout.size.width), plotBottom + spacing.xs.toPx()))
        }
        // Целые градусы — сетка и подписи слева
        (low..high step 10).forEach { tenths ->
            val y = y(tenths)
            drawLine(colors.outlineVariant, Offset(left, y), Offset(right, y), GridWidth.toPx())
            if (tenths in (low + 1) until high || tenths == high) {
                val layout = measurer.measure((tenths / 10).toString(), labelStyle)
                drawText(layout, topLeft = Offset(0f, y - layout.size.height / 2f))
            }
        }
        markers.forEach { marker ->
            val mx = x(marker.time)
            drawLine(colors.tertiary, Offset(mx, top), Offset(mx, plotBottom), GridWidth.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(Dash.toPx(), Dash.toPx())))
            val layout = measurer.measure(marker.label, markerStyle)
            drawText(layout, topLeft = Offset((mx - layout.size.width - spacing.xs.toPx()).coerceAtLeast(left), plotBottom - layout.size.height - spacing.xs.toPx()))
        }
        if (points.size >= 2) {
            val path = Path()
            points.forEachIndexed { i, point -> if (i == 0) path.moveTo(x(point.time), y(point.tenths)) else path.lineTo(x(point.time), y(point.tenths)) }
            drawPath(path, lineColor, style = Stroke(LineWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        points.forEachIndexed { i, point ->
            val center = Offset(x(point.time), y(point.tenths))
            if (i == points.lastIndex) {
                drawCircle(ring, LastRadius.toPx() + LastRing.toPx(), center)
                drawCircle(lineColor, LastRadius.toPx(), center)
            } else {
                drawCircle(lineColor, PointRadius.toPx(), center)
            }
        }
        // Приёмы лекарств — строка на лекарство: подпись сверху, под ней точки нейтрального цвета —
        // это записи, а не ряд графика
        marks.forEachIndexed { index, row ->
            val rowTop = PlotHeight.toPx() + AxisHeight.toPx() + index * RowHeight.toPx()
            drawText(measurer.measure(row.label, labelStyle, maxLines = 1), topLeft = Offset(left, rowTop))
            row.times.forEach { time -> drawCircle(colors.onSurfaceVariant, MarkRadius.toPx(), Offset(x(time), rowTop + RowDotOffset.toPx())) }
        }
    }
}
