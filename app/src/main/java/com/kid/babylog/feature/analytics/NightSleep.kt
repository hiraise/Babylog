package com.kid.babylog.feature.analytics

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Ночь — с этого часа вечера до [NIGHT_END_HOUR] утра, день — остальное (ADR-0036), как в опроснике
 * детского сна BISQ; источники — [SLEEP_SOURCES].
 */
internal const val NIGHT_START_HOUR = 19
internal const val NIGHT_END_HOUR = 7

/**
 * Отрезок `[start, end)` по частям: что пришлось на ночь (с 19:00 до 7:00) и что на день. Сон через
 * границу делится: с 18:30 до 20:00 — 30 мин днём и 1 ч ночью. Части — по порядку, `first` — ночные.
 */
internal fun splitByNight(start: Long, end: Long, zone: ZoneId): Pair<List<Pair<Long, Long>>, List<Pair<Long, Long>>> {
    val night = mutableListOf<Pair<Long, Long>>()
    val day = mutableListOf<Pair<Long, Long>>()
    if (end <= start) return night to day
    fun at(date: LocalDate, hour: Int): Long = date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
    // Ночь, начавшаяся вечером накануне, может захватить утро первого дня отрезка
    var date = Instant.ofEpochMilli(start).atZone(zone).toLocalDate().minusDays(1)
    var cursor = start
    while (cursor < end) {
        val nightStart = at(date, NIGHT_START_HOUR)
        val nightEnd = at(date.plusDays(1), NIGHT_END_HOUR)
        if (cursor < nightStart) {
            day += cursor to minOf(end, nightStart)
            cursor = minOf(end, nightStart)
        }
        if (cursor in nightStart until nightEnd) {
            night += cursor to minOf(end, nightEnd)
            cursor = minOf(end, nightEnd)
        }
        date = date.plusDays(1)
    }
    return night to day
}
