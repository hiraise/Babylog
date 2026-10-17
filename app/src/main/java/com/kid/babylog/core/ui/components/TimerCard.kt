package com.kid.babylog.core.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kid.babylog.R
import com.kid.babylog.core.ui.formatTimer
import com.kid.babylog.core.ui.theme.BabyLogTheme

/**
 * Плитка таймера (сон, кормление) — как плитка быстрой записи [ActionTile]: большая иконка и
 * подпись [label], без кнопок. Ставь две плитки в ряд: `Row(Modifier.height(IntrinsicSize.Min))`,
 * у каждой `Modifier.weight(1f).fillMaxHeight()` — тогда они одной высоты (ADR-0021).
 *
 * Пока таймер не идёт ([runningSince] = null) — фон карточки, касание — [onStart], под подписью —
 * [idleCaption]: сколько прошло с конца прошлого («не спит 1 ч 20 мин»), чтобы не идти за этим
 * в «Историю», — до трёх строк: в плитку они помещаются в высоту идущего таймера. Идёт — фон
 * `primary`, иконка поднимается, под ней время с [runningSince], подпись —
 * [runningLabel] (например «левая грудь»); касание — [onStop]. [corner] — необязательная кнопка
 * в правом верхнем углу идущего таймера (сменить грудь).
 *
 * @param startLabel и [stopLabel] — что сделает касание, для TalkBack: «начать сон», «проснулась».
 */
@Composable
fun TimerTile(
    @DrawableRes icon: Int,
    label: String,
    runningSince: Long?,
    now: Long,
    startLabel: String,
    stopLabel: String,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    runningLabel: String = label,
    idleCaption: String? = null,
    corner: (@Composable () -> Unit)? = null,
) {
    val spacing = BabyLogTheme.spacing
    val colors = MaterialTheme.colorScheme
    val running = runningSince != null
    val container by animateColorAsState(if (running) colors.primary else colors.surfaceContainerLow, TileColorMotion, label = "фон")
    val content by animateColorAsState(if (running) colors.onPrimary else colors.onSurface, TileColorMotion, label = "текст")
    val iconTint by animateColorAsState(if (running) colors.onPrimary else colors.primary, TileColorMotion, label = "иконка")
    val elapsed = runningSince?.let { (now - it).coerceAtLeast(0) }
    // Пока время уезжает при остановке, на плитке последнее значение, а не «00:00»
    val lastElapsed = remember { mutableLongStateOf(0L) }
    SideEffect { if (elapsed != null) lastElapsed.longValue = elapsed }
    AppCard(
        modifier = modifier,
        onClick = if (running) onStop else onStart,
        onClickLabel = if (running) stopLabel else startLabel,
        contentPadding = PaddingValues(spacing.s),
        containerColor = container,
        contentColor = content,
    ) {
        // Высота плитки — по идущему таймеру (иконка, время, подпись): старт и стоп её не меняют,
        // время выезжает внутри неподвижной плитки, а ряды ниже не дёргаются
        Box(Modifier.fillMaxWidth().heightIn(min = 104.dp).fillMaxHeight()) {
            // Содержимое по центру: когда появляется время, иконка уходит вверх
            // Отступы — внутри строк, а не spacedBy: промежуток у исчезающего времени иначе пропадает
            // скачком в последнем кадре, и подпись прыгает
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AppIcon(icon, contentDescription = null, modifier = Modifier.size(32.dp), tint = iconTint)
                // Одинаковая длительность без пружины: у пружины длинный хвост, и иконка с подписью ещё
                // десяток кадров ползут на пиксель — это выглядит как дрожание
                AnimatedVisibility(
                    visible = running,
                    enter = fadeIn(tween(TILE_MOTION_MS)) +
                        expandVertically(tween(TILE_MOTION_MS, easing = FastOutSlowInEasing), expandFrom = Alignment.Top),
                    exit = fadeOut(tween(TILE_MOTION_MS)) +
                        shrinkVertically(tween(TILE_MOTION_MS, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Top),
                ) {
                    // Цифры Nunito одной ширины: строка не дрожит. «1:23:45» на половине экрана 360 dp
                    // в displaySmall не влезает — шрифт уменьшается сам
                    val style = MaterialTheme.typography.displaySmall
                    Text(
                        formatTimer(elapsed ?: lastElapsed.longValue),
                        modifier = Modifier.padding(top = spacing.xs),
                        style = style,
                        maxLines = 1,
                        autoSize = TextAutoSize.StepBased(minFontSize = style.fontSize * 0.6f, maxFontSize = style.fontSize),
                    )
                }
                Text(
                    if (running) runningLabel else label,
                    modifier = Modifier.padding(top = spacing.xs),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Уходит, пока выезжает время, и наоборот. Три строки ниже строки времени — высота
                // плитки по-прежнему задаётся идущим таймером
                AnimatedVisibility(
                    visible = !running && idleCaption != null,
                    enter = fadeIn(tween(TILE_MOTION_MS)) +
                        expandVertically(tween(TILE_MOTION_MS, easing = FastOutSlowInEasing), expandFrom = Alignment.Top),
                    exit = fadeOut(tween(TILE_MOTION_MS)) +
                        shrinkVertically(tween(TILE_MOTION_MS, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Top),
                ) {
                    Text(
                        idleCaption.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (running && corner != null) {
                Box(Modifier.align(Alignment.TopEnd)) { corner() }
            }
        }
    }
}

/** Длительность анимаций плитки таймера: цвет, появление и исчезновение времени — синхронно. */
private const val TILE_MOTION_MS = 250
private val TileColorMotion = tween<Color>(TILE_MOTION_MS, easing = FastOutSlowInEasing)

/** Кнопка-иконка в углу идущего таймера ([TimerTile] `corner`): «Сменить грудь». Цвет — как у текста плитки. */
@Composable
fun TimerCornerButton(@DrawableRes icon: Int, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        AppIcon(icon, contentDescription = description)
    }
}

@Preview(name = "TimerTile", widthDp = 720)
@Composable
private fun TimerTilePreview() = LightDarkPreview {
    val start = 1_786_000_000_000L
    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(BabyLogTheme.spacing.s)) {
        TimerTile(
            R.drawable.ic_sleep, "Сон", runningSince = null, now = start,
            startLabel = "начать сон", stopLabel = "проснулся", onStart = {}, onStop = {},
            modifier = Modifier.weight(1f).fillMaxHeight(),
            idleCaption = "не спит 1 ч 20 мин",
        )
        TimerTile(
            R.drawable.ic_feeding, "Кормление", runningSince = start, now = start + 754_000,
            startLabel = "начать кормление", stopLabel = "закончить кормление", onStart = {}, onStop = {},
            modifier = Modifier.weight(1f).fillMaxHeight(),
            runningLabel = "левая грудь",
            corner = { TimerCornerButton(R.drawable.ic_swap, "Сменить грудь", onClick = {}) },
        )
    }
}
