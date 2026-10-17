package com.kid.babylog.feature.diary

import com.kid.babylog.core.model.Breast
import com.kid.babylog.core.model.Child
import com.kid.babylog.core.model.Feeding
import com.kid.babylog.core.model.Sleep
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
        assertEquals("закончилось 40 мин назад", afterFeedingCaption(now - 40 * minute, now))
        assertEquals("не спит меньше минуты", awakeCaption(now - 10_000, now))
        assertEquals("закончилось только что", afterFeedingCaption(now - 10_000, now))
    }

    @Test
    fun `no caption without a finished record or after a day`() {
        assertNull(awakeCaption(null, now))
        assertNull(afterFeedingCaption(null, now))
        assertNull(awakeCaption(now - 24 * 60 * minute, now))
        assertEquals("закончилось 23 ч 59 мин назад", afterFeedingCaption(now - (24 * 60 - 1) * minute, now))
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
        assertEquals(now - 30 * minute, state.lastFeedingEnd)
    }
}
