import Foundation
import Testing
@testable import BabyLog

/** Экраны разделов «Аналитики»: кормления, сон, вес, плач и метки (ADR-0029). */
@MainActor
@Suite(.serialized)
struct AnalyticsSectionScreenshotTest {
    @Test(arguments: [
        (section: AnalyticsSection.feedings, name: "analytics_feedings"),
        (section: .sleep, name: "analytics_sleep"),
        (section: .weight, name: "analytics_weight"),
        (section: .crying, name: "analytics_crying"),
        (section: .tags, name: "analytics_tags"),
    ]) func section(section: AnalyticsSection, name: String) {
        let clock = TestClock(date: Date(timeIntervalSince1970: Double(analyticsNow) / 1000), timeZone: analyticsZone)
        assertComponentSnapshot(
            name: name,
            style: .light,
            height: section == .crying || section == .tags ? 1400 : 1000,
            clock: clock
        ) {
            AnalyticsContent(
                events: analyticsSampleEvents(),
                child: analyticsChild,
                now: analyticsNow,
                onOpenDay: { _ in },
                initialSection: section
            )
        }
    }
}
