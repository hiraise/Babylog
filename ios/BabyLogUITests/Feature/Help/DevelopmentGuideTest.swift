import XCTest

/** Гайд «development»: советы недели и экран навыков на настоящих файлах советов. */
final class DevelopmentGuideTest: XCTestCase {
    @MainActor func testGuidePassesFromFirstStepToDone() {
        let guide = GuideHarness("development")
        guide.show()
        guide.tap("Развитие")
        guide.next()
        guide.tap("подробнее")
        guide.assertDone()
    }
}
