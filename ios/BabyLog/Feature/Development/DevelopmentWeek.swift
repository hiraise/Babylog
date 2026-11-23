import Foundation

/**
 Что раздел «Развитие» показывает ребёнку на этой неделе жизни (ADR-0037).

 `tips` — советы недели без «когда показать педиатру», начавшиеся позже — сверху;
 `doctorTips` — «когда показать педиатру» для этого возраста;
 `milestones` — окна навыков ВОЗ в порядке таблицы и где ребёнок относительно каждого.
 */
struct DevelopmentWeek: Sendable {
    let ageDays: Int
    let week: Int
    let tips: [Tip]
    let doctorTips: [Tip]
    let milestones: [(window: MilestoneWindow, state: MilestoneState)]

    /** Все советы недели: обычные, потом «когда показать педиатру». */
    var allTips: [Tip] { tips + doctorTips }
}

/** Неделя ребёнка, родившегося `birth`; nil — дата рождения ещё не наступила. */
func developmentWeek(
    data: DevelopmentData,
    birth: Date,
    today: Date,
    timeZone: TimeZone = .current
) -> DevelopmentWeek? {
    guard let age = ageInDays(birth: birth, today: today, timeZone: timeZone) else { return nil }
    let forAge = tipsFor(data.tips, ageDays: age)
    return DevelopmentWeek(
        ageDays: age,
        week: weekOfLife(age),
        tips: forAge.filter { $0.kind == .tip },
        doctorTips: forAge.filter { $0.kind == .doctor },
        milestones: data.milestones.milestones.map { (window: $0, state: $0.stateAt(age)) }
    )
}

/**
 Есть ли советы этой недели, которые раздел ребёнку ещё не показывал: точка на пункте «Развитие»
 и «новые» на полосе советов. Отметку ставит открытие раздела (`DevelopmentViewModel.markSeen`).
 */
func hasNewTips(week: DevelopmentWeek?, childId: Int64, seenWeeks: [Int64: Int]) -> Bool {
    guard let week else { return false }
    return !week.allTips.isEmpty && seenWeeks[childId] != week.week
}

/** «21-я неделя»: порядковое числительное женского рода в именительном падеже. */
func weekTitle(_ week: Int) -> String {
    "\(week)-я неделя"
}

/** «на 21-ю неделю»: то же в винительном. */
func weekAccusative(_ week: Int) -> String {
    "\(week)-ю неделю"
}
