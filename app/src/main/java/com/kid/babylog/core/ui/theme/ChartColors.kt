package com.kid.babylog.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Цвета рядов графиков «Аналитики» (ADR-0028). Цвет — у сущности, а не у места на графике: сон
 * везде фиолетовый, кормление — зелёное, и так на всех карточках вкладки.
 *
 * Каждый цвет даёт не меньше 3:1 к фону карточки, а соседние в одном столбике или полосе
 * различимы и при дальтонизме: кормление рядом со сном, «пописал» — с «и то, и то», «и то, и то» —
 * с «покакал» (проверено по цветовой разнице в OKLab на моделях протанопии и дейтеранопии).
 * Поэтому у бутылочки своего цвета нет: оранжевый и красный рядом с зелёным для дальтоника сливаются.
 * Плач (ADR-0030) в «Ритме дня» соседствует со сном и кормлением — поэтому пурпурный: красный
 * и оранжевый рядом с зелёным не различить, а от фиолетового сна его отделяет и светлота.
 * Контраст проверяет `ThemeContrastTest`.
 *
 * Новый ряд — новое поле здесь и в обеих палитрах, а не литерал в экране (ADR-0010).
 */
@Immutable
class ChartColors(
    val sleep: Color,
    /** Кормление грудью и бутылочкой. В светлой теме совпадает с `primary`. */
    val feeding: Color,
    val diaperWet: Color,
    val diaperMixed: Color,
    val diaperDirty: Color,
    val weight: Color,
    val height: Color,
    val crying: Color,
)

internal val LightChartColors = ChartColors(
    sleep = Color(0xFF6250D6),
    feeding = Color(0xFF348333),
    diaperWet = Color(0xFF2A78D6),
    diaperMixed = Color(0xFFD55181),
    diaperDirty = Color(0xFFB37A00),
    weight = Color(0xFFEB6834),
    height = Color(0xFF0F8A6A),
    crying = Color(0xFFB5338A),
)

/** Тёмная палитра — те же оттенки, но светлее для тёмной карточки; зелёный темнее `primary`, чтобы не светился. */
internal val DarkChartColors = ChartColors(
    sleep = Color(0xFF9085E9),
    feeding = Color(0xFF3F9E3E),
    diaperWet = Color(0xFF3987E5),
    diaperMixed = Color(0xFFD55181),
    diaperDirty = Color(0xFFC98500),
    weight = Color(0xFFD95926),
    height = Color(0xFF199E70),
    crying = Color(0xFFE05A9C),
)

val LocalChartColors = staticCompositionLocalOf { LightChartColors }
