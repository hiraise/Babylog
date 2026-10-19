package com.kid.babylog.feature.summary

import com.kid.babylog.core.model.BabyEvent
import com.kid.babylog.core.model.Bottle
import com.kid.babylog.core.model.Breast
import com.kid.babylog.core.model.Crying
import com.kid.babylog.core.model.Diaper
import com.kid.babylog.core.model.DiaperKind
import com.kid.babylog.core.model.Feeding
import com.kid.babylog.core.model.Medicine
import com.kid.babylog.core.model.Sleep
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Итоги одного дня для одного ребёнка (ADR-0026): сколько и сколько всего. Что идёт сейчас и что
 * было последним, здесь нет — это показывают плитки «Сегодня».
 */
data class DaySummary(
    /** Кормления грудью, начатые в этот день; смена груди не считается новым кормлением ([feedingSessions]). */
    val breastFeedings: Int,
    /** Бутылочки этого дня (ADR-0025): сколько раз и сколько всего миллилитров. */
    val bottles: Int,
    val bottleMl: Int,
    val diapers: Map<DiaperKind, Int>,
    /** Сон, пересекающийся с этими сутками; активный считается до `now`. */
    val sleepMillis: Long,
    /** Лекарства: название из последней записи и число приёмов; названия сравниваются без регистра. */
    val medicines: List<Pair<String, Int>>,
    /** Плач (ADR-0030), начатый в этот день, и его время внутри суток; идущий — до `now`, как сон. */
    val cryings: Int = 0,
    val cryingMillis: Long = 0,
) {
    /** Все кормления дня: грудью и бутылочкой. */
    val feedings: Int get() = breastFeedings + bottles

    val diaperCount: Int get() = diapers.values.sum()
}

/** Кормление целиком: запись или несколько записей встык при смене груди (ADR-0015). */
data class FeedingSession(val start: Long, val end: Long?, val lastBreast: Breast) {
    val isActive: Boolean get() = end == null
}

/** Кормления по возрастанию начала: запись, начатая ровно в конце предыдущей, — её продолжение. */
fun feedingSessions(events: List<BabyEvent>): List<FeedingSession> {
    val sessions = mutableListOf<FeedingSession>()
    events.filterIsInstance<Feeding>()
        .sortedWith(compareBy<Feeding> { it.start }.thenBy { it.id })
        .forEach { feeding ->
            val last = sessions.lastOrNull()
            if (last?.end != null && last.end == feeding.start) {
                sessions[sessions.lastIndex] = last.copy(end = feeding.end, lastBreast = feeding.breast)
            } else {
                sessions += FeedingSession(feeding.start, feeding.end, feeding.breast)
            }
        }
    return sessions
}

/** [events] — записи одного ребёнка в любом порядке. */
fun summarizeDay(events: List<BabyEvent>, date: LocalDate, now: Long, zone: ZoneId): DaySummary {
    val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
    val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    // Новые сверху: так «последнее» — первое в списке
    val newestFirst = events.sortedWith(compareByDescending<BabyEvent> { it.start }.thenByDescending { it.id })
    val dayEvents = newestFirst.filter { it.start in dayStart until dayEnd }

    val feedings = feedingSessions(events)
    val dayBottles = dayEvents.filterIsInstance<Bottle>()
    val sleeps = events.filterIsInstance<Sleep>()
    val sleepMillis = sleeps.sumOf { sleep ->
        val start = maxOf(sleep.start, dayStart)
        val end = minOf(sleep.end ?: now, dayEnd)
        (end - start).coerceAtLeast(0)
    }

    val cryings = events.filterIsInstance<Crying>()
    val cryingMillis = cryings.sumOf { crying ->
        (minOf(crying.end ?: now, dayEnd) - maxOf(crying.start, dayStart)).coerceAtLeast(0)
    }

    val medicines = dayEvents.filterIsInstance<Medicine>()
        .groupBy { it.name.trim().lowercase() }
        .values
        .map { group -> group.first().name.trim() to group.size }

    return DaySummary(
        breastFeedings = feedings.count { it.start in dayStart until dayEnd },
        bottles = dayBottles.size,
        bottleMl = dayBottles.sumOf { it.volumeMl },
        diapers = dayEvents.filterIsInstance<Diaper>().groupingBy { it.kind }.eachCount(),
        sleepMillis = sleepMillis,
        medicines = medicines,
        cryings = cryings.count { it.start in dayStart until dayEnd },
        cryingMillis = cryingMillis,
    )
}
