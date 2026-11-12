import Foundation

/**
 Какую неделю жизни раздел «Развитие» уже открывали у каждого ребёнка (ADR-0037): пока текущую не
 открывали — на пункте панели точка, на полосе советов — «новые». Это настройка телефона, а не дневника:
 в базу и CSV она не попадает.
 */
protocol DevelopmentSeenStore: Sendable {
    /** Последняя открытая неделя жизни по id ребёнка. */
    var seenWeeks: StoreState<[Int64: Int]> { get }

    func markSeen(childId: Int64, week: Int)
}

final class PrefsDevelopmentSeenStore: DevelopmentSeenStore, @unchecked Sendable {
    let seenWeeks: StoreState<[Int64: Int]>
    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        var weeks: [Int64: Int] = [:]
        for (key, value) in defaults.dictionaryRepresentation() where key.hasPrefix(Self.keyPrefix) {
            guard let childId = Int64(key.dropFirst(Self.keyPrefix.count)),
                  let number = value as? NSNumber else { continue }
            weeks[childId] = number.intValue
        }
        seenWeeks = StoreState(weeks)
    }

    func markSeen(childId: Int64, week: Int) {
        defaults.set(week, forKey: Self.keyPrefix + String(childId))
        var updated = seenWeeks.value
        updated[childId] = week
        seenWeeks.set(updated)
    }

    private static let keyPrefix = "development_seen_week_"
}

/** «Развитие» в примере дневника (ADR-0043): открытые там недели не гасят точку в настоящем дневнике. */
final class MemoryDevelopmentSeenStore: DevelopmentSeenStore, @unchecked Sendable {
    let seenWeeks = StoreState<[Int64: Int]>([:])

    func markSeen(childId: Int64, week: Int) {
        var updated = seenWeeks.value
        updated[childId] = week
        seenWeeks.set(updated)
    }
}
