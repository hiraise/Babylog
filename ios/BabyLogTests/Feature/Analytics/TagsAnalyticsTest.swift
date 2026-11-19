import Foundation
import Testing
@testable import BabyLog

struct TagsAnalyticsTest {
    private let zone = TimeZone(identifier: "Europe/Moscow")!
    private var today: Date { calendar.date(from: DateComponents(year: 2026, month: 10, day: 18))! }
    private let minute: Int64 = 60_000

    private var calendar: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.locale = RU
        calendar.firstWeekday = 2
        calendar.timeZone = zone
        return calendar
    }

    private func at(_ date: Date, hour: Int, minute: Int = 0) -> Int64 {
        let components = calendar.dateComponents([.year, .month, .day], from: date)
        let value = calendar.date(from: DateComponents(
            year: components.year,
            month: components.month,
            day: components.day,
            hour: hour,
            minute: minute
        ))!
        return Int64(value.timeIntervalSince1970 * 1000)
    }

    private var now: Int64 { at(today, hour: 11, minute: 20) }
    private func day(_ ago: Int) -> Date { calendar.date(byAdding: .day, value: -ago, to: today)! }

    private func model(events: [any BabyEvent], period: AnalyticsPeriod = .week) -> AnalyticsModel {
        analyticsModel(events: events, period: period, now: now, timeZone: zone, sex: .girl)
    }

    @Test func noTagsAndNoNotesMeansNoTagsTile() {
        let event = Feeding(id: 1, childId: 1, start: at(day(1), hour: 8), end: at(day(1), hour: 8, minute: 15), breast: .left)
        #expect(model(events: [event]).tags == nil)
    }

    @Test func tagsAreCountedOverPeriodByDays() {
        let events: [any BabyEvent] = [
            Crying(id: 1, childId: 1, start: at(day(1), hour: 19), end: at(day(1), hour: 19, minute: 20), reasons: ["колики"]),
            Crying(id: 2, childId: 1, start: at(day(3), hour: 20), end: at(day(3), hour: 20, minute: 30), reasons: ["колики"]),
            Note(id: 3, childId: 1, start: at(day(1), hour: 21), tags: ["зубки"], note: "Тёрла дёсны"),
            Note(id: 4, childId: 1, start: at(day(2), hour: 9), note: "Без меток"),
        ]
        let tags = model(events: events).tags!
        #expect(tags.cryings == 2)
        #expect(tags.notes == 2)
        #expect(tags.counts() == "плач 2 раза · 2 заметки")
        #expect(tags.frequencies.map(\.tag) == ["колики", "зубки"])
        #expect(tags.frequencies.map(\.count) == [2, 1])
        #expect(tags.frequencies[0].marks == [false, false, false, true, false, true, false])
        #expect(tags.untaggedNotes == 1)
    }

    @Test func routineComparesDaysWithNoteTagAgainstDaysWithout() {
        // 12 полных суток: с «зубками» сон 10 ч и плач 40 мин, без них — 12 ч и 10 мин.
        let events = (1...12).flatMap { ago -> [any BabyEvent] in
            let teeth = ago <= 3
            var events: [any BabyEvent] = [
                Sleep(id: Int64(ago * 10), childId: 1, start: at(day(ago), hour: 6), end: at(day(ago), hour: teeth ? 16 : 18)),
                Crying(
                    id: Int64(ago * 10 + 1),
                    childId: 1,
                    start: at(day(ago), hour: 20),
                    end: at(day(ago), hour: 20) + Int64(teeth ? 40 : 10) * minute
                ),
            ]
            if teeth {
                events.append(Note(id: Int64(ago * 10 + 2), childId: 1, start: at(day(ago), hour: 21), tags: ["зубки"]))
            }
            if ago == 5 {
                events.append(Note(id: Int64(ago * 10 + 3), childId: 1, start: at(day(ago), hour: 10), tags: ["прививка"]))
            }
            return events
        }
        let tags = model(events: events, period: .month).tags!
        #expect(tags.routine[0] == TagRoutine(tag: "зубки", days: 3, sleep: "сон −2 ч", crying: "плач +30 мин"))
        #expect(tags.routine[1] == TagRoutine(tag: "прививка", days: 1, sleep: nil, crying: nil))
    }
}
