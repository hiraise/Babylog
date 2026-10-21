package com.kid.babylog.feature.summary

import com.kid.babylog.R
import com.kid.babylog.core.model.Bottle
import com.kid.babylog.core.model.Breast
import com.kid.babylog.core.model.Crying
import com.kid.babylog.core.model.Diaper
import com.kid.babylog.core.model.DiaperKind
import com.kid.babylog.core.model.Feeding
import com.kid.babylog.core.model.Medicine
import com.kid.babylog.core.model.Milk
import com.kid.babylog.core.model.Sex
import com.kid.babylog.core.model.Sleep
import com.kid.babylog.core.model.Temperature
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class DaySummaryTest {

    private val zone = ZoneId.of("Europe/Moscow")
    private val today = LocalDate.of(2026, 10, 15)
    private val yesterday = today.minusDays(1)

    private fun at(date: LocalDate, hour: Int, minute: Int = 0): Long =
        LocalDateTime.of(date, java.time.LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()

    private val now = at(today, 12)
    private val hour = 3_600_000L
    private val minute = 60_000L

    /** Законченное кормление длиной [minutes] минут. */
    private fun fed(id: Long, start: Long, breast: Breast, minutes: Int = 15) =
        Feeding(id, 1, start, end = start + minutes * minute, breast = breast)

    @Test
    fun `crying line appears only on days with crying`() {
        assertEquals(4, summarizeDay(listOf(fed(1, at(today, 8), Breast.LEFT)), today, now, zone).lines(null).size)
        val events = listOf(
            Crying(2, 1, at(today, 9), end = at(today, 9, 24)),
            // Идущий плач считается до now, как сон
            Crying(3, 1, at(today, 11, 50)),
            Crying(4, 1, at(yesterday, 20), end = at(yesterday, 20, 30)),
        )
        val summary = summarizeDay(events, today, now, zone)
        assertEquals(2, summary.cryings)
        assertEquals(34 * minute, summary.cryingMillis)
        assertEquals("Плач: 2 раза, 34 мин", summary.lines(null).last().shown())
    }

    @Test
    fun `counts only records of the chosen day`() {
        val events = listOf(
            fed(1, at(today, 8), Breast.LEFT),
            fed(2, at(today, 11), Breast.RIGHT),
            fed(3, at(yesterday, 23), Breast.LEFT),
            Diaper(4, 1, at(today, 9), DiaperKind.WET),
            Diaper(5, 1, at(today, 10), DiaperKind.WET),
            Diaper(6, 1, at(today, 10, 30), DiaperKind.DIRTY),
        )
        val summary = summarizeDay(events, today, now, zone)
        assertEquals(2, summary.feedings)
        assertEquals(mapOf(DiaperKind.WET to 2, DiaperKind.DIRTY to 1), summary.diapers)
        assertEquals(3, summary.diaperCount)
        val diapers = summary.lines(null)[1]
        assertEquals("Подгузников: 3 (пописал×2 · покакал×1)", diapers.shown())
        assertEquals(listOf(R.drawable.ic_diaper_wet, R.drawable.ic_diaper_dirty), diapers.parts.map { it.icon })
    }

    @Test
    fun `summary has only the day totals, not the last feeding`() {
        // «Последнее кормление» — на плитке кормления (ADR-0026): в сводке его нет и за сегодня
        val events = listOf(fed(1, at(yesterday, 23), Breast.RIGHT))
        val summary = summarizeDay(events, today, now, zone)
        assertEquals(0, summary.feedings)
        assertEquals("Кормлений: 0", summary.lines(null).first().shown())
        assertEquals(1, summarizeDay(events, yesterday, now, zone).feedings)
    }

    @Test
    fun `switching breast is one feeding`() {
        val events = listOf(
            fed(1, at(today, 10), Breast.LEFT, minutes = 10),
            fed(2, at(today, 10, 10), Breast.RIGHT, minutes = 5),
            fed(3, at(today, 8), Breast.RIGHT), // отдельное кормление: начато не в конце другого
        )
        val summary = summarizeDay(events, today, now, zone)
        assertEquals(2, summary.feedings)
        // Только грудью — разбивать нечего
        assertEquals("Кормлений: 2", summary.lines(null).first().shown())
    }

    @Test
    fun `running feeding is counted like a finished one`() {
        val events = listOf(
            fed(1, at(today, 11, 40), Breast.LEFT, minutes = 10),
            Feeding(2, 1, at(today, 11, 50), end = null, breast = Breast.RIGHT),
        )
        assertEquals("Кормлений: 1", summarizeDay(events, today, now, zone).lines(null).first().shown())
    }

    @Test
    fun `old feeding without duration is a separate feeding`() {
        val start = at(today, 9)
        val events = listOf(Feeding(1, 1, start, end = start, breast = Breast.LEFT), fed(2, at(today, 9, 30), Breast.RIGHT))
        assertEquals(2, summarizeDay(events, today, now, zone).feedings)
    }

    @Test
    fun `diapers of a girl are in feminine form`() {
        val events = listOf(Diaper(1, 1, at(today, 9), DiaperKind.WET), Diaper(2, 1, at(today, 10), DiaperKind.MIXED))
        val line = summarizeDay(events, today, now, zone).lines(Sex.GIRL)[1]
        assertEquals("Подгузников: 2 (пописала×1 · и то, и то×1)", line.shown())
        assertEquals(listOf(R.drawable.ic_diaper_wet, R.drawable.ic_diaper_mixed), line.parts.map { it.icon })
    }

    @Test
    fun `sleep across midnight is split between days`() {
        val events = listOf(Sleep(1, 1, at(yesterday, 22), end = at(today, 6)))
        assertEquals(2 * hour, summarizeDay(events, yesterday, now, zone).sleepMillis)
        assertEquals(6 * hour, summarizeDay(events, today, now, zone).sleepMillis)
    }

    @Test
    fun `active sleep counts until now`() {
        val events = listOf(Sleep(1, 1, at(today, 10), end = null))
        val summary = summarizeDay(events, today, now, zone)
        assertEquals(2 * hour, summary.sleepMillis)
        // Что малыш спит сейчас, видно на плитке сна (ADR-0026)
        assertEquals("Сон: 2 ч", summary.lines(null)[2].shown())
    }

    @Test
    fun `medicines are grouped ignoring case with the latest name`() {
        val events = listOf(
            Medicine(1, 1, at(today, 8), "витамин d"),
            Medicine(2, 1, at(today, 9), "Витамин D "),
            Medicine(3, 1, at(today, 10), "Нурофен"),
        )
        val summary = summarizeDay(events, today, now, zone)
        assertEquals(listOf("Нурофен" to 1, "Витамин D" to 2), summary.medicines)
        assertEquals("Лекарств: 3 (Нурофен, Витамин D ×2)", summary.lines(null).last().shown())
    }

    @Test
    fun `empty day shows zeros without breakdowns`() {
        val lines = summarizeDay(emptyList(), yesterday, now, zone).lines(null)
        assertEquals(
            listOf("Кормлений: 0", "Подгузников: 0", "Сон: 0 мин", "Лекарств: 0"),
            lines.map { it.shown() },
        )
    }

    /** Строка так, как её читает TalkBack: метка, значение, разбивка с описаниями вместо иконок. */
    private fun SummaryLine.shown() =
        "$label $value" + if (parts.isEmpty()) "" else parts.joinToString(" · ", " (", ")") { "${it.description}×${it.count}${it.suffix}" }

    @Test
    fun `bottles count as feedings and split the feeding line by icons`() {
        val events = listOf(
            fed(1, at(today, 8), Breast.LEFT),
            Bottle(2, 1, at(today, 10), volumeMl = 120, milk = Milk.FORMULA),
            Bottle(3, 1, at(today, 11), volumeMl = 90),
            Bottle(4, 1, at(yesterday, 22), volumeMl = 150),
        )
        val summary = summarizeDay(events, today, now, zone)
        assertEquals(3, summary.feedings)
        assertEquals(2 to 210, summary.bottles to summary.bottleMl)
        val line = summary.lines(null).first()
        assertEquals("Кормлений: 3 (грудью×1 · бутылочкой×2, 210\u00A0мл)", line.shown())
        assertEquals(listOf(R.drawable.ic_feeding, R.drawable.ic_bottle), line.parts.map { it.icon })
        // Только бутылочки — без части «грудью»
        val bottlesOnly = summarizeDay(events.drop(1), today, now, zone).lines(null).first()
        assertEquals("Кормлений: 2 (бутылочкой×2, 210\u00A0мл)", bottlesOnly.shown())
    }

    @Test
    fun `temperature line shows the highest of the day`() {
        val one = summarizeDay(listOf(Temperature(id = 1, childId = 1, start = at(today, 3, 10), tenths = 392)), today, now, zone)
        assertEquals(SummaryLine(R.drawable.ic_thermometer, "Температура:", "39,2"), one.lines(Sex.GIRL).last())
        val many = summarizeDay(
            listOf(
                Temperature(id = 1, childId = 1, start = at(today, 3, 10), tenths = 392),
                Temperature(id = 2, childId = 1, start = at(today, 7, 30), tenths = 381),
                Temperature(id = 3, childId = 1, start = at(yesterday, 22), tenths = 399),
            ),
            today, now, zone,
        )
        assertEquals(SummaryLine(R.drawable.ic_thermometer, "Температура:", "до 39,2 (2 раза)"), many.lines(Sex.GIRL).last())
        assertEquals(4, summarizeDay(emptyList(), today, now, zone).lines(Sex.GIRL).size)
    }
}
