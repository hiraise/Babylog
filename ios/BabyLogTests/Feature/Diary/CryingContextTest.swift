import Foundation
import Testing
@testable import BabyLog

struct CryingContextTest {
    private let zone = TimeZone(identifier: "Europe/Moscow")!
    private let minute: Int64 = 60_000
    private var day: Date {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = zone
        return calendar.date(from: DateComponents(year: 2026, month: 10, day: 18))!
    }

    private func at(_ hour: Int, _ minute: Int = 0, date: Date? = nil) -> Int64 {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = zone
        let day = date ?? self.day
        let components = calendar.dateComponents([.year, .month, .day], from: day)
        let value = calendar.date(from: DateComponents(
            year: components.year,
            month: components.month,
            day: components.day,
            hour: hour,
            minute: minute
        ))!
        return Int64(value.timeIntervalSince1970 * 1000)
    }

    @Test func beforeCryingHasAwakeTimeWholeFeedingWithBreastSwitchAndDiaper() {
        let crying = Crying(id: 10, childId: 1, start: at(16))
        let events: [any BabyEvent] = [
            Sleep(id: 1, childId: 1, start: at(9), end: at(10)),
            Sleep(id: 2, childId: 1, start: at(11), end: at(12)),
            Sleep(id: 3, childId: 1, start: at(13, 30), end: at(14)),
            Feeding(id: 4, childId: 1, start: at(14, 10), end: at(14, 25), breast: .left),
            Feeding(id: 5, childId: 1, start: at(14, 25), end: at(14, 40), breast: .right),
            Diaper(id: 6, childId: 1, start: at(15), kind: .wet),
            crying,
        ]
        let context = cryingContext(events: events, crying: crying)
        #expect(context.awakeMillis == 120 * minute)
        #expect(context.usualAwakeMillis == 75 * minute)
        #expect(context.sinceFeedingMillis == 80 * minute)
        #expect(context.feeding == "правая, 30 мин")
        #expect(!context.feedingByBottle)
        #expect(context.sinceDiaperMillis == 60 * minute)
        #expect(context.diaper == .wet)
    }

    @Test func bottleAfterBreastIsLastFeeding() {
        let crying = Crying(id: 10, childId: 1, start: at(16))
        let events: [any BabyEvent] = [
            Feeding(id: 1, childId: 1, start: at(12), end: at(12, 20), breast: .left),
            Bottle(id: 2, childId: 1, start: at(15, 30), volumeMl: 120),
            crying,
        ]
        let context = cryingContext(events: events, crying: crying)
        #expect(context.feedingByBottle)
        #expect(context.feeding == "бутылочка 120\u{00A0}мл")
        #expect(context.sinceFeedingMillis == 30 * minute)
    }

    @Test func recordsOlderThanSixHoursAreForgottenAndCryingInSleepHasNoAwakeTime() {
        let crying = Crying(id: 10, childId: 1, start: at(20))
        let events: [any BabyEvent] = [
            Sleep(id: 1, childId: 1, start: at(8), end: at(9)),
            Feeding(id: 2, childId: 1, start: at(9), end: at(9, 20), breast: .left),
            Diaper(id: 3, childId: 1, start: at(9, 30), kind: .dirty),
            crying,
        ]
        let context = cryingContext(events: events, crying: crying)
        #expect(context.awakeMillis == nil)
        #expect(context.sinceFeedingMillis == nil)
        #expect(context.feeding == nil)
        #expect(context.sinceDiaperMillis == nil)
        #expect(context.diaper == nil)

        let inSleep = Crying(id: 11, childId: 1, start: at(8, 30))
        #expect(cryingContext(events: events + [inSleep], crying: inSleep).awakeMillis == nil)
    }
}
