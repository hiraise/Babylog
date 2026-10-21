package com.kid.babylog.feature.illness

import com.kid.babylog.core.model.BabyEvent
import com.kid.babylog.core.model.Breast
import com.kid.babylog.core.model.Child
import com.kid.babylog.core.model.Diaper
import com.kid.babylog.core.model.DiaperKind
import com.kid.babylog.core.model.Feeding
import com.kid.babylog.core.model.Illness
import com.kid.babylog.core.model.IllnessName
import com.kid.babylog.core.model.Medicine
import com.kid.babylog.core.model.Note
import com.kid.babylog.core.model.Sex
import com.kid.babylog.core.model.Sleep
import com.kid.babylog.core.model.Temperature
import com.kid.babylog.core.model.TemperatureMethod
import com.kid.babylog.core.model.Weight
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.TimeZone

/** Карточка, экран и «Для врача» болезни (ADR-0040) на истории Маши: ОРВИ с 7 октября, отит на 4-й день. */
class IllnessModelTest {

    private val zone = ZoneOffset.UTC
    private val hour = 3_600_000L
    private lateinit var systemZone: TimeZone

    // Время в тексте — в поясе устройства: фиксируем его
    @Before
    fun fixTimeZone() {
        systemZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun restoreTimeZone() = TimeZone.setDefault(systemZone)

    private fun at(iso: String): Long = Instant.parse(iso).toEpochMilli()

    private val illness = Illness(
        id = 1, childId = 1, start = at("2026-10-07T19:30:00Z"),
        names = listOf(IllnessName("ОРВИ"), IllnessName("Отит", LocalDate.of(2026, 10, 10))),
    )
    private val now = at("2026-10-10T12:30:00Z")

    /** Две недели до болезни — режим: 8 кормлений, 7 мокрых подгузников, сон 12 ч; 9 октября — болеет. */
    private fun events(): List<BabyEvent> = buildList {
        add(illness)
        var id = 100L
        (1..14).forEach { back ->
            val day = at("2026-10-07T00:00:00Z") - back * 24 * hour
            repeat(8) { add(Feeding(id = id++, childId = 1, start = day + it * 3 * hour, end = day + it * 3 * hour + 15 * 60_000, breast = Breast.LEFT)) }
            repeat(7) { add(Diaper(id = id++, childId = 1, start = day + it * 3 * hour + hour, kind = DiaperKind.WET)) }
            add(Sleep(id = id++, childId = 1, start = day + 12 * hour, end = day + 24 * hour))
        }
        val sick = at("2026-10-09T00:00:00Z")
        repeat(11) { add(Feeding(id = id++, childId = 1, start = sick + it * 2 * hour, end = sick + it * 2 * hour + 10 * 60_000, breast = Breast.RIGHT)) }
        repeat(4) { add(Diaper(id = id++, childId = 1, start = sick + it * 4 * hour, kind = DiaperKind.WET)) }
        add(Diaper(id = id++, childId = 1, start = sick + 20 * hour, kind = DiaperKind.MIXED))
        add(Sleep(id = id++, childId = 1, start = sick + 13 * hour, end = sick + 13 * hour + 650 * 60_000))
        add(Temperature(id = 2, childId = 1, start = at("2026-10-07T19:30:00Z"), tenths = 384, method = TemperatureMethod.ARMPIT))
        add(Temperature(id = 3, childId = 1, start = at("2026-10-10T03:10:00Z"), tenths = 392))
        add(Temperature(id = 4, childId = 1, start = at("2026-10-10T10:40:00Z"), tenths = 388))
        add(Medicine(id = 5, childId = 1, start = at("2026-10-10T03:15:00Z"), name = "Ибупрофен", note = "4 мл"))
        add(Medicine(id = 6, childId = 1, start = at("2026-10-10T12:00:00Z"), name = "Амоксициллин", note = "5 мл"))
        add(Note(id = 7, childId = 1, start = at("2026-10-10T11:30:00Z"), tags = listOf("врач"), note = "Отит слева"))
    }

    @Test
    fun `card shows the day, names and the latest temperature and medicine`() {
        val card = illnessCardModel(events(), illness, now, zone)
        assertEquals(4, card.day)
        assertEquals("ОРВИ, Отит", card.names)
        assertEquals(388, card.lastTemperature?.tenths)
        assertEquals("Амоксициллин", card.lastMedicine?.name)
    }

    @Test
    fun `screen lists days newest first with names and start in their days`() {
        val model = illnessScreenModel(events(), illness, now, zone)
        assertEquals(4, model.days)
        assertEquals(listOf(4, 3, 2, 1), model.byDay.map { it.number })
        val today = model.byDay.first().entries
        assertEquals(listOf(6L, 7L, 4L, 5L, 3L), today.filterIsInstance<DayEntry.Record>().map { it.event.id })
        assertTrue(today.last() is DayEntry.NameAdded)
        assertTrue(model.byDay.last().entries.last() is DayEntry.Started)
        assertEquals(3, model.temperatures.size)
    }

    @Test
    fun `yesterday is compared with the usual two weeks before`() {
        val diary = illnessScreenModel(events(), illness, now, zone).diary!!
        assertTrue(diary.yesterday)
        val lines = diary.lines.associateBy { it.kind }
        assertEquals(5.0, lines.getValue(DiaryLineKind.WET_DIAPERS).value, 0.0)
        assertEquals(7.0, lines.getValue(DiaryLineKind.WET_DIAPERS).usual!!, 0.0)
        assertEquals(11.0, lines.getValue(DiaryLineKind.FEEDINGS).value, 0.0)
        assertEquals(8.0, lines.getValue(DiaryLineKind.FEEDINGS).usual!!, 0.0)
        // Плач не записывают — строки о нём нет
        assertNull(lines[DiaryLineKind.CRYING])
    }

    @Test
    fun `no comparison on the first day`() {
        assertNull(illnessScreenModel(events(), illness, at("2026-10-07T21:00:00Z"), zone).diary)
    }

    @Test
    fun `doctor text has who, what, temperature, medicines, diary and notes`() {
        val child = Child(1, "Маша", at("2026-01-01T00:00:00Z"), Sex.GIRL)
        val text = doctorText(illnessScreenModel(events(), illness, now, zone), child, Weight(childId = 1, start = 0, grams = 8900), zone)
        val expected = """
            Маша, 9 мес 9 дн., 8,9 кг
            Болеет с 7 окт. (4-й день): ОРВИ, с 10 окт. — Отит.

            Температура: макс. 39,2 (10 окт., 03:10), последняя 38,8 (10 окт., 10:40).
            По дням: 7 окт. — 38,4 · 10 — 39,2

            Лекарства:
            • Ибупрофен 4 мл — 1 раз, последний 10 окт. в 03:15
            • Амоксициллин 5 мл — 1 раз, последний 10 окт. в 12:00

            Вчера: мокрых подгузников 5 (обычно 7), кормлений 11 (обычно 8), сон 10 ч 50 мин (обычно 12 ч).

            Заметки:
            10 окт., 11:30 — Отит слева [врач]
        """.trimIndent()
        assertEquals(expected, text)
    }

    @Test
    fun `ended illness says how long it lasted`() {
        val ended = illness.copy(end = at("2026-10-14T20:00:00Z"), names = emptyList())
        val child = Child(1, "Маша", null, Sex.GIRL)
        val text = doctorText(illnessScreenModel(listOf(ended), ended, at("2026-10-20T10:00:00Z"), zone), child, null, zone)
        assertEquals("Маша\nБолела 7–14 окт. (8 дней).", text)
    }
}
