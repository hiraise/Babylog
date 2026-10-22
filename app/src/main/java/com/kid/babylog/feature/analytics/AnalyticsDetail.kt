package com.kid.babylog.feature.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kid.babylog.R
import com.kid.babylog.core.ui.components.AppCard
import com.kid.babylog.core.ui.components.AppIcon
import com.kid.babylog.core.ui.guide.GuideTarget
import com.kid.babylog.core.ui.guide.guideTarget
import com.kid.babylog.core.ui.theme.BabyLogTheme
import java.time.LocalDate

/** Ширина колонки разницы в списке замеров: «+270 г» и «−1,5 см» выравниваются по правому краю. */
private val ChangeWidth = 72.dp

/**
 * Экран раздела «Аналитики» (ADR-0029): «‹ Название», выбор периода (если раздел от него зависит)
 * и карточки ADR-0028; у веса и роста под графиком — список замеров. Нет данных раздела — подсказка.
 * [selection] — выбранный столбик «метрика:номер», его хранит [AnalyticsContent].
 */
@Composable
internal fun AnalyticsDetail(
    section: AnalyticsSection,
    model: AnalyticsModel,
    period: AnalyticsPeriod,
    onPeriod: (AnalyticsPeriod) -> Unit,
    selection: String?,
    onSelection: (String?) -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenTag: (String) -> Unit = {},
) {
    val chart = BabyLogTheme.chartColors
    var sleepHelp by rememberSaveable { mutableStateOf(false) }
    if (sleepHelp) SleepHelpSheet(onDismiss = { sleepHelp = false })
    LazyColumn(
        modifier = modifier.fillMaxSize().guideTarget(GuideTarget.ANALYTICS_DETAIL),
        contentPadding = analyticsListPadding(),
        verticalArrangement = Arrangement.spacedBy(BabyLogTheme.spacing.m),
    ) {
        item(key = "header") { DetailHeader(section.title, onBack) }
        if (section.usesPeriod) {
            item(key = "period") { PeriodPicker(period, onPeriod, model.illnessNote) }
        }
        when (section) {
            AnalyticsSection.RHYTHM -> {
                val rhythm = model.rhythm
                if (rhythm == null) {
                    item(key = "none") { HintCard("За последнюю неделю записей сна, кормлений и подгузников нет") }
                } else {
                    item(key = "rhythm") { RhythmCard(rhythm, onOpenDay) }
                }
            }
            AnalyticsSection.FEEDINGS, AnalyticsSection.SLEEP, AnalyticsSection.DIAPERS, AnalyticsSection.CRYING -> {
                val metrics = model.metrics.filter { it.metric.section == section }
                if (metrics.isEmpty()) {
                    item(key = "none") { HintCard("Записей пока нет") }
                }
                metrics.forEach { metric ->
                    item(key = metric.metric.name) {
                        val prefix = "${metric.metric.name}:"
                        MetricCard(
                            model = metric,
                            selected = selection?.takeIf { it.startsWith(prefix) }?.removePrefix(prefix)?.toIntOrNull(),
                            onSelect = { index -> onSelection(index?.let { prefix + it }) },
                            onOpenDay = onOpenDay,
                            // Как сон делится на ночь и день (ADR-0036)
                            onHelp = if (metric.metric == Metric.SLEEP) ({ sleepHelp = true }) else null,
                        )
                    }
                }
                // Сон в дни болезни и без (ADR-0040) — рядом с графиком, средние не меняются
                if (section == AnalyticsSection.SLEEP) {
                    model.illnessSleep?.let { sleep -> item(key = "illness") { IllnessSleepCard(sleep) } }
                }
                // Плач (ADR-0030): что было перед ним и почему — под графиком минут
                if (section == AnalyticsSection.CRYING) {
                    model.crying?.let { crying ->
                        item(key = "before") { CryingBeforeCard(crying) }
                        item(key = "causes") { CryingCausesCard(crying) }
                    }
                }
            }
            AnalyticsSection.WEIGHT, AnalyticsSection.HEIGHT -> {
                val weight = section == AnalyticsSection.WEIGHT
                val growth = if (weight) model.weight else model.height
                if (growth == null) {
                    item(key = "none") { HintCard(if (weight) "Взвешиваний пока нет" else "Замеров роста пока нет") }
                } else {
                    item(key = "chart") {
                        GrowthCard(
                            model = growth,
                            icon = if (weight) R.drawable.ic_weight else R.drawable.ic_height,
                            title = section.title,
                            color = if (weight) chart.weight else chart.height,
                        )
                    }
                    item(key = "history") { MeasurementsCard(if (weight) "Взвешивания" else "Замеры", growth.history) }
                }
            }
            AnalyticsSection.MEDICINES -> if (model.medicines.isEmpty()) {
                item(key = "none") { HintCard("Лекарств за этот период нет") }
            } else {
                item(key = "medicines") { MedicinesCard(model.medicines) }
            }
            AnalyticsSection.TAGS -> {
                val tags = model.tags
                if (tags == null) {
                    item(key = "none") { HintCard("Меток пока нет: их ставят после плача и в заметках") }
                } else {
                    item(key = "frequency") { TagsFrequencyCard(tags, byWeeks = period == AnalyticsPeriod.ALL, onOpenTag = onOpenTag) }
                    if (tags.routine.isNotEmpty()) {
                        item(key = "routine") { TagsRoutineCard(tags) }
                    }
                    item(key = "footnote") { TagsFootnote() }
                }
            }
        }
    }
}

/** «‹ Название»: стрелка возвращает к обзору, как и системная «Назад»; название — заголовок для TalkBack. */
@Composable
private fun DetailHeader(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            AppIcon(R.drawable.ic_chevron_left, contentDescription = "Назад к обзору")
        }
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
    }
}

/** Замеры веса или роста, свежие сверху: дата, значение и разница с предыдущим замером. */
@Composable
private fun MeasurementsCard(title: String, entries: List<GrowthEntry>) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    AppCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(BabyLogTheme.spacing.s)) {
            CardHeader(R.drawable.ic_calendar, title, "к прошлому замеру")
            entries.forEach { entry ->
                Row(
                    modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(entry.date, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(entry.value, style = MaterialTheme.typography.titleMedium)
                    Text(
                        entry.change.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = muted,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(ChangeWidth),
                    )
                }
            }
        }
    }
}
