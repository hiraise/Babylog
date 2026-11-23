import XCTest

/** Гайд «health»: температура, лекарство по подсказке и болезнь до выздоровления. */
final class HealthGuideTest: XCTestCase {
    @MainActor func testGuidePassesFromFirstStepToDone() {
        let guide = GuideHarness("health")
        guide.show()
        guide.tap("Здоровье")
        guide.tap("Температура")
        guide.typeInto("Температура", "37,8")
        guide.tap("Сохранить")
        guide.tap("Здоровье")
        guide.tap("Лекарство")
        guide.type("Ви")
        guide.tap("Витамин D")
        guide.tap("Сохранить")
        guide.tap("Здоровье")
        guide.tap("Заболела")
        guide.tap("Начать")
        guide.tapAction("открыть болезнь")
        guide.tap("Выздоровела")
        guide.tapLast("Выздоровела")
        guide.assertDone()
    }
}
