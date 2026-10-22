package com.kid.babylog.feature.diary

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kid.babylog.core.model.BabyEvent
import com.kid.babylog.core.model.Child
import com.kid.babylog.core.model.Illness
import com.kid.babylog.core.ui.theme.BabyLogTheme
import com.kid.babylog.core.ui.toLocalDate
import com.kid.babylog.feature.editor.NewEventKind
import com.kid.babylog.feature.illness.IllnessCard
import com.kid.babylog.feature.illness.illnessCardModel
import com.kid.babylog.feature.summary.DaySummaryCard
import java.time.LocalDate
import java.time.ZoneId

/** Действия пользователя на главном экране. */
internal class DiaryActions(
    val onStartSleep: () -> Unit,
    val onEndSleep: () -> Unit,
    val onStartFeeding: () -> Unit,
    val onEndFeeding: () -> Unit,
    val onSwitchBreast: () -> Unit,
    val onStartCrying: () -> Unit,
    val onEndCrying: () -> Unit,
    val onAdd: (NewEventKind) -> Unit,
    val onEditEvent: (BabyEvent) -> Unit,
    val onDeleteEvent: (BabyEvent) -> Unit,
    val onSelectDate: (LocalDate) -> Unit,
    val onPickDate: () -> Unit,
    /** Плитка «Здоровье», «Температура» на карточке болезни и её заголовок (ADR-0040). */
    val onHealth: () -> Unit = {},
    val onTemperature: () -> Unit = {},
    val onOpenIllness: (Illness) -> Unit = {},
)

/**
 * Раздел «Сегодня» (ADR-0035): плитки сна и кормления в ряд, под ними широкая плитка плача (ADR-0030)
 * с «Заметкой» рядом, плитки быстрых записей, сводка за сегодня и под ней — полоса советов недели
 * [tipsBanner] (ADR-0037), если советы есть. Ребёнок — в шапке. Пока ребёнок болеет, над таймерами —
 * карточка болезни ([IllnessCard], ADR-0040): ночью за ней и открывают «Сегодня».
 */
@Composable
internal fun TodayContent(
    state: DiaryUiState,
    child: Child,
    now: Long,
    actions: DiaryActions,
    modifier: Modifier = Modifier,
    tipsBanner: (@Composable () -> Unit)? = null,
    helpPointer: (@Composable () -> Unit)? = null,
) {
    val spacing = BabyLogTheme.spacing
    val illness = state.activeIllness
    // Пересчитываем раз в минуту, а не на каждый тик секундного таймера экрана
    val minuteNow = now / 60_000
    val illnessCard = remember(state.events, illness, minuteNow) {
        illness?.let { illnessCardModel(state.events, it, now, ZoneId.systemDefault()) }
    }
    DiaryList(modifier) {
        // Где справка (ADR-0043) — над плитками, чтобы новичок увидел её сразу; один раз
        if (helpPointer != null) item(key = "help") { helpPointer() }
        if (illnessCard != null) {
            item(key = "illness") {
                IllnessCard(
                    model = illnessCard,
                    now = now,
                    onOpen = { actions.onOpenIllness(illnessCard.illness) },
                    onTemperature = actions.onTemperature,
                    onMedicine = { actions.onAdd(NewEventKind.MEDICINE) },
                    onNote = { actions.onAdd(NewEventKind.NOTE) },
                )
            }
        }
        item(key = "timers") {
            // Таймеры — одна группа: плач под сном и кормлением на шаге внутри ряда, а не между карточками
            Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
                // IntrinsicSize.Min: плитки одной высоты, даже когда идёт только один таймер
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
                    val tile = Modifier.weight(1f).fillMaxHeight()
                    SleepCard(child, state.activeSleep, state.lastSleepEnd, now, actions.onStartSleep, actions.onEndSleep, tile)
                    FeedingCard(
                        activeFeeding = state.activeFeeding,
                        lastFeeding = state.lastFeeding,
                        now = now,
                        onStart = actions.onStartFeeding,
                        onEnd = actions.onEndFeeding,
                        onSwitchBreast = actions.onSwitchBreast,
                        modifier = tile,
                    )
                }
                CryingNoteRow(
                    activeCrying = state.activeCrying,
                    lastCrying = state.lastCrying,
                    now = now,
                    onStartCrying = actions.onStartCrying,
                    onEndCrying = actions.onEndCrying,
                    onAddNote = { actions.onAdd(NewEventKind.NOTE) },
                )
            }
        }
        item(key = "quick") {
            QuickActions(
                lastDiaper = state.lastDiaper,
                lastWeight = state.lastWeight?.start,
                lastHeight = state.lastHeight?.start,
                sleeping = state.activeSleep != null,
                now = now,
                onAdd = actions.onAdd,
                onHealth = actions.onHealth,
                illnessDay = illnessCard?.day,
            )
        }
        item(key = "summary") {
            DaySummaryCard(events = state.events, date = now.toLocalDate(), now = now, sex = child.sex)
        }
        if (tipsBanner != null) item(key = "tips") { tipsBanner() }
    }
}

/** «Дневник», вид «По дням» (ADR-0035): выбор дня, сводка выбранного дня и его записи. Свайп листает дни. */
@Composable
internal fun HistoryContent(
    state: DiaryUiState,
    child: Child,
    now: Long,
    selectedDate: LocalDate,
    actions: DiaryActions,
    modifier: Modifier = Modifier,
) {
    val events = state.events
    val today = now.toLocalDate()
    val days = remember(events, today) { historyDays(events, today) }
    val dayEvents = remember(events, selectedDate) { events.filter { it.start.toLocalDate() == selectedDate } }
    val prevDay = days.lastOrNull { it < selectedDate }
    val nextDay = days.firstOrNull { it > selectedDate }

    // Горизонтальный свайп в любом месте вида листает дни
    val swipeThresholdPx = with(LocalDensity.current) { 90.dp.toPx() }
    var dragTotal by remember { mutableFloatStateOf(0f) }
    DiaryList(
        modifier.pointerInput(prevDay, nextDay) {
            detectHorizontalDragGestures(
                onDragStart = { dragTotal = 0f },
                onHorizontalDrag = { _, dragAmount -> dragTotal += dragAmount },
                onDragEnd = {
                    when {
                        // палец вправо -> более ранний день
                        dragTotal > swipeThresholdPx && prevDay != null -> actions.onSelectDate(prevDay)
                        // палец влево -> более поздний день
                        dragTotal < -swipeThresholdPx && nextDay != null -> actions.onSelectDate(nextDay)
                    }
                },
            )
        },
    ) {
        item(key = "daynav") {
            DayNavRow(
                selectedDate = selectedDate,
                today = today,
                hasPrev = prevDay != null,
                hasNext = nextDay != null,
                onPrev = { prevDay?.let(actions.onSelectDate) },
                onNext = { nextDay?.let(actions.onSelectDate) },
                onPick = actions.onPickDate,
            )
        }
        item(key = "summary") {
            DaySummaryCard(events = events, date = selectedDate, now = now, sex = child.sex)
        }
        records(
            events = dayEvents,
            photoCounts = state.photoCounts,
            child = child,
            actions = actions,
            emptyText = if (events.isEmpty()) {
                "Пока нет записей.\nОтмечайте сон, кормления, подгузники и лекарства в разделе «Сегодня»."
            } else {
                "Нет записей за этот день"
            },
        )
    }
}

/** Записи дня, новые сверху; пустой день — подсказка [emptyText]. */
private fun LazyListScope.records(
    events: List<BabyEvent>,
    photoCounts: Map<Long, Int>,
    child: Child,
    actions: DiaryActions,
    emptyText: String,
) {
    if (events.isEmpty()) {
        item(key = "empty") {
            Text(
                emptyText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = BabyLogTheme.spacing.xxl),
            )
        }
    }
    items(events, key = { it.id }) { event ->
        EventRow(
            event = event,
            sex = child.sex,
            photoCount = photoCounts[event.id] ?: 0,
            onEdit = { actions.onEditEvent(event) },
            onDelete = { actions.onDeleteEvent(event) },
        )
    }
}

/** Лента раздела: карточки с общими отступами экрана. */
@Composable
private fun DiaryList(modifier: Modifier, content: LazyListScope.() -> Unit) {
    val spacing = BabyLogTheme.spacing
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        // Сверху отступ не меньше размытия тени карточек, иначе край тени срежется
        contentPadding = PaddingValues(start = spacing.l, top = spacing.m, end = spacing.l, bottom = spacing.xl),
        verticalArrangement = Arrangement.spacedBy(spacing.m),
        content = content,
    )
}
