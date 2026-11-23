import XCTest

/** Гайд «analytics»: обзор плиток, раздел «Сон» и назад к обзору. */
final class AnalyticsGuideTest: XCTestCase {
    @MainActor func testGuidePassesFromFirstStepToDone() {
        let guide = GuideHarness("analytics")
        guide.show()
        guide.tap("Аналитика")
        guide.next()
        guide.tapTarget("ANALYTICS_SLEEP_TILE")
        guide.next()
        guide.tapIcon("Назад к обзору")
        guide.assertDone()
    }
}
