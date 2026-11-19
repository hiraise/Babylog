import XCTest

/** Справка (ADR-0043): ⋮ → «Справка» → тема → пример и обратно; в примере нет переноса данных. */
@MainActor final class HelpNavigationTest: XCTestCase {
    func testHelpOpensFromMenuAndExampleClosesBack() {
        let guide = GuideHarness("sleep-feeding")
        guide.show()

        guide.tapIcon("Меню")
        XCTAssertTrue(guide.app.buttons["Тема"].exists)
        XCTAssertFalse(guide.app.buttons["Экспорт в файл"].exists)
        XCTAssertFalse(guide.app.buttons["Импорт из файла"].exists)
        XCTAssertFalse(guide.app.buttons["Справка"].exists)
        guide.back()

        guide.tapIcon("Закрыть пример")
        XCTAssertTrue(guide.app.buttons["Попробовать на примере"].waitForExistence(timeout: 10))
        guide.tapIcon("Назад")
        guide.tapIcon("Назад")

        guide.tapIcon("Меню")
        guide.tap("Справка")
        let topic = guide.app.buttons
            .matching(NSPredicate(format: "label BEGINSWITH %@", "Сон и кормление"))
            .firstMatch
        XCTAssertTrue(topic.waitForExistence(timeout: 10))
        topic.tap()
        XCTAssertTrue(guide.app.buttons["Попробовать на примере"].waitForExistence(timeout: 10))
        guide.tap("Попробовать на примере")
        XCTAssertTrue(guide.app.staticTexts["Пример · ничего не сохранится"].waitForExistence(timeout: 20))
        XCTAssertTrue(guide.app.staticTexts["Дневник Сони"].waitForExistence(timeout: 20))
    }
}
