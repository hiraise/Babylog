import XCTest

/** Гайд «crying»: плач перемоткой, причина сохраняется в ту же запись. */
final class CryingGuideTest: XCTestCase {
    @MainActor func testGuidePassesFromFirstStepToDone() {
        let guide = GuideHarness("crying")
        guide.show()
        guide.tapAction("начать плач")
        guide.next("Прошло 10 минут")
        guide.tapAction("закончить плач")
        guide.tap("колики")
        guide.tap("Сохранить")
        guide.next()
        guide.assertDone()
    }
}
