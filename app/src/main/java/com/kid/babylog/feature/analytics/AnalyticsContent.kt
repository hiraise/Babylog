package com.kid.babylog.feature.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.kid.babylog.R
import com.kid.babylog.core.model.BabyEvent
import com.kid.babylog.core.model.Child
import com.kid.babylog.core.ui.components.AppCard
import com.kid.babylog.core.ui.components.ChoiceChip
import com.kid.babylog.core.ui.theme.BabyLogTheme
import java.time.LocalDate
import java.time.ZoneId

/**
 * Вкладка «Аналитика» (ADR-0028): период, «Ритм дня», графики кормлений, бутылочки, сна и подгузников,
 * вес, рост и лекарства. Всё считается из [events] выбранного ребёнка при показе ([analyticsModel]).
 *
 * Касание столбика показывает итоги под графиком, «В историю» и касание строки ритма открывают день
 * в «Истории» ([onOpenDay]). Период и выбранный столбик переживают поворот; при смене ребёнка выбор
 * сбрасывается.
 */
@Composable
fun AnalyticsContent(
    events: List<BabyEvent>,
    child: Child,
    now: Long,
    onOpenDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    var period by rememberSaveable { mutableStateOf(AnalyticsPeriod.WEEK) }
    // Выбранный столбик — «метрика:номер»: строка сохраняется в Bundle как есть
    var selection by rememberSaveable(period, child.id) { mutableStateOf<String?>(null) }
    // Пересчитываем раз в минуту, а не на каждый тик секундного таймера экрана
    val minuteNow = now / 60_000
    val model = remember(events, period, minuteNow, child.sex) {
        analyticsModel(events, period, now, ZoneId.systemDefault(), child.sex)
    }
    val spacing = BabyLogTheme.spacing
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        // Сверху отступ не меньше размытия тени карточек, как на других вкладках
        contentPadding = PaddingValues(start = spacing.l, top = spacing.m, end = spacing.l, bottom = spacing.xl),
        verticalArrangement = Arrangement.spacedBy(spacing.m),
    ) {
        item(key = "period") {
            PeriodPicker(period, onSelect = { period = it })
        }
        if (!model.hasRecords) {
            item(key = "empty") { EmptyHint() }
        }
        model.rhythm?.let { rhythm ->
            item(key = "rhythm") { RhythmCard(rhythm, onOpenDay) }
        }
        model.metrics.forEach { metric ->
            item(key = metric.metric.name) {
                val prefix = "${metric.metric.name}:"
                MetricCard(
                    model = metric,
                    selected = selection?.takeIf { it.startsWith(prefix) }?.removePrefix(prefix)?.toIntOrNull(),
                    onSelect = { index -> selection = index?.let { prefix + it } },
                    onOpenDay = onOpenDay,
                )
            }
        }
        model.weight?.let { weight ->
            item(key = "weight") { GrowthCard(weight, R.drawable.ic_weight, "Вес", BabyLogTheme.chartColors.weight) }
        }
        model.height?.let { height ->
            item(key = "height") { GrowthCard(height, R.drawable.ic_height, "Рост", BabyLogTheme.chartColors.height) }
        }
        if (model.medicines.isNotEmpty()) {
            item(key = "medicines") { MedicinesCard(model.medicines) }
        }
    }
}

/** Чипы периода и пояснение к штриховке. */
@Composable
private fun PeriodPicker(period: AnalyticsPeriod, onSelect: (AnalyticsPeriod) -> Unit) {
    val spacing = BabyLogTheme.spacing
    Column {
        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
            AnalyticsPeriod.entries.forEach { option ->
                ChoiceChip(selected = option == period, onClick = { onSelect(option) }, label = option.title)
            }
        }
        Text(
            if (period == AnalyticsPeriod.ALL) {
                "Заштрихована неделя, которая ещё идёт: в средние входят только прошедшие сутки"
            } else {
                "Заштрихован день, который ещё идёт: в средние он не входит"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyHint() {
    AppCard(Modifier.fillMaxWidth()) {
        Text(
            "Графики появятся, когда в дневнике будут записи.\nОтмечайте сон, кормления и подгузники на вкладке «Сегодня».",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
