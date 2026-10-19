package com.kid.babylog.core.model

/** Бодрствование длиннее этого — пропущенная запись сна, а не бодрствование. */
const val MAX_AWAKE_MILLIS = 6 * 60 * 60_000L

/** Бодрствование настолько длиннее обычного — «дольше обычного»: на глаз это уже заметно. */
const val LONGER_THAN_USUAL = 1.15

/** «Обычно не спит» — по окнам бодрствования за столько суток: окна растут вместе с малышом. */
private const val USUAL_AWAKE_DAYS = 7L
private const val DAY_MILLIS = 86_400_000L

/**
 * Конец последнего законченного сна не позже [at]; null — сна до [at] не было или в [at] ребёнок
 * спал. Для «не спит 2 ч 15 мин» перед плачем (ADR-0030).
 */
fun List<BabyEvent>.lastSleepEndBefore(at: Long): Long? {
    val sleeps = filterIsInstance<Sleep>()
    if (sleeps.any { it.start <= at && (it.end ?: Long.MAX_VALUE) > at }) return null
    return sleeps.mapNotNull { it.end }.filter { it <= at }.maxOrNull()
}

/**
 * Промежутки бодрствования — от конца сна до начала следующего, — начавшиеся в `[from, to)` и не
 * длиннее [MAX_AWAKE_MILLIS]: длиннее — забытая запись сна, а не бодрствование.
 */
fun List<BabyEvent>.awakeWindows(from: Long, to: Long): List<Long> {
    val sleeps = filterIsInstance<Sleep>().mapNotNull { sleep -> sleep.end?.let { sleep.start to it } }.sortedBy { it.first }
    return sleeps.zipWithNext { (_, end), (nextStart, _) -> end to nextStart }
        .filter { (end, nextStart) -> end in from until to && nextStart > end && nextStart - end <= MAX_AWAKE_MILLIS }
        .map { (end, nextStart) -> nextStart - end }
}

/**
 * Конец последнего кормления не позже [at]: у груди — конец записи (при смене груди это конец всего
 * кормления), у бутылочки — её время. null — кормлений до [at] не было.
 */
fun List<BabyEvent>.lastFeedingEndBefore(at: Long): Long? =
    (filterIsInstance<Feeding>().mapNotNull { it.end } + filterIsInstance<Bottle>().map { it.start }).filter { it <= at }.maxOrNull()

/** Часы суток `[from, to)` (0–23, `to` может перейти через полночь: 22 → 1), где начались [count] случаев. */
data class PeakHours(val from: Int, val to: Int, val count: Int)

/**
 * Когда чаще: до [window] часов подряд, где началось больше всего из [startMinutes] (минуты от
 * полуночи), с отрезанными по краям пустыми часами — 18:30 и 20:10 дают «с 18 до 21». null — случаев
 * меньше трёх или в лучшем окне нет и половины: тогда «чаще» ничего не значит.
 */
fun peakHours(startMinutes: List<Int>, window: Int = 4): PeakHours? {
    if (startMinutes.size < 3) return null
    val byHour = IntArray(24).also { hours -> startMinutes.forEach { hours[(it / 60) % 24]++ } }
    val hours = (0 until 24)
        .map { from -> (0 until window).map { (from + it) % 24 } }
        .maxBy { range -> range.sumOf { byHour[it] } }
        .filter { byHour[it] > 0 }
    val count = hours.sumOf { byHour[it] }
    return if (count * 2 >= startMinutes.size) PeakHours(hours.first(), (hours.last() + 1) % 24, count) else null
}

/** Обычное бодрствование перед [at]: медиана окон ([awakeWindows]) за неделю до него; null — окон нет. */
fun List<BabyEvent>.usualAwakeBefore(at: Long): Long? = awakeWindows(at - USUAL_AWAKE_DAYS * DAY_MILLIS, at).median()

/** Медиана: пропущенная или лишняя запись её почти не сдвигает, в отличие от среднего. */
fun List<Long>.median(): Long? {
    if (isEmpty()) return null
    val sorted = sorted()
    val mid = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
}
