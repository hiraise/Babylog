package com.kid.babylog.feature.analytics

import com.kid.babylog.core.model.BabyEvent
import com.kid.babylog.core.model.Medicine
import com.kid.babylog.core.model.Weight
import com.kid.babylog.core.ui.RU
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
 * Карточка «Вес» — всегда за всё время: взвешивают редко, за неделю точек нет. [points] — дни от
 * первого взвешивания и килограммы; график рисуется от двух взвешиваний.
 */
data class WeightModel(
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

/** Прибавку считаем к взвешиванию не ближе стольких дней: между соседними разница — в пределах ошибки весов. */
private const val GAIN_MIN_DAYS = 14L
private const val DAY_MILLIS = 86_400_000f

private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", RU)
private val MONTH = DateTimeFormatter.ofPattern("LLL", RU)
private val CLOCK = DateTimeFormatter.ofPattern("HH:mm")

fun weightModel(events: List<BabyEvent>, now: Long, zone: ZoneId): WeightModel? {
    val weights = events.filterIsInstance<Weight>().sortedWith(compareBy<Weight> { it.start }.thenBy { it.id })
    val last = weights.lastOrNull() ?: return null
    val first = weights.first()
    fun dateOf(t: Long): LocalDate = Instant.ofEpochMilli(t).atZone(zone).toLocalDate()
    val today = dateOf(now)

    val base = weights.lastOrNull { it.start <= last.start - GAIN_MIN_DAYS * DAY_MILLIS.toLong() }
    val note = base?.let {
        val days = (last.start - it.start) / DAY_MILLIS
        val perWeek = ((last.grams - it.grams) * 7 / days / 5).roundToInt() * 5
        val sign = if (perWeek >= 0) "+" else "−"
        "$sign${abs(perWeek)} г в неделю с ${dateOf(it.start).format(DAY_MONTH)}"
    }

    val points = weights.map { (it.start - first.start) / DAY_MILLIS to it.grams / 1000f }
    val yMin = floor(weights.minOf { it.grams } / 1000f)
    val yMax = ceil(weights.maxOf { it.grams } / 1000f).let { if (it <= yMin) yMin + 1 else it }
    val step = if (yMax - yMin <= 4) 1 else 2
    val yValues = generateSequence(yMin) { it + step }.takeWhile { it <= yMax }.toList()
    val yTicks = yValues.mapIndexed { i, kg -> Tick(kg, kg.roundToInt().toString() + if (i == yValues.lastIndex) " кг" else "") }

    // Деления по оси времени — начала месяцев; на коротком отрезке — первое и последнее взвешивание
    val firstDate = dateOf(first.start)
    val lastDate = dateOf(last.start)
    val monthStarts = generateSequence(firstDate.withDayOfMonth(1).plusMonths(1)) { it.plusMonths(1) }
        .takeWhile { it <= lastDate }
        .map { Tick(ChronoUnit.DAYS.between(firstDate, it).toFloat(), it.format(MONTH)) }
        .toList()
    val xTicks = monthStarts.takeIf { it.size >= 2 }
        ?: listOf(Tick(0f, firstDate.format(DAY_MONTH)), Tick(points.last().first, lastDate.format(DAY_MONTH)))

    return WeightModel(
        value = formatWeight(last.grams),
        date = dayName(lastDate, today),
        note = note,
        points = points,
        xMax = maxOf(points.last().first, 1f),
        yMin = yMin,
        yMax = yMax,
        yTicks = yTicks,
        xTicks = xTicks,
        description = "Вес с ${firstDate.format(DAY_MONTH)}: ${weights.size} " +
            "${plural(weights.size, "взвешивание", "взвешивания", "взвешиваний")}, " +
            "от ${formatWeight(weights.minOf { it.grams })} до ${formatWeight(weights.maxOf { it.grams })}",
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
