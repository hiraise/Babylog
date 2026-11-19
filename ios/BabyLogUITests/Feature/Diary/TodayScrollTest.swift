import XCTest

/** Сводка дня прокручивается к видимому месту над нижней навигацией, в том числе при крупном тексте. */
final class TodayScrollTest: XCTestCase {
    @MainActor func testDaySummaryScrollsAboveNavigation() {
        let app = XCUIApplication()
        app.launch()
        let summary = app.descendants(matching: .any)
            .matching(NSPredicate(format: "label BEGINSWITH %@", "Лекарств:"))
            .firstMatch
        let today = app.buttons["Сегодня"].firstMatch
        for _ in 0..<10 where !summary.isHittable || summary.frame.maxY > today.frame.minY {
            app.swipeUp()
        }
        XCTAssertTrue(summary.isHittable, "Сводка дня не доступна прокруткой")
        XCTAssertTrue(today.isHittable)
        XCTAssertLessThanOrEqual(summary.frame.maxY, today.frame.minY)
    }
}
