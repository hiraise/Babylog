package com.kid.babylog.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Заливка идущего таймера (`TimerTile`, `WideTimerTile`): фон [container], текст и иконка на нём —
 * [content]. В светлой теме это акцент `primary`, в тёмной — тон акцента `primaryContainer`
 * со светлыми цифрами: большая светлая плашка ночью слепит (ADR-0032).
 */
@Immutable
class TimerColors(val container: Color, val content: Color)

/** Заливка идущего таймера для палитры [colors]; [dark] — тёмная ли тема. */
internal fun timerColors(colors: ColorScheme, dark: Boolean): TimerColors =
    if (dark) TimerColors(colors.primaryContainer, colors.onPrimaryContainer) else TimerColors(colors.primary, colors.onPrimary)

/** Заливка идущего таймера текущей темы; задаёт [BabyLogTheme], читай через [BabyLogTheme.timerColors]. */
val LocalTimerColors = staticCompositionLocalOf { timerColors(LightColors, dark = false) }
