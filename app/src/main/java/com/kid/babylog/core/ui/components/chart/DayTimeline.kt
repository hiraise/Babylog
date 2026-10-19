package com.kid.babylog.core.ui.components.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.kid.babylog.core.ui.theme.BabyLogTheme

/** Полоса на шкале суток: доли суток от 0 до 1. [ring] — обвести цветом фона, чтобы отделить от соседней. */
@Immutable
data class TimelineBand(val from: Float, val to: Float, val color: Color, val ring: Boolean = false)

/** Мгновенная отметка — риска во всю высоту полосы. */
@Immutable
data class TimelineTick(val at: Float, val color: Color)

/**
 * Строка шкалы: сутки слева направо. [end] — докуда сутки прошли (у сегодня — до текущего момента),
 * дальше шкала пуста; [dots] — точки под шкалой (подгузники); [pins] — булавки над шкалой (заметки):
 * черта через полосу с головкой сверху, цвета `onSurface`; [description] — для TalkBack.
 */
@Immutable
data class TimelineRow(
    val label: String,
    val bands: List<TimelineBand>,
    val ticks: List<TimelineTick>,
    val dots: List<Float>,
    val end: Float,
    val description: String,
    val pins: List<Float> = emptyList(),
)

private val LabelWidth = 56.dp
private val AxisHeight = 16.dp
private val RowHeight = 28.dp
private val StripHeight = 16.dp
private val TrackRadius = 4.dp
private val BandRadius = 3.dp
private val MinBandWidth = 2.dp
private val TickWidth = 2.dp
private val Ring = 1.5.dp
private val DotRadius = 2.dp
private val DotOffset = 6.dp
private val GridWidth = 1.dp
private val PinWidth = 2.dp
private val PinHeadRadius = 3.dp

/**
 * «Ритм дня» (ADR-0028): по строке на сутки, шкала от 0 до 24 часов с делениями через 6 часов.
 * Полосы рисуются по порядку [TimelineRow.bands], отметки — поверх них. Касание строки — [onRowClick]
 * с её номером; строка для TalkBack — одна кнопка с описанием. [onRowClick] null — строки не нажимаются:
 * на обзоре «Аналитики» (ADR-0029) нажимается вся карточка.
 */
@Composable
fun DayTimeline(
    rows: List<TimelineRow>,
    dotColor: Color,
    onRowClick: ((Int) -> Unit)?,
    onClickLabel: String?,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val spacing = BabyLogTheme.spacing
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant)
    val measurer = rememberTextMeasurer()
    Column(modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
            Spacer(Modifier.width(LabelWidth))
            Canvas(Modifier.weight(1f).height(AxisHeight)) {
                (0..24 step 6).forEach { hour ->
                    val layout = measurer.measure(hour.toString(), labelStyle)
                    val x = hour / 24f * size.width
                    val left = when (hour) {
                        0 -> 0f
                        24 -> size.width - layout.size.width
                        else -> x - layout.size.width / 2f
                    }
                    drawText(layout, topLeft = Offset(left, 0f))
                }
            }
        }
        rows.forEachIndexed { index, row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(RowHeight)
                    .then(if (onRowClick != null) Modifier.clickable(onClickLabel = onClickLabel) { onRowClick(index) } else Modifier)
                    .semantics(mergeDescendants = true) { contentDescription = row.description },
                horizontalArrangement = Arrangement.spacedBy(spacing.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    row.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.width(LabelWidth),
                )
                Canvas(Modifier.weight(1f).fillMaxHeight()) {
                    drawRow(row, track = colors.surfaceVariant, background = colors.surfaceContainerLow, dotColor = dotColor, pinColor = colors.onSurface)
                }
            }
        }
    }
}

private fun DrawScope.drawRow(row: TimelineRow, track: Color, background: Color, dotColor: Color, pinColor: Color) {
    val strip = StripHeight.toPx()
    val top = (size.height - strip - DotOffset.toPx()) / 2
    val width = size.width
    val ring = Ring.toPx()
    drawRoundRect(track, Offset(0f, top), Size(width * row.end, strip), CornerRadius(TrackRadius.toPx()))
    listOf(0.25f, 0.5f, 0.75f).filter { it < row.end }.forEach { at ->
        drawLine(background, Offset(at * width, top), Offset(at * width, top + strip), GridWidth.toPx())
    }
    row.bands.forEach { band ->
        val left = band.from * width
        val bandWidth = maxOf((band.to - band.from) * width, MinBandWidth.toPx())
        if (band.ring) {
            drawRect(background, Offset(left - ring, top), Size(bandWidth + 2 * ring, strip))
        }
        drawRoundRect(band.color, Offset(left, top), Size(bandWidth, strip), CornerRadius(BandRadius.toPx()))
    }
    row.ticks.forEach { tick ->
        val left = tick.at * width - TickWidth.toPx() / 2
        drawRect(background, Offset(left - ring, top), Size(TickWidth.toPx() + 2 * ring, strip))
        drawRect(tick.color, Offset(left, top), Size(TickWidth.toPx(), strip))
    }
    row.dots.forEach { at ->
        drawCircle(dotColor, DotRadius.toPx(), Offset(at * width, top + strip + DotOffset.toPx() - DotRadius.toPx()))
    }
    // Булавка — поверх всего: черта с обводкой цвета фона, чтобы её было видно и на полосе сна
    row.pins.forEach { at ->
        val x = at * width
        val head = Offset(x, top - PinHeadRadius.toPx())
        drawLine(background, head, Offset(x, top + strip), PinWidth.toPx() + 2 * ring)
        drawCircle(background, PinHeadRadius.toPx() + ring, head)
        drawLine(pinColor, head, Offset(x, top + strip), PinWidth.toPx())
        drawCircle(pinColor, PinHeadRadius.toPx(), head)
    }
}
