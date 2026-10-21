package com.kid.babylog.feature.analytics

import com.kid.babylog.core.model.BabyEvent
import com.kid.babylog.core.model.Bottle
import com.kid.babylog.core.model.Crying
import com.kid.babylog.core.model.Diaper
import com.kid.babylog.core.model.Feeding
import com.kid.babylog.core.model.Height
import com.kid.babylog.core.model.Illness
import com.kid.babylog.core.model.Medicine
import com.kid.babylog.core.model.Note
import com.kid.babylog.core.model.Sex
import com.kid.babylog.core.model.Sleep
import com.kid.babylog.core.model.Temperature
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
    /** Вес и рост — за всё время; null — замеров нет. */
    val weight: GrowthModel?,
    val height: GrowthModel?,
    val medicines: List<MedicineLine>,
    /** Что было перед плачем и почему (ADR-0030); null — законченного плача за период нет. */
    val crying: CryingModel? = null,
    /** Метки плача и заметок (ADR-0031); null — меток и заметок у ребёнка нет: плитки нет. */
    val tags: TagsModel? = null,
    /** «Болела: 7–14 окт.» под выбором периода (ADR-0040); null — в периоде не болел(а). */
    val illnessNote: String? = null,
    /** Сон в дни болезни и без — в разделе «Сон»; null — сравнивать не с чем. */
    val illnessSleep: IllnessSleep? = null,
)

/**
 * [events] — записи одного ребёнка в любом порядке; [sex] — для «пописал / пописала».
 * Карточка «Бутылочка» есть, только если за период были бутылочки; «Плач» — если плач записывали
 * хоть раз: тем, кто его не отмечает, плитка с нулями не нужна.
 */
fun analyticsModel(events: List<BabyEvent>, period: AnalyticsPeriod, now: Long, zone: ZoneId, sex: Sex?): AnalyticsModel {
    if (events.isEmpty()) return AnalyticsModel(hasRecords = false, rhythm = null, metrics = emptyList(), weight = null, height = null, medicines = emptyList())
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val length = period.days
    val days: List<DayStats>
    val previous: List<DayStats>
    if (length == null) {
        // «Всё время» — с первой записи режима: вес и рост с рождения не растягивают графики на пустые недели
        val firstRoutine = events.filter { it.isRoutine }.minOfOrNull { it.start } ?: now
        // Записи «из будущего» (часы телефона спешили, опечатка в дате) период не сдвигают: он кончается сегодня
        val firstDay = minOf(Instant.ofEpochMilli(firstRoutine).atZone(zone).toLocalDate(), today)
        // Недели — с понедельника: столбики «всего времени» совпадают с календарными неделями
        days = dailyStats(events, firstDay.minusDays(firstDay.dayOfWeek.value - 1L), today, now, zone)
        previous = emptyList()
    } else {
        val both = dailyStats(events, today.minusDays(2L * length - 1), today, now, zone)
        previous = both.take(length)
        days = both.drop(length)
    }
    val week = if (length == RHYTHM_DAYS) days else dailyStats(events, today.minusDays(RHYTHM_DAYS - 1L), today, now, zone)
    val periodStart = days.first().date.atStartOfDay(zone).toInstant().toEpochMilli()
    val since = if (length == null) null else periodStart
    val cries = events.any { it is Crying }
    return AnalyticsModel(
        hasRecords = true,
        rhythm = week.takeIf { stats -> stats.any { it.tracked } }?.let { rhythmModel(events, it, now, zone) },
        metrics = Metric.entries
            .filter { it != Metric.BOTTLES || days.any { day -> day.bottles > 0 } }
            .filter { it != Metric.CRYING || cries }
            .map { metricModel(it, period, days, previous, sex) },
        weight = weightModel(events, now, zone),
        height = heightModel(events, now, zone),
        medicines = medicineLines(events, since, now, zone),
        crying = if (cries) cryingModel(events, periodStart, now + 1) else null,
        tags = tagsModel(events, days, byWeeks = length == null, zone),
        illnessNote = illnessNote(events, days.first().date, days.last().date, today, sex, zone),
        illnessSleep = illnessSleep(days, sex),
    )
}

/** Запись режима дня: по ним видно, что дневник в эти сутки вели ([DayStats.tracked]). */
private val BabyEvent.isRoutine: Boolean
    get() = when (this) {
        is Feeding, is Bottle, is Sleep, is Diaper -> true
        // Плач и заметки без сна и кормлений режим дня не описывают (ADR-0030, ADR-0031)
        is Medicine, is Weight, is Height, is Crying, is Note, is Illness, is Temperature -> false
    }
