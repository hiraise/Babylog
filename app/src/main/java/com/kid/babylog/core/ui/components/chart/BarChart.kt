package com.kid.babylog.core.ui.components.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.kid.babylog.core.ui.theme.BabyLogTheme

/** Часть столбика: [value] в единицах шкалы и цвет ряда из `BabyLogTheme.chartColors`. */
@Immutable
data class BarPart(val value: Float, val color: Color)

/**
 * Столбик [BarChart]: [parts] снизу вверх, [label] — подпись под ним (пустая — без подписи),
 * [description] — что прочитает TalkBack. [partial] — сутки или неделя ещё идут: столбик заштрихован.
 */
@Immutable
data class ChartBar(val parts: List<BarPart>, val label: String, val description: String, val partial: Boolean = false)

private val ChartHeight = 112.dp
private val MaxBarWidth = 24.dp
private val BarRadius = 4.dp
private val BarGap = 2.dp
private val HatchStep = 5.dp
private val HatchWidth = 1.5.dp
private val LineWidth = 1.dp

/** Доля ширины слота под столбик: остальное — воздух между столбиками. */
private const val BAR_SHARE = 0.62f
private const val DIMMED = 0.4f
private const val PARTIAL_FILL = 0.18f

/**
 * Столбчатая диаграмма «Аналитики» (ADR-0028):
 * - столбик не толще 24 dp, верх скруглён на 4 dp, низ прямой — на общей базовой линии;
 * - части одного столбика разделяет зазор 2 dp цвета фона, а не обводка;
 * - незаконченные сутки или неделя ([ChartBar.partial]) — штриховка вместо заливки;
 * - сетка — линии верха шкалы и её середины с подписями [maxLabel], [midLabel] справа;
 * - касание столбика выбирает его ([onSelect] с номером), остальные бледнеют; касание выбранного
 *   снимает выбор (`null`). Что показать о выбранном, решает экран.
 */
@Composable
fun BarChart(
    bars: List<ChartBar>,
    maxValue: Float,
    maxLabel: String,
    midLabel: String,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val spacing = BabyLogTheme.spacing
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant)
    val measurer = rememberTextMeasurer()
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(ChartHeight)) {
            Canvas(Modifier.fillMaxWidth().height(ChartHeight)) {
                val top = spacing.s.toPx()
                val mid = top + (size.height - top) / 2
                val right = size.width - spacing.xxl.toPx()
                val line = LineWidth.toPx()
                listOf(top, mid, size.height - line / 2).forEach { y ->
                    drawLine(colors.outlineVariant, Offset(0f, y), Offset(right, y), line)
                }
                listOf(maxLabel to top, midLabel to mid).forEach { (text, y) ->
                    val layout = measurer.measure(text, labelStyle)
                    drawText(layout, topLeft = Offset(size.width - layout.size.width, y - layout.size.height / 2f))
                }
            }
            Row(Modifier.fillMaxWidth().height(ChartHeight).padding(end = spacing.xxl)) {
                bars.forEachIndexed { index, bar ->
                    Canvas(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .alpha(if (selected != null && selected != index) DIMMED else 1f)
                            .selectable(selected = selected == index, role = Role.Button) {
                                onSelect(if (selected == index) null else index)
                            }
                            .semantics { contentDescription = bar.description },
                    ) {
                        drawBar(bar, maxValue, topInset = spacing.s.toPx())
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(end = spacing.xxl)) {
            bars.forEachIndexed { index, bar ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    // Подпись шире слота (у 30 столбиков) выходит за его края, а не переносится
                    Text(
                        bar.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected == index) colors.onSurface else colors.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.wrapContentWidth(unbounded = true),
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawBar(bar: ChartBar, maxValue: Float, topInset: Float) {
    if (maxValue <= 0f) return
    val width = minOf(MaxBarWidth.toPx(), size.width * BAR_SHARE)
    val left = (size.width - width) / 2
    val plot = size.height - topInset
    val radius = CornerRadius(BarRadius.toPx())
    val visible = bar.parts.filter { it.value > 0f }
    var bottom = size.height
    visible.forEachIndexed { index, part ->
        val top = bottom - maxOf(part.value / maxValue * plot, LineWidth.toPx())
        // Скруглён только верх всего столбика: части ниже — прямоугольники
        val corner = if (index == visible.lastIndex) radius else CornerRadius.Zero
        val path = Path().apply {
            addRoundRect(
                RoundRect(
                    left, top, left + width, bottom,
                    topLeftCornerRadius = corner, topRightCornerRadius = corner,
                    bottomRightCornerRadius = CornerRadius.Zero, bottomLeftCornerRadius = CornerRadius.Zero,
                ),
            )
        }
        if (bar.partial) {
            drawPath(path, part.color.copy(alpha = PARTIAL_FILL))
            clipPath(path) {
                val height = bottom - top
                var x = left - height
                while (x < left + width) {
                    drawLine(part.color, Offset(x, bottom), Offset(x + height, top), HatchWidth.toPx())
                    x += HatchStep.toPx()
                }
            }
        } else {
            drawPath(path, part.color)
        }
        bottom = top - BarGap.toPx()
    }
}
