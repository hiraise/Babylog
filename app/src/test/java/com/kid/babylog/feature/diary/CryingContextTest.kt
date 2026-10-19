package com.kid.babylog.feature.diary

import com.kid.babylog.core.model.Bottle
import com.kid.babylog.core.model.Breast
import com.kid.babylog.core.model.Crying
import com.kid.babylog.core.model.Diaper
import com.kid.babylog.core.model.DiaperKind
import com.kid.babylog.core.model.Feeding
import com.kid.babylog.core.model.Sleep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class CryingContextTest {

    private val zone = ZoneId.of("Europe/Moscow")
    private val day = LocalDate.of(2026, 10, 18)
    private val minute = 60_000L

    private fun at(h: Int, m: Int = 0, date: LocalDate = day): Long =
        LocalDateTime.of(date, LocalTime.of(h, m)).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `before crying — awake time, the whole feeding with a breast switch and the diaper`() {
        val crying = Crying(10, 1, at(16))
        val events = listOf(
            Sleep(1, 1, at(9), end = at(10)),
            Sleep(2, 1, at(11), end = at(12)),
            Sleep(3, 1, at(13, 30), end = at(14)),
            // Смена груди по таймеру — одно кормление 14:10–14:40
            Feeding(4, 1, at(14, 10), end = at(14, 25), breast = Breast.LEFT),
            Feeding(5, 1, at(14, 25), end = at(14, 40), breast = Breast.RIGHT),
            Diaper(6, 1, at(15), DiaperKind.WET),
            crying,
        )
        val context = cryingContext(events, crying)
        assertEquals(120 * minute, context.awakeMillis)
        // Окна бодрствования 1 ч и 1 ч 30 мин
        assertEquals(75 * minute, context.usualAwakeMillis)
        assertEquals(80 * minute, context.sinceFeedingMillis)
        assertEquals("правая, 30 мин", context.feeding)
        assertFalse(context.feedingByBottle)
        assertEquals(60 * minute, context.sinceDiaperMillis)
        assertEquals(DiaperKind.WET, context.diaper)
    }

    @Test
    fun `a bottle after the breast is the last feeding`() {
        val crying = Crying(10, 1, at(16))
        val events = listOf(
            Feeding(1, 1, at(12), end = at(12, 20), breast = Breast.LEFT),
            Bottle(2, 1, at(15, 30), volumeMl = 120),
            crying,
        )
        val context = cryingContext(events, crying)
        assertTrue(context.feedingByBottle)
        assertEquals("бутылочка 120 мл", context.feeding)
        assertEquals(30 * minute, context.sinceFeedingMillis)
    }

    @Test
    fun `records older than six hours are forgotten ones and crying in sleep has no awake time`() {
        val crying = Crying(10, 1, at(20))
        val events = listOf(
            Sleep(1, 1, at(8), end = at(9)),
            Feeding(2, 1, at(9), end = at(9, 20), breast = Breast.LEFT),
            Diaper(3, 1, at(9, 30), DiaperKind.DIRTY),
            crying,
        )
        val context = cryingContext(events, crying)
        assertNull(context.awakeMillis)
        assertNull(context.sinceFeedingMillis)
        assertNull(context.feeding)
        assertNull(context.sinceDiaperMillis)
        assertNull(context.diaper)

        // Плач во время сна (проснулась с плачем, а сон ещё не закончен) — «не спит» не считаем
        val inSleep = Crying(11, 1, at(8, 30))
        assertNull(cryingContext(events + inSleep, inSleep).awakeMillis)
    }
}
