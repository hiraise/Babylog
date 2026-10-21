package com.kid.babylog.feature.shade

import com.kid.babylog.core.model.Breast
import com.kid.babylog.core.model.Child
import com.kid.babylog.core.model.Crying
import com.kid.babylog.core.model.Feeding
import com.kid.babylog.core.model.Sleep
import com.kid.babylog.testing.FakeDiaryRepository
import com.kid.babylog.testing.FakeShadeStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** Кнопки уведомления в шторке (ADR-0042) — те же правила таймеров, что на экране, плюс память для «Отменить». */
class ShadeCommandsTest {

    private val clock = MutableClock(Instant.parse("2026-10-21T10:00:00Z").toEpochMilli())
    private val repo = FakeDiaryRepository(listOf(Child(1, "Маша", null)))
    private val store = FakeShadeStore()
    private val commands = ShadeCommands(repo, store, clock)

    private fun sleeps() = repo.events.value.filterIsInstance<Sleep>()
    private fun feedings() = repo.events.value.filterIsInstance<Feeding>()
    private fun cryings() = repo.events.value.filterIsInstance<Crying>()

    @Test
    fun `sleep starts and ends, stop can be undone for a while`() = runTest {
        commands.perform(ShadeAction.START_SLEEP, childId = 1)
        clock.advance(40 * 60_000)
        commands.perform(ShadeAction.END_SLEEP, childId = 1)
        val sleep = sleeps().single()
        assertEquals(clock.millis(), sleep.end)
        assertEquals(sleep.id, store.memo.value.stopped?.eventId)

        commands.perform(ShadeAction.RESUME, childId = 1, eventId = sleep.id)
        assertTrue(sleeps().single().isActive)
        assertNull(store.memo.value.stopped)
    }

    @Test
    fun `late undo does nothing`() = runTest {
        commands.perform(ShadeAction.START_SLEEP, childId = 1)
        commands.perform(ShadeAction.END_SLEEP, childId = 1)
        clock.advance(STOP_UNDO_MS)
        commands.perform(ShadeAction.RESUME, childId = 1, eventId = sleeps().single().id)
        assertTrue(!sleeps().single().isActive)
    }

    @Test
    fun `feeding starts with the other breast and ends sleep`() = runTest {
        repo.addEvent(Feeding(childId = 1, start = 1_000, end = 2_000, breast = Breast.LEFT))
        commands.perform(ShadeAction.START_SLEEP, childId = 1)
        commands.perform(ShadeAction.START_FEEDING, childId = 1)
        assertTrue(!sleeps().single().isActive)
        assertEquals(Breast.RIGHT, feedings().single { it.isActive }.breast)

        clock.advance(60_000)
        commands.perform(ShadeAction.SWITCH_BREAST, childId = 1)
        assertEquals(Breast.LEFT, feedings().single { it.isActive }.breast)
    }

    @Test
    fun `crying that woke the child can be cancelled and sleep goes on`() = runTest {
        commands.perform(ShadeAction.START_SLEEP, childId = 1)
        clock.advance(60 * 60_000)
        commands.perform(ShadeAction.START_CRYING, childId = 1)
        val crying = cryings().single()
        assertTrue(!sleeps().single().isActive)
        assertEquals(crying.id, store.memo.value.startedCrying?.cryingId)

        commands.perform(ShadeAction.CANCEL_CRYING, childId = 1, eventId = crying.id)
        assertTrue(cryings().isEmpty())
        assertTrue(sleeps().single().isActive)
    }

    @Test
    fun `crying stopped by a button or by falling asleep asks for details`() = runTest {
        commands.perform(ShadeAction.START_CRYING, childId = 1)
        clock.advance(6 * 60_000)
        commands.perform(ShadeAction.END_CRYING, childId = 1)
        assertEquals(cryings().single().id, store.memo.value.finishedCryingId)

        commands.perform(ShadeAction.START_CRYING, childId = 1)
        assertNull(store.memo.value.finishedCryingId)
        clock.advance(60_000)
        commands.perform(ShadeAction.START_SLEEP, childId = 1)
        val second = cryings().maxBy { it.start }
        assertTrue(!second.isActive)
        assertEquals(second.id, store.memo.value.finishedCryingId)

        commands.cryingDetailsOpened()
        assertNull(store.memo.value.finishedCryingId)
    }

    @Test
    fun `button for a deleted child does nothing`() = runTest {
        commands.perform(ShadeAction.START_SLEEP, childId = 42)
        assertTrue(repo.events.value.isEmpty())
    }
}

/** Часы, которые тест двигает сам. */
private class MutableClock(private var millis: Long) : Clock() {
    fun advance(ms: Long) {
        millis += ms
    }

    override fun millis(): Long = millis
    override fun instant(): Instant = Instant.ofEpochMilli(millis)
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = this
}
