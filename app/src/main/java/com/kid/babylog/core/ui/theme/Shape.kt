package com.kid.babylog.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Скругления по макету. Компоненты Material 3 сами берут свою ступень:
 * - `extraSmall` 11 dp — поля ввода, меню, snackbar;
 * - `small` 18 dp — чипы;
 * - `medium` 18 dp — карточки;
 * - `large` 24 dp — FAB;
 * - `extraLarge` 30 dp — диалоги, пикеры даты и времени, нижние листы.
 *
 * Кнопки Material 3 скруглены по высоте (форма «пилюля») и от этой шкалы не зависят.
 */
internal val BabyLogShapes = Shapes(
    extraSmall = RoundedCornerShape(11.dp),
    small = RoundedCornerShape(18.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
)
