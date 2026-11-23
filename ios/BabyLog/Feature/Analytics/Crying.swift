import Foundation

/** Причин и «что помогло» на карточке — не больше стольких: дальше редкие, они только удлиняют список. */
private let topCryingItems = 5

/**
 Экран раздела «Плач» (ADR-0030): что было перед плачем и почему, по законченному плачу периода.
 Медианы, а не средние: пропущенная запись сна или кормления их почти не сдвигает.
 */
struct CryingModel: Sendable {
    /** Сколько раз плакал за период — из стольких считаны остальные числа. */
    var episodes: Int
    /** Бодрствование перед плачем и обычное окно бодрствования; nil — не из чего считать. */
    var awakeBefore: Int64?
    var usualAwake: Int64?
    /** У скольких раз из `episodes` известно бодрствование перед плачем: был сон не раньше чем за 6 ч. */
    var awakeEpisodes: Int
    /** Сколько раз из `awakeEpisodes` плач начинался после бодрствования дольше обычного. */
    var longerThanUsual: Int
    /** Сколько прошло после кормления и обычный промежуток между кормлениями. */
    var sinceFeeding: Int64?
    var feedingInterval: Int64?
    /** Метки и «что помогло»: сколько раз встречались, частые первыми. */
    var reasons: [(tag: String, count: Int)]
    var helped: [(tag: String, count: Int)]
}

/** Плач, начатый в `[from, to)`; nil — законченного плача в этот период нет. */
func cryingModel(events: [any BabyEvent], from: Int64, to: Int64) -> CryingModel? {
    let cryings = events.compactMap { $0 as? Crying }.filter { $0.end != nil && $0.start >= from && $0.start < to }
    guard !cryings.isEmpty else { return nil }
    let usual = events.awakeWindows(from: from, to: to).median()
    let awake = cryings.compactMap { crying -> Int64? in
        guard let last = events.lastSleepEndBefore(crying.start) else { return nil }
        let value = crying.start - last
        return value <= maxAwakeMillis ? value : nil
    }
    let sinceFeeding = cryings.compactMap { crying -> Int64? in
        guard let last = events.lastFeedingEndBefore(crying.start) else { return nil }
        let value = crying.start - last
        return value <= maxAwakeMillis ? value : nil
    }
    let feedingStarts = (feedingSessions(events: events).map(\.start) + events.compactMap { ($0 as? Bottle)?.start })
        .filter { $0 >= from && $0 < to }
        .sorted()
    let feedingGaps = zip(feedingStarts, feedingStarts.dropFirst()).map { $1 - $0 }

    return CryingModel(
        episodes: cryings.count,
        awakeBefore: awake.median(),
        usualAwake: usual,
        awakeEpisodes: awake.count,
        longerThanUsual: usual.map { usual in awake.count { Double($0) > Double(usual) * longerThanUsual } } ?? 0,
        sinceFeeding: sinceFeeding.median(),
        feedingInterval: feedingGaps.median(),
        reasons: countedCryingItems(cryings.flatMap(\.reasons)),
        helped: countedCryingItems(cryings.flatMap(\.helped))
    )
}

private func countedCryingItems(_ items: [String]) -> [(tag: String, count: Int)] {
    let counted: [(tag: String, count: Int)] = Dictionary(grouping: items, by: { $0 })
        .map { (tag: $0.key, count: $0.value.count) }
    let sorted = counted.sorted { $0.count == $1.count ? $0.tag < $1.tag : $0.count > $1.count }
    return Array(sorted.prefix(topCryingItems))
}

/** Подпись о доле случаев после слишком долгого бодрствования. */
func longerThanUsualText(_ longer: Int, _ episodes: Int) -> String {
    if longer == 0 { return "Ни разу плач не начался после бодрствования дольше обычного" }
    if longer == episodes { return "Каждый раз плач начинался после бодрствования дольше обычного" }
    return "В \(longer) из \(episodes) раз плач начался после бодрствования дольше обычного"
}

/** Плач за неделю одной строкой: `perDay` — «11 мин» в сутки, `note` — «0,7 раза в сутки · чаще с 19 до 22 ч». */
struct CryingWeekSummary: Hashable, Sendable {
    var perDay: String
    var note: String?
}

/**
 Плач за последние 7 суток для карточки над лентой «Заметок» (ADR-0031) — те же числа, что у плитки
 «Плач» за неделю. nil — плач не записывали или полных суток с записями за неделю нет.
 */
func cryingWeekSummary(events: [any BabyEvent], now: Int64, timeZone: TimeZone) -> CryingWeekSummary? {
    guard events.contains(where: { $0 is Crying }), let days = AnalyticsPeriod.week.days else { return nil }
    let today = toLocalDate(now, timeZone: timeZone)
    var calendar = Calendar(identifier: .gregorian)
    calendar.timeZone = timeZone
    let from = calendar.date(byAdding: .day, value: -(2 * days - 1), to: today)!
    let both = dailyStats(events: events, from: from, to: today, now: now, timeZone: timeZone)
    let week = Array(both.dropFirst(days))
    guard week.contains(where: \.countable) else { return nil }
    let model = metricModel(metric: .crying, period: .week, days: week, previous: Array(both.prefix(days)), sex: nil, timeZone: timeZone)
    return CryingWeekSummary(perDay: model.value, note: model.note)
}

/** «чаще с 18 до 21 ч» — когда начинался плач (`peakHours`); `starts` — минуты от полуночи. */
func cryingPeak(_ starts: [Int]) -> String? {
    peakHours(starts).map { "чаще с \($0.from) до \($0.to) ч" }
}
