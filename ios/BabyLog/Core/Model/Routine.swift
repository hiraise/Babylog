import Foundation

/** Бодрствование длиннее этого — пропущенная запись сна, а не бодрствование. */
let maxAwakeMillis: Int64 = 6 * 60 * 60_000
/** Бодрствование настолько длиннее обычного — «дольше обычного». */
let longerThanUsual = 1.15
private let usualAwakeDays: Int64 = 7
private let dayMillis: Int64 = 86_400_000

extension Collection where Element == any BabyEvent {
    /** Конец последнего законченного сна не позже `at`; nil — в `at` ребёнок спал или сна не было. */
    func lastSleepEndBefore(_ at: Int64) -> Int64? {
        let sleeps = compactMap { $0 as? Sleep }
        if sleeps.contains(where: { $0.start <= at && ($0.end ?? Int64.max) > at }) { return nil }
        return sleeps.compactMap(\.end).filter { $0 <= at }.max()
    }

    /** Промежутки бодрствования, начавшиеся в `[from, to)` и не длиннее `maxAwakeMillis`. */
    func awakeWindows(from: Int64, to: Int64) -> [Int64] {
        let sleeps = compactMap { $0 as? Sleep }
            .compactMap { sleep in sleep.end.map { (sleep.start, $0) } }
            .sorted { $0.0 < $1.0 }
        guard sleeps.count > 1 else { return [] }
        return (0..<(sleeps.count - 1)).compactMap { index in
            let end = sleeps[index].1
            let nextStart = sleeps[index + 1].0
            guard end >= from, end < to, nextStart > end, nextStart - end <= maxAwakeMillis else { return nil }
            return nextStart - end
        }
    }

    /** Конец последнего кормления не позже `at`: у груди — конец, у бутылочки — её время. */
    func lastFeedingEndBefore(_ at: Int64) -> Int64? {
        let feeding = compactMap { $0 as? Feeding }.compactMap(\.end)
        let bottles = compactMap { $0 as? Bottle }.map(\.start)
        return (feeding + bottles).filter { $0 <= at }.max()
    }

    /** Обычное бодрствование перед `at`: медиана окон за неделю до него. */
    func usualAwakeBefore(_ at: Int64) -> Int64? {
        awakeWindows(from: at - usualAwakeDays * dayMillis, to: at).median()
    }
}

/** Часы суток `[from, to)` (0–23, `to` может перейти через полночь), где начались `count` случаев. */
struct PeakHours: Hashable, Sendable {
    var from: Int
    var to: Int
    var count: Int
}

/** До `window` часов подряд с наибольшим числом начал; nil — данных для закономерности мало. */
func peakHours(_ startMinutes: [Int], window: Int = 4) -> PeakHours? {
    if startMinutes.count < 3 { return nil }
    var byHour = Array(repeating: 0, count: 24)
    for minutes in startMinutes { byHour[(minutes / 60) % 24] += 1 }
    var best: [Int] = []
    var bestCount = -1
    for from in 0..<24 {
        let range = (0..<window).map { (from + $0) % 24 }
        let count = range.reduce(0) { $0 + byHour[$1] }
        if count > bestCount {
            best = range
            bestCount = count
        }
    }
    let hours = best.filter { byHour[$0] > 0 }
    guard let first = hours.first, let last = hours.last else { return nil }
    let count = hours.reduce(0) { $0 + byHour[$1] }
    return count * 2 >= startMinutes.count ? PeakHours(from: first, to: (last + 1) % 24, count: count) : nil
}

extension Collection where Element == Int64 {
    /** Медиана: пропущенная или лишняя запись её почти не сдвигает, в отличие от среднего. */
    func median() -> Int64? {
        let sorted = self.sorted()
        guard !sorted.isEmpty else { return nil }
        let middle = sorted.count / 2
        return sorted.count % 2 == 1 ? sorted[middle] : (sorted[middle - 1] + sorted[middle]) / 2
    }
}
