package com.kid.babylog.feature.analytics

import com.kid.babylog.core.model.BabyEvent
import com.kid.babylog.core.model.Sleep
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Перерыв в сне до часа (ночное кормление, смена подгузника) не прерывает ночь. */
internal const val NIGHT_GAP_MILLIS = 60 * 60_000L

/** Ночь — сон, захватывающий отрезок от полуночи до этого часа. */
private const val NIGHT_CORE_END_HOUR = 5L

/** Подряд идущие сны с перерывами до [NIGHT_GAP_MILLIS]; [active] — последний из них ещё идёт. */
internal class SleepBlock(val start: Long, var end: Long, val sleeps: MutableList<Sleep>, var active: Boolean)

/** Блоки снов по времени; идущий сон — до [now]. */
internal fun sleepBlocks(events: List<BabyEvent>, now: Long): List<SleepBlock> {
    val blocks = mutableListOf<SleepBlock>()
    events.filterIsInstance<Sleep>().sortedBy { it.start }.forEach { sleep ->
        val end = sleep.end ?: now
        val last = blocks.lastOrNull()
        if (last != null && sleep.start - last.end <= NIGHT_GAP_MILLIS) {
            last.end = maxOf(last.end, end)
            last.sleeps += sleep
            last.active = last.active || sleep.isActive
        } else {
            blocks += SleepBlock(sleep.start, end, mutableListOf(sleep), active = sleep.isActive)
        }
    }
    return blocks
}

/** Сколько блок [block] захватывает от ядра ночи суток [date] — с полуночи до 5:00; 0 — не захватывает. */
internal fun nightCoreOverlap(block: SleepBlock, date: LocalDate, zone: ZoneId): Long {
    val coreStart = date.atStartOfDay(zone)
    val coreEnd = coreStart.plusHours(NIGHT_CORE_END_HOUR).toInstant().toEpochMilli()
    return maxOf(0L, minOf(block.end, coreEnd) - maxOf(block.start, coreStart.toInstant().toEpochMilli()))
}

/**
 * Ночные сны (ADR-0036): сны блоков, которые захватывают ядро ночи каких-нибудь суток, — та же ночь,
 * от которой «Ритм дня» считает засыпание и пробуждение (ADR-0028). Остальные сны — дневные.
 * Идущий сон — по тому, что уже известно: вечерний сон станет ночным, когда наступит полночь.
 */
internal fun nightSleeps(events: List<BabyEvent>, now: Long, zone: ZoneId): Set<Sleep> =
    sleepBlocks(events, now)
        .filter { block ->
            val first = Instant.ofEpochMilli(block.start).atZone(zone).toLocalDate()
            val last = Instant.ofEpochMilli(block.end).atZone(zone).toLocalDate()
            generateSequence(first) { it.plusDays(1) }.takeWhile { it <= last }.any { nightCoreOverlap(block, it, zone) > 0 }
        }
        .flatMapTo(mutableSetOf()) { it.sleeps }
