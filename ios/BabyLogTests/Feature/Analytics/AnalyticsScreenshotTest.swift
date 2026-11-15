import Foundation
import Testing
@testable import BabyLog

/** Обзор «Аналитики» на эталонных скриншотах (ADR-0051, ADR-0029). */
@MainActor
@Suite(.serialized)
struct AnalyticsScreenshotTest {
    @Test func twoMonthsLight() async throws {
        let model = try await makeAnalyticsViewModel()
        assertAnalytics(model: model, dark: false, height: 1000, name: "analytics_week")
    }

    @Test func twoMonthsDark() async throws {
        let model = try await makeAnalyticsViewModel()
        assertAnalytics(model: model, dark: true, height: 852, name: "analytics_dark")
    }

    @Test func oddMedicineTileKeepsFullColumnWidth() async throws {
        let repository = FakeDiaryRepository(children: [analyticsChild])
        _ = try await repository.addEvent(Diaper(id: 1, childId: 1, start: analyticsNow - 3_600_000, kind: .wet))
        _ = try await repository.addEvent(Crying(id: 2, childId: 1, start: analyticsNow - 3_000_000, end: analyticsNow - 2_400_000))
        _ = try await repository.addEvent(Medicine(id: 3, childId: 1, start: analyticsNow - 1_800_000, name: "Витамин D"))
        let clock = TestClock(date: Date(timeIntervalSince1970: Double(analyticsNow) / 1000), timeZone: analyticsZone)
        let model = DiaryViewModel(repository: repository, selection: MemorySelectedChildStore(), documents: FakeDocumentStore(), clock: clock)
        _ = await waitUntil { model.state.events.count == 3 }

        assertAnalytics(model: model, dark: false, height: 1000, name: "analytics_medicine_odd")
    }

    private func assertAnalytics(model: DiaryViewModel, dark: Bool, height: Double, name: String) {
        let clock = TestClock(date: Date(timeIntervalSince1970: Double(analyticsNow) / 1000), timeZone: analyticsZone)
        assertComponentSnapshot(name: name, style: dark ? .dark : .light, height: height, clock: clock) {
            DiaryScreen(
                model: model,
                theme: ThemeChoice(),
                onThemeChange: { _ in },
                initialSection: .analytics
            )
        }
    }
}
