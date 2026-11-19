import XCTest

/** Сводка «Для врача» передаёт системному «Поделиться» текст предпросмотра; отправка повторяется после отмены. */
final class DoctorShareTest: XCTestCase {
    @MainActor func testDoctorSummarySharesPreviewTextAndCanBeRepeated() {
        let guide = GuideHarness("health")
        guide.show()
        guide.tap("Здоровье")
        guide.tap("Заболела")
        guide.tap("Начать")
        guide.tapAction("открыть болезнь")
        guide.tap("+ Название")
        guide.type("ОРВИ")
        guide.tap("Добавить")
        guide.tap("Температура")
        guide.typeInto("Температура", "37,8")
        guide.tap("Сохранить")
        guide.tap("Лекарство")
        guide.type("Ви")
        guide.tap("Витамин D")
        guide.tap("Сохранить")
        guide.tap("Для врача")

        let preview = guide.app.staticTexts["doctor-summary"]
        XCTAssertTrue(preview.waitForExistence(timeout: 10), "Нет предпросмотра сводки")
        let expected = preview.label
        XCTAssertTrue(expected.contains("ОРВИ"))
        XCTAssertTrue(expected.contains("37,8"))
        XCTAssertTrue(expected.contains("Витамин D"))
        guide.tap("Поделиться")
        let activity = guide.app.otherElements["ActivityListView"]
        XCTAssertTrue(activity.waitForExistence(timeout: 10), "Системное «Поделиться» не открылось")

        let copy = guide.app.descendants(matching: .any)
            .matching(NSPredicate(format: "label IN %@", ["Copy", "Копировать", "Скопировать"]))
            .firstMatch
        XCTAssertTrue(copy.waitForExistence(timeout: 10), "В системном листе нет действия копирования")
        copy.tap()
        XCTAssertTrue(activity.waitForNonExistence(timeout: 10))
        XCTAssertTrue(guide.app.buttons["Для врача"].waitForExistence(timeout: 10), "Экран болезни закрылся")

        guide.tap("Для врача")
        XCTAssertEqual(preview.label, expected)
        guide.tap("Поделиться")
        XCTAssertTrue(activity.waitForExistence(timeout: 10), "Повторное «Поделиться» не открылось")
        guide.back()
        XCTAssertTrue(guide.app.buttons["Для врача"].waitForExistence(timeout: 10), "Отмена закрыла экран болезни")
    }
}
