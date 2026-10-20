package com.kid.babylog.feature.analytics

import com.kid.babylog.core.model.BabyEvent
import com.kid.babylog.core.model.Bottle
import com.kid.babylog.core.model.Breast
import com.kid.babylog.core.model.Diaper
import com.kid.babylog.core.model.DiaperKind
import com.kid.babylog.core.model.Feeding
import com.kid.babylog.core.model.Sleep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class RhythmTest {

    private val zone = ZoneId.of("Europe/Moscow")
    private val today = LocalDate.of(2026, 10, 18)

    private fun at(date: LocalDate, h: Int, m: Int = 0): Long =
        LocalDateTime.of(date, LocalTime.of(h, m)).atZone(zone).toInstant().toEpochMilli()

    private val now = at(today, 11, 20)
    private fun day(n: Long) = today.minusDays(n)

    private fun rhythm(events: List<BabyEvent>): RhythmModel {
        val stats = dailyStats(events, today.minusDays(RHYTHM_DAYS - 1L), today, now, zone)
        return rhythmModel(events, stats, now, zone)
    }

    /** Ночь на сутки [date]: засыпание накануне в [bed], пробуждения на кормление в 01:30 и 04:40, подъём в [wake]. */
    private fun night(date: LocalDate, bed: LocalTime, wake: LocalTime, firstId: Long): List<Sleep> {
        val evening = if (bed.hour >= 12) date.minusDays(1) else date
        val bedtime = LocalDateTime.of(evening, bed).atZone(zone).toInstant().toEpochMilli()
        return listOf(
            Sleep(firstId, 1, bedtime, end = at(date, 1, 30)),
            Sleep(firstId + 1, 1, at(date, 1, 50), end = at(date, 4, 40)),
            Sleep(firstId + 2, 1, at(date, 5, 0), end = LocalDateTime.of(date, wake).atZone(zone).toInstant().toEpochMilli()),
        )
    }

    @Test
    fun `night joins sleeps with short wakings and counts them`() {
        val nights = nights(night(day(1), LocalTime.of(21, 30), LocalTime.of(6, 45), 1), listOf(day(1)), now, zone)
        val night = nights.single()
        assertEquals(LocalDateTime.of(day(2), LocalTime.of(21, 30)).atZone(zone).toInstant().toEpochMilli(), night.start)
        assertEquals(at(day(1), 6, 45), night.end)
        assertEquals(2, night.wakings)
    }

    @Test
    fun `bedtime around midnight averages to midnight, not to noon`() {
        val events = night(day(1), LocalTime.of(23, 50), LocalTime.of(7, 0), 1) +
            night(day(2), LocalTime.of(0, 10), LocalTime.of(7, 0), 10) +
            night(day(3), LocalTime.of(0, 0), LocalTime.of(7, 0), 20)
        assertEquals(
            "Засыпает на ночь ≈ 00:00, просыпается ≈ 07:00, самый долгий сон — 2 ч 50 мин",
            rhythm(events).summary,
        )
    }

    @Test
    fun `fewer than three nights — only the longest sleep`() {
        val events = night(day(1), LocalTime.of(21, 0), LocalTime.of(6, 0), 1)
        assertEquals("Самый долгий сон — 4 ч 30 мин", rhythm(events).summary)
    }

    @Test
    fun `night still going on is not counted`() {
        val sleeping = listOf(Sleep(1, 1, at(day(1), 21, 30), end = at(today, 2)), Sleep(2, 1, at(today, 2, 20)))
        assertTrue(nights(sleeping, listOf(today), at(today, 4), zone).isEmpty())
    }

    @Test
    fun `instant feedings are ticks and today ends now`() {
        val events = listOf(
            Feeding(1, 1, at(today, 7), end = at(today, 7, 30), breast = Breast.LEFT),
            // До ADR-0015 — без длительности
            Feeding(2, 1, at(day(1), 9), end = at(day(1), 9), breast = Breast.RIGHT),
            Bottle(3, 1, at(today, 10), volumeMl = 120),
            Diaper(4, 1, at(today, 8), DiaperKind.WET),
        )
        val (todayRow, yesterdayRow) = rhythm(events).days
        assertEquals("сегодня", todayRow.label)
        assertEquals(listOf(DaySpan(7 / 24f, 7.5f / 24f)), todayRow.feedings)
        assertEquals(listOf(10 / 24f), todayRow.instantFeedings)
        assertEquals(listOf(8 / 24f), todayRow.diapers)
        assertEquals((11 + 20 / 60f) / 24f, todayRow.end, 1e-4f)
        assertEquals(listOf(9 / 24f), yesterdayRow.instantFeedings)
        assertEquals(1f, yesterdayRow.end)
        assertEquals("сб 17", yesterdayRow.label)
    }

    @Test
    fun `active sleep is drawn up to now`() {
        val model = rhythm(listOf(Sleep(1, 1, at(today, 10))))
        val sleep = model.days.first().daySleeps.single()
        assertEquals(10 / 24f, sleep.from)
        assertEquals((11 + 20 / 60f) / 24f, sleep.to, 1e-4f)
        // Идущий сон — не «самый долгий» и не ночь
        assertNull(model.summary)
    }

    @Test
    fun `night sleep and naps are drawn apart`() {
        // Ночь — с 19:00 до 7:00 (ADR-0036): сон с 18:30 до 19:30 — две полосы
        val events = night(day(1), LocalTime.of(21, 30), LocalTime.of(6, 45), 1) +
            Sleep(10, 1, at(day(1), 10), end = at(day(1), 11, 30)) +
            Sleep(11, 1, at(day(1), 18, 30), end = at(day(1), 19, 30))
        val model = rhythm(events)
        val yesterday = model.days[1]
        assertEquals(listOf(0f, 110 / 1440f, 300 / 1440f, 19 / 24f), yesterday.nightSleeps.map { it.from })
        assertEquals(listOf(10 / 24f, 18.5f / 24), yesterday.daySleeps.map { it.from })
        assertEquals(listOf(11.5f / 24, 19 / 24f), yesterday.daySleeps.map { it.to })
        // Засыпание в 21:30 — ночной сон и в строке позавчера
        assertEquals(listOf(DaySpan(21.5f / 24, 1f)), model.days[2].nightSleeps)
        assertTrue(model.days[2].daySleeps.isEmpty())
    }
}
