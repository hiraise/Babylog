package com.kid.babylog.feature.analytics

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kid.babylog.R
import com.kid.babylog.core.ui.components.AppCard
import com.kid.babylog.core.ui.components.AppIcon
import com.kid.babylog.core.ui.components.chart.AxisTick
import com.kid.babylog.core.ui.components.chart.BarChart
import com.kid.babylog.core.ui.components.chart.BarPart
import com.kid.babylog.core.ui.components.chart.ChartBar
import com.kid.babylog.core.ui.components.chart.DayTimeline
import com.kid.babylog.core.ui.components.chart.LineChart
import com.kid.babylog.core.ui.components.chart.TimelineBand
import com.kid.babylog.core.ui.components.chart.TimelineRow
import com.kid.babylog.core.ui.components.chart.TimelineTick
import com.kid.babylog.core.ui.diaperIcon
import com.kid.babylog.core.ui.theme.BabyLogTheme
import com.kid.babylog.core.ui.theme.ChartColors
import java.time.LocalDate

private val HeaderIconSize = 20.dp
private val LegendIconSize = 18.dp
private val SwatchSize = 10.dp
private val LegendDotSize = 4.dp
private val SwatchShape = RoundedCornerShape(2.dp)
private val PinGlyphSize = 12.dp
private val PinHead = 3.dp
private val PinStroke = 2.dp

internal fun ChartColors.of(series: Series): Color = when (series) {
    Series.FEEDING -> feeding
    Series.SLEEP -> sleep
    Series.DIAPER_WET -> diaperWet
    Series.DIAPER_MIXED -> diaperMixed
    Series.DIAPER_DIRTY -> diaperDirty
    Series.CRYING -> crying
}

@DrawableRes
internal fun Metric.icon(): Int = when (this) {
    Metric.FEEDINGS -> R.drawable.ic_feeding
    Metric.BOTTLES -> R.drawable.ic_bottle
    Metric.SLEEP -> R.drawable.ic_sleep
    Metric.DIAPERS -> R.drawable.ic_diaper
    Metric.CRYING -> R.drawable.ic_crying
}

/** Заголовок карточки: иконка, название и справа — пояснение мелко («к прошлой неделе», «за всё время»). */
@Composable
internal fun CardHeader(@DrawableRes icon: Int, title: String, trailing: String?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(BabyLogTheme.spacing.s)) {
        AppIcon(icon, contentDescription = null, modifier = Modifier.size(HeaderIconSize), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
        trailing?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Главное число карточки, рядом — к чему оно ([unit]), под ним — пояснение [note]. */
@Composable
private fun Headline(value: String, unit: String, note: String?) {
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(BabyLogTheme.spacing.s)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.alignByBaseline())
            Text(
                unit,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alignByBaseline(),
            )
        }
        note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/** Булавка заметки для легенды — как на шкале «Ритма дня». */
@Composable
private fun PinGlyph() {
    val ink = MaterialTheme.colorScheme.onSurface
    Canvas(Modifier.size(PinGlyphSize)) {
        val head = Offset(size.width / 2, PinHead.toPx())
        drawLine(ink, head, Offset(size.width / 2, size.height), PinStroke.toPx())
        drawCircle(ink, PinHead.toPx(), head)
    }
}

@Composable
private fun Swatch(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(SwatchSize).background(color, SwatchShape))
}

/**
 * Карточка-график величины за период: число, сравнение, легенда, столбики. Касание столбика
 * показывает его итоги под графиком, у суток — с «В историю» ([onOpenDay]).
 */
@Composable
internal fun MetricCard(model: MetricModel, selected: Int?, onSelect: (Int?) -> Unit, onOpenDay: (LocalDate) -> Unit) {
    val chart = BabyLogTheme.chartColors
    val spacing = BabyLogTheme.spacing
    AppCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
            CardHeader(model.metric.icon(), model.metric.title, model.delta)
            Headline(model.value, model.unit, model.note)
            if (model.legend.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.m)) {
                    model.legend.forEach { item ->
                        Row(
                            modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "${item.label} ${item.text}" },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                        ) {
                            Swatch(chart.of(item.series))
                            AppIcon(
                                diaperIcon(item.kind),
                                contentDescription = null,
                                modifier = Modifier.size(LegendIconSize),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(item.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            BarChart(
                bars = model.columns.map { column ->
                    ChartBar(
                        parts = column.parts.map { (series, value) -> BarPart(value, chart.of(series)) },
                        label = column.label,
                        description = column.caption,
                        partial = column.partial,
                    )
                },
                maxValue = model.maxValue,
                maxLabel = model.maxLabel,
                midLabel = model.midLabel,
                selected = selected,
                onSelect = onSelect,
            )
            selected?.let(model.columns::getOrNull)?.let { column ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(column.caption, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    column.date?.let { date -> TextButton(onClick = { onOpenDay(date) }) { Text("В историю") } }
                }
            }
        }
    }
}

/**
 * Строки [DayTimeline] из суток ритма: сон, кормления грудью и плач — полосами, остальные кормления —
 * рисками, заметки — булавками (ADR-0031). Плач — поверх сна, с обводкой: он стык в стык с пробуждением,
 * и без обводки полосы слились бы.
 */
internal fun timelineRows(days: List<RhythmDay>, chart: ChartColors): List<TimelineRow> = days.map { day ->
    TimelineRow(
        label = day.label,
        bands = day.sleeps.map { TimelineBand(it.from, it.to, chart.sleep) } +
            day.feedings.map { TimelineBand(it.from, it.to, chart.feeding, ring = true) } +
            day.cryings.map { TimelineBand(it.from, it.to, chart.crying, ring = true) },
        ticks = day.instantFeedings.map { TimelineTick(it, chart.feeding) },
        dots = day.diapers,
        end = day.end,
        description = day.description,
        pins = day.notes,
    )
}

/** «Ритм дня» за неделю: касание строки открывает сутки в «Истории». */
@Composable
internal fun RhythmCard(model: RhythmModel, onOpenDay: (LocalDate) -> Unit) {
    val chart = BabyLogTheme.chartColors
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val spacing = BabyLogTheme.spacing
    AppCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
            CardHeader(R.drawable.ic_schedule, "Ритм дня", "последние $RHYTHM_DAYS дней")
            DayTimeline(
                rows = timelineRows(model.days, chart),
                dotColor = muted,
                onRowClick = { onOpenDay(model.days[it].date) },
                onClickLabel = "открыть в истории",
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.m)) {
                // «плач» в легенде — только если он есть на шкале: тем, кто его не отмечает, пункт ни к чему
                val crying = model.days.any { it.cryings.isNotEmpty() }
                listOfNotNull(chart.sleep to "сон", chart.feeding to "кормление", (chart.crying to "плач").takeIf { crying }).forEach { (color, text) ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        Swatch(color)
                        Text(text, style = MaterialTheme.typography.bodySmall, color = muted)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    Box(Modifier.size(LegendDotSize).background(muted, CircleShape))
                    Text("подгузник", style = MaterialTheme.typography.bodySmall, color = muted)
                }
                if (model.days.any { it.notes.isNotEmpty() }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        PinGlyph()
                        Text("заметка", style = MaterialTheme.typography.bodySmall, color = muted)
                    }
                }
            }
            model.summary?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

/** Вес или рост за всё время: последний замер, прибавка, линия по замерам цвета [color]. */
@Composable
internal fun GrowthCard(model: GrowthModel, @DrawableRes icon: Int, title: String, color: Color) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(BabyLogTheme.spacing.s)) {
            CardHeader(icon, title, "за всё время")
            Headline(model.value, model.date, model.note)
            if (model.points.size >= 2) {
                LineChart(
                    points = model.points,
                    xMax = model.xMax,
                    yMin = model.yMin,
                    yMax = model.yMax,
                    yTicks = model.yTicks.map { AxisTick(it.value, it.label) },
                    xTicks = model.xTicks.map { AxisTick(it.value, it.label) },
                    color = color,
                    description = model.description,
                )
            }
        }
    }
}

@Composable
internal fun MedicinesCard(lines: List<MedicineLine>) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(BabyLogTheme.spacing.m)) {
            CardHeader(R.drawable.ic_medicine, "Лекарства", null)
            lines.forEach { line ->
                Column(Modifier.semantics(mergeDescendants = true) {}) {
                    Text(line.name, style = MaterialTheme.typography.bodyLarge)
                    Text(line.info, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
