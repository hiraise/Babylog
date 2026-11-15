import Foundation
import Testing
@testable import BabyLog

/**
 Касания нижней панели и «Развития» (ADR-0035, ADR-0037): панель переключает разделы, «Назад»
 ведёт на «Сегодня», а с экрана навыков — к обзору раздела; полоса советов открывает «Развитие»,
 и точка «новые» гаснет; без даты рождения советов и навыков нет.
 */
@MainActor
@Suite(.serialized)
struct DevelopmentFlowTest {
    private let utc = TimeZone(secondsFromGMT: 0)!
    private let today = isoDate("2026-10-18T08:20:00Z")
    private let masha = Child(id: 1, name: "Маша", birthDate: millis("2026-05-20T00:00:00Z"), sex: .girl)

    @Test func tipsBannerOpensDevelopmentAndNewMarkGoesOut() async throws {
        let seen = MemoryDevelopmentSeenStore()
        let development = DevelopmentViewModel(source: AssetTipsSource(), seen: seen)
        _ = await waitUntil { development.data != nil }
        let week = try #require(currentDevelopmentWeek(data: development.data, child: masha, today: today, timeZone: utc))
        var navigation = DiaryNavigationState()

        #expect(week.week == 22)
        #expect(hasNewTips(week: week, childId: masha.id, seenWeeks: development.seenWeeks))
        navigation.select(.development)
        #expect(navigation.section == .development)

        development.markSeen(childId: masha.id, week: week.week)
        _ = await waitUntil { development.seenWeeks == [1: 22] }
        #expect(seen.seenWeeks.value == [1: 22])
        #expect(!hasNewTips(week: week, childId: masha.id, seenWeeks: development.seenWeeks))
        let returned = navigation.back()
        #expect(returned)
        #expect(navigation.section == .today)
    }

    @Test func bottomBarSwitchesSectionsAndBackReturnsToToday() {
        var navigation = DiaryNavigationState()
        navigation.select(.diary)
        navigation.diaryView = .notes
        navigation.select(.analytics)
        #expect(navigation.section == .analytics)
        let returned = navigation.back()
        #expect(returned)
        #expect(navigation.section == .today)
    }

    @Test func skillsOpenInsideSectionAndBackReturnsToOverview() {
        let model = DevelopmentContentModel(childId: masha.id)
        #expect(!model.showSkills)
        model.openSkills()
        #expect(model.showSkills)
        model.backToOverview()
        #expect(!model.showSkills)
    }

    @Test func tipAndAllTipsSheetsOpenAndClose() async throws {
        let development = DevelopmentViewModel(source: AssetTipsSource(), seen: MemoryDevelopmentSeenStore())
        _ = await waitUntil { development.data != nil }
        let week = try #require(currentDevelopmentWeek(data: development.data, child: masha, today: today, timeZone: utc))
        let first = try #require(week.tips.first)
        let model = DevelopmentContentModel(childId: masha.id)

        model.openTip(first.id)
        #expect(model.openTipId == first.id)
        #expect(first.sources.isEmpty == false)
        model.dismissTip()
        #expect(model.openTipId == nil)
        model.openAllTips()
        #expect(model.showAllTips)
        model.openFromAllTips(first.id)
        #expect(!model.showAllTips)
        #expect(model.openTipId == first.id)
    }

    @Test func withoutBirthDateDevelopmentHasNoWeek() async {
        let development = DevelopmentViewModel(source: AssetTipsSource(), seen: MemoryDevelopmentSeenStore())
        _ = await waitUntil { development.data != nil }
        let child = Child(id: 1, name: "Маша", birthDate: nil, sex: .girl)
        #expect(currentDevelopmentWeek(data: development.data, child: child, today: today, timeZone: utc) == nil)
    }

    @Test func chartAndBannerTextsMatchAndroidFormatting() {
        #expect(formatMonths(115) == "3,8")
        #expect(formatMonthValue(13.5) == "13,5")
        let week = DevelopmentWeek(
            ageDays: 150,
            week: 22,
            tips: [tip("sleep", category: .sleep), tip("feeding", category: .feeding)],
            doctorTips: [tip("doctor", category: .health, kind: .doctor)],
            milestones: []
        )
        #expect(bannerSummary(week) == "Сон, кормление, здоровье · 3 совета")
        #expect(tipAgeText(tip("age", ages: 0...59, category: .sleep)) == "для 0–2 мес")
    }

    private func tip(
        _ id: String,
        ages: ClosedRange<Int> = 0...180,
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
            reviewedAt: isoDate("2026-10-20T00:00:00Z")
        )
    }
}

private func isoDate(_ value: String) -> Date {
    ISO8601DateFormatter().date(from: value)!
}

private func millis(_ value: String) -> Int64 {
    Int64(isoDate(value).timeIntervalSince1970 * 1000)
}
