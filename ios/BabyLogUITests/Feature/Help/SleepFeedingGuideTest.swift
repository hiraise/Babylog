import XCTest

/** Гайд «sleep-feeding»: сон, кормление со сменой груди и бутылочка — на настоящем экране. */
final class SleepFeedingGuideTest: XCTestCase {
    @MainActor func testGuidePassesFromFirstStepToDone() {
        let guide = GuideHarness("sleep-feeding")
        guide.show()
        guide.tapAction("начать сон")
        guide.tap("Запустить таймер")
        guide.next("Прошло 40 минут")
        guide.tapAction("проснулась")
        guide.next("Понятно")
        guide.tapAction("начать кормление")
        guide.tap("Левая грудь")
        guide.tap("Запустить таймер")
        guide.tapIcon("Сменить грудь")
        guide.next("Прошло 15 минут")
        guide.tapAction("закончить кормление")
        guide.tapAction("начать кормление")
        guide.tap("Бутылочка")
        guide.tap("Сохранить")
        guide.assertDone()
    }
}
