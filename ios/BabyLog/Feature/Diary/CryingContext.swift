import Foundation

/** Что было до плача: бодрствование, последнее кормление и подгузник. */
struct CryingContext: Equatable, Sendable {
    var awakeMillis: Int64?
    var usualAwakeMillis: Int64?
    var sinceFeedingMillis: Int64?
    var feeding: String?
    var feedingByBottle: Bool
    var sinceDiaperMillis: Int64?
    var diaper: DiaperKind?
}

/**
 «Что было до» плача `crying` по записям ребёнка `events`: от последнего сна, кормления
 и подгузника до начала плача, и обычное бодрствование — медиана окон за неделю перед ним.
 Записи давнее `maxAwakeMillis` — забытые, а не «не спит 9 часов»: их нет в карточке.
 */
func cryingContext(events: [any BabyEvent], crying: Crying) -> CryingContext {
    let at = crying.start
    func recent(_ time: Int64?) -> Int64? {
        guard let time, (0...maxAwakeMillis).contains(at - time) else { return nil }
        return time
    }

    let sleepEnd = recent(events.lastSleepEndBefore(at))
    let usual = events.usualAwakeBefore(at)
    let session = feedingSessions(events: events)
        .filter { $0.end != nil && $0.end! <= at }
        .max { ($0.end ?? 0) < ($1.end ?? 0) }
    let bottle = events.compactMap { $0 as? Bottle }.filter { $0.start <= at }.max { $0.start < $1.start }
    let byBottle = bottle != nil && (session?.end ?? Int64.min) < bottle!.start
    let feedingEnd = recent(byBottle ? bottle?.start : session?.end)
    let feeding: String?
    if feedingEnd == nil {
        feeding = nil
    } else if byBottle, let bottle {
        feeding = bottleLabel(bottle.volumeMl, milk: bottle.milk)
    } else if let session {
        feeding = "\(breastLabel(session.lastBreast)), \(formatDuration((session.end ?? session.start) - session.start))"
    } else {
        feeding = nil
    }

    let diaper = events.compactMap { $0 as? Diaper }.filter { $0.start <= at }.max { $0.start < $1.start }
    let diaperTime = recent(diaper?.start)
    return CryingContext(
        awakeMillis: sleepEnd.map { at - $0 },
        usualAwakeMillis: usual,
        sinceFeedingMillis: feedingEnd.map { at - $0 },
        feeding: feeding,
        feedingByBottle: byBottle,
        sinceDiaperMillis: diaperTime.map { at - $0 },
        diaper: diaperTime == nil ? nil : diaper?.kind
    )
}
