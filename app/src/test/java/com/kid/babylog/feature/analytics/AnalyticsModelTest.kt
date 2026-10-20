package com.kid.babylog.feature.analytics

import com.kid.babylog.core.model.BabyEvent
import com.kid.babylog.core.model.Bottle
import com.kid.babylog.core.model.Breast
import com.kid.babylog.core.model.Diaper
import com.kid.babylog.core.model.DiaperKind
import com.kid.babylog.core.model.Feeding
import com.kid.babylog.core.model.Height
import com.kid.babylog.core.model.Medicine
import com.kid.babylog.core.model.Sex
import com.kid.babylog.core.model.Sleep
import com.kid.babylog.core.model.Weight
import com.kid.babylog.testing.sampleMonths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class AnalyticsModelTest {

    private val zone = ZoneId.of("Europe/Moscow")
    private val today = LocalDate.of(2026, 10, 18) // воскресенье
    private val minute = 60_000L

    private fun at(date: LocalDate, h: Int, m: Int = 0): Long =
        LocalDateTime.of(date, LocalTime.of(h, m)).atZone(zone).toInstant().toEpochMilli()

    private val now = at(today, 11, 20)

    private fun day(n: Long) = today.minusDays(n)

    private fun fed(id: Long, start: Long, minutes: Int = 15) = Feeding(id, 1, start, end = start + minutes * minute, breast = Breast.LEFT)

    private fun model(events: List<BabyEvent>, period: AnalyticsPeriod = AnalyticsPeriod.WEEK) =
        analyticsModel(events, period, now, zone, Sex.GIRL)

    private fun AnalyticsModel.metric(metric: Metric) = metrics.single { it.metric == metric }

    @Test
    fun `no records — a hint instead of charts`() {
        val model = model(emptyList())
        assertFalse(model.hasRecords)
        assertTrue(model.metrics.isEmpty())
    }

    @Test
    fun `averages skip today and days without records`() {
        // Три полных дня с 2, 4 и 6 кормлениями, сегодня — 10: среднее 4, а не (2+4+6+10)/7
        var id = 1L
        val events = listOf(1L to 2, 2L to 4, 4L to 6, 0L to 10).flatMap { (ago, count) ->
            (0 until count).map { fed(id++, at(day(ago), 1 + it)) }
        }
        val feedings = model(events).metric(Metric.FEEDINGS)
        assertEquals("4,0", feedings.value)
        assertEquals("в сутки", feedings.unit)
        // Столбик сегодня — заштрихован, у дня без записей столбика нет
        assertTrue(feedings.columns.last().partial)
        assertTrue(feedings.columns[3].parts.isEmpty())
        assertEquals("чт, 15 окт.: записей нет", feedings.columns[3].caption)
        assertEquals(listOf("пн", "вт", "ср", "чт", "пт", "сб", "вс"), feedings.columns.map { it.label })
    }

    @Test
    fun `comparison with the previous week needs three full days there`() {
        var id = 1L
        val thisWeek = (1L..3L).flatMap { ago -> (0 until 8).map { fed(id++, at(day(ago), 1 + it * 2)) } }
        val twoDaysBefore = (8L..9L).flatMap { ago -> (0 until 6).map { fed(id++, at(day(ago), 1 + it * 2)) } }
        assertNull(model(thisWeek + twoDaysBefore).metric(Metric.FEEDINGS).delta)

        val threeDaysBefore = twoDaysBefore + (0 until 6).map { fed(id++, at(day(10), 1 + it * 2)) }
        assertEquals("+2,0 к прошлой неделе", model(thisWeek + threeDaysBefore).metric(Metric.FEEDINGS).delta)
    }

    @Test
    fun `breast time shows only days where every feeding has a duration`() {
        val events = listOf(
            fed(1, at(day(1), 8), minutes = 20),
            fed(2, at(day(1), 11), minutes = 20),
            fed(3, at(day(2), 8), minutes = 30),
            // День до таймера: кормление без длительности — сутки в «у груди» не входят
            Feeding(4, 1, at(day(3), 8), end = at(day(3), 8), breast = Breast.RIGHT),
            Feeding(5, 1, at(day(3), 12), end = at(day(3), 12), breast = Breast.RIGHT),
        )
        val note = model(events).metric(Metric.FEEDINGS).note.orEmpty()
        assertTrue(note, note.endsWith("у груди 35 мин в сутки по 2 дням из 3"))
    }

    @Test
    fun `no breast time at all when no feeding has a duration`() {
        val events = (0 until 6).map { Feeding(it + 1L, 1, at(day(1 + it / 3L), 8 + it), end = at(day(1 + it / 3L), 8 + it), breast = Breast.LEFT) }
        val note = model(events).metric(Metric.FEEDINGS).note.orEmpty()
        assertFalse(note, note.contains("у груди"))
    }

    @Test
    fun `typical interval is the median gap between feedings`() {
        // Промежутки 3, 3, 3 и 9 часов (пропущенная ночная запись) — медиана 3 ч
        val events = listOf(6, 9, 12, 15).map { fed(it.toLong(), at(day(1), it)) } +
            fed(30, at(day(2), 15)) + fed(31, at(day(2), 18))
        val note = model(events).metric(Metric.FEEDINGS).note.orEmpty()
        assertTrue(note, note.startsWith("обычно раз в 3 ч"))
    }

    @Test
    fun `bottle card appears only with bottles in the period`() {
        assertTrue(model(listOf(fed(1, at(day(1), 8)))).metrics.none { it.metric == Metric.BOTTLES })
        val bottles = model(listOf(Bottle(1, 1, at(day(1), 8), volumeMl = 120), Bottle(2, 1, at(day(1), 20), volumeMl = 150)))
            .metric(Metric.BOTTLES)
        assertEquals("270 мл", bottles.value)
        assertEquals("2,0 бутылочки в сутки", bottles.note)
    }

    @Test
    fun `diaper legend uses the child's sex`() {
        val events = listOf(
            Diaper(1, 1, at(day(1), 8), DiaperKind.WET),
            Diaper(2, 1, at(day(1), 9), DiaperKind.WET),
            Diaper(3, 1, at(day(1), 10), DiaperKind.DIRTY),
        )
        val diapers = model(events).metric(Metric.DIAPERS)
        assertEquals(listOf("пописала" to "2,0", "и то, и то" to "0,0", "покакала" to "1,0"), diapers.legend.map { it.label to it.text })
        assertEquals("сб, 17 окт.: 3 подгузника (пописала 2, покакала 1)", diapers.columns[5].caption)
    }

    @Test
    fun `sleep tells how often it happens, like feedings`() {
        // Сны в 1, 9, 12 и 15 ч: четыре раза, промежутки 8, 3 и 3 ч — обычно раз в 3 ч
        val events = listOf(1 to 6, 9 to 10, 12 to 13, 15 to 16).mapIndexed { i, (from, to) ->
            Sleep(i + 1L, 1, at(day(1), from), end = at(day(1), to))
        }
        val sleep = model(events).metric(Metric.SLEEP)
        assertEquals("4,0 раза в сутки · обычно раз в 3 ч · самый долгий сон — 5 ч", sleep.note)
        assertEquals("сб, 17 окт.: сон 8 ч (ночью 5 ч, днём 3 ч), 4 раза, самый долгий 5 ч", sleep.columns[5].caption)
    }

    @Test
    fun `sleep scale is rounded to whole four hours`() {
        val sleep = model(listOf(Sleep(1, 1, at(day(1), 0), end = at(day(1), 13)))).metric(Metric.SLEEP)
        assertEquals("16 ч", sleep.maxLabel)
        assertEquals("8 ч", sleep.midLabel)
        assertEquals("13 ч", sleep.value)
    }

    @Test
    fun `all time is weekly from the monday of the first routine record`() {
        val events = sampleMonths(childId = 1, today = today, now = now, zone = zone, days = 30)
        val feedings = model(events, AnalyticsPeriod.ALL).metric(Metric.FEEDINGS)
        // 30 суток по сегодня начинаются в субботу 19 сентября: первая неделя — с понедельника 14 сентября.
        // Вес с рождения — не запись режима: «всё время» из-за него не начинается в мае
        assertTrue(feedings.columns.first().caption, feedings.columns.first().caption.startsWith("неделя с 14 сент."))
        assertEquals(5, feedings.columns.size)
        assertTrue(feedings.columns.last().partial)
        assertEquals("по неделям", feedings.delta)
    }

    @Test
    fun `medicines are counted for the period with the latest spelling`() {
        val events = listOf(
            Medicine(1, 1, at(day(20), 9), "витамин d"),
            Medicine(2, 1, at(day(1), 7, 20), "витамин d"),
            Medicine(3, 1, at(day(0), 7, 20), "Витамин D"),
            Diaper(4, 1, at(day(1), 8), DiaperKind.WET),
        )
        assertEquals(
            listOf(MedicineLine("Витамин D", "2 приёма · последний сегодня в 07:20", count = 2, last = "последний сегодня в 07:20")),
            model(events).medicines,
        )
        assertEquals("3 приёма · последний сегодня в 07:20", model(events, AnalyticsPeriod.ALL).medicines.single().info)
    }

    @Test
    fun `weight gain is measured against a weighing at least two weeks earlier`() {
        val events = listOf(
            Weight(1, 1, at(day(36), 10), 5850),
            Weight(2, 1, at(day(14), 10), 6080),
            Weight(3, 1, at(day(4), 10), 6350),
        )
        val weight = model(events).weight!!
        assertEquals("6,35 кг", weight.value)
        assertEquals("14 окт.", weight.date)
        // Взвешивание 4 октября ближе двух недель — прибавка от 12 сентября: 500 г за 32 дня
        assertEquals("+110 г в неделю с 12 сент.", weight.note)
        // Плитке обзора — та же прибавка без даты (ADR-0029)
        assertEquals("+110 г в неделю", weight.rate)
    }

    @Test
    fun `measurements are listed newest first with the change to the previous one`() {
        val events = listOf(
            Weight(1, 1, at(day(36), 10), 5850),
            Weight(2, 1, at(day(14), 10), 6080),
            Weight(3, 1, at(day(4), 10), 6350),
            Height(4, 1, at(day(30), 11), 600),
            Height(5, 1, at(day(10), 11), 600),
        )
        assertEquals(
            listOf(
                GrowthEntry("14 окт.", "6,35 кг", "+270 г"),
                GrowthEntry("4 окт.", "6,08 кг", "+230 г"),
                GrowthEntry("12 сент.", "5,85 кг", null),
            ),
            model(events).weight!!.history,
        )
        assertEquals("±0", model(events).height!!.history.first().change)
    }

    @Test
    fun `height gain is per month against a measurement at least four weeks earlier`() {
        val events = listOf(
            Height(1, 1, at(day(91), 11), 575),
            Height(2, 1, at(day(30), 11), 600),
            Height(3, 1, at(day(10), 11), 625),
        )
        val height = model(events).height!!
        assertEquals("62,5 см", height.value)
        // Замер 18 сентября ближе четырёх недель — прибавка от 19 июля: 5 см за 81 день
        assertEquals("+1,9 см в месяц с 19 июл.", height.note)
        assertEquals(listOf("56", "58", "60", "62", "64 см"), height.yTicks.map { it.label })
        assertNull(model(listOf(Diaper(1, 1, at(day(1), 8), DiaperKind.WET))).height)
    }

    @Test
    fun `sleep shows night and day averages and stacks them`() {
        // Ночь — с 19:00 до 7:00 (ADR-0036): ночью 10 и 8 ч, днём 3 и 4 ч — в среднем 9 и 3,5 ч
        val events = listOf(
            Sleep(1, 1, at(day(2), 0), end = at(day(2), 6)),
            Sleep(2, 1, at(day(2), 13), end = at(day(2), 16)),
            Sleep(3, 1, at(day(2), 20), end = at(day(1), 0)),
            Sleep(4, 1, at(day(1), 0), end = at(day(1), 5)),
            Sleep(5, 1, at(day(1), 12), end = at(day(1), 16)),
            Sleep(6, 1, at(day(1), 21), end = at(today, 0)),
        )
        val sleep = model(events).metric(Metric.SLEEP)
        assertEquals("12 ч 30 мин", sleep.value)
        assertEquals(
            listOf(Series.NIGHT_SLEEP to "ночью 9 ч", Series.DAY_SLEEP to "днём 3 ч 30 мин"),
            sleep.legend.map { it.series to it.text },
        )
        // Ночь внизу столбика, день сверху
        assertEquals(listOf(Series.NIGHT_SLEEP to 8f, Series.DAY_SLEEP to 4f), sleep.columns[5].parts)
        assertEquals("пт, 16 окт.: сон 13 ч (ночью 10 ч, днём 3 ч), 3 раза, самый долгий 6 ч", sleep.columns[4].caption)
    }

    @Test
    fun `weekly sleep caption tells night and day`() {
        val events = (1L..14L).flatMap { n ->
            listOf(
                Sleep(n * 3, 1, at(day(n), 0), end = at(day(n), 6)),
                Sleep(n * 3 + 1, 1, at(day(n), 13), end = at(day(n), 16)),
                Sleep(n * 3 + 2, 1, at(day(n), 21), end = at(day(n - 1), 0)),
            )
        }
        val sleep = model(events, AnalyticsPeriod.ALL).metric(Metric.SLEEP)
        assertTrue(sleep.columns.first().caption, sleep.columns.first().caption.endsWith("в среднем 12 ч в сутки (ночью 9 ч, днём 3 ч)"))
    }
}
