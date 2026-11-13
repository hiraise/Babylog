import Foundation

/**
 Итоги одного дня для одного ребёнка (ADR-0026): сколько и сколько всего. Что идёт сейчас и что
 было последним, здесь нет — это показывают плитки «Сегодня».
 */
struct DaySummary: Sendable {
    /** Кормления грудью, начатые в этот день; смена груди не считается новым кормлением. */
    var breastFeedings: Int
    /** Бутылочки этого дня: сколько раз и сколько всего миллилитров. */
    var bottles: Int
    var bottleMl: Int
    var diapers: [DiaperKind: Int]
    /** Сон, пересекающийся с этими сутками; активный считается до `now`. */
    var sleepMillis: Int64
    /** Лекарства: название из последней записи и число приёмов; названия сравниваются без регистра. */
    var medicines: [(name: String, count: Int)]
    /** Плач, начатый в этот день, и его время внутри суток; идущий — до `now`, как сон. */
    var cryings: Int = 0
    var cryingMillis: Int64 = 0
    /** Замеры температуры этого дня: сколько и самая высокая в десятых градуса; nil — не мерили. */
    var temperatures: Int = 0
    var maxTemperatureTenths: Int? = nil

    var feedings: Int { breastFeedings + bottles }
    var diaperCount: Int { diapers.values.reduce(0, +) }
}

/** Кормление целиком: запись или несколько записей встык при смене груди. */
struct FeedingSession: Hashable, Sendable {
    var start: Int64
    var end: Int64?
    var lastBreast: Breast

    var isActive: Bool { end == nil }
}

/** Кормления по возрастанию начала: запись, начатая ровно в конце предыдущей, — её продолжение. */
func feedingSessions(events: [any BabyEvent]) -> [FeedingSession] {
    var sessions: [FeedingSession] = []
    let feedings = events.compactMap { $0 as? Feeding }.sorted {
        $0.start == $1.start ? $0.id < $1.id : $0.start < $1.start
    }
    for feeding in feedings {
        if let last = sessions.last, last.end != nil, last.end == feeding.start {
            sessions[sessions.count - 1] = FeedingSession(
                start: last.start,
                end: feeding.end,
                lastBreast: feeding.breast
            )
        } else {
            sessions.append(FeedingSession(start: feeding.start, end: feeding.end, lastBreast: feeding.breast))
        }
    }
    return sessions
}

/** `events` — записи одного ребёнка в любом порядке. */
func summarizeDay(events: [any BabyEvent], date: Date, now: Int64, timeZone: TimeZone) -> DaySummary {
    var calendar = Calendar(identifier: .gregorian)
    calendar.locale = RU
    calendar.firstWeekday = 2
    calendar.timeZone = timeZone
    let dayStartDate = calendar.startOfDay(for: date)
    let dayEndDate = calendar.date(byAdding: .day, value: 1, to: dayStartDate)!
    let dayStart = Int64(dayStartDate.timeIntervalSince1970 * 1000)
    let dayEnd = Int64(dayEndDate.timeIntervalSince1970 * 1000)

    let newestFirst = events.sorted { $0.start == $1.start ? $0.id > $1.id : $0.start > $1.start }
    let dayEvents = newestFirst.filter { $0.start >= dayStart && $0.start < dayEnd }
    let feedings = feedingSessions(events: events)
    let dayBottles = dayEvents.compactMap { $0 as? Bottle }

    let sleepMillis = events.compactMap { $0 as? Sleep }.reduce(Int64(0)) { total, sleep in
        let start = max(sleep.start, dayStart)
        let end = min(sleep.end ?? now, dayEnd)
        return total + max(end - start, 0)
    }
    let cryingEvents = events.compactMap { $0 as? Crying }
    let cryingMillis = cryingEvents.reduce(Int64(0)) { total, crying in
        total + max(min(crying.end ?? now, dayEnd) - max(crying.start, dayStart), 0)
    }

    var medicineGroups: [String: [Medicine]] = [:]
    var medicineOrder: [String] = []
    for medicine in dayEvents.compactMap({ $0 as? Medicine }) {
        let key = medicine.name.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        if medicineGroups[key] == nil { medicineOrder.append(key) }
        medicineGroups[key, default: []].append(medicine)
    }
    let medicines = medicineOrder.map { key in
        let group = medicineGroups[key]!
        return (group[0].name.trimmingCharacters(in: .whitespacesAndNewlines), group.count)
    }
    let temperatures = dayEvents.compactMap { $0 as? Temperature }
    var diapers: [DiaperKind: Int] = [:]
    for diaper in dayEvents.compactMap({ $0 as? Diaper }) { diapers[diaper.kind, default: 0] += 1 }

    return DaySummary(
        breastFeedings: feedings.count { $0.start >= dayStart && $0.start < dayEnd },
        bottles: dayBottles.count,
        bottleMl: dayBottles.reduce(0) { $0 + $1.volumeMl },
        diapers: diapers,
        sleepMillis: sleepMillis,
        medicines: medicines,
        cryings: cryingEvents.count { $0.start >= dayStart && $0.start < dayEnd },
        cryingMillis: cryingMillis,
        temperatures: temperatures.count,
        maxTemperatureTenths: temperatures.map(\.tenths).max()
    )
}
