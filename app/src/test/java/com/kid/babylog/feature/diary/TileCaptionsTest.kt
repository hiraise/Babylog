package com.kid.babylog.feature.diary

import com.kid.babylog.core.model.Breast
import com.kid.babylog.core.model.Child
import com.kid.babylog.core.model.Diaper
import com.kid.babylog.core.model.DiaperKind
import com.kid.babylog.core.model.Feeding
import com.kid.babylog.core.model.Sleep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Подписи плиток: у сна и кормления, пока таймер не идёт, — сколько прошло с конца прошлого;
 * у подгузника — с последней смены, а через 3 часа без сна подпись выделяется.
 */
class TileCaptionsTest {

    private val now = 1_786_000_000_000L
    private val minute = 60_000L

    @Test
    fun `captions count from the end of the last sleep and feeding`() {
        assertEquals("не спит 1 ч 20 мин", awakeCaption(now - 80 * minute, now))
        assertEquals("левая, 40 мин назад", afterFeedingCaption(LastFeeding(now - 40 * minute, Breast.LEFT), now))
        assertEquals("бутылочка, 2 ч назад", afterFeedingCaption(LastFeeding(now - 120 * minute, breast = null), now))
        assertEquals("не спит меньше минуты", awakeCaption(now - 10_000, now))
        assertEquals("правая, только что", afterFeedingCaption(LastFeeding(now - 10_000, Breast.RIGHT), now))
    }

    @Test
    fun `no caption without a finished record or after a day`() {
        assertNull(awakeCaption(null, now))
        assertNull(afterFeedingCaption(null, now))
        assertNull(awakeCaption(now - 24 * 60 * minute, now))
        assertEquals("левая, 23 ч 59 мин назад", afterFeedingCaption(LastFeeding(now - (24 * 60 - 1) * minute, Breast.LEFT), now))
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
        assertEquals(LastFeeding(now - 30 * minute, Breast.RIGHT), state.lastFeeding)
    }

    @Test
    fun `diaper caption counts from the last change and disappears after a day`() {
        assertEquals("2 ч 10 мин назад", diaperCaption(now - 130 * minute, now))
        assertEquals("только что", diaperCaption(now - 10_000, now))
        assertNull(diaperCaption(null, now))
        assertNull(diaperCaption(now - 24 * 60 * minute, now))
    }

    @Test
    fun `diaper is overdue after three hours unless the baby sleeps`() {
        assertFalse(diaperOverdue(now - (3 * 60 - 1) * minute, sleeping = false, now = now))
        assertTrue(diaperOverdue(now - 3 * 60 * minute, sleeping = false, now = now))
        assertFalse(diaperOverdue(now - 4 * 60 * minute, sleeping = true, now = now))
        // Без подписи (сутки и больше, не меняли) — и без выделения
        assertFalse(diaperOverdue(now - 24 * 60 * minute, sleeping = false, now = now))
        assertFalse(diaperOverdue(null, sleeping = false, now = now))
    }

    @Test
    fun `last diaper is the latest change of any kind`() {
        val events = listOf(
            Diaper(id = 3, childId = 1, start = now - 20 * minute, kind = DiaperKind.WET),
            Sleep(id = 2, childId = 1, start = now - 200 * minute, end = now - 90 * minute),
            Diaper(id = 1, childId = 1, start = now - 300 * minute, kind = DiaperKind.DIRTY),
        )
        val state = DiaryUiState.of(listOf(Child(1, "Маша", null)), Child(1, "Маша", null), events)
        assertEquals(now - 20 * minute, state.lastDiaper)
    }
}
