package com.kid.babylog.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
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

private val PALETTES = listOf(
    Palette("светлая", LightColors, lightColorScheme()),
    Palette("тёмная", DarkColors, darkColorScheme()),
)

/**
 * Белый и чёрный (тона 100 и 0) одинаковы в любой палитре, поэтому их совпадение с базовой схемой
 * не значит, что роль забыта: так совпадают `scrim` и белые `onPrimary`, `onSecondary`, `onTertiary`,
 * `onError`, `surfaceContainerLowest` светлой темы. Цветная роль из базовой схемы — фиолетовая.
 */
private val HUELESS = setOf(Color.White, Color.Black)

private val RU = Locale.forLanguageTag("ru")

/**
 * Правила палитры из ADR-0010: контраст пар «текст — фон», ни одной роли из базовой схемы
 * и `surfaceTint`, равный `surface`.
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
        val charts = listOf(Triple("светлая", LightColors, LightChartColors), Triple("тёмная", DarkColors, DarkChartColors))
        val failures = charts.flatMap { (name, scheme, chart) ->
            listOf(
                "sleep" to chart.sleep, "feeding" to chart.feeding, "diaperWet" to chart.diaperWet,
                "diaperMixed" to chart.diaperMixed, "diaperDirty" to chart.diaperDirty, "weight" to chart.weight,
                "height" to chart.height,
            ).mapNotNull { (series, color) ->
                val ratio = contrast(color, scheme.surfaceContainerLow)
                if (ratio >= NON_TEXT_MINIMUM) return@mapNotNull null
                "$name: $series ${color.hex()} на карточке — ${"%.2f".format(RU, ratio)}:1"
            }
        }
        assertTrue("Цвет графика сливается с карточкой:\n" + failures.joinToString("\n"), failures.isEmpty())
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
}

/** Контраст по WCAG 2: (L1 + 0,05) / (L2 + 0,05), L1 — относительная яркость более светлого цвета. */
private fun contrast(a: Color, b: Color): Double {
    require(a.alpha == 1f && b.alpha == 1f) { "Контраст считается только для непрозрачных цветов" }
    val la = a.luminance().toDouble()
    val lb = b.luminance().toDouble()
    return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
}

/** `#RRGGBB` без альфы — для сообщений и сравнения с цветами из XML-ресурсов. */
internal fun Color.hex(): String = "#%06X".format(toArgb() and 0xFFFFFF)
