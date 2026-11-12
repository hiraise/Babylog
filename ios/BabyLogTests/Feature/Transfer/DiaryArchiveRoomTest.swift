import Foundation
import Testing
@testable import BabyLog

/**
 Архив с фото на настоящей базе GRDB (ADR-0045): фото пишутся в транзакции импорта,
 и оборванный архив не оставляет в дневнике ничего.
 */
struct DiaryArchiveRoomTest {
    private let file = URL(fileURLWithPath: "/baby-diary-room.zip")
    private let files = FakeDocumentStore()

    private func exportSource(_ source: AppDatabase, photoSize: Int = 64) async throws {
        let repository = RoomDiaryRepository(database: source)
        let masha = try await repository.addChild(name: "Маша", birthDate: nil, sex: nil, photo: ChildPhoto(jpeg: testJpeg(1)))
        let diaper = try await repository.addEvent(Diaper(childId: masha, start: 1_000_000, kind: .dirty))
        try await repository.setEventPhotos(
            eventId: diaper,
            edit: PhotoEdit(keep: [], add: [testJpeg(2, size: photoSize), testJpeg(3, size: photoSize)])
        )
        _ = try await files.write(file) { output in
            try await exportArchive(repository: repository, output: output)
        }
    }

    @Test func photosAreWrittenInImportTransaction() async throws {
        let source = try AppDatabase.inMemory()
        let target = try AppDatabase.inMemory()
        defer {
            try? source.close()
            try? target.close()
        }
        try await exportSource(source)
        let repository = RoomDiaryRepository(database: target)
        let result = try await importFile(repository: repository, documents: files, url: file, defaultChildId: 1)
        #expect(result.photosAdded == 2)
        let masha = try await repository.allChildren().first
        #expect(masha?.photo?.jpeg == testJpeg(1))
        let diaper = try await repository.allEvents().first
        var iterator = repository.observeEventPhotos(eventId: diaper!.id).makeAsyncIterator()
        let photos = try await iterator.next() ?? []
        #expect(photos.map(\.jpeg) == [testJpeg(2), testJpeg(3)])
    }

    @Test func brokenArchiveLeavesDiaryAsItWas() async throws {
        let source = try AppDatabase.inMemory()
        let target = try AppDatabase.inMemory()
        defer {
            try? source.close()
            try? target.close()
        }
        try await exportSource(source, photoSize: 50_000)
        let whole = files.files[file]!
        files.files[file] = whole.dropLast(30_000)
        let repository = RoomDiaryRepository(database: target)
        do {
            _ = try await importFile(repository: repository, documents: files, url: file, defaultChildId: 1)
            Issue.record("Оборванный архив должен отклоняться")
        } catch let error as TransferError {
            #expect(error.message.hasPrefix("архив"))
        }
        let children = try await repository.allChildren()
        let events = try await repository.allEvents()
        let photos = try await repository.photoIds()
        #expect(children.isEmpty)
        #expect(events.isEmpty)
        #expect(photos.isEmpty)
    }
}
