package com.kid.babylog.feature.analytics

import com.kid.babylog.core.model.BabyEvent
import com.kid.babylog.core.model.Bottle
import com.kid.babylog.core.model.Crying
import com.kid.babylog.core.model.LONGER_THAN_USUAL
import com.kid.babylog.core.model.MAX_AWAKE_MILLIS
import com.kid.babylog.core.model.awakeWindows
import com.kid.babylog.core.model.lastFeedingEndBefore
import com.kid.babylog.core.model.lastSleepEndBefore
import com.kid.babylog.core.model.median
import com.kid.babylog.core.model.peakHours
import com.kid.babylog.feature.summary.feedingSessions
import java.time.Instant
import java.time.ZoneId

/** Причин и «что помогло» на карточке — не больше стольких: дальше редкие, они только удлиняют список. */
private const val TOP_ITEMS = 5

/**
 * Экран раздела «Плач» (ADR-0030): что было перед плачем и почему, по законченному плачу периода.
 * Медианы, а не средние: пропущенная запись сна или кормления их почти не сдвигает.
 */
data class CryingModel(
    /** Сколько раз плакал за период — из стольких считаны остальные числа. */
    val episodes: Int,
    /** Бодрствование перед плачем и обычное окно бодрствования; null — не из чего считать. */
    val awakeBefore: Long?,
    val usualAwake: Long?,
    /** У скольких раз из [episodes] известно бодрствование перед плачем: был сон не раньше чем за 6 ч. */
    val awakeEpisodes: Int,
    /** Сколько раз из [awakeEpisodes] плач начинался после бодрствования дольше обычного. */
    val longerThanUsual: Int,
    /** Сколько прошло после кормления и обычный промежуток между кормлениями. */
    val sinceFeeding: Long?,
    val feedingInterval: Long?,
    /** Метки и «что помогло»: сколько раз встречались, частые первыми. */
    val reasons: List<Pair<String, Int>>,
    val helped: List<Pair<String, Int>>,
)

/** Плач, начатый в `[from, to)`; null — законченного плача в этот период нет. */
fun cryingModel(events: List<BabyEvent>, from: Long, to: Long): CryingModel? {
    val cryings = events.filterIsInstance<Crying>().filter { it.end != null && it.start in from until to }
    if (cryings.isEmpty()) return null
    val usual = events.awakeWindows(from, to).median()
    val awake = cryings.mapNotNull { crying ->
        events.lastSleepEndBefore(crying.start)?.let { crying.start - it }?.takeIf { it <= MAX_AWAKE_MILLIS }
    }

    val sinceFeeding = cryings.mapNotNull { crying ->
        events.lastFeedingEndBefore(crying.start)?.let { crying.start - it }?.takeIf { it <= MAX_AWAKE_MILLIS }
    }
    val feedingStarts = (feedingSessions(events).map { it.start } + events.filterIsInstance<Bottle>().map { it.start })
        .filter { it in from until to }
        .sorted()

    return CryingModel(
        episodes = cryings.size,
        awakeBefore = awake.median(),
        usualAwake = usual,
        awakeEpisodes = awake.size,
        longerThanUsual = if (usual == null) 0 else awake.count { it > usual * LONGER_THAN_USUAL },
        sinceFeeding = sinceFeeding.median(),
        feedingInterval = feedingStarts.zipWithNext { a, b -> b - a }.median(),
        reasons = counted(cryings.flatMap { it.reasons }),
        helped = counted(cryings.flatMap { it.helped }),
    )
}

private fun counted(items: List<String>): List<Pair<String, Int>> =
    items.groupingBy { it }.eachCount().entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .take(TOP_ITEMS)
        .map { it.key to it.value }

/** Плач за неделю одной строкой: [perDay] — «11 мин» в сутки, [note] — «0,7 раза в сутки · чаще с 19 до 22 ч». */
data class CryingWeekSummary(val perDay: String, val note: String?)

/**
 * Плач за последние 7 суток для карточки над лентой «Заметок» (ADR-0031) — те же числа, что у плитки
 * «Плач» за неделю. null — плач не записывали или полных суток с записями за неделю нет.
 */
fun cryingWeekSummary(events: List<BabyEvent>, now: Long, zone: ZoneId): CryingWeekSummary? {
    if (events.none { it is Crying }) return null
    val days = AnalyticsPeriod.WEEK.days ?: return null
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val both = dailyStats(events, today.minusDays(2L * days - 1), today, now, zone)
    val week = both.drop(days)
    if (week.none { it.countable }) return null
    val model = metricModel(Metric.CRYING, AnalyticsPeriod.WEEK, week, both.take(days), sex = null)
    return CryingWeekSummary(model.value, model.note)
}

/** «чаще с 18 до 21 ч» — когда начинался плач ([peakHours]); [starts] — минуты от полуночи. */
internal fun cryingPeak(starts: List<Int>): String? = peakHours(starts)?.let { "чаще с ${it.from} до ${it.to} ч" }
