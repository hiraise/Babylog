import Foundation
import GRDB

struct DiaryDao: Sendable {
    func fetchChildren(_ db: Database) throws -> [ChildEntity] {
        try ChildEntity.fetchAll(db, sql: "SELECT * FROM children ORDER BY id")
    }

    func countChildren(_ db: Database) throws -> Int {
        try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM children") ?? 0
    }

    @discardableResult
    func insertChild(_ db: Database, child: ChildEntity) throws -> Int64 {
        try db.execute(
            sql: "INSERT INTO children (name, birthDate, sex, photo) VALUES (?, ?, ?, ?)",
            arguments: [child.name, child.birthDate, child.sex, child.photo]
        )
        return db.lastInsertedRowID
    }

    func updateChild(_ db: Database, child: ChildEntity) throws {
        try db.execute(
            sql: "UPDATE children SET name = ?, birthDate = ?, sex = ?, photo = ? WHERE id = ?",
            arguments: [child.name, child.birthDate, child.sex, child.photo, child.id]
        )
    }

    func deleteChild(_ db: Database, childId: Int64) throws {
        try db.execute(sql: "DELETE FROM children WHERE id = ?", arguments: [childId])
    }

    func fetchEvents(_ db: Database, childId: Int64) throws -> [EventEntity] {
        try EventEntity.fetchAll(db, sql: "SELECT * FROM events WHERE childId = ? ORDER BY startTime DESC, id DESC", arguments: [childId])
    }

    func fetchAllEvents(_ db: Database) throws -> [EventEntity] {
        try EventEntity.fetchAll(db, sql: "SELECT * FROM events ORDER BY startTime ASC, id ASC")
    }

    func countActive(_ db: Database, childId: Int64, type: String) throws -> Int {
        try Int.fetchOne(
            db,
            sql: "SELECT COUNT(*) FROM events WHERE childId = ? AND type = ? AND endTime IS NULL",
            arguments: [childId, type]
        ) ?? 0
    }

    func activeEvents(_ db: Database, childId: Int64, type: String) throws -> [EventEntity] {
        try EventEntity.fetchAll(
            db,
            sql: "SELECT * FROM events WHERE childId = ? AND type = ? AND endTime IS NULL",
            arguments: [childId, type]
        )
    }

    @discardableResult
    func insertEvent(_ db: Database, event: EventEntity) throws -> Int64 {
        try db.execute(
            sql: "INSERT INTO events (childId, type, startTime, endTime, detail, note) VALUES (?, ?, ?, ?, ?, ?)",
            arguments: [event.childId, event.type, event.startTime, event.endTime, event.detail, event.note]
        )
        return db.lastInsertedRowID
    }

    func updateEvent(_ db: Database, event: EventEntity) throws {
        try db.execute(
            sql: "UPDATE events SET childId = ?, type = ?, startTime = ?, endTime = ?, detail = ?, note = ? WHERE id = ?",
            arguments: [event.childId, event.type, event.startTime, event.endTime, event.detail, event.note, event.id]
        )
    }

    func deleteEvent(_ db: Database, eventId: Int64) throws {
        try db.execute(sql: "DELETE FROM events WHERE id = ?", arguments: [eventId])
    }

    func deleteEvents(_ db: Database, childId: Int64) throws {
        try db.execute(sql: "DELETE FROM events WHERE childId = ?", arguments: [childId])
    }

    func fetchPhotos(_ db: Database, eventId: Int64) throws -> [EventPhotoEntity] {
        try EventPhotoEntity.fetchAll(db, sql: "SELECT * FROM event_photos WHERE eventId = ? ORDER BY position, id", arguments: [eventId])
    }

    func fetchPhotoCounts(_ db: Database, childId: Int64) throws -> [PhotoCount] {
        try PhotoCount.fetchAll(
            db,
            sql: "SELECT p.eventId AS eventId, COUNT(*) AS count FROM event_photos p JOIN events e ON e.id = p.eventId WHERE e.childId = ? GROUP BY p.eventId",
            arguments: [childId]
        )
    }

    func deletePhotosExcept(_ db: Database, eventId: Int64, keep: [Int64]) throws {
        if keep.isEmpty {
            try db.execute(sql: "DELETE FROM event_photos WHERE eventId = ?", arguments: [eventId])
        } else {
            let placeholders = Array(repeating: "?", count: keep.count).joined(separator: ",")
            try db.execute(
                sql: "DELETE FROM event_photos WHERE eventId = ? AND id NOT IN (\(placeholders))",
                arguments: StatementArguments([eventId] + keep)
            )
        }
    }

    func movePhoto(_ db: Database, id: Int64, eventId: Int64, position: Int) throws {
        try db.execute(
            sql: "UPDATE event_photos SET position = ? WHERE id = ? AND eventId = ?",
            arguments: [position, id, eventId]
        )
    }

    func insertPhoto(_ db: Database, photo: EventPhotoEntity) throws {
        try db.execute(
            sql: "INSERT INTO event_photos (eventId, position, jpeg) VALUES (?, ?, ?)",
            arguments: [photo.eventId, photo.position, photo.jpeg]
        )
    }

    func fetchPhotoRefs(_ db: Database) throws -> [PhotoRef] {
        try PhotoRef.fetchAll(db, sql: "SELECT id, eventId FROM event_photos ORDER BY eventId, position, id")
    }

    func fetchPhotoJpeg(_ db: Database, id: Int64) throws -> Data? {
        try Data.fetchOne(db, sql: "SELECT jpeg FROM event_photos WHERE id = ?", arguments: [id])
    }
}
