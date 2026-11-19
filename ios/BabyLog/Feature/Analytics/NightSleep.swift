import Foundation

/**
 Ночь — с этого часа вечера до `nightEndHour` утра, день — остальное (ADR-0036), как в опроснике
 детского сна BISQ; источники — `SLEEP_SOURCES`.
 */
let nightStartHour = 19
let nightEndHour = 7

typealias AnalyticsRange = (start: Int64, end: Int64)

/**
 Отрезок `[start, end)` по частям: что пришлось на ночь (с 19:00 до 7:00) и что на день. Сон через
 границу делится: с 18:30 до 20:00 — 30 мин днём и 1 ч ночью. Части — по порядку, `night` — ночные.
 */
func splitByNight(start: Int64, end: Int64, timeZone: TimeZone) -> (night: [AnalyticsRange], day: [AnalyticsRange]) {
    var night: [AnalyticsRange] = []
    var day: [AnalyticsRange] = []
    guard end > start else { return (night, day) }
    var calendar = Calendar(identifier: .gregorian)
    calendar.timeZone = timeZone

    func at(_ date: Date, hour: Int) -> Int64 {
        let components = calendar.dateComponents([.year, .month, .day], from: date)
        let value = calendar.date(from: DateComponents(
            year: components.year,
            month: components.month,
            day: components.day,
            hour: hour
        ))!
        return Int64(value.timeIntervalSince1970 * 1000)
    }

    // Ночь, начавшаяся вечером накануне, может захватить утро первого дня отрезка.
    var date = calendar.date(byAdding: .day, value: -1, to: toLocalDate(start, timeZone: timeZone))!
    var cursor = start
    while cursor < end {
        let nextDate = calendar.date(byAdding: .day, value: 1, to: date)!
        let nightStart = at(date, hour: nightStartHour)
        let nightEnd = at(nextDate, hour: nightEndHour)
        if cursor < nightStart {
            let segmentEnd = min(end, nightStart)
            day.append((cursor, segmentEnd))
            cursor = segmentEnd
        }
        if cursor >= nightStart && cursor < nightEnd {
            let segmentEnd = min(end, nightEnd)
            night.append((cursor, segmentEnd))
            cursor = segmentEnd
        }
        date = nextDate
    }
    return (night, day)
}
