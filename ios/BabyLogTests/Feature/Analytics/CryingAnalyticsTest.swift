import Foundation
import Testing
@testable import BabyLog

struct CryingAnalyticsTest {
    private let zone = TimeZone(identifier: "Europe/Moscow")!
    private var today: Date { calendar.date(from: DateComponents(year: 2026, month: 10, day: 18))! }
    private var yesterday: Date { calendar.date(byAdding: .day, value: -1, to: today)! }
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

    /** Вчера: окна бодрствования 2 ч, 1 ч, 1 ч и 3 ч — обычно 1 ч 30 мин; кормления после пробуждений. */
    private var routineDay: [any BabyEvent] {
        [
            Sleep(id: 1, childId: 1, start: at(yesterday, hour: 5), end: at(yesterday, hour: 7)),
            Sleep(id: 2, childId: 1, start: at(yesterday, hour: 9), end: at(yesterday, hour: 10)),
            Sleep(id: 3, childId: 1, start: at(yesterday, hour: 11), end: at(yesterday, hour: 12, minute: 30)),
            Sleep(id: 4, childId: 1, start: at(yesterday, hour: 13, minute: 30), end: at(yesterday, hour: 15)),
            Sleep(id: 5, childId: 1, start: at(yesterday, hour: 18), end: at(yesterday, hour: 19)),
            Feeding(id: 10, childId: 1, start: at(yesterday, hour: 7, minute: 10), end: at(yesterday, hour: 7, minute: 30), breast: .left),
            Feeding(id: 11, childId: 1, start: at(yesterday, hour: 10), end: at(yesterday, hour: 10, minute: 15), breast: .right),
            Feeding(id: 12, childId: 1, start: at(yesterday, hour: 12, minute: 30), end: at(yesterday, hour: 12, minute: 50), breast: .left),
            Feeding(id: 13, childId: 1, start: at(yesterday, hour: 15, minute: 30), end: at(yesterday, hour: 15, minute: 50), breast: .right),
        ]
    }

    private var cryings: [any BabyEvent] {
        [
            Crying(
                id: 20,
                childId: 1,
                start: at(yesterday, hour: 10, minute: 40),
                end: at(yesterday, hour: 10, minute: 50),
                reasons: ["колики"],
                helped: ["грудь"]
            ),
            Crying(
                id: 21,
                childId: 1,
                start: at(yesterday, hour: 17),
                end: at(yesterday, hour: 17, minute: 30),
                reasons: ["колики", "усталость"],
                helped: ["укачивание"]
            ),
        ]
    }

    private func model(events: [any BabyEvent]) -> AnalyticsModel {
        analyticsModel(events: events, period: .week, now: now, timeZone: zone, sex: .girl)
    }

    @Test func noCryingRecordsMeansNoCryingTile() {
        let model = model(events: routineDay)
        #expect(!model.metrics.contains { $0.metric == .crying })
        #expect(model.crying == nil)
    }

    @Test func cryingMinutesAreAveragedOverDiaryDays() {
        let onlyCrying = Crying(
            id: 22,
            childId: 1,
            start: at(calendar.date(byAdding: .day, value: -2, to: today)!, hour: 20),
            end: at(calendar.date(byAdding: .day, value: -2, to: today)!, hour: 21)
        )
        let crying = model(events: routineDay + cryings + [onlyCrying]).metrics.first { $0.metric == .crying }!
        #expect(crying.value == "40 мин")
        #expect(crying.note == "2,0 раза в сутки")
        #expect(crying.columns[5].caption == "сб, 17 окт.: плач 40 мин, 2 раза")
        #expect(crying.columns[4].caption == "пт, 16 окт.: записей нет")
        #expect(crying.maxLabel == "1 ч")
        #expect(crying.midLabel == "30 мин")
    }

    @Test func dayWithoutCryingAmongCryingDaysIsZeroMinutes() {
        let crying = model(events: routineDay + routineDay.map { shifted($0) } + cryings).metrics.first { $0.metric == .crying }!
        #expect(crying.value == "20 мин")
        #expect(crying.columns[4].caption == "пт, 16 окт.: плача нет")
        #expect(formatValue(.crying, value: 0) == "0 мин")
    }

    @Test func beforeCryingAwakeTimeAndTimeAfterFeedingAgainstUsual() {
        let crying = model(events: routineDay + cryings).crying!
        #expect(crying.episodes == 2)
        #expect(crying.awakeBefore == 80 * minute)
        #expect(crying.usualAwake == 90 * minute)
        #expect(crying.awakeEpisodes == 2)
        #expect(crying.longerThanUsual == 1)
        #expect(longerThanUsualText(1, 2) == "В 1 из 2 раз плач начался после бодрствования дольше обычного")
        #expect(longerThanUsualText(0, 2) == "Ни разу плач не начался после бодрствования дольше обычного")
        #expect(longerThanUsualText(2, 2) == "Каждый раз плач начинался после бодрствования дольше обычного")
        #expect(crying.sinceFeeding == 47 * minute + 30_000)
        #expect(crying.feedingInterval == 170 * minute)
        #expect(crying.reasons.map { "\($0.tag):\($0.count)" } == ["колики:2", "усталость:1"])
        #expect(crying.helped.map { "\($0.tag):\($0.count)" } == ["грудь:1", "укачивание:1"])
    }

    @Test func cryingStillGoingIsNotInCausesYet() {
        #expect(model(events: routineDay + [Crying(id: 30, childId: 1, start: at(today, hour: 11))]).crying == nil)
    }

    @Test func cryingPeakIsBusiestHoursTrimmedToWhereCryingStarted() {
        #expect(cryingPeak([18 * 60 + 30, 19 * 60, 20 * 60 + 10, 10 * 60]) == "чаще с 18 до 21 ч")
        #expect(cryingPeak([23 * 60 + 10, 23 * 60 + 50, 20]) == "чаще с 23 до 1 ч")
        #expect(cryingPeak([2 * 60, 8 * 60, 14 * 60, 20 * 60]) == nil)
        #expect(cryingPeak([19 * 60, 19 * 60 + 30]) == nil)
    }

    /** Та же запись сутками раньше — день режима без плача. */
    private func shifted(_ event: any BabyEvent) -> any BabyEvent {
        let day: Int64 = 24 * 60 * minute
        switch event {
        case let event as Sleep:
            return Sleep(id: event.id + 100, childId: event.childId, start: event.start - day, end: event.end.map { $0 - day })
        case let event as Feeding:
            return Feeding(id: event.id + 100, childId: event.childId, start: event.start - day, end: event.end.map { $0 - day }, breast: event.breast)
        default:
            Issue.record("в дне режима только сон и кормления")
            return event
        }
    }
}
