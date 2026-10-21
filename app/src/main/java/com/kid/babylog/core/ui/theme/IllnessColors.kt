package com.kid.babylog.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance

/**
 * Фон болезни (ADR-0040) — второй акцент палитры, `tertiaryContainer`, наполовину поверх карточки:
 * карточка болезни на «Сегодня» и шапка её экрана заметны в каждой палитре, но не красные — тревоги
 * приложение не добавляет. Текст на нём — `onTertiaryContainer`, второстепенный — `tertiary`
 * (контраст проверяет `ThemeContrastTest`).
 */
val ColorScheme.illnessContainer: Color
    get() = tertiaryContainer.copy(alpha = 0.5f).compositeOver(surfaceContainerLow)

/**
 * Полоса дней болезни за столбиками «Аналитики» (ADR-0040): тот же оттенок, что [illnessContainer],
 * в светлой теме бледнее — это фон, а не ряд. Столбики на ней — в кайме цвета карточки (`drawBar`),
 * поэтому их контраст тот же, что на карточке.
 */
val ColorScheme.illnessBand: Color
    get() = tertiaryContainer.copy(alpha = if (surfaceContainerLow.luminance() > 0.5f) 0.35f else 0.5f)
        .compositeOver(surfaceContainerLow)
