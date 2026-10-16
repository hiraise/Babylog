package com.kid.babylog.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EventRecordTest {

    private val events = listOf(
        Feeding(id = 1, childId = 7, start = 1_000, breast = Breast.RIGHT, note = "ночь"),
        Diaper(id = 2, childId = 7, start = 2_000, kind = DiaperKind.MIXED),
        Sleep(id = 3, childId = 7, start = 3_000, end = 9_000, note = "дома"),
        Sleep(id = 4, childId = 7, start = 10_000, end = null),
        Medicine(id = 5, childId = 7, start = 4_000, name = "Витамин D", note = "1 капля"),
        Weight(id = 6, childId = 7, start = 5_000, grams = 4350),
    )

    @Test
    fun `every type survives record round trip`() {
        events.forEach { event ->
            assertEquals(event, event.toRecord().toEvent(id = event.id, childId = event.childId))
        }
    }

    @Test
    fun `codes are case insensitive and normalized`() {
        val event = EventRecord("feeding", 1_000, null, "left", "").toEvent(childId = 1)
        assertEquals(Breast.LEFT, (event as Feeding).breast)
        assertEquals("LEFT", event.toRecord().detail)
        assertEquals("FEEDING", event.toRecord().type)
    }

    @Test
    fun `end is kept only for sleep`() {
        val feeding = EventRecord(EventRecord.FEEDING, 1_000, 2_000, "LEFT", "").toEvent(childId = 1)!!
        assertNull(feeding.toRecord().end)
    }

    @Test
    fun `invalid records are rejected`() {
        assertNull(EventRecord("BADTYPE", 1, null, "X", "").toEvent(childId = 1))
        assertNull(EventRecord(EventRecord.FEEDING, 1, null, "MIDDLE", "").toEvent(childId = 1))
        assertNull(EventRecord(EventRecord.DIAPER, 1, null, "", "").toEvent(childId = 1))
        assertNull(EventRecord(EventRecord.MEDICINE, 1, null, " ", "без названия").toEvent(childId = 1))
        assertNull(EventRecord(EventRecord.WEIGHT, 1, null, "не число", "").toEvent(childId = 1))
        assertNull(EventRecord(EventRecord.WEIGHT, 1, null, "100", "").toEvent(childId = 1))
        assertNull(EventRecord(EventRecord.WEIGHT, 1, null, "99999", "").toEvent(childId = 1))
    }

    @Test
    fun `child names compare ignoring case and outer spaces`() {
        val children = listOf(Child(1, "Маша", null), Child(2, "Петя", null))
        assertTrue(children.isNameTaken(" маша "))
        assertTrue(!children.isNameTaken("Маша", exceptId = 1))
        assertTrue(!isValidChildName("   "))
        assertTrue(!isValidChildName("x".repeat(Child.MAX_NAME_LENGTH + 1)))
    }
}
