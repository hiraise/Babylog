package com.kid.babylog.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.kid.babylog.core.data.DarkPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import kotlin.math.cbrt
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.reflect.KProperty1

/**
 * Роль палитры — ссылка на свойство `ColorScheme`: у неё есть имя (для сообщений) и геттер,
 * а опечатку в имени поймает компилятор.
 */
private typealias Role = KProperty1<ColorScheme, Color>

/** Все роли `ColorScheme` (Material 3 1.4.0); полноту списка проверяет тест. */
private val ROLES: List<Role> = listOf(
    ColorScheme::primary, ColorScheme::onPrimary, ColorScheme::primaryContainer, ColorScheme::onPrimaryContainer,
    ColorScheme::inversePrimary,
    ColorScheme::secondary, ColorScheme::onSecondary, ColorScheme::secondaryContainer, ColorScheme::onSecondaryContainer,
    ColorScheme::tertiary, ColorScheme::onTertiary, ColorScheme::tertiaryContainer, ColorScheme::onTertiaryContainer,
    ColorScheme::background, ColorScheme::onBackground,
    ColorScheme::surface, ColorScheme::onSurface, ColorScheme::surfaceVariant, ColorScheme::onSurfaceVariant,
    ColorScheme::surfaceTint, ColorScheme::inverseSurface, ColorScheme::inverseOnSurface,
    ColorScheme::error, ColorScheme::onError, ColorScheme::errorContainer, ColorScheme::onErrorContainer,
    ColorScheme::outline, ColorScheme::outlineVariant, ColorScheme::scrim,
    ColorScheme::surfaceBright, ColorScheme::surfaceDim, ColorScheme::surfaceContainerLowest,
    ColorScheme::surfaceContainerLow, ColorScheme::surfaceContainer, ColorScheme::surfaceContainerHigh,
    ColorScheme::surfaceContainerHighest,
    ColorScheme::primaryFixed, ColorScheme::primaryFixedDim, ColorScheme::onPrimaryFixed,
    ColorScheme::onPrimaryFixedVariant,
    ColorScheme::secondaryFixed, ColorScheme::secondaryFixedDim, ColorScheme::onSecondaryFixed,
    ColorScheme::onSecondaryFixedVariant,
    ColorScheme::tertiaryFixed, ColorScheme::tertiaryFixedDim, ColorScheme::onTertiaryFixed,
    ColorScheme::onTertiaryFixedVariant,
)

/** Все поверхности, на которых бывает текст `onSurface` и `onSurfaceVariant` и рамка `outline`. */
private val SURFACES: List<Role> = listOf(
    ColorScheme::background, ColorScheme::surface, ColorScheme::surfaceVariant, ColorScheme::surfaceBright,
    ColorScheme::surfaceDim, ColorScheme::surfaceContainerLowest, ColorScheme::surfaceContainerLow,
    ColorScheme::surfaceContainer, ColorScheme::surfaceContainerHigh, ColorScheme::surfaceContainerHighest,
)

/**
 * Поверхности без текста `primary` и `error`. На `surfaceContainerHighest` эти цвета дают 4,1–4,5:1,
 * а это фон `TextField` с заливкой и `Card` из Material 3: поэтому поля ввода — только `OutlinedTextField`,
 * карточки — `AppCard` (KDoc `LightColors`). `surfaceVariant` и `surfaceDim` компоненты Material 3
 * под текст не берут; в светлой теме они того же цвета `#F2F2F2`.
 */
private val NO_ACCENT_SURFACES: Set<Role> = setOf(
    ColorScheme::surfaceVariant, ColorScheme::surfaceDim, ColorScheme::surfaceContainerHighest,
)

/** Поверхности, где бывает акцентный текст `primary` и текст ошибки `error`: все, кроме исключённых выше. */
private val ACCENT_SURFACES: List<Role> = SURFACES - NO_ACCENT_SURFACES

private const val TEXT_MINIMUM = 4.5 // текст (ADR-0010, WCAG AA)
private const val NON_TEXT_MINIMUM = 3.0 // рамки и другие нетекстовые элементы

private class ContrastPair(val foreground: Role, val background: Role, val minimum: Double)

private infix fun Role.on(background: Role) = ContrastPair(this, background, TEXT_MINIMUM)

private val PAIRS: List<ContrastPair> = listOf(
    ColorScheme::onPrimary on ColorScheme::primary,
    ColorScheme::onPrimaryContainer on ColorScheme::primaryContainer,
    ColorScheme::onSecondary on ColorScheme::secondary,
    ColorScheme::onSecondaryContainer on ColorScheme::secondaryContainer,
    ColorScheme::onTertiary on ColorScheme::tertiary,
    ColorScheme::onTertiaryContainer on ColorScheme::tertiaryContainer,
    ColorScheme::onError on ColorScheme::error,
    ColorScheme::onErrorContainer on ColorScheme::errorContainer,
    ColorScheme::onBackground on ColorScheme::background,
    ColorScheme::inverseOnSurface on ColorScheme::inverseSurface,
    ColorScheme::inversePrimary on ColorScheme::inverseSurface,
    ColorScheme::onPrimaryFixed on ColorScheme::primaryFixed,
    ColorScheme::onPrimaryFixedVariant on ColorScheme::primaryFixed,
    ColorScheme::onPrimaryFixed on ColorScheme::primaryFixedDim,
    ColorScheme::onSecondaryFixed on ColorScheme::secondaryFixed,
    ColorScheme::onSecondaryFixedVariant on ColorScheme::secondaryFixed,
    ColorScheme::onTertiaryFixed on ColorScheme::tertiaryFixed,
    ColorScheme::onTertiaryFixedVariant on ColorScheme::tertiaryFixed,
) +
    listOf(ColorScheme::onSurface, ColorScheme::onSurfaceVariant).flatMap { text -> SURFACES.map { text on it } } +
    listOf(ColorScheme::primary, ColorScheme::error).flatMap { text -> ACCENT_SURFACES.map { text on it } } +
    // Рамка — на всех поверхностях: Material 3 рисует outline и на surfaceContainerHighest (ручка Switch)
    SURFACES.map { ContrastPair(ColorScheme::outline, it, NON_TEXT_MINIMUM) }

/** Палитра темы и базовая схема Material 3, от которой она должна отличаться. */
private class Palette(val name: String, val colors: ColorScheme, val base: ColorScheme)

/** Светлая палитра и все тёмные (ADR-0032). */
private val PALETTES = listOf(Palette("светлая", LightColors, lightColorScheme())) +
    DarkPalette.entries.map { Palette("тёмная ${it.name}", darkColors(it), darkColorScheme()) }

/**
 * Белый и чёрный (тона 100 и 0) одинаковы в любой палитре, поэтому их совпадение с базовой схемой
 * не значит, что роль забыта: так совпадают `scrim` и белые `onPrimary`, `onSecondary`, `onTertiary`,
 * `onError`, `surfaceContainerLowest` светлой темы. Цветная роль из базовой схемы — фиолетовая.
 */
private val HUELESS = setOf(Color.White, Color.Black)

private val RU = Locale.forLanguageTag("ru")

/** Ряд графика — ссылка на свойство `ChartColors`, как роль палитры. */
private typealias ChartSeries = KProperty1<ChartColors, Color>

private val CHART_SERIES: List<ChartSeries> = listOf(
    ChartColors::sleep, ChartColors::daySleep, ChartColors::feeding, ChartColors::diaperWet, ChartColors::diaperMixed,
    ChartColors::diaperDirty, ChartColors::weight, ChartColors::height, ChartColors::crying,
    ChartColors::milestone, ChartColors::milestoneOther, ChartColors::temperature,
)

/**
 * Полоса болезни заметно отличается от карточки, но остаётся фоном. Столбики на ней — в кайме цвета карточки
 * (`drawBar`), поэтому их контраст к полосе не проверяем: он тот же, что к карточке.
 */
private const val BAND_MINIMUM = 1.08

/**
 * Ряды, которые стоят рядом (KDoc `ChartColors`): сон, кормление и плач — в полосе «Ритма дня»,
 * подгузники — в столбике в порядке `DIAPER_SERIES`. Новый график поставил рядом другие ряды — добавь пару.
 */
private val CHART_NEIGHBORS: List<Pair<ChartSeries, ChartSeries>> = listOf(
    ChartColors::sleep to ChartColors::feeding,
    ChartColors::sleep to ChartColors::daySleep,
    ChartColors::daySleep to ChartColors::feeding,
    ChartColors::daySleep to ChartColors::crying,
    ChartColors::sleep to ChartColors::crying,
    ChartColors::feeding to ChartColors::crying,
    ChartColors::diaperWet to ChartColors::diaperMixed,
    ChartColors::diaperMixed to ChartColors::diaperDirty,
    ChartColors::milestone to ChartColors::milestoneOther,
)

/** Графики светлой темы и всех тёмных палитр — вместе с палитрой, на карточке которой они лежат. */
private val CHARTS: List<Triple<String, ColorScheme, ChartColors>> =
    listOf(Triple("светлая", LightColors, LightChartColors)) +
        DarkPalette.entries.map { Triple("тёмная ${it.name}", darkColors(it), darkChartColors(it)) }

/**
 * Как глаз различает цвета: [matrix] переводит линейный sRGB в то, что видит дальтоник (null — обычное
 * зрение), [minimum] — наименьшая разница ΔE в OKLab ×100 между соседними рядами графика.
 */
private class Vision(val name: String, val matrix: List<DoubleArray>?, val minimum: Double)

/**
 * Обычное зрение, полные протанопия и дейтеранопия — матрицы Machado, Oliveira и Fernandes (2009).
 * Ниже 15 соседей путает и обычный глаз, ниже 8 — дальтоник. Тританопия редка, её не проверяем.
 */
private val VISIONS: List<Vision> = listOf(
    Vision("обычное зрение", null, 15.0),
    Vision(
        "протанопия",
        listOf(
            doubleArrayOf(0.152286, 1.052583, -0.204868),
            doubleArrayOf(0.114503, 0.786281, 0.099216),
            doubleArrayOf(-0.003882, -0.048116, 1.051998),
        ),
        8.0,
    ),
    Vision(
        "дейтеранопия",
        listOf(
            doubleArrayOf(0.367322, 0.860646, -0.227968),
            doubleArrayOf(0.280085, 0.672501, 0.047413),
            doubleArrayOf(-0.011820, 0.042940, 0.968881),
        ),
        8.0,
    ),
)

/**
 * Заливка идущего таймера к карточке. Плитка таймера стоит в ряду с карточками других плиток
 * и должна отделяться от них светлотой, а не только оттенком.
 */
private const val TIMER_MINIMUM = 2.0

/**
 * Правила палитры из ADR-0010 и ADR-0032: контраст пар «текст — фон», рядов графиков и идущего таймера,
 * различимость соседних рядов при дальтонизме, ни одной роли из базовой схемы и `surfaceTint`, равный `surface`.
 */
class ThemeContrastTest {

    @Test
    fun `text and outline pairs reach WCAG contrast`() {
        val failures = PALETTES.flatMap { palette ->
            PAIRS.mapNotNull { pair ->
                val foreground = pair.foreground.get(palette.colors)
                val background = pair.background.get(palette.colors)
                val ratio = contrast(foreground, background)
                if (ratio >= pair.minimum) return@mapNotNull null
                "${palette.name}: ${pair.foreground.name} ${foreground.hex()} на ${pair.background.name} " +
                    "${background.hex()} — ${"%.2f".format(RU, ratio)}:1, нужно не меньше ${"%.1f".format(RU, pair.minimum)}:1"
            }
        }
        assertTrue("Контраст ниже нормы ADR-0010:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `chart colors stand out on the card`() {
        // Ряды графиков «Аналитики» — нетекстовые элементы на карточке: не меньше 3:1 (ADR-0028)
        val failures = CHARTS.flatMap { (name, scheme, chart) ->
            CHART_SERIES.mapNotNull { series ->
                val color = series.get(chart)
                val ratio = contrast(color, scheme.surfaceContainerLow)
                if (ratio >= NON_TEXT_MINIMUM) return@mapNotNull null
                "$name: ${series.name} ${color.hex()} на карточке — ${"%.2f".format(RU, ratio)}:1"
            }
        }
        assertTrue("Цвет графика сливается с карточкой:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `neighboring chart colors differ under color blindness`() {
        val failures = CHARTS.flatMap { (name, _, chart) ->
            CHART_NEIGHBORS.flatMap { (first, second) ->
                VISIONS.mapNotNull { vision ->
                    val difference = difference(first.get(chart), second.get(chart), vision.matrix)
                    if (difference >= vision.minimum) return@mapNotNull null
                    "$name, ${vision.name}: ${first.name} и ${second.name} — ΔE ${"%.1f".format(RU, difference)}, " +
                        "нужно не меньше ${"%.0f".format(RU, vision.minimum)}"
                }
            }
        }
        assertTrue("Соседние ряды графика сливаются:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `running timer stands out among the cards`() {
        // «Классика» — прежняя зелёная тема, её оставили как есть (ADR-0032): тон акцента к карточке у неё 1,27:1
        val palettes = listOf(Triple("светлая", LightColors, false)) +
            (DarkPalette.entries - DarkPalette.CLASSIC).map { Triple("тёмная ${it.name}", darkColors(it), true) }
        val failures = palettes.mapNotNull { (name, scheme, dark) ->
            val fill = timerColors(scheme, dark).container
            val card = scheme.surfaceContainerLow
            val ratio = contrast(fill, card)
            if (ratio >= TIMER_MINIMUM) return@mapNotNull null
            "$name: заливка ${fill.hex()} на фоне карточки ${card.hex()} — ${"%.2f".format(RU, ratio)}:1"
        }
        assertTrue(
            "Идущий таймер теряется среди карточек, нужно не меньше ${"%.0f".format(RU, TIMER_MINIMUM)}:1:\n" +
                failures.joinToString("\n"),
            failures.isEmpty(),
        )
    }

    @Test
    fun `no role is left from the base Material scheme`() {
        val leftovers = PALETTES.flatMap { palette ->
            ROLES.filter { role ->
                val value = role.get(palette.colors)
                value == role.get(palette.base) && value !in HUELESS
            }.map { role -> "${palette.name}: ${role.name} ${role.get(palette.colors).hex()}" }
        }
        assertTrue(
            "Роли остались из базовой схемы Material 3 — задай их в Color.kt:\n" + leftovers.joinToString("\n"),
            leftovers.isEmpty(),
        )
    }

    @Test
    fun `surfaceTint equals surface`() {
        // Глубину карточкам даёт тень, тональный оттенок не нужен. Забытый surfaceTint тест выше не поймает:
        // lightColorScheme и darkColorScheme подставляют вместо него переданный primary, а не цвет базовой схемы
        PALETTES.forEach { palette ->
            assertEquals(
                "${palette.name}: surfaceTint должен равняться surface, иначе Surface с tonalElevation станет зелёным",
                palette.colors.surface.hex(),
                palette.colors.surfaceTint.hex(),
            )
        }
    }

    @Test
    fun `roles list covers every ColorScheme color`() {
        // Геттеры цвета в байткоде: getPrimary-0d7_KjU() и т. п. — у Color это value class поверх Long
        val schemeRoles = ColorScheme::class.java.methods
            .filter { it.parameterCount == 0 && it.returnType == Long::class.javaPrimitiveType && it.name.startsWith("get") }
            .map { it.name.removePrefix("get").substringBefore('-').replaceFirstChar(Char::lowercaseChar) }
            .toSet()
        val listed = ROLES.map { it.name }
        assertEquals("Роль повторяется в ROLES", listed.size, listed.toSet().size)
        assertTrue("Рефлексия не нашла геттеры цвета ColorScheme", schemeRoles.containsAll(listed))
        assertEquals(
            "Роли ColorScheme, которых нет в ROLES, — добавь их в ROLES и в обе палитры Color.kt",
            emptySet<String>(),
            schemeRoles - listed.toSet(),
        )
    }

    @Test
    fun `illness colors keep text and bars readable`() {
        val failures = CHARTS.flatMap { (name, scheme, _) ->
            val container = scheme.illnessContainer
            val band = scheme.illnessBand
            val checks = listOf(
                Triple("onTertiaryContainer на фоне болезни", contrast(scheme.onTertiaryContainer, container), TEXT_MINIMUM),
                Triple("tertiary на фоне болезни", contrast(scheme.tertiary, container), TEXT_MINIMUM),
                Triple("полоса болезни к карточке", contrast(band, scheme.surfaceContainerLow), BAND_MINIMUM),
            )
            checks.mapNotNull { (what, ratio, minimum) ->
                if (ratio >= minimum) null else "$name: $what — ${"%.2f".format(RU, ratio)}:1, нужно ${"%.2f".format(RU, minimum)}"
            }
        }
        assertTrue("Цвета болезни:\n" + failures.joinToString("\n"), failures.isEmpty())
    }
}

/** Контраст по WCAG 2: (L1 + 0,05) / (L2 + 0,05), L1 — относительная яркость более светлого цвета. */
private fun contrast(a: Color, b: Color): Double {
    require(a.alpha == 1f && b.alpha == 1f) { "Контраст считается только для непрозрачных цветов" }
    val la = a.luminance().toDouble()
    val lb = b.luminance().toDouble()
    return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
}

/** Цветовая разница ΔE в OKLab ×100 — какой её видит глаз с матрицей [vision] (null — обычное зрение). */
private fun difference(a: Color, b: Color, vision: List<DoubleArray>?): Double {
    val x = a.oklab(vision)
    val y = b.oklab(vision)
    return 100 * sqrt((x[0] - y[0]).pow(2) + (x[1] - y[1]).pow(2) + (x[2] - y[2]).pow(2))
}

/** Цвет в OKLab (Björn Ottosson, 2020) — каким его видит глаз с матрицей [vision] для линейного sRGB. */
private fun Color.oklab(vision: List<DoubleArray>?): DoubleArray {
    val linear = listOf(red, green, blue).map { decodeSrgb(it.toDouble()) }
    val (r, g, b) = vision?.map { row -> (row[0] * linear[0] + row[1] * linear[1] + row[2] * linear[2]).coerceIn(0.0, 1.0) }
        ?: linear
    val long = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
    val medium = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
    val short = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
    return doubleArrayOf(
        0.2104542553 * long + 0.7936177850 * medium - 0.0040720468 * short,
        1.9779984951 * long - 2.4285922050 * medium + 0.4505937099 * short,
        0.0259040371 * long + 0.7827717662 * medium - 0.8086757660 * short,
    )
}

/** Гамма-кодированный канал sRGB → линейный, оба 0–1. */
private fun decodeSrgb(encoded: Double): Double =
    if (encoded <= 0.04045) encoded / 12.92 else ((encoded + 0.055) / 1.055).pow(2.4)

/** `#RRGGBB` без альфы — для сообщений и сравнения с цветами из XML-ресурсов. */
internal fun Color.hex(): String = "#%06X".format(toArgb() and 0xFFFFFF)
