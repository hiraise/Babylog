package com.kid.babylog.core.ui.components.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.unit.dp

private val SparkHeight = 24.dp
private val SparkBarWidth = 8.dp
private val SparkBarRadius = 2.dp
private val SparkStroke = 2.dp
private val SparkDot = 3.dp
private val SparkRing = 1.5.dp

/**
 * Мини-график плитки обзора «Аналитики» (ADR-0029): те же столбики, что у [BarChart], — части,
 * зазоры между ними, штриховка идущих суток, — но без сетки, подписей и касаний. Высота шкалы —
 * [maxValue], как у большого графика, поэтому столбики плитки и экрана раздела одной формы.
 * TalkBack его не читает: число на плитке говорит то же. Сутки болезни ([ChartBar.band]) — на полосе
 * [bandColor], как на большом графике (ADR-0040).
 */
@Composable
fun SparkBars(bars: List<ChartBar>, maxValue: Float, modifier: Modifier = Modifier, bandColor: Color = Color.Transparent) {
    val halo = MaterialTheme.colorScheme.surfaceContainerLow
    Canvas(modifier.fillMaxWidth().height(SparkHeight)) {
        if (bars.isEmpty()) return@Canvas
        val slot = size.width / bars.size
        bars.forEachIndexed { index, bar ->
            inset(left = index * slot, top = 0f, right = size.width - (index + 1) * slot, bottom = 0f) {
                if (bar.band) drawRect(bandColor)
                drawBar(bar, maxValue, topInset = 0f, maxWidth = SparkBarWidth, cornerRadius = SparkBarRadius, halo = halo.takeIf { bar.band })
            }
        }
    }
}

/**
 * Мини-линия замеров на плитке (ADR-0029): линия 2 dp по [points] (x, y) в собственных границах —
 * видна форма роста, а не шкала. Последняя точка — с кольцом цвета карточки, как у [LineChart];
 * один замер — одна точка справа. TalkBack её не читает: последний замер написан на плитке.
 */
@Composable
fun SparkLine(points: List<Pair<Float, Float>>, color: Color, modifier: Modifier = Modifier) {
    val ring = MaterialTheme.colorScheme.surfaceContainerLow
    Canvas(modifier.fillMaxWidth().height(SparkHeight)) {
        if (points.isEmpty()) return@Canvas
        val pad = (SparkDot + SparkRing).toPx()
        val xMin = points.minOf { it.first }
        val xSpan = (points.maxOf { it.first } - xMin).takeIf { it > 0f }
        val yMin = points.minOf { it.second }
        val ySpan = (points.maxOf { it.second } - yMin).takeIf { it > 0f }
        // Одна точка или ровная линия — по правому краю и посередине высоты
        fun x(value: Float) = xSpan?.let { pad + (value - xMin) / it * (size.width - 2 * pad) } ?: (size.width - pad)
        fun y(value: Float) = ySpan?.let { size.height - pad - (value - yMin) / it * (size.height - 2 * pad) } ?: (size.height / 2)
        if (points.size >= 2) {
            val path = Path()
            points.forEachIndexed { i, (px, py) -> if (i == 0) path.moveTo(x(px), y(py)) else path.lineTo(x(px), y(py)) }
            drawPath(path, color, style = Stroke(width = SparkStroke.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        val last = Offset(x(points.last().first), y(points.last().second))
        drawCircle(ring, pad, last)
        drawCircle(color, SparkDot.toPx(), last)
    }
}
