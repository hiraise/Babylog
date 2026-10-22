package com.kid.babylog.feature.notes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kid.babylog.R
import com.kid.babylog.core.model.BabyEvent
import com.kid.babylog.core.model.Bottle
import com.kid.babylog.core.model.Crying
import com.kid.babylog.core.model.Diaper
import com.kid.babylog.core.model.Feeding
import com.kid.babylog.core.model.Height
import com.kid.babylog.core.model.Illness
import com.kid.babylog.core.model.Medicine
import com.kid.babylog.core.model.Note
import com.kid.babylog.core.model.Sex
import com.kid.babylog.core.model.Sleep
import com.kid.babylog.core.model.Temperature
import com.kid.babylog.core.model.Weight
import com.kid.babylog.core.model.eventTags
import com.kid.babylog.core.ui.components.AppCard
import com.kid.babylog.core.ui.components.AppIcon
import com.kid.babylog.core.ui.components.ChoiceChip
import com.kid.babylog.core.ui.eventIcon
import com.kid.babylog.core.ui.eventTimeLine
import com.kid.babylog.core.ui.eventTitle
import com.kid.babylog.core.ui.formatClock
import com.kid.babylog.core.ui.formatDayHeader
import com.kid.babylog.core.ui.formatDuration
import com.kid.babylog.core.ui.guide.GuideTarget
import com.kid.babylog.core.ui.guide.guideTarget
import com.kid.babylog.core.ui.theme.BabyLogTheme
import com.kid.babylog.core.ui.toLocalDate
import java.time.ZoneId

/** Снизу ленты — место под кнопку «Заметка»: последняя запись не прячется под ней. */
private val FabClearance = 88.dp
private val LeadIconSize = 24.dp

/**
 * Плач за неделю над лентой — числа из «Аналитики» ([perDay] — «11 мин», [note] — «0,7 раза в сутки ·
 * чаще с 19 до 22 ч»): касание открывает её раздел «Плач».
 */
data class CryingWeek(val perDay: String, val note: String?)

/**
 * Фильтр ленты переживает поворот строкой: метка — с «#» впереди, остальное — словом. Так метка
 * «плач» или «#» не спутается с фильтром: метки бывают любые, кроме пустых.
 */
val NotesFilterSaver = Saver<NotesFilter, String>(
    save = { filter ->
        when (filter) {
            NotesFilter.All -> ""
            NotesFilter.CryingOnly -> CRYING_KEY
            NotesFilter.NotesOnly -> NOTES_KEY
            NotesFilter.Illnesses -> ILLNESSES_KEY
            is NotesFilter.Tag -> TAG_PREFIX + filter.tag
        }
    },
    restore = { key ->
        when {
            key.startsWith(TAG_PREFIX) -> NotesFilter.Tag(key.removePrefix(TAG_PREFIX))
            key == CRYING_KEY -> NotesFilter.CryingOnly
            key == NOTES_KEY -> NotesFilter.NotesOnly
            key == ILLNESSES_KEY -> NotesFilter.Illnesses
            else -> NotesFilter.All
        }
    },
)

private const val CRYING_KEY = "crying"
private const val NOTES_KEY = "notes"
private const val ILLNESSES_KEY = "illnesses"
private const val TAG_PREFIX = "#"

/**
 * Вкладка «Заметки» (ADR-0031): лента плача и заметок по дням, свежие сверху. Над ней — плач за неделю
 * ([cryingWeek], касание — [onOpenAnalytics]) и фильтры: «Все», «Плач», «Заметки» и метки. Выбранная
 * метка — сводка ([tagSummaryItems]) и записи с ней; метку выбирают и касанием метки на записи. Системная
 * «Назад» снимает фильтр. Касание записи — правка ([onEdit]), корзина — удаление ([onDelete]), как
 * в «Истории». «Заметка» внизу — новая заметка ([onAddNote]). Фильтр «Болезни» — вместо ленты болезни
 * карточками ([illnessItems], ADR-0040), касание открывает её экран ([onOpenIllness]).
 */
@Composable
fun NotesContent(
    events: List<BabyEvent>,
    sex: Sex?,
    now: Long,
    photoCounts: Map<Long, Int>,
    filter: NotesFilter,
    onFilter: (NotesFilter) -> Unit,
    cryingWeek: CryingWeek?,
    onOpenAnalytics: () -> Unit,
    onAddNote: () -> Unit,
    onEdit: (BabyEvent) -> Unit,
    onDelete: (BabyEvent) -> Unit,
    modifier: Modifier = Modifier,
    onOpenIllness: (Illness) -> Unit = {},
) {
    val spacing = BabyLogTheme.spacing
    val zone = ZoneId.systemDefault()
    val today = now.toLocalDate()
    // Пересчитываем раз в минуту, а не на каждый тик секундного таймера экрана
    val minuteNow = now / 60_000
    val feed = remember(events, filter) { notesFeed(events, filter, zone) }
    val tags = remember(events) { filterTags(events) }
    val summary = remember(events, filter, minuteNow) { (filter as? NotesFilter.Tag)?.let { tagSummary(events, it.tag, now, zone) } }
    val hasCrying = remember(events) { events.any { it is Crying } }
    val hasNotes = remember(events) { events.any { it is Note } }
    val illnesses = remember(events, minuteNow) { illnessEntries(events, now, zone) }
    val list = rememberLazyListState()
    BackHandler(enabled = filter != NotesFilter.All) { onFilter(NotesFilter.All) }
    // Другой фильтр — с начала ленты: сводка метки сверху
    LaunchedEffect(filter) { list.scrollToItem(0) }

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            state = list,
            // Гайд справки (ADR-0043): открыта лента или сводка метки
            modifier = Modifier.fillMaxSize().guideTarget(if (filter is NotesFilter.Tag) GuideTarget.TAG_SUMMARY else GuideTarget.NOTES_FEED),
            contentPadding = PaddingValues(start = spacing.l, top = spacing.m, end = spacing.l, bottom = FabClearance),
            verticalArrangement = Arrangement.spacedBy(spacing.m),
        ) {
            // Плач за неделю — над всей лентой и над плачем; у метки — своя сводка
            if (cryingWeek != null && filter !is NotesFilter.Tag && filter != NotesFilter.Illnesses) {
                item(key = "crying") { CryingWeekCard(cryingWeek, onOpenAnalytics) }
            }
            if (tags.isNotEmpty() || hasCrying || illnesses.isNotEmpty()) {
                item(key = "filters") { FilterRow(filter, tags, hasCrying, hasNotes, illnesses.isNotEmpty(), onFilter) }
            }
            if (filter == NotesFilter.Illnesses) illnessItems(illnesses, today, onOpenIllness)
            val openTag = { tag: String -> onFilter(NotesFilter.Tag(tag)) }
            summary?.let { tagSummary -> tagSummaryItems(tagSummary, sex, feed.sumOf { it.records.size }, openTag) }
            if (feed.isEmpty() && (filter != NotesFilter.Illnesses || illnesses.isEmpty())) {
                item(key = "empty") { EmptyHint(filter) }
            }
            feed.forEach { day ->
                item(key = "day:${day.date}") {
                    Text(
                        formatDayHeader(day.date, today),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = spacing.xs).semantics { heading() },
                    )
                }
                items(day.records, key = { it.id }) { record ->
                    FeedCard(record, photoCounts[record.id] ?: 0, onEdit = { onEdit(record) }, onDelete = { onDelete(record) }, onTag = openTag)
                }
            }
        }
        // Вариант с содержимым, а не с text/icon: тот в этой версии Material 3 начинает свёрнутым, без подписи
        ExtendedFloatingActionButton(
            onClick = onAddNote,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.align(Alignment.BottomEnd).padding(spacing.l),
        ) {
            AppIcon(R.drawable.ic_add, contentDescription = null)
            Spacer(Modifier.width(spacing.s))
            Text("Заметка")
        }
    }
}

/** «Плач: 11 мин в сутки за неделю», под ним — сколько раз и когда чаще; ведёт в «Аналитику». */
@Composable
private fun CryingWeekCard(week: CryingWeek, onOpen: () -> Unit) {
    val spacing = BabyLogTheme.spacing
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onOpen,
        onClickLabel = "открыть плач в аналитике",
        contentPadding = PaddingValues(start = spacing.l, top = spacing.m, end = spacing.s, bottom = spacing.m),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.m)) {
            AppIcon(R.drawable.ic_analytics, contentDescription = null, modifier = Modifier.size(LeadIconSize), tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text("Плач: ${week.perDay} в сутки за неделю", style = MaterialTheme.typography.bodyLarge)
                Text(
                    listOfNotNull(week.note, "подробнее в «Аналитике»").joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                )
            }
            AppIcon(R.drawable.ic_chevron_right, contentDescription = null, tint = muted)
        }
    }
}

/** «Все», «Плач», «Заметки» и «Болезни» (если такие записи есть) и метки — выбирается один; ряд листается вбок. */
@Composable
private fun FilterRow(
    filter: NotesFilter,
    tags: List<String>,
    hasCrying: Boolean,
    hasNotes: Boolean,
    hasIllnesses: Boolean,
    onFilter: (NotesFilter) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(BabyLogTheme.spacing.s),
    ) {
        ChoiceChip(selected = filter == NotesFilter.All, onClick = { onFilter(NotesFilter.All) }, label = "Все")
        if (hasCrying) {
            ChoiceChip(selected = filter == NotesFilter.CryingOnly, onClick = { onFilter(NotesFilter.CryingOnly) }, label = "Плач")
        }
        if (hasNotes) {
            ChoiceChip(selected = filter == NotesFilter.NotesOnly, onClick = { onFilter(NotesFilter.NotesOnly) }, label = "Заметки")
        }
        if (hasIllnesses) {
            ChoiceChip(selected = filter == NotesFilter.Illnesses, onClick = { onFilter(NotesFilter.Illnesses) }, label = "Болезни")
        }
        // Выбранная метка видна в ряду, даже если она редкая и в первые не попала
        val selectedTag = (filter as? NotesFilter.Tag)?.tag
        (tags + listOfNotNull(selectedTag)).distinct().forEach { tag ->
            ChoiceChip(selected = tag == selectedTag, onClick = { onFilter(NotesFilter.Tag(tag)) }, label = tag)
        }
    }
}

/** Пустая лента: что сюда попадёт и откуда. */
@Composable
private fun EmptyHint(filter: NotesFilter) {
    Text(
        when (filter) {
            NotesFilter.All -> "Здесь будут плач и заметки.\nПлач отмечают плиткой на «Сегодня», заметку — кнопкой «Заметка»."
            NotesFilter.CryingOnly -> "Плача пока нет"
            NotesFilter.NotesOnly -> "Заметок пока нет. Новая — кнопкой «Заметка»"
            NotesFilter.Illnesses -> "Болезней пока нет"
            is NotesFilter.Tag -> "Записей с этой меткой нет"
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = BabyLogTheme.spacing.xxl),
    )
}

/**
 * Запись ленты: у плача — «Плач · 24 мин» и время с тем, что помогло, у заметки — её текст и время;
 * под ними метки. Касание метки открывает её сводку ([onTag]), касание остальной карточки — правку.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FeedCard(record: BabyEvent, photoCount: Int, onEdit: () -> Unit, onDelete: () -> Unit, onTag: (String) -> Unit) {
    val spacing = BabyLogTheme.spacing
    val colors = MaterialTheme.colorScheme
    val (title, subtitle, comment) = feedLines(record, photoCount)
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onEdit,
        onClickLabel = "изменить запись",
        // Справа отступ меньше: у кнопки удаления своё поле 48 dp вокруг иконки, как в «Истории»
        contentPadding = PaddingValues(start = spacing.l, top = spacing.s, end = spacing.xs, bottom = spacing.s),
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(spacing.m)) {
            AppIcon(
                eventIcon(record),
                contentDescription = null,
                modifier = Modifier.padding(top = spacing.m).size(LeadIconSize),
                tint = colors.primary,
            )
            val tags = record.eventTags
            // Снизу отступа нет, если есть метки: воздух вокруг метки даёт её поле нажатия 48 dp
            Column(Modifier.weight(1f).padding(top = spacing.s, bottom = if (tags.isEmpty()) spacing.s else 0.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                comment?.let { Text(it, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic, color = colors.onSurfaceVariant) }
                if (tags.isNotEmpty()) {
                    // Без шагов: поле нажатия метки (48 dp) само отделяет её от текста и ряды друг от друга
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        tags.forEach { tag -> TagLabel(tag, onClick = { onTag(tag) }) }
                    }
                }
            }
            IconButton(onClick = onDelete) {
                AppIcon(R.drawable.ic_delete, contentDescription = "Удалить", tint = colors.onSurfaceVariant)
            }
        }
    }
}

/** Заголовок, вторая строка и комментарий записи ленты. */
private fun feedLines(record: BabyEvent, photoCount: Int): Triple<String, String, String?> {
    val photos = if (photoCount > 0) " · $photoCount фото" else ""
    return when (record) {
        is Crying -> {
            val end = record.end
            Triple(
                if (end == null) "Плач · идёт" else "Плач · ${formatDuration(end - record.start)}",
                (if (end == null) "с ${formatClock(record.start)}" else "${formatClock(record.start)}–${formatClock(end)}") +
                    (record.helped.takeIf { it.isNotEmpty() }?.let { " · помогло: ${it.joinToString(", ")}" } ?: ""),
                record.note.ifBlank { null },
            )
        }
        // Заметка без текста — только метки или фото: заголовок «Заметка»
        is Note -> Triple(record.note.ifBlank { "Заметка" }, formatClock(record.start) + photos, null)
        // В ленте только плач и заметки ([NotesFilter.matches]); остальные — как в «Истории»
        is Feeding, is Bottle, is Diaper, is Sleep, is Medicine, is Weight, is Height, is Illness, is Temperature ->
            Triple(eventTitle(record, null), eventTimeLine(record) + photos, record.note.ifBlank { null })
    }
}

/**
 * Метка — подпись на мягкой подложке `secondaryContainer`. С [onClick] — кнопка: подложка остаётся
 * маленькой, а нажимается поле 48 dp вокруг (`minimumInteractiveComponentSize`), как у чипов Material.
 */
@Composable
internal fun TagLabel(tag: String, count: Int? = null, onClick: (() -> Unit)? = null) {
    val colors = MaterialTheme.colorScheme
    val spacing = BabyLogTheme.spacing
    Text(
        if (count == null) tag else "$tag $count",
        style = MaterialTheme.typography.labelMedium,
        color = colors.onSecondaryContainer,
        modifier = Modifier
            .then(if (onClick != null) Modifier.minimumInteractiveComponentSize() else Modifier)
            .clip(CircleShape)
            .background(colors.secondaryContainer)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = "показать записи с меткой", onClick = onClick) else Modifier)
            .padding(horizontal = spacing.s, vertical = spacing.xs),
    )
}
