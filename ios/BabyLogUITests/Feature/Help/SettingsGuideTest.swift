import XCTest

/** Гайд «settings»: меню ⋮ → «Тема», лист закрывается свайпом; остальное — пояснения. */
final class SettingsGuideTest: XCTestCase {
    @MainActor func testGuidePassesFromFirstStepToDone() {
        let guide = GuideHarness("settings")
        guide.show()
        guide.tapIcon("Меню")
        guide.tap("Тема")
        guide.back()
        guide.next()
        guide.next()
        guide.assertDone()
    }
}
