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
 * Дневной сон (ADR-0036) — тот же фиолетовый, но светлее ночного [sleep] во всех наборах: в столбике
 * они соседи, и различает их светлота. В тёмных темнее ночного нельзя — не будет 3:1 к карточке.
 * Контраст во всех палитрах проверяет `ThemeContrastTest`.
 *
 * Тёмных наборов два: спокойный [CalmDarkChartColors] — общий для спокойных палитр (ADR-0032),
 * и прежний [ClassicDarkChartColors] — для «Классики».
 *
 * Новый ряд — новое поле здесь и во всех трёх наборах, а не литерал в экране (ADR-0010).
 */
@Immutable
class ChartColors(
    /** Ночной сон и сон вообще: «засыпает на ночь», плитка «Сон». */
    val sleep: Color,
    /** Дневной сон (ADR-0036): светлее [sleep], лежит над ним в столбике «Сна». */
    val daySleep: Color,
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
    daySleep = Color(0xFF8D8BCF),
    feeding = Color(0xFF348333),
    diaperWet = Color(0xFF2A78D6),
    diaperMixed = Color(0xFFD55181),
    diaperDirty = Color(0xFFB37A00),
    weight = Color(0xFFEB6834),
    height = Color(0xFF0F8A6A),
    crying = Color(0xFFB5338A),
)

/**
 * Тёмный набор «Классики» — те же оттенки, что у светлой, но светлее для тёмной карточки; зелёный
 * темнее её `primary`, чтобы не светился.
 */
internal val ClassicDarkChartColors = ChartColors(
    sleep = Color(0xFF9085E9),
    daySleep = Color(0xFFC6C3FD),
    feeding = Color(0xFF3F9E3E),
    diaperWet = Color(0xFF3987E5),
    diaperMixed = Color(0xFFD55181),
    diaperDirty = Color(0xFFC98500),
    weight = Color(0xFFD95926),
    height = Color(0xFF199E70),
    crying = Color(0xFFE05A9C),
)

/**
 * Спокойный тёмный набор (ADR-0032): те же сущности, но хрома ниже — 0,10–0,13 в OKLCH против 0,14–0,17.
 * Плач — малиновый и темнее сна: при спокойной хроме одного оттенка не хватает, чтобы различить их
 * при дальтонизме. Соседи в столбике и полосе различимы: ΔE в OKLab не меньше 10 при протанопии
 * и дейтеранопии и не меньше 19 при обычном зрении.
 */
internal val CalmDarkChartColors = ChartColors(
    sleep = Color(0xFF868BD9),
    daySleep = Color(0xFFC0C6FC),
    feeding = Color(0xFF6C9F61),
    diaperWet = Color(0xFF5798CE),
    diaperMixed = Color(0xFFAE5A84),
    diaperDirty = Color(0xFFB48E34),
    weight = Color(0xFFCF8358),
    height = Color(0xFF46A499),
    crying = Color(0xFFAF4F71),
)

val LocalChartColors = staticCompositionLocalOf { LightChartColors }
