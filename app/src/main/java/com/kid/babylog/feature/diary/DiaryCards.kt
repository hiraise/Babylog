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
import com.kid.babylog.core.ui.formatAgo
import com.kid.babylog.core.ui.formatDuration
import com.kid.babylog.core.ui.theme.BabyLogTheme
import com.kid.babylog.feature.editor.NewEventKind

/**
 * Таймер сна — плитка в ряду с кормлением (ADR-0021): касание начинает сон, во время сна — заканчивает.
 * Пока сон не идёт — сколько малыш уже не спит ([awakeCaption]): по этому окну решают, когда укладывать.
 */
@Composable
internal fun SleepCard(
    child: Child,
    activeSleep: Sleep?,
    lastSleepEnd: Long?,
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
        idleCaption = awakeCaption(lastSleepEnd, now),
    )
}

/**
 * Таймер кормления (ADR-0015) — плитка в ряду со сном (ADR-0021): касание начинает кормление,
 * во время кормления — заканчивает; ⇄ в углу меняет грудь.
 *
 * Пока кормление не идёт — сколько прошло с его конца ([afterFeedingCaption]): можно ли купать или
 * класть на живот. Сводка дня считает от начала кормления — по ней решают, когда кормить снова.
 */
@Composable
internal fun FeedingCard(
    activeFeeding: Feeding?,
    lastFeedingEnd: Long?,
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
        idleCaption = afterFeedingCaption(lastFeedingEnd, now),
        corner = { TimerCornerButton(R.drawable.ic_swap, "Сменить грудь", onSwitchBreast) },
    )
}

/** «не спит 1 ч 20 мин» — с конца последнего сна; сна не было или он давно ([sinceLast]) — null. */
internal fun awakeCaption(lastSleepEnd: Long?, now: Long): String? =
    sinceLast(lastSleepEnd, now)?.let { "не спит ${formatDuration(it)}" }

/** «закончилось 40 мин назад» — с конца последнего кормления; не было или давно ([sinceLast]) — null. */
internal fun afterFeedingCaption(lastFeedingEnd: Long?, now: Long): String? =
    sinceLast(lastFeedingEnd, now)?.let { "закончилось ${formatAgo(it)}" }

/** Сколько прошло с [end]; сутки и больше — null: так долго не спят и не едят, просто не записывали. */
private fun sinceLast(end: Long?, now: Long): Long? =
    end?.let { (now - it).coerceAtLeast(0) }?.takeIf { it < SINCE_LAST_LIMIT_MS }

private const val SINCE_LAST_LIMIT_MS = 24 * 60 * 60 * 1000L

/** Плитки быстрого добавления записей. Сон и кормление начинаются отдельно — в [SleepCard] и [FeedingCard]. */
@Composable
internal fun QuickActions(onAdd: (NewEventKind) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(BabyLogTheme.spacing.s)) {
        ActionTile(R.drawable.ic_diaper, "Подгузник", { onAdd(NewEventKind.DIAPER) }, Modifier.weight(1f))
        ActionTile(R.drawable.ic_medicine, "Лекарство", { onAdd(NewEventKind.MEDICINE) }, Modifier.weight(1f))
        ActionTile(R.drawable.ic_weight, "Вес", { onAdd(NewEventKind.WEIGHT) }, Modifier.weight(1f))
    }
}

/**
 * Строка истории: нажатие на карточку — правка, корзина — удаление. [sex] — пол ребёнка, чья запись;
 * [photoCount] — сколько у записи фото (ADR-0024): «14:30 · 2 фото», чтобы найти их, не открывая записи.
 */
@Composable
internal fun EventRow(event: BabyEvent, sex: Sex?, photoCount: Int, onEdit: () -> Unit, onDelete: () -> Unit) {
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
            subtitle = eventTimeLine(event) + if (photoCount > 0) " · $photoCount фото" else "",
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
