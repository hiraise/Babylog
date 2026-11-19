import Foundation

/** Фото записи (ADR-0024). Хранится отдельно: список записей грузится без JPEG. */
struct EventPhoto: Hashable, Sendable, CustomStringConvertible {
    var id: Int64
    var jpeg: Data

    /** Длинная сторона: цвет и консистенцию врачу видно, а база растёт медленно. */
    static let maxSide = 1600
    /** Сколько фото можно приложить к одной записи. */
    static let maxPerEvent = 6
    /** Наибольший JPEG в базе: строка должна уместиться в окно курсора SQLite (2 МБ). */
    static let maxBytes = 1_200_000

    var description: String { "EventPhoto(id=\(id), \(jpeg.count) B)" }
}

/** `keep` — id сохранённых фото в новом порядке; `add` — новые JPEG после них. */
struct PhotoEdit: Sendable {
    var keep: [Int64]
    var add: [Data]

    init(keep: [Int64], add: [Data]) {
        self.keep = keep
        self.add = add
    }
}
