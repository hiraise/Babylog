package com.kid.babylog.feature.illness

import com.kid.babylog.core.model.BabyEvent
import com.kid.babylog.core.model.Crying
import com.kid.babylog.core.model.DiaperKind
import com.kid.babylog.core.model.Illness
import com.kid.babylog.core.model.IllnessMedicine
import com.kid.babylog.core.model.IllnessName
import com.kid.babylog.core.model.Note
import com.kid.babylog.core.model.Temperature
import com.kid.babylog.core.model.USUAL_DAYS
import com.kid.babylog.core.model.illnessMedicines
import com.kid.babylog.core.model.illnessRecords
import com.kid.babylog.core.model.lastDate
import com.kid.babylog.core.model.sinceDate
import com.kid.babylog.core.model.startDate
import com.kid.babylog.core.ui.formatDuration
import com.kid.babylog.feature.summary.DaySummary
import com.kid.babylog.feature.summary.summarizeDay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** «Обычно» — только если до болезни столько суток с записями: по одному-двум дням это ещё не «обычно». */
private const val MIN_USUAL_DAYS = 3

/**
 * Карточка болезни на «Сегодня» (ADR-0040): какой день, названия (null — без названия), последняя
 * температура и последнее лекарство — только факты, без оценок.
 */
data class IllnessCardModel(
    val illness: Illness,
    val day: Int,
    val names: String?,
    val lastTemperature: Temperature?,
    val lastMedicine: IllnessMedicine?,
)

fun illnessCardModel(events: List<BabyEvent>, illness: Illness, now: Long, zone: ZoneId): IllnessCardModel {
    val today = dateOf(now, zone)
    val records = events.illnessRecords(illness, now)
    return IllnessCardModel(
        illness = illness,
        day = (today.toEpochDay() - illness.startDate(zone).toEpochDay() + 1).toInt(),
        names = illness.names.joinToString(", ") { it.name }.ifEmpty { null },
        lastTemperature = records.filterIsInstance<Temperature>().maxByOrNull { it.start },
        lastMedicine = events.illnessMedicines(illness, now).firstOrNull(),
    )
}

/** Что записано за сутки и что обычно: сравнение «по дневнику» на экране болезни. */
data class DiaryLine(val kind: DiaryLineKind, val value: Double, val usual: Double?)

enum class DiaryLineKind { WET_DIAPERS, FEEDINGS, SLEEP, CRYING }

/**
 * «По дневнику» (ADR-0040): у идущей болезни — вчера, у прошлой — в среднем за дни болезни; рядом —
 * обычно, за [USUAL_DAYS] суток до болезни. [yesterday] — сравниваются только вчерашние сутки.
 */
data class DiaryComparison(val yesterday: Boolean, val lines: List<DiaryLine>)

/** Строка «По дням»: запись болезни, добавленное название, начало или конец. */
sealed interface DayEntry {
    val time: Long?

    data class Record(val event: BabyEvent) : DayEntry {
        override val time: Long get() = event.start
    }

    data class NameAdded(val name: IllnessName) : DayEntry {
        override val time: Long? get() = null
    }

    data class Started(override val time: Long) : DayEntry

    data class Ended(override val time: Long) : DayEntry
}

/** Сутки болезни на её экране: какой это день болезни и что в нём было, свежее сверху. */
data class IllnessDay(val date: LocalDate, val number: Int, val entries: List<DayEntry>)

/**
 * Экран болезни (ADR-0040): записи болезни ([illnessRecords]), температура по времени, лекарства, «по
 * дневнику» и сутки со всем, что в них было, — свежие сверху.
 */
data class IllnessScreenModel(
    val illness: Illness,
    val today: LocalDate,
    val days: Int,
    val temperatures: List<Temperature>,
    val medicines: List<IllnessMedicine>,
    val notes: List<Note>,
    val diary: DiaryComparison?,
    val byDay: List<IllnessDay>,
)

fun illnessScreenModel(events: List<BabyEvent>, illness: Illness, now: Long, zone: ZoneId): IllnessScreenModel {
    val today = dateOf(now, zone)
    val records = events.illnessRecords(illness, now)
    val first = illness.startDate(zone)
    val last = maxOf(illness.lastDate(today, zone), records.maxOfOrNull { dateOf(it.start, zone) } ?: first)
    val byDay = generateSequence(last) { it.minusDays(1) }.takeWhile { it >= first }.map { date ->
        val entries = buildList {
            illness.end?.takeIf { dateOf(it, zone) == date }?.let { add(DayEntry.Ended(it)) }
            records.filter { dateOf(it.start, zone) == date }.sortedByDescending { it.start }.forEach { add(DayEntry.Record(it)) }
            illness.names.filter { it.sinceDate(illness, zone) == date }.forEach { add(DayEntry.NameAdded(it)) }
            if (date == first) add(DayEntry.Started(illness.start))
        }
        IllnessDay(date, (date.toEpochDay() - first.toEpochDay() + 1).toInt(), entries)
    }.toList()
    return IllnessScreenModel(
        illness = illness,
        today = today,
        days = (illness.lastDate(today, zone).toEpochDay() - first.toEpochDay() + 1).toInt(),
        temperatures = records.filterIsInstance<Temperature>(),
        medicines = events.illnessMedicines(illness, now),
        notes = records.filterIsInstance<Note>(),
        diary = diaryComparison(events, illness, now, zone),
        byDay = byDay,
    )
}

/**
 * «По дневнику»: подгузники с мочой (мокрые и «и то, и то»), кормления, сон и — если плач записывают —
 * плач. Сутки без записей режима не считаются ни «вчера», ни «обычно». null — сравнивать нечего: болезнь
 * началась сегодня, или вчера дневник не вели.
 */
internal fun diaryComparison(events: List<BabyEvent>, illness: Illness, now: Long, zone: ZoneId): DiaryComparison? {
    val today = dateOf(now, zone)
    val first = illness.startDate(zone)
    val yesterday = illness.isActive
    val sick = if (yesterday) {
        listOf(today.minusDays(1)).filter { it >= first }
    } else {
        generateSequence(first) { it.plusDays(1) }.takeWhile { it <= illness.lastDate(today, zone) && it < today }.toList()
    }
    val sickDays = sick.map { summarizeDay(events, it, now, zone) }.filter { it.tracked }
    if (sickDays.isEmpty()) return null
    val usualDays = (1..USUAL_DAYS).map { summarizeDay(events, first.minusDays(it), now, zone) }.filter { it.tracked }
        .takeIf { it.size >= MIN_USUAL_DAYS }
    val cries = events.any { it is Crying }
    val lines = DiaryLineKind.entries.filter { it != DiaryLineKind.CRYING || cries }.map { kind ->
        DiaryLine(kind, sickDays.map { it.value(kind) }.average(), usualDays?.map { it.value(kind) }?.average())
    }
    return DiaryComparison(yesterday, lines)
}

private val DaySummary.tracked: Boolean get() = feedings > 0 || diaperCount > 0 || sleepMillis > 0

private fun DaySummary.value(kind: DiaryLineKind): Double = when (kind) {
    DiaryLineKind.WET_DIAPERS -> ((diapers[DiaperKind.WET] ?: 0) + (diapers[DiaperKind.MIXED] ?: 0)).toDouble()
    DiaryLineKind.FEEDINGS -> feedings.toDouble()
    DiaryLineKind.SLEEP -> sleepMillis.toDouble()
    DiaryLineKind.CRYING -> cryingMillis.toDouble()
}

/** Значение строки «по дневнику» словами: «5», «10 ч 50 мин», среднее — с округлением до целого. */
internal fun DiaryLine.format(value: Double): String = when (kind) {
    DiaryLineKind.WET_DIAPERS, DiaryLineKind.FEEDINGS -> value.roundToInt().toString()
    // «0 мин», а не «меньше минуты»: плача в эти сутки не было
    DiaryLineKind.SLEEP, DiaryLineKind.CRYING -> if (value < 60_000) "0 мин" else formatDuration(value.roundToLong())
}

internal fun dateOf(millis: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
