import Foundation

private let daysInWeek = 7

/**
 Полных дней жизни на `today` (ADR-0037); nil — дата рождения ещё не наступила. День рождения — день 0.
 Дату рождения из `Child.birthDate` переводи в локальный день через `toLocalDate`.
 */
func ageInDays(birth: Date, today: Date, timeZone: TimeZone = .current) -> Int? {
    var calendar = Calendar(identifier: .gregorian)
    calendar.locale = RU
    calendar.firstWeekday = 2
    calendar.timeZone = timeZone
    let days = calendar.dateComponents(
        [.day],
        from: calendar.startOfDay(for: birth),
        to: calendar.startOfDay(for: today)
    ).day ?? 0
    return days >= 0 ? days : nil
}

/** Неделя жизни: первая — дни 0–6. */
func weekOfLife(_ ageDays: Int) -> Int {
    ageDays / daysInWeek + 1
}

/**
 Советы для возраста: те, чей диапазон включает `ageDays`. Сверху — начавшиеся позже (новее для ребёнка),
 «когда показать педиатру» — после остальных.
 */
func tipsFor(_ tips: [Tip], ageDays: Int) -> [Tip] {
    tips.enumerated()
        .filter { $0.element.ages.contains(ageDays) }
        .sorted { left, right in
            let leftDoctor = left.element.kind == .doctor
            let rightDoctor = right.element.kind == .doctor
            if leftDoctor != rightDoctor { return !leftDoctor }
            if left.element.ages.lowerBound != right.element.ages.lowerBound {
                return left.element.ages.lowerBound > right.element.ages.lowerBound
            }
            return left.offset < right.offset
        }
        .map { $0.element }
}
