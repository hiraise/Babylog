package com.kid.babylog.feature.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.kid.babylog.R
import com.kid.babylog.core.ui.components.AppCard
import com.kid.babylog.core.ui.components.StatTile
import com.kid.babylog.core.ui.components.chart.BarPart
import com.kid.babylog.core.ui.components.chart.ChartBar
import com.kid.babylog.core.ui.components.chart.DayTimeline
import com.kid.babylog.core.ui.components.chart.SparkBars
import com.kid.babylog.core.ui.components.chart.SparkLine
import com.kid.babylog.core.ui.plural
import com.kid.babylog.core.ui.theme.BabyLogTheme

/** Сколько последних суток «Ритма дня» видно на обзоре; вся неделя — на экране раздела. */
private const val RHYTHM_PREVIEW_DAYS = 3

/** Столбиков в мини-графике не больше этого: у «всего времени» недель может быть много. */
private const val SPARK_COLUMNS = 30

/** Плитка обзора: что показывает и какой раздел открывает. */
private sealed interface Tile {
    data class OfMetric(val model: MetricModel) : Tile
    data class OfGrowth(val section: AnalyticsSection, val model: GrowthModel) : Tile
    data class OfMedicines(val lines: List<MedicineLine>) : Tile
    data class OfTags(val model: TagsModel) : Tile
}

/** Плитки по порядку: режим дня, рост, лекарства, метки; бутылочка — внутри кормлений, плитки нет. */
private fun tiles(model: AnalyticsModel): List<Tile> = buildList {
    model.metrics.filter { it.metric != Metric.BOTTLES }.forEach { add(Tile.OfMetric(it)) }
    model.weight?.let { add(Tile.OfGrowth(AnalyticsSection.WEIGHT, it)) }
    model.height?.let { add(Tile.OfGrowth(AnalyticsSection.HEIGHT, it)) }
    if (model.medicines.isNotEmpty()) add(Tile.OfMedicines(model.medicines))
    model.tags?.let { add(Tile.OfTags(it)) }
}

/**
 * Обзор «Аналитики» (ADR-0029): выбор периода, «Ритм дня» за последние сутки и плитки показателей
 * по две в ряд — все числа на одном экране. Касание ритма или плитки — [onOpen] с разделом.
 */
@Composable
internal fun AnalyticsOverview(
    model: AnalyticsModel,
    period: AnalyticsPeriod,
    onPeriod: (AnalyticsPeriod) -> Unit,
    onOpen: (AnalyticsSection) -> Unit,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val spacing = BabyLogTheme.spacing
    val tiles = tiles(model)
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
        contentPadding = analyticsListPadding(),
        verticalArrangement = Arrangement.spacedBy(spacing.m),
    ) {
        item(key = "period") { PeriodPicker(period, onPeriod) }
        if (!model.hasRecords) {
            item(key = "empty") {
                HintCard("Графики появятся, когда в дневнике будут записи.\nОтмечайте сон, кормления и подгузники на вкладке «Сегодня».")
            }
        }
        model.rhythm?.let { rhythm ->
            item(key = "rhythm") { RhythmPreviewCard(rhythm, onOpen = { onOpen(AnalyticsSection.RHYTHM) }) }
        }
        if (tiles.isNotEmpty()) {
            item(key = "tiles") {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
                    tiles.chunked(2).forEach { pair ->
                        // IntrinsicSize.Min: плитки ряда одной высоты, мини-графики на одной линии
                        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
                            pair.forEach { tile -> TileView(tile, onOpen, Modifier.weight(1f).fillMaxHeight()) }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TileView(tile: Tile, onOpen: (AnalyticsSection) -> Unit, modifier: Modifier) {
    val chart = BabyLogTheme.chartColors
    when (tile) {
        is Tile.OfMetric -> {
            val metric = tile.model
            StatTile(
                icon = metric.metric.icon(),
                title = metric.metric.title,
                value = metric.value,
                unit = metric.unit,
                note = metric.delta,
                onClick = { onOpen(metric.metric.section) },
                modifier = modifier,
            ) {
                SparkBars(
                    bars = metric.columns.takeLast(SPARK_COLUMNS).map { column ->
                        ChartBar(
                            parts = column.parts.map { (series, value) -> BarPart(value, chart.of(series)) },
                            label = "",
                            description = column.caption,
                            partial = column.partial,
                        )
                    },
                    maxValue = metric.maxValue,
                )
            }
        }
        is Tile.OfGrowth -> {
            val weight = tile.section == AnalyticsSection.WEIGHT
            StatTile(
                icon = if (weight) R.drawable.ic_weight else R.drawable.ic_height,
                title = tile.section.title,
                value = tile.model.value,
                unit = tile.model.date,
                note = tile.model.rate,
                onClick = { onOpen(tile.section) },
                modifier = modifier,
            ) {
                SparkLine(tile.model.points, if (weight) chart.weight else chart.height)
            }
        }
        is Tile.OfMedicines -> {
            val total = tile.lines.sumOf { it.count }
            val first = tile.lines.first()
            StatTile(
                icon = R.drawable.ic_medicine,
                title = AnalyticsSection.MEDICINES.title,
                value = total.toString(),
                unit = plural(total, "приём", "приёма", "приёмов"),
                note = first.name + if (tile.lines.size > 1) " и ещё ${tile.lines.size - 1}" else "",
                onClick = { onOpen(AnalyticsSection.MEDICINES) },
                modifier = modifier,
            ) {
                Text(
                    first.last,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // «колики ×9», под ним — следующая метка (две в узкую плитку не влезают) и сколько плача и заметок (ADR-0031)
        is Tile.OfTags -> {
            val top = tile.model.frequencies
            StatTile(
                icon = R.drawable.ic_tag,
                title = AnalyticsSection.TAGS.title,
                value = top.firstOrNull()?.let { "${it.tag} ×${it.count}" } ?: "—",
                unit = top.getOrNull(1)?.let { "${it.tag} ×${it.count}" } ?: "меток за период нет".takeIf { top.isEmpty() },
                note = tile.model.counts(),
                onClick = { onOpen(AnalyticsSection.TAGS) },
                modifier = modifier,
            )
        }
    }
}

/**
 * «Ритм дня» на обзоре: последние [RHYTHM_PREVIEW_DAYS] суток и средние ночи. Нажимается вся
 * карточка — открывает неделю ([onOpen]); строки сами не нажимаются, день открывают с экрана раздела.
 */
@Composable
private fun RhythmPreviewCard(model: RhythmModel, onOpen: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    AppCard(Modifier.fillMaxWidth(), onClick = onOpen, onClickLabel = "открыть ритм за неделю") {
        Column(verticalArrangement = Arrangement.spacedBy(BabyLogTheme.spacing.s)) {
            CardHeader(R.drawable.ic_schedule, AnalyticsSection.RHYTHM.title, "все $RHYTHM_DAYS дней ›")
            DayTimeline(
                rows = timelineRows(model.days.take(RHYTHM_PREVIEW_DAYS), BabyLogTheme.chartColors),
                dotColor = muted,
                onRowClick = null,
                onClickLabel = null,
            )
            model.summary?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = muted) }
        }
    }
}
