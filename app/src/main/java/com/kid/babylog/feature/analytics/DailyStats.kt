package com.kid.babylog.feature.analytics

import com.kid.babylog.core.model.BabyEvent
import com.kid.babylog.core.model.Bottle
import com.kid.babylog.core.model.Crying
import com.kid.babylog.core.model.Diaper
import com.kid.babylog.core.model.DiaperKind
import com.kid.babylog.core.model.Feeding
import com.kid.babylog.core.model.Height
import com.kid.babylog.core.model.Medicine
import com.kid.babylog.core.model.Note
import com.kid.babylog.core.model.Sleep
import com.kid.babylog.core.model.Weight
import com.kid.babylog.feature.summary.feedingSessions
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Итоги одних суток для графиков (ADR-0028). Считаются так же, как сводка дня (`summarizeDay`):
 * кормление — сессия груди или бутылочка, начатые в эти сутки; сон — его часть внутри суток,
 * идущий — до `now`. Сводка и график одного дня показывают одни и те же числа.
 */
data class DayStats(
    val date: LocalDate,
    /**
     * В эти сутки начались кормление, бутылочка, сон или подгузник. Сутки без таких записей —
     * «дневник не вели», а не нули: одно взвешивание или лекарство режим дня не описывает.
     */
    val tracked: Boolean,
    /** Сутки закончились — это не сегодня. */
    val complete: Boolean,
    val breastFeedings: Int,
    val bottles: Int,
    val bottleMl: Int,
    /**
     * Время у груди; null — неизвестно: у какого-то кормления этих суток нет длительности
     * (записи до ADR-0015, у них конец равен началу) или оно ещё идёт. Длительность не придумываем.
     */
    val breastMillis: Long?,
    val sleepMillis: Long,
    /** Самый долгий законченный сон, начатый в эти сутки. */
    val longestSleepMillis: Long,
    val diapers: Map<DiaperKind, Int>,
    /** Начала кормлений — сессий груди и бутылочек — по возрастанию. */
    val feedingStarts: List<Long>,
    /** Начала снов, начатых в эти сутки, по возрастанию: сколько раз спал и как часто. */
    val sleepStarts: List<Long> = emptyList(),
    /** Плач (ADR-0030), начатый в эти сутки: сколько раз и во сколько — минуты от полуночи. */
    val cryings: Int = 0,
    val cryingStartMinutes: List<Int> = emptyList(),
    /** Плач внутри суток, как сон: через полночь делится, идущий — до `now`. */
    val cryingMillis: Long = 0,
) {
    val feedings: Int get() = breastFeedings + bottles
    val sleeps: Int get() = sleepStarts.size
    val diaperCount: Int get() = diapers.values.sum()

    /** Сутки идут в средние, только если закончились и в них вели дневник. */
    val countable: Boolean get() = tracked && complete
}

private class DayAccumulator {
    var tracked = false
    var breastFeedings = 0
    var bottles = 0
    var bottleMl = 0
    var breastMillis = 0L
    var breastUnknown = false
    var sleepMillis = 0L
    var longestSleepMillis = 0L
    val diapers = mutableMapOf<DiaperKind, Int>()
    val feedingStarts = mutableListOf<Long>()
    val sleepStarts = mutableListOf<Long>()
    var cryings = 0
    val cryingStartMinutes = mutableListOf<Int>()
    var cryingMillis = 0L
}

/**
 * Итоги каждых суток с [from] по [to] включительно, по порядку. [events] — записи одного ребёнка
 * в любом порядке. Один проход по записям: на «всё время» за год это доли секунды.
 */
fun dailyStats(events: List<BabyEvent>, from: LocalDate, to: LocalDate, now: Long, zone: ZoneId): List<DayStats> {
    if (to < from) return emptyList()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val days = generateSequence(from) { it.plusDays(1) }.takeWhile { it <= to }.associateWith { DayAccumulator() }
    fun dateOf(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    fun startOf(date: LocalDate): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()

    /** Часть отрезка [start, end) внутри каждых суток периода. */
    fun spread(start: Long, end: Long, add: DayAccumulator.(Long) -> Unit) {
        var date = maxOf(dateOf(start), from)
        val last = minOf(dateOf(end), to)
        while (date <= last) {
            val overlap = minOf(end, startOf(date.plusDays(1))) - maxOf(start, startOf(date))
            if (overlap > 0) days.getValue(date).add(overlap)
            date = date.plusDays(1)
        }
    }

    feedingSessions(events).forEach { session ->
        days[dateOf(session.start)]?.apply {
            breastFeedings++
            feedingStarts += session.start
        }
    }
    events.forEach { event ->
        when (event) {
            is Feeding -> days[dateOf(event.start)]?.apply {
                tracked = true
                val end = event.end
                if (end == null || end <= event.start) breastUnknown = true else breastMillis += end - event.start
            }
            is Bottle -> days[dateOf(event.start)]?.apply {
                tracked = true
                bottles++
                bottleMl += event.volumeMl
                feedingStarts += event.start
            }
            is Diaper -> days[dateOf(event.start)]?.apply {
                tracked = true
                diapers.merge(event.kind, 1, Int::plus)
            }
            is Sleep -> {
                days[dateOf(event.start)]?.apply {
                    tracked = true
                    sleepStarts += event.start
                }
                val end = event.end ?: now
                spread(event.start, end) { sleepMillis += it }
                if (event.end != null) {
                    days[dateOf(event.start)]?.apply { longestSleepMillis = maxOf(longestSleepMillis, end - event.start) }
                }
            }
            // Плач — не запись режима: сутки только с плачем дневником не считаются (tracked не ставим)
            is Crying -> {
                val local = Instant.ofEpochMilli(event.start).atZone(zone)
                days[local.toLocalDate()]?.apply {
                    cryings++
                    cryingStartMinutes += local.hour * 60 + local.minute
                }
                spread(event.start, event.end ?: now) { cryingMillis += it }
            }
            // Вес, лекарства и заметки в суточные итоги не входят: у них свои карточки (у заметок — «Метки», ADR-0031)
            is Medicine, is Weight, is Height, is Note -> Unit
        }
    }
    return days.map { (date, day) ->
        DayStats(
            date = date,
            tracked = day.tracked,
            complete = date < today,
            breastFeedings = day.breastFeedings,
            bottles = day.bottles,
            bottleMl = day.bottleMl,
            breastMillis = day.breastMillis.takeUnless { day.breastUnknown },
            sleepMillis = day.sleepMillis,
            longestSleepMillis = day.longestSleepMillis,
            diapers = day.diapers.toMap(),
            feedingStarts = day.feedingStarts.sorted(),
            sleepStarts = day.sleepStarts.sorted(),
            cryings = day.cryings,
            cryingStartMinutes = day.cryingStartMinutes.sorted(),
            cryingMillis = day.cryingMillis,
        )
    }
}
