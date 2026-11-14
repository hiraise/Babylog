import Foundation

/**
 Итоги одних суток для графиков (ADR-0028). Считаются так же, как сводка дня (`summarizeDay`):
 кормление — сессия груди или бутылочка, начатые в эти сутки; сон — его часть внутри суток,
 идущий — до `now`. Сводка и график одного дня показывают одни и те же числа.
 */
struct DayStats: Sendable {
    var date: Date
    /**
     В эти сутки начались кормление, бутылочка, сон или подгузник. Сутки без таких записей —
     «дневник не вели», а не нули: одно взвешивание или лекарство режим дня не описывает.
     */
    var tracked: Bool
    /** Сутки закончились — это не сегодня. */
    var complete: Bool
    var breastFeedings: Int
    var bottles: Int
    var bottleMl: Int
    /**
     Время у груди; nil — неизвестно: у какого-то кормления этих суток нет длительности
     (записи до ADR-0015, у них конец равен началу) или оно ещё идёт. Длительность не придумываем.
     */
    var breastMillis: Int64?
    var sleepMillis: Int64
    /**
     Часть `sleepMillis` ночью (ADR-0036): с 0:00 до 7:00 и с 19:00 до полуночи этих суток (`splitByNight`).
     Ночь и день вместе — сон суток из сводки.
     */
    var nightSleepMillis: Int64 = 0
    /** Самый долгий законченный сон, начатый в эти сутки. */
    var longestSleepMillis: Int64
    var diapers: [DiaperKind: Int]
    /** Начала кормлений — сессий груди и бутылочек — по возрастанию. */
    var feedingStarts: [Int64]
    /** Начала снов, начатых в эти сутки, по возрастанию: сколько раз спал и как часто. */
    var sleepStarts: [Int64] = []
    /** Плач (ADR-0030), начатый в эти сутки: сколько раз и во сколько — минуты от полуночи. */
    var cryings: Int = 0
    var cryingStartMinutes: [Int] = []
    /** Плач внутри суток, как сон: через полночь делится, идущий — до `now`. */
    var cryingMillis: Int64 = 0
    /** В эти сутки ребёнок болел (ADR-0040): их столбик на графиках — на полосе болезни. В средние входят как есть. */
    var ill: Bool = false

    var feedings: Int { breastFeedings + bottles }
    var sleeps: Int { sleepStarts.count }
    var daySleepMillis: Int64 { sleepMillis - nightSleepMillis }
    var diaperCount: Int { diapers.values.reduce(0, +) }

    /** Сутки идут в средние, только если закончились и в них вели дневник. */
    var countable: Bool { tracked && complete }
}

private final class DayAccumulator {
    var tracked = false
    var breastFeedings = 0
    var bottles = 0
    var bottleMl = 0
    var breastMillis: Int64 = 0
    var breastUnknown = false
    var sleepMillis: Int64 = 0
    var nightSleepMillis: Int64 = 0
    var longestSleepMillis: Int64 = 0
    var diapers: [DiaperKind: Int] = [:]
    var feedingStarts: [Int64] = []
    var sleepStarts: [Int64] = []
    var cryings = 0
    var cryingStartMinutes: [Int] = []
    var cryingMillis: Int64 = 0
    var ill = false
}

/**
 Итоги каждых суток с `from` по `to` включительно, по порядку. `events` — записи одного ребёнка
 в любом порядке. Один проход по записям: на «всё время» за год это доли секунды.
 */
func dailyStats(events: [any BabyEvent], from: Date, to: Date, now: Int64, timeZone: TimeZone) -> [DayStats] {
    guard to >= from else { return [] }
    var calendar = Calendar(identifier: .gregorian)
    calendar.locale = RU
    calendar.firstWeekday = 2
    calendar.timeZone = timeZone
    let today = toLocalDate(now, timeZone: timeZone)
    var dates: [Date] = []
    var days: [Date: DayAccumulator] = [:]
    var date = from
    while date <= to {
        dates.append(date)
        days[date] = DayAccumulator()
        date = calendar.date(byAdding: .day, value: 1, to: date)!
    }

    func dateOf(_ millis: Int64) -> Date {
        toLocalDate(millis, timeZone: timeZone)
    }

    func startOf(_ date: Date) -> Int64 {
        Int64(calendar.startOfDay(for: date).timeIntervalSince1970 * 1000)
    }

    /** Часть отрезка `[start, end)` внутри каждых суток периода. */
    func spread(_ start: Int64, _ end: Int64, add: (DayAccumulator, Int64) -> Void) {
        var date = max(dateOf(start), from)
        let last = min(dateOf(end), to)
        while date <= last {
            let next = calendar.date(byAdding: .day, value: 1, to: date)!
            let overlap = min(end, startOf(next)) - max(start, startOf(date))
            if overlap > 0, let day = days[date] { add(day, overlap) }
            date = next
        }
    }

    for session in feedingSessions(events: events) {
        guard let day = days[dateOf(session.start)] else { continue }
        day.breastFeedings += 1
        day.feedingStarts.append(session.start)
    }

    for event in events {
        switch event {
        case let event as Feeding:
            guard let day = days[dateOf(event.start)] else { continue }
            day.tracked = true
            if event.end == nil || event.end! <= event.start {
                day.breastUnknown = true
            } else {
                day.breastMillis += event.end! - event.start
            }
        case let event as Bottle:
            guard let day = days[dateOf(event.start)] else { continue }
            day.tracked = true
            day.bottles += 1
            day.bottleMl += event.volumeMl
            day.feedingStarts.append(event.start)
        case let event as Diaper:
            guard let day = days[dateOf(event.start)] else { continue }
            day.tracked = true
            day.diapers[event.kind, default: 0] += 1
        case let event as Sleep:
            if let day = days[dateOf(event.start)] {
                day.tracked = true
                day.sleepStarts.append(event.start)
            }
            let end = event.end ?? now
            spread(event.start, end) { day, overlap in day.sleepMillis += overlap }
            for range in splitByNight(start: event.start, end: end, timeZone: timeZone).night {
                spread(range.start, range.end) { day, overlap in day.nightSleepMillis += overlap }
            }
            if let end = event.end, let day = days[dateOf(event.start)] {
                day.longestSleepMillis = max(day.longestSleepMillis, end - event.start)
            }
        case let event as Crying:
            // Плач — не запись режима: сутки только с плачем дневником не считаются (`tracked` не ставим).
            let components = calendar.dateComponents([.hour, .minute], from: Date(timeIntervalSince1970: Double(event.start) / 1000))
            if let day = days[dateOf(event.start)] {
                day.cryings += 1
                day.cryingStartMinutes.append((components.hour ?? 0) * 60 + (components.minute ?? 0))
            }
            spread(event.start, event.end ?? now) { day, overlap in day.cryingMillis += overlap }
        case let event as Illness:
            // Болезнь захватывает сутки целиком: с дня начала по день конца, идущая — по сегодня (ADR-0040).
            var illnessDate = max(dateOf(event.start), from)
            let last = min(dateOf(event.end ?? now), to)
            while illnessDate <= last {
                days[illnessDate]?.ill = true
                illnessDate = calendar.date(byAdding: .day, value: 1, to: illnessDate)!
            }
        case is Medicine, is Weight, is Height, is Note, is Temperature:
            // Вес, лекарства, заметки и температура в суточные итоги не входят: у них свои карточки.
            break
        default:
            break
        }
    }

    return dates.map { date in
        let day = days[date]!
        return DayStats(
            date: date,
            tracked: day.tracked,
            complete: date < today,
            breastFeedings: day.breastFeedings,
            bottles: day.bottles,
            bottleMl: day.bottleMl,
            breastMillis: day.breastUnknown ? nil : day.breastMillis,
            sleepMillis: day.sleepMillis,
            nightSleepMillis: day.nightSleepMillis,
            longestSleepMillis: day.longestSleepMillis,
            diapers: day.diapers,
            feedingStarts: day.feedingStarts.sorted(),
            sleepStarts: day.sleepStarts.sorted(),
            cryings: day.cryings,
            cryingStartMinutes: day.cryingStartMinutes.sorted(),
            cryingMillis: day.cryingMillis,
            ill: day.ill
        )
    }
}
