package com.kid.babylog.feature.analytics

import com.kid.babylog.core.model.BabyEvent
import com.kid.babylog.core.model.Height
import com.kid.babylog.core.model.Medicine
import com.kid.babylog.core.model.Weight
import com.kid.babylog.core.ui.RU
import com.kid.babylog.core.ui.formatHeight
import com.kid.babylog.core.ui.formatWeight
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** Деление шкалы графика: значение и подпись. */
data class Tick(val value: Float, val label: String)

/**
 * Карточка замера — веса или роста — всегда за всё время: меряют редко, за неделю точек нет.
 * [points] — дни от первого замера и значение в единицах оси (кг, см); график — от двух замеров.
 */
data class GrowthModel(
    val value: String,
    val date: String,
    val note: String?,
    val points: List<Pair<Float, Float>>,
    val xMax: Float,
    val yMin: Float,
    val yMax: Float,
    val yTicks: List<Tick>,
    val xTicks: List<Tick>,
    val description: String,
)

/** Замер: время и значение в единицах записи (граммы, миллиметры). */
private class Measurement(val id: Long, val start: Long, val value: Int)

/**
 * Чем вес отличается от роста на карточке: единицы оси и подписей, и как считать прибавку —
 * к замеру не ближе [baseDays] дней (ближе разница — в пределах ошибки замера), за [rateDays] дней.
 */
private class GrowthKind(
    val title: String,
    val unit: String,
    val toAxis: (Int) -> Float,
    val format: (Int) -> String,
    val baseDays: Long,
    val rateDays: Float,
    val rateLabel: String,
    /** Прибавка за [rateDays] в единицах записи → «95 г», «2,5 см». */
    val formatRate: (Float) -> String,
    val measurements: (Int) -> String,
)

private val WEIGHT = GrowthKind(
    title = "Вес",
    unit = "кг",
    toAxis = { it / 1000f },
    format = ::formatWeight,
    baseDays = 14,
    rateDays = 7f,
    rateLabel = "в неделю",
    formatRate = { grams -> "${(grams / 5).roundToInt() * 5} г" },
    measurements = { n -> "$n ${plural(n, "взвешивание", "взвешивания", "взвешиваний")}" },
)

/** Рост меряют раз в месяц с точностью до 0,5 см (ADR-0027): прибавка — в месяц, база — не ближе 4 недель. */
private val HEIGHT = GrowthKind(
    title = "Рост",
    unit = "см",
    toAxis = { it / 10f },
    format = ::formatHeight,
    baseDays = 28,
    rateDays = 30.44f,
    rateLabel = "в месяц",
    formatRate = { millimeters -> "%.1f см".format(RU, millimeters / 10) },
    measurements = { n -> "$n ${plural(n, "замер", "замера", "замеров")}" },
)

private const val DAY_MILLIS = 86_400_000f

/** Деления оси значений: шаг — наименьший из этих, при котором делений не больше [MAX_TICKS]. */
private val STEPS = listOf(1, 2, 5, 10, 20, 50)
private const val MAX_TICKS = 5

private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", RU)
private val MONTH = DateTimeFormatter.ofPattern("LLL", RU)
private val CLOCK = DateTimeFormatter.ofPattern("HH:mm")

fun weightModel(events: List<BabyEvent>, now: Long, zone: ZoneId): GrowthModel? =
    growthModel(WEIGHT, events.filterIsInstance<Weight>().map { Measurement(it.id, it.start, it.grams) }, now, zone)

fun heightModel(events: List<BabyEvent>, now: Long, zone: ZoneId): GrowthModel? =
    growthModel(HEIGHT, events.filterIsInstance<Height>().map { Measurement(it.id, it.start, it.millimeters) }, now, zone)

private fun growthModel(kind: GrowthKind, all: List<Measurement>, now: Long, zone: ZoneId): GrowthModel? {
    val measurements = all.sortedWith(compareBy<Measurement> { it.start }.thenBy { it.id })
    val last = measurements.lastOrNull() ?: return null
    val first = measurements.first()
    fun dateOf(t: Long): LocalDate = Instant.ofEpochMilli(t).atZone(zone).toLocalDate()

    val base = measurements.lastOrNull { it.start <= last.start - kind.baseDays * DAY_MILLIS.toLong() }
    val note = base?.let {
        val rate = (last.value - it.value) * kind.rateDays / ((last.start - it.start) / DAY_MILLIS)
        val sign = if (rate >= 0) "+" else "−"
        "$sign${kind.formatRate(abs(rate))} ${kind.rateLabel} с ${dateOf(it.start).format(DAY_MONTH)}"
    }

    val points = measurements.map { (it.start - first.start) / DAY_MILLIS to kind.toAxis(it.value) }
    val low = measurements.minOf { kind.toAxis(it.value) }
    val high = measurements.maxOf { kind.toAxis(it.value) }
    val step = STEPS.firstOrNull { (ceil(high / it) - floor(low / it)) < MAX_TICKS } ?: STEPS.last()
    val yMin = floor(low / step) * step
    val yMax = (ceil(high / step) * step).let { if (it <= yMin) yMin + step else it }
    val yValues = generateSequence(yMin) { it + step }.takeWhile { it <= yMax + step / 2f }.toList()
    val yTicks = yValues.mapIndexed { i, value ->
        Tick(value, value.roundToInt().toString() + if (i == yValues.lastIndex) " ${kind.unit}" else "")
    }

    // Деления по оси времени — начала месяцев; на коротком отрезке — первый и последний замер
    val firstDate = dateOf(first.start)
    val lastDate = dateOf(last.start)
    val monthStarts = generateSequence(firstDate.withDayOfMonth(1).plusMonths(1)) { it.plusMonths(1) }
        .takeWhile { it <= lastDate }
        .map { Tick(ChronoUnit.DAYS.between(firstDate, it).toFloat(), it.format(MONTH)) }
        .toList()
    val xTicks = monthStarts.takeIf { it.size >= 2 }
        ?: listOf(Tick(0f, firstDate.format(DAY_MONTH)), Tick(points.last().first, lastDate.format(DAY_MONTH)))

    return GrowthModel(
        value = kind.format(last.value),
        date = dayName(lastDate, dateOf(now)),
        note = note,
        points = points,
        xMax = maxOf(points.last().first, 1f),
        yMin = yMin,
        yMax = yMax,
        yTicks = yTicks,
        xTicks = xTicks,
        description = "${kind.title} с ${firstDate.format(DAY_MONTH)}: ${kind.measurements(measurements.size)}, " +
            "от ${kind.format(measurements.minOf { it.value })} до ${kind.format(measurements.maxOf { it.value })}",
    )
}

/** Лекарство за период: название из последней записи, сколько приёмов и когда последний. */
data class MedicineLine(val name: String, val info: String)

/** Лекарства с [since] (null — за всё время); названия сравниваются без регистра, как в сводке дня. */
fun medicineLines(events: List<BabyEvent>, since: Long?, now: Long, zone: ZoneId): List<MedicineLine> {
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    return events.filterIsInstance<Medicine>()
        .filter { since == null || it.start >= since }
        .groupBy { it.name.trim().lowercase() }
        .values
        .map { doses -> doses.maxBy { it.start } to doses.size }
        .sortedByDescending { (last, _) -> last.start }
        .map { (last, count) ->
            val time = Instant.ofEpochMilli(last.start).atZone(zone)
            MedicineLine(
                name = last.name.trim(),
                info = "$count ${plural(count, "приём", "приёма", "приёмов")} · " +
                    "последний ${dayName(time.toLocalDate(), today)} в ${time.toLocalTime().format(CLOCK)}",
            )
        }
}

/** «сегодня», «вчера», «14 окт.» */
private fun dayName(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "сегодня"
    today.minusDays(1) -> "вчера"
    else -> date.format(DAY_MONTH)
}
