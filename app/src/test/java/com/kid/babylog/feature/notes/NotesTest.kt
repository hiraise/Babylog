package com.kid.babylog.feature.notes

import com.kid.babylog.core.model.BabyEvent
import com.kid.babylog.core.model.Breast
import com.kid.babylog.core.model.Crying
import com.kid.babylog.core.model.Diaper
import com.kid.babylog.core.model.DiaperKind
import com.kid.babylog.core.model.Feeding
import com.kid.babylog.core.model.Note
import com.kid.babylog.core.model.PeakHours
import com.kid.babylog.core.model.Sleep
import com.kid.babylog.core.model.Illness
import com.kid.babylog.core.model.IllnessName
import com.kid.babylog.core.model.Medicine
import com.kid.babylog.core.model.Temperature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class NotesTest {

    private val zone = ZoneId.of("Europe/Moscow")
    private val today = LocalDate.of(2026, 10, 18) // воскресенье
    private val minute = 60_000L

    private fun at(date: LocalDate, h: Int, m: Int = 0): Long =
        LocalDateTime.of(date, LocalTime.of(h, m)).atZone(zone).toInstant().toEpochMilli()

    private val now = at(today, 20)

    private fun cry(id: Long, date: LocalDate, h: Int, m: Int = 0, reasons: List<String> = emptyList(), helped: List<String> = emptyList()) =
        Crying(id, 1, at(date, h, m), end = at(date, h, m) + 20 * minute, reasons = reasons, helped = helped)

    @Test
    fun `feed has only crying and notes, by days, newest first`() {
        val events = listOf<BabyEvent>(
            cry(1, today.minusDays(1), 19, reasons = listOf("колики")),
            Note(2, 1, at(today, 11, 20), note = "Перевернулась!", tags = listOf("новое")),
            Diaper(3, 1, at(today, 12), DiaperKind.WET),
            cry(4, today, 18, 32, reasons = listOf("колики", "усталость")),
        )
        val feed = notesFeed(events, NotesFilter.All, zone)
        assertEquals(listOf(today, today.minusDays(1)), feed.map { it.date })
        assertEquals(listOf(4L, 2L), feed.first().records.map { it.id })

        assertEquals(listOf(4L, 1L), notesFeed(events, NotesFilter.CryingOnly, zone).flatMap { day -> day.records.map { it.id } })
        assertEquals(listOf(2L), notesFeed(events, NotesFilter.NotesOnly, zone).flatMap { day -> day.records.map { it.id } })
        assertEquals(listOf(2L), notesFeed(events, NotesFilter.Tag("новое"), zone).flatMap { day -> day.records.map { it.id } })
        // Метки ряда — частые первыми, при равенстве по алфавиту
        assertEquals(listOf("колики", "новое", "усталость"), filterTags(events))
    }

    @Test
    fun `tag summary counts crying and notes and marks the last four weeks`() {
        val events = listOf<BabyEvent>(
            cry(1, today, 18, reasons = listOf("колики", "усталость"), helped = listOf("укачивание")),
            cry(2, today.minusDays(1), 19, reasons = listOf("колики"), helped = listOf("укачивание", "грудь")),
            cry(3, today.minusDays(3), 20, 30, reasons = listOf("колики"), helped = listOf("тёплое на живот")),
            Note(4, 1, at(today.minusDays(1), 21, 50), tags = listOf("питание мамы", "колики"), note = "Ела капусту"),
            // Пять недель назад — вне календаря, но в счёте метки
            cry(5, today.minusWeeks(5), 14, reasons = listOf("колики")),
        )
        val summary = tagSummary(events, "колики", now, zone)
        assertEquals(4, summary.cryings)
        assertEquals(1, summary.notes)
        // Календарь — с понедельника три недели назад по сегодняшнее воскресенье
        assertEquals(28, summary.days.size)
        assertEquals(LocalDate.of(2026, 9, 21), summary.days.first().date)
        val yesterday = summary.days.single { it.date == today.minusDays(1) }
        assertEquals(1, yesterday.cryings)
        assertTrue(yesterday.note)
        assertTrue(summary.days.last().today)
        assertFalse(summary.days.any { it.future })
        // Три из четырёх — вечером: «с 18 до 21»
        assertEquals(PeakHours(18, 21, 3), summary.peak)
        assertEquals(listOf("укачивание" to 2, "грудь" to 1, "тёплое на живот" to 1), summary.helped)
        assertEquals(listOf("питание мамы" to 1, "усталость" to 1), summary.together)
    }

    @Test
    fun `before a tagged crying — awake longer than usual and time after feeding`() {
        val day = today.minusDays(1)
        val events = listOf<BabyEvent>(
            // Обычные окна бодрствования — 1 ч
            Sleep(1, 1, at(day, 6), end = at(day, 8)),
            Sleep(2, 1, at(day, 9), end = at(day, 10)),
            Sleep(3, 1, at(day, 11), end = at(day, 12)),
            // Перед плачем в 15:00 не спал 3 ч — дольше обычного
            Feeding(4, 1, at(day, 14), end = at(day, 14, 20), breast = Breast.LEFT),
            cry(5, day, 15, reasons = listOf("усталость")),
        )
        val summary = tagSummary(events, "усталость", now, zone)
        assertEquals(1, summary.awakeKnown)
        assertEquals(1, summary.longerAwake)
        assertEquals(40 * minute, summary.sinceFeeding)
    }

    @Test
    fun `this week's days after today are left empty`() {
        val wednesday = LocalDate.of(2026, 10, 14)
        val summary = tagSummary(listOf(Note(1, 1, at(wednesday, 9), tags = listOf("зубки"))), "зубки", at(wednesday, 10), zone)
        assertEquals(LocalDate.of(2026, 10, 18), summary.days.last().date)
        assertEquals(4, summary.days.count { it.future })
        assertEquals(0, summary.cryings)
        assertEquals(1, summary.notes)
    }

    @Test
    fun `illnesses are listed newest first, the going one on top`() {
        val past = Illness(id = 1, childId = 1, start = at(today.minusDays(11), 19, 30), end = at(today.minusDays(4), 20), names = listOf(IllnessName("ОРВИ"), IllnessName("Отит", today.minusDays(8))))
        val going = Illness(id = 2, childId = 1, start = at(today.minusDays(2), 8))
        val events = listOf(
            past, going,
            Temperature(id = 3, childId = 1, start = at(today.minusDays(8), 3), tenths = 392),
            Temperature(id = 4, childId = 1, start = at(today.minusDays(9), 3), tenths = 386),
            Medicine(id = 5, childId = 1, start = at(today.minusDays(10), 9), name = "Ибупрофен"),
            Medicine(id = 6, childId = 1, start = at(today.minusDays(8), 12), name = "Амоксициллин"),
        )
        val entries = illnessEntries(events, now, zone)
        assertEquals(listOf(2L, 1L), entries.map { it.illness.id })
        assertEquals(3, entries[0].days)
        assertEquals("с 16 окт.", entries[0].range)
        val ended = entries[1]
        assertEquals("ОРВИ, Отит", ended.title)
        assertEquals("7–14 окт.", ended.range)
        assertEquals(8, ended.days)
        assertEquals(392, ended.maxTenths)
        assertEquals(listOf("Ибупрофен", "Амоксициллин"), ended.medicines)
    }

    @Test
    fun `illnesses filter shows no feed records`() {
        assertFalse(NotesFilter.Illnesses.matches(Note(id = 1, childId = 1, start = now, note = "сыпь")))
    }
}
