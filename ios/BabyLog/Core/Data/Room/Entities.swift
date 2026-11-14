import Foundation
import GRDB

struct ChildEntity: Codable, FetchableRecord, TableRecord, Sendable {
    static let databaseTableName = "children"

    var id: Int64 = 0
    var name: String
    var birthDate: Int64?
    /** Код `Sex`, nil — пол не указан. */
    var sex: String? = nil
    /** JPEG `ChildPhoto`, nil — фото нет (ADR-0023). */
    var photo: Data? = nil
}

/** Записи всех типов в одной таблице; смысл `detail` по типам — в `EventRecord` и ADR-0008. */
struct EventEntity: Codable, FetchableRecord, TableRecord, Sendable {
    static let databaseTableName = "events"

    var id: Int64 = 0
    var childId: Int64
    var type: String
    var startTime: Int64
    var endTime: Int64?
    var detail: String
    var note: String
}

/** Фото записи (ADR-0024). Записи правятся только `UPDATE`, не `REPLACE`: иначе фото уйдут каскадом. */
struct EventPhotoEntity: Codable, FetchableRecord, TableRecord, Sendable {
    static let databaseTableName = "event_photos"

    var id: Int64 = 0
    var eventId: Int64
    /** Порядок фото в записи, с нуля. */
    var position: Int
    var jpeg: Data
}

struct PhotoCount: Codable, FetchableRecord, Sendable {
    var eventId: Int64
    var count: Int
}

struct PhotoRef: Codable, FetchableRecord, Sendable {
    var id: Int64
    var eventId: Int64
}

extension ChildEntity {
    func toChild() -> Child {
        Child(id: id, name: name, birthDate: birthDate, sex: Sex.fromCode(sex), photo: photo.map(ChildPhoto.init))
    }
}

extension Child {
    func toEntity() -> ChildEntity {
        ChildEntity(id: id, name: name, birthDate: birthDate, sex: sex?.rawValue, photo: photo?.jpeg)
    }
}

extension EventEntity {
    /** nil — строка не разбирается (такого не пишет ни одна версия приложения). */
    func toEvent() -> (any BabyEvent)? {
        EventRecord(type: type, start: startTime, end: endTime, detail: detail, note: note).toEvent(id: id, childId: childId)
    }
}

extension BabyEvent {
    func toEntity() -> EventEntity {
        let record = toRecord()
        return EventEntity(id: id, childId: childId, type: record.type, startTime: record.start, endTime: record.end, detail: record.detail, note: record.note)
    }
}
