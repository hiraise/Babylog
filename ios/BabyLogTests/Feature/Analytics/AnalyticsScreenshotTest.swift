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
