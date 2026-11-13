import Foundation
import Testing
@testable import BabyLog

struct DaySummaryTest {
    private let zone = TimeZone(identifier: "Europe/Moscow")!
    private var today: Date { makeDate(year: 2026, month: 10, day: 15) }
    private var yesterday: Date { calendar.date(byAdding: .day, value: -1, to: today)! }
    private var now: Int64 { at(today, hour: 12) }
    private let hour: Int64 = 3_600_000
    private let minute: Int64 = 60_000

    private var calendar: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = zone
        return calendar
    }

    private func makeDate(year: Int, month: Int, day: Int) -> Date {
        calendar.date(from: DateComponents(year: year, month: month, day: day))!
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

    private func fed(id: Int64, start: Int64, breast: Breast, minutes: Int = 15) -> Feeding {
        Feeding(id: id, childId: 1, start: start, end: start + Int64(minutes) * minute, breast: breast)
    }

    @Test func cryingLineAppearsOnlyOnDaysWithCrying() {
        #expect(summarizeDay(events: [fed(id: 1, start: at(today, hour: 8), breast: .left)], date: today, now: now, timeZone: zone).lines(sex: nil).count == 4)
        let events: [any BabyEvent] = [
            Crying(id: 2, childId: 1, start: at(today, hour: 9), end: at(today, hour: 9, minute: 24)),
            Crying(id: 3, childId: 1, start: at(today, hour: 11, minute: 50)),
            Crying(id: 4, childId: 1, start: at(yesterday, hour: 20), end: at(yesterday, hour: 20, minute: 30)),
        ]
        let summary = summarizeDay(events: events, date: today, now: now, timeZone: zone)
        #expect(summary.cryings == 2)
        #expect(summary.cryingMillis == 34 * minute)
        #expect(summary.lines(sex: nil).last?.shown == "Плач: 2 раза, 34 мин")
    }

    @Test func countsOnlyRecordsOfChosenDay() {
        let events: [any BabyEvent] = [
            fed(id: 1, start: at(today, hour: 8), breast: .left),
            fed(id: 2, start: at(today, hour: 11), breast: .right),
            fed(id: 3, start: at(yesterday, hour: 23), breast: .left),
            Diaper(id: 4, childId: 1, start: at(today, hour: 9), kind: .wet),
            Diaper(id: 5, childId: 1, start: at(today, hour: 10), kind: .wet),
            Diaper(id: 6, childId: 1, start: at(today, hour: 10, minute: 30), kind: .dirty),
        ]
        let summary = summarizeDay(events: events, date: today, now: now, timeZone: zone)
        #expect(summary.feedings == 2)
        #expect(summary.diapers == [.wet: 2, .dirty: 1])
        #expect(summary.diaperCount == 3)
        let diapers = summary.lines(sex: nil)[1]
        #expect(diapers.shown == "Подгузников: 3 (пописал×2 · покакал×1)")
        #expect(diapers.parts.map(\.icon) == ["ic_diaper_wet", "ic_diaper_dirty"])
    }

    @Test func summaryHasOnlyDayTotalsNotLastFeeding() {
        let events = [fed(id: 1, start: at(yesterday, hour: 23), breast: .right)]
        let summary = summarizeDay(events: events, date: today, now: now, timeZone: zone)
        #expect(summary.feedings == 0)
        #expect(summary.lines(sex: nil).first?.shown == "Кормлений: 0")
        #expect(summarizeDay(events: events, date: yesterday, now: now, timeZone: zone).feedings == 1)
    }

    @Test func switchingBreastIsOneFeeding() {
        let events = [
            fed(id: 1, start: at(today, hour: 10), breast: .left, minutes: 10),
            fed(id: 2, start: at(today, hour: 10, minute: 10), breast: .right, minutes: 5),
            fed(id: 3, start: at(today, hour: 8), breast: .right),
        ]
        let summary = summarizeDay(events: events, date: today, now: now, timeZone: zone)
        #expect(summary.feedings == 2)
        #expect(summary.lines(sex: nil).first?.shown == "Кормлений: 2")
    }

    @Test func runningFeedingIsCountedLikeFinishedOne() {
        let events: [any BabyEvent] = [
            fed(id: 1, start: at(today, hour: 11, minute: 40), breast: .left, minutes: 10),
            Feeding(id: 2, childId: 1, start: at(today, hour: 11, minute: 50), breast: .right),
        ]
        #expect(summarizeDay(events: events, date: today, now: now, timeZone: zone).lines(sex: nil).first?.shown == "Кормлений: 1")
    }

    @Test func oldFeedingWithoutDurationIsSeparateFeeding() {
        let start = at(today, hour: 9)
        let events = [
            Feeding(id: 1, childId: 1, start: start, end: start, breast: .left),
            fed(id: 2, start: at(today, hour: 9, minute: 30), breast: .right),
        ]
        #expect(summarizeDay(events: events, date: today, now: now, timeZone: zone).feedings == 2)
    }

    @Test func diapersOfGirlAreInFeminineForm() {
        let events: [any BabyEvent] = [
            Diaper(id: 1, childId: 1, start: at(today, hour: 9), kind: .wet),
            Diaper(id: 2, childId: 1, start: at(today, hour: 10), kind: .mixed),
        ]
        let line = summarizeDay(events: events, date: today, now: now, timeZone: zone).lines(sex: .girl)[1]
        #expect(line.shown == "Подгузников: 2 (пописала×1 · и то, и то×1)")
        #expect(line.parts.map(\.icon) == ["ic_diaper_wet", "ic_diaper_mixed"])
    }

    @Test func sleepAcrossMidnightIsSplitBetweenDays() {
        let events = [Sleep(id: 1, childId: 1, start: at(yesterday, hour: 22), end: at(today, hour: 6))]
        #expect(summarizeDay(events: events, date: yesterday, now: now, timeZone: zone).sleepMillis == 2 * hour)
        #expect(summarizeDay(events: events, date: today, now: now, timeZone: zone).sleepMillis == 6 * hour)
    }

    @Test func activeSleepCountsUntilNow() {
        let events = [Sleep(id: 1, childId: 1, start: at(today, hour: 10))]
        let summary = summarizeDay(events: events, date: today, now: now, timeZone: zone)
        #expect(summary.sleepMillis == 2 * hour)
        #expect(summary.lines(sex: nil)[2].shown == "Сон: 2 ч")
    }

    @Test func medicinesAreGroupedIgnoringCaseWithLatestName() {
        let events = [
            Medicine(id: 1, childId: 1, start: at(today, hour: 8), name: "витамин d"),
            Medicine(id: 2, childId: 1, start: at(today, hour: 9), name: "Витамин D "),
            Medicine(id: 3, childId: 1, start: at(today, hour: 10), name: "Нурофен"),
        ]
        let summary = summarizeDay(events: events, date: today, now: now, timeZone: zone)
        #expect(summary.medicines.map { $0.name } == ["Нурофен", "Витамин D"])
        #expect(summary.medicines.map { $0.count } == [1, 2])
        #expect(summary.lines(sex: nil).last?.shown == "Лекарств: 3 (Нурофен, Витамин D ×2)")
    }

    @Test func emptyDayShowsZerosWithoutBreakdowns() {
        let lines = summarizeDay(events: [], date: yesterday, now: now, timeZone: zone).lines(sex: nil)
        #expect(lines.map(\.shown) == ["Кормлений: 0", "Подгузников: 0", "Сон: 0 мин", "Лекарств: 0"])
    }

    @Test func bottlesCountAsFeedingsAndSplitFeedingLineByIcons() {
        let events: [any BabyEvent] = [
            fed(id: 1, start: at(today, hour: 8), breast: .left),
            Bottle(id: 2, childId: 1, start: at(today, hour: 10), volumeMl: 120, milk: .formula),
            Bottle(id: 3, childId: 1, start: at(today, hour: 11), volumeMl: 90),
            Bottle(id: 4, childId: 1, start: at(yesterday, hour: 22), volumeMl: 150),
        ]
        let summary = summarizeDay(events: events, date: today, now: now, timeZone: zone)
        #expect(summary.feedings == 3)
        #expect(summary.bottles == 2 && summary.bottleMl == 210)
        let line = summary.lines(sex: nil).first!
        #expect(line.shown == "Кормлений: 3 (грудью×1 · бутылочкой×2, 210\u{00A0}мл)")
        #expect(line.parts.map(\.icon) == ["ic_feeding", "ic_bottle"])
        let bottlesOnly = summarizeDay(events: Array(events.dropFirst()), date: today, now: now, timeZone: zone).lines(sex: nil).first!
        #expect(bottlesOnly.shown == "Кормлений: 2 (бутылочкой×2, 210\u{00A0}мл)")
    }

    @Test func temperatureLineShowsHighestOfDay() {
        let one = summarizeDay(
            events: [Temperature(id: 1, childId: 1, start: at(today, hour: 3, minute: 10), tenths: 392)],
            date: today,
            now: now,
            timeZone: zone
        )
        #expect(one.lines(sex: .girl).last == SummaryLine(icon: "ic_thermometer", label: "Температура:", value: "39,2"))
        let many = summarizeDay(
            events: [
                Temperature(id: 1, childId: 1, start: at(today, hour: 3, minute: 10), tenths: 392),
                Temperature(id: 2, childId: 1, start: at(today, hour: 7, minute: 30), tenths: 381),
                Temperature(id: 3, childId: 1, start: at(yesterday, hour: 22), tenths: 399),
            ],
            date: today,
            now: now,
            timeZone: zone
        )
        #expect(many.lines(sex: .girl).last == SummaryLine(icon: "ic_thermometer", label: "Температура:", value: "до 39,2 (2 раза)"))
        #expect(summarizeDay(events: [], date: today, now: now, timeZone: zone).lines(sex: .girl).count == 4)
    }
}
