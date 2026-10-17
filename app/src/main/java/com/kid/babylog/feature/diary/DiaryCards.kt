package com.kid.babylog.feature.diary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kid.babylog.R
import com.kid.babylog.core.model.BabyEvent
import com.kid.babylog.core.model.Child
import com.kid.babylog.core.model.Feeding
import com.kid.babylog.core.model.Sex
import com.kid.babylog.core.model.Sleep
import com.kid.babylog.core.ui.breastLabel
import com.kid.babylog.core.ui.bySex
import com.kid.babylog.core.ui.components.ActionTile
import com.kid.babylog.core.ui.components.AppCard
import com.kid.babylog.core.ui.components.AppIcon
import com.kid.babylog.core.ui.components.ItemRow
import com.kid.babylog.core.ui.components.TimerCornerButton
import com.kid.babylog.core.ui.components.TimerTile
import com.kid.babylog.core.ui.eventIcon
import com.kid.babylog.core.ui.eventTimeLine
import com.kid.babylog.core.ui.eventTitle
import com.kid.babylog.core.ui.theme.BabyLogTheme
import com.kid.babylog.feature.editor.NewEventKind

/** Таймер сна — плитка в ряду с кормлением (ADR-0021): касание начинает сон, во время сна — заканчивает. */
@Composable
internal fun SleepCard(
    child: Child,
    activeSleep: Sleep?,
    now: Long,
    onStart: () -> Unit,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TimerTile(
        icon = R.drawable.ic_sleep,
        label = "Сон",
        runningSince = activeSleep?.start,
        now = now,
        startLabel = "начать сон",
        stopLabel = bySex(child.sex, "проснулся", "проснулась"),
        onStart = onStart,
        onStop = onEnd,
        modifier = modifier,
    )
}

/**
 * Таймер кормления (ADR-0015) — плитка в ряду со сном (ADR-0021): касание начинает кормление,
 * во время кормления — заканчивает; ⇄ в углу меняет грудь.
 */
@Composable
internal fun FeedingCard(
    activeFeeding: Feeding?,
    now: Long,
    onStart: () -> Unit,
    onEnd: () -> Unit,
    onSwitchBreast: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TimerTile(
        icon = R.drawable.ic_feeding,
        label = "Кормление",
        runningSince = activeFeeding?.start,
        now = now,
        startLabel = "начать кормление",
        stopLabel = "закончить кормление",
        onStart = onStart,
        onStop = onEnd,
        modifier = modifier,
        runningLabel = activeFeeding?.let { "${breastLabel(it.breast)} грудь" } ?: "Кормление",
        corner = { TimerCornerButton(R.drawable.ic_swap, "Сменить грудь", onSwitchBreast) },
    )
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

/** Строка истории: нажатие на карточку — правка, корзина — удаление. [sex] — пол ребёнка, чья запись. */
@Composable
internal fun EventRow(event: BabyEvent, sex: Sex?, onEdit: () -> Unit, onDelete: () -> Unit) {
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
            title = eventTitle(event, sex),
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
