package com.kid.babylog.feature.analytics

import com.kid.babylog.core.model.DiaperKind
import com.kid.babylog.core.model.Sex
import com.kid.babylog.core.ui.RU
import com.kid.babylog.core.ui.diaperLabel
import com.kid.babylog.core.ui.formatDuration
import com.kid.babylog.core.ui.plural
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Период «Аналитики» (ADR-0028): последние 7 или 30 суток — по дням, всё время — по неделям. */
enum class AnalyticsPeriod(val title: String, val days: Int?) {
    WEEK("7 дней", 7),
    MONTH("30 дней", 30),
    ALL("Всё время", null),
}

/** Величина, которой посвящена карточка-график. */
enum class Metric(val title: String) {
    FEEDINGS("Кормления"),
    BOTTLES("Бутылочка"),
    SLEEP("Сон"),
    DIAPERS("Подгузники"),
    /** Плач (ADR-0030): минуты в сутки; карточка есть, только если плач записывали. */
    CRYING("Плач"),
}

/** Ряд графика; цвет ряда UI берёт из темы (`ChartColors`), а не из модели. Сон — ночной и дневной (ADR-0036). */
enum class Series { FEEDING, NIGHT_SLEEP, DAY_SLEEP, DIAPER_WET, DIAPER_MIXED, DIAPER_DIRTY, CRYING }

/**
 * Столбик графика: части снизу вверх. [partial] — сутки или неделя ещё идут; [date] — день,
 * который откроет «В историю» (у недельного столбика null); [caption] — итоги столбика словами,
 * их показывает выбранный столбик и читает TalkBack.
 */
data class ChartColumn(
    val parts: List<Pair<Series, Float>>,
    val label: String,
    val partial: Boolean,
    val date: LocalDate?,
    val caption: String,
)

/**
 * Пункт легенды: цвет ряда, вид подгузника для иконки (у сна иконки нет), название ряда для TalkBack
 * и среднее за сутки — у сна вместе с «ночью» или «днём».
 */
data class LegendItem(val series: Series, val kind: DiaperKind?, val label: String, val text: String)

/** Карточка-график: главное число, сравнение, пояснение, легенда и столбики. */
data class MetricModel(
    val metric: Metric,
    val value: String,
    val unit: String,
    val delta: String?,
    val note: String?,
    val legend: List<LegendItem>,
    val columns: List<ChartColumn>,
    val maxValue: Float,
    val maxLabel: String,
    val midLabel: String,
)

private const val HOUR = 3_600_000f
private const val MINUTE = 60_000f

/** Сравнение с прошлым периодом — только если в нём не меньше стольких полных суток с записями. */
private const val MIN_DAYS_TO_COMPARE = 3

private val DAY_NAME = DateTimeFormatter.ofPattern("EE, d MMM", RU)
private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", RU)
private val MONTH = DateTimeFormatter.ofPattern("LLL", RU)
private val DIAPER_SERIES = listOf(
    DiaperKind.WET to Series.DIAPER_WET,
    DiaperKind.MIXED to Series.DIAPER_MIXED,
    DiaperKind.DIRTY to Series.DIAPER_DIRTY,
)

/** Будущий столбик: сутки (одни [days] или пусто, если дневник не вели) или неделя (её полные сутки с записями). */
private class Bucket(val days: List<DayStats>, val label: String, val partial: Boolean, val date: LocalDate?, val name: String)

/** По столбику на сутки периода [days]; сутки без записей — пустой столбик. */
private fun dayBuckets(days: List<DayStats>, period: AnalyticsPeriod): List<Bucket> {
    var monthShown = false
    return days.map { day ->
        val label = when (period) {
            AnalyticsPeriod.WEEK -> day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, RU)
            // Из 30 столбиков подписан каждый понедельник; месяц — у первого и у начала месяца
            else -> if (day.date.dayOfWeek.value != 1) {
                ""
            } else if (!monthShown || day.date.dayOfMonth <= 7) {
                monthShown = true
                day.date.format(DAY_MONTH)
            } else {
                day.date.dayOfMonth.toString()
            }
        }
        Bucket(
            days = listOf(day).filter { it.tracked },
            label = label,
            partial = !day.complete,
            date = day.date,
            name = if (day.complete) day.date.format(DAY_NAME) else "сегодня",
        )
    }
}

/** Недели с понедельника: среднее за полные сутки с записями; текущая неделя — [Bucket.partial]. */
private fun weekBuckets(days: List<DayStats>): List<Bucket> {
    var previousMonth = -1
    return days.chunked(7).map { week ->
        val start = week.first().date
        val label = if (start.monthValue != previousMonth) start.format(MONTH) else ""
        previousMonth = start.monthValue
        Bucket(
            days = week.filter { it.countable },
            label = label,
            partial = week.size < 7 || !week.last().complete,
            date = null,
            name = "неделя с ${start.format(DAY_MONTH)}",
        )
    }
}

/** Средние за сутки по [days]; пусто — нечего показывать. */
private fun parts(metric: Metric, days: List<DayStats>): List<Pair<Series, Float>> {
    if (days.isEmpty()) return emptyList()
    fun avg(value: (DayStats) -> Number) = days.map { value(it).toFloat() }.average().toFloat()
    return when (metric) {
        Metric.FEEDINGS -> listOf(Series.FEEDING to avg { it.feedings })
        Metric.BOTTLES -> listOf(Series.FEEDING to avg { it.bottleMl })
        // Ночь внизу столбика, день сверху: ночной сон — основа суток
        Metric.SLEEP -> listOf(Series.NIGHT_SLEEP to avg { it.nightSleepMillis } / HOUR, Series.DAY_SLEEP to avg { it.daySleepMillis } / HOUR)
        Metric.DIAPERS -> DIAPER_SERIES.map { (kind, series) -> series to avg { it.diapers[kind] ?: 0 } }
        Metric.CRYING -> listOf(Series.CRYING to avg { it.cryingMillis } / MINUTE)
    }
}

private fun total(metric: Metric, days: List<DayStats>): Float? =
    parts(metric, days).takeIf { it.isNotEmpty() }?.sumOf { it.second.toDouble() }?.toFloat()

/** «8,3», «245 мл», «13 ч 5 мин» */
internal fun formatValue(metric: Metric, value: Float): String = when (metric) {
    Metric.FEEDINGS, Metric.DIAPERS -> "%.1f".format(RU, value)
    Metric.BOTTLES -> "${(value / 5).roundToInt() * 5} мл"
    Metric.SLEEP -> formatDuration((value * HOUR).roundToLong())
    // Ноль — «0 мин», а не «меньше минуты»: плача в эти сутки не было
    Metric.CRYING -> if (value <= 0f) "0 мин" else formatDuration((value * MINUTE).roundToLong())
}

/** «+0,4», «−15 мин», «+20 мл»; меньше шага — «без изменений». */
private fun formatChange(metric: Metric, change: Float): String {
    val sign = if (change > 0) "+" else "−"
    return when (metric) {
        Metric.FEEDINGS, Metric.DIAPERS ->
            if (abs(change) < 0.05f) "без изменений" else sign + "%.1f".format(RU, abs(change))
        Metric.BOTTLES ->
            if (abs(change) < 5f) "без изменений" else "$sign${(abs(change) / 5).roundToInt() * 5} мл"
        Metric.SLEEP ->
            if (abs(change) * HOUR < 60_000) "без изменений" else sign + formatDuration((abs(change) * HOUR).roundToLong())
        Metric.CRYING ->
            if (abs(change) < 1f) "без изменений" else sign + formatDuration((abs(change) * MINUTE).roundToLong())
    }
}

/** Верх шкалы — круглое число не меньше максимума: середина шкалы тоже круглая. */
private fun scaleTop(metric: Metric, max: Float): Int = when (metric) {
    Metric.FEEDINGS, Metric.DIAPERS -> maxOf(2, ceil(max / 2).toInt() * 2)
    Metric.BOTTLES -> maxOf(100, ceil(max / 100).toInt() * 100)
    Metric.SLEEP -> maxOf(4, ceil(max / 4).toInt() * 4)
    // Плач — в минутах: верх шкалы кратен получасу
    Metric.CRYING -> maxOf(30, ceil(max / 30).toInt() * 30)
}

private fun axisLabel(metric: Metric, value: Int): String = when (metric) {
    Metric.SLEEP -> "$value ч"
    Metric.CRYING -> formatDuration(value * MINUTE.toLong())
    Metric.FEEDINGS, Metric.BOTTLES, Metric.DIAPERS -> value.toString()
}

/**
 * Карточка [metric] за период: [days] — сутки периода, [previous] — столько же суток перед ним
 * (для «всё время» пусто). Средние — только по полным суткам с записями ([DayStats.countable]).
 */
internal fun metricModel(metric: Metric, period: AnalyticsPeriod, days: List<DayStats>, previous: List<DayStats>, sex: Sex?): MetricModel {
    val byWeeks = period == AnalyticsPeriod.ALL
    val buckets = if (byWeeks) weekBuckets(days) else dayBuckets(days, period)
    val columns = buckets.map { bucket ->
        ChartColumn(parts(metric, bucket.days), bucket.label, bucket.partial, bucket.date, caption(metric, bucket, byWeeks, sex))
    }
    val top = scaleTop(metric, columns.maxOfOrNull { column -> column.parts.sumOf { it.second.toDouble() }.toFloat() } ?: 0f)
    val countable = days.filter { it.countable }

    val value: Float?
    val delta: String?
    var note: String?
    val legendDays: List<DayStats>
    if (byWeeks) {
        // Главное число — последняя полная неделя, для сравнения — первая неделя дневника
        val weeks = buckets.filter { it.days.isNotEmpty() }
        val latest = weeks.lastOrNull { !it.partial } ?: weeks.lastOrNull()
        legendDays = latest?.days.orEmpty()
        value = total(metric, legendDays)
        delta = "по неделям"
        note = weeks.firstOrNull()?.takeIf { weeks.size > 1 && it !== latest }
            ?.let { first -> total(metric, first.days)?.let { "в первую неделю дневника — ${formatValue(metric, it)}" } }
    } else {
        legendDays = countable
        value = total(metric, countable)
        val before = previous.filter { it.countable }
        val previousValue = total(metric, before)
        delta = if (value != null && previousValue != null && before.size >= MIN_DAYS_TO_COMPARE) {
            formatChange(metric, value - previousValue) + if (period == AnalyticsPeriod.WEEK) " к прошлой неделе" else " к прошлым 30 дням"
        } else {
            null
        }
        note = null
    }
    note = listOfNotNull(periodNote(metric, if (byWeeks) legendDays else countable, days), note).joinToString(" · ").ifEmpty { null }

    return MetricModel(
        metric = metric,
        value = value?.let { formatValue(metric, it) } ?: "—",
        unit = if (value == null) "нет полных суток с записями" else "в сутки",
        delta = delta,
        note = note,
        legend = when {
            legendDays.isEmpty() -> emptyList()
            metric == Metric.DIAPERS -> DIAPER_SERIES.map { (kind, series) ->
                LegendItem(series, kind, diaperLabel(kind, sex), "%.1f".format(RU, legendDays.map { (it.diapers[kind] ?: 0).toFloat() }.average()))
            }
            // Сколько спит ночью и сколько днём — в среднем за те же сутки, что и главное число
            metric == Metric.SLEEP -> listOf(
                LegendItem(Series.NIGHT_SLEEP, null, "сон", "ночью " + formatDuration(legendDays.map { it.nightSleepMillis }.average().roundToLong())),
                LegendItem(Series.DAY_SLEEP, null, "сон", "днём " + formatDuration(legendDays.map { it.daySleepMillis }.average().roundToLong())),
            )
            else -> emptyList()
        },
        columns = columns,
        maxValue = top.toFloat(),
        maxLabel = axisLabel(metric, top),
        midLabel = axisLabel(metric, top / 2),
    )
}

/** Пояснение под главным числом: по [countable] — полным суткам с записями, [all] — всем суткам периода. */
private fun periodNote(metric: Metric, countable: List<DayStats>, all: List<DayStats>): String? = when (metric) {
    Metric.FEEDINGS -> listOfNotNull(typicalInterval(countable.flatMap { it.feedingStarts }), breastTime(countable)).joinToString(" · ").ifEmpty { null }
    Metric.BOTTLES -> countable.takeIf { it.isNotEmpty() }
        ?.let { "%.1f".format(RU, it.map { day -> day.bottles }.average()) + " бутылочки в сутки" }
    // Как часто спит — как у кормлений: сколько раз в сутки и «обычно раз в …» по началам снов
    Metric.SLEEP -> listOfNotNull(
        countable.takeIf { it.isNotEmpty() }?.let { "%.1f".format(RU, it.map { day -> day.sleeps }.average()) + " раза в сутки" },
        typicalInterval(countable.flatMap { it.sleepStarts }),
        all.maxOfOrNull { it.longestSleepMillis }?.takeIf { it > 0 }?.let { "самый долгий сон — ${formatDuration(it)}" },
    ).joinToString(" · ").ifEmpty { null }
    Metric.DIAPERS -> null
    Metric.CRYING -> listOfNotNull(
        countable.takeIf { it.isNotEmpty() }?.let { "%.1f".format(RU, it.map { day -> day.cryings }.average()) + " раза в сутки" },
        cryingPeak(countable.flatMap { it.cryingStartMinutes }),
    ).joinToString(" · ").ifEmpty { null }
}

/**
 * «обычно раз в 2 ч 50 мин» — медиана промежутков между началами кормлений или снов ([starts]):
 * пропущенная запись её не сдвинет.
 */
private fun typicalInterval(starts: List<Long>): String? {
    val gaps = starts.sorted().zipWithNext { a, b -> b - a }.sorted()
    if (gaps.size < 2) return null
    val median = if (gaps.size % 2 == 1) gaps[gaps.size / 2] else (gaps[gaps.size / 2 - 1] + gaps[gaps.size / 2]) / 2
    return "обычно раз в ${formatDuration(median)}"
}

/**
 * «у груди 2 ч 16 мин в сутки» — по суткам, где у всех кормлений грудью есть длительность. Кормления
 * до ADR-0015 записаны без неё: такие сутки в среднее не входят, а не считаются нулём, и это видно —
 * «по 5 дням из 7». Нет ни одних таких суток — строки нет.
 */
private fun breastTime(days: List<DayStats>): String? {
    val withBreast = days.filter { it.breastFeedings > 0 }
    val known = withBreast.mapNotNull { it.breastMillis }
    if (known.isEmpty()) return null
    val text = "у груди ${formatDuration(known.average().roundToLong())} в сутки"
    return if (known.size == withBreast.size) {
        text
    } else {
        "$text по ${known.size} ${plural(known.size, "дню", "дням", "дням")} из ${withBreast.size}"
    }
}

/** Итоги столбика словами: для выбранного столбика и для TalkBack. */
private fun caption(metric: Metric, bucket: Bucket, byWeeks: Boolean, sex: Sex?): String {
    val suffix = when {
        !bucket.partial -> ""
        byWeeks -> " (неделя идёт)"
        else -> " (день идёт)"
    }
    if (bucket.days.isEmpty()) return "${bucket.name}: записей нет$suffix"
    if (byWeeks) {
        val average = total(metric, bucket.days) ?: 0f
        val split = if (metric == Metric.SLEEP) {
            sleepSplit(bucket.days.map { it.nightSleepMillis }.average().roundToLong(), bucket.days.map { it.daySleepMillis }.average().roundToLong())
        } else {
            ""
        }
        return "${bucket.name}: в среднем ${formatValue(metric, average)} в сутки$split$suffix"
    }
    val day = bucket.days.single()
    val text = when (metric) {
        Metric.FEEDINGS -> buildString {
            append("${day.feedings} ${plural(day.feedings, "кормление", "кормления", "кормлений")}")
            if (day.bottles > 0) append(", из них бутылочкой ${day.bottles}")
            day.breastMillis?.takeIf { it > 0 }?.let { append(", у груди ${formatDuration(it)}") }
        }
        Metric.BOTTLES -> "${day.bottles} ${plural(day.bottles, "бутылочка", "бутылочки", "бутылочек")}, ${day.bottleMl} мл"
        Metric.SLEEP -> "сон ${formatDuration(day.sleepMillis)}${day.sleepSplit()}" +
            (day.sleeps.takeIf { it > 0 }?.let { ", $it ${plural(it, "раз", "раза", "раз")}" } ?: "") +
            (day.longestSleepMillis.takeIf { it > 0 }?.let { ", самый долгий ${formatDuration(it)}" } ?: "")
        // Плач через полночь: минуты есть в обоих сутках, а раз — только в тех, где он начался
        Metric.CRYING -> when {
            day.cryings == 0 && day.cryingMillis == 0L -> "плача нет"
            day.cryings == 0 -> "плач ${formatDuration(day.cryingMillis)}"
            else -> "плач ${formatDuration(day.cryingMillis)}, ${day.cryings} ${plural(day.cryings, "раз", "раза", "раз")}"
        }
        Metric.DIAPERS -> "${day.diaperCount} ${plural(day.diaperCount, "подгузник", "подгузника", "подгузников")}" +
            DIAPER_SERIES.mapNotNull { (kind, _) -> day.diapers[kind]?.let { "${diaperLabel(kind, sex)} $it" } }
                .joinToString(", ", prefix = " (", postfix = ")")
                .takeIf { day.diaperCount > 0 }.orEmpty()
    }
    return "${bucket.name}: $text$suffix"
}

/** « (ночью 9 ч 40 мин, днём 2 ч 52 мин)» — из чего сложился сон (ADR-0036); сна нет — пусто. */
internal fun sleepSplit(nightMillis: Long, dayMillis: Long): String =
    listOfNotNull(
        nightMillis.takeIf { it > 0 }?.let { "ночью ${formatDuration(it)}" },
        dayMillis.takeIf { it > 0 }?.let { "днём ${formatDuration(it)}" },
    ).joinToString(", ", prefix = " (", postfix = ")").takeIf { nightMillis > 0 || dayMillis > 0 }.orEmpty()

internal fun DayStats.sleepSplit(): String = sleepSplit(nightSleepMillis, daySleepMillis)
