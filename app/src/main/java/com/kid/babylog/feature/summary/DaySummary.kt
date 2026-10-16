package com.kid.babylog.feature.summary

import com.kid.babylog.core.model.BabyEvent
import com.kid.babylog.core.model.Diaper
import com.kid.babylog.core.model.DiaperKind
import com.kid.babylog.core.model.Feeding
import com.kid.babylog.core.model.Medicine
import com.kid.babylog.core.model.Sleep
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Итоги одного дня для одного ребёнка. */
data class DaySummary(
    val isToday: Boolean,
    val feedings: Int,
    /** Последнее кормление вообще, не только за этот день; есть только у сегодняшней сводки. */
    val lastFeeding: Feeding?,
    val diapers: Map<DiaperKind, Int>,
    /** Сон, пересекающийся с этими сутками; активный считается до `now`. */
    val sleepMillis: Long,
    val sleepingNow: Boolean,
    /** Лекарства: название из последней записи и число приёмов; названия сравниваются без регистра. */
    val medicines: List<Pair<String, Int>>,
) {
    val diaperCount: Int get() = diapers.values.sum()
}

/** [events] — записи одного ребёнка в любом порядке. */
fun summarizeDay(events: List<BabyEvent>, date: LocalDate, now: Long, zone: ZoneId): DaySummary {
    val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
    val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val isToday = date == Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    // Новые сверху: так «последнее» — первое в списке
    val newestFirst = events.sortedWith(compareByDescending<BabyEvent> { it.start }.thenByDescending { it.id })
    val dayEvents = newestFirst.filter { it.start in dayStart until dayEnd }

    val sleeps = events.filterIsInstance<Sleep>()
    val sleepMillis = sleeps.sumOf { sleep ->
        val start = maxOf(sleep.start, dayStart)
        val end = minOf(sleep.end ?: now, dayEnd)
        (end - start).coerceAtLeast(0)
    }

    val medicines = dayEvents.filterIsInstance<Medicine>()
        .groupBy { it.name.trim().lowercase() }
        .values
        .map { group -> group.first().name.trim() to group.size }

    return DaySummary(
        isToday = isToday,
        feedings = dayEvents.count { it is Feeding },
        lastFeeding = if (isToday) newestFirst.firstNotNullOfOrNull { it as? Feeding } else null,
        diapers = dayEvents.filterIsInstance<Diaper>().groupingBy { it.kind }.eachCount(),
        sleepMillis = sleepMillis,
        sleepingNow = isToday && sleeps.any { it.isActive },
        medicines = medicines,
    )
}
