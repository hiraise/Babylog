package com.kid.babylog.feature.diary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.kid.babylog.R
import com.kid.babylog.core.model.BabyEvent
import com.kid.babylog.core.model.Sleep
import com.kid.babylog.core.ui.components.ActionTile
import com.kid.babylog.core.ui.components.AppCard
import com.kid.babylog.core.ui.components.AppIcon
import com.kid.babylog.core.ui.components.ItemRow
import com.kid.babylog.core.ui.eventIcon
import com.kid.babylog.core.ui.eventTimeLine
import com.kid.babylog.core.ui.eventTitle
import com.kid.babylog.core.ui.formatClock
import com.kid.babylog.core.ui.formatTimer
import com.kid.babylog.core.ui.theme.BabyLogTheme
import com.kid.babylog.feature.editor.NewEventKind

/** Таймер сна выбранного ребёнка: «Уснул…» или идущий сон с кнопкой «Проснулся». */
@Composable
internal fun SleepCard(childName: String, activeSleep: Sleep?, now: Long, onStart: () -> Unit, onEnd: () -> Unit) {
    val spacing = BabyLogTheme.spacing
    if (activeSleep != null) {
        AppCard(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                Text("$childName спит", style = MaterialTheme.typography.titleMedium)
                Text(
                    "с ${formatClock(activeSleep.start)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Цифры Nunito одной ширины: строка таймера не дрожит, когда меняются секунды
                Text(
                    formatTimer((now - activeSleep.start).coerceAtLeast(0)),
                    style = MaterialTheme.typography.displayMedium,
                )
                Button(
                    onClick = onEnd,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = spacing.s),
                ) {
                    Text("Проснулся")
                }
            }
        }
    } else {
        AppCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = spacing.l, vertical = spacing.m),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.m),
            ) {
                AppIcon(R.drawable.ic_sleep, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Сон", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Button(onClick = onStart) { Text("Уснул…") }
            }
        }
    }
}

/** Плитки быстрого добавления записей. Сон начинается отдельно — в [SleepCard]. */
@Composable
internal fun QuickActions(onAdd: (NewEventKind) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(BabyLogTheme.spacing.s)) {
        ActionTile(R.drawable.ic_feeding, "Кормление", { onAdd(NewEventKind.FEEDING) }, Modifier.weight(1f))
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
