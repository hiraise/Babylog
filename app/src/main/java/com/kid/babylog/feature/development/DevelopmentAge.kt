package com.kid.babylog.feature.development

import java.time.LocalDate
import java.time.temporal.ChronoUnit

private const val DAYS_IN_WEEK = 7

/**
 * Полных дней жизни на [today] (ADR-0037); null — дата рождения ещё не наступила. День рождения — день 0.
 * Дату рождения из `Child.birthDate` переводи в [LocalDate] в поясе устройства (`toLocalDate`).
 */
fun ageInDays(birth: LocalDate, today: LocalDate): Int? =
    ChronoUnit.DAYS.between(birth, today).takeIf { it >= 0 }?.toInt()

/** Неделя жизни: первая — дни 0–6. */
fun weekOfLife(ageDays: Int): Int = ageDays / DAYS_IN_WEEK + 1

/**
 * Советы для возраста: те, чей диапазон включает [ageDays]. Сверху — начавшиеся позже (новее для ребёнка),
 * «когда показать педиатру» — после остальных.
 */
fun tipsFor(tips: List<Tip>, ageDays: Int): List<Tip> =
    tips.filter { ageDays in it.ages }
        .sortedWith(compareBy<Tip> { it.kind == TipKind.DOCTOR }.thenByDescending { it.ages.first })
