package com.kid.babylog.feature.diary

import com.kid.babylog.core.model.Bottle
import com.kid.babylog.core.model.Breast
import com.kid.babylog.core.model.Child
import com.kid.babylog.core.model.Feeding
import com.kid.babylog.core.model.Sleep
import com.kid.babylog.core.ui.formatClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Подписи плиток сна и кормления, пока таймер не идёт: сколько прошло с конца прошлого. */
class TileCaptionsTest {

    private val now = 1_786_000_000_000L
    private val minute = 60_000L

    @Test
    fun `captions count from the end of the last sleep and feeding`() {
        assertEquals("не спит 1 ч 20 мин", awakeCaption(now - 80 * minute, now))
        // Начало — по часам, сколько прошло — с конца
        val start = now - 65 * minute
        assertEquals(
            "левая в ${formatClock(start)}\nзакончилось 40 мин назад",
            afterFeedingCaption(LastFeeding(start, now - 40 * minute, Breast.LEFT), now),
        )
        val bottle = now - 120 * minute
        assertEquals("бутылочка в ${formatClock(bottle)}\n2 ч назад", afterFeedingCaption(LastFeeding(bottle, bottle, breast = null), now))
        assertEquals("не спит меньше минуты", awakeCaption(now - 10_000, now))
        assertEquals("закончилось только что", afterFeedingCaption(LastFeeding(start, now - 10_000, Breast.RIGHT), now)?.substringAfter('\n'))
    }

    @Test
    fun `no caption without a finished record or after a day`() {
        assertNull(awakeCaption(null, now))
        assertNull(afterFeedingCaption(null, now))
        assertNull(awakeCaption(now - 24 * 60 * minute, now))
        val end = now - (24 * 60 - 1) * minute
        assertEquals("закончилось 23 ч 59 мин назад", afterFeedingCaption(LastFeeding(end - 15 * minute, end, Breast.LEFT), now)?.substringAfter('\n'))
    }

    @Test
    fun `last end ignores the running timer and takes the latest end`() {
        val events = listOf(
            Sleep(id = 4, childId = 1, start = now - 10 * minute),
            Feeding(id = 3, childId = 1, start = now - 50 * minute, end = now - 30 * minute, breast = Breast.RIGHT),
            Sleep(id = 2, childId = 1, start = now - 200 * minute, end = now - 90 * minute),
            Feeding(id = 1, childId = 1, start = now - 300 * minute, end = now - 280 * minute, breast = Breast.LEFT),
        )
        val state = DiaryUiState.of(listOf(Child(1, "Маша", null)), Child(1, "Маша", null), events)
        assertEquals(now - 90 * minute, state.lastSleepEnd)
        assertEquals(LastFeeding(now - 50 * minute, now - 30 * minute, Breast.RIGHT), state.lastFeeding)
    }

    @Test
    fun `after switching breast the feeding starts at the first breast and ends with the last`() {
        val events = listOf(
            Feeding(id = 2, childId = 1, start = now - 50 * minute, end = now - 40 * minute, breast = Breast.RIGHT),
            Feeding(id = 1, childId = 1, start = now - 60 * minute, end = now - 50 * minute, breast = Breast.LEFT),
            Bottle(id = 3, childId = 1, start = now - 120 * minute, volumeMl = 90),
        )
        val state = DiaryUiState.of(listOf(Child(1, "Маша", null)), Child(1, "Маша", null), events)
        assertEquals(LastFeeding(now - 60 * minute, now - 40 * minute, Breast.RIGHT), state.lastFeeding)
    }
}
