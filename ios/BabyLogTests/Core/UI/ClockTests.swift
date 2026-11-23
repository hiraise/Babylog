import Foundation
import Testing
@testable import BabyLog

struct ClockTests {
    @Test func millisecondsMatchAndroidStorageConvention() {
        let clock = TestClock(date: Date(timeIntervalSince1970: 1_700_000_000.125))

        #expect(clock.millis() == 1_700_000_000_125)
    }

    @Test func calendarUsesRussianGregorianConvention() {
        let timeZone = TimeZone(identifier: "Europe/Moscow")!
        let calendar = TestClock(timeZone: timeZone).calendar()

        #expect(calendar.identifier == .gregorian)
        #expect(calendar.locale?.identifier == "ru_RU")
        #expect(calendar.firstWeekday == 2)
        #expect(calendar.timeZone == timeZone)
    }
}
