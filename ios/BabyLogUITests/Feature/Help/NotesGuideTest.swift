import XCTest

/** Гайд «notes»: заметка с меткой, лента «Заметки» и сводка метки. */
final class NotesGuideTest: XCTestCase {
    @MainActor func testGuidePassesFromFirstStepToDone() {
        let guide = GuideHarness("notes")
        guide.show()
        guide.tap("Заметка")
        guide.type("Тёрла дёсны")
        guide.tap("зубки")
        guide.tap("Сохранить")
        guide.tap("Дневник")
        guide.tap("Заметки")
        guide.tapFirst("зубки", scroll: true)
        guide.next()
        guide.assertDone()
    }
}
