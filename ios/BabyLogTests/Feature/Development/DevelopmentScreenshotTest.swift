import Foundation
import Testing
@testable import BabyLog

/**
 «Развитие» на настоящих советах (ADR-0037): обзор недели в светлой и тёмной теме, экран навыков,
 совет с расхождением источников и полоса советов на «Сегодня». Маше 151 день — 22-я неделя.
 */
@MainActor
@Suite(.serialized)
struct DevelopmentScreenshotTest {
    private let clock = TestClock(date: isoDate("2026-10-18T08:20:00Z"), timeZone: TimeZone(secondsFromGMT: 0)!)
    private let today = isoDate("2026-10-18T08:20:00Z")
    private let masha = Child(id: 1, name: "Маша", birthDate: millis("2026-05-20T00:00:00Z"), sex: .girl)

    @Test func overview() async {
        await screen(name: "development_overview", section: .development, dark: false)
    }

    @Test func overviewDark() async {
        await screen(name: "development_overview_dark", section: .development, dark: true)
    }

    @Test func todayWithTipsBanner() async {
        await screen(name: "diary_today_tips", section: .today, dark: false)
    }

    @Test func skills() async throws {
        let development = await makeDevelopment()
        let data = try #require(development.data)
        assertComponentSnapshot(name: "development_skills", style: .light, height: 1200, clock: clock) {
            DevelopmentContent(child: masha, data: data, today: today, onSetBirthDate: {}, initialSkills: true)
        }
    }

    @Test func tipWithDisagreeingSources() async throws {
        let development = await makeDevelopment()
        let tip = try #require(development.data?.tips.first { $0.id == "m3-solids-timing" })
        assertComponentSnapshot(name: "development_tip", style: .light, height: 1100, clock: clock) {
            TipDetails(tip: tip)
        }
    }

    private func screen(name: String, section: MainSection, dark: Bool) async {
        let model = DiaryViewModel(
            repository: FakeDiaryRepository(children: [masha]),
            selection: MemorySelectedChildStore(),
            documents: FakeDocumentStore(),
            clock: clock
        )
        _ = await waitUntil { model.state.child != nil }
        let development = await makeDevelopment()
        assertComponentSnapshot(name: name, style: dark ? .dark : .light, height: 1200, clock: clock) {
            DiaryScreen(
                model: model,
                theme: ThemeChoice(),
                onThemeChange: { _ in },
                development: development,
                initialSection: section
            )
        }
    }

    private func makeDevelopment() async -> DevelopmentViewModel {
        let development = DevelopmentViewModel(source: AssetTipsSource(), seen: MemoryDevelopmentSeenStore())
        _ = await waitUntil { development.data != nil }
        return development
    }
}

private func isoDate(_ value: String) -> Date {
    ISO8601DateFormatter().date(from: value)!
}

private func millis(_ value: String) -> Int64 {
    Int64(isoDate(value).timeIntervalSince1970 * 1000)
}
