import Foundation
@testable import BabyLog

struct TestClock: Clock {
    let date: Date
    let timeZone: TimeZone

    init(
        date: Date = Date(timeIntervalSince1970: 1_700_000_000),
        timeZone: TimeZone = TimeZone(identifier: "Europe/Moscow")!
    ) {
        self.date = date
        self.timeZone = timeZone
    }

    func now() -> Date {
        date
    }
}
