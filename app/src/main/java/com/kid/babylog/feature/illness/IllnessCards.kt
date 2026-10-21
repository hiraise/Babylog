package com.kid.babylog.feature.illness

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kid.babylog.R
import com.kid.babylog.core.model.BabyEvent
import com.kid.babylog.core.model.Bottle
import com.kid.babylog.core.model.Crying
import com.kid.babylog.core.model.Diaper
import com.kid.babylog.core.model.Feeding
import com.kid.babylog.core.model.Height
import com.kid.babylog.core.model.Illness
import com.kid.babylog.core.model.IllnessMedicine
import com.kid.babylog.core.model.Medicine
import com.kid.babylog.core.model.Note
import com.kid.babylog.core.model.Sex
import com.kid.babylog.core.model.Sleep
import com.kid.babylog.core.model.Temperature
import com.kid.babylog.core.model.Weight
import com.kid.babylog.core.model.startDate
import com.kid.babylog.core.ui.RU
import com.kid.babylog.core.ui.bySex
import com.kid.babylog.core.ui.components.AppCard
import com.kid.babylog.core.ui.components.AppIcon
import com.kid.babylog.core.ui.components.chart.MarkRow
import com.kid.babylog.core.ui.components.chart.TemperatureChart
import com.kid.babylog.core.ui.components.chart.TemperaturePoint
import com.kid.babylog.core.ui.components.chart.TimeMarker
import com.kid.babylog.core.ui.eventIcon
import com.kid.babylog.core.ui.eventTimeLine
import com.kid.babylog.core.ui.eventTitle
import com.kid.babylog.core.ui.formatAgo
import com.kid.babylog.core.ui.formatClock
import com.kid.babylog.core.ui.formatShortDay
import com.kid.babylog.core.ui.formatTemperature
import com.kid.babylog.core.ui.plural
import com.kid.babylog.core.ui.temperatureMethodLabel
import com.kid.babylog.core.ui.temperatureValue
import com.kid.babylog.core.ui.theme.BabyLogTheme
import com.kid.babylog.core.ui.toLocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val HeaderIconSize = 20.dp
private val RowIconSize = 20.dp
private val TimeWidth = 44.dp
private val DAY_LABEL = DateTimeFormatter.ofPattern("EE d", RU)

/** Заголовок карточки: иконка, название и справа мелко — пояснение. */
@Composable
private fun CardTitle(@DrawableRes icon: Int, title: String, trailing: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(BabyLogTheme.spacing.s)) {
        AppIcon(icon, contentDescription = null, modifier = Modifier.size(HeaderIconSize), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
        trailing?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/** «сегодня в 03:10», «9 окт. в 15:30». */
private fun at(time: Long, now: Long): String = "${formatShortDay(time.toLocalDate(), now.toLocalDate())} в ${formatClock(time)}"

/**
 * Температура (ADR-0041): последняя крупно, самая высокая — справа, под ними график со всеми замерами
 * и приёмами лекарств ([TemperatureChart]). [onAdd] — новый замер, у идущей болезни.
 */
@Composable
internal fun TemperatureCard(model: IllnessScreenModel, now: Long, onAdd: (() -> Unit)?) {
    val spacing = BabyLogTheme.spacing
    val zone = ZoneId.systemDefault()
    val temperatures = model.temperatures
    val max = temperatures.maxWithOrNull(compareBy<Temperature> { it.tenths }.thenBy { it.start })
    AppCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
            CardTitle(R.drawable.ic_thermometer, "Температура", max?.let { "макс. ${temperatureValue(it.tenths)} · ${at(it.start, now)}" })
            val last = temperatures.maxByOrNull { it.start }
            if (last == null) {
                Text("Температуру не записывали", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
                    Text(temperatureValue(last.tenths), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.alignByBaseline())
                    Text(at(last.start, now), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.alignByBaseline())
                }
                val illness = model.illness
                val from = illness.start
                val to = maxOf(illness.end ?: now, model.medicines.maxOfOrNull { it.last } ?: 0L, last.start)
                TemperatureChart(
                    points = temperatures.map { TemperaturePoint(it.start, it.tenths) },
                    marks = model.medicines.sortedBy { it.times.first() }.map { MarkRow(it.name, it.times) },
                    from = from,
                    to = to,
                    days = chartDays(illness, to, zone),
                    markers = illness.names.mapNotNull { name ->
                        name.since?.takeIf { it > illness.startDate(zone) }?.let { TimeMarker(it.atStartOfDay(zone).toInstant().toEpochMilli(), "+ ${name.name}") }
                    },
                    lineColor = BabyLogTheme.chartColors.temperature,
                    description = "Температура: ${temperatures.size} ${plural(temperatures.size, "замер", "замера", "замеров")}, " +
                        "самая высокая ${max?.let { formatTemperature(it.tenths) }}, последняя ${formatTemperature(last.tenths)} ${at(last.start, now)}",
                )
            }
            onAdd?.let { TextButton(onClick = it) { Text("+ Температура") } }
        }
    }
}

/** Сутки на шкале графика: начало каждых суток болезни (первые — с её начала) и подпись «ср 7». */
private fun chartDays(illness: Illness, to: Long, zone: ZoneId): List<Pair<Long, String>> {
    val first = illness.startDate(zone)
    val last = to.toLocalDate(zone)
    return generateSequence(first) { it.plusDays(1) }.takeWhile { it <= last }.map { date ->
        maxOf(date.atStartOfDay(zone).toInstant().toEpochMilli(), illness.start) to date.format(DAY_LABEL)
    }.toList()
}

/** Лекарства болезни: название и доза, сколько раз и когда последний — только факты. [onAdd] — новый приём. */
@Composable
internal fun MedicinesCard(medicines: List<IllnessMedicine>, now: Long, onAdd: (() -> Unit)?) {
    val spacing = BabyLogTheme.spacing
    AppCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
            CardTitle(R.drawable.ic_medicine, "Лекарства")
            if (medicines.isEmpty()) {
                Text("Лекарства не записывали", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            medicines.forEachIndexed { index, medicine ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(Modifier.semantics(mergeDescendants = true) {}) {
                    Text(listOfNotNull(medicine.name, medicine.note.ifBlank { null }).joinToString(" · "), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "${medicine.count} ${plural(medicine.count, "раз", "раза", "раз")} · последний ${at(medicine.last, now)}" +
                            if (now - medicine.last < 24 * 60 * 60_000L) ", ${formatAgo(now - medicine.last)}" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            onAdd?.let { TextButton(onClick = it) { Text("+ Лекарство") } }
        }
    }
}

/**
 * «Вчера по дневнику» у идущей болезни, «В дни болезни» у прошлой: подгузники с мочой, кормления, сон
 * и плач — рядом «обычно» за две недели до болезни, если было с чем сравнить.
 */
@Composable
internal fun DiaryCard(diary: DiaryComparison) {
    val spacing = BabyLogTheme.spacing
    val colors = MaterialTheme.colorScheme
    AppCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
            CardTitle(
                R.drawable.ic_diary,
                if (diary.yesterday) "Вчера по дневнику" else "В дни болезни в среднем",
                "обычно — за 2 недели до болезни".takeIf { diary.lines.any { it.usual != null } },
            )
            diary.lines.forEach { line ->
                val (icon, what) = when (line.kind) {
                    DiaryLineKind.WET_DIAPERS -> R.drawable.ic_diaper to "Мокрых подгузников"
                    DiaryLineKind.FEEDINGS -> R.drawable.ic_feeding to "Кормлений"
                    DiaryLineKind.SLEEP -> R.drawable.ic_sleep to "Сон"
                    DiaryLineKind.CRYING -> R.drawable.ic_crying to "Плач"
                }
                Row(Modifier.semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(spacing.m)) {
                    AppIcon(icon, contentDescription = null, modifier = Modifier.size(RowIconSize), tint = colors.primary)
                    Column {
                        Text("$what — ${line.format(line.value)}", style = MaterialTheme.typography.bodyMedium)
                        line.usual?.let { Text("обычно ${line.format(it)}", style = MaterialTheme.typography.bodySmall, color = colors.tertiary) }
                    }
                }
            }
        }
    }
}

/**
 * Сутки болезни: записи свежие сверху — время, иконка, что было. Начало, конец и новое название —
 * цветом болезни. Касание записи — правка ([onEdit]).
 */
@Composable
internal fun DayCard(day: IllnessDay, sex: Sex?, onEdit: (BabyEvent) -> Unit) {
    val spacing = BabyLogTheme.spacing
    val colors = MaterialTheme.colorScheme
    AppCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = spacing.xs)) {
        if (day.entries.isEmpty()) {
            Text("Записей нет", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, modifier = Modifier.padding(horizontal = spacing.l, vertical = spacing.s))
        }
        day.entries.forEachIndexed { index, entry ->
            if (index > 0) HorizontalDivider(Modifier.padding(horizontal = spacing.l), color = colors.outlineVariant)
            val record = (entry as? DayEntry.Record)?.event
            val (icon, text, illnessTone) = entryLine(entry, sex)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (record != null) Modifier.clickable(onClickLabel = "изменить запись") { onEdit(record) } else Modifier)
                    .padding(horizontal = spacing.l, vertical = spacing.s),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(spacing.s),
            ) {
                Text(entry.time?.let(::formatClock).orEmpty(), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, modifier = Modifier.width(TimeWidth))
                AppIcon(icon, contentDescription = null, modifier = Modifier.size(RowIconSize), tint = if (illnessTone) colors.tertiary else colors.primary)
                Text(text, style = MaterialTheme.typography.bodyMedium, color = if (illnessTone) colors.tertiary else colors.onSurface, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** Иконка, текст строки суток и «цвет болезни» — у начала, конца и нового названия. */
private fun entryLine(entry: DayEntry, sex: Sex?): Triple<Int, String, Boolean> = when (entry) {
    is DayEntry.Started -> Triple(R.drawable.ic_illness, bySex(sex, "Заболел", "Заболела"), true)
    is DayEntry.Ended -> Triple(R.drawable.ic_illness, bySex(sex, "Выздоровел", "Выздоровела"), true)
    is DayEntry.NameAdded -> Triple(R.drawable.ic_add, "Название: ${entry.name.name}", true)
    is DayEntry.Record -> Triple(eventIcon(entry.event), recordText(entry.event, sex), false)
}

/** Запись болезни словами: «38,8 °C · подмышкой», «Ибупрофен · 4 мл», текст заметки с метками. */
private fun recordText(event: BabyEvent, sex: Sex?): String = when (event) {
    is Temperature -> formatTemperature(event.tenths) + (event.method?.let { " · ${temperatureMethodLabel(it)}" } ?: "")
    is Medicine -> listOfNotNull(event.name, event.note.ifBlank { null }).joinToString(" · ")
    is Note -> listOfNotNull(event.note.ifBlank { null }, event.tags.takeIf { it.isNotEmpty() }?.joinToString(", ")).joinToString(" · ").ifEmpty { "Заметка" }
    // В записях болезни только температура, лекарства и заметки (illnessRecords); остальное — как в «Дневнике»
    is Feeding, is Bottle, is Diaper, is Sleep, is Weight, is Height, is Crying, is Illness -> "${eventTitle(event, sex)} · ${eventTimeLine(event)}"
}
