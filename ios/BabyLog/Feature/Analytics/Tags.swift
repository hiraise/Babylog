import Foundation

/** Меток в «Как часто» и в «Метках и режиме» — не больше стольких: дальше редкие. */
private let topTags = 6
private let topRoutine = 4

/** «Метки и режим» сравнивают, только если полных суток с меткой и без неё не меньше стольких. */
let minRoutineDays = 3

/** Разница меньше этой — «без разницы»: на глаз её не видно, а записи неточны на минуты. */
private let sameSleepMillis: Int64 = 10 * 60_000
private let sameCryingMillis: Int64 = 5 * 60_000

/** Метка в «Как часто»: сколько раз была за период и в каких столбиках — сутках или неделях. */
struct TagFrequency: Hashable, Sendable {
    var tag: String
    var count: Int
    var marks: [Bool]
}

/**
 «Метки и режим» для метки заметок `tag`: в сутки с ней сон (`sleep`) и плач (`crying`) — против
 суток без неё, словами: «сон −1 ч 10 мин», «плач — без разницы». nil у обоих — данных мало;
 `crying` nil — плач не записывали. `days` — сколько суток периода с этой меткой.
 */
struct TagRoutine: Hashable, Sendable {
    var tag: String
    var days: Int
    var sleep: String?
    var crying: String?
}

/**
 Раздел и плитка «Метки» (ADR-0031): плач и заметки за период, как часто каждая метка и что
 в сутки с ней с режимом. `untaggedNotes` — заметки без меток: в счёт меток они не входят.
 */
struct TagsModel: Sendable {
    var cryings: Int
    var notes: Int
    var frequencies: [TagFrequency]
    var untaggedNotes: Int
    var routine: [TagRoutine]
}

/**
 Метки за сутки периода `days` (`dailyStats`); `byWeeks` — у «всего времени» столбики по неделям.
 «Метки и режим» — только по заметкам: у плача метка — его причина, и плача в такие сутки больше
 само собой. nil — меток и заметок у ребёнка нет совсем: плитки нет.
 */
func tagsModel(events: [any BabyEvent], days: [DayStats], byWeeks: Bool, timeZone: TimeZone) -> TagsModel? {
    guard !days.isEmpty, events.contains(where: { $0 is Note || !$0.eventTags.isEmpty }) else { return nil }
    let first = days.first!.date
    let last = days.last!.date
    let records = events.filter {
        ($0 is Crying || $0 is Note) && toLocalDate($0.start, timeZone: timeZone) >= first
            && toLocalDate($0.start, timeZone: timeZone) <= last
    }
    let buckets: [Set<Date>]
    if byWeeks {
        buckets = stride(from: 0, to: days.count, by: 7).map {
            Set(days[$0..<min($0 + 7, days.count)].map(\.date))
        }
    } else {
        buckets = days.map { [$0.date] }
    }

    let taggedDates = records.flatMap { record -> [(tag: String, date: Date)] in
        record.eventTags.map { (tag: $0, date: toLocalDate(record.start, timeZone: timeZone)) }
    }
    let groupedTags = Dictionary(grouping: taggedDates, by: { $0.tag })
    let sortedTags: [(tag: String, dates: [Date])] = groupedTags.map { tag, values in
        (tag: tag, dates: values.map(\.date))
    }.sorted { $0.dates.count == $1.dates.count ? $0.tag < $1.tag : $0.dates.count > $1.dates.count }
    let frequencies = sortedTags.prefix(topTags).map { tag, dates in
        let marked = Set(dates)
        return TagFrequency(tag: tag, count: dates.count, marks: buckets.map { !$0.isDisjoint(with: marked) })
    }

    let countable = days.filter(\.countable)
    let cries = events.contains(where: { $0 is Crying })
    let noteDates = records.compactMap { $0 as? Note }.flatMap { note -> [(tag: String, date: Date)] in
        note.tags.map { (tag: $0, date: toLocalDate(note.start, timeZone: timeZone)) }
    }
    let groupedRoutine = Dictionary(grouping: noteDates, by: { $0.tag })
    let sortedRoutine: [(tag: String, dates: Set<Date>)] = groupedRoutine.map { tag, values in
        (tag: tag, dates: Set(values.map(\.date)))
    }.sorted { $0.dates.count == $1.dates.count ? $0.tag < $1.tag : $0.dates.count > $1.dates.count }
    let routine = sortedRoutine.prefix(topRoutine).map { tag, dates in
            let with = countable.filter { dates.contains($0.date) }
            let without = countable.filter { !dates.contains($0.date) }
            if with.count < minRoutineDays || without.count < minRoutineDays {
                return TagRoutine(tag: tag, days: dates.count, sleep: nil, crying: nil)
            }
            func diff(_ value: (DayStats) -> Int64) -> Double {
                let withAverage = with.reduce(0.0) { $0 + Double(value($1)) } / Double(with.count)
                let withoutAverage = without.reduce(0.0) { $0 + Double(value($1)) } / Double(without.count)
                return withAverage - withoutAverage
            }
            return TagRoutine(
                tag: tag,
                days: dates.count,
                sleep: tagChange("сон", diff: diff(\.sleepMillis), same: sameSleepMillis),
                crying: cries ? tagChange("плач", diff: diff(\.cryingMillis), same: sameCryingMillis) : nil
            )
        }

    return TagsModel(
        cryings: records.count { $0 is Crying },
        notes: records.count { $0 is Note },
        frequencies: Array(frequencies),
        untaggedNotes: records.count { $0 is Note && $0.eventTags.isEmpty },
        routine: Array(routine)
    )
}

extension TagsModel {
    func counts() -> String {
        "плач \(cryings) \(plural(cryings, one: "раз", few: "раза", many: "раз")) · "
            + "\(notes) \(plural(notes, one: "заметка", few: "заметки", many: "заметок"))"
    }
}

/** «сон −1 ч 10 мин», «плач +30 мин»; меньше `same` — «плач — без разницы». */
private func tagChange(_ what: String, diff: Double, same: Int64) -> String {
    if abs(diff) < Double(same) { return "\(what) — без разницы" }
    return "\(what) \(diff > 0 ? "+" : "−")\(formatDuration(Int64(abs(diff))))"
}
