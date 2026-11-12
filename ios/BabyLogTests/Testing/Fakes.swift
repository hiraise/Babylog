import Foundation
@testable import BabyLog

private final class FakeValueStore<Value: Sendable>: @unchecked Sendable {
    private let lock = NSLock()
    private var value: Value
    private var continuations: [UUID: AsyncThrowingStream<Value, Error>.Continuation] = [:]

    init(_ value: Value) {
        self.value = value
    }

    var current: Value {
        lock.lock()
        defer { lock.unlock() }
        return value
    }

    func set(_ newValue: Value) {
        lock.lock()
        value = newValue
        let continuations = Array(continuations.values)
        lock.unlock()
        for continuation in continuations { continuation.yield(newValue) }
    }

    func stream() -> AsyncThrowingStream<Value, Error> {
        AsyncThrowingStream { continuation in
            let id = UUID()
            lock.lock()
            continuations[id] = continuation
            let initial = value
            lock.unlock()
            continuation.yield(initial)
            continuation.onTermination = { @Sendable _ in
                self.lock.lock()
                self.continuations.removeValue(forKey: id)
                self.lock.unlock()
            }
        }
    }
}

/** Репозиторий в памяти с тем же поведением, что у GRDB-реализации. */
final class FakeDiaryRepository: DiaryRepository, @unchecked Sendable {
    private let childrenStore: FakeValueStore<[Child]>
    private let eventsStore = FakeValueStore<[any BabyEvent]>([])
    private let photosStore = FakeValueStore<[Int64: [EventPhoto]]>([:])
    private let lock = NSLock()
    private var nextChildId: Int64
    private var nextEventId: Int64 = 1
    private var nextPhotoId: Int64 = 1
    private var positions: [Int64: Int] = [:]

    init(children: [Child] = [Child(id: 1, name: Child.defaultName, birthDate: nil)]) {
        childrenStore = FakeValueStore(children)
        nextChildId = (children.map(\.id).max() ?? 0) + 1
    }

    var children: [Child] { childrenStore.current }
    var events: [any BabyEvent] { eventsStore.current }
    var photos: [Int64: [EventPhoto]] { photosStore.current }

    func observeChildren() -> AsyncThrowingStream<[Child], Error> {
        childrenStore.stream()
    }

    func observeEvents(childId: Int64) -> AsyncThrowingStream<[any BabyEvent], Error> {
        let source = eventsStore.stream()
        return AsyncThrowingStream { continuation in
            let task = Task {
                do {
                    for try await list in source {
                        continuation.yield(list.filter { $0.childId == childId }.sorted {
                            $0.start == $1.start ? $0.id > $1.id : $0.start > $1.start
                        })
                    }
                    continuation.finish()
                } catch {
                    continuation.finish(throwing: error)
                }
            }
            continuation.onTermination = { @Sendable _ in task.cancel() }
        }
    }

    func allChildren() async throws -> [Child] { syncAllChildren() }
    func allEvents() async throws -> [any BabyEvent] { try syncAllEvents() }

    @discardableResult
    func addChild(name: String, birthDate: Int64?, sex: Sex?, photo: ChildPhoto?) async throws -> Int64 {
        syncAddChild(name: name, birthDate: birthDate, sex: sex, photo: photo)
    }

    func updateChild(_ child: Child) async throws { syncUpdateChild(child) }
    @discardableResult func deleteChild(_ childId: Int64) async throws -> Bool { syncDeleteChild(childId) }
    @discardableResult func addEvent(_ event: any BabyEvent) async throws -> Int64 { syncAddEvent(event) }
    func updateEvent(_ event: any BabyEvent) async throws { syncUpdateEvent(event) }
    func deleteEvent(_ event: any BabyEvent) async throws { syncDeleteEvent(event) }
    @discardableResult func startSleep(childId: Int64, start: Int64) async throws -> Bool { syncStartSleep(childId: childId, start: start) }
    func startCrying(childId: Int64, start: Int64) async throws -> Int64? { syncStartCrying(childId: childId, start: start) }
    func startIllness(_ illness: Illness) async throws -> Int64? { syncStartIllness(illness) }
    @discardableResult func startFeeding(childId: Int64, start: Int64, breast: Breast) async throws -> Bool {
        syncStartFeeding(childId: childId, start: start, breast: breast)
    }

    func observeEventPhotos(eventId: Int64) -> AsyncThrowingStream<[EventPhoto], Error> {
        let source = photosStore.stream()
        return AsyncThrowingStream { continuation in
            let task = Task {
                do {
                    for try await photos in source { continuation.yield(photos[eventId] ?? []) }
                    continuation.finish()
                } catch {
                    continuation.finish(throwing: error)
                }
            }
            continuation.onTermination = { @Sendable _ in task.cancel() }
        }
    }

    func observePhotoCounts(childId: Int64) -> AsyncThrowingStream<[Int64: Int], Error> {
        let eventSource = eventsStore.stream()
        let photoSource = photosStore.stream()
        return AsyncThrowingStream { continuation in
            let state = FakePhotoCounts(childId: childId)
            let eventsTask = Task {
                do {
                    for try await events in eventSource { continuation.yield(await state.setEvents(events)) }
                } catch {
                    continuation.finish(throwing: error)
                }
            }
            let photosTask = Task {
                do {
                    for try await photos in photoSource { continuation.yield(await state.setPhotos(photos)) }
                } catch {
                    continuation.finish(throwing: error)
                }
            }
            continuation.onTermination = { @Sendable _ in
                eventsTask.cancel()
                photosTask.cancel()
            }
        }
    }

    func setEventPhotos(eventId: Int64, edit: PhotoEdit) async throws { syncSetEventPhotos(eventId: eventId, edit: edit) }
    func photoIds() async throws -> [Int64: [Int64]] { syncPhotoIds() }
    func photoJpeg(_ photoId: Int64) async throws -> Data? { syncPhotoJpeg(photoId) }
    func addEventPhoto(eventId: Int64, position: Int, jpeg: Data) async throws {
        syncAddEventPhoto(eventId: eventId, position: position, jpeg: jpeg)
    }
    func addBottle(_ bottle: Bottle) async throws { syncAddBottle(bottle) }

    func inTransaction<T: Sendable>(_ block: @Sendable (any DiaryRepositoryTransaction) throws -> T) async throws -> T {
        try block(FakeDiaryTransaction(repository: self))
    }

    fileprivate func syncAllChildren() -> [Child] { children }
    fileprivate func syncAllEvents() throws -> [any BabyEvent] {
        events.sorted { $0.start == $1.start ? $0.id < $1.id : $0.start < $1.start }
    }

    fileprivate func syncAddChild(name: String, birthDate: Int64?, sex: Sex?, photo: ChildPhoto?) -> Int64 {
        lock.lock()
        let id = nextChildId
        nextChildId += 1
        lock.unlock()
        childrenStore.set(children + [Child(id: id, name: name.trimmingCharacters(in: .whitespacesAndNewlines), birthDate: birthDate, sex: sex, photo: photo)])
        return id
    }

    fileprivate func syncUpdateChild(_ child: Child) {
        var trimmed = child
        trimmed.name = child.name.trimmingCharacters(in: .whitespacesAndNewlines)
        childrenStore.set(children.map { $0.id == child.id ? trimmed : $0 })
    }

    fileprivate func syncDeleteChild(_ childId: Int64) -> Bool {
        guard children.count > 1 else { return false }
        childrenStore.set(children.filter { $0.id != childId })
        let removed = Set(events.filter { $0.childId == childId }.map(\.id))
        eventsStore.set(events.filter { $0.childId != childId })
        var updated = photos
        for id in removed { updated.removeValue(forKey: id) }
        photosStore.set(updated)
        return true
    }

    fileprivate func syncAddEvent(_ event: any BabyEvent) -> Int64 {
        lock.lock()
        let id = nextEventId
        nextEventId += 1
        lock.unlock()
        eventsStore.set(events + [stored(event, id: id)])
        return id
    }

    fileprivate func syncUpdateEvent(_ event: any BabyEvent) {
        eventsStore.set(events.map { $0.id == event.id ? stored(event, id: event.id) : $0 })
    }

    fileprivate func syncDeleteEvent(_ event: any BabyEvent) {
        eventsStore.set(events.filter { $0.id != event.id })
        var updated = photos
        updated.removeValue(forKey: event.id)
        photosStore.set(updated)
    }

    fileprivate func syncSetEventPhotos(eventId: Int64, edit: PhotoEdit) {
        let current = Dictionary(uniqueKeysWithValues: (photos[eventId] ?? []).map { ($0.id, $0) })
        let kept = edit.keep.compactMap { current[$0] }
        var added: [EventPhoto] = []
        for jpeg in edit.add {
            lock.lock()
            let id = nextPhotoId
            nextPhotoId += 1
            lock.unlock()
            added.append(EventPhoto(id: id, jpeg: jpeg))
        }
        let list = kept + added
        for (index, photo) in list.enumerated() { positions[photo.id] = index }
        var updated = photos
        updated[eventId] = list
        photosStore.set(updated)
    }

    fileprivate func syncPhotoIds() -> [Int64: [Int64]] {
        photos.filter { !$0.value.isEmpty }.mapValues { $0.map(\.id) }
    }

    fileprivate func syncPhotoJpeg(_ photoId: Int64) -> Data? {
        photos.values.flatMap { $0 }.first { $0.id == photoId }?.jpeg
    }

    fileprivate func syncAddEventPhoto(eventId: Int64, position: Int, jpeg: Data) {
        lock.lock()
        let id = nextPhotoId
        nextPhotoId += 1
        lock.unlock()
        let photo = EventPhoto(id: id, jpeg: jpeg)
        positions[id] = position
        var updated = photos
        updated[eventId] = ((updated[eventId] ?? []) + [photo]).sorted {
            (positions[$0.id] ?? 0) == (positions[$1.id] ?? 0) ? $0.id < $1.id : (positions[$0.id] ?? 0) < (positions[$1.id] ?? 0)
        }
        photosStore.set(updated)
    }

    fileprivate func syncStartSleep(childId: Int64, start: Int64) -> Bool {
        if events.contains(where: { $0.childId == childId && ($0 as? Sleep)?.isActive == true }) { return false }
        for event in events.compactMap({ $0 as? Feeding }) where event.childId == childId && event.isActive {
            var copy = event
            copy.end = max(event.start, start)
            syncUpdateEvent(copy)
        }
        for event in events.compactMap({ $0 as? Crying }) where event.childId == childId && event.isActive {
            var copy = event
            copy.end = max(event.start, start)
            syncUpdateEvent(copy)
        }
        _ = syncAddEvent(Sleep(childId: childId, start: start))
        return true
    }

    fileprivate func syncStartCrying(childId: Int64, start: Int64) -> Int64? {
        if events.contains(where: { $0.childId == childId && ($0 as? Crying)?.isActive == true }) { return nil }
        for event in events.compactMap({ $0 as? Sleep }) where event.childId == childId && event.isActive {
            var copy = event
            copy.end = max(event.start, start)
            syncUpdateEvent(copy)
        }
        return syncAddEvent(Crying(childId: childId, start: start))
    }

    fileprivate func syncStartIllness(_ illness: Illness) -> Int64? {
        if events.contains(where: { $0.childId == illness.childId && ($0 as? Illness)?.isActive == true }) { return nil }
        var started = illness
        started.end = nil
        return syncAddEvent(started)
    }

    fileprivate func syncStartFeeding(childId: Int64, start: Int64, breast: Breast) -> Bool {
        if events.contains(where: { $0.childId == childId && ($0 as? Feeding)?.isActive == true }) { return false }
        for event in events.compactMap({ $0 as? Sleep }) where event.childId == childId && event.isActive {
            var copy = event
            copy.end = max(event.start, start)
            syncUpdateEvent(copy)
        }
        _ = syncAddEvent(Feeding(childId: childId, start: start, breast: breast))
        return true
    }

    fileprivate func syncAddBottle(_ bottle: Bottle) {
        let at = bottle.start.truncatedToSecond()
        for event in events.compactMap({ $0 as? Sleep }) where event.childId == bottle.childId && event.isActive && event.start <= at {
            var copy = event
            copy.end = at
            syncUpdateEvent(copy)
        }
        _ = syncAddEvent(bottle)
    }

    private func stored(_ event: any BabyEvent, id: Int64) -> any BabyEvent {
        let record = event.toRecord()
        let truncated = EventRecord(
            type: record.type,
            start: record.start.truncatedToSecond(),
            end: record.end?.truncatedToSecond(),
            detail: record.detail,
            note: record.note
        )
        return truncated.toEvent(id: id, childId: event.childId)!
    }
}

private struct FakeDiaryTransaction: DiaryRepositoryTransaction {
    let repository: FakeDiaryRepository

    func allChildren() throws -> [Child] { repository.syncAllChildren() }
    func allEvents() throws -> [any BabyEvent] { try repository.syncAllEvents() }
    @discardableResult func addChild(name: String, birthDate: Int64?, sex: Sex?, photo: ChildPhoto?) throws -> Int64 {
        repository.syncAddChild(name: name, birthDate: birthDate, sex: sex, photo: photo)
    }
    func updateChild(_ child: Child) throws { repository.syncUpdateChild(child) }
    @discardableResult func deleteChild(_ childId: Int64) throws -> Bool { repository.syncDeleteChild(childId) }
    @discardableResult func addEvent(_ event: any BabyEvent) throws -> Int64 { repository.syncAddEvent(event) }
    func updateEvent(_ event: any BabyEvent) throws { repository.syncUpdateEvent(event) }
    func deleteEvent(_ event: any BabyEvent) throws { repository.syncDeleteEvent(event) }
    @discardableResult func startSleep(childId: Int64, start: Int64) throws -> Bool {
        repository.syncStartSleep(childId: childId, start: start)
    }
    func startCrying(childId: Int64, start: Int64) throws -> Int64? { repository.syncStartCrying(childId: childId, start: start) }
    func startIllness(_ illness: Illness) throws -> Int64? { repository.syncStartIllness(illness) }
    @discardableResult func startFeeding(childId: Int64, start: Int64, breast: Breast) throws -> Bool {
        repository.syncStartFeeding(childId: childId, start: start, breast: breast)
    }
    func setEventPhotos(eventId: Int64, edit: PhotoEdit) throws { repository.syncSetEventPhotos(eventId: eventId, edit: edit) }
    func photoIds() throws -> [Int64: [Int64]] { repository.syncPhotoIds() }
    func photoJpeg(_ photoId: Int64) throws -> Data? { repository.syncPhotoJpeg(photoId) }
    func addEventPhoto(eventId: Int64, position: Int, jpeg: Data) throws {
        repository.syncAddEventPhoto(eventId: eventId, position: position, jpeg: jpeg)
    }
    func addBottle(_ bottle: Bottle) throws { repository.syncAddBottle(bottle) }
}

private actor FakePhotoCounts {
    let childId: Int64
    var events: [any BabyEvent] = []
    var photos: [Int64: [EventPhoto]] = [:]

    init(childId: Int64) {
        self.childId = childId
    }

    func setEvents(_ events: [any BabyEvent]) -> [Int64: Int] {
        self.events = events
        return counts()
    }

    func setPhotos(_ photos: [Int64: [EventPhoto]]) -> [Int64: Int] {
        self.photos = photos
        return counts()
    }

    private func counts() -> [Int64: Int] {
        let ids = Set(events.filter { $0.childId == childId }.map(\.id))
        return photos.filter { ids.contains($0.key) && !$0.value.isEmpty }.mapValues(\.count)
    }
}

/** Сообщение о восстановлении базы в памяти (ADR-0033). */
final class FakeRecoveryNotices: RecoveryNotices, @unchecked Sendable {
    private let lock = NSLock()
    private var recovery: DatabaseRecovery?

    var pending: DatabaseRecovery? {
        lock.lock()
        defer { lock.unlock() }
        return recovery
    }

    func report(_ recovery: DatabaseRecovery) {
        lock.lock()
        self.recovery = recovery
        lock.unlock()
    }

    func dismiss() {
        lock.lock()
        recovery = nil
        lock.unlock()
    }
}
