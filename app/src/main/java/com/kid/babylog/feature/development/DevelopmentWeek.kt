package com.kid.babylog.feature.development

import java.time.LocalDate

/**
 * Что раздел «Развитие» показывает ребёнку на этой неделе жизни (ADR-0037).
 *
 * @param tips советы недели без «когда показать педиатру», начавшиеся позже — сверху.
 * @param doctorTips «когда показать педиатру» для этого возраста.
 * @param milestones окна навыков ВОЗ в порядке таблицы и где ребёнок относительно каждого.
 */
data class DevelopmentWeek(
    val ageDays: Int,
    val week: Int,
    val tips: List<Tip>,
    val doctorTips: List<Tip>,
    val milestones: List<Pair<MilestoneWindow, MilestoneState>>,
) {
    /** Все советы недели: обычные, потом «когда показать педиатру». */
    val allTips: List<Tip> get() = tips + doctorTips
}

/** Неделя ребёнка, родившегося [birth]; null — дата рождения ещё не наступила. */
fun developmentWeek(data: DevelopmentData, birth: LocalDate, today: LocalDate): DevelopmentWeek? {
    val age = ageInDays(birth, today) ?: return null
    val forAge = tipsFor(data.tips, age)
    return DevelopmentWeek(
        ageDays = age,
        week = weekOfLife(age),
        tips = forAge.filter { it.kind == TipKind.TIP },
        doctorTips = forAge.filter { it.kind == TipKind.DOCTOR },
        milestones = data.milestones.milestones.map { it to it.stateAt(age) },
    )
}

/**
 * Есть ли советы этой недели, которые раздел ребёнку ещё не показывал: точка на пункте «Развитие»
 * и «новые» на полосе советов. Отметку ставит открытие раздела ([DevelopmentViewModel.markSeen]).
 */
fun hasNewTips(week: DevelopmentWeek?, childId: Long, seenWeeks: Map<Long, Int>): Boolean =
    week != null && week.allTips.isNotEmpty() && seenWeeks[childId] != week.week

/** «21-я неделя»: порядковое числительное женского рода в именительном падеже. */
fun weekTitle(week: Int): String = "$week-я неделя"

/** «на 21-ю неделю»: то же в винительном. */
fun weekAccusative(week: Int): String = "$week-ю неделю"
