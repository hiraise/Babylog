import Foundation
import Testing
@testable import BabyLog

/** Ход гайда (ADR-0043): шаги засчитываются по записям, цели и кнопке и не откатываются. */
@MainActor
struct GuideControllerTest {
    private let base = TestClock(
        date: ISO8601DateFormatter().date(from: "2026-10-15T10:00:00Z")!,
        timeZone: TimeZone(secondsFromGMT: 0)!
    )

    private var clock: GuideClock { GuideClock(base: base) }
    private var sleep: Sleep { Sleep(id: 1, childId: 1, start: base.millis()) }
    private var sleeping: StepDone {
        .records { now, _ in now.contains { $0 is Sleep && $0.isActive } }
    }
    private var topic: HelpTopic {
        HelpTopic(
            id: "test",
            title: "Тест",
            icon: "ic_help",
            summary: "",
            steps: [
                GuideStep(text: "Запустите сон", done: sleeping, target: .sleepTile, sheetHint: "В листе"),
                GuideStep(text: "Подождите", done: .next, button: "Прошло 40 минут", skipMinutes: 40),
                GuideStep(text: "Откройте кормление", done: .shown(.feedingTile)),
            ]
        )
    }

    @Test func recordStepWaitsForRecord() {
        let guide = GuideController(topic: topic, clock: clock)
        guide.onEvents([])
        #expect(guide.index == 0)
        #expect(guide.target == .sleepTile)
        #expect(guide.sheetHint == "В листе")
        guide.onEvents([sleep])
        #expect(guide.index == 1)
        #expect(guide.sheetHint == "Подождите")
    }

    @Test func buttonSkipsExampleClockAndPassesStep() {
        let clock = clock
        let guide = GuideController(topic: topic, clock: clock)
        guide.onEvents([sleep])
        guide.press()
        #expect(clock.millis() == base.millis() + 40 * 60_000)
        #expect(guide.index == 2)
    }

    @Test func buttonDoesNothingOnActionStep() {
        let clock = clock
        let guide = GuideController(topic: topic, clock: clock)
        guide.onEvents([])
        guide.press()
        #expect(guide.index == 0)
        #expect(clock.millis() == base.millis())
    }

    @Test func passedStepStaysPassedWhenRecordGoesAway() {
        let guide = GuideController(topic: topic, clock: clock)
        guide.onEvents([sleep])
        var ended = sleep
        ended.end = base.millis()
        guide.onEvents([ended])
        #expect(guide.index == 1)
    }

    @Test func skipPassesActionStep() {
        let clock = clock
        let guide = GuideController(topic: topic, clock: clock)
        guide.onEvents([])
        guide.skip()
        #expect(guide.index == 1)
        #expect(clock.millis() == base.millis())
    }

    @Test func visitedStepPassesWhenEarlierSheetCloses() {
        let sheet = GuideController(
            topic: HelpTopic(id: "sheet", title: "Лист", icon: "ic_help", summary: "", steps: [
                GuideStep(text: "Откройте лист", done: .shown(.daySelector)),
                GuideStep(text: "Выберите день", done: .visited(.daySelector)),
            ]),
            clock: clock
        )
        sheet.shown(.daySelector)
        #expect(sheet.index == 1)
        sheet.hidden(.daySelector)
        #expect(sheet.finished)
    }

    @Test func childrenStepPassesWhenChildIsAdded() {
        let masha = Child(id: 1, name: "Маша", birthDate: nil, sex: nil)
        let children = GuideController(
            topic: HelpTopic(id: "children", title: "Дети", icon: "ic_help", summary: "", steps: [
                GuideStep(text: "Добавьте ребёнка", done: .children { now, atStart in now.count > atStart.count }),
            ]),
            clock: clock
        )
        children.onChildren([masha])
        #expect(children.index == 0)
        children.onChildren([masha, Child(id: 2, name: "Петя", birthDate: nil, sex: nil)])
        #expect(children.finished)
    }

    @Test func shownStepFinishesGuide() {
        let guide = GuideController(topic: topic, clock: clock)
        guide.shown(.feedingTile)
        guide.onEvents([sleep])
        guide.press()
        #expect(guide.finished)
        #expect(guide.target == nil)
        #expect(guide.sheetHint == nil)
    }
}
