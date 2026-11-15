import Foundation
import Observation

/**
 Ход гайда по теме (ADR-0043): какой шаг сейчас и засчитан ли он. Шаг засчитывается сам — по записям
 и детям примера (`onEvents`, `onChildren`), появлению и закрытию цели (`shown`, `hidden`) — либо
 кнопкой шага (`press`); шаг с действием можно пропустить (`skip`). Пройденный шаг не откатывается.
 */
@MainActor @Observable final class GuideController: GuideTracker {
    let topic: HelpTopic
    @ObservationIgnored private let clock: GuideClock

    private(set) var index = 0

    var step: GuideStep? { topic.steps.indices.contains(index) ? topic.steps[index] : nil }
    var finished: Bool { index >= topic.steps.count }
    var target: GuideTarget? { step?.target }
    var sheetHint: String? { step.map { $0.sheetHint ?? $0.text } }

    @ObservationIgnored private var onScreen: [GuideTarget: Int] = [:]
    /** nil — записи примера ещё не пришли: шаги по записям ждут. */
    @ObservationIgnored private var events: [any BabyEvent]?
    @ObservationIgnored private var atStart: [any BabyEvent] = []
    @ObservationIgnored private var children: [Child]?
    @ObservationIgnored private var childrenAtStart: [Child] = []
    /** Цели, которые были на экране во время текущего шага, — для `StepDone.visited`. */
    @ObservationIgnored private var visited: Set<GuideTarget> = []

    init(topic: HelpTopic, clock: GuideClock) {
        self.topic = topic
        self.clock = clock
    }

    func shown(_ target: GuideTarget) {
        onScreen[target] = (onScreen[target] ?? 0) + 1
        visited.insert(target)
        advance()
    }

    func hidden(_ target: GuideTarget) {
        let count = (onScreen[target] ?? 0) - 1
        if count > 0 { onScreen[target] = count } else { onScreen.removeValue(forKey: target) }
        advance()
    }

    /** Дети примера изменились. Первые — точка отсчёта первого шага. */
    func onChildren(_ list: [Child]) {
        if children == nil { childrenAtStart = list }
        children = list
        advance()
    }

    /** Записи примера изменились. Первые — точка отсчёта первого шага. */
    func onEvents(_ list: [any BabyEvent]) {
        if events == nil { atStart = list }
        events = list
        advance()
    }

    /** Кнопка шага: перематывает часы примера на `GuideStep.skipMinutes` и засчитывает шаг. */
    func press() {
        guard let current = step, case .next = current.done else { return }
        clock.skip(minutes: current.skipMinutes)
        moveOn()
        advance()
    }

    /** «Пропустить шаг»: шаг с действием засчитывается без него — гайд не застревает. */
    func skip() {
        guard step != nil else { return }
        moveOn()
        advance()
    }

    private func advance() {
        while let current = step, isDone(current.done) { moveOn() }
    }

    private func isDone(_ done: StepDone) -> Bool {
        switch done {
        case let .records(check):
            events.map { check($0, atStart) } ?? false
        case let .children(check):
            children.map { check($0, childrenAtStart) } ?? false
        case let .shown(target):
            onScreen[target] != nil
        case let .visited(target):
            visited.contains(target) && onScreen[target] == nil
        case .next:
            false
        }
    }

    private func moveOn() {
        index += 1
        atStart = events ?? []
        childrenAtStart = children ?? []
        // Что уже открыто, считается увиденным и в новом шаге: лист, открытый раньше, закроют в нём.
        visited = Set(onScreen.keys)
    }
}

/**
 Часы примера дневника (ADR-0043): идут вместе с настоящими, а кнопка шага перематывает их вперёд.
 Записи примера получают время этих часов.
 */
final class GuideClock: Clock, @unchecked Sendable {
    private let base: any Clock
    private let lock = NSLock()
    private var storedOffset: Int64 = 0

    init(base: any Clock) {
        self.base = base
    }

    var timeZone: TimeZone { base.timeZone }

    func now() -> Date {
        base.now().addingTimeInterval(Double(offset) / 1000)
    }

    func skip(minutes: Int) {
        lock.lock()
        storedOffset += Int64(minutes) * 60_000
        lock.unlock()
    }

    private var offset: Int64 {
        lock.lock()
        defer { lock.unlock() }
        return storedOffset
    }
}
