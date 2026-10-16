package com.kid.babylog.feature.diary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kid.babylog.R
import com.kid.babylog.core.model.BabyEvent
import com.kid.babylog.core.model.Feeding
import com.kid.babylog.core.model.Sleep
import com.kid.babylog.core.ui.breastLabel
import com.kid.babylog.core.ui.components.ActionTile
import com.kid.babylog.core.ui.components.AppCard
import com.kid.babylog.core.ui.components.AppIcon
import com.kid.babylog.core.ui.components.ItemRow
import com.kid.babylog.core.ui.components.RunningTimerCard
import com.kid.babylog.core.ui.components.TimerStartCard
import com.kid.babylog.core.ui.eventIcon
import com.kid.babylog.core.ui.eventTimeLine
import com.kid.babylog.core.ui.eventTitle
import com.kid.babylog.core.ui.theme.BabyLogTheme
import com.kid.babylog.feature.editor.NewEventKind

/** Таймер сна выбранного ребёнка: «Уснул…» или идущий сон с кнопкой «Проснулся». */
@Composable
internal fun SleepCard(childName: String, activeSleep: Sleep?, now: Long, onStart: () -> Unit, onEnd: () -> Unit) {
    if (activeSleep == null) {
        TimerStartCard(R.drawable.ic_sleep, "Сон", "Уснул…", onStart)
    } else {
        RunningTimerCard("$childName спит", activeSleep.start, now) {
            Button(onClick = onEnd, modifier = Modifier.fillMaxWidth()) { Text("Проснулся") }
        }
    }
}

/** Таймер кормления (ADR-0015): «Начать…» или идущее кормление с кнопками «Закончить» и «Сменить грудь». */
@Composable
internal fun FeedingCard(
    childName: String,
    activeFeeding: Feeding?,
    now: Long,
    onStart: () -> Unit,
    onEnd: () -> Unit,
    onSwitchBreast: () -> Unit,
) {
    if (activeFeeding == null) {
        TimerStartCard(R.drawable.ic_feeding, "Кормление", "Начать…", onStart)
    } else {
        RunningTimerCard("$childName ест · ${breastLabel(activeFeeding.breast)} грудь", activeFeeding.start, now) {
            Button(onClick = onEnd, modifier = Modifier.fillMaxWidth()) { Text("Закончить") }
            OutlinedButton(onClick = onSwitchBreast, modifier = Modifier.fillMaxWidth()) { Text("Сменить грудь") }
        }
    }
}

/** Плитки быстрого добавления записей. Сон и кормление начинаются отдельно — в [SleepCard] и [FeedingCard]. */
@Composable
internal fun QuickActions(onAdd: (NewEventKind) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(BabyLogTheme.spacing.s)) {
        ActionTile(R.drawable.ic_diaper, "Подгузник", { onAdd(NewEventKind.DIAPER) }, Modifier.weight(1f))
        ActionTile(R.drawable.ic_medicine, "Лекарство", { onAdd(NewEventKind.MEDICINE) }, Modifier.weight(1f))
        ActionTile(R.drawable.ic_weight, "Вес", { onAdd(NewEventKind.WEIGHT) }, Modifier.weight(1f))
    }
}

/** Строка истории: нажатие на карточку — правка, корзина — удаление. */
@Composable
internal fun EventRow(event: BabyEvent, onEdit: () -> Unit, onDelete: () -> Unit) {
    val spacing = BabyLogTheme.spacing
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onEdit,
        onClickLabel = "изменить запись",
        // Справа отступ меньше: у кнопки удаления своё поле 48 dp вокруг иконки
        contentPadding = PaddingValues(start = spacing.l, top = spacing.s, end = spacing.xs, bottom = spacing.s),
    ) {
        ItemRow(
            icon = eventIcon(event),
            title = eventTitle(event),
            subtitle = eventTimeLine(event),
            note = event.note,
            trailing = {
                IconButton(onClick = onDelete) {
                    AppIcon(
                        R.drawable.ic_delete,
                        contentDescription = "Удалить",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
    }
}
