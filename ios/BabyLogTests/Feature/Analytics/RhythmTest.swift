import Foundation
import Testing
@testable import BabyLog

struct RhythmTest {
    private let zone = TimeZone(identifier: "Europe/Moscow")!
    private var today: Date { calendar.date(from: DateComponents(year: 2026, month: 10, day: 18))! }

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

    private func rhythm(events: [any BabyEvent]) -> RhythmModel {
        let stats = dailyStats(events: events, from: day(rhythmDays - 1), to: today, now: now, timeZone: zone)
        return rhythmModel(events: events, stats: stats, now: now, timeZone: zone)
    }

    /** Ночь на сутки `date`: засыпание накануне, пробуждения в 01:30 и 04:40, подъём утром. */
    private func night(
        _ date: Date,
        bedHour: Int,
        bedMinute: Int,
        wakeHour: Int,
        wakeMinute: Int,
        firstId: Int64
    ) -> [Sleep] {
        let evening = bedHour >= 12 ? calendar.date(byAdding: .day, value: -1, to: date)! : date
        return [
            Sleep(id: firstId, childId: 1, start: at(evening, hour: bedHour, minute: bedMinute), end: at(date, hour: 1, minute: 30)),
            Sleep(id: firstId + 1, childId: 1, start: at(date, hour: 1, minute: 50), end: at(date, hour: 4, minute: 40)),
            Sleep(id: firstId + 2, childId: 1, start: at(date, hour: 5), end: at(date, hour: wakeHour, minute: wakeMinute)),
        ]
    }

    @Test func nightJoinsSleepsWithShortWakingsAndCountsThem() {
        let result = nights(
            events: night(day(1), bedHour: 21, bedMinute: 30, wakeHour: 6, wakeMinute: 45, firstId: 1),
            dates: [day(1)],
            now: now,
            timeZone: zone
        )
        #expect(result.count == 1)
        #expect(result[0].start == at(day(2), hour: 21, minute: 30))
        #expect(result[0].end == at(day(1), hour: 6, minute: 45))
        #expect(result[0].wakings == 2)
    }

    @Test func bedtimeAroundMidnightAveragesToMidnightNotNoon() {
        let events = night(day(1), bedHour: 23, bedMinute: 50, wakeHour: 7, wakeMinute: 0, firstId: 1)
            + night(day(2), bedHour: 0, bedMinute: 10, wakeHour: 7, wakeMinute: 0, firstId: 10)
            + night(day(3), bedHour: 0, bedMinute: 0, wakeHour: 7, wakeMinute: 0, firstId: 20)
        #expect(rhythm(events: events).summary == "Засыпает на ночь ≈ 00:00, просыпается ≈ 07:00, самый долгий сон — 2 ч 50 мин")
    }

    @Test func fewerThanThreeNightsShowsOnlyLongestSleep() {
        let events = night(day(1), bedHour: 21, bedMinute: 0, wakeHour: 6, wakeMinute: 0, firstId: 1)
        #expect(rhythm(events: events).summary == "Самый долгий сон — 4 ч 30 мин")
    }

    @Test func nightStillGoingIsNotCounted() {
        let events = [
            Sleep(id: 1, childId: 1, start: at(day(1), hour: 21, minute: 30), end: at(today, hour: 2)),
            Sleep(id: 2, childId: 1, start: at(today, hour: 2, minute: 20)),
        ]
        #expect(nights(events: events, dates: [today], now: at(today, hour: 4), timeZone: zone).isEmpty)
    }

    @Test func instantFeedingsAreTicksAndTodayEndsNow() {
        let events: [any BabyEvent] = [
            Feeding(id: 1, childId: 1, start: at(today, hour: 7), end: at(today, hour: 7, minute: 30), breast: .left),
            Feeding(id: 2, childId: 1, start: at(day(1), hour: 9), end: at(day(1), hour: 9), breast: .right),
            Bottle(id: 3, childId: 1, start: at(today, hour: 10), volumeMl: 120),
            Diaper(id: 4, childId: 1, start: at(today, hour: 8), kind: .wet),
        ]
        let rows = rhythm(events: events).days
        let todayRow = rows[0]
        let yesterdayRow = rows[1]
        #expect(todayRow.label == "сегодня")
        #expect(todayRow.feedings == [DaySpan(from: 7 / 24, to: 7.5 / 24)])
        #expect(todayRow.instantFeedings == [10 / 24])
        #expect(todayRow.diapers == [8 / 24])
        #expect(abs(todayRow.end - Float(11 * 60 + 20) / 1440) < 0.0001)
        #expect(yesterdayRow.instantFeedings == [9 / 24])
        #expect(yesterdayRow.end == 1)
        #expect(yesterdayRow.label == "сб 17")
    }

    @Test func activeSleepIsDrawnUpToNow() {
        let model = rhythm(events: [Sleep(id: 1, childId: 1, start: at(today, hour: 10))])
        let sleep = model.days[0].daySleeps[0]
        #expect(sleep.from == Float(10) / 24)
        #expect(abs(sleep.to - Float(11 * 60 + 20) / 1440) < 0.0001)
        #expect(model.summary == nil)
    }

    @Test func nightSleepAndNapsAreDrawnApart() {
        let events = night(day(1), bedHour: 21, bedMinute: 30, wakeHour: 6, wakeMinute: 45, firstId: 1)
            + [
                Sleep(id: 10, childId: 1, start: at(day(1), hour: 10), end: at(day(1), hour: 11, minute: 30)),
                Sleep(id: 11, childId: 1, start: at(day(1), hour: 18, minute: 30), end: at(day(1), hour: 19, minute: 30)),
            ]
        let model = rhythm(events: events)
        let yesterday = model.days[1]
        let nightStarts: [Float] = [0, 110 / 1440, 300 / 1440, 19 / 24]
        #expect(yesterday.nightSleeps.map(\.from) == nightStarts)
        #expect(yesterday.daySleeps.map(\.from) == [10.0 / 24, 18.5 / 24])
        #expect(yesterday.daySleeps.map(\.to) == [11.5 / 24, 19.0 / 24])
        #expect(model.days[2].nightSleeps == [DaySpan(from: 21.5 / 24, to: 1)])
        #expect(model.days[2].daySleeps.isEmpty)
    }
}
