import Foundation

/** «Обычно» — только если до болезни столько суток с записями: по одному-двум дням это ещё не «обычно». */
private let minUsualDays = 3

/**
 Карточка болезни на «Сегодня» (ADR-0040): какой день, названия (nil — без названия), последняя
 температура и последнее лекарство — только факты, без оценок.
 */
struct IllnessCardModel: Sendable {
    var illness: Illness
    var day: Int
    var names: String?
    var lastTemperature: Temperature?
    var lastMedicine: IllnessMedicine?
}

func illnessCardModel(events: [any BabyEvent], illness: Illness, now: Int64, timeZone: TimeZone) -> IllnessCardModel {
    let records = events.illnessRecords(illness, now: now)
    let names = illness.names.map(\.name).joined(separator: ", ")
    return IllnessCardModel(
        illness: illness,
        day: illness.dayNumber(toLocalDate(now, timeZone: timeZone), timeZone: timeZone),
        names: names.isEmpty ? nil : names,
        lastTemperature: records.compactMap { $0 as? Temperature }.max { $0.start < $1.start },
        lastMedicine: events.illnessMedicines(illness, now: now).first
    )
}

/** Что записано за сутки и что обычно: сравнение «по дневнику» на экране болезни. */
struct DiaryLine: Sendable {
    var kind: DiaryLineKind
    var value: Double
    var usual: Double?
}

enum DiaryLineKind: CaseIterable, Sendable {
    case wetDiapers
    case feedings
    case sleep
    case crying
}

/**
 «По дневнику» (ADR-0040): у идущей болезни — вчера, у прошлой — в среднем за дни болезни; рядом —
 обычно, за `usualDays` суток до болезни. `yesterday` — сравниваются только вчерашние сутки.
 */
struct DiaryComparison: Sendable {
    var yesterday: Bool
    var lines: [DiaryLine]
}

/** Строка «По дням»: запись болезни, добавленное название, начало или конец. */
enum DayEntry: Sendable {
    case record(any BabyEvent)
    case nameAdded(IllnessName)
    case started(Int64)
    case ended(Int64)

    var time: Int64? {
        switch self {
        case let .record(event): event.start
        case .nameAdded: nil
        case let .started(time), let .ended(time): time
        }
    }
}

/** Сутки болезни на её экране: какой это день болезни и что в нём было, свежее сверху. */
struct IllnessDay: Sendable {
    var date: Date
    var number: Int
    var entries: [DayEntry]
}

/**
 Экран болезни (ADR-0040): записи болезни (`illnessRecords`), температура по времени, лекарства, «по
 дневнику» и сутки со всем, что в них было, — свежие сверху.
 */
struct IllnessScreenModel: Sendable {
    var illness: Illness
    var today: Date
    var days: Int
    var temperatures: [Temperature]
    var medicines: [IllnessMedicine]
    var notes: [Note]
    var diary: DiaryComparison?
    var byDay: [IllnessDay]
}

func illnessScreenModel(events: [any BabyEvent], illness: Illness, now: Int64, timeZone: TimeZone) -> IllnessScreenModel {
    let today = toLocalDate(now, timeZone: timeZone)
    let records = events.illnessRecords(illness, now: now)
    let first = illness.startDate(timeZone)
    let lastRecord = records.map { toLocalDate($0.start, timeZone: timeZone) }.max() ?? first
    let last = max(illness.lastDate(today: today, timeZone: timeZone), lastRecord)
    var days: [IllnessDay] = []
    var date = last
    while date >= first {
        var entries: [DayEntry] = []
        if let end = illness.end, toLocalDate(end, timeZone: timeZone) == date {
            entries.append(.ended(end))
        }
        entries.append(contentsOf: records
            .filter { toLocalDate($0.start, timeZone: timeZone) == date }
            .sorted { $0.start == $1.start ? $0.id < $1.id : $0.start > $1.start }
            .map(DayEntry.record))
        entries.append(contentsOf: illness.names
            .filter { $0.sinceDate(illness, timeZone: timeZone) == date }
            .map(DayEntry.nameAdded))
        if date == first { entries.append(.started(illness.start)) }
        days.append(IllnessDay(date: date, number: illness.dayNumber(date, timeZone: timeZone), entries: entries))
        guard let previous = illnessCalendar(timeZone).date(byAdding: .day, value: -1, to: date) else { break }
        date = previous
    }
    return IllnessScreenModel(
        illness: illness,
        today: today,
        days: illness.days(today: today, timeZone: timeZone),
        temperatures: records.compactMap { $0 as? Temperature },
        medicines: events.illnessMedicines(illness, now: now),
        notes: records.compactMap { $0 as? Note },
        diary: diaryComparison(events: events, illness: illness, now: now, timeZone: timeZone),
        byDay: days
    )
}

/**
 «По дневнику»: подгузники с мочой (мокрые и «и то, и то»), кормления, сон и — если плач записывают —
 плач. Сутки без записей режима не считаются ни «вчера», ни «обычно». nil — сравнивать нечего: болезнь
 началась сегодня, или вчера дневник не вели.
 */
func diaryComparison(events: [any BabyEvent], illness: Illness, now: Int64, timeZone: TimeZone) -> DiaryComparison? {
    let today = toLocalDate(now, timeZone: timeZone)
    let first = illness.startDate(timeZone)
    let calendar = illnessCalendar(timeZone)
    let yesterday = illness.isActive
    var sick: [Date] = []
    if yesterday {
        if let day = calendar.date(byAdding: .day, value: -1, to: today), day >= first { sick = [day] }
    } else {
        let last = illness.lastDate(today: today, timeZone: timeZone)
        var date = first
        while date <= last && date < today {
            sick.append(date)
            guard let next = calendar.date(byAdding: .day, value: 1, to: date) else { break }
            date = next
        }
    }
    let sickDays = sick.map { summarizeDay(events: events, date: $0, now: now, timeZone: timeZone) }.filter(\.tracked)
    guard !sickDays.isEmpty else { return nil }
    let usual = (1...Int(usualDays)).compactMap { back in
        calendar.date(byAdding: .day, value: -back, to: first).map {
            summarizeDay(events: events, date: $0, now: now, timeZone: timeZone)
        }
    }.filter(\.tracked)
    let usualDays = usual.count >= minUsualDays ? usual : nil
    let cries = events.contains { $0 is Crying }
    let lines = DiaryLineKind.allCases.filter { $0 != .crying || cries }.map { kind in
        DiaryLine(
            kind: kind,
            value: sickDays.map { $0.value(kind) }.average(),
            usual: usualDays?.map { $0.value(kind) }.average()
        )
    }
    return DiaryComparison(yesterday: yesterday, lines: lines)
}

private extension DaySummary {
    var tracked: Bool { feedings > 0 || diaperCount > 0 || sleepMillis > 0 }

    func value(_ kind: DiaryLineKind) -> Double {
        switch kind {
        case .wetDiapers: Double((diapers[.wet] ?? 0) + (diapers[.mixed] ?? 0))
        case .feedings: Double(feedings)
        case .sleep: Double(sleepMillis)
        case .crying: Double(cryingMillis)
        }
    }
}

private extension Array where Element == Double {
    func average() -> Double { reduce(0, +) / Double(count) }
}

/** Значение строки «по дневнику» словами: «5», «10 ч 50 мин», среднее — с округлением до целого. */
extension DiaryLine {
    func format(_ value: Double) -> String {
        switch kind {
        case .wetDiapers, .feedings:
            String(Int(value.rounded()))
        case .sleep, .crying:
            // «0 мин», а не «меньше минуты»: плача в эти сутки не было
            value < 60_000 ? "0 мин" : formatDuration(Int64(value.rounded()))
        }
    }
}

func illnessCalendar(_ timeZone: TimeZone) -> Calendar {
    var calendar = Calendar(identifier: .gregorian)
    calendar.locale = RU
    calendar.firstWeekday = 2
    calendar.timeZone = timeZone
    return calendar
}
