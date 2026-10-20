package com.kid.babylog.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import com.kid.babylog.core.data.DarkPalette

/** Цвет тремя числами OKLCH: светлота, хрома, оттенок в градусах (см. [oklch]). */
private class Lch(val l: Double, val c: Double, val h: Double)

// Светлота OKLCH, общая для всех спокойных палитр: палитры различаются только оттенком и хромой
private const val LOWEST = 0.165 // surfaceContainerLowest, surfaceDim
private const val BACKGROUND = 0.195 // фон экрана и верхней панели: глубокий, чтобы карточки от него отделялись
private const val CARD = 0.252 // карточки и меню
private const val SHEET = 0.287 // нижние листы и пикеры
private const val HIGHEST = 0.327 // заливка без акцентного текста
private const val DIVIDER = 0.36 // outlineVariant
private const val OUTLINE = 0.63 // рамки: не меньше 3:1 на любой поверхности
private const val MUTED_TEXT = 0.765 // второстепенный текст: не меньше 4,5:1 на любой поверхности
private const val TEXT = 0.925 // текст: около 12,7:1 на карточке, но не белый
private const val TONE = 0.47 // тон акцента primaryContainer — заливка идущего таймера, в 2,2–2,4 раза светлее карточки

// Ошибка во всех спокойных палитрах одна — коралловая
private const val ERROR_HUE = 24.0

/**
 * Спокойная тёмная палитра (ADR-0032). Светлота поверхностей, текста и рамок одна на все палитры —
 * они различаются оттенком. Поэтому экран, который хорошо смотрится в одной палитре, смотрится и
 * в остальных. Контраст пар каждой палитры и заметность идущего таймера проверяет `ThemeContrastTest`.
 *
 * Спокойная, но не блёклая: фон глубокий, а цвет — у того, что важно. Акцент насыщеннее фона в разы
 * (хрома 0,10–0,12 против 0,01–0,02), плитка идущего таймера — того же оттенка, светлее карточки
 * в 2,2–2,4 раза, с почти белыми цифрами. Если всё одного оттенка и одной насыщенности, элементы
 * сливаются и смотреть тяжело, хотя контраст текста высокий.
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
    toneChroma: Double = accent.c,
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
        onPrimary = ofAccent(0.22, minOf(0.04, accent.c * 0.4)),
        primaryContainer = ofAccent(TONE, toneChroma),
        onPrimaryContainer = ofAccent(0.97, 0.02),
        inversePrimary = ofAccent(0.47, accent.c),
        secondary = ofAccent(0.80, secondaryChroma),
        onSecondary = ofAccent(0.22, secondaryChroma * 0.8),
        secondaryContainer = ofAccent(0.34, secondaryChroma * 0.9),
        onSecondaryContainer = ofAccent(0.92, secondaryChroma * 0.7),
        tertiary = ofTertiary(tertiary.l, tertiary.c),
        onTertiary = ofTertiary(0.22, 0.04),
        tertiaryContainer = ofTertiary(0.36, tertiary.c * 0.55),
        onTertiaryContainer = ofTertiary(0.92, tertiary.c * 0.4),
        background = background,
        onBackground = text,
        surface = background,
        onSurface = text,
        surfaceVariant = highest,
        onSurfaceVariant = oklch(MUTED_TEXT, textChroma * 1.2, textHue),
        surfaceTint = background,
        inverseSurface = oklch(0.92, textChroma, textHue),
        inverseOnSurface = neutral(0.25),
        error = ofError(0.74, 0.12),
        onError = ofError(0.22, 0.05),
        errorContainer = ofError(0.36, 0.09),
        onErrorContainer = ofError(0.92, 0.04),
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

/** «Сумерки»: лаванда на сине-фиолетовом графите. */
private val DuskColors = calmDarkScheme(
    neutralHue = 285.0, neutralChroma = 0.020, textHue = 290.0, textChroma = 0.012,
    accent = Lch(0.74, 0.119, 292.0), secondaryChroma = 0.035, tertiary = Lch(0.82, 0.078, 60.0),
)

/** «Ночник»: абрикос и сливки на тёплом какао. Тон акцента глуше: с полной хромой плитка таймера рыжая. */
private val NightlightColors = calmDarkScheme(
    neutralHue = 55.0, neutralChroma = 0.011, textHue = 80.0, textChroma = 0.018,
    accent = Lch(0.76, 0.119, 62.0), secondaryChroma = 0.035, tertiary = Lch(0.80, 0.065, 145.0),
    toneChroma = 0.095,
)

/** «Шалфей»: зелень на мшистом графите — ближе всех к светлой теме. */
private val SageColors = calmDarkScheme(
    neutralHue = 165.0, neutralChroma = 0.012, textHue = 115.0, textChroma = 0.012,
    accent = Lch(0.75, 0.098, 150.0), secondaryChroma = 0.03, tertiary = Lch(0.82, 0.078, 60.0),
)

/** «Пудра»: роза на сливовом графите. */
private val PowderColors = calmDarkScheme(
    neutralHue = 350.0, neutralChroma = 0.016, textHue = 25.0, textChroma = 0.012,
    accent = Lch(0.74, 0.105, 8.0), secondaryChroma = 0.03, tertiary = Lch(0.82, 0.065, 290.0),
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
