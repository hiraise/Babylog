import XCTest

/** Гайд «diary»: вчера, выбор дня из списка и правка записи. */
final class DiaryGuideTest: XCTestCase {
    @MainActor func testGuidePassesFromFirstStepToDone() {
        let guide = GuideHarness("diary")
        guide.show()
        guide.tap("Дневник")
        guide.tapIcon("Предыдущий день")
        guide.tap("Вчера")
        guide.tapFirst(guide.dayLabel(daysAgo: 2))
        guide.tapAction("изменить запись")
        guide.tap("Отмена")
        guide.assertDone()
    }
}
