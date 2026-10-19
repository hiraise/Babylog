package com.kid.babylog.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EventRecordTest {

    private val events = listOf(
        Feeding(id = 1, childId = 7, start = 1_000, end = 1_900, breast = Breast.RIGHT, note = "ночь"),
        Feeding(id = 7, childId = 7, start = 11_000, end = null, breast = Breast.LEFT),
        Diaper(id = 2, childId = 7, start = 2_000, kind = DiaperKind.MIXED),
        Sleep(id = 3, childId = 7, start = 3_000, end = 9_000, note = "дома"),
        Sleep(id = 4, childId = 7, start = 10_000, end = null),
        Medicine(id = 5, childId = 7, start = 4_000, name = "Витамин D", note = "1 капля"),
        Weight(id = 6, childId = 7, start = 5_000, grams = 4350),
        Height(id = 10, childId = 7, start = 5_500, millimeters = 625, note = "у врача"),
        Bottle(id = 8, childId = 7, start = 6_000, volumeMl = 120, milk = Milk.FORMULA, note = "докорм"),
        Bottle(id = 9, childId = 7, start = 7_000, volumeMl = 90),
        Crying(id = 11, childId = 7, start = 8_000, end = 8_900, reasons = listOf("колики", "голод"), helped = listOf("укачивание"), note = "вечер"),
        Crying(id = 12, childId = 7, start = 12_000, end = null),
        Crying(id = 13, childId = 7, start = 13_000, end = 13_500, helped = listOf("грудь")),
        Note(id = 14, childId = 7, start = 14_000, tags = listOf("зубки", "питание мамы"), note = "Тёрла ушко, отказывалась от груди"),
        Note(id = 15, childId = 7, start = 15_000, note = "Первый раз перевернулась"),
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
    fun `end is kept only for sleep and feeding`() {
        val feeding = EventRecord(EventRecord.FEEDING, 1_000, 2_000, "LEFT", "").toEvent(childId = 1)!!
        assertEquals(2_000L, feeding.toRecord().end)
        val diaper = EventRecord(EventRecord.DIAPER, 1_000, 2_000, "WET", "").toEvent(childId = 1)!!
        assertNull(diaper.toRecord().end)
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
        // Рост — в миллиметрах, целым: «62.5» — не сантиметры, а ошибка
        assertNull(EventRecord(EventRecord.HEIGHT, 1, null, "62.5", "").toEvent(childId = 1))
        assertNull(EventRecord(EventRecord.HEIGHT, 1, null, "62", "").toEvent(childId = 1))
        assertNull(EventRecord(EventRecord.HEIGHT, 1, null, "1501", "").toEvent(childId = 1))
    }

    @Test
    fun `crying detail keeps reasons and helped apart and normalizes tags`() {
        val crying = Crying(childId = 1, start = 0, end = 60_000, reasons = listOf("колики", "усталость"), helped = listOf("на руках"))
        assertEquals("колики;усталость|на руках", crying.toRecord().detail)
        assertEquals("", Crying(childId = 1, start = 0).toRecord().detail)
        // Регистр, пробелы и повторы — к одному виду; пустые метки пропадают
        val parsed = EventRecord("crying", 0, 60_000, " Колики ;колики;; ЗУБКИ |  белый   шум ", "").toEvent(childId = 1) as Crying
        assertEquals(listOf("колики", "зубки"), parsed.reasons)
        assertEquals(listOf("белый шум"), parsed.helped)
        assertEquals(60_000L, parsed.end)
        // Больше одного «|» — непонятно, где что: запись некорректна
        assertNull(EventRecord(EventRecord.CRYING, 0, 60_000, "а|б|в", "").toEvent(childId = 1))
    }

    @Test
    fun `note detail is its tags, the text stays in the note`() {
        val note = Note(childId = 1, start = 0, tags = listOf("зубки", "питание мамы"), note = "Тёрла ушко")
        assertEquals(EventRecord("NOTE", 0, null, "зубки;питание мамы", "Тёрла ушко"), note.toRecord())
        // Метки — к общему виду, как у плача; заметка без меток и конца — тоже заметка
        val parsed = EventRecord("note", 0, 5_000, " Зубки ;; ЗУБКИ ; новое ", "").toEvent(childId = 1) as Note
        assertEquals(listOf("зубки", "новое"), parsed.tags)
        assertEquals(Note(childId = 1, start = 0), EventRecord("NOTE", 0, null, "", "").toEvent(childId = 1))
    }

    @Test
    fun `tags are lower case without separators and not too long`() {
        assertEquals("колики живот", normalizeTag("  Колики;Живот  "))
        assertEquals("а б", normalizeTag("а|б"))
        assertNull(normalizeTag(" ; | "))
        assertEquals(MAX_TAG_LENGTH, normalizeTag("о".repeat(50))!!.length)
    }

    @Test
    fun `height detail is millimeters`() {
        assertEquals("625", Height(childId = 1, start = 0, millimeters = 625).toRecord().detail)
        assertEquals(500, (EventRecord("height", 1_000, null, " 500 ", "").toEvent(childId = 1) as Height).millimeters)
    }

    @Test
    fun `child names compare ignoring case and outer spaces`() {
        val children = listOf(Child(1, "Маша", null), Child(2, "Петя", null))
        assertTrue(children.isNameTaken(" маша "))
        assertTrue(!children.isNameTaken("Маша", exceptId = 1))
        assertTrue(!isValidChildName("   "))
        assertTrue(!isValidChildName("x".repeat(Child.MAX_NAME_LENGTH + 1)))
    }

    @Test
    fun `bottle detail is volume and optional milk code`() {
        assertEquals("120:FORMULA", Bottle(childId = 1, start = 0, volumeMl = 120, milk = Milk.FORMULA).toRecord().detail)
        assertEquals("90", Bottle(childId = 1, start = 0, volumeMl = 90).toRecord().detail)
        val parsed = EventRecord("bottle", 1_000, null, " 60 : breast_milk ", "").toEvent(childId = 1) as Bottle
        assertEquals(60 to Milk.BREAST_MILK, parsed.volumeMl to parsed.milk)
        // Объём вне диапазона, неизвестный код, лишняя часть — запись некорректна
        listOf("0", "501", "abc", "120:JUICE", "120:FORMULA:x", "").forEach {
            assertNull(it, EventRecord(EventRecord.BOTTLE, 1_000, null, it, "").toEvent(childId = 1))
        }
    }
}
