package com.kid.babylog.feature.help

import com.kid.babylog.core.data.DiaryRepository
import com.kid.babylog.core.model.BabyEvent
import com.kid.babylog.core.model.Bottle
import com.kid.babylog.core.model.Breast
import com.kid.babylog.core.model.Crying
import com.kid.babylog.core.model.Diaper
import com.kid.babylog.core.model.DiaperKind
import com.kid.babylog.core.model.Feeding
import com.kid.babylog.core.model.Height
import com.kid.babylog.core.model.Illness
import com.kid.babylog.core.model.IllnessName
import com.kid.babylog.core.model.Temperature
import com.kid.babylog.core.model.TemperatureMethod
import com.kid.babylog.core.model.Medicine
import com.kid.babylog.core.model.Sex
import com.kid.babylog.core.model.Note
import com.kid.babylog.core.model.Milk
import com.kid.babylog.core.model.Sleep
import com.kid.babylog.core.model.Weight
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

/**
 * Дневник 4-месячной девочки за [days] суток по [now] включительно: пример в гайдах справки (ADR-0043),
 * скриншоты «Аналитики» и тестовый дневник `/seed`. Поменяешь записи — поменяются эталоны скриншотов.
 * Режим как в жизни: ночь с 1–2 пробуждениями на кормление, 3–4 дневных сна, кормления после сна,
 * иногда бутылочка смеси на ночь, подгузники, витамин D по утрам, вес с рождения раз в две недели,
 * рост — раз в месяц, через вечер — плач с причинами (ADR-0030).
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
    // Вес с рождения (SAMPLE_AGE_DAYS дней назад) раз в две недели
    listOf(0 to 3250, 4 to 3120, 10 to 3300, 17 to 3560, 31 to 4080, 45 to 4620, 59 to 5020, 73 to 5380, 87 to 5690, 108 to 5850, 122 to 6080, 144 to 6350)
        .forEach { (age, grams) ->
            val start = today.minusDays(SAMPLE_AGE_DAYS - age).atStartOfDay(zone).plusHours(10).toInstant().toEpochMilli()
            events += Weight(id++, childId, start, grams)
        }
    // Рост у врача раз в месяц, с точностью до 0,5 см
    listOf(0 to 500, 31 to 540, 59 to 575, 87 to 600, 122 to 625, 144 to 645).forEach { (age, millimeters) ->
        val start = today.minusDays(SAMPLE_AGE_DAYS - age).atStartOfDay(zone).plusHours(11).toInstant().toEpochMilli()
        events += Height(id++, childId, start, millimeters)
    }
    val nurofen = today.minusDays(4).atStartOfDay(zone).plusHours(22).toInstant().toEpochMilli()
    events += Medicine(id++, childId, nurofen, "Нурофен", note = "2,5 мл")
    events += Medicine(id++, childId, nurofen + 86_400_000L, "Нурофен", note = "2,5 мл")
    // ОРВИ (ADR-0040) вокруг нурофена: с вечера 5 дней назад по утро позавчера, замеры температуры (ADR-0041)
    fun hoursAfter(daysAgo: Int, hours: Double) =
        today.minusDays(daysAgo.toLong()).atStartOfDay(zone).toInstant().toEpochMilli() + (hours * 3_600_000).toLong()
    events += Illness(id++, childId, hoursAfter(5, 18.0), end = hoursAfter(2, 10.0), names = listOf(IllnessName("ОРВИ")))
    listOf(5 to 18.5 to 382, 4 to 21.8 to 386, 3 to 21.7 to 381, 2 to 8.0 to 372).forEach { (time, tenths) ->
        events += Temperature(id++, childId, hoursAfter(time.first, time.second), tenths, TemperatureMethod.ARMPIT)
    }
    // Плач по вечерам, между вечерним сном и ночью (ADR-0030). Свой генератор: остальные записи от него не сдвигаются
    val crying = Random(seed + 7)
    val reasons = listOf(listOf("колики"), listOf("усталость"), listOf("колики", "живот"), listOf("перевозбуждение"), emptyList())
    val helped = listOf(listOf("укачивание"), listOf("грудь"), listOf("тёплое на живот"), listOf("на руках", "укачивание"))
    for (dayIndex in days - 1 downTo 0) {
        if (crying.nextFloat() < 0.5f) continue
        val midnight = today.minusDays(dayIndex.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
        val start = midnight + (1175 + crying.nextInt(-10, 11)) * MINUTE
        val length = 12 + crying.nextInt(0, 30)
        events += Crying(
            id++, childId, start, end = start + length * MINUTE,
            reasons = reasons[crying.nextInt(reasons.size)], helped = helped[crying.nextInt(helped.size)],
        )
    }
    // Заметки (ADR-0031): питание мамы раз в 4 дня, зубки — последние дни, «новое» — трижды, одна без меток
    fun noteAt(daysAgo: Int, hour: Int, text: String, vararg tags: String) {
        val time = today.minusDays(daysAgo.toLong()).atStartOfDay(zone).plusHours(hour.toLong()).toInstant().toEpochMilli()
        events += Note(id++, childId, time, tags = tags.toList(), note = text)
    }
    (2 until days step 4).forEach { noteAt(it, 13, "Ела капусту на обед", "питание мамы") }
    (0..3).forEach { noteAt(it, 16, "Тянет всё в рот, тёрла дёсны", "зубки") }
    listOf(3, 17, 31).forEach { noteAt(it, 11, "Новое: держит голову лёжа на животе", "новое") }
    noteAt(1, 9, "Гуляли в парке два часа")
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

/** Девочка примера: её дневник — в гайдах справки (ADR-0043) и в `/seed`. */
const val SAMPLE_CHILD_NAME = "Соня"

/** Сколько дней девочке примера: вес в [sampleMonths] записан с рождения, [SAMPLE_AGE_DAYS] дней назад. */
const val SAMPLE_AGE_DAYS = 148L


/**
 * Кладёт в [repository] пример дневника для гайда (ADR-0043): девочку [SAMPLE_CHILD_NAME] и её записи по
 * [now]. Идущих таймеров нет: гайд сам предложит их запустить. Возвращает id ребёнка.
 */
suspend fun fillSampleDiary(repository: DiaryRepository, now: Long, zone: ZoneId): Long {
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val birth = today.minusDays(SAMPLE_AGE_DAYS).atStartOfDay(zone).toInstant().toEpochMilli()
    val childId = repository.addChild(SAMPLE_CHILD_NAME, birth, Sex.GIRL, photo = null)
    val events = sampleMonths(childId, today, now, zone).filterNot { it is Sleep && it.isActive || it is Feeding && it.isActive }
    repository.inTransaction { events.forEach { repository.addEvent(it) } }
    return childId
}
