import Foundation
import Testing
@testable import BabyLog

struct AnalyticsModelTest {
    private let zone = TimeZone(identifier: "Europe/Moscow")!
    private var today: Date { makeDate(year: 2026, month: 10, day: 18) }
    private let minute: Int64 = 60_000

    private var calendar: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.locale = RU
        calendar.firstWeekday = 2
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

    private var now: Int64 { at(today, hour: 11, minute: 20) }
    private func day(_ ago: Int) -> Date { calendar.date(byAdding: .day, value: -ago, to: today)! }

    private func fed(id: Int64, start: Int64, minutes: Int = 15) -> Feeding {
        Feeding(id: id, childId: 1, start: start, end: start + Int64(minutes) * minute, breast: .left)
    }

    private func model(events: [any BabyEvent], period: AnalyticsPeriod = .week) -> AnalyticsModel {
        analyticsModel(events: events, period: period, now: now, timeZone: zone, sex: .girl)
    }

    @Test func noRecordsShowsHintInsteadOfCharts() {
        let model = model(events: [])
        #expect(!model.hasRecords)
        #expect(model.metrics.isEmpty)
    }

    @Test func averagesSkipTodayAndDaysWithoutRecords() {
        // Три полных дня с 2, 4 и 6 кормлениями, сегодня — 10: среднее 4, а не (2+4+6+10)/7.
        var id: Int64 = 1
        let events = [(ago: 1, count: 2), (ago: 2, count: 4), (ago: 4, count: 6), (ago: 0, count: 10)]
            .flatMap { item -> [any BabyEvent] in
                (0..<item.count).map { index in
                    let event = fed(id: id, start: at(day(item.ago), hour: 1 + index))
                    id += 1
                    return event
                }
            }
        let feedings = model(events: events).metric(.feedings)
        #expect(feedings.value == "4,0")
        #expect(feedings.unit == "в сутки")
        #expect(feedings.columns.last!.partial)
        #expect(feedings.columns[3].parts.isEmpty)
        #expect(feedings.columns[3].caption == "чт, 15 окт.: записей нет")
        #expect(feedings.columns.map(\.label) == ["пн", "вт", "ср", "чт", "пт", "сб", "вс"])
    }

    @Test func comparisonWithPreviousWeekNeedsThreeFullDaysThere() {
        var id: Int64 = 1
        let thisWeek = (1...3).flatMap { ago -> [any BabyEvent] in
            (0..<8).map { index in
                let event = fed(id: id, start: at(day(ago), hour: 1 + index * 2))
                id += 1
                return event
            }
        }
        let twoDaysBefore = (8...9).flatMap { ago -> [any BabyEvent] in
            (0..<6).map { index in
                let event = fed(id: id, start: at(day(ago), hour: 1 + index * 2))
                id += 1
                return event
            }
        }
        #expect(model(events: thisWeek + twoDaysBefore).metric(.feedings).delta == nil)
        let thirdDay = (0..<6).map { index -> any BabyEvent in
            let event = fed(id: id, start: at(day(10), hour: 1 + index * 2))
            id += 1
            return event
        }
        #expect(model(events: thisWeek + twoDaysBefore + thirdDay).metric(.feedings).delta == "+2,0 к прошлой неделе")
    }

    @Test func breastTimeShowsOnlyDaysWhereEveryFeedingHasDuration() {
        let events: [any BabyEvent] = [
            fed(id: 1, start: at(day(1), hour: 8), minutes: 20),
            fed(id: 2, start: at(day(1), hour: 11), minutes: 20),
            fed(id: 3, start: at(day(2), hour: 8), minutes: 30),
            Feeding(id: 4, childId: 1, start: at(day(3), hour: 8), end: at(day(3), hour: 8), breast: .right),
            Feeding(id: 5, childId: 1, start: at(day(3), hour: 12), end: at(day(3), hour: 12), breast: .right),
        ]
        let note = model(events: events).metric(.feedings).note ?? ""
        #expect(note.hasSuffix("у груди 35 мин в сутки по 2 дням из 3"))
    }

    @Test func noBreastTimeAtAllWhenNoFeedingHasDuration() {
        let events = (0..<6).map { index -> any BabyEvent in
            let start = at(day(1 + index / 3), hour: 8 + index)
            return Feeding(id: Int64(index + 1), childId: 1, start: start, end: start, breast: .left)
        }
        let note = model(events: events).metric(.feedings).note ?? ""
        #expect(!note.contains("у груди"))
    }

    @Test func typicalIntervalIsMedianGapBetweenFeedings() {
        // Промежутки 3, 3, 3 и 9 часов (пропущенная ночная запись) — медиана 3 ч.
        let events = [6, 9, 12, 15].map { fed(id: Int64($0), start: at(day(1), hour: $0)) }
            + [fed(id: 30, start: at(day(2), hour: 15)), fed(id: 31, start: at(day(2), hour: 18))]
        let note = model(events: events).metric(.feedings).note ?? ""
        #expect(note.hasPrefix("обычно раз в 3 ч"))
    }

    @Test func bottleCardAppearsOnlyWithBottlesInPeriod() {
        #expect(!model(events: [fed(id: 1, start: at(day(1), hour: 8))]).metrics.contains { $0.metric == .bottles })
        let bottles = model(events: [
            Bottle(id: 1, childId: 1, start: at(day(1), hour: 8), volumeMl: 120),
            Bottle(id: 2, childId: 1, start: at(day(1), hour: 20), volumeMl: 150),
        ]).metric(.bottles)
        #expect(bottles.value == "270 мл")
        #expect(bottles.note == "2,0 бутылочки в сутки")
    }

    @Test func diaperLegendUsesChildSex() {
        let events: [any BabyEvent] = [
            Diaper(id: 1, childId: 1, start: at(day(1), hour: 8), kind: .wet),
            Diaper(id: 2, childId: 1, start: at(day(1), hour: 9), kind: .wet),
            Diaper(id: 3, childId: 1, start: at(day(1), hour: 10), kind: .dirty),
        ]
        let diapers = model(events: events).metric(.diapers)
        #expect(diapers.legend.map(\.label) == ["пописала", "и то, и то", "покакала"])
        #expect(diapers.legend.map(\.text) == ["2,0", "0,0", "1,0"])
        #expect(diapers.columns[5].caption == "сб, 17 окт.: 3 подгузника (пописала 2, покакала 1)")
    }

    @Test func sleepTellsHowOftenItHappensLikeFeedings() {
        let events = [(from: 1, to: 6), (from: 9, to: 10), (from: 12, to: 13), (from: 15, to: 16)]
            .enumerated().map { index, hours in
                Sleep(id: Int64(index + 1), childId: 1, start: at(day(1), hour: hours.from), end: at(day(1), hour: hours.to))
            }
        let sleep = model(events: events).metric(.sleep)
        #expect(sleep.note == "4,0 раза в сутки · обычно раз в 3 ч · самый долгий сон — 5 ч")
        #expect(sleep.columns[5].caption == "сб, 17 окт.: сон 8 ч (ночью 5 ч, днём 3 ч), 4 раза, самый долгий 5 ч")
    }

    @Test func sleepScaleIsRoundedToWholeFourHours() {
        let sleep = model(events: [Sleep(id: 1, childId: 1, start: at(day(1), hour: 0), end: at(day(1), hour: 13))]).metric(.sleep)
        #expect(sleep.maxLabel == "16 ч")
        #expect(sleep.midLabel == "8 ч")
        #expect(sleep.value == "13 ч")
    }

    @Test func allTimeWithFutureRoutineRecordsEndsToday() {
        let events = [fed(id: 1, start: at(calendar.date(byAdding: .day, value: 9, to: today)!, hour: 8)),
                      fed(id: 2, start: at(calendar.date(byAdding: .day, value: 10, to: today)!, hour: 8))]
        let feedings = model(events: events, period: .all).metric(.feedings)
        #expect(feedings.columns.count == 1)
        #expect(feedings.columns[0].partial)
    }

    @Test func allTimeIsWeeklyFromMondayOfFirstRoutineRecord() {
        var events: [any BabyEvent] = (0..<30).map { ago in
            fed(id: Int64(ago + 1), start: at(day(ago), hour: 8))
        }
        events.append(Weight(id: 100, childId: 1, start: at(makeDate(year: 2026, month: 5, day: 1), hour: 10), grams: 3500))
        let feedings = model(events: events, period: .all).metric(.feedings)
        #expect(feedings.columns.first!.caption.hasPrefix("неделя с 14 сент."))
        #expect(feedings.columns.count == 5)
        #expect(feedings.columns.last!.partial)
        #expect(feedings.delta == "по неделям")
    }

    @Test func medicinesAreCountedForPeriodWithLatestSpelling() {
        let events: [any BabyEvent] = [
            Medicine(id: 1, childId: 1, start: at(day(20), hour: 9), name: "витамин d"),
            Medicine(id: 2, childId: 1, start: at(day(1), hour: 7, minute: 20), name: "витамин d"),
            Medicine(id: 3, childId: 1, start: at(day(0), hour: 7, minute: 20), name: "Витамин D"),
            Diaper(id: 4, childId: 1, start: at(day(1), hour: 8), kind: .wet),
        ]
        #expect(model(events: events).medicines == [
            MedicineLine(name: "Витамин D", info: "2 приёма · последний сегодня в 07:20", count: 2, last: "последний сегодня в 07:20"),
        ])
        #expect(model(events: events, period: .all).medicines[0].info == "3 приёма · последний сегодня в 07:20")
    }

    @Test func weightGainUsesWeighingAtLeastTwoWeeksEarlier() {
        let events: [any BabyEvent] = [
            Weight(id: 1, childId: 1, start: at(day(36), hour: 10), grams: 5850),
            Weight(id: 2, childId: 1, start: at(day(14), hour: 10), grams: 6080),
            Weight(id: 3, childId: 1, start: at(day(4), hour: 10), grams: 6350),
        ]
        let weight = model(events: events).weight!
        #expect(weight.value == "6,35 кг")
        #expect(weight.date == "14 окт.")
        #expect(weight.note == "+110 г в неделю с 12 сент.")
        #expect(weight.rate == "+110 г в неделю")
    }

    @Test func measurementsAreNewestFirstWithChangeToPrevious() {
        let events: [any BabyEvent] = [
            Weight(id: 1, childId: 1, start: at(day(36), hour: 10), grams: 5850),
            Weight(id: 2, childId: 1, start: at(day(14), hour: 10), grams: 6080),
            Weight(id: 3, childId: 1, start: at(day(4), hour: 10), grams: 6350),
            Height(id: 4, childId: 1, start: at(day(30), hour: 11), millimeters: 600),
            Height(id: 5, childId: 1, start: at(day(10), hour: 11), millimeters: 600),
        ]
        #expect(model(events: events).weight!.history == [
            GrowthEntry(date: "14 окт.", value: "6,35 кг", change: "+270 г"),
            GrowthEntry(date: "4 окт.", value: "6,08 кг", change: "+230 г"),
            GrowthEntry(date: "12 сент.", value: "5,85 кг", change: nil),
        ])
        #expect(model(events: events).height!.history[0].change == "±0")
    }

    @Test func heightGainIsPerMonthAgainstMeasurementFourWeeksEarlier() {
        let events: [any BabyEvent] = [
            Height(id: 1, childId: 1, start: at(day(91), hour: 11), millimeters: 575),
            Height(id: 2, childId: 1, start: at(day(30), hour: 11), millimeters: 600),
            Height(id: 3, childId: 1, start: at(day(10), hour: 11), millimeters: 625),
        ]
        let height = model(events: events).height!
        #expect(height.value == "62,5 см")
        #expect(height.note == "+1,9 см в месяц с 19 июл.")
        #expect(height.yTicks.map(\.label) == ["56", "58", "60", "62", "64 см"])
        #expect(model(events: [Diaper(id: 1, childId: 1, start: at(day(1), hour: 8), kind: .wet)]).height == nil)
    }

    @Test func sleepShowsNightAndDayAveragesAndStacksThem() {
        let events: [any BabyEvent] = [
            Sleep(id: 1, childId: 1, start: at(day(2), hour: 0), end: at(day(2), hour: 6)),
            Sleep(id: 2, childId: 1, start: at(day(2), hour: 13), end: at(day(2), hour: 16)),
            Sleep(id: 3, childId: 1, start: at(day(2), hour: 20), end: at(day(1), hour: 0)),
            Sleep(id: 4, childId: 1, start: at(day(1), hour: 0), end: at(day(1), hour: 5)),
            Sleep(id: 5, childId: 1, start: at(day(1), hour: 12), end: at(day(1), hour: 16)),
            Sleep(id: 6, childId: 1, start: at(day(1), hour: 21), end: at(today, hour: 0)),
        ]
        let sleep = model(events: events).metric(.sleep)
        #expect(sleep.value == "12 ч 30 мин")
        #expect(sleep.legend.map(\.series) == [.nightSleep, .daySleep])
        #expect(sleep.legend.map(\.text) == ["ночью 9 ч", "днём 3 ч 30 мин"])
        #expect(sleep.columns[5].parts.map(\.series) == [.nightSleep, .daySleep])
        #expect(sleep.columns[5].parts.map(\.value) == [8, 4])
        #expect(sleep.columns[4].caption == "пт, 16 окт.: сон 13 ч (ночью 10 ч, днём 3 ч), 3 раза, самый долгий 6 ч")
    }

    @Test func weeklySleepCaptionTellsNightAndDay() {
        let events = (1...14).flatMap { ago -> [any BabyEvent] in
            [
                Sleep(id: Int64(ago * 3), childId: 1, start: at(day(ago), hour: 0), end: at(day(ago), hour: 6)),
                Sleep(id: Int64(ago * 3 + 1), childId: 1, start: at(day(ago), hour: 13), end: at(day(ago), hour: 16)),
                Sleep(id: Int64(ago * 3 + 2), childId: 1, start: at(day(ago), hour: 21), end: at(day(ago - 1), hour: 0)),
            ]
        }
        let sleep = model(events: events, period: .all).metric(.sleep)
        #expect(sleep.columns[0].caption.hasSuffix("в среднем 12 ч в сутки (ночью 9 ч, днём 3 ч)"))
    }
}

private extension AnalyticsModel {
    func metric(_ metric: Metric) -> MetricModel {
        metrics.first { $0.metric == metric }!
    }
}
