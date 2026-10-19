package com.kid.babylog.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import com.kid.babylog.core.data.DarkPalette

/** Цвет тремя числами OKLCH: светлота, хрома, оттенок в градусах (см. [oklch]). */
private class Lch(val l: Double, val c: Double, val h: Double)

// Светлота OKLCH, общая для всех спокойных палитр: палитры различаются только оттенком и хромой
private const val LOWEST = 0.195 // surfaceContainerLowest, surfaceDim
private const val BACKGROUND = 0.225 // фон экрана и верхней панели
private const val CARD = 0.262 // карточки и меню
private const val SHEET = 0.292 // нижние листы и пикеры
private const val HIGHEST = 0.330 // заливка без акцентного текста
private const val DIVIDER = 0.37 // outlineVariant
private const val OUTLINE = 0.63 // рамки: не меньше 3:1 на любой поверхности
private const val MUTED_TEXT = 0.745 // второстепенный текст: не меньше 4,5:1 на любой поверхности
private const val TEXT = 0.905 // текст: около 11,7:1 на карточке, а не 13:1 белого
private const val TONE = 0.39 // тон акцента primaryContainer — заливка идущего таймера

// Ошибка во всех спокойных палитрах одна — мягкий коралловый
private const val ERROR_HUE = 24.0

/**
 * Спокойная тёмная палитра (ADR-0032). Текст не белый, акцент вдвое менее насыщенный, чем
 * макетный зелёный, а светлота поверхностей, текста и рамок одна на все палитры — они различаются
 * оттенком. Поэтому экран, который хорошо смотрится в одной палитре, смотрится и в остальных.
 * Контраст пар каждой палитры проверяет `ThemeContrastTest`.
 *
 * @param neutralHue оттенок фона, карточек и рамок; [neutralChroma] — насколько он заметен.
 * @param textHue оттенок текста; у второстепенного текста хрома в 1,2 раза больше [textChroma].
 * @param accent акцент `primary`: текст, иконки, кнопки, выбранные чипы.
 * @param secondaryChroma хрома ролей `secondary*` — они того же оттенка, что [accent].
 * @param tertiary второй акцент — оттенок, дополняющий [accent].
 * @param toneChroma хрома тона акцента `primaryContainer`: идущий таймер, аватар, кнопка «Заметка».
 */
private fun calmDarkScheme(
    neutralHue: Double,
    neutralChroma: Double,
    textHue: Double,
    textChroma: Double,
    accent: Lch,
    secondaryChroma: Double,
    tertiary: Lch,
    toneChroma: Double = accent.c * 0.85,
): ColorScheme {
    fun neutral(l: Double, c: Double = neutralChroma) = oklch(l, c, neutralHue)
    fun ofAccent(l: Double, c: Double) = oklch(l, c, accent.h)
    fun ofTertiary(l: Double, c: Double) = oklch(l, c, tertiary.h)
    fun ofError(l: Double, c: Double) = oklch(l, c, ERROR_HUE)
    val text = oklch(TEXT, textChroma, textHue)
    val background = neutral(BACKGROUND)
    val card = neutral(CARD)
    val sheet = neutral(SHEET)
    val highest = neutral(HIGHEST)
    val lowest = neutral(LOWEST)
    return darkColorScheme(
        primary = ofAccent(accent.l, accent.c),
        onPrimary = ofAccent(0.25, minOf(0.04, accent.c * 0.5)),
        primaryContainer = ofAccent(TONE, toneChroma),
        onPrimaryContainer = ofAccent(0.92, accent.c * 0.4),
        inversePrimary = ofAccent(0.47, accent.c * 1.1),
        secondary = ofAccent(0.78, secondaryChroma),
        onSecondary = ofAccent(0.25, secondaryChroma * 0.8),
        secondaryContainer = ofAccent(0.35, secondaryChroma * 0.9),
        onSecondaryContainer = ofAccent(0.90, secondaryChroma * 0.7),
        tertiary = ofTertiary(tertiary.l, tertiary.c),
        onTertiary = ofTertiary(0.25, minOf(0.04, tertiary.c * 0.5)),
        tertiaryContainer = ofTertiary(0.37, tertiary.c * 0.6),
        onTertiaryContainer = ofTertiary(0.91, tertiary.c * 0.5),
        background = background,
        onBackground = text,
        surface = background,
        onSurface = text,
        surfaceVariant = highest,
        onSurfaceVariant = oklch(MUTED_TEXT, textChroma * 1.2, textHue),
        surfaceTint = background,
        inverseSurface = oklch(0.90, textChroma, textHue),
        inverseOnSurface = neutral(0.27),
        error = ofError(0.74, 0.095),
        onError = ofError(0.25, 0.05),
        errorContainer = ofError(0.37, 0.07),
        onErrorContainer = ofError(0.91, 0.04),
        outline = neutral(OUTLINE, neutralChroma * 1.2),
        outlineVariant = neutral(DIVIDER),
        scrim = Color.Black,
        surfaceBright = sheet,
        surfaceContainer = card,
        surfaceContainerHigh = sheet,
        surfaceContainerHighest = highest,
        surfaceContainerLow = card,
        surfaceContainerLowest = lowest,
        surfaceDim = lowest,
        primaryFixed = PrimaryFixed,
        primaryFixedDim = PrimaryFixedDim,
        onPrimaryFixed = OnPrimaryFixed,
        onPrimaryFixedVariant = OnPrimaryFixedVariant,
        secondaryFixed = SecondaryFixed,
        secondaryFixedDim = SecondaryFixedDim,
        onSecondaryFixed = OnSecondaryFixed,
        onSecondaryFixedVariant = OnSecondaryFixedVariant,
        tertiaryFixed = TertiaryFixed,
        tertiaryFixedDim = TertiaryFixedDim,
        onTertiaryFixed = OnTertiaryFixed,
        onTertiaryFixedVariant = OnTertiaryFixedVariant,
    )
}

/** «Сумерки»: пыльная лаванда на сине-фиолетовом графите. */
private val DuskColors = calmDarkScheme(
    neutralHue = 285.0, neutralChroma = 0.020, textHue = 290.0, textChroma = 0.012,
    accent = Lch(0.74, 0.085, 292.0), secondaryChroma = 0.035, tertiary = Lch(0.82, 0.06, 60.0),
)

/** «Ночник»: абрикос и сливки на тёплом какао. Тон акцента глуше: с обычной хромой плитка таймера рыжая. */
private val NightlightColors = calmDarkScheme(
    neutralHue = 55.0, neutralChroma = 0.011, textHue = 80.0, textChroma = 0.018,
    accent = Lch(0.76, 0.085, 62.0), secondaryChroma = 0.035, tertiary = Lch(0.80, 0.05, 145.0),
    toneChroma = 0.052,
)

/** «Шалфей»: приглушённый зелёный на мшистом графите — ближе всех к светлой теме. */
private val SageColors = calmDarkScheme(
    neutralHue = 165.0, neutralChroma = 0.012, textHue = 115.0, textChroma = 0.012,
    accent = Lch(0.75, 0.07, 150.0), secondaryChroma = 0.03, tertiary = Lch(0.82, 0.06, 60.0),
)

/** «Пудра»: пыльная роза на сливовом графите. */
private val PowderColors = calmDarkScheme(
    neutralHue = 350.0, neutralChroma = 0.016, textHue = 25.0, textChroma = 0.012,
    accent = Lch(0.74, 0.075, 8.0), secondaryChroma = 0.03, tertiary = Lch(0.82, 0.05, 290.0),
)

/** Цвета тёмной темы в палитре [palette] (ADR-0032). Образцы в листе «Тема» берут их отсюда. */
internal fun darkColors(palette: DarkPalette): ColorScheme = when (palette) {
    DarkPalette.DUSK -> DuskColors
    DarkPalette.NIGHTLIGHT -> NightlightColors
    DarkPalette.SAGE -> SageColors
    DarkPalette.POWDER -> PowderColors
    DarkPalette.CLASSIC -> ClassicDarkColors
}

/** Цвета графиков тёмной темы: у спокойных палитр общие, у «Классики» — прежние. */
internal fun darkChartColors(palette: DarkPalette): ChartColors = when (palette) {
    DarkPalette.DUSK, DarkPalette.NIGHTLIGHT, DarkPalette.SAGE, DarkPalette.POWDER -> CalmDarkChartColors
    DarkPalette.CLASSIC -> ClassicDarkChartColors
}
