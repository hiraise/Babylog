import Foundation

/**
 Фото в форме записи: уже в базе (`saved`, по id) или только что добавленное — файл
 в кэше (`pending`), в базу оно попадёт по «Сохранить».
 */
enum FormPhoto: Hashable {
    case saved(id: Int64)
    case pending(path: String)
}

/**
 Фото формы в сохранённом состоянии — только id и пути. Сами фото в состояние view не кладём:
 большие данные не должны проходить через сохранение UI при уходе приложения в фон.
 */
enum FormPhotosSaver {
    static func save(_ photos: [FormPhoto]) -> [String] {
        photos.map { photo in
            switch photo {
            case let .saved(id): savedPrefix + String(id)
            case let .pending(path): pendingPrefix + path
            }
        }
    }

    static func restore(_ saved: [String]) -> [FormPhoto] {
        saved.map { value in
            if value.hasPrefix(savedPrefix), let id = Int64(value.dropFirst(savedPrefix.count)) {
                return .saved(id: id)
            }
            return .pending(path: String(value.dropFirst(pendingPrefix.count)))
        }
    }
}

private let savedPrefix = "id:"
private let pendingPrefix = "file:"

/**
 Фото формы после «Сохранить»: сохранённые остаются в том же порядке, новые встают после них.
 `read` — байты нового фото; nil (файл пропал) — фото пропускается.
 */
extension Array where Element == FormPhoto {
    func toEdit(read: (String) -> Data?) -> PhotoEdit {
        PhotoEdit(
            keep: compactMap { if case let .saved(id) = $0 { id } else { nil } },
            add: compactMap { if case let .pending(path) = $0 { read(path) } else { nil } }
        )
    }
}

/**
 Подпись к фото подгузника для мессенджера — кто, что и когда, чтобы врачу не нужны были пояснения:
 «Маша, 4 мес 12 дн.», «Подгузник: покакала», «17 октября 2026, 14:30», комментарий.
 */
func diaperShareText(
    child: Child,
    kind: DiaperKind,
    time: Int64,
    note: String,
    today: Date,
    timeZone: TimeZone = .current
) -> String {
    var who = [child.name.trimmingCharacters(in: .whitespacesAndNewlines)]
    if let birthDate = child.birthDate {
        who.append(formatAge(birth: toLocalDate(birthDate, timeZone: timeZone), today: today, sex: child.sex, timeZone: timeZone))
    }
    var lines = [
        who.joined(separator: ", "),
        "Подгузник: \(diaperLabel(kind, sex: child.sex))",
        "\(formatDate(time, timeZone: timeZone)), \(formatClock(time, timeZone: timeZone))",
    ]
    let trimmedNote = note.trimmingCharacters(in: .whitespacesAndNewlines)
    if !trimmedNote.isEmpty { lines.append("Комментарий: \(trimmedNote)") }
    return lines.joined(separator: "\n")
}
