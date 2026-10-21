package com.kid.babylog.feature.illness

import com.kid.babylog.core.model.Child
import com.kid.babylog.core.model.Temperature
import com.kid.babylog.core.model.Weight
import com.kid.babylog.core.model.sinceDate
import com.kid.babylog.core.model.startDate
import com.kid.babylog.core.ui.bySex
import com.kid.babylog.core.ui.formatAge
import com.kid.babylog.core.ui.formatClock
import com.kid.babylog.core.ui.formatDayRange
import com.kid.babylog.core.ui.formatWeight
import com.kid.babylog.core.ui.plural
import com.kid.babylog.core.ui.temperatureValue
import com.kid.babylog.core.ui.toLocalDate
import java.time.LocalDate
import java.time.ZoneId

/**
 * Сводка болезни для врача (ADR-0040) — обычный текст для системного «Поделиться»: кто, с какого дня
 * и что за болезнь, температура (самая высокая, последняя, по дням), лекарства, «по дневнику» против
 * обычного и заметки. Только записи — без выводов: что они значат, решает врач.
 */
fun doctorText(model: IllnessScreenModel, child: Child, lastWeight: Weight?, zone: ZoneId): String {
    val illness = model.illness
    val today = model.today
    val first = illness.startDate(zone)
    val blocks = mutableListOf<String>()

    val who = listOfNotNull(
        child.name.trim(),
        child.birthDate?.let { formatAge(it.toLocalDate(zone), today, child.sex) },
        lastWeight?.let { formatWeight(it.grams) },
    ).joinToString(", ")
    val (fromStart, later) = illness.names.partition { it.sinceDate(illness, zone) <= first }
    val names = listOfNotNull(
        fromStart.joinToString(", ") { it.name }.ifEmpty { null },
        later.joinToString(", ") { "с ${day(it.sinceDate(illness, zone))} — ${it.name}" }.ifEmpty { null },
    ).joinToString(", ")
    val period = if (illness.isActive) {
        "Болеет с ${day(first)} (${model.days}-й день)"
    } else {
        "${bySex(child.sex, "Болел", "Болела")} ${formatDayRange(first, first.plusDays(model.days - 1L))} " +
            "(${model.days} ${plural(model.days, "день", "дня", "дней")})"
    }
    blocks += "$who\n$period" + (if (names.isEmpty()) "." else ": $names.")

    temperatureBlock(model.temperatures, zone)?.let { blocks += it }

    if (model.medicines.isNotEmpty()) {
        blocks += "Лекарства:\n" + model.medicines.sortedBy { it.times.first() }.joinToString("\n") { medicine ->
            val dose = medicine.note.ifBlank { null }?.let { " $it" } ?: ""
            "• ${medicine.name}$dose — ${medicine.count} ${plural(medicine.count, "раз", "раза", "раз")}, " +
                "последний ${day(medicine.last.toLocalDate(zone))} в ${formatClock(medicine.last)}"
        }
    }

    model.diary?.let { diary ->
        val parts = diary.lines.joinToString(", ") { line ->
            val what = when (line.kind) {
                DiaryLineKind.WET_DIAPERS -> "мокрых подгузников"
                DiaryLineKind.FEEDINGS -> "кормлений"
                DiaryLineKind.SLEEP -> "сон"
                DiaryLineKind.CRYING -> "плач"
            }
            "$what ${line.format(line.value)}" + (line.usual?.let { " (обычно ${line.format(it)})" } ?: "")
        }
        blocks += (if (diary.yesterday) "Вчера" else "В дни болезни в среднем") + ": $parts."
    }

    if (model.notes.isNotEmpty()) {
        blocks += "Заметки:\n" + model.notes.joinToString("\n") { note ->
            val text = listOfNotNull(note.note.ifBlank { null }, note.tags.takeIf { it.isNotEmpty() }?.joinToString(", ", "[", "]"))
                .joinToString(" ").ifEmpty { "заметка" }
            "${day(note.start.toLocalDate(zone))}, ${formatClock(note.start)} — $text"
        }
    }
    return blocks.joinToString("\n\n")
}

/**
 * «Температура: макс. 39,2 (10 окт., 03:10), последняя 38,8 (10 окт., 10:40).» и ниже — самая высокая
 * за каждые сутки: «По дням: 7 окт. — 38,4 · 8 — 38,6». Замеров нет — null.
 */
private fun temperatureBlock(temperatures: List<Temperature>, zone: ZoneId): String? {
    if (temperatures.isEmpty()) return null
    val max = temperatures.maxWith(compareBy<Temperature> { it.tenths }.thenBy { it.start })
    val last = temperatures.maxBy { it.start }
    fun at(t: Temperature) = "${temperatureValue(t.tenths)} (${day(t.start.toLocalDate(zone))}, ${formatClock(t.start)})"
    val head = "Температура: макс. ${at(max)}" + if (last != max) ", последняя ${at(last)}." else "."
    val byDay = temperatures.groupBy { it.start.toLocalDate(zone) }.toSortedMap().entries
    if (byDay.size < 2) return head
    var month = -1
    val days = byDay.joinToString(" · ") { (date, list) ->
        val label = if (date.monthValue == month) date.dayOfMonth.toString() else day(date)
        month = date.monthValue
        "$label — ${temperatureValue(list.maxOf { it.tenths })}"
    }
    return "$head\nПо дням: $days"
}

/** «7 окт.» */
private fun day(date: LocalDate): String = formatDayRange(date, date)
