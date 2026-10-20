package com.kid.babylog.feature.analytics

import com.kid.babylog.core.model.Bottle
import com.kid.babylog.core.model.Breast
import com.kid.babylog.core.model.Diaper
import com.kid.babylog.core.model.DiaperKind
import com.kid.babylog.core.model.Feeding
import com.kid.babylog.core.model.Medicine
import com.kid.babylog.core.model.Sleep
import com.kid.babylog.core.model.Weight
import com.kid.babylog.feature.summary.summarizeDay
import com.kid.babylog.testing.sampleMonths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class DailyStatsTest {

    private val zone = ZoneId.of("Europe/Moscow")
    private val today = LocalDate.of(2026, 10, 18)
    private val yesterday = today.minusDays(1)
    private val minute = 60_000L
    private val hour = 60 * minute

    private fun at(date: LocalDate, h: Int, m: Int = 0, zone: ZoneId = this.zone): Long =
        LocalDateTime.of(date, LocalTime.of(h, m)).atZone(zone).toInstant().toEpochMilli()

    private val now = at(today, 11, 20)

    @Test
    fun `every day matches the day summary`() {
        // Сводка «Истории» и столбик одного дня не должны расходиться (ADR-0028)
        val events = sampleMonths(childId = 1, today = today, now = now, zone = zone, days = 20)
        val stats = dailyStats(events, today.minusDays(19), today, now, zone)
        assertEquals(20, stats.size)
        stats.forEach { day ->
            val summary = summarizeDay(events, day.date, now, zone)
            assertEquals("кормления ${day.date}", summary.feedings, day.feedings)
            assertEquals("бутылочки ${day.date}", summary.bottles, day.bottles)
            assertEquals("мл ${day.date}", summary.bottleMl, day.bottleMl)
            assertEquals("подгузники ${day.date}", summary.diapers, day.diapers)
            assertEquals("сон ${day.date}", summary.sleepMillis, day.sleepMillis)
            assertEquals("ночь и день ${day.date}", day.sleepMillis, day.nightSleepMillis + day.daySleepMillis)
        }
    }

    @Test
    fun `sleep across midnight is split between the days`() {
        val events = listOf(Sleep(1, 1, at(yesterday, 22), end = at(today, 6)))
        val (before, after) = dailyStats(events, yesterday, today, now, zone)
        assertEquals(2 * hour, before.sleepMillis)
        assertEquals(6 * hour, after.sleepMillis)
        // Самый долгий сон — у суток, где он начался
        assertEquals(8 * hour, before.longestSleepMillis)
        assertEquals(0L, after.longestSleepMillis)
    }

    @Test
    fun `day of the spring clock change has 23 hours`() {
        // В Берлине 29 марта 2026 в 02:00 часы переводят на 03:00
        val berlin = ZoneId.of("Europe/Berlin")
        val change = LocalDate.of(2026, 3, 29)
        val events = listOf(Sleep(1, 1, at(change.minusDays(1), 22, zone = berlin), end = at(change, 6, zone = berlin)))
        val (before, day) = dailyStats(events, change.minusDays(1), change, at(change.plusDays(1), 12, zone = berlin), berlin)
        assertEquals(2 * hour, before.sleepMillis)
        assertEquals(5 * hour, day.sleepMillis)
    }

    @Test
    fun `active sleep counts until now`() {
        val events = listOf(Sleep(1, 1, at(today, 10)))
        assertEquals(80 * minute, dailyStats(events, today, today, now, zone).single().sleepMillis)
    }

    @Test
    fun `day without routine records is not tracked`() {
        // Вес и лекарство режим дня не описывают: такие сутки — «не вели», а не нули
        val events = listOf(
            Weight(1, 1, at(yesterday, 10), 6350),
            Medicine(2, 1, at(yesterday, 11), "Витамин D"),
            Diaper(3, 1, at(today, 9), DiaperKind.WET),
        )
        val (before, after) = dailyStats(events, yesterday, today, now, zone)
        assertFalse(before.tracked)
        assertTrue(after.tracked)
        assertTrue(before.complete)
        assertFalse(after.complete)
        assertFalse(after.countable)
    }

    @Test
    fun `breast time is unknown when a feeding has no duration`() {
        // Кормления до ADR-0015 записаны без длительности: конец равен началу
        val events = listOf(
            Feeding(1, 1, at(yesterday, 8), end = at(yesterday, 8, 15), breast = Breast.LEFT),
            Feeding(2, 1, at(yesterday, 11), end = at(yesterday, 11), breast = Breast.RIGHT),
            Feeding(3, 1, at(today, 8), end = at(today, 8, 20), breast = Breast.LEFT),
            Feeding(4, 1, at(today, 8, 20), end = at(today, 8, 30), breast = Breast.RIGHT),
        )
        val (before, after) = dailyStats(events, yesterday, today, now, zone)
        assertNull(before.breastMillis)
        assertEquals(2, before.feedings)
        // Смена груди — одно кормление, время — обе записи
        assertEquals(1, after.feedings)
        assertEquals(30 * minute, after.breastMillis)
    }

    @Test
    fun `bottles count as feedings and their starts as feeding starts`() {
        val events = listOf(
            Feeding(1, 1, at(today, 7), end = at(today, 7, 15), breast = Breast.LEFT),
            Bottle(2, 1, at(today, 10), volumeMl = 120),
        )
        val day = dailyStats(events, today, today, now, zone).single()
        assertEquals(2, day.feedings)
        assertEquals(120, day.bottleMl)
        assertEquals(listOf(at(today, 7), at(today, 10)), day.feedingStarts)
        assertEquals(15 * minute, day.breastMillis)
    }

    @Test
    fun `sleep is split into night and day`() {
        // Ночь — с 19:00 до 7:00 (ADR-0036); сон через границу делится
        val events = listOf(
            Sleep(1, 1, at(yesterday, 13), end = at(yesterday, 15)),
            Sleep(2, 1, at(yesterday, 18, 30), end = at(yesterday, 20)), // 30 мин днём, 1 ч ночью
            Sleep(3, 1, at(yesterday, 21), end = at(today, 2)),
            Sleep(4, 1, at(today, 2, 30), end = at(today, 6, 30)),
            Sleep(5, 1, at(today, 6, 45), end = at(today, 8)), // 15 мин ночью, 1 ч днём
            Sleep(6, 1, at(today, 10)), // идёт
        )
        val (before, after) = dailyStats(events, yesterday, today, now, zone)
        assertEquals(4 * hour, before.nightSleepMillis)
        assertEquals(150 * minute, before.daySleepMillis)
        assertEquals(6 * hour + 15 * minute, after.nightSleepMillis)
        assertEquals(140 * minute, after.daySleepMillis)
    }

    @Test
    fun `night boundaries follow the clock change`() {
        // В Берлине 29 марта 2026 в 02:00 часы переводят на 03:00: ночь с 19:00 до 7:00 — 11 часов
        val berlin = ZoneId.of("Europe/Berlin")
        val change = LocalDate.of(2026, 3, 29)
        val (night, day) = splitByNight(at(change.minusDays(1), 18, zone = berlin), at(change, 8, zone = berlin), berlin)
        assertEquals(listOf(at(change.minusDays(1), 19, zone = berlin) to at(change, 7, zone = berlin)), night)
        assertEquals(2 * hour, day.sumOf { (from, to) -> to - from })
    }
}
