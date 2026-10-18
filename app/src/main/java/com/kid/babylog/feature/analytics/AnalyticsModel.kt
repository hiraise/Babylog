package com.kid.babylog.feature.analytics

import com.kid.babylog.core.model.BabyEvent
import com.kid.babylog.core.model.Bottle
import com.kid.babylog.core.model.Diaper
import com.kid.babylog.core.model.Feeding
import com.kid.babylog.core.model.Medicine
import com.kid.babylog.core.model.Sex
import com.kid.babylog.core.model.Sleep
import com.kid.babylog.core.model.Weight
import java.time.Instant
import java.time.ZoneId

/**
 * Всё, что показывает вкладка «Аналитика» (ADR-0028). Считается из записей ребёнка на лету,
 * ничего не хранит: исправленная запись сразу меняет графики.
 */
data class AnalyticsModel(
    /** У ребёнка есть хоть одна запись; иначе вкладка — подсказка вместо графиков. */
    val hasRecords: Boolean,
    /** Ритм последних 7 суток — независимо от периода; null — за неделю записей нет. */
    val rhythm: RhythmModel?,
    val metrics: List<MetricModel>,
    /** Вес — за всё время; null — взвешиваний нет. */
    val weight: WeightModel?,
    val medicines: List<MedicineLine>,
)

/**
 * [events] — записи одного ребёнка в любом порядке; [sex] — для «пописал / пописала».
 * Карточка «Бутылочка» есть, только если за период были бутылочки.
 */
fun analyticsModel(events: List<BabyEvent>, period: AnalyticsPeriod, now: Long, zone: ZoneId, sex: Sex?): AnalyticsModel {
    if (events.isEmpty()) return AnalyticsModel(hasRecords = false, rhythm = null, metrics = emptyList(), weight = null, medicines = emptyList())
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val length = period.days
    val days: List<DayStats>
    val previous: List<DayStats>
    if (length == null) {
        // «Всё время» — с первой записи режима: вес с рождения не растягивает графики на пустые недели
        val firstRoutine = events.filter { it.isRoutine }.minOfOrNull { it.start } ?: now
        val firstDay = Instant.ofEpochMilli(firstRoutine).atZone(zone).toLocalDate()
        // Недели — с понедельника: столбики «всего времени» совпадают с календарными неделями
        days = dailyStats(events, firstDay.minusDays(firstDay.dayOfWeek.value - 1L), today, now, zone)
        previous = emptyList()
    } else {
        val both = dailyStats(events, today.minusDays(2L * length - 1), today, now, zone)
        previous = both.take(length)
        days = both.drop(length)
    }
    val week = if (length == RHYTHM_DAYS) days else dailyStats(events, today.minusDays(RHYTHM_DAYS - 1L), today, now, zone)
    val since = if (length == null) null else days.first().date.atStartOfDay(zone).toInstant().toEpochMilli()
    return AnalyticsModel(
        hasRecords = true,
        rhythm = week.takeIf { stats -> stats.any { it.tracked } }?.let { rhythmModel(events, it, now, zone) },
        metrics = Metric.entries
            .filter { it != Metric.BOTTLES || days.any { day -> day.bottles > 0 } }
            .map { metricModel(it, period, days, previous, sex) },
        weight = weightModel(events, now, zone),
        medicines = medicineLines(events, since, now, zone),
    )
}

/** Запись режима дня: по ним видно, что дневник в эти сутки вели ([DayStats.tracked]). */
private val BabyEvent.isRoutine: Boolean
    get() = when (this) {
        is Feeding, is Bottle, is Sleep, is Diaper -> true
        is Medicine, is Weight -> false
    }
