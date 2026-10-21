package com.kid.babylog.feature.shade

import com.kid.babylog.core.data.CancelableCrying
import com.kid.babylog.core.data.ShadeMemo
import com.kid.babylog.core.data.StoppedTimer
import com.kid.babylog.core.model.BabyEvent
import com.kid.babylog.core.model.Bottle
import com.kid.babylog.core.model.Breast
import com.kid.babylog.core.model.Child
import com.kid.babylog.core.model.Crying
import com.kid.babylog.core.model.Feeding
import com.kid.babylog.core.model.Sex
import com.kid.babylog.core.model.Sleep
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.TimeZone

class ShadeContentTest {

    private val now = Instant.parse("2026-10-21T10:00:00Z").toEpochMilli()
    private val masha = Child(1, "Маша", null, Sex.GIRL)
    private val savedZone = TimeZone.getDefault()

    @Before
    fun setUp() = TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

    @After
    fun tearDown() = TimeZone.setDefault(savedZone)

    private fun minutesAgo(minutes: Long) = now - minutes * 60_000

    private fun content(vararg events: BabyEvent, memo: ShadeMemo = ShadeMemo(), at: Long = now) =
        shadeContent(masha, events.sortedByDescending { it.start }, memo, at)

    @Test
    fun `idle shows when the child woke up and the last feeding`() {
        val content = content(
            Sleep(id = 1, childId = 1, start = minutesAgo(180), end = minutesAgo(40)),
            Feeding(id = 2, childId = 1, start = minutesAgo(120), end = minutesAgo(100), breast = Breast.RIGHT),
        )
        assertTrue(content.isIdle)
        assertEquals("Не спит с 09:20", content.idleTitle)
        assertEquals("Не спит" to "с 09:20", content.idleShort)
        assertEquals("Кормление в 08:00, правая", content.idleText)
        assertEquals(Breast.LEFT, content.nextBreast)
    }

    @Test
    fun `times from another day name the day`() {
        val content = content(Sleep(id = 1, childId = 1, start = minutesAgo(15 * 60), end = minutesAgo(11 * 60)))
        assertEquals("Не спит с 23:00, вчера", content.idleTitle)
    }

    @Test
    fun `a bottle after the breast is the last feeding, but the breast still alternates`() {
        val content = content(
            Sleep(id = 1, childId = 1, start = minutesAgo(300), end = minutesAgo(200)),
            Feeding(id = 2, childId = 1, start = minutesAgo(120), end = minutesAgo(100), breast = Breast.LEFT),
            Bottle(id = 3, childId = 1, start = minutesAgo(30), volumeMl = 120),
        )
        assertEquals("Бутылочка в 09:30, 120 мл", content.idleText)
        assertEquals(Breast.RIGHT, content.nextBreast)
    }

    @Test
    fun `empty diary still offers the buttons`() {
        val content = content()
        assertEquals("Сон, кормление и плач — кнопками ниже", content.idleTitle)
        assertEquals("Маша" to null, content.idleShort)
        assertNull(content.idleText)
        assertEquals(Breast.LEFT, content.nextBreast)
    }

    @Test
    fun `feeding and crying run together, feeding offers to switch breast`() {
        val content = content(
            Feeding(id = 1, childId = 1, start = minutesAgo(12), breast = Breast.LEFT),
            Crying(id = 2, childId = 1, start = minutesAgo(3)),
        )
        assertEquals(ShadeTimer("Кормление", "левая с 09:48", minutesAgo(12), ShadeLink.SwitchBreast), content.feeding)
        assertEquals(ShadeTimer("Плач", "с 09:57", minutesAgo(3)), content.crying)
        assertNull(content.sleep)
    }

    @Test
    fun `stopped timer can be resumed for a while`() {
        val sleep = Sleep(id = 5, childId = 1, start = minutesAgo(72), end = now)
        val memo = ShadeMemo(stopped = StoppedTimer(5, now + STOP_UNDO_MS))
        val fresh = content(sleep, memo = memo)
        assertEquals(ShadeRow("Сон: 1 ч 12 мин", "Отменить", 5), fresh.stopped)
        assertEquals(now + STOP_UNDO_MS, fresh.refreshAt)
        val late = content(sleep, memo = memo, at = now + STOP_UNDO_MS)
        assertNull(late.stopped)
        assertNull(late.refreshAt)
    }

    @Test
    fun `crying that interrupted sleep can be cancelled within a minute`() {
        val crying = Crying(id = 7, childId = 1, start = now)
        val memo = ShadeMemo(startedCrying = CancelableCrying(7, sleepId = 6, until = now + CANCEL_CRYING_MS))
        val fresh = content(crying, memo = memo)
        assertEquals(ShadeTimer("Плач", "с 10:00 · сон закончен", now, ShadeLink.CancelCrying(7)), fresh.crying)
        val late = content(crying, memo = memo, at = now + CANCEL_CRYING_MS)
        assertEquals(ShadeTimer("Плач", "с 10:00", now), late.crying)
        // Началось кормление — сон уже не вернуть
        val feeding = Feeding(id = 9, childId = 1, start = now, breast = Breast.LEFT)
        assertEquals(ShadeTimer("Плач", "с 10:00", now), content(crying, feeding, memo = memo).crying)
    }

    @Test
    fun `crying stopped from the shade asks for details until they are given or an hour passes`() {
        val crying = Crying(id = 8, childId = 1, start = minutesAgo(6), end = now)
        val memo = ShadeMemo(finishedCryingId = 8)
        assertEquals(ShadeRow("Плакала 6 мин", "Отметить", 8), content(crying, memo = memo).cryingDone)
        assertNull(content(crying.copy(reasons = listOf("колики")), memo = memo).cryingDone)
        assertNull(content(crying, memo = memo, at = now + CRYING_DETAILS_MS).cryingDone)
    }

    @Test
    fun `boy cried`() {
        val crying = Crying(id = 8, childId = 1, start = minutesAgo(6), end = now)
        val content = shadeContent(masha.copy(sex = Sex.BOY), listOf(crying), ShadeMemo(finishedCryingId = 8), now)
        assertEquals("Плакал 6 мин", content.cryingDone?.text)
    }
}
