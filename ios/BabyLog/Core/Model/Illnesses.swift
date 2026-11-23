import Foundation

let maxIllnessNameLength = 40
/** Новая болезнь в пределах стольких суток после прошлой — лист спросит о возвращении (ADR-0040). */
let relapseDays: Int64 = 7
let usualDays: Int64 = 14
let courseLookbackDays: Int64 = 14
let courseGapMillis: Int64 = 36 * 60 * 60_000
let defaultIllnessNames = ["простуда", "ОРВИ", "отит", "кишечная инфекция", "конъюнктивит"]
private let illnessDayMillis: Int64 = 86_400_000

/** Название в общем виде: регистр сохраняется, пробелы одиночные, без «;» и «:». */
func normalizeIllnessName(_ raw: String) -> String? {
    let separators = raw.replacingOccurrences(of: "[;:]", with: " ", options: .regularExpression)
    let spaces = separators.replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression)
    let normalized = String(spaces.trimmingCharacters(in: .whitespacesAndNewlines).prefix(maxIllnessNameLength))
        .trimmingCharacters(in: .whitespacesAndNewlines)
    return normalized.isEmpty ? nil : normalized
}

extension Illness {
    func startDate(_ timeZone: TimeZone) -> Date {
        localDay(Date(timeIntervalSince1970: Double(start) / 1000), timeZone: timeZone)
    }

    /** Последний день болезни; идущая — `today`. */
    func lastDate(today: Date, timeZone: TimeZone) -> Date {
        end.map { localDay(Date(timeIntervalSince1970: Double($0) / 1000), timeZone: timeZone) } ?? today
    }

    /** День болезни на `date`: день начала — первый. */
    func dayNumber(_ date: Date, timeZone: TimeZone) -> Int {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = timeZone
        return (calendar.dateComponents([.day], from: startDate(timeZone), to: date).day ?? 0) + 1
    }

    /** Сколько дней длилась (идущая — длится по `today`) болезнь: оба крайних дня считаются. */
    func days(today: Date, timeZone: TimeZone) -> Int {
        dayNumber(lastDate(today: today, timeZone: timeZone), timeZone: timeZone)
    }

    /** Названия болезни через запятую; без названий — nil. */
    func title() -> String? {
        let value = names.map(\.name).joined(separator: ", ")
        return value.isEmpty ? nil : value
    }
}

extension IllnessName {
    /** День, с которого стоит название: без своего — день начала болезни. */
    func sinceDate(_ illness: Illness, timeZone: TimeZone) -> Date {
        since ?? illness.startDate(timeZone)
    }
}

extension Collection where Element == any BabyEvent {
    /** Идущая болезнь ребёнка (у него она одна, ADR-0040). */
    func activeIllness() -> Illness? {
        compactMap { $0 as? Illness }.filter(\.isActive).max { $0.start < $1.start }
    }

    /** Прошлая болезнь, если идущей нет, а прошлая кончилась не больше `relapseDays` суток назад. */
    func recentIllness(now: Int64) -> Illness? {
        guard activeIllness() == nil else { return nil }
        return compactMap { $0 as? Illness }
            .compactMap { illness in illness.end.map { (illness, $0) } }
            .max { $0.1 < $1.1 }
            .flatMap { now - $0.1 >= 0 && now - $0.1 <= relapseDays * illnessDayMillis ? $0.0 : nil }
    }

    /** Дни, которые захватила болезнь — по всем болезням ребёнка. */
    func illnessDates(today: Date, timeZone: TimeZone) -> Set<Date> {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = timeZone
        var result: Set<Date> = []
        for illness in compactMap({ $0 as? Illness }) {
            var date = illness.startDate(timeZone)
            let last = illness.lastDate(today: today, timeZone: timeZone)
            while date <= last {
                result.insert(date)
                guard let next = calendar.date(byAdding: .day, value: 1, to: date) else { break }
                date = next
            }
        }
        return result
    }

    /** Температура, лекарства и заметки от начала до конца болезни и продолжающиеся курсы лекарств. */
    func illnessRecords(_ illness: Illness, now: Int64) -> [any BabyEvent] {
        let until = illness.end ?? now
        let inside = filter { event in
            guard event.childId == illness.childId, event.start >= illness.start, event.start <= until else { return false }
            return event is Temperature || event is Medicine || event is Note
        }
        return (inside + courseAfter(illness)).sorted {
            $0.start == $1.start ? $0.id < $1.id : $0.start < $1.start
        }
    }

    private func courseAfter(_ illness: Illness) -> [any BabyEvent] {
        guard let end = illness.end else { return [] }
        let medicines = compactMap { $0 as? Medicine }.filter { $0.childId == illness.childId }
        let groups = Dictionary(grouping: medicines) { medicineKey($0.name) }
        return groups.values.flatMap { records -> [any BabyEvent] in
            let sorted = records.sorted { $0.start < $1.start }
            let inside = sorted.filter { $0.start >= illness.start && $0.start <= end }
            let lookback = illness.start - courseLookbackDays * illnessDayMillis
            let usedBefore = sorted.contains { $0.start >= lookback && $0.start < illness.start }
            guard !inside.isEmpty, !usedBefore, var last = inside.last?.start else { return [] }
            var result: [any BabyEvent] = []
            for next in sorted where next.start > end {
                guard next.start - last <= courseGapMillis else { break }
                result.append(next)
                last = next.start
            }
            return result
        }
    }

    /** Курсы, начатые в болезни, с последним приёмом не раньше `courseGapMillis` до `now`. */
    func ongoingCourses(_ illness: Illness, now: Int64) -> [String] {
        let until = illness.end ?? now
        let medicines = compactMap { $0 as? Medicine }.filter { $0.childId == illness.childId }
        return Dictionary(grouping: medicines) { medicineKey($0.name) }.values.compactMap { records in
            let inside = records.filter { $0.start >= illness.start && $0.start <= until }
            let lookback = illness.start - courseLookbackDays * illnessDayMillis
            let usedBefore = records.contains { $0.start >= lookback && $0.start < illness.start }
            guard let last = records.max(by: { $0.start < $1.start }), !inside.isEmpty, !usedBefore,
                  now - last.start <= courseGapMillis else { return nil }
            return last.name.trimmingCharacters(in: .whitespacesAndNewlines)
        }
    }

    /** Лекарства болезни, последние данные первыми; названия сравниваются без регистра и «ё». */
    func illnessMedicines(_ illness: Illness, now: Int64) -> [IllnessMedicine] {
        let records = illnessRecords(illness, now: now).compactMap { $0 as? Medicine }
        return Dictionary(grouping: records) { medicineKey($0.name) }.values.map { records in
            let sorted = records.sorted { $0.start < $1.start }
            let newest = sorted.last!
            return IllnessMedicine(
                name: newest.name.trimmingCharacters(in: .whitespacesAndNewlines),
                last: newest.start,
                note: sorted.reversed().first { !$0.note.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }?.note
                    .trimmingCharacters(in: .whitespacesAndNewlines) ?? "",
                count: records.count,
                times: sorted.map(\.start)
            )
        }.sorted { $0.last > $1.last }
    }

    /** Названия из прошлых болезней — частые первыми, затем стандартные; без повторов без учёта регистра. */
    func illnessNameChoices() -> [String] {
        let past = compactMap { $0 as? Illness }.sorted { $0.start > $1.start }.flatMap(\.names).map(\.name)
        let groups = Dictionary(grouping: past) { $0.lowercased() }
        let counted = groups.values.sorted {
            $0.count == $1.count ? $0[0].lowercased() < $1[0].lowercased() : $0.count > $1.count
        }.map { $0[0] }
        var seen: Set<String> = []
        return (counted + defaultIllnessNames).filter { seen.insert($0.lowercased()).inserted }
    }
}

/** Факты о лекарстве болезни: последнее название, время, комментарий и приёмы. */
struct IllnessMedicine: Hashable, Sendable {
    var name: String
    var last: Int64
    var note: String
    var count: Int
    var times: [Int64]
}

private func localDay(_ date: Date, timeZone: TimeZone) -> Date {
    var calendar = Calendar(identifier: .gregorian)
    calendar.timeZone = timeZone
    return calendar.startOfDay(for: date)
}

private func medicineKey(_ name: String) -> String {
    name.trimmingCharacters(in: .whitespacesAndNewlines).lowercased().replacingOccurrences(of: "ё", with: "е")
}
