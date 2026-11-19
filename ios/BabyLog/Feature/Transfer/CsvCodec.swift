import Foundation

struct TransferError: LocalizedError, Equatable {
    let message: String

    init(_ message: String) {
        self.message = message
    }

    var errorDescription: String? { message }
}

/**
 CSV-формат (ADR-0009): `type,start,end,detail,note,child,photos`.
 Время — локальное, "yyyy-MM-dd HH:mm:ss" (при импорте допускается 'T' как разделитель
 и время без секунд). Экранирование по RFC 4180. Строка PROFILE описывает ребёнка:
 дата рождения в `start` (может быть пустой), пол в `detail` (`BOY`/`GIRL`, может быть пустым,
 ADR-0016), имя в `child`. `photos` — файлы фото в архиве экспорта через `;` (ADR-0045): у PROFILE —
 фото ребёнка, у записи — её фото по порядку.
 */
enum CsvCodec {
    static let header = "type,start,end,detail,note,child,photos"
    static let profileRow = "PROFILE"
    private static let photoSeparator = ";"

    /**
     Поле длиннее — не запись приложения: так выглядят строки, склеенные незакрытой кавычкой, или чужой
     файл. Строка базы с таким полем не влезла бы в окно курсора SQLite (2 МБ) и роняла бы чтение дневника.
     */
    static let maxFieldLength = 100_000

    static func formatTime(_ millis: Int64, zone: TimeZone = .current) -> String {
        formatter(zone: zone).string(from: Date(timeIntervalSince1970: Double(millis) / 1000))
    }

    static func parseTime(_ raw: String, zone: TimeZone = .current) -> Int64? {
        var value = raw.trimmingCharacters(in: .whitespacesAndNewlines).replacingOccurrences(of: "T", with: " ")
        if value.isEmpty { return nil }
        if value.utf16.count == 16 { value += ":00" }
        let format = formatter(zone: zone)
        guard let date = format.date(from: value), format.string(from: date) == value else { return nil }
        return Int64(date.timeIntervalSince1970 * 1000)
    }

    private static func formatter(zone: TimeZone) -> DateFormatter {
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = zone
        formatter.dateFormat = "yyyy-MM-dd HH:mm:ss"
        formatter.isLenient = false
        return formatter
    }

    /**
     Весь дневник: строка PROFILE на каждого ребёнка, затем записи в переданном порядке. `childPhotos` — файл
     фото ребёнка по его id, `eventPhotos` — файлы фото записи по её id; это пути внутри архива (ADR-0045).
     */
    static func encode(
        children: [Child],
        events: [any BabyEvent],
        childPhotos: [Int64: String] = [:],
        eventPhotos: [Int64: [String]] = [:],
        zone: TimeZone = .current
    ) -> String {
        let names = Dictionary(uniqueKeysWithValues: children.map { ($0.id, $0.name) })
        var csv = "\u{FEFF}"
        csv += header + "\r\n"
        for child in children {
            csv += profileRow + ","
            csv += child.birthDate.map { formatTime($0, zone: zone) } ?? ""
            csv += ",," + (child.sex?.rawValue ?? "") + ",,"
            csv += escape(child.name) + ","
            csv += escape(childPhotos[child.id] ?? "") + "\r\n"
        }
        for event in events {
            let record = event.toRecord()
            csv += record.type + ","
            csv += formatTime(record.start, zone: zone) + ","
            csv += (record.end.map { formatTime($0, zone: zone) } ?? "") + ","
            csv += escape(record.detail) + ","
            csv += escape(record.note) + ","
            csv += escape(names[event.childId] ?? "") + ","
            csv += escape((eventPhotos[event.id] ?? []).joined(separator: photoSeparator)) + "\r\n"
        }
        return csv
    }

    private static func escape(_ value: String) -> String {
        guard value.contains(where: { $0 == "," || $0 == "\"" || $0 == "\n" || $0 == "\r" }) else { return value }
        return "\"" + value.replacingOccurrences(of: "\"", with: "\"\"") + "\""
    }

    /**
     Разбирает CSV в строки-записи с учётом кавычек и переносов внутри кавычек. Кавычка, не закрытая до
     конца файла, — `TransferError` с номером строки: иначе остаток файла молча стал бы одним полем.
     */
    static func parseRows(_ text: String) throws -> [[String]] {
        var rows: [[String]] = []
        var row: [String] = []
        var field = ""
        var inQuotes = false
        var line = 1
        var quoteLine = 0
        var index = 0
        let withoutBOM = text.hasPrefix("\u{FEFF}") ? String(text.dropFirst()) : text
        let source = Array(withoutBOM.unicodeScalars)
        while index < source.count {
            let character = source[index]
            if character == "\n" { line += 1 }
            if inQuotes {
                if character == "\"", index + 1 < source.count, source[index + 1] == "\"" {
                    field.append("\"")
                    index += 1
                } else if character == "\"" {
                    inQuotes = false
                } else {
                    field.unicodeScalars.append(character)
                }
            } else if character == "\"", field.isEmpty {
                inQuotes = true
                quoteLine = line
            } else if character == "," {
                row.append(field)
                field = ""
            } else if character == "\r" {
                // Строку завершит \n.
            } else if character == "\n" {
                row.append(field)
                field = ""
                if row.contains(where: { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }) { rows.append(row) }
                row = []
            } else {
                field.unicodeScalars.append(character)
            }
            index += 1
        }
        if inQuotes { throw TransferError("в строке \(quoteLine) кавычка не закрыта — исправьте файл") }
        row.append(field)
        if row.contains(where: { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }) { rows.append(row) }
        return rows
    }

    /** Строка CSV → плоская запись; nil — нет обязательных колонок или время не разбирается. */
    static func rowToRecord(_ row: [String]) -> EventRecord? {
        if row.count < 4 { return nil }
        guard let start = parseTime(row[1]) else { return nil }
        let end: Int64?
        if row[2].trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            end = nil
        } else {
            guard let parsed = parseTime(row[2]) else { return nil }
            end = parsed
        }
        return EventRecord(
            type: row[0].trimmingCharacters(in: .whitespacesAndNewlines),
            start: start,
            end: end,
            detail: row[3].trimmingCharacters(in: .whitespacesAndNewlines),
            note: (row.count > 4 ? row[4] : "").trimmingCharacters(in: .whitespacesAndNewlines)
        )
    }

    /** Имя ребёнка из колонки `child`; пусто — колонки нет (файл старой версии) или она не заполнена. */
    static func childName(_ row: [String]) -> String {
        (row.count > 5 ? row[5] : "").trimmingCharacters(in: .whitespacesAndNewlines)
    }

    /** Файлы фото из колонки `photos` по порядку; пусто — фото нет или файл версии до 1.10 (ADR-0045). */
    static func photoNames(_ row: [String]) -> [String] {
        (row.count > 6 ? row[6] : "").split(separator: ";", omittingEmptySubsequences: false)
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
    }
}
