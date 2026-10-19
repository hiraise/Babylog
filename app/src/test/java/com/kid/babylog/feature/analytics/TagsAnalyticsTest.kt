package com.kid.babylog.feature.analytics

import com.kid.babylog.core.model.BabyEvent
import com.kid.babylog.core.model.Breast
import com.kid.babylog.core.model.Crying
import com.kid.babylog.core.model.Feeding
import com.kid.babylog.core.model.Note
import com.kid.babylog.core.model.Sex
import com.kid.babylog.core.model.Sleep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class TagsAnalyticsTest {

    private val zone = ZoneId.of("Europe/Moscow")
    private val today = LocalDate.of(2026, 10, 18) // воскресенье
    private val minute = 60_000L

    private fun at(date: LocalDate, h: Int, m: Int = 0): Long =
        LocalDateTime.of(date, LocalTime.of(h, m)).atZone(zone).toInstant().toEpochMilli()

    private val now = at(today, 11, 20)

    private fun day(n: Long) = today.minusDays(n)

    private fun model(events: List<BabyEvent>, period: AnalyticsPeriod = AnalyticsPeriod.WEEK) =
        analyticsModel(events, period, now, zone, Sex.GIRL)

    @Test
    fun `no tags and no notes — no tags tile`() {
        assertNull(model(listOf(Feeding(1, 1, at(day(1), 8), end = at(day(1), 8, 15), breast = Breast.LEFT))).tags)
    }

    @Test
    fun `tags are counted over the period, by days`() {
        val events = listOf(
            Crying(1, 1, at(day(1), 19), end = at(day(1), 19, 20), reasons = listOf("колики")),
            Crying(2, 1, at(day(3), 20), end = at(day(3), 20, 30), reasons = listOf("колики")),
            Note(3, 1, at(day(1), 21), tags = listOf("зубки"), note = "Тёрла дёсны"),
            Note(4, 1, at(day(2), 9), note = "Без меток"),
        )
        val tags = checkNotNull(model(events).tags)
        assertEquals(2, tags.cryings)
        assertEquals(2, tags.notes)
        assertEquals("плач 2 раза · 2 заметки", tags.counts())
        assertEquals(listOf("колики" to 2, "зубки" to 1), tags.frequencies.map { it.tag to it.count })
        // Клетки — сутки недели с понедельника: колики в чт и сб
        assertEquals(listOf(false, false, false, true, false, true, false), tags.frequencies.first().marks)
        assertEquals(1, tags.untaggedNotes)
    }

    @Test
    fun `routine compares days with a note tag against days without`() {
        // 12 полных суток: с «зубками» (сутки 1–3) сон 10 ч и плач 40 мин, без них — 12 ч и 10 мин
        val events = (1L..12L).flatMap { n ->
            val teeth = n <= 3
            listOfNotNull(
                Sleep(n * 10, 1, at(day(n), 6), end = at(day(n), if (teeth) 16 else 18)),
                Crying(n * 10 + 1, 1, at(day(n), 20), end = at(day(n), 20) + (if (teeth) 40 else 10) * minute),
                Note(n * 10 + 2, 1, at(day(n), 21), tags = listOf("зубки")).takeIf { teeth },
                Note(n * 10 + 3, 1, at(day(n), 10), tags = listOf("прививка")).takeIf { n == 5L },
            )
        }
        val tags = checkNotNull(model(events, AnalyticsPeriod.MONTH).tags)
        assertEquals(TagRoutine("зубки", 3, sleep = "сон −2 ч", crying = "плач +30 мин"), tags.routine[0])
        // Один день с меткой — мало данных, а не вывод по одному дню
        assertEquals(TagRoutine("прививка", 1, sleep = null, crying = null), tags.routine[1])
    }
}
