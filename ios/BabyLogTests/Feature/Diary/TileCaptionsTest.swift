import Foundation
import Testing
@testable import BabyLog

/** Подписи плиток: окно бодрствования, последнее кормление, плач, подгузник и даты замеров. */
struct TileCaptionsTest {
    private let now: Int64 = 1_786_000_000_000
    private let minute: Int64 = 60_000
    private let utc = TimeZone(secondsFromGMT: 0)!

    @Test func captionsCountFromEndOfLastSleepAndFeeding() {
        #expect(awakeCaption(lastSleepEnd: now - 80 * minute, now: now) == "не спит 1 ч 20 мин")
        let start = now - 65 * minute
        #expect(afterFeedingCaption(
            lastFeeding: LastFeeding(start: start, end: now - 40 * minute, breast: .left),
            now: now,
            timeZone: utc
        ) == "левая в \(formatClock(start, timeZone: utc))\nзакончилось 40 мин назад")
        let bottle = now - 120 * minute
        #expect(afterFeedingCaption(
            lastFeeding: LastFeeding(start: bottle, end: bottle, breast: nil),
            now: now,
            timeZone: utc
        ) == "бутылочка в \(formatClock(bottle, timeZone: utc))\n2 ч назад")
        #expect(awakeCaption(lastSleepEnd: now - 10_000, now: now) == "не спит меньше минуты")
        let justEnded = afterFeedingCaption(
            lastFeeding: LastFeeding(start: start, end: now - 10_000, breast: .right),
            now: now,
            timeZone: utc
        )
        #expect(justEnded?.components(separatedBy: "\n").last == "закончилось только что")
    }

    @Test func cryingCaptionTellsWhenLastCryingEndedAndHowLongItWas() {
        #expect(cryingCaption(
            lastCrying: Crying(id: 1, childId: 1, start: now - 144 * minute, end: now - 120 * minute),
            now: now
        ) == "2 ч назад · 24 мин")
        #expect(cryingCaption(lastCrying: Crying(id: 1, childId: 1, start: now - 10 * minute), now: now) == nil)
        #expect(cryingCaption(
            lastCrying: Crying(id: 1, childId: 1, start: now - 25 * 60 * minute, end: now - 24 * 60 * minute),
            now: now
        ) == nil)
        #expect(cryingCaption(lastCrying: nil, now: now) == nil)
    }

    @Test func noCaptionWithoutFinishedRecordOrAfterDay() {
        #expect(awakeCaption(lastSleepEnd: nil, now: now) == nil)
        #expect(afterFeedingCaption(lastFeeding: nil, now: now, timeZone: utc) == nil)
        #expect(awakeCaption(lastSleepEnd: now - 24 * 60 * minute, now: now) == nil)
        let end = now - (24 * 60 - 1) * minute
        let caption = afterFeedingCaption(
            lastFeeding: LastFeeding(start: end - 15 * minute, end: end, breast: .left),
            now: now,
            timeZone: utc
        )
        #expect(caption?.components(separatedBy: "\n").last == "закончилось 23 ч 59 мин назад")
    }

    @Test func lastEndIgnoresRunningTimerAndTakesLatestEnd() {
        let events: [any BabyEvent] = [
            Sleep(id: 4, childId: 1, start: now - 10 * minute),
            Feeding(id: 3, childId: 1, start: now - 50 * minute, end: now - 30 * minute, breast: .right),
            Sleep(id: 2, childId: 1, start: now - 200 * minute, end: now - 90 * minute),
            Feeding(id: 1, childId: 1, start: now - 300 * minute, end: now - 280 * minute, breast: .left),
        ]
        let child = Child(id: 1, name: "Маша", birthDate: nil)
        let state = DiaryUiState.of(children: [child], child: child, events: events)
        #expect(state.lastSleepEnd == now - 90 * minute)
        #expect(state.lastFeeding == LastFeeding(start: now - 50 * minute, end: now - 30 * minute, breast: .right))
    }

    @Test func afterSwitchingBreastFeedingStartsAtFirstBreastAndEndsWithLast() {
        let events: [any BabyEvent] = [
            Feeding(id: 2, childId: 1, start: now - 50 * minute, end: now - 40 * minute, breast: .right),
            Feeding(id: 1, childId: 1, start: now - 60 * minute, end: now - 50 * minute, breast: .left),
            Bottle(id: 3, childId: 1, start: now - 120 * minute, volumeMl: 90),
        ]
        let child = Child(id: 1, name: "Маша", birthDate: nil)
        let state = DiaryUiState.of(children: [child], child: child, events: events)
        #expect(state.lastFeeding == LastFeeding(start: now - 60 * minute, end: now - 40 * minute, breast: .right))
    }

    @Test func diaperCaptionCountsFromLastChangeAndDisappearsAfterDay() {
        #expect(diaperCaption(lastDiaper: now - 130 * minute, now: now) == "2 ч 10 мин назад")
        #expect(diaperCaption(lastDiaper: now - 10_000, now: now) == "только что")
        #expect(diaperCaption(lastDiaper: nil, now: now) == nil)
        #expect(diaperCaption(lastDiaper: now - 24 * 60 * minute, now: now) == nil)
    }

    @Test func diaperIsOverdueAfterThreeHoursUnlessBabySleeps() {
        #expect(!diaperOverdue(lastDiaper: now - (3 * 60 - 1) * minute, sleeping: false, now: now))
        #expect(diaperOverdue(lastDiaper: now - 3 * 60 * minute, sleeping: false, now: now))
        #expect(!diaperOverdue(lastDiaper: now - 4 * 60 * minute, sleeping: true, now: now))
        #expect(!diaperOverdue(lastDiaper: now - 24 * 60 * minute, sleeping: false, now: now))
        #expect(!diaperOverdue(lastDiaper: nil, sleeping: false, now: now))
    }

    @Test func lastDiaperIsLatestChangeOfAnyKind() {
        let events: [any BabyEvent] = [
            Diaper(id: 3, childId: 1, start: now - 20 * minute, kind: .wet),
            Sleep(id: 2, childId: 1, start: now - 200 * minute, end: now - 90 * minute),
            Diaper(id: 1, childId: 1, start: now - 300 * minute, kind: .dirty),
        ]
        let child = Child(id: 1, name: "Маша", birthDate: nil)
        let state = DiaryUiState.of(children: [child], child: child, events: events)
        #expect(state.lastDiaper == now - 20 * minute)
    }

    @Test func measurementCaptionIsDateOfLastOne() {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = utc
        let todayDate = calendar.date(from: DateComponents(year: 2026, month: 10, day: 15, hour: 10))!
        let today = Int64(todayDate.timeIntervalSince1970 * 1000)
        let day: Int64 = 24 * 60 * minute
        #expect(measuredCaption(lastMeasured: today - 9 * 60 * minute, now: today, timeZone: utc) == "сегодня")
        #expect(measuredCaption(lastMeasured: today - day, now: today, timeZone: utc) == "вчера")
        #expect(measuredCaption(lastMeasured: today - 14 * day, now: today, timeZone: utc) == "1 окт.")
        #expect(measuredCaption(lastMeasured: today - 365 * day, now: today, timeZone: utc) == "15 окт. 2025")
        #expect(measuredCaption(lastMeasured: nil, now: today, timeZone: utc) == nil)
    }

    @Test func lastWeightAndHeightAreLatestOfTheirKind() {
        let events: [any BabyEvent] = [
            Height(id: 4, childId: 1, start: now - 10 * minute, millimeters: 630),
            Weight(id: 3, childId: 1, start: now - 20 * minute, grams: 6_400),
            Height(id: 2, childId: 1, start: now - 30 * 24 * 60 * minute, millimeters: 600),
            Weight(id: 1, childId: 1, start: now - 31 * 24 * 60 * minute, grams: 5_900),
        ]
        let child = Child(id: 1, name: "Маша", birthDate: nil)
        let state = DiaryUiState.of(children: [child], child: child, events: events)
        #expect(state.lastHeight?.millimeters == 630)
        #expect(state.lastWeight?.grams == 6_400)
    }
}
