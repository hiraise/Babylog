import Foundation

/// Часы для UI («сейчас», таймеры). Подменяются в тестах; в приложении — из `AppContainer`.
protocol Clock: Sendable {
    var timeZone: TimeZone { get }
    func now() -> Date
}

extension Clock {
    func millis() -> Int64 {
        Int64(now().timeIntervalSince1970 * 1000)
    }

    func calendar() -> Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.locale = Locale(identifier: "ru_RU")
        calendar.firstWeekday = 2
        calendar.timeZone = timeZone
        return calendar
    }
}

struct SystemClock: Clock {
    var timeZone: TimeZone { .current }

    func now() -> Date {
        Date()
    }
}
