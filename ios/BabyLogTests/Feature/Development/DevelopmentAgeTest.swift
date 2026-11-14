import Foundation
import Testing
@testable import BabyLog

struct DevelopmentAgeTest {
    private let utc = TimeZone(secondsFromGMT: 0)!
    private let birth = Self.date("2026-05-28")

    @Test func birthdayIsDayZeroOfTheFirstWeek() {
        #expect(ageInDays(birth: birth, today: birth, timeZone: utc) == 0)
        #expect(weekOfLife(0) == 1)
    }

    @Test func weeksOfLifeStartEverySeventhDay() {
        #expect(weekOfLife(6) == 1)
        #expect(weekOfLife(7) == 2)
        #expect(weekOfLife(363) == 52)
        #expect(weekOfLife(364) == 53)
    }

    @Test func ageCountsFullDaysAcrossMonthsAndLeapYears() {
        #expect(ageInDays(birth: birth, today: Self.date("2026-10-21"), timeZone: utc) == 146)
        #expect(ageInDays(
            birth: Self.date("2027-05-28"),
            today: Self.date("2028-05-28"),
            timeZone: utc
        ) == 366)
    }

    @Test func birthDateInTheFutureGivesNoAge() {
        let tomorrow = Calendar(identifier: .gregorian).date(byAdding: .day, value: 1, to: birth)!
        #expect(ageInDays(birth: tomorrow, today: birth, timeZone: utc) == nil)
    }

    @Test func tipsForAgeAreNewestFirstAndDoctorTipsLast() {
        let early = tip("early", ages: 0...100)
        let later = tip("later", ages: 50...200)
        let doctor = tip("doctor", ages: 0...364, kind: .doctor)
        let future = tip("future", ages: 201...300)

        #expect(tipsFor([doctor, early, future, later], ageDays: 60).map(\.id) == ["later", "early", "doctor"])
    }

    @Test func rangeBoundsAreInclusive() {
        let tips = [tip("a", ages: 10...20)]
        #expect(tipsFor(tips, ageDays: 10).count == 1)
        #expect(tipsFor(tips, ageDays: 20).count == 1)
        #expect(tipsFor(tips, ageDays: 21).isEmpty)
    }

    @Test func milestoneStateIsRelativeToItsWindow() {
        let sitting = MilestoneWindow(id: "sitting", title: "Сидит без поддержки", window: 115...279, months: 3.8...9.2)
        #expect(sitting.stateAt(114) == .ahead)
        #expect(sitting.stateAt(115) == .inWindow)
        #expect(sitting.stateAt(279) == .inWindow)
        #expect(sitting.stateAt(280) == .past)
    }

    private func tip(_ id: String, ages: ClosedRange<Int>, kind: TipKind = .tip) -> Tip {
        Tip(
            id: id,
            ages: ages,
            category: .sleep,
            kind: kind,
            title: id,
            summary: id,
            body: id,
            points: [],
            sources: [TipSource(name: "NHS", url: "https://www.nhs.uk/", section: "Раздел")],
            disagreement: [],
            reviewedAt: Self.date("2026-10-20")
        )
    }

    private static func date(_ value: String) -> Date {
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.date(from: value)!
    }
}
