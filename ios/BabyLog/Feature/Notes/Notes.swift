import Foundation

/** Меток в ряду фильтров — не больше стольких: дальше редкие, их видно на записях. */
private let maxFilterTags = 12

/** Календарь метки — столько недель с понедельника, последняя — текущая. */
let tagWeeks = 4

/** «Что помогало» и «Часто вместе» — не больше стольких: дальше единичные случаи. */
private let topItems = 6

/**
 Что показывает лента «Заметок» (ADR-0031): всё, только плач, только заметки или записи с меткой — тогда
 над лентой сводка метки. `illnesses` — вместо ленты болезни карточками (ADR-0040).
 */
enum NotesFilter: Hashable, Sendable {
    case all
    case cryingOnly
    case notesOnly
    case illnesses
    case tag(String)

    /** В ленте только плач и заметки; у «Болезней» ленты нет — там карточки. */
    func matches(_ event: any BabyEvent) -> Bool {
        guard event is Crying || event is Note else { return false }
        return switch self {
        case .all: true
        case .cryingOnly: event is Crying
        case .notesOnly: event is Note
        case .illnesses: false
        case let .tag(tag): event.eventTags.contains(tag)
        }
    }
}

/**
 Болезнь карточкой в «Заметках» (ADR-0040): названия, дни, сколько дней,
 самая высокая температура и чем лечили.
 */
struct IllnessEntry: Sendable {
    var illness: Illness
    var title: String?
    var range: String
    var days: Int
    var maxTenths: Int?
    var medicines: [String]
}

/** Болезни ребёнка, свежие сверху: идущая первой. */
func illnessEntries(events: [any BabyEvent], now: Int64, timeZone: TimeZone) -> [IllnessEntry] {
    let today = toLocalDate(now, timeZone: timeZone)
    return events.compactMap { $0 as? Illness }.map { illness in
        let records = events.illnessRecords(illness, now: now)
        let first = illness.startDate(timeZone)
        return IllnessEntry(
            illness: illness,
            title: illness.title(),
            range: illness.isActive
                ? "с \(formatDayRange(first: first, last: first, timeZone: timeZone))"
                : formatDayRange(first: first, last: illness.lastDate(today: today, timeZone: timeZone), timeZone: timeZone),
            days: illness.days(today: today, timeZone: timeZone),
            maxTenths: records.compactMap { ($0 as? Temperature)?.tenths }.max(),
            medicines: events.illnessMedicines(illness, now: now)
                .sorted { ($0.times.first ?? 0) < ($1.times.first ?? 0) }
                .map(\.name)
        )
    }.sorted {
        if $0.illness.isActive != $1.illness.isActive { return $0.illness.isActive }
        return $0.illness.start > $1.illness.start
    }
}

/** Записи ленты за один день, свежие сверху. */
struct FeedDay: Sendable {
    var date: Date
    var records: [any BabyEvent]
}

/** Плач и заметки из `events` по `filter` — по дням, свежие сверху. */
func notesFeed(events: [any BabyEvent], filter: NotesFilter, timeZone: TimeZone) -> [FeedDay] {
    let records = events.filter(filter.matches).sorted {
        $0.start == $1.start ? $0.id > $1.id : $0.start > $1.start
    }
    return Dictionary(grouping: records) { toLocalDate($0.start, timeZone: timeZone) }
        .map { FeedDay(date: $0.key, records: $0.value) }
        .sorted { $0.date > $1.date }
}

/** Метки для ряда фильтров: из плача и заметок, частые первыми, при равенстве — по алфавиту. */
func filterTags(events: [any BabyEvent]) -> [String] {
    counted(events.flatMap(\.eventTags)).prefix(maxFilterTags).map(\.name)
}

/** День календаря метки: сколько раз плакал с этой меткой, была ли заметка с ней. */
struct TagDay: Hashable, Sendable {
    var date: Date
    var cryings: Int
    var note: Bool
    var today: Bool
    var future: Bool
}

/** Сколько раз встретилось значение; частые первыми, при равенстве — по алфавиту. */
struct TagCount: Hashable, Sendable {
    var name: String
    var count: Int
}

/**
 Сводка метки над лентой (ADR-0031): сколько раз плач и заметки с ней, календарь последних
 `tagWeeks` недель, когда начинался плач, что было перед ним, что помогало и какие метки рядом.
 Совпадения в записях, а не причины: так и подписано на экране.
 */
struct TagSummary: Sendable {
    var tag: String
    var cryings: Int
    var notes: Int
    var days: [TagDay]
    /** Начала плача с меткой — минуты от полуночи; `peak` — когда чаще. */
    var cryingStarts: [Int]
    var peak: PeakHours?
    /** Сколько раз из `awakeKnown` плач начался после бодрствования дольше обычного. */
    var longerAwake: Int
    var awakeKnown: Int
    /** Сколько обычно прошло после кормления — медиана; nil — кормлений рядом не было. */
    var sinceFeeding: Int64?
    var helped: [TagCount]
    var together: [TagCount]
}

/** Сводка метки `tag` по записям ребёнка `events`; `now` — для календаря и «сегодня». */
func tagSummary(events: [any BabyEvent], tag: String, now: Int64, timeZone: TimeZone) -> TagSummary {
    let calendar = notesCalendar(timeZone)
    let tagged = events.filter { $0.eventTags.contains(tag) }
    let cryings = tagged.compactMap { $0 as? Crying }
    let today = toLocalDate(now, timeZone: timeZone)
    let daysFromMonday = (calendar.component(.weekday, from: today) + 5) % 7
    let firstDay = calendar.date(byAdding: .day, value: -daysFromMonday - (tagWeeks - 1) * 7, to: today)!
    let cryingDays = Dictionary(grouping: cryings) { toLocalDate($0.start, timeZone: timeZone) }.mapValues(\.count)
    let noteDays = Set(tagged.compactMap { $0 as? Note }.map { toLocalDate($0.start, timeZone: timeZone) })
    let days = (0..<(tagWeeks * 7)).map { offset in
        let date = calendar.date(byAdding: .day, value: offset, to: firstDay)!
        return TagDay(
            date: date,
            cryings: cryingDays[date] ?? 0,
            note: noteDays.contains(date),
            today: date == today,
            future: date > today
        )
    }

    let starts = cryings.map {
        let components = calendar.dateComponents([.hour, .minute], from: Date(timeIntervalSince1970: Double($0.start) / 1000))
        return (components.hour ?? 0) * 60 + (components.minute ?? 0)
    }
    let awake: [Bool] = cryings.compactMap { crying in
        guard let sleepEnd = events.lastSleepEndBefore(crying.start),
              let usual = events.usualAwakeBefore(crying.start) else { return nil }
        let awakeFor = crying.start - sleepEnd
        guard awakeFor <= maxAwakeMillis else { return nil }
        return Double(awakeFor) > Double(usual) * longerThanUsual
    }
    let sinceFeeding = cryings.compactMap { crying in
        events.lastFeedingEndBefore(crying.start).flatMap { end in
            let elapsed = crying.start - end
            return elapsed <= maxAwakeMillis ? elapsed : nil
        }
    }
    return TagSummary(
        tag: tag,
        cryings: cryings.count,
        notes: tagged.count - cryings.count,
        days: days,
        cryingStarts: starts,
        peak: peakHours(starts),
        longerAwake: awake.filter { $0 }.count,
        awakeKnown: awake.count,
        sinceFeeding: sinceFeeding.median(),
        helped: Array(counted(cryings.flatMap(\.helped)).prefix(topItems)),
        together: Array(counted(tagged.flatMap { $0.eventTags.filter { $0 != tag } }).prefix(topItems))
    )
}

/** Сколько раз встречается каждое из `items`: частые первыми, при равенстве — по алфавиту. */
private func counted(_ items: [String]) -> [TagCount] {
    Dictionary(grouping: items) { $0 }.map { TagCount(name: $0.key, count: $0.value.count) }.sorted {
        $0.count == $1.count ? $0.name < $1.name : $0.count > $1.count
    }
}

private func notesCalendar(_ timeZone: TimeZone) -> Calendar {
    var calendar = Calendar(identifier: .gregorian)
    calendar.locale = RU
    calendar.firstWeekday = 2
    calendar.timeZone = timeZone
    return calendar
}
