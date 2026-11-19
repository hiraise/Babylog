import XCTest

/** Гайд «records»: подгузник, вес и рост записываются в пример дневника. */
final class RecordsGuideTest: XCTestCase {
    @MainActor func testGuidePassesFromFirstStepToDone() {
        let guide = GuideHarness("records")
        guide.show()
        guide.tap("Подгузник")
        guide.tap("Пописала")
        guide.tap("Сохранить")
        guide.next()
        guide.next()
        guide.tap("Вес")
        guide.type("6450")
        guide.tap("Сохранить")
        guide.tap("Рост")
        guide.type("64,5")
        guide.tap("Сохранить")
        guide.assertDone()
    }
}
