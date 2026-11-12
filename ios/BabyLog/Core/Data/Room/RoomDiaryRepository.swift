import Foundation
import GRDB

struct RoomDiaryRepository: DiaryRepository {
    private let database: AppDatabase
    private let dao = DiaryDao()

    init(database: AppDatabase) {
        self.database = database
    }

    func observeChildren() -> AsyncThrowingStream<[Child], Error> {
        let observation = ValueObservation.tracking { db in
            try DiaryDao().fetchChildren(db).map { $0.toChild() }
        }
        return stream(observation.values(in: database.queue))
    }

    func observeEvents(childId: Int64) -> AsyncThrowingStream<[any BabyEvent], Error> {
        let observation = ValueObservation.tracking { db in
            try DiaryDao().fetchEvents(db, childId: childId).toEvents()
        }
        return stream(observation.values(in: database.queue))
    }

    func allChildren() async throws -> [Child] {
        try await database.queue.read { try DiaryDao().fetchChildren($0).map { $0.toChild() } }
    }

    func allEvents() async throws -> [any BabyEvent] {
        try await database.queue.read { try DiaryDao().fetchAllEvents($0).toEvents() }
    }

    @discardableResult
    func addChild(name: String, birthDate: Int64?, sex: Sex?, photo: ChildPhoto?) async throws -> Int64 {
        try await write { try $0.addChild(name: name, birthDate: birthDate, sex: sex, photo: photo) }
    }

    func updateChild(_ child: Child) async throws {
        try await write { try $0.updateChild(child) }
    }

    @discardableResult
    func deleteChild(_ childId: Int64) async throws -> Bool {
        try await write { try $0.deleteChild(childId) }
    }

    @discardableResult
    func addEvent(_ event: any BabyEvent) async throws -> Int64 {
        try await write { try $0.addEvent(event) }
    }

    func updateEvent(_ event: any BabyEvent) async throws {
        try await write { try $0.updateEvent(event) }
    }

    func deleteEvent(_ event: any BabyEvent) async throws {
        try await write { try $0.deleteEvent(event) }
    }

    @discardableResult
    func startSleep(childId: Int64, start: Int64) async throws -> Bool {
        try await write { try $0.startSleep(childId: childId, start: start) }
    }

    func startCrying(childId: Int64, start: Int64) async throws -> Int64? {
        try await write { try $0.startCrying(childId: childId, start: start) }
    }

    func startIllness(_ illness: Illness) async throws -> Int64? {
        try await write { try $0.startIllness(illness) }
    }

    @discardableResult
    func startFeeding(childId: Int64, start: Int64, breast: Breast) async throws -> Bool {
        try await write { try $0.startFeeding(childId: childId, start: start, breast: breast) }
    }

    func observeEventPhotos(eventId: Int64) -> AsyncThrowingStream<[EventPhoto], Error> {
        let observation = ValueObservation.tracking { db in
            try DiaryDao().fetchPhotos(db, eventId: eventId).map { EventPhoto(id: $0.id, jpeg: $0.jpeg) }
        }
        return stream(observation.values(in: database.queue))
    }

    func observePhotoCounts(childId: Int64) -> AsyncThrowingStream<[Int64: Int], Error> {
        let observation = ValueObservation.tracking { db in
            try Dictionary(uniqueKeysWithValues: DiaryDao().fetchPhotoCounts(db, childId: childId).map { ($0.eventId, $0.count) })
        }
        return stream(observation.values(in: database.queue))
    }

    func setEventPhotos(eventId: Int64, edit: PhotoEdit) async throws {
        try await write { try $0.setEventPhotos(eventId: eventId, edit: edit) }
    }

    func photoIds() async throws -> [Int64: [Int64]] {
        try await read { try $0.photoIds() }
    }

    func photoJpeg(_ photoId: Int64) async throws -> Data? {
        try await read { try $0.photoJpeg(photoId) }
    }

    func addEventPhoto(eventId: Int64, position: Int, jpeg: Data) async throws {
        try await write { try $0.addEventPhoto(eventId: eventId, position: position, jpeg: jpeg) }
    }

    func addBottle(_ bottle: Bottle) async throws {
        try await write { try $0.addBottle(bottle) }
    }

    func inTransaction<T: Sendable>(_ block: @Sendable (any DiaryRepositoryTransaction) throws -> T) async throws -> T {
        try await write(block)
    }

    private func read<T: Sendable>(_ block: @Sendable (RoomDiaryTransaction) throws -> T) async throws -> T {
        try await database.queue.read { db in try block(RoomDiaryTransaction(db: db, dao: dao)) }
    }

    private func write<T: Sendable>(_ block: @Sendable (any DiaryRepositoryTransaction) throws -> T) async throws -> T {
        try await database.queue.write { db in try block(RoomDiaryTransaction(db: db, dao: dao)) }
    }

    private func stream<T: Sendable>(_ values: AsyncValueObservation<T>) -> AsyncThrowingStream<T, Error> {
        AsyncThrowingStream { continuation in
            let task = Task {
                do {
                    for try await value in values { continuation.yield(value) }
                    continuation.finish()
                } catch {
                    continuation.finish(throwing: error)
                }
            }
            continuation.onTermination = { @Sendable _ in task.cancel() }
        }
    }
}

struct RoomDiaryTransaction: DiaryRepositoryTransaction {
    let db: Database
    let dao: DiaryDao

    func allChildren() throws -> [Child] {
        try dao.fetchChildren(db).map { $0.toChild() }
    }

    func allEvents() throws -> [any BabyEvent] {
        try dao.fetchAllEvents(db).toEvents()
    }

    @discardableResult
    func addChild(name: String, birthDate: Int64?, sex: Sex?, photo: ChildPhoto?) throws -> Int64 {
        let child = Child(id: 0, name: name.trimmingCharacters(in: .whitespacesAndNewlines), birthDate: birthDate, sex: sex, photo: photo)
        return try dao.insertChild(db, child: child.toEntity())
    }

    func updateChild(_ child: Child) throws {
        var trimmed = child
        trimmed.name = child.name.trimmingCharacters(in: .whitespacesAndNewlines)
        try dao.updateChild(db, child: trimmed.toEntity())
    }

    @discardableResult
    func deleteChild(_ childId: Int64) throws -> Bool {
        if try dao.countChildren(db) <= 1 { return false }
        // Явно, не полагаясь на ON DELETE CASCADE: внешние ключи SQLite могут быть выключены.
        try dao.deleteEvents(db, childId: childId)
        try dao.deleteChild(db, childId: childId)
        return true
    }

    @discardableResult
    func addEvent(_ event: any BabyEvent) throws -> Int64 {
        var entity = event.toEntity().truncated()
        entity.id = 0
        return try dao.insertEvent(db, event: entity)
    }

    func updateEvent(_ event: any BabyEvent) throws {
        try dao.updateEvent(db, event: event.toEntity().truncated())
    }

    func deleteEvent(_ event: any BabyEvent) throws {
        try dao.deleteEvent(db, eventId: event.id)
    }

    @discardableResult
    func startSleep(childId: Int64, start: Int64) throws -> Bool {
        if try dao.countActive(db, childId: childId, type: EventRecord.sleep) > 0 { return false }
        try finishActive(childId: childId, type: EventRecord.feeding, at: start)
        try finishActive(childId: childId, type: EventRecord.crying, at: start)
        try addEvent(Sleep(childId: childId, start: start))
        return true
    }

    func startCrying(childId: Int64, start: Int64) throws -> Int64? {
        if try dao.countActive(db, childId: childId, type: EventRecord.crying) > 0 { return nil }
        try finishActive(childId: childId, type: EventRecord.sleep, at: start)
        return try addEvent(Crying(childId: childId, start: start))
    }

    func startIllness(_ illness: Illness) throws -> Int64? {
        if try dao.countActive(db, childId: illness.childId, type: EventRecord.illness) > 0 { return nil }
        var started = illness
        started.end = nil
        return try addEvent(started)
    }

    @discardableResult
    func startFeeding(childId: Int64, start: Int64, breast: Breast) throws -> Bool {
        if try dao.countActive(db, childId: childId, type: EventRecord.feeding) > 0 { return false }
        try finishActive(childId: childId, type: EventRecord.sleep, at: start)
        try addEvent(Feeding(childId: childId, start: start, breast: breast))
        return true
    }

    func setEventPhotos(eventId: Int64, edit: PhotoEdit) throws {
        try dao.deletePhotosExcept(db, eventId: eventId, keep: edit.keep)
        for (position, id) in edit.keep.enumerated() {
            try dao.movePhoto(db, id: id, eventId: eventId, position: position)
        }
        for (index, jpeg) in edit.add.enumerated() {
            try dao.insertPhoto(db, photo: EventPhotoEntity(eventId: eventId, position: edit.keep.count + index, jpeg: jpeg))
        }
    }

    func photoIds() throws -> [Int64: [Int64]] {
        Dictionary(grouping: try dao.fetchPhotoRefs(db), by: \.eventId).mapValues { $0.map(\.id) }
    }

    func photoJpeg(_ photoId: Int64) throws -> Data? {
        try dao.fetchPhotoJpeg(db, id: photoId)
    }

    func addEventPhoto(eventId: Int64, position: Int, jpeg: Data) throws {
        try dao.insertPhoto(db, photo: EventPhotoEntity(eventId: eventId, position: position, jpeg: jpeg))
    }

    func addBottle(_ bottle: Bottle) throws {
        let at = bottle.start.truncatedToSecond()
        for sleep in try dao.activeEvents(db, childId: bottle.childId, type: EventRecord.sleep) where sleep.startTime <= at {
            var finished = sleep
            finished.endTime = at
            try dao.updateEvent(db, event: finished)
        }
        try addEvent(bottle)
    }

    private func finishActive(childId: Int64, type: String, at: Int64) throws {
        for event in try dao.activeEvents(db, childId: childId, type: type) {
            var finished = event
            finished.endTime = max(event.startTime, at.truncatedToSecond())
            try dao.updateEvent(db, event: finished)
        }
    }
}

private extension EventEntity {
    func truncated() -> EventEntity {
        var copy = self
        copy.startTime = startTime.truncatedToSecond()
        copy.endTime = endTime?.truncatedToSecond()
        return copy
    }
}

private extension Array where Element == EventEntity {
    func toEvents() -> [any BabyEvent] {
        compactMap { entity in
            guard let event = entity.toEvent() else {
                print("BabyLog: пропущена нераспознанная запись id=\(entity.id) type=\(entity.type)")
                return nil
            }
            return event
        }
    }
}
