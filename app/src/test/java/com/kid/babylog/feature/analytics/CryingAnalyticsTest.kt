package com.kid.babylog.feature.analytics

import com.kid.babylog.core.model.BabyEvent
import com.kid.babylog.core.model.Breast
import com.kid.babylog.core.model.Crying
import com.kid.babylog.core.model.Feeding
import com.kid.babylog.core.model.Sex
import com.kid.babylog.core.model.Sleep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class CryingAnalyticsTest {

    private val zone = ZoneId.of("Europe/Moscow")
    private val today = LocalDate.of(2026, 10, 18) // воскресенье
    private val yesterday = today.minusDays(1)
    private val minute = 60_000L

    private fun at(date: LocalDate, h: Int, m: Int = 0): Long =
        LocalDateTime.of(date, LocalTime.of(h, m)).atZone(zone).toInstant().toEpochMilli()

    private val now = at(today, 11, 20)

    /** Вчера: окна бодрствования 2 ч, 1 ч, 1 ч и 3 ч — обычно 1 ч 30 мин; кормления после пробуждений. */
    private val day: List<BabyEvent> = listOf(
        Sleep(1, 1, at(yesterday, 5), end = at(yesterday, 7)),
        Sleep(2, 1, at(yesterday, 9), end = at(yesterday, 10)),
        Sleep(3, 1, at(yesterday, 11), end = at(yesterday, 12, 30)),
        Sleep(4, 1, at(yesterday, 13, 30), end = at(yesterday, 15)),
        Sleep(5, 1, at(yesterday, 18), end = at(yesterday, 19)),
        Feeding(10, 1, at(yesterday, 7, 10), end = at(yesterday, 7, 30), breast = Breast.LEFT),
        Feeding(11, 1, at(yesterday, 10), end = at(yesterday, 10, 15), breast = Breast.RIGHT),
        Feeding(12, 1, at(yesterday, 12, 30), end = at(yesterday, 12, 50), breast = Breast.LEFT),
        Feeding(13, 1, at(yesterday, 15, 30), end = at(yesterday, 15, 50), breast = Breast.RIGHT),
    )

    private val cryings = listOf(
        // 40 мин после сна, 25 мин после кормления
        Crying(20, 1, at(yesterday, 10, 40), end = at(yesterday, 10, 50), reasons = listOf("колики"), helped = listOf("грудь")),
        // 2 ч после сна — дольше обычного, — 1 ч 10 мин после кормления
        Crying(21, 1, at(yesterday, 17), end = at(yesterday, 17, 30), reasons = listOf("колики", "усталость"), helped = listOf("укачивание")),
    )

    private fun model(events: List<BabyEvent>) = analyticsModel(events, AnalyticsPeriod.WEEK, now, zone, Sex.GIRL)

    @Test
    fun `no crying records — no crying tile`() {
        val model = model(day)
        assertTrue(model.metrics.none { it.metric == Metric.CRYING })
        assertNull(model.crying)
    }

    @Test
    fun `crying minutes are averaged over diary days and told per day`() {
        // Позавчера — только плач: сутки без записей режима в средние не входят
        val onlyCrying = Crying(22, 1, at(today.minusDays(2), 20), end = at(today.minusDays(2), 21))
        val crying = model(day + cryings + onlyCrying).metrics.single { it.metric == Metric.CRYING }
        assertEquals("40 мин", crying.value)
        assertEquals("2,0 раза в сутки", crying.note)
        assertEquals("сб, 17 окт.: плач 40 мин, 2 раза", crying.columns[5].caption)
        assertEquals("пт, 16 окт.: записей нет", crying.columns[4].caption)
        assertEquals("1 ч", crying.maxLabel)
        assertEquals("30 мин", crying.midLabel)
    }

    @Test
    fun `a day without crying among crying days is zero minutes, not less than a minute`() {
        val crying = model(day + day.map { shifted(it) } + cryings).metrics.single { it.metric == Metric.CRYING }
        assertEquals("20 мин", crying.value)
        assertEquals("пт, 16 окт.: плача нет", crying.columns[4].caption)
        assertEquals("0 мин", formatValue(Metric.CRYING, 0f))
    }

    @Test
    fun `before crying — awake time and time after feeding against the usual`() {
        val crying = checkNotNull(model(day + cryings).crying)
        assertEquals(2, crying.episodes)
        // Перед плачем не спала 40 мин и 2 ч — медиана 1 ч 20 мин; обычное окно — 1 ч 30 мин
        assertEquals(80 * minute, crying.awakeBefore)
        assertEquals(90 * minute, crying.usualAwake)
        assertEquals(2, crying.awakeEpisodes)
        assertEquals(1, crying.longerThanUsual)
        assertEquals("В 1 из 2 раз плач начался после бодрствования дольше обычного", longerThanUsualText(1, 2))
        assertEquals("Ни разу плач не начался после бодрствования дольше обычного", longerThanUsualText(0, 2))
        assertEquals("Каждый раз плач начинался после бодрствования дольше обычного", longerThanUsualText(2, 2))
        // После кормления 25 и 70 мин; между началами кормлений 2 ч 50 мин, 2 ч 30 мин и 3 ч
        assertEquals(47 * minute + 30_000, crying.sinceFeeding)
        assertEquals(170 * minute, crying.feedingInterval)
        assertEquals(listOf("колики" to 2, "усталость" to 1), crying.reasons)
        assertEquals(listOf("грудь" to 1, "укачивание" to 1), crying.helped)
    }

    @Test
    fun `crying that is still going is not in the causes yet`() {
        assertNull(model(day + Crying(30, 1, at(today, 11))).crying)
    }

    @Test
    fun `crying peak is the busiest hours trimmed to where crying started`() {
        assertEquals("чаще с 18 до 21 ч", cryingPeak(listOf(18 * 60 + 30, 19 * 60, 20 * 60 + 10, 10 * 60)))
        assertEquals("чаще с 23 до 1 ч", cryingPeak(listOf(23 * 60 + 10, 23 * 60 + 50, 20)))
        // Плач в разное время или меньше трёх раз — пика нет
        assertNull(cryingPeak(listOf(2 * 60, 8 * 60, 14 * 60, 20 * 60)))
        assertNull(cryingPeak(listOf(19 * 60, 19 * 60 + 30)))
    }

    /** Та же запись сутками раньше — день режима без плача. */
    private fun shifted(event: BabyEvent): BabyEvent {
        val day = 24 * 60 * minute
        return when (event) {
            is Sleep -> event.copy(id = event.id + 100, start = event.start - day, end = event.end?.minus(day))
            is Feeding -> event.copy(id = event.id + 100, start = event.start - day, end = event.end?.minus(day))
            else -> error("в дне режима только сон и кормления")
        }
    }
}
