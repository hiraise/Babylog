import Foundation
import Testing
@testable import BabyLog

private let repositoryT1: Int64 = 1_786_000_000_000
private let repositoryT2: Int64 = 1_786_003_600_000

private func eventHashes(_ events: [any BabyEvent]) -> [AnyHashable] {
    events.map { AnyHashable($0) }
}

struct RoomDiaryRepositoryTest {
    private let directory = FileManager.default.temporaryDirectory
        .appendingPathComponent("BabyLogTests-\(UUID().uuidString)", isDirectory: true)
    private var databaseURL: URL { directory.appendingPathComponent(AppDatabase.name) }

    private func withRepository<T>(_ block: (RoomDiaryRepository) async throws -> T) async throws -> T {
        let database = try AppDatabase.build(at: databaseURL)
        do {
            let result = try await block(RoomDiaryRepository(database: database))
            try database.close()
            return result
        } catch {
            try? database.close()
            throw error
        }
    }

    @Test func newDatabaseStartsWithOneDefaultChild() async throws {
        defer { try? FileManager.default.removeItem(at: directory) }
        try await withRepository { repository in
            let children = try await repository.allChildren()
            #expect(children == [Child(id: 1, name: Child.defaultName, birthDate: nil)])
        }
    }

    @Test func activeSleepSurvivesRestartAndStaysOnlyOne() async throws {
        defer { try? FileManager.default.removeItem(at: directory) }
        try await withRepository { repository in
            let started = try await repository.startSleep(childId: 1, start: repositoryT1)
            let duplicate = try await repository.startSleep(childId: 1, start: repositoryT2)
            #expect(started)
            #expect(!duplicate)
        }
        try await withRepository { repository in
            let events = try await repository.allEvents()
            #expect(eventHashes(events) == [AnyHashable(Sleep(id: 1, childId: 1, start: repositoryT1))])
        }
    }

    @Test func activeFeedingIsOnlyOneAndSleepEndsIt() async throws {
        defer { try? FileManager.default.removeItem(at: directory) }
        try await withRepository { repository in
            let started = try await repository.startFeeding(childId: 1, start: repositoryT1, breast: .left)
            let duplicate = try await repository.startFeeding(childId: 1, start: repositoryT2, breast: .right)
            let slept = try await repository.startSleep(childId: 1, start: repositoryT2)
            #expect(started)
            #expect(!duplicate)
            #expect(slept)
            let expected: [AnyHashable] = [
                AnyHashable(Feeding(id: 1, childId: 1, start: repositoryT1, end: repositoryT2, breast: .left)),
                AnyHashable(Sleep(id: 2, childId: 1, start: repositoryT2)),
            ]
            let events = try await repository.allEvents()
            #expect(eventHashes(events) == expected)
            let fedAgain = try await repository.startFeeding(childId: 1, start: repositoryT1, breast: .right)
            #expect(fedAgain)
            let sleep = try await repository.allEvents().compactMap { $0 as? Sleep }.last
            #expect(sleep == Sleep(id: 2, childId: 1, start: repositoryT2, end: repositoryT2))
        }
    }

    @Test func cryingWakesBabyAndFallingAsleepEndsCrying() async throws {
        defer { try? FileManager.default.removeItem(at: directory) }
        try await withRepository { repository in
            let slept = try await repository.startSleep(childId: 1, start: repositoryT1)
            let cryingId = try await repository.startCrying(childId: 1, start: repositoryT2)
            let duplicate = try await repository.startCrying(childId: 1, start: repositoryT2 + 60_000)
            #expect(slept)
            #expect(cryingId != nil)
            #expect(duplicate == nil)
            let expected: [AnyHashable] = [
                AnyHashable(Sleep(id: 1, childId: 1, start: repositoryT1, end: repositoryT2)),
                AnyHashable(Crying(id: 2, childId: 1, start: repositoryT2)),
            ]
            let events = try await repository.allEvents()
            #expect(eventHashes(events) == expected)
            let sleptAgain = try await repository.startSleep(childId: 1, start: repositoryT2 + 600_000)
            #expect(sleptAgain)
            let crying = try await repository.allEvents().compactMap { $0 as? Crying }.first
            #expect(crying == Crying(id: 2, childId: 1, start: repositoryT2, end: repositoryT2 + 600_000))
        }
    }

    @Test func timeIsStoredWithSecondPrecision() async throws {
        defer { try? FileManager.default.removeItem(at: directory) }
        try await withRepository { repository in
            _ = try await repository.addEvent(Medicine(childId: 1, start: repositoryT1 + 750, name: "Витамин D"))
            let start = try await repository.allEvents().first?.start
            #expect(start == repositoryT1)
        }
    }

    @Test func deletingChildRemovesOnlyItsRecordsAndNeverLastChild() async throws {
        defer { try? FileManager.default.removeItem(at: directory) }
        try await withRepository { repository in
            let second = try await repository.addChild(name: "Петя", birthDate: nil, sex: .boy, photo: nil)
            let added = try await repository.allChildren().last
            #expect(added == Child(id: second, name: "Петя", birthDate: nil, sex: .boy))
            _ = try await repository.addEvent(Medicine(childId: 1, start: repositoryT1, name: "Витамин D"))
            _ = try await repository.addEvent(Medicine(childId: second, start: repositoryT2, name: "Нурофен"))
            let deleted = try await repository.deleteChild(second)
            #expect(deleted)
            let childIds = try await repository.allChildren().map(\.id)
            let eventChildIds = try await repository.allEvents().map(\.childId)
            let deletedLast = try await repository.deleteChild(1)
            #expect(childIds == [1])
            #expect(eventChildIds == [1])
            #expect(!deletedLast)
        }
    }

    @Test func childPhotoIsStoredReplacedAndRemoved() async throws {
        defer { try? FileManager.default.removeItem(at: directory) }
        try await withRepository { repository in
            let first = ChildPhoto(jpeg: Data([1, 2, 3]))
            let id = try await repository.addChild(name: "Петя", birthDate: nil, sex: nil, photo: first)
            let initialPhoto = try await repository.allChildren().last?.photo
            #expect(initialPhoto == first)
            let second = ChildPhoto(jpeg: Data([4, 5]))
            var child = try await repository.allChildren().last!
            child.photo = second
            try await repository.updateChild(child)
            let replacedPhoto = try await repository.allChildren().last?.photo
            #expect(replacedPhoto == second)
            child.photo = nil
            try await repository.updateChild(child)
            let removedPhoto = try await repository.allChildren().last?.photo
            #expect(removedPhoto == nil)
            let deleted = try await repository.deleteChild(id)
            let remainingPhotos = try await repository.allChildren().map(\.photo)
            #expect(deleted)
            #expect(remainingPhotos == [nil])
        }
    }

    @Test func eventPhotosKeepOrderSurviveEditingAndGoWithEvent() async throws {
        defer { try? FileManager.default.removeItem(at: directory) }
        try await withRepository { repository in
            let diaper = Diaper(childId: 1, start: repositoryT1, kind: .dirty)
            let id = try await repository.addEvent(diaper)
            try await repository.setEventPhotos(eventId: id, edit: PhotoEdit(keep: [], add: [Data([1]), Data([2]), Data([3])]))
            var iterator = repository.observeEventPhotos(eventId: id).makeAsyncIterator()
            let initial = try await iterator.next() ?? []
            #expect(initial.count == 3)
            #expect(initial.map { Int($0.jpeg[0]) } == [1, 2, 3])
            let a = initial[0]
            let c = initial[2]
            try await repository.setEventPhotos(eventId: id, edit: PhotoEdit(keep: [c.id, a.id], add: [Data([4])]))
            iterator = repository.observeEventPhotos(eventId: id).makeAsyncIterator()
            let reordered = try await iterator.next()?.map { Int($0.jpeg[0]) }
            #expect(reordered == [3, 1, 4])
            var updated = diaper
            updated.id = id
            updated.kind = .mixed
            try await repository.updateEvent(updated)
            var counts = repository.observePhotoCounts(childId: 1).makeAsyncIterator()
            let photoCounts = try await counts.next()
            #expect(photoCounts == [id: 3])
            try await repository.deleteEvent(updated)
            iterator = repository.observeEventPhotos(eventId: id).makeAsyncIterator()
            let remaining = try await iterator.next()
            #expect(remaining == [])
        }
    }

    @Test func deletingChildRemovesPhotosOfItsRecords() async throws {
        defer { try? FileManager.default.removeItem(at: directory) }
        try await withRepository { repository in
            let second = try await repository.addChild(name: "Петя", birthDate: nil, sex: nil, photo: nil)
            let id = try await repository.addEvent(Diaper(childId: second, start: repositoryT1, kind: .wet))
            try await repository.setEventPhotos(eventId: id, edit: PhotoEdit(keep: [], add: [Data([1])]))
            var counts = repository.observePhotoCounts(childId: second).makeAsyncIterator()
            let photoCounts = try await counts.next()
            #expect(photoCounts == [id: 1])
            let deleted = try await repository.deleteChild(second)
            #expect(deleted)
            var photos = repository.observeEventPhotos(eventId: id).makeAsyncIterator()
            let remaining = try await photos.next()
            #expect(remaining == [])
        }
    }

    @Test func bottleEndsSleepItFallsIntoButNotLaterOne() async throws {
        defer { try? FileManager.default.removeItem(at: directory) }
        try await withRepository { repository in
            let slept = try await repository.startSleep(childId: 1, start: repositoryT1)
            #expect(slept)
            try await repository.addBottle(Bottle(childId: 1, start: repositoryT2, volumeMl: 120))
            let firstSleepEnd = try await repository.allEvents().compactMap { $0 as? Sleep }.first?.end
            #expect(firstSleepEnd == repositoryT2)
            let sleptAgain = try await repository.startSleep(childId: 1, start: repositoryT2 + 60_000)
            #expect(sleptAgain)
            try await repository.addBottle(Bottle(childId: 1, start: repositoryT1 + 60_000, volumeMl: 90))
            let lastSleepEnd = try await repository.allEvents().compactMap { $0 as? Sleep }.last?.end
            let volumes = try await repository.allEvents().compactMap { $0 as? Bottle }.sorted { $0.id < $1.id }.map(\.volumeMl)
            #expect(lastSleepEnd == nil)
            #expect(volumes == [120, 90])
        }
    }

    @Test func illnessIsOnlyOneAndDoesNotTouchTimers() async throws {
        defer { try? FileManager.default.removeItem(at: directory) }
        try await withRepository { repository in
            let slept = try await repository.startSleep(childId: 1, start: repositoryT1)
            let illness = Illness(childId: 1, start: repositoryT2, end: repositoryT2 + 1_000)
            let illnessId = try await repository.startIllness(illness)
            let duplicate = try await repository.startIllness(illness)
            let events = try await repository.allEvents()
            #expect(slept)
            #expect(illnessId != nil)
            #expect(duplicate == nil)
            #expect(events.compactMap { $0 as? Sleep }.first?.end == nil)
            #expect(events.compactMap { $0 as? Illness }.first?.end == nil)
        }
    }
}
