package com.kid.babylog.feature.diary

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import com.kid.babylog.core.ui.theme.BabyLogTheme
import com.kid.babylog.core.ui.toLocalDate
import com.kid.babylog.feature.children.ChildCard
import com.kid.babylog.feature.children.ChildSwitcher
import com.kid.babylog.feature.editor.NewEventKind
import com.kid.babylog.feature.summary.DaySummaryCard
import java.time.LocalDate

/** Действия пользователя на главном экране. */
internal class DiaryActions(
    val onSelectChild: (Long) -> Unit,
    val onEditChild: () -> Unit,
    val onStartSleep: () -> Unit,
    val onEndSleep: () -> Unit,
    val onStartFeeding: () -> Unit,
    val onEndFeeding: () -> Unit,
    val onSwitchBreast: () -> Unit,
    val onAdd: (NewEventKind) -> Unit,
    val onEditEvent: (BabyEvent) -> Unit,
    val onDeleteEvent: (BabyEvent) -> Unit,
    val onSelectDate: (LocalDate) -> Unit,
    val onPickDate: () -> Unit,
)

/** Лента главного экрана без собственного состояния: всё приходит параметрами. */
@Composable
internal fun DiaryContent(
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

    val spacing = BabyLogTheme.spacing
    // Горизонтальный свайп в любом месте экрана листает дни истории
    val swipeThresholdPx = with(LocalDensity.current) { 90.dp.toPx() }
    var dragTotal by remember { mutableFloatStateOf(0f) }
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(prevDay, nextDay) {
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
        // Сверху отступ не меньше размытия тени карточек, иначе край тени срежется
        contentPadding = PaddingValues(start = spacing.l, top = spacing.m, end = spacing.l, bottom = spacing.xl),
        verticalArrangement = Arrangement.spacedBy(spacing.m),
    ) {
        if (state.children.size > 1) {
            item(key = "children") {
                ChildSwitcher(state.children, child.id, actions.onSelectChild)
            }
        }
        item(key = "child") {
            ChildCard(child, state.lastWeight, now, actions.onEditChild)
        }
        item(key = "sleep") {
            SleepCard(child.name, state.activeSleep, now, actions.onStartSleep, actions.onEndSleep)
        }
        item(key = "feeding") {
            FeedingCard(
                childName = child.name,
                activeFeeding = state.activeFeeding,
                now = now,
                onStart = actions.onStartFeeding,
                onEnd = actions.onEndFeeding,
                onSwitchBreast = actions.onSwitchBreast,
            )
        }
        item(key = "quick") {
            QuickActions(actions.onAdd)
        }
        item(key = "summary") {
            DaySummaryCard(events = events, date = selectedDate, now = now)
        }
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
        val emptyText = when {
            events.isEmpty() -> "Пока нет записей.\nОтмечайте сон, кормления, подгузники и лекарства кнопками выше."
            dayEvents.isEmpty() -> "Нет записей за этот день"
            else -> null
        }
        if (emptyText != null) {
            item(key = "empty") {
                Text(
                    emptyText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = spacing.xxl),
                )
            }
        }
        items(dayEvents, key = { it.id }) { event ->
            EventRow(
                event = event,
                onEdit = { actions.onEditEvent(event) },
                onDelete = { actions.onDeleteEvent(event) },
            )
        }
    }
}
