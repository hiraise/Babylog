import Foundation
import Testing
@testable import BabyLog

struct DailyStatsTest {
    private let zone = TimeZone(identifier: "Europe/Moscow")!
    private var today: Date { makeDate(year: 2026, month: 10, day: 18) }
    private var yesterday: Date { calendar().date(byAdding: .day, value: -1, to: today)! }
    private let minute: Int64 = 60_000
    private let hour: Int64 = 60 * 60_000

    private func calendar(_ timeZone: TimeZone? = nil) -> Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.locale = RU
        calendar.firstWeekday = 2
        calendar.timeZone = timeZone ?? zone
        return calendar
    }

    private func makeDate(year: Int, month: Int, day: Int, timeZone: TimeZone? = nil) -> Date {
        calendar(timeZone).date(from: DateComponents(year: year, month: month, day: day))!
    }

    private func at(_ date: Date, hour: Int, minute: Int = 0, timeZone: TimeZone? = nil) -> Int64 {
        let calendar = calendar(timeZone)
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
    private func day(_ ago: Int) -> Date { calendar().date(byAdding: .day, value: -ago, to: today)! }

    @Test func everyDayMatchesDaySummary() {
        // Сводка «Истории» и столбик одного дня не должны расходиться (ADR-0028).
        let events = (0..<20).flatMap { ago -> [any BabyEvent] in
            let date = day(ago)
            let previous = day(ago + 1)
            let id = Int64(ago * 10)
            return [
                Feeding(id: id + 1, childId: 1, start: at(date, hour: 7), end: at(date, hour: 7, minute: 20), breast: .left),
                Bottle(id: id + 2, childId: 1, start: at(date, hour: 10), volumeMl: 100 + ago),
                Sleep(id: id + 3, childId: 1, start: at(previous, hour: 22), end: at(date, hour: 6)),
                Sleep(id: id + 4, childId: 1, start: at(date, hour: 13), end: at(date, hour: 14)),
                Diaper(id: id + 5, childId: 1, start: at(date, hour: 9), kind: ago % 2 == 0 ? .wet : .dirty),
            ]
        }
        let stats = dailyStats(events: events, from: day(19), to: today, now: now, timeZone: zone)
        #expect(stats.count == 20)
        for day in stats {
            let summary = summarizeDay(events: events, date: day.date, now: now, timeZone: zone)
            #expect(summary.feedings == day.feedings)
            #expect(summary.bottles == day.bottles)
            #expect(summary.bottleMl == day.bottleMl)
            #expect(summary.diapers == day.diapers)
            #expect(summary.sleepMillis == day.sleepMillis)
            #expect(day.sleepMillis == day.nightSleepMillis + day.daySleepMillis)
        }
    }

    @Test func sleepAcrossMidnightIsSplitBetweenDays() {
        let events = [Sleep(id: 1, childId: 1, start: at(yesterday, hour: 22), end: at(today, hour: 6))]
        let stats = dailyStats(events: events, from: yesterday, to: today, now: now, timeZone: zone)
        #expect(stats[0].sleepMillis == 2 * hour)
        #expect(stats[1].sleepMillis == 6 * hour)
        #expect(stats[0].longestSleepMillis == 8 * hour)
        #expect(stats[1].longestSleepMillis == 0)
    }

    @Test func springClockChangeDayHas23Hours() {
        let berlin = TimeZone(identifier: "Europe/Berlin")!
        let change = makeDate(year: 2026, month: 3, day: 29, timeZone: berlin)
        let previous = calendar(berlin).date(byAdding: .day, value: -1, to: change)!
        let next = calendar(berlin).date(byAdding: .day, value: 1, to: change)!
        let events = [Sleep(id: 1, childId: 1, start: at(previous, hour: 22, timeZone: berlin), end: at(change, hour: 6, timeZone: berlin))]
        let stats = dailyStats(events: events, from: previous, to: change, now: at(next, hour: 12, timeZone: berlin), timeZone: berlin)
        #expect(stats[0].sleepMillis == 2 * hour)
        #expect(stats[1].sleepMillis == 5 * hour)
    }

    @Test func activeSleepCountsUntilNow() {
        let events = [Sleep(id: 1, childId: 1, start: at(today, hour: 10))]
        #expect(dailyStats(events: events, from: today, to: today, now: now, timeZone: zone)[0].sleepMillis == 80 * minute)
    }

    @Test func dayWithoutRoutineRecordsIsNotTracked() {
        let events: [any BabyEvent] = [
            Weight(id: 1, childId: 1, start: at(yesterday, hour: 10), grams: 6350),
            Medicine(id: 2, childId: 1, start: at(yesterday, hour: 11), name: "Витамин D"),
            Diaper(id: 3, childId: 1, start: at(today, hour: 9), kind: .wet),
        ]
        let stats = dailyStats(events: events, from: yesterday, to: today, now: now, timeZone: zone)
        #expect(!stats[0].tracked)
        #expect(stats[1].tracked)
        #expect(stats[0].complete)
        #expect(!stats[1].complete)
        #expect(!stats[1].countable)
    }

    @Test func breastTimeIsUnknownWhenFeedingHasNoDuration() {
        let events: [any BabyEvent] = [
            Feeding(id: 1, childId: 1, start: at(yesterday, hour: 8), end: at(yesterday, hour: 8, minute: 15), breast: .left),
            Feeding(id: 2, childId: 1, start: at(yesterday, hour: 11), end: at(yesterday, hour: 11), breast: .right),
            Feeding(id: 3, childId: 1, start: at(today, hour: 8), end: at(today, hour: 8, minute: 20), breast: .left),
            Feeding(id: 4, childId: 1, start: at(today, hour: 8, minute: 20), end: at(today, hour: 8, minute: 30), breast: .right),
        ]
        let stats = dailyStats(events: events, from: yesterday, to: today, now: now, timeZone: zone)
        #expect(stats[0].breastMillis == nil)
        #expect(stats[0].feedings == 2)
        #expect(stats[1].feedings == 1)
        #expect(stats[1].breastMillis == 30 * minute)
    }

    @Test func bottlesCountAsFeedingsAndTheirStartsAsFeedingStarts() {
        let events: [any BabyEvent] = [
            Feeding(id: 1, childId: 1, start: at(today, hour: 7), end: at(today, hour: 7, minute: 15), breast: .left),
            Bottle(id: 2, childId: 1, start: at(today, hour: 10), volumeMl: 120),
        ]
        let day = dailyStats(events: events, from: today, to: today, now: now, timeZone: zone)[0]
        #expect(day.feedings == 2)
        #expect(day.bottleMl == 120)
        #expect(day.feedingStarts == [at(today, hour: 7), at(today, hour: 10)])
        #expect(day.breastMillis == 15 * minute)
    }

    @Test func sleepIsSplitIntoNightAndDay() {
        let events: [any BabyEvent] = [
            Sleep(id: 1, childId: 1, start: at(yesterday, hour: 13), end: at(yesterday, hour: 15)),
            Sleep(id: 2, childId: 1, start: at(yesterday, hour: 18, minute: 30), end: at(yesterday, hour: 20)),
            Sleep(id: 3, childId: 1, start: at(yesterday, hour: 21), end: at(today, hour: 2)),
            Sleep(id: 4, childId: 1, start: at(today, hour: 2, minute: 30), end: at(today, hour: 6, minute: 30)),
            Sleep(id: 5, childId: 1, start: at(today, hour: 6, minute: 45), end: at(today, hour: 8)),
            Sleep(id: 6, childId: 1, start: at(today, hour: 10)),
        ]
        let stats = dailyStats(events: events, from: yesterday, to: today, now: now, timeZone: zone)
        #expect(stats[0].nightSleepMillis == 4 * hour)
        #expect(stats[0].daySleepMillis == 150 * minute)
        #expect(stats[1].nightSleepMillis == 6 * hour + 15 * minute)
        #expect(stats[1].daySleepMillis == 140 * minute)
    }

    @Test func nightBoundariesFollowClockChange() {
        let berlin = TimeZone(identifier: "Europe/Berlin")!
        let change = makeDate(year: 2026, month: 3, day: 29, timeZone: berlin)
        let previous = calendar(berlin).date(byAdding: .day, value: -1, to: change)!
        let split = splitByNight(
            start: at(previous, hour: 18, timeZone: berlin),
            end: at(change, hour: 8, timeZone: berlin),
            timeZone: berlin
        )
        #expect(split.night.count == 1)
        #expect(split.night[0].start == at(previous, hour: 19, timeZone: berlin))
        #expect(split.night[0].end == at(change, hour: 7, timeZone: berlin))
        #expect(split.day.reduce(Int64(0)) { $0 + $1.end - $1.start } == 2 * hour)
    }

    @Test func illnessDaysAreMarkedFromStartToEndAndGoingOneToToday() {
        let events: [any BabyEvent] = [
            Illness(id: 1, childId: 1, start: at(day(6), hour: 19, minute: 30), end: at(day(4), hour: 9)),
            Illness(id: 2, childId: 1, start: at(yesterday, hour: 23)),
            Temperature(id: 3, childId: 1, start: at(yesterday, hour: 23, minute: 10), tenths: 384),
        ]
        let stats = dailyStats(events: events, from: day(7), to: today, now: now, timeZone: zone)
        #expect(stats.map(\.ill) == [false, true, true, true, false, false, true, true])
        #expect(!stats.contains { $0.tracked })
    }
}
