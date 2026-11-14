import Foundation
import Testing
@testable import BabyLog

struct DevelopmentWeekTest {
    private let utc = TimeZone(secondsFromGMT: 0)!
    private let birth = Self.date("2026-05-20")
    private let sitting = MilestoneWindow(id: "sitting", title: "Сидит без поддержки", window: 115...279, months: 3.8...9.2)
    private let walking = MilestoneWindow(id: "walking", title: "Ходит самостоятельно", window: 250...534, months: 8.2...17.6)

    private var data: DevelopmentData {
        DevelopmentData(
            tips: [
                tip("sleep", ages: 0...180, category: .sleep),
                tip("solids", ages: 126...181, category: .feeding),
                tip("doctor", ages: 112...181, category: .development, kind: .doctor),
                tip("later", ages: 200...300, category: .safety),
            ],
            milestones: MilestoneTable(
                source: TipSource(name: "ВОЗ", url: "https://cdn.who.int/t.pdf", section: "1st, 99th"),
                reviewedAt: birth,
                milestones: [sitting, walking]
            )
        )
    }

    @Test func weekSplitsTipsFromDoctorTipsAndPlacesTheChildInEachWindow() throws {
        let week = try #require(developmentWeek(data: data, birth: birth, today: plusDays(150, to: birth), timeZone: utc))

        #expect(week.ageDays == 150)
        #expect(week.week == 22)
        #expect(week.tips.map(\.id) == ["solids", "sleep"])
        #expect(week.doctorTips.map(\.id) == ["doctor"])
        #expect(week.allTips.map(\.id) == ["solids", "sleep", "doctor"])
        #expect(week.milestones.map { $0.state } == [.inWindow, .ahead])
    }

    @Test func noWeekBeforeBirth() {
        #expect(developmentWeek(data: data, birth: birth, today: plusDays(-1, to: birth), timeZone: utc) == nil)
    }

    @Test func tipsAreNewUntilThisWeekWasOpenedForThisChild() throws {
        let week = try #require(developmentWeek(data: data, birth: birth, today: plusDays(150, to: birth), timeZone: utc))

        #expect(hasNewTips(week: week, childId: 1, seenWeeks: [:]))
        #expect(hasNewTips(week: week, childId: 1, seenWeeks: [1: 21, 2: 22]))
        #expect(!hasNewTips(week: week, childId: 1, seenWeeks: [1: 22]))
        #expect(!hasNewTips(week: nil, childId: 1, seenWeeks: [:]))
        let noTips = developmentWeek(
            data: DevelopmentData(tips: [], milestones: data.milestones),
            birth: birth,
            today: plusDays(150, to: birth),
            timeZone: utc
        )
        #expect(!hasNewTips(week: noTips, childId: 1, seenWeeks: [:]))
    }

    @Test func textsNameTheWeek() {
        #expect(weekTitle(22) == "22-я неделя")
        #expect("на \(weekAccusative(22))" == "на 22-ю неделю")
    }

    private func tip(
        _ id: String,
        ages: ClosedRange<Int>,
        category: TipCategory,
        kind: TipKind = .tip
    ) -> Tip {
        Tip(
            id: id,
            ages: ages,
            category: category,
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

    private func plusDays(_ days: Int, to date: Date) -> Date {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = utc
        return calendar.date(byAdding: .day, value: days, to: date)!
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
