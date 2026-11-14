import Foundation

/** «В дни болезни и без» — только если тех и других полных суток с записями не меньше стольких, как у меток (ADR-0031). */
private let minIllnessDaysToCompare = 3

/**
 «Болела: 7–14 окт. и с 18 окт.» — болезни (ADR-0040), захватившие сутки периода с `first` по `last`;
 nil — не болел(а). Идущая — «с 18 окт.».
 */
func illnessNote(events: [any BabyEvent], first: Date, last: Date, today: Date, sex: Sex?, timeZone: TimeZone) -> String? {
    let ranges = events.compactMap { $0 as? Illness }
        .filter { $0.startDate(timeZone) <= last && $0.lastDate(today: today, timeZone: timeZone) >= first }
        .sorted { $0.start < $1.start }
        .map { illness -> String in
            let start = illness.startDate(timeZone)
            if illness.isActive { return "с \(formatDayRange(first: start, last: start, timeZone: timeZone))" }
            return formatDayRange(first: start, last: illness.lastDate(today: today, timeZone: timeZone), timeZone: timeZone)
        }
    guard !ranges.isEmpty else { return nil }
    let joined = ranges.count == 1 ? ranges[0] : ranges.dropLast().joined(separator: ", ") + " и " + ranges.last!
    return "\(bySex(sex, masculine: "Болел", feminine: "Болела")): \(joined)"
}

/** Сон в сутки в дни болезни и без: средние и по скольким суткам; `illLabel` — «болел» или «болела». */
struct IllnessSleep: Hashable, Sendable {
    var illMillis: Int64
    var illDays: Int
    var healthyMillis: Int64
    var healthyDays: Int
    var illLabel: String
}

/**
 Сон «в дни болезни и без» (ADR-0040) по полным суткам с записям `days`; nil — тех или других меньше
 `minIllnessDaysToCompare`. Дни болезни из средних не убираются — это сравнение рядом, а не поправка.
 */
func illnessSleep(_ days: [DayStats], sex: Sex?) -> IllnessSleep? {
    let countable = days.filter(\.countable)
    let ill = countable.filter(\.ill)
    let healthy = countable.filter { !$0.ill }
    guard ill.count >= minIllnessDaysToCompare, healthy.count >= minIllnessDaysToCompare else { return nil }
    func average(_ values: [Int64]) -> Int64 {
        Int64((values.reduce(0.0) { $0 + Double($1) } / Double(values.count) + 0.5).rounded(.down))
    }
    return IllnessSleep(
        illMillis: average(ill.map(\.sleepMillis)),
        illDays: ill.count,
        healthyMillis: average(healthy.map(\.sleepMillis)),
        healthyDays: healthy.count,
        illLabel: bySex(sex, masculine: "болел", feminine: "болела")
    )
}
