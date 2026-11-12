import Foundation

/**
 Ребёнок. `birthDate` — epoch millis локальной полуночи дня рождения, nil — не указана;
 `sex` — пол, nil — не указан (ADR-0016); `photo` — фото для аватара, nil — нет (ADR-0023).
 */
struct Child: Hashable, Sendable {
    var id: Int64
    var name: String
    var birthDate: Int64?
    var sex: Sex? = nil
    var photo: ChildPhoto? = nil

    static let defaultName = "Малыш"
    static let maxNameLength = 40
}

/** Пол ребёнка: от него зависят слова вроде «уснул / уснула» (ADR-0016). Код в базе и CSV — `rawValue`. */
enum Sex: String, CaseIterable, Hashable, Sendable {
    case boy = "BOY"
    case girl = "GIRL"

    /** Код из базы или CSV без учёта регистра; пустой или неизвестный — nil. */
    static func fromCode(_ code: String?) -> Sex? {
        guard let code else { return nil }
        let normalized = code.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        return allCases.first { $0.rawValue == normalized }
    }
}

/**
 Фото ребёнка — уже кадрированный квадратный JPEG `size`×`size` (ADR-0023). Сравнивается по содержимому.
 */
struct ChildPhoto: Hashable, Sendable, CustomStringConvertible {
    var jpeg: Data

    static let size = 512

    var description: String { "ChildPhoto(\(jpeg.count) B)" }
}

/** Имена сравниваются без учёта регистра и пробелов по краям: по имени дети сопоставляются в CSV. */
func sameChildName(_ a: String, _ b: String) -> Bool {
    a.trimmingCharacters(in: .whitespacesAndNewlines)
        .compare(b.trimmingCharacters(in: .whitespacesAndNewlines), options: .caseInsensitive) == .orderedSame
}

/** Занято ли имя другим ребёнком (кроме `exceptId`). Имена должны быть уникальны — см. ADR-0008. */
func isNameTaken(_ children: [Child], name: String, exceptId: Int64? = nil) -> Bool {
    children.contains { $0.id != exceptId && sameChildName($0.name, name) }
}

func isValidChildName(_ name: String) -> Bool {
    let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
    return !trimmed.isEmpty && trimmed.count <= Child.maxNameLength
}
