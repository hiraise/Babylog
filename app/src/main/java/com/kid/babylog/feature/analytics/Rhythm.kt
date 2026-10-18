package com.kid.babylog.feature.analytics

import com.kid.babylog.core.model.BabyEvent
import com.kid.babylog.core.model.Bottle
import com.kid.babylog.core.model.Diaper
import com.kid.babylog.core.model.Feeding
import com.kid.babylog.core.model.Medicine
import com.kid.babylog.core.model.Sleep
import com.kid.babylog.core.model.Weight
import com.kid.babylog.core.ui.RU
import com.kid.babylog.core.ui.formatDuration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** Отрезок суток в долях: 0 — начало суток, 1 — следующая полночь. */
data class DaySpan(val from: Float, val to: Float)

/**
 * Строка «Ритма дня»: сон и кормления грудью — полосами, бутылочки и кормления без длительности
 * (до ADR-0015) — рисками, подгузники — точками; [end] — докуда сутки уже прошли (у сегодня — до `now`).
 */
data class RhythmDay(
    val date: LocalDate,
    val label: String,
    val sleeps: List<DaySpan>,
    val feedings: List<DaySpan>,
    val instantFeedings: List<Float>,
    val diapers: List<Float>,
    val end: Float,
    val description: String,
)

/** «Ритм дня» за последние [RHYTHM_DAYS] суток, сегодня сверху; [summary] — средние ночи одной строкой. */
data class RhythmModel(val days: List<RhythmDay>, val summary: String?)

const val RHYTHM_DAYS = 7

/** Перерыв в сне до часа (ночное кормление, смена подгузника) не прерывает ночь. */
private const val NIGHT_GAP_MILLIS = 60 * 60_000L

/** Ночь суток — сон, захватывающий отрезок от полуночи до этого часа. */
private const val NIGHT_CORE_END_HOUR = 5L

/** Средние засыпания и пробуждения показываются, если ночей не меньше стольких. */
private const val MIN_NIGHTS = 3

private val ROW_LABEL = DateTimeFormatter.ofPattern("EE d", RU)
private val DAY_NAME = DateTimeFormatter.ofPattern("EE, d MMM", RU)
private val CLOCK = DateTimeFormatter.ofPattern("HH:mm")

/** [stats] — итоги тех же суток ([dailyStats]), по возрастанию даты: из них подпись строки для TalkBack. */
fun rhythmModel(events: List<BabyEvent>, stats: List<DayStats>, now: Long, zone: ZoneId): RhythmModel {
    val days = stats.reversed().map { day -> rhythmDay(events, day, now, zone) }
    return RhythmModel(days, rhythmSummary(events, stats.map { it.date }, now, zone))
}

private fun rhythmDay(events: List<BabyEvent>, day: DayStats, now: Long, zone: ZoneId): RhythmDay {
    val start = day.date.atStartOfDay(zone).toInstant().toEpochMilli()
    val end = day.date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val visibleEnd = minOf(end, now)
    fun fraction(t: Long) = ((t - start).toFloat() / (end - start)).coerceIn(0f, 1f)
    fun span(from: Long, to: Long): DaySpan? =
        if (to <= start || from >= visibleEnd) null else DaySpan(fraction(maxOf(from, start)), fraction(minOf(to, visibleEnd)))
    fun instant(t: Long): Float? = if (t in start until visibleEnd) fraction(t) else null

    val sleeps = mutableListOf<DaySpan>()
    val feedings = mutableListOf<DaySpan>()
    val instants = mutableListOf<Float>()
    val diapers = mutableListOf<Float>()
    events.forEach { event ->
        when (event) {
            is Sleep -> span(event.start, event.end ?: now)?.let(sleeps::add)
            is Feeding -> {
                val feedingEnd = event.end
                if (feedingEnd != null && feedingEnd <= event.start) {
                    instant(event.start)?.let(instants::add)
                } else {
                    span(event.start, feedingEnd ?: now)?.let(feedings::add)
                }
            }
            is Bottle -> instant(event.start)?.let(instants::add)
            is Diaper -> instant(event.start)?.let(diapers::add)
            is Medicine, is Weight -> Unit
        }
    }
    val name = if (day.complete) day.date.format(DAY_NAME) else "сегодня"
    return RhythmDay(
        date = day.date,
        label = if (day.complete) day.date.format(ROW_LABEL) else "сегодня",
        sleeps = sleeps,
        feedings = feedings,
        instantFeedings = instants,
        diapers = diapers,
        end = fraction(visibleEnd),
        description = if (!day.tracked) {
            "$name: записей нет"
        } else {
            "$name: сон ${formatDuration(day.sleepMillis)}, " +
                "${day.feedings} ${plural(day.feedings, "кормление", "кормления", "кормлений")}, " +
                "${day.diaperCount} ${plural(day.diaperCount, "подгузник", "подгузника", "подгузников")}"
        },
    )
}

/** Ночной сон: от засыпания до утреннего пробуждения, [wakings] — сколько раз просыпался между. */
internal data class Night(val start: Long, val end: Long, val wakings: Int)

private class SleepBlock(val start: Long, var end: Long, var pieces: Int, var active: Boolean)

/**
 * Ночи, закончившиеся утром суток [dates]. Ночь — подряд идущие сны с перерывами до часа, которые
 * захватывают отрезок с полуночи до 5:00 (из них — с наибольшим захватом). Ночь, которая ещё идёт, не в счёт.
 */
internal fun nights(events: List<BabyEvent>, dates: List<LocalDate>, now: Long, zone: ZoneId): List<Night> {
    val blocks = mutableListOf<SleepBlock>()
    events.filterIsInstance<Sleep>().sortedBy { it.start }.forEach { sleep ->
        val end = sleep.end ?: now
        val last = blocks.lastOrNull()
        if (last != null && sleep.start - last.end <= NIGHT_GAP_MILLIS) {
            last.end = maxOf(last.end, end)
            last.pieces++
            last.active = last.active || sleep.isActive
        } else {
            blocks += SleepBlock(sleep.start, end, pieces = 1, active = sleep.isActive)
        }
    }
    return dates.mapNotNull { date ->
        val coreStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val coreEnd = date.atStartOfDay(zone).plusHours(NIGHT_CORE_END_HOUR).toInstant().toEpochMilli()
        blocks.maxByOrNull { minOf(it.end, coreEnd) - maxOf(it.start, coreStart) }
            ?.takeIf { minOf(it.end, coreEnd) > maxOf(it.start, coreStart) && !it.active && it.end < now - NIGHT_GAP_MILLIS }
            ?.let { Night(it.start, it.end, it.pieces - 1) }
    }
}

/** «Засыпает на ночь ≈ 21:30, просыпается ≈ 06:45, самый долгий сон — 4 ч 30 мин» по суткам [dates]. */
private fun rhythmSummary(events: List<BabyEvent>, dates: List<LocalDate>, now: Long, zone: ZoneId): String? {
    val parts = mutableListOf<String>()
    val nights = nights(events, dates, now, zone)
    if (nights.size >= MIN_NIGHTS) {
        fun minuteOfDay(t: Long) = Instant.ofEpochMilli(t).atZone(zone).toLocalTime().toSecondOfDay() / 60
        // Засыпание считаем от полудня: 23:50 и 00:10 в среднем — полночь, а не полдень
        val bedtime = (nights.map { (minuteOfDay(it.start) + 720) % 1440 }.average().roundToInt() + 720) % 1440
        val wake = nights.map { minuteOfDay(it.end) }.average().roundToInt()
        parts += "засыпает на ночь ≈ ${clock(bedtime)}"
        parts += "просыпается ≈ ${clock(wake)}"
    }
    val from = dates.firstOrNull()?.atStartOfDay(zone)?.toInstant()?.toEpochMilli() ?: return null
    events.filterIsInstance<Sleep>()
        .filter { it.start >= from }
        .mapNotNull { sleep -> sleep.end?.let { it - sleep.start } }
        .maxOrNull()
        ?.let { parts += "самый долгий сон — ${formatDuration(it)}" }
    return parts.joinToString(", ").replaceFirstChar(Char::titlecase).ifEmpty { null }
}

private fun clock(minuteOfDay: Int): String = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60).format(CLOCK)
