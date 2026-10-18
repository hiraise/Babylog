package com.kid.babylog.testing

import com.kid.babylog.core.model.BabyEvent
import com.kid.babylog.core.model.Bottle
import com.kid.babylog.core.model.Breast
import com.kid.babylog.core.model.Diaper
import com.kid.babylog.core.model.DiaperKind
import com.kid.babylog.core.model.Feeding
import com.kid.babylog.core.model.Height
import com.kid.babylog.core.model.Medicine
import com.kid.babylog.core.model.Milk
import com.kid.babylog.core.model.Sleep
import com.kid.babylog.core.model.Weight
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

/**
 * Дневник 4-месячной девочки за [days] суток по [now] включительно — для скриншотов «Аналитики».
 * Режим как в жизни: ночь с 1–2 пробуждениями на кормление, 3–4 дневных сна, кормления после сна,
 * иногда бутылочка смеси на ночь, подгузники, витамин D по утрам, вес с рождения раз в две недели,
 * рост — раз в месяц.
 * Сон и кормление не пересекаются (ADR-0020). Записи детерминированы [seed]: эталоны не плывут.
 */
fun sampleMonths(childId: Long, today: LocalDate, now: Long, zone: ZoneId, days: Int = 60, seed: Int = 20261018): List<BabyEvent> {
    val random = Random(seed)
    fun jitter(minutes: Int) = random.nextInt(-minutes, minutes + 1)
    val events = mutableListOf<BabyEvent>()
    var id = 1L
    fun feed(start: Long, minutes: Int) {
        events += Feeding(id++, childId, start, end = start + minutes * MINUTE, breast = if (id % 2 == 0L) Breast.LEFT else Breast.RIGHT)
    }
    var previousBed: Long? = null
    for (dayIndex in days - 1 downTo 0) {
        val date = today.minusDays(dayIndex.toLong())
        val midnight = date.atStartOfDay(zone).toInstant().toEpochMilli()
        fun at(minutes: Int) = midnight + minutes * MINUTE
        val wake = at(405 + jitter(20))
        previousBed?.let { bed ->
            val firstWake = at(90 + jitter(30))
            val firstFeed = 14 + jitter(5)
            events += Sleep(id++, childId, bed, end = firstWake)
            feed(firstWake + 3 * MINUTE, firstFeed)
            if (random.nextBoolean()) events += Diaper(id++, childId, firstWake + (5 + firstFeed) * MINUTE, DiaperKind.WET)
            val resumed = firstWake + (8 + firstFeed) * MINUTE
            if (random.nextFloat() < 0.7f) {
                val secondWake = at(280 + jitter(30))
                val secondFeed = 13 + jitter(4)
                events += Sleep(id++, childId, resumed, end = secondWake)
                feed(secondWake + 3 * MINUTE, secondFeed)
                events += Sleep(id++, childId, secondWake + (8 + secondFeed) * MINUTE, end = wake)
            } else {
                events += Sleep(id++, childId, resumed, end = wake)
            }
        }
        feed(wake + 10 * MINUTE, 18 + jitter(5))
        events += Diaper(id++, childId, wake + 32 * MINUTE, DiaperKind.WET)
        events += Medicine(id++, childId, wake + 35 * MINUTE, "Витамин D", note = "1 капля")
        val naps = listOf(515 to 75, 725 to 85, 930 to 60)
        naps.forEachIndexed { i, (start, length) ->
            val napStart = at(start + jitter(15))
            val napEnd = napStart + (length + jitter(15)) * MINUTE
            events += Sleep(id++, childId, napStart, end = napEnd)
            feed(napEnd + 10 * MINUTE, 17 + jitter(5))
            val kind = if (i == 1 && random.nextFloat() < 0.4f) DiaperKind.MIXED else DiaperKind.WET
            events += Diaper(id++, childId, napEnd + 30 * MINUTE, kind)
        }
        if (random.nextFloat() < 0.7f) {
            val napStart = at(1095 + jitter(15))
            val napEnd = napStart + (35 + jitter(10)) * MINUTE
            events += Sleep(id++, childId, napStart, end = napEnd)
            feed(napEnd + 12 * MINUTE, 16 + jitter(4))
        } else {
            feed(at(1120 + jitter(10)), 18)
        }
        events += Diaper(id++, childId, at(1170 + jitter(10)), if (random.nextFloat() < 0.6f) DiaperKind.DIRTY else DiaperKind.WET)
        val bed = at(1290 + jitter(20))
        if (random.nextFloat() < 0.4f) {
            events += Bottle(id++, childId, bed - 20 * MINUTE, volumeMl = 120 + 10 * random.nextInt(4), milk = Milk.FORMULA)
        } else {
            feed(bed - 25 * MINUTE, 17)
        }
        events += Diaper(id++, childId, bed - 32 * MINUTE, DiaperKind.WET)
        previousBed = bed
    }
    // Вес с рождения (148 дней назад) раз в две недели
    listOf(0 to 3250, 4 to 3120, 10 to 3300, 17 to 3560, 31 to 4080, 45 to 4620, 59 to 5020, 73 to 5380, 87 to 5690, 108 to 5850, 122 to 6080, 144 to 6350)
        .forEach { (age, grams) ->
            val start = today.minusDays(148L - age).atStartOfDay(zone).plusHours(10).toInstant().toEpochMilli()
            events += Weight(id++, childId, start, grams)
        }
    // Рост у врача раз в месяц, с точностью до 0,5 см
    listOf(0 to 500, 31 to 540, 59 to 575, 87 to 600, 122 to 625, 144 to 645).forEach { (age, millimeters) ->
        val start = today.minusDays(148L - age).atStartOfDay(zone).plusHours(11).toInstant().toEpochMilli()
        events += Height(id++, childId, start, millimeters)
    }
    val nurofen = today.minusDays(4).atStartOfDay(zone).plusHours(22).toInstant().toEpochMilli()
    events += Medicine(id++, childId, nurofen, "Нурофен", note = "2,5 мл")
    events += Medicine(id++, childId, nurofen + 86_400_000L, "Нурофен", note = "2,5 мл")
    // Будущее отрезаем; сон, который ещё не закончился к now, — идущий
    return events.filter { it.start < now }.map { event ->
        when {
            event is Sleep && (event.end ?: now) > now -> event.copy(end = null)
            event is Feeding && (event.end ?: now) > now -> event.copy(end = null)
            else -> event
        }
    }
}

private const val MINUTE = 60_000L
