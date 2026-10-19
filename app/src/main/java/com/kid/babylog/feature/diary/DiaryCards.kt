package com.kid.babylog.feature.diary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kid.babylog.R
import com.kid.babylog.core.model.BabyEvent
import com.kid.babylog.core.model.Child
import com.kid.babylog.core.model.Crying
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
import com.kid.babylog.core.ui.components.WideTimerTile
import com.kid.babylog.core.ui.eventIcon
import com.kid.babylog.core.ui.eventTimeLine
import com.kid.babylog.core.ui.eventTitle
import com.kid.babylog.core.ui.formatAgo
import com.kid.babylog.core.ui.formatClock
import com.kid.babylog.core.ui.formatDuration
import com.kid.babylog.core.ui.formatShortDay
import com.kid.babylog.core.ui.theme.BabyLogTheme
import com.kid.babylog.core.ui.toLocalDate
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
 * Пока кормление не идёт — чем кормили, во сколько начали и сколько прошло с конца
 * ([afterFeedingCaption]): какой грудью начинать теперь, когда кормить снова («каждые 3 часа» —
 * от начала) и можно ли купать или класть на живот (от конца). Только здесь: сводка дня — итоги
 * дня, «последнее» она не повторяет (ADR-0026).
 */
@Composable
internal fun FeedingCard(
    activeFeeding: Feeding?,
    lastFeeding: LastFeeding?,
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
        idleCaption = afterFeedingCaption(lastFeeding, now),
        corner = { TimerCornerButton(R.drawable.ic_swap, "Сменить грудь", onSwitchBreast) },
    )
}

/**
 * Плач (ADR-0030) — широкая плитка под сном и кормлением, рядом с «Заметкой» ([CryingNoteRow]):
 * касание сразу запускает таймер, ещё касание останавливает и открывает лист «Плач закончился».
 * Пока не плачет — когда был последний плач и сколько длился ([cryingCaption]).
 */
@Composable
internal fun CryingCard(
    activeCrying: Crying?,
    lastCrying: Crying?,
    now: Long,
    onStart: () -> Unit,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    WideTimerTile(
        icon = R.drawable.ic_crying,
        label = "Плач",
        runningSince = activeCrying?.start,
        now = now,
        startLabel = "начать плач",
        stopLabel = "закончить плач",
        onStart = onStart,
        onStop = onEnd,
        modifier = modifier,
        idleCaption = cryingCaption(lastCrying, now),
    )
}

/**
 * Ряд под сном и кормлением: плач ([CryingCard]) и «Заметка» — пятая плитка быстрой записи
 * ([ActionTile], без пояснения). Заметка — рядом с плачем, а не в ряду [QuickActions]: там на 360 dp
 * «Подгузник» и «Лекарство» уже впритык, а метки у плача и заметок общие (ADR-0031).
 *
 * «Заметка» шириной в плитку ряда [QuickActions] под ней, плач — в остальные три: края плиток двух
 * рядов на одной линии. Поэтому ширина считается от ширины ряда, а не весами: с одним промежутком
 * вместо трёх плитка вышла бы на 4 dp шире «Роста».
 */
@Composable
internal fun CryingNoteRow(
    activeCrying: Crying?,
    lastCrying: Crying?,
    now: Long,
    onStartCrying: () -> Unit,
    onEndCrying: () -> Unit,
    onAddNote: () -> Unit,
) {
    val gap = BabyLogTheme.spacing.s
    BoxWithConstraints {
        val noteWidth = (maxWidth - gap * (QUICK_ACTION_COUNT - 1)) / QUICK_ACTION_COUNT
        // IntrinsicSize.Min: плитки одной высоты — у плача она постоянна (WideTimerTile)
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(gap)) {
            CryingCard(activeCrying, lastCrying, now, onStartCrying, onEndCrying, Modifier.weight(1f).fillMaxHeight())
            ActionTile(R.drawable.ic_note, "Заметка", onAddNote, Modifier.width(noteWidth).fillMaxHeight())
        }
    }
}

/** «2 ч назад · 24 мин» — последний законченный плач; не было или давно ([sinceLast]) — null. */
internal fun cryingCaption(lastCrying: Crying?, now: Long): String? {
    val end = lastCrying?.end ?: return null
    return sinceLast(end, now)?.let { "${formatAgo(it)} · ${formatDuration(end - lastCrying.start)}" }
}

/** «не спит 1 ч 20 мин» — с конца последнего сна; сна не было или он давно ([sinceLast]) — null. */
internal fun awakeCaption(lastSleepEnd: Long?, now: Long): String? =
    sinceLast(lastSleepEnd, now)?.let { "не спит ${formatDuration(it)}" }

/**
 * Две строки: чем кормили и во сколько начали — начало по часам, чтобы не путать его со второй
 * строкой, — и сколько прошло с конца: «левая в 12:00» / «закончилось 40 мин назад». У бутылочки
 * начало и конец — одно время: «бутылочка в 12:30» / «40 мин назад». Не было или давно ([sinceLast]) — null.
 */
internal fun afterFeedingCaption(lastFeeding: LastFeeding?, now: Long): String? {
    val ago = sinceLast(lastFeeding?.end, now) ?: return null
    val breast = lastFeeding?.breast
    val what = breast?.let(::breastLabel) ?: "бутылочка"
    val since = if (breast == null) formatAgo(ago) else "закончилось ${formatAgo(ago)}"
    return "$what в ${formatClock(lastFeeding!!.start)}\n$since"
}

/** Сколько прошло с [end]; сутки и больше — null: так долго не спят и не едят, просто не записывали. */
private fun sinceLast(end: Long?, now: Long): Long? =
    end?.let { (now - it).coerceAtLeast(0) }?.takeIf { it < SINCE_LAST_LIMIT_MS }

private const val SINCE_LAST_LIMIT_MS = 24 * 60 * 60 * 1000L

/**
 * Плитки быстрого добавления записей. Сон и кормление начинаются отдельно — в [SleepCard] и [FeedingCard],
 * «Заметка» — рядом с плачем ([CryingNoteRow]), шириной в плитку этого ряда ([QUICK_ACTION_COUNT]).
 *
 * Под «Подгузник» — сколько прошло с последней смены ([diaperCaption]); давно ([diaperOverdue]) —
 * пояснение выделено. Под «Вес» и «Рост» — дата последнего замера ([measuredCaption]); сам вес и рост —
 * в шапке под именем. Это только на плитках: сводка дня их не повторяет — одно и то же в двух местах
 * с разным расчётом расходится.
 */
@Composable
internal fun QuickActions(
    lastDiaper: Long?,
    lastWeight: Long?,
    lastHeight: Long?,
    sleeping: Boolean,
    now: Long,
    onAdd: (NewEventKind) -> Unit,
) {
    // IntrinsicSize.Min: плитки одной высоты, даже когда пояснение есть не у всех
    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(BabyLogTheme.spacing.s)) {
        val tile = Modifier.weight(1f).fillMaxHeight()
        ActionTile(
            R.drawable.ic_diaper,
            "Подгузник",
            { onAdd(NewEventKind.DIAPER) },
            tile,
            caption = diaperCaption(lastDiaper, now),
            highlight = diaperOverdue(lastDiaper, sleeping, now),
        )
        ActionTile(R.drawable.ic_medicine, "Лекарство", { onAdd(NewEventKind.MEDICINE) }, tile)
        ActionTile(R.drawable.ic_weight, "Вес", { onAdd(NewEventKind.WEIGHT) }, tile, caption = measuredCaption(lastWeight, now))
        ActionTile(R.drawable.ic_height, "Рост", { onAdd(NewEventKind.HEIGHT) }, tile, caption = measuredCaption(lastHeight, now))
    }
}

/** Сколько плиток в ряду [QuickActions]: по ней «Заметка» в [CryingNoteRow] встаёт над последней. */
private const val QUICK_ACTION_COUNT = 4

/**
 * Когда взвешивали или мерили рост в последний раз: «сегодня», «вчера», «15 окт.» ([formatShortDay]).
 * Дата, а не «… назад», как у подгузника: замеры редкие, и следующий (у врача — раз в месяц) отсчитывают
 * по дате. Суточного срока, как у [sinceLast], нет: замер месячной давности — тоже последний. Не было — null.
 */
internal fun measuredCaption(lastMeasured: Long?, now: Long): String? =
    lastMeasured?.let { formatShortDay(it.toLocalDate(), now.toLocalDate()) }

/** «2 ч 10 мин назад» — с последней смены подгузника; не меняли или давно ([sinceLast]) — null. */
internal fun diaperCaption(lastDiaper: Long?, now: Long): String? = sinceLast(lastDiaper, now)?.let(::formatAgo)

/**
 * Пора ли менять подгузник: с последней смены прошло [DIAPER_CHANGE_MS] и больше. Пока малыш спит —
 * нет: будить ради подгузника не нужно. Нет пояснения ([diaperCaption] — null) — тоже нет.
 */
internal fun diaperOverdue(lastDiaper: Long?, sleeping: Boolean, now: Long): Boolean =
    !sleeping && (sinceLast(lastDiaper, now) ?: 0) >= DIAPER_CHANGE_MS

/** Подгузник меняют каждые 2–3 часа — после трёх пояснение на плитке выделяется. */
private const val DIAPER_CHANGE_MS = 3 * 60 * 60 * 1000L

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
