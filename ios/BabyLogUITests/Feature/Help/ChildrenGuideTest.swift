import XCTest

/** Гайд «children»: второй ребёнок добавляется и выбирается, потом — обратно Соня. */
final class ChildrenGuideTest: XCTestCase {
    @MainActor func testGuidePassesFromFirstStepToDone() {
        let guide = GuideHarness("children")
        guide.show()
        guide.tapAction("выбрать ребёнка")
        guide.tap("Добавить ребёнка")
        guide.type("Миша")
        guide.tap("Сохранить")
        guide.tapAction("выбрать ребёнка")
        guide.tapFirst("Соня")
        guide.next()
        guide.assertDone()
    }
}
