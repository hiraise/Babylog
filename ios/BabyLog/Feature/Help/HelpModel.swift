import Foundation

/**
 Тема справки (ADR-0043). Текст шагов — одновременно справка на экране темы и подсказки гайда,
 который проходит тему на примере дневника.

 `id` не меняется: по нему помнятся открытые и пройденные темы.
 `start` — раздел примера, с которого начинается гайд.
 */
struct HelpTopic: Hashable {
    let id: String
    let title: String
    let icon: String
    let summary: String
    let steps: [GuideStep]
    let start: GuideStart

    init(
        id: String,
        title: String,
        icon: String,
        summary: String,
        steps: [GuideStep],
        start: GuideStart = .today
    ) {
        self.id = id
        self.title = title
        self.icon = icon
        self.summary = summary
        self.steps = steps
        self.start = start
    }
}

/** Раздел главного экрана, с которого гайд открывает пример. */
enum GuideStart: Hashable, Sendable {
    case today
    case diary
    case development
    case analytics
}

/**
 Шаг темы.

 `text` — что сделать: так он читается и на экране темы, и в панели гайда.
 `target` — куда нажать: гайд обводит этот элемент.
 `sheetHint` — текст открытого нижнего листа, если там нужно сказать другое, чем `text`.
 `button` — подпись кнопки шага со `StepDone.next`; nil — «Далее».
 `skipMinutes` — на сколько кнопка шага перематывает часы примера.
 `demoOnly` — шаг только для гайда: на экране темы его нет.
 `since` — версия, в которой шаг появился или изменился; nil — шаг первой версии справки.
 */
final class GuideStep: Hashable {
    let text: String
    let done: StepDone
    let target: GuideTarget?
    let sheetHint: String?
    let button: String?
    let skipMinutes: Int
    let demoOnly: Bool
    let since: String?

    init(
        text: String,
        done: StepDone = .next,
        target: GuideTarget? = nil,
        sheetHint: String? = nil,
        button: String? = nil,
        skipMinutes: Int = 0,
        demoOnly: Bool = false,
        since: String? = nil
    ) {
        self.text = text
        self.done = done
        self.target = target
        self.sheetHint = sheetHint
        self.button = button
        self.skipMinutes = skipMinutes
        self.demoOnly = demoOnly
        self.since = since
    }

    static func == (lhs: GuideStep, rhs: GuideStep) -> Bool { lhs === rhs }
    func hash(into hasher: inout Hasher) { hasher.combine(ObjectIdentifier(self)) }
}

/**
 Когда шаг засчитан. Засчитанный шаг не откатывается, даже если условие потом перестало выполняться.
 Шаг, который не засчитывается кнопкой, можно пропустить («Пропустить шаг» на панели).
 */
enum StepDone {
    /** По записям примера: проверка получает записи сейчас и в начале шага. */
    case records(check: (_ now: [any BabyEvent], _ atStart: [any BabyEvent]) -> Bool)

    /** По детям примера: проверка получает детей сейчас и в начале шага. */
    case children(check: (_ now: [Child], _ atStart: [Child]) -> Bool)

    /** Цель появилась на экране: открылся раздел, экран или лист. */
    case shown(GuideTarget)

    /** Цель была на экране во время шага и закрылась. */
    case visited(GuideTarget)

    /** Кнопкой шага. */
    case next
}

extension HelpTopic {
    /** Шаги для экрана темы — без шагов только для гайда. */
    var readingSteps: [GuideStep] { steps.filter { !$0.demoOnly } }

    /**
     Шаги темы, помеченные «Новое»: новее версии, на которой тему открывали, а если не открывали —
     новее первого запуска со справкой.
     */
    func newSteps(progress: HelpProgress) -> Set<GuideStep> {
        let seen = progress.opened[id] ?? progress.baseline
        return Set(steps.filter { step in
            guard let since = step.since else { return false }
            guard let seen else { return true }
            return compareVersions(since, seen) > 0
        })
    }

    func isNew(progress: HelpProgress) -> Bool { !newSteps(progress: progress).isEmpty }
}

/** Точка на ⋮ и у «Справки»: в справке есть новое или ещё не сказали, где она. */
func helpBadge(topics: [HelpTopic], progress: HelpProgress) -> Bool {
    !progress.pointerDone || topics.contains { $0.isNew(progress: progress) }
}

/** С какой темы продолжать «по порядку»: первая непройденная, а если пройдены все — первая. */
func nextUnfinished(topics: [HelpTopic], progress: HelpProgress) -> HelpTopic? {
    topics.first { !progress.completed.contains($0.id) } ?? topics.first
}

/** Тема после `topic` в списке; nil — это последняя. */
func topicAfter(topics: [HelpTopic], topic: HelpTopic) -> HelpTopic? {
    guard let index = topics.firstIndex(where: { $0.id == topic.id }) else { return topics.first }
    let next = index + 1
    return topics.indices.contains(next) ? topics[next] : nil
}

/**
 Сравнивает версии по числам: «1.10» новее «1.9». Хвост после «-» («1.9-debug») не считается,
 нечисловая часть — ноль.
 */
func compareVersions(_ a: String, _ b: String) -> Int {
    func parts(_ version: String) -> [Int] {
        version.split(separator: "-", maxSplits: 1).first?
            .split(separator: ".", omittingEmptySubsequences: false)
            .map { Int($0) ?? 0 } ?? []
    }
    let left = parts(a)
    let right = parts(b)
    for index in 0..<max(left.count, right.count) {
        let l = index < left.count ? left[index] : 0
        let r = index < right.count ? right[index] : 0
        if l != r { return l < r ? -1 : 1 }
    }
    return 0
}
