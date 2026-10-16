package com.kid.babylog.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Шкала отступов (ADR-0010): `padding` и `spacedBy` в экранах берут шаг отсюда через
 * [BabyLogTheme.spacing], а не литерал `dp`.
 *
 * - [l] — край экрана и отступ внутри карточки;
 * - [m] — между карточками;
 * - [s], [xs] — между элементами внутри карточки: иконка и текст, строки, чипы;
 * - [xl], [xxl] — между крупными блоками экрана.
 */
@Immutable
data class Spacing(
    val xs: Dp = 4.dp,
    val s: Dp = 8.dp,
    val m: Dp = 12.dp,
    val l: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
)

/** Отступы текущей темы; задаёт [BabyLogTheme], читай через [BabyLogTheme.spacing]. */
val LocalSpacing = staticCompositionLocalOf { Spacing() }
