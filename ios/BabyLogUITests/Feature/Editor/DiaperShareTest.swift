import XCTest

/** Системное «Поделиться» записью подгузника продолжает открываться из её формы. */
final class DiaperShareTest: XCTestCase {
    @MainActor func testExistingDiaperOpensShareSheet() {
        let guide = GuideHarness("records")
        guide.show()
        guide.tap("Подгузник")
        guide.tap("Пописала")
        guide.tap("Сохранить")
        guide.tap("Дневник")
        guide.tapAction("изменить запись")
        guide.tap("Поделиться")

        XCTAssertTrue(
            guide.app.otherElements["ActivityListView"].waitForExistence(timeout: 10),
            "Системное «Поделиться» не открылось из записи подгузника"
        )
    }
}
