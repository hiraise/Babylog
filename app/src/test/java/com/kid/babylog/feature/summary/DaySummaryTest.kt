package com.kid.babylog.feature.summary

import com.kid.babylog.core.model.Breast
import com.kid.babylog.core.model.Diaper
import com.kid.babylog.core.model.DiaperKind
import com.kid.babylog.core.model.Feeding
import com.kid.babylog.core.model.Medicine
import com.kid.babylog.core.model.Sleep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class DaySummaryTest {

    private val zone = ZoneId.of("Europe/Moscow")
    private val today = LocalDate.of(2026, 10, 15)
    private val yesterday = today.minusDays(1)

    private fun at(date: LocalDate, hour: Int, minute: Int = 0): Long =
        LocalDateTime.of(date, java.time.LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()

    private val now = at(today, 12)
    private val hour = 3_600_000L
    private val minute = 60_000L

    /** Законченное кормление длиной [minutes] минут. */
    private fun fed(id: Long, start: Long, breast: Breast, minutes: Int = 15) =
        Feeding(id, 1, start, end = start + minutes * minute, breast = breast)

    @Test
    fun `counts only records of the chosen day`() {
        val events = listOf(
            fed(1, at(today, 8), Breast.LEFT),
            fed(2, at(today, 11), Breast.RIGHT),
            fed(3, at(yesterday, 23), Breast.LEFT),
            Diaper(4, 1, at(today, 9), DiaperKind.WET),
            Diaper(5, 1, at(today, 10), DiaperKind.WET),
            Diaper(6, 1, at(today, 10, 30), DiaperKind.DIRTY),
        )
        val summary = summarizeDay(events, today, now, zone)
        assertEquals(2, summary.feedings)
        assertEquals(mapOf(DiaperKind.WET to 2, DiaperKind.DIRTY to 1), summary.diapers)
        assertEquals(3, summary.diaperCount)
        assertEquals("Подгузников: 3 (пописал ×2 · покакал ×1)", summary.lines(now)[1].shown())
    }

    @Test
    fun `last feeding is shown only today and may be from yesterday`() {
        val events = listOf(fed(1, at(yesterday, 23), Breast.RIGHT))
        val summary = summarizeDay(events, today, now, zone)
        assertEquals(0, summary.feedings)
        assertEquals(Breast.RIGHT, summary.lastFeeding?.lastBreast)
        assertNull(summarizeDay(events, yesterday, now, zone).lastFeeding)
        assertEquals("Кормлений: 0 · последнее: правая, 13 ч назад", summary.lines(now).first().shown())
    }

    @Test
    fun `switching breast is one feeding counted from its start`() {
        val events = listOf(
            fed(1, at(today, 10), Breast.LEFT, minutes = 10),
            fed(2, at(today, 10, 10), Breast.RIGHT, minutes = 5),
            fed(3, at(today, 8), Breast.RIGHT), // отдельное кормление: начато не в конце другого
        )
        val summary = summarizeDay(events, today, now, zone)
        assertEquals(2, summary.feedings)
        assertEquals(FeedingSession(at(today, 10), at(today, 10, 15), Breast.RIGHT), summary.lastFeeding)
        assertEquals("Кормлений: 2 · последнее: правая, 2 ч назад", summary.lines(now).first().shown())
    }

    @Test
    fun `active feeding is shown instead of time ago`() {
        val events = listOf(
            fed(1, at(today, 11, 40), Breast.LEFT, minutes = 10),
            Feeding(2, 1, at(today, 11, 50), end = null, breast = Breast.RIGHT),
        )
        val summary = summarizeDay(events, today, now, zone)
        assertEquals(1, summary.feedings)
        assertEquals("Кормлений: 1 · сейчас ест: правая", summary.lines(now).first().shown())
    }

    @Test
    fun `old feeding without duration is a separate feeding`() {
        val start = at(today, 9)
        val events = listOf(Feeding(1, 1, start, end = start, breast = Breast.LEFT), fed(2, at(today, 9, 30), Breast.RIGHT))
        assertEquals(2, summarizeDay(events, today, now, zone).feedings)
    }

    @Test
    fun `sleep across midnight is split between days`() {
        val events = listOf(Sleep(1, 1, at(yesterday, 22), end = at(today, 6)))
        assertEquals(2 * hour, summarizeDay(events, yesterday, now, zone).sleepMillis)
        assertEquals(6 * hour, summarizeDay(events, today, now, zone).sleepMillis)
    }

    @Test
    fun `active sleep counts until now and only today is sleeping`() {
        val events = listOf(Sleep(1, 1, at(today, 10), end = null))
        val summary = summarizeDay(events, today, now, zone)
        assertEquals(2 * hour, summary.sleepMillis)
        assertTrue(summary.sleepingNow)
        assertFalse(summarizeDay(events, yesterday, now, zone).sleepingNow)
    }

    @Test
    fun `medicines are grouped ignoring case with the latest name`() {
        val events = listOf(
            Medicine(1, 1, at(today, 8), "витамин d"),
            Medicine(2, 1, at(today, 9), "Витамин D "),
            Medicine(3, 1, at(today, 10), "Нурофен"),
        )
        val summary = summarizeDay(events, today, now, zone)
        assertEquals(listOf("Нурофен" to 1, "Витамин D" to 2), summary.medicines)
        assertEquals("Лекарств: 3 (Нурофен, Витамин D ×2)", summary.lines(now).last().shown())
    }

    @Test
    fun `empty day shows zeros without breakdowns`() {
        val lines = summarizeDay(emptyList(), yesterday, now, zone).lines(now)
        assertEquals(
            listOf("Кормлений: 0", "Подгузников: 0", "Сон: 0 мин", "Лекарств: 0"),
            lines.map { it.shown() },
        )
    }

    /** Строка так, как её собирает карточка: метка, пробел, значение. */
    private fun SummaryLine.shown() = "$label $value"
}
