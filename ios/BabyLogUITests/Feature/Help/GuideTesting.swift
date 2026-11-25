import XCTest

/** Гайд темы на примере дневника: приложение открывается сразу в гайде с базой в памяти (ADR-0043). */
@MainActor final class GuideHarness {
    let app = XCUIApplication()
    private let topicId: String

    init(_ topicId: String) {
        self.topicId = topicId
    }

    func show() {
        app.launchArguments = ["-guide", topicId]
        app.launch()
        let marker = app.staticTexts["Пример · ничего не сохранится"]
        XCTAssertTrue(marker.waitForExistence(timeout: 20), "Гайд \(topicId) не открылся")
        settle()
    }

    /** Кнопка или пункт с точным текстом. */
    func tap(_ text: String) {
        let exact = app.buttons[text].firstMatch
        let element = exact.exists
            ? exact
            : app.buttons.matching(NSPredicate(format: "label CONTAINS %@", text)).firstMatch
        XCTAssertTrue(element.waitForExistence(timeout: 10), "Нет кнопки «\(text)»")
        element.tap()
        settle()
    }

    /** Кнопка-иконка по описанию для VoiceOver. */
    func tapIcon(_ description: String) {
        tap(description)
    }

    /** Элемент по действию из Android-эталона. */
    func tapAction(_ clickLabel: String) {
        switch clickLabel {
        case "начать сон", "проснулась": tapTarget("SLEEP_TILE")
        case "начать кормление", "закончить кормление": tapTarget("FEEDING_TILE")
        case "начать плач", "закончить плач": tapTarget("CRYING_TILE")
        case "открыть болезнь": tapTarget("ILLNESS_CARD")
        case "выбрать ребёнка": tapTarget("CHILD_SELECTOR")
        case "изменить запись":
            let row = app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH 'event-row-'")).firstMatch
            XCTAssertTrue(row.waitForExistence(timeout: 10), "Нет записи для правки")
            row.tap()
            settle()
        default:
            XCTFail("Неизвестное действие \(clickLabel)")
        }
    }

    func tapTarget(_ target: String) {
        let element = app.descendants(matching: .any)
            .matching(identifier: "guide-target-\(target)")
            .firstMatch
        XCTAssertTrue(element.waitForExistence(timeout: 10), "Нет цели \(target)")
        element.tap()
        settle()
    }

    /** Первый из нескольких элементов с этим текстом. */
    func tapFirst(_ text: String, scroll: Bool = false) {
        let predicate = NSPredicate(format: "label CONTAINS %@", text)
        let element = app.buttons.matching(predicate).firstMatch
        XCTAssertTrue(element.waitForExistence(timeout: 10), "Нет элемента «\(text)»")
        if scroll { scrollToHittable(element) }
        element.tap()
        settle()
    }

    /** Последняя кнопка с этим текстом: кнопка листа поверх такой же кнопки экрана. */
    func tapLast(_ text: String) {
        let query = app.buttons.matching(identifier: text)
        XCTAssertTrue(query.firstMatch.waitForExistence(timeout: 10), "Нет кнопки «\(text)»")
        let elements = query.allElementsBoundByIndex
        let element = elements.last(where: { $0.isHittable }) ?? elements.last!
        element.tap()
        settle()
    }

    /** Ввод в первое поле открытого листа. */
    func type(_ text: String) {
        var element = app.textFields.firstMatch
        if !element.exists { element = app.textViews.firstMatch }
        XCTAssertTrue(element.waitForExistence(timeout: 10), "Нет поля ввода")
        element.tap()
        element.typeText(text)
        settle()
    }

    /** Замена текста в поле с описанием «Температура». */
    func typeInto(_ description: String, _ text: String) {
        let element = app.textFields[description]
        XCTAssertTrue(element.waitForExistence(timeout: 10), "Нет поля \(description)")
        element.tap()
        let old = (element.value as? String) ?? ""
        for _ in old { element.typeText(XCUIKeyboardKey.delete.rawValue) }
        element.typeText(text)
        settle()
    }

    /** Свайп вниз закрывает открытый лист независимо от его высоты. */
    func back() {
        let activity = app.otherElements["ActivityListView"].firstMatch
        let appSheet = app.descendants(matching: .any).matching(identifier: "app-sheet").firstMatch
        let sheet = activity.exists ? activity : appSheet
        XCTAssertTrue(sheet.waitForExistence(timeout: 10), "Нет открытого листа")
        let start = sheet.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.05))
        let end = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.95))
        start.press(forDuration: 0.1, thenDragTo: end)
        settle()
    }

    func next(_ button: String = "Далее") {
        tap(button)
    }

    func step(_ number: Int) {
        let predicate = NSPredicate(format: "label CONTAINS %@", "· шаг \(number) из")
        let element = app.staticTexts.matching(predicate).firstMatch
        XCTAssertTrue(element.waitForExistence(timeout: 10), "Гайд \(topicId) не на шаге \(number)")
    }

    func assertDone() {
        let element = app.staticTexts["Готово: тема «\(Self.titles[topicId]!)» пройдена"]
        XCTAssertTrue(element.waitForExistence(timeout: 15), "Гайд \(topicId) не завершён")
    }

    func dayLabel(daysAgo: Int) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "ru_RU")
        formatter.timeZone = .current
        formatter.dateFormat = "d MMMM, EEEE"
        return formatter.string(from: Date().addingTimeInterval(TimeInterval(-daysAgo * 86_400)))
    }

    private func scrollToHittable(_ element: XCUIElement) {
        for _ in 0..<8 where !element.isHittable {
            app.swipeUp()
        }
    }

    private func settle() {
        Thread.sleep(forTimeInterval: 0.35)
    }

    private static let titles = [
        "sleep-feeding": "Сон и кормление",
        "records": "Подгузник, вес и рост",
        "crying": "Плач",
        "notes": "Заметки и метки",
        "health": "Здоровье и болезнь",
        "diary": "Дневник по дням",
        "analytics": "Аналитика",
        "development": "Развитие",
        "children": "Дети и фото",
        "settings": "Тема, шторка и ваши данные",
    ]
}
