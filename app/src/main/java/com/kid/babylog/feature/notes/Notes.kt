package com.kid.babylog.feature.notes

import com.kid.babylog.core.model.BabyEvent
import com.kid.babylog.core.model.Crying
import com.kid.babylog.core.model.Illness
import com.kid.babylog.core.model.LONGER_THAN_USUAL
import com.kid.babylog.core.model.MAX_AWAKE_MILLIS
import com.kid.babylog.core.model.Note
import com.kid.babylog.core.model.PeakHours
import com.kid.babylog.core.model.Temperature
import com.kid.babylog.core.model.days
import com.kid.babylog.core.model.illnessMedicines
import com.kid.babylog.core.model.illnessRecords
import com.kid.babylog.core.model.lastDate
import com.kid.babylog.core.model.startDate
import com.kid.babylog.core.model.title
import com.kid.babylog.core.model.eventTags
import com.kid.babylog.core.model.lastFeedingEndBefore
import com.kid.babylog.core.model.lastSleepEndBefore
import com.kid.babylog.core.model.median
import com.kid.babylog.core.model.peakHours
import com.kid.babylog.core.model.usualAwakeBefore
import com.kid.babylog.core.ui.formatDayRange
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Меток в ряду фильтров — не больше стольких: дальше редкие, их видно на записях. */
private const val MAX_FILTER_TAGS = 12

/** Календарь метки — столько недель с понедельника, последняя — текущая. */
const val TAG_WEEKS = 4

/** «Что помогало» и «Часто вместе» — не больше стольких: дальше единичные случаи. */
private const val TOP_ITEMS = 6

/**
 * Что показывает лента «Заметок» (ADR-0031): всё, только плач, только заметки или записи с меткой — тогда
 * над лентой сводка метки. [Illnesses] — вместо ленты болезни карточками (ADR-0040).
 */
sealed interface NotesFilter {
    data object All : NotesFilter

    data object CryingOnly : NotesFilter

    data object NotesOnly : NotesFilter

    data object Illnesses : NotesFilter

    data class Tag(val tag: String) : NotesFilter
}

/** Запись подходит под фильтр: в ленте только плач и заметки; у «Болезней» ленты нет — там карточки ([illnessEntries]). */
fun NotesFilter.matches(event: BabyEvent): Boolean = (event is Crying || event is Note) && when (this) {
    NotesFilter.All -> true
    NotesFilter.CryingOnly -> event is Crying
    NotesFilter.NotesOnly -> event is Note
    NotesFilter.Illnesses -> false
    is NotesFilter.Tag -> tag in event.eventTags
}

/**
 * Болезнь карточкой в «Заметках» (ADR-0040): названия ([title], null — без названия), дни ([range] —
 * «7–14 окт.», идущая — «с 18 окт.»), сколько дней, самая высокая температура и чем лечили.
 */
data class IllnessEntry(
    val illness: Illness,
    val title: String?,
    val range: String,
    val days: Int,
    val maxTenths: Int?,
    val medicines: List<String>,
)

/** Болезни ребёнка, свежие сверху: идущая первой. */
fun illnessEntries(events: List<BabyEvent>, now: Long, zone: ZoneId): List<IllnessEntry> {
    val today = dateOf(now, zone)
    return events.filterIsInstance<Illness>().sortedByDescending { it.start }.map { illness ->
        val records = events.illnessRecords(illness, now)
        val first = illness.startDate(zone)
        IllnessEntry(
            illness = illness,
            title = illness.title(),
            range = if (illness.isActive) "с ${formatDayRange(first, first)}" else formatDayRange(first, illness.lastDate(today, zone)),
            days = illness.days(today, zone),
            maxTenths = records.filterIsInstance<Temperature>().maxOfOrNull { it.tenths },
            medicines = events.illnessMedicines(illness, now).sortedBy { it.times.first() }.map { it.name },
        )
    }.sortedByDescending { it.illness.isActive }
}

/** Записи ленты за один день, свежие сверху. */
data class FeedDay(val date: LocalDate, val records: List<BabyEvent>)

/** Плач и заметки из [events] по [filter] — по дням, свежие сверху. */
fun notesFeed(events: List<BabyEvent>, filter: NotesFilter, zone: ZoneId): List<FeedDay> =
    events.filter(filter::matches)
        .sortedWith(compareByDescending<BabyEvent> { it.start }.thenByDescending { it.id })
        .groupBy { dateOf(it.start, zone) }
        .map { (date, records) -> FeedDay(date, records) }

/** Метки для ряда фильтров: из плача и заметок, частые первыми, при равенстве — по алфавиту. */
fun filterTags(events: List<BabyEvent>): List<String> = counted(events.flatMap { it.eventTags }).take(MAX_FILTER_TAGS).map { it.first }

/** День календаря метки: сколько раз плакал с этой меткой, была ли заметка с ней; [future] — день ещё не наступил. */
data class TagDay(val date: LocalDate, val cryings: Int, val note: Boolean, val today: Boolean, val future: Boolean)

/**
 * Сводка метки над лентой (ADR-0031): сколько раз плач и заметки с ней, календарь последних
 * [TAG_WEEKS] недель, когда начинался плач, что было перед ним, что помогало и какие метки рядом.
 * Совпадения в записях, а не причины: так и подписано на экране.
 */
data class TagSummary(
    val tag: String,
    val cryings: Int,
    val notes: Int,
    val days: List<TagDay>,
    /** Начала плача с меткой — минуты от полуночи; [peak] — когда чаще. */
    val cryingStarts: List<Int>,
    val peak: PeakHours?,
    /** Сколько раз из [awakeKnown] плач начался после бодрствования дольше обычного. */
    val longerAwake: Int,
    val awakeKnown: Int,
    /** Сколько обычно прошло после кормления — медиана; null — кормлений рядом не было. */
    val sinceFeeding: Long?,
    val helped: List<Pair<String, Int>>,
    val together: List<Pair<String, Int>>,
)

/** Сводка метки [tag] по записям ребёнка [events]; [now] — для календаря и «сегодня». */
fun tagSummary(events: List<BabyEvent>, tag: String, now: Long, zone: ZoneId): TagSummary {
    val tagged = events.filter { tag in it.eventTags }
    val cryings = tagged.filterIsInstance<Crying>()
    val today = dateOf(now, zone)
    val firstDay = today.minusDays(today.dayOfWeek.value - 1L).minusWeeks(TAG_WEEKS - 1L)
    val cryingDays = cryings.groupingBy { dateOf(it.start, zone) }.eachCount()
    val noteDays = tagged.filterIsInstance<Note>().map { dateOf(it.start, zone) }.toSet()
    val days = (0 until TAG_WEEKS * 7L).map { firstDay.plusDays(it) }.map { date ->
        TagDay(date, cryingDays[date] ?: 0, date in noteDays, today = date == today, future = date > today)
    }

    val starts = cryings.map { Instant.ofEpochMilli(it.start).atZone(zone).let { time -> time.hour * 60 + time.minute } }
    // Перед плачем: не спал — от конца сна, обычное — за неделю до этого плача; давнее 6 ч — забытая запись
    val awake = cryings.mapNotNull { crying ->
        val awakeFor = events.lastSleepEndBefore(crying.start)?.let { crying.start - it }?.takeIf { it <= MAX_AWAKE_MILLIS }
        val usual = events.usualAwakeBefore(crying.start)
        if (awakeFor != null && usual != null) awakeFor > usual * LONGER_THAN_USUAL else null
    }
    val sinceFeeding = cryings.mapNotNull { crying ->
        events.lastFeedingEndBefore(crying.start)?.let { crying.start - it }?.takeIf { it <= MAX_AWAKE_MILLIS }
    }
    return TagSummary(
        tag = tag,
        cryings = cryings.size,
        notes = tagged.size - cryings.size,
        days = days,
        cryingStarts = starts,
        peak = peakHours(starts),
        longerAwake = awake.count { it },
        awakeKnown = awake.size,
        sinceFeeding = sinceFeeding.median(),
        helped = counted(cryings.flatMap { it.helped }).take(TOP_ITEMS),
        together = counted(tagged.flatMap { it.eventTags - tag }).take(TOP_ITEMS),
    )
}

/** Сколько раз встречается каждое из [items]: частые первыми, при равенстве — по алфавиту. */
private fun counted(items: List<String>): List<Pair<String, Int>> =
    items.groupingBy { it }.eachCount().entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .map { it.key to it.value }

private fun dateOf(millis: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
