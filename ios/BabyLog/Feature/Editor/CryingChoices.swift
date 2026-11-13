import Foundation

private let maxReasons = 10
private let maxHelped = 8
private let maxNoteTags = 10

/** Метки листа: `shown` — чипы своего типа, частые первыми; `other` — подсказки другого типа. */
struct TagChoices: Hashable, Sendable {
    var shown: [String]
    var other: [String]
}

/** Варианты для листа плача: причины-метки и что помогло. */
struct CryingChoices: Hashable, Sendable {
    var reasons: TagChoices
    var helped: [String]
}

/** Варианты из записей ребёнка: сначала использованные по частоте, затем стандартные без повторов. */
func cryingChoices(events: [any BabyEvent]) -> CryingChoices {
    let cryings = events.compactMap { $0 as? Crying }
    return CryingChoices(
        reasons: tagChoices(
            own: cryings.flatMap(\.reasons),
            others: events.compactMap { $0 as? Note }.flatMap(\.tags),
            defaults: defaultCryingReasons,
            max: maxReasons
        ),
        helped: Array(ranked(used: cryings.flatMap(\.helped), defaults: defaultCryingHelped).prefix(maxHelped))
    )
}

/** Метки для листа заметки; причины плача становятся только подсказками. */
func noteTagChoices(events: [any BabyEvent]) -> TagChoices {
    tagChoices(
        own: events.compactMap { $0 as? Note }.flatMap(\.tags),
        others: events.compactMap { $0 as? Crying }.flatMap(\.reasons),
        defaults: defaultNoteTags,
        max: maxNoteTags
    )
}

private func tagChoices(own: [String], others: [String], defaults: [String], max: Int) -> TagChoices {
    let shown = Array(ranked(used: own, defaults: defaults).prefix(max))
    return TagChoices(shown: shown, other: ranked(used: others, defaults: []).filter { !shown.contains($0) })
}

private func ranked(used: [String], defaults: [String]) -> [String] {
    let counts = Dictionary(grouping: used, by: { $0 }).mapValues(\.count)
    let past = counts.keys.sorted {
        let left = counts[$0] ?? 0
        let right = counts[$1] ?? 0
        return left == right ? $0 < $1 : left > right
    }
    var seen = Set<String>()
    return (past + defaults).filter { seen.insert($0).inserted }
}
