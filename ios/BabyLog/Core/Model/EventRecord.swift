import Foundation

/** Плоское представление записи — так она хранится в таблице `events` и в CSV. */
struct EventRecord: Hashable, Sendable {
    var type: String
    var start: Int64
    var end: Int64?
    var detail: String
    var note: String

    static let feeding = "FEEDING"
    static let bottle = "BOTTLE"
    static let diaper = "DIAPER"
    static let sleep = "SLEEP"
    static let medicine = "MEDICINE"
    static let weight = "WEIGHT"
    static let height = "HEIGHT"
    static let crying = "CRYING"
    static let noteType = "NOTE"
    static let illness = "ILLNESS"
    static let temperature = "TEMPERATURE"
}

extension BabyEvent {
    func toRecord() -> EventRecord {
        switch self {
        case let event as Feeding:
            EventRecord(type: EventRecord.feeding, start: event.start, end: event.end, detail: event.breast.rawValue, note: event.note)
        case let event as Bottle:
            EventRecord(type: EventRecord.bottle, start: event.start, end: nil, detail: codedDetail(event.volumeMl, event.milk?.rawValue), note: event.note)
        case let event as Diaper:
            EventRecord(type: EventRecord.diaper, start: event.start, end: nil, detail: event.kind.rawValue, note: event.note)
        case let event as Sleep:
            EventRecord(type: EventRecord.sleep, start: event.start, end: event.end, detail: "", note: event.note)
        case let event as Medicine:
            EventRecord(type: EventRecord.medicine, start: event.start, end: nil, detail: event.name, note: event.note)
        case let event as Weight:
            EventRecord(type: EventRecord.weight, start: event.start, end: nil, detail: String(event.grams), note: event.note)
        case let event as Height:
            EventRecord(type: EventRecord.height, start: event.start, end: nil, detail: String(event.millimeters), note: event.note)
        case let event as Crying:
            EventRecord(type: EventRecord.crying, start: event.start, end: event.end, detail: cryingDetail(event.reasons, event.helped), note: event.note)
        case let event as Note:
            EventRecord(type: EventRecord.noteType, start: event.start, end: nil, detail: event.tags.joined(separator: ";"), note: event.note)
        case let event as Illness:
            EventRecord(type: EventRecord.illness, start: event.start, end: event.end, detail: illnessDetail(event.names), note: event.note)
        case let event as Temperature:
            EventRecord(type: EventRecord.temperature, start: event.start, end: nil, detail: codedDetail(event.tenths, event.method?.rawValue), note: event.note)
        default:
            preconditionFailure("Неизвестный тип записи \(type(of: self))")
        }
    }
}

private func codedDetail(_ value: Int, _ code: String?) -> String {
    [String(value), code].compactMap { $0 }.joined(separator: ":")
}

private func illnessDetail(_ names: [IllnessName]) -> String {
    names.map { $0.name + ($0.since.map { ":\(isoDate($0))" } ?? "") }.joined(separator: ";")
}

private func cryingDetail(_ reasons: [String], _ helped: [String]) -> String {
    reasons.isEmpty && helped.isEmpty ? "" : reasons.joined(separator: ";") + "|" + helped.joined(separator: ";")
}

extension EventRecord {
    /**
     Проверяет запись и превращает её в `BabyEvent`; nil — запись некорректна.
     Регистр кодов не важен. `end` учитывается только у сна, кормления, плача и болезни.
     */
    func toEvent(id: Int64 = 0, childId: Int64) -> (any BabyEvent)? {
        switch type.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() {
        case EventRecord.feeding:
            (enumValue(detail) as Breast?).map { Feeding(id: id, childId: childId, start: start, end: end, breast: $0, note: note) }
        case EventRecord.bottle:
            bottleOrNull(id: id, childId: childId)
        case EventRecord.diaper:
            (enumValue(detail) as DiaperKind?).map { Diaper(id: id, childId: childId, start: start, kind: $0, note: note) }
        case EventRecord.sleep:
            Sleep(id: id, childId: childId, start: start, end: end, note: note)
        case EventRecord.medicine:
            detail.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : Medicine(id: id, childId: childId, start: start, name: detail, note: note)
        case EventRecord.weight:
            integer(detail).flatMap { Weight.range.contains($0) ? Weight(id: id, childId: childId, start: start, grams: $0, note: note) : nil }
        case EventRecord.height:
            integer(detail).flatMap { Height.range.contains($0) ? Height(id: id, childId: childId, start: start, millimeters: $0, note: note) : nil }
        case EventRecord.crying:
            cryingOrNull(id: id, childId: childId)
        case EventRecord.noteType:
            Note(id: id, childId: childId, start: start, tags: tags(detail), note: note)
        case EventRecord.illness:
            illnessNames(detail).map { Illness(id: id, childId: childId, start: start, end: end, names: $0, note: note) }
        case EventRecord.temperature:
            temperatureOrNull(id: id, childId: childId)
        default:
            nil
        }
    }

    private func integer(_ detail: String) -> Int? {
        Int(detail.trimmingCharacters(in: .whitespacesAndNewlines))
    }

    private func bottleOrNull(id: Int64, childId: Int64) -> Bottle? {
        let parts = detail.split(separator: ":", omittingEmptySubsequences: false)
        guard let volume = integer(String(parts[0])), Bottle.range.contains(volume) else { return nil }
        let milk: Milk?
        switch parts.count {
        case 1: milk = nil
        case 2:
            guard let parsed: Milk = enumValue(String(parts[1])) else { return nil }
            milk = parsed
        default: return nil
        }
        return Bottle(id: id, childId: childId, start: start, volumeMl: volume, milk: milk, note: note)
    }

    private func temperatureOrNull(id: Int64, childId: Int64) -> Temperature? {
        let parts = detail.split(separator: ":", omittingEmptySubsequences: false)
        guard let tenths = integer(String(parts[0])), Temperature.range.contains(tenths) else { return nil }
        let method: TemperatureMethod?
        switch parts.count {
        case 1: method = nil
        case 2:
            guard let parsed: TemperatureMethod = enumValue(String(parts[1])) else { return nil }
            method = parsed
        default: return nil
        }
        return Temperature(id: id, childId: childId, start: start, tenths: tenths, method: method, note: note)
    }

    private func cryingOrNull(id: Int64, childId: Int64) -> Crying? {
        let parts = detail.split(separator: "|", omittingEmptySubsequences: false)
        guard parts.count <= 2 else { return nil }
        return Crying(
            id: id,
            childId: childId,
            start: start,
            end: end,
            reasons: tags(String(parts[0])),
            helped: parts.count > 1 ? tags(String(parts[1])) : [],
            note: note
        )
    }

    private func illnessNames(_ detail: String) -> [IllnessName]? {
        var names: [IllnessName] = []
        for part in detail.split(separator: ";", omittingEmptySubsequences: false) {
            if part.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { continue }
            let pieces = part.split(separator: ":", omittingEmptySubsequences: false)
            if pieces.count > 2 { return nil }
            guard let name = normalizeIllnessName(String(pieces[0])) else { continue }
            var since: Date? = nil
            if pieces.count == 2 {
                let raw = pieces[1].trimmingCharacters(in: .whitespacesAndNewlines)
                if !raw.isEmpty {
                    guard let parsed = parseISODate(raw) else { return nil }
                    since = parsed
                }
            }
            if !names.contains(where: { $0.name.compare(name, options: .caseInsensitive) == .orderedSame }) {
                names.append(IllnessName(name, since: since))
            }
        }
        return names
    }

    private func tags(_ part: String?) -> [String] {
        var result: [String] = []
        for raw in (part ?? "").split(separator: ";", omittingEmptySubsequences: false) {
            guard let tag = normalizeTag(String(raw)), !result.contains(tag) else { continue }
            result.append(tag)
        }
        return result
    }
}

private func enumValue<T: RawRepresentable & CaseIterable>(_ code: String) -> T? where T.RawValue == String {
    let normalized = code.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
    return T.allCases.first { $0.rawValue == normalized }
}

private func isoDate(_ date: Date) -> String {
    let formatter = DateFormatter()
    formatter.calendar = Calendar(identifier: .gregorian)
    formatter.locale = Locale(identifier: "en_US_POSIX")
    formatter.timeZone = TimeZone(secondsFromGMT: 0)
    formatter.dateFormat = "yyyy-MM-dd"
    return formatter.string(from: date)
}

private func parseISODate(_ raw: String) -> Date? {
    let formatter = DateFormatter()
    formatter.calendar = Calendar(identifier: .gregorian)
    formatter.locale = Locale(identifier: "en_US_POSIX")
    formatter.timeZone = TimeZone(secondsFromGMT: 0)
    formatter.dateFormat = "yyyy-MM-dd"
    formatter.isLenient = false
    guard let date = formatter.date(from: raw), formatter.string(from: date) == raw else { return nil }
    return date
}
