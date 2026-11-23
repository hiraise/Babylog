import Foundation

/** Отрезок суток в долях: 0 — начало суток, 1 — следующая полночь. */
struct DaySpan: Hashable, Sendable {
    var from: Float
    var to: Float
}

/**
 Строка «Ритма дня»: сон — ночной и дневной (ADR-0036), кормления грудью и плач (ADR-0030) — полосами,
 бутылочки и кормления без длительности (до ADR-0015) — рисками, подгузники — точками, заметки (ADR-0031) —
 булавками над строкой; `end` — докуда сутки уже прошли (у сегодня — до `now`).
 */
struct RhythmDay: Sendable {
    var date: Date
    var label: String
    var nightSleeps: [DaySpan]
    var daySleeps: [DaySpan]
    var feedings: [DaySpan]
    var cryings: [DaySpan]
    var instantFeedings: [Float]
    var diapers: [Float]
    var notes: [Float] = []
    var end: Float
    var description: String
}

/** «Ритм дня» за последние `rhythmDays` суток, сегодня сверху; `summary` — средние ночи одной строкой. */
struct RhythmModel: Sendable {
    var days: [RhythmDay]
    var summary: String?
}

let rhythmDays = 7

/** Перерыв в сне до часа (ночное кормление, смена подгузника) не прерывает ночь. */
private let nightGapMillis: Int64 = 60 * 60_000

/** Ночь суток — сон, захватывающий отрезок от полуночи до этого часа. */
private let nightCoreEndHour = 5

/** Средние засыпания и пробуждения показываются, если ночей не меньше стольких. */
private let minNights = 3

private func rhythmCalendar(_ timeZone: TimeZone) -> Calendar {
    var calendar = Calendar(identifier: .gregorian)
    calendar.locale = RU
    calendar.firstWeekday = 2
    calendar.timeZone = timeZone
    return calendar
}

private func rhythmFormatter(_ format: String, timeZone: TimeZone) -> DateFormatter {
    let formatter = DateFormatter()
    formatter.locale = RU
    formatter.calendar = rhythmCalendar(timeZone)
    formatter.timeZone = timeZone
    formatter.shortWeekdaySymbols = ["вс", "пн", "вт", "ср", "чт", "пт", "сб"]
    formatter.shortStandaloneWeekdaySymbols = formatter.shortWeekdaySymbols
    formatter.shortMonthSymbols = ["янв.", "февр.", "мар.", "апр.", "мая", "июн.", "июл.", "авг.", "сент.", "окт.", "нояб.", "дек."]
    formatter.shortStandaloneMonthSymbols = ["янв.", "февр.", "март", "апр.", "май", "июнь", "июль", "авг.", "сент.", "окт.", "нояб.", "дек."]
    formatter.dateFormat = format
    return formatter
}

/** `stats` — итоги тех же суток (`dailyStats`), по возрастанию даты: из них подпись строки для VoiceOver. */
func rhythmModel(events: [any BabyEvent], stats: [DayStats], now: Int64, timeZone: TimeZone) -> RhythmModel {
    let days = stats.reversed().map { rhythmDay(events: events, day: $0, now: now, timeZone: timeZone) }
    return RhythmModel(days: days, summary: rhythmSummary(events: events, dates: stats.map(\.date), now: now, timeZone: timeZone))
}

private func rhythmDay(events: [any BabyEvent], day: DayStats, now: Int64, timeZone: TimeZone) -> RhythmDay {
    let calendar = rhythmCalendar(timeZone)
    let start = Int64(calendar.startOfDay(for: day.date).timeIntervalSince1970 * 1000)
    let nextDate = calendar.date(byAdding: .day, value: 1, to: day.date)!
    let end = Int64(calendar.startOfDay(for: nextDate).timeIntervalSince1970 * 1000)
    let visibleEnd = min(end, now)

    func fraction(_ time: Int64) -> Float {
        min(max(Float(time - start) / Float(end - start), 0), 1)
    }

    func span(_ from: Int64, _ to: Int64) -> DaySpan? {
        guard to > start, from < visibleEnd else { return nil }
        return DaySpan(from: fraction(max(from, start)), to: fraction(min(to, visibleEnd)))
    }

    func instant(_ time: Int64) -> Float? {
        time >= start && time < visibleEnd ? fraction(time) : nil
    }

    var nightSleeps: [DaySpan] = []
    var daySleeps: [DaySpan] = []
    var feedings: [DaySpan] = []
    var cryings: [DaySpan] = []
    var instants: [Float] = []
    var diapers: [Float] = []
    var notes: [Float] = []

    for event in events {
        switch event {
        case let event as Sleep:
            // Сон через 7:00 или 19:00 — две полосы: ночная часть и дневная (ADR-0036).
            let split = splitByNight(start: event.start, end: event.end ?? now, timeZone: timeZone)
            for range in split.night {
                if let value = span(range.start, range.end) { nightSleeps.append(value) }
            }
            for range in split.day {
                if let value = span(range.start, range.end) { daySleeps.append(value) }
            }
        case let event as Feeding:
            if let feedingEnd = event.end, feedingEnd <= event.start {
                if let value = instant(event.start) { instants.append(value) }
            } else if let value = span(event.start, event.end ?? now) {
                feedings.append(value)
            }
        case let event as Bottle:
            if let value = instant(event.start) { instants.append(value) }
        case let event as Diaper:
            if let value = instant(event.start) { diapers.append(value) }
        case let event as Crying:
            if let value = span(event.start, event.end ?? now) { cryings.append(value) }
        case let event as Note:
            if let value = instant(event.start) { notes.append(value) }
        case is Medicine, is Weight, is Height, is Illness, is Temperature:
            // Болезнь — полоса дней на графиках, а не отметка в ритме суток (ADR-0040).
            break
        default:
            break
        }
    }

    let name = day.complete ? rhythmFormatter("EE, d MMM", timeZone: timeZone).string(from: day.date) : "сегодня"
    let description: String
    if !day.tracked {
        description = "\(name): записей нет"
    } else {
        description = "\(name): сон \(formatDuration(day.sleepMillis))\(day.sleepSplit()), "
            + "\(day.feedings) \(plural(day.feedings, one: "кормление", few: "кормления", many: "кормлений")), "
            + "\(day.diaperCount) \(plural(day.diaperCount, one: "подгузник", few: "подгузника", many: "подгузников"))"
            + (day.cryingMillis > 0 ? ", плач \(formatDuration(day.cryingMillis))" : "")
            + (notes.isEmpty ? "" : ", \(notes.count) \(plural(notes.count, one: "заметка", few: "заметки", many: "заметок"))")
    }
    return RhythmDay(
        date: day.date,
        label: day.complete ? rhythmFormatter("EE d", timeZone: timeZone).string(from: day.date) : "сегодня",
        nightSleeps: nightSleeps,
        daySleeps: daySleeps,
        feedings: feedings,
        cryings: cryings,
        instantFeedings: instants,
        diapers: diapers,
        notes: notes,
        end: fraction(visibleEnd),
        description: description
    )
}

/** Ночной сон: от засыпания до утреннего пробуждения, `wakings` — сколько раз просыпался между. */
struct Night: Hashable, Sendable {
    var start: Int64
    var end: Int64
    var wakings: Int
}

private struct SleepBlock {
    var start: Int64
    var end: Int64
    var pieces: Int
    var active: Bool
}

/**
 Ночи, закончившиеся утром суток `dates`. Ночь — подряд идущие сны с перерывами до часа, которые
 захватывают отрезок с полуночи до 5:00 (из них — с наибольшим захватом). Ночь, которая ещё идёт, не в счёт.
 */
func nights(events: [any BabyEvent], dates: [Date], now: Int64, timeZone: TimeZone) -> [Night] {
    var blocks: [SleepBlock] = []
    let sleeps = events.compactMap { $0 as? Sleep }.sorted { $0.start < $1.start }
    for sleep in sleeps {
        let end = sleep.end ?? now
        if let last = blocks.last, sleep.start - last.end <= nightGapMillis {
            let index = blocks.count - 1
            blocks[index].end = max(blocks[index].end, end)
            blocks[index].pieces += 1
            blocks[index].active = blocks[index].active || sleep.isActive
        } else {
            blocks.append(SleepBlock(start: sleep.start, end: end, pieces: 1, active: sleep.isActive))
        }
    }
    let calendar = rhythmCalendar(timeZone)
    return dates.compactMap { date in
        let coreStartDate = calendar.startOfDay(for: date)
        let coreEndDate = calendar.date(byAdding: .hour, value: nightCoreEndHour, to: coreStartDate)!
        let coreStart = Int64(coreStartDate.timeIntervalSince1970 * 1000)
        let coreEnd = Int64(coreEndDate.timeIntervalSince1970 * 1000)
        func overlap(_ block: SleepBlock) -> Int64 {
            min(block.end, coreEnd) - max(block.start, coreStart)
        }
        guard let block = blocks.max(by: { overlap($0) < overlap($1) }),
              overlap(block) > 0,
              !block.active,
              block.end < now - nightGapMillis else { return nil }
        return Night(start: block.start, end: block.end, wakings: block.pieces - 1)
    }
}

/** «Засыпает на ночь ≈ 21:30, просыпается ≈ 06:45, самый долгий сон — 4 ч 30 мин» по суткам `dates`. */
private func rhythmSummary(events: [any BabyEvent], dates: [Date], now: Int64, timeZone: TimeZone) -> String? {
    var parts: [String] = []
    let nightList = nights(events: events, dates: dates, now: now, timeZone: timeZone)
    let calendar = rhythmCalendar(timeZone)
    if nightList.count >= minNights {
        func minuteOfDay(_ time: Int64) -> Int {
            let components = calendar.dateComponents([.hour, .minute, .second], from: Date(timeIntervalSince1970: Double(time) / 1000))
            return (components.hour ?? 0) * 60 + (components.minute ?? 0) + (components.second ?? 0) / 60
        }
        func roundedAverage(_ values: [Int]) -> Int {
            let average = values.reduce(0.0) { $0 + Double($1) } / Double(values.count)
            return Int((average + 0.5).rounded(.down))
        }
        // Засыпание считаем от полудня: 23:50 и 00:10 в среднем — полночь, а не полдень.
        let bedtime = (roundedAverage(nightList.map { (minuteOfDay($0.start) + 720) % 1440 }) + 720) % 1440
        let wake = roundedAverage(nightList.map { minuteOfDay($0.end) })
        parts.append("засыпает на ночь ≈ \(rhythmClock(bedtime))")
        parts.append("просыпается ≈ \(rhythmClock(wake))")
    }
    guard let first = dates.first else { return nil }
    let from = Int64(calendar.startOfDay(for: first).timeIntervalSince1970 * 1000)
    let longest = events.compactMap { $0 as? Sleep }
        .filter { $0.start >= from }
        .compactMap { sleep in sleep.end.map { $0 - sleep.start } }
        .max()
    if let longest { parts.append("самый долгий сон — \(formatDuration(longest))") }
    let joined = parts.joined(separator: ", ")
    return joined.isEmpty ? nil : joined.prefix(1).uppercased() + joined.dropFirst()
}

private func rhythmClock(_ minuteOfDay: Int) -> String {
    String(format: "%02d:%02d", minuteOfDay / 60, minuteOfDay % 60)
}
