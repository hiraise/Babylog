package com.kid.babylog.feature.analytics

import com.kid.babylog.core.model.BabyEvent
import com.kid.babylog.core.model.Crying
import com.kid.babylog.core.model.Note
import com.kid.babylog.core.model.eventTags
import com.kid.babylog.core.ui.formatDuration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

/** Меток в «Как часто» и в «Метках и режиме» — не больше стольких: дальше редкие. */
private const val TOP_TAGS = 6
private const val TOP_ROUTINE = 4

/** «Метки и режим» сравнивают, только если полных суток с меткой и без неё не меньше стольких. */
internal const val MIN_ROUTINE_DAYS = 3

/** Разница меньше этой — «без разницы»: на глаз её не видно, а записи неточны на минуты. */
private const val SAME_SLEEP_MILLIS = 10 * 60_000L
private const val SAME_CRYING_MILLIS = 5 * 60_000L

/** Метка в «Как часто»: сколько раз была за период и в каких столбиках — сутках или неделях. */
data class TagFrequency(val tag: String, val count: Int, val marks: List<Boolean>)

/**
 * «Метки и режим» для метки заметок [tag]: в сутки с ней сон ([sleep]) и плач ([crying]) — против
 * суток без неё, словами: «сон −1 ч 10 мин», «плач — без разницы». null у обоих — данных мало;
 * [crying] null — плач не записывали. [days] — сколько суток периода с этой меткой.
 */
data class TagRoutine(val tag: String, val days: Int, val sleep: String?, val crying: String?)

/**
 * Раздел и плитка «Метки» (ADR-0031): плач и заметки за период, как часто каждая метка и что
 * в сутки с ней с режимом. [untaggedNotes] — заметки без меток: в счёт меток они не входят.
 */
data class TagsModel(
    val cryings: Int,
    val notes: Int,
    val frequencies: List<TagFrequency>,
    val untaggedNotes: Int,
    val routine: List<TagRoutine>,
)

/**
 * Метки за сутки периода [days] ([dailyStats]); [byWeeks] — у «всего времени» столбики по неделям.
 * «Метки и режим» — только по заметкам: у плача метка — его причина, и плача в такие сутки больше
 * само собой. null — меток и заметок у ребёнка нет совсем: плитки нет.
 */
fun tagsModel(events: List<BabyEvent>, days: List<DayStats>, byWeeks: Boolean, zone: ZoneId): TagsModel? {
    if (days.isEmpty() || events.none { it is Note || it.eventTags.isNotEmpty() }) return null
    fun dateOf(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    val period = days.first().date..days.last().date
    val records = events.filter { (it is Crying || it is Note) && dateOf(it.start) in period }
    val buckets = if (byWeeks) days.chunked(7).map { week -> week.map { it.date }.toSet() } else days.map { setOf(it.date) }

    val frequencies = records.flatMap { record -> record.eventTags.map { it to dateOf(record.start) } }
        .groupBy({ it.first }, { it.second })
        .entries
        .sortedWith(compareByDescending<Map.Entry<String, List<LocalDate>>> { it.value.size }.thenBy { it.key })
        .take(TOP_TAGS)
        .map { (tag, dates) ->
            val marked = dates.toSet()
            TagFrequency(tag, dates.size, buckets.map { bucket -> bucket.any { it in marked } })
        }

    val countable = days.filter { it.countable }
    val cries = events.any { it is Crying }
    val routine = records.filterIsInstance<Note>()
        .flatMap { note -> note.tags.map { it to dateOf(note.start) } }
        .groupBy({ it.first }, { it.second })
        .mapValues { it.value.toSet() }
        .entries
        .sortedWith(compareByDescending<Map.Entry<String, Set<LocalDate>>> { it.value.size }.thenBy { it.key })
        .take(TOP_ROUTINE)
        .map { (tag, dates) ->
            val with = countable.filter { it.date in dates }
            val without = countable.filter { it.date !in dates }
            if (with.size < MIN_ROUTINE_DAYS || without.size < MIN_ROUTINE_DAYS) {
                TagRoutine(tag, dates.size, sleep = null, crying = null)
            } else {
                fun diff(value: (DayStats) -> Long) = with.map(value).average() - without.map(value).average()
                TagRoutine(
                    tag = tag,
                    days = dates.size,
                    sleep = change("сон", diff { it.sleepMillis }, SAME_SLEEP_MILLIS),
                    crying = if (cries) change("плач", diff { it.cryingMillis }, SAME_CRYING_MILLIS) else null,
                )
            }
        }

    return TagsModel(
        cryings = records.count { it is Crying },
        notes = records.count { it is Note },
        frequencies = frequencies,
        untaggedNotes = records.count { it is Note && it.eventTags.isEmpty() },
        routine = routine,
    )
}

/** «сон −1 ч 10 мин», «плач +30 мин»; меньше [same] — «плач — без разницы». */
private fun change(what: String, diff: Double, same: Long): String = when {
    abs(diff) < same -> "$what — без разницы"
    else -> "$what ${if (diff > 0) "+" else "−"}${formatDuration(abs(diff).toLong())}"
}
