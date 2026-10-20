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
 * с «покакал» (по цветовой разнице в OKLab на моделях протанопии и дейтеранопии).
 * Поэтому у бутылочки своего цвета нет: оранжевый и красный рядом с зелёным для дальтоника сливаются.
 * Плач (ADR-0030) в «Ритме дня» соседствует со сном и кормлением — поэтому пурпурный: красный
 * и оранжевый рядом с зелёным не различить, а от фиолетового сна его отделяет и светлота.
 * Дневной сон (ADR-0036) — тот же фиолетовый, но светлее ночного [sleep] во всех наборах: в столбике
 * они соседи, и различает их светлота. В тёмных темнее ночного нельзя — не будет 3:1 к карточке.
 * Контраст и различимость соседей во всех палитрах проверяет `ThemeContrastTest`; новый график
 * поставил рядом другие ряды — добавь их пару в его `CHART_NEIGHBORS`.
 *
 * Тёмных наборов два: спокойный [CalmDarkChartColors] — общий для спокойных палитр (ADR-0032),
 * и прежний [ClassicDarkChartColors] — для «Классики».
 *
 * Окна навыков ВОЗ (ADR-0037): окно, в котором ребёнок сейчас, — [milestone], остальные — [milestoneOther].
 * Это соседи на одном графике, их различает и светлота: зелёный против серого.
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
    /** Окно навыка ВОЗ, в котором ребёнок сейчас (ADR-0037); в каждом наборе — как [feeding]. */
    val milestone: Color,
    /** Окна навыков, до которых ребёнок не дорос или которые прошли: серый той же заметности. */
    val milestoneOther: Color,
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
    milestone = Color(0xFF348333),
    milestoneOther = Color(0xFF8C8C8C),
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
    milestone = Color(0xFF3F9E3E),
    milestoneOther = Color(0xFF9AA3AD),
)

/**
 * Спокойный тёмный набор (ADR-0032): те же сущности, хрома 0,12–0,16 в OKLCH — на ступень ниже
 * «Классики» (0,14–0,17), но достаточно, чтобы ряды не сливались с цветным фоном палитры.
 * Плач — малиновый и темнее сна: одного оттенка не хватает, чтобы различить их при дальтонизме.
 * Соседи в столбике и полосе различимы: ΔE в OKLab не меньше 10 при протанопии и дейтеранопии
 * и не меньше 21 при обычном зрении.
 */
internal val CalmDarkChartColors = ChartColors(
    sleep = Color(0xFF8387EA),
    daySleep = Color(0xFFC0C6FC),
    feeding = Color(0xFF5FA350),
    diaperWet = Color(0xFF3D99DF),
    diaperMixed = Color(0xFFC0558D),
    diaperDirty = Color(0xFFB98C06),
    weight = Color(0xFFD8783D),
    height = Color(0xFF02A99B),
    crying = Color(0xFFBE4673),
    milestone = Color(0xFF5FA350),
    milestoneOther = Color(0xFF9AA2AA),
)

val LocalChartColors = staticCompositionLocalOf { LightChartColors }
