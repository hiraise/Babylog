package com.kid.babylog.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Правила болезни (ADR-0040): что к ней относится по времени, курсы лекарств, дни, подсказки. */
class IllnessesTest {

    private val zone = ZoneOffset.UTC
    private val hour = 3_600_000L
    private val day = 24 * hour

    private fun at(iso: String): Long = Instant.parse(iso).toEpochMilli()

    private val start = at("2026-10-07T19:30:00Z")
    private val end = at("2026-10-14T20:00:00Z")
    private val illness = Illness(id = 1, childId = 1, start = start, end = end, names = listOf(IllnessName("ОРВИ"), IllnessName("Отит", LocalDate.of(2026, 10, 10))))

    @Test
    fun `records inside the illness belong to it by time`() {
        val events = listOf(
            illness,
            Temperature(id = 2, childId = 1, start = start, tenths = 384),
            Note(id = 3, childId = 1, start = at("2026-10-10T11:30:00Z"), tags = listOf("врач"), note = "Отит слева"),
            Medicine(id = 4, childId = 1, start = at("2026-10-08T07:10:00Z"), name = "Ибупрофен"),
            // До болезни, после неё и чужой ребёнок — не её
            Temperature(id = 5, childId = 1, start = start - 1, tenths = 369),
            Temperature(id = 6, childId = 1, start = end + hour, tenths = 368),
            Temperature(id = 7, childId = 2, start = start + hour, tenths = 390),
            // Режим дня в записи болезни не входит
            Sleep(id = 8, childId = 1, start = start + hour, end = start + 2 * hour),
        )
        assertEquals(listOf(2L, 4L, 3L), events.illnessRecords(illness, now = end + day).map { it.id })
    }

    @Test
    fun `going illness collects records up to now`() {
        val going = illness.copy(end = null)
        val events = listOf(going, Temperature(id = 2, childId = 1, start = end + day, tenths = 380))
        assertEquals(listOf(2L), events.illnessRecords(going, now = end + 2 * day).map { it.id })
        assertEquals(emptyList<Long>(), events.illnessRecords(going, now = end).map { it.id })
    }

    @Test
    fun `course started in the illness stays in it after the end`() {
        val events = listOf(
            illness,
            // Антибиотик с 10 октября дважды в день — до 16-го, а выздоровела 14-го
            *(0 until 13).map { i -> Medicine(id = 10L + i, childId = 1, start = at("2026-10-10T12:00:00Z") + i * 12 * hour, name = "Амоксициллин") }.toTypedArray(),
            // Через неделю — уже не курс: перерыв больше 36 часов
            Medicine(id = 30, childId = 1, start = at("2026-10-23T09:00:00Z"), name = "амоксициллин"),
            // Витамин D дают каждый день и до болезни: его приёмы после неё не курс болезни
            Medicine(id = 40, childId = 1, start = at("2026-10-01T09:00:00Z"), name = "Витамин D"),
            Medicine(id = 41, childId = 1, start = at("2026-10-12T09:00:00Z"), name = "Витамин D"),
            Medicine(id = 42, childId = 1, start = at("2026-10-15T09:00:00Z"), name = "Витамин D"),
        )
        val medicines = events.illnessRecords(illness, now = at("2026-10-30T00:00:00Z")).filterIsInstance<Medicine>()
        assertEquals(listOf("2026-10-16T12:00:00Z"), medicines.filter { it.start > end }.map { Instant.ofEpochMilli(it.start).toString() }.takeLast(1))
        assertEquals(13, medicines.count { it.name == "Амоксициллин" })
        assertEquals(listOf(41L), medicines.filter { it.name == "Витамин D" }.map { it.id })
    }

    @Test
    fun `ongoing courses name medicines started in the illness and given lately`() {
        val going = illness.copy(end = null)
        val now = at("2026-10-14T21:00:00Z")
        val events = listOf(
            going,
            Medicine(id = 2, childId = 1, start = at("2026-10-14T12:00:00Z"), name = "Амоксициллин"),
            Medicine(id = 3, childId = 1, start = at("2026-10-08T07:00:00Z"), name = "Ибупрофен"),
            Medicine(id = 4, childId = 1, start = at("2026-10-01T09:00:00Z"), name = "Витамин D"),
            Medicine(id = 5, childId = 1, start = at("2026-10-14T09:00:00Z"), name = "Витамин D"),
        )
        assertEquals(listOf("Амоксициллин"), events.ongoingCourses(going, now))
    }

    @Test
    fun `illness medicines count doses and keep the last dose note`() {
        val events = listOf(
            illness,
            Medicine(id = 2, childId = 1, start = at("2026-10-08T07:10:00Z"), name = "ибупрофен", note = "4 мл"),
            Medicine(id = 3, childId = 1, start = at("2026-10-10T03:15:00Z"), name = "Ибупрофен"),
            Medicine(id = 4, childId = 1, start = at("2026-10-09T15:30:00Z"), name = "Парацетамол", note = "5 мл"),
        )
        val medicines = events.illnessMedicines(illness, now = end)
        assertEquals(listOf("Ибупрофен", "Парацетамол"), medicines.map { it.name })
        assertEquals(2, medicines.first().count)
        assertEquals("4 мл", medicines.first().note)
        assertEquals(at("2026-10-10T03:15:00Z"), medicines.first().last)
    }

    @Test
    fun `active and recent illness`() {
        val going = illness.copy(id = 9, start = end + 4 * day, end = null)
        assertEquals(going, listOf(illness, going).activeIllness())
        assertNull(listOf(illness).activeIllness())
        assertEquals(illness, listOf(illness).recentIllness(now = end + 4 * day))
        assertNull(listOf(illness).recentIllness(now = end + 8 * day))
        // Пока идёт другая болезнь, прошлую не предлагают
        assertNull(listOf(illness, going).recentIllness(now = end + 4 * day))
    }

    @Test
    fun `days and names are counted by calendar days`() {
        assertEquals(LocalDate.of(2026, 10, 7), illness.startDate(zone))
        assertEquals(4, illness.dayNumber(LocalDate.of(2026, 10, 10), zone))
        assertEquals(8, illness.days(LocalDate.of(2026, 10, 20), zone))
        assertEquals(3, illness.copy(end = null).days(LocalDate.of(2026, 10, 9), zone))
        assertEquals(LocalDate.of(2026, 10, 7), illness.names[0].sinceDate(illness, zone))
        assertEquals("ОРВИ, Отит", illness.title())
        val dates = listOf(illness).illnessDates(LocalDate.of(2026, 10, 20), zone)
        assertEquals(8, dates.size)
        assertEquals(LocalDate.of(2026, 10, 14), dates.max())
    }

    @Test
    fun `name choices put past names first without case duplicates`() {
        val events = listOf(
            illness,
            Illness(id = 2, childId = 1, start = start - 30 * day, end = start - 25 * day, names = listOf(IllnessName("орви"))),
            Illness(id = 3, childId = 1, start = start - 60 * day, end = start - 55 * day, names = listOf(IllnessName("Дерматит"))),
        )
        val choices = events.illnessNameChoices()
        // Чаще всего — ОРВИ, в последней болезни написанное заглавными
        assertEquals(listOf("ОРВИ", "Дерматит", "Отит"), choices.take(3))
        assertEquals(1, choices.count { it.equals("ОРВИ", ignoreCase = true) })
        assertEquals(DEFAULT_ILLNESS_NAMES, emptyList<BabyEvent>().illnessNameChoices())
    }
}
