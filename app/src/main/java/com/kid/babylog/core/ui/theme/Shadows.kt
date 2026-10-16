package com.kid.babylog.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.dp

/**
 * Тени темы.
 *
 * [card] — мягкая тень карточек и чипов из макета; рисуется `Modifier.dropShadow(shape, card)`
 * с формой самого элемента. В тёмной теме её нет (`null`): в макете там теней нет, карточку
 * от фона отделяет цвет `surfaceContainerLow`.
 */
@Immutable
class Shadows(val card: Shadow?)

internal val LightShadows = Shadows(card = Shadow(radius = 10.dp, color = Color.Black.copy(alpha = 0.10f)))

internal val DarkShadows = Shadows(card = null)

/** Тени текущей темы; задаёт [BabyLogTheme], читай через [BabyLogTheme.shadows]. */
val LocalShadows = staticCompositionLocalOf { LightShadows }
