import Foundation
import Testing
@testable import BabyLog

/** Перенос из меню: архив получает имя текущего дня, записывает и возвращает записи с фото. */
@MainActor
@Suite(.serialized)
struct TransferMenuTest {
    private let utc = TimeZone(secondsFromGMT: 0)!
    private let now: Int64 = 1_792_663_200_000
    private let jpeg = Data([0xFF, 0xD8, 0xFF, 0xE0, 1, 2, 3])

    @Test func exportFilenameUsesCurrentDay() {
        #expect(diaryArchiveFilename(now, timeZone: utc) == "baby-diary-2026-10-22.zip")
    }

    @Test func exportSavesRecordsAndPhotosToPickedArchive() async throws {
        let repository = FakeDiaryRepository(children: [Child(id: 1, name: "Маша", birthDate: nil)])
        let diaper = try await repository.addEvent(Diaper(childId: 1, start: now, kind: .dirty))
        try await repository.setEventPhotos(eventId: diaper, edit: PhotoEdit(keep: [], add: [jpeg]))
        let documents = FakeDocumentStore()
        let model = DiaryViewModel(
            repository: repository,
            selection: MemorySelectedChildStore(),
            documents: documents,
            clock: TestClock(date: Date(timeIntervalSince1970: Double(now) / 1000), timeZone: utc)
        )
        _ = await waitUntil { model.state.child != nil }
        let url = URL(fileURLWithPath: "/tmp/picked-diary.zip")

        await model.exportTo(url)
        #expect(documents.files[url] != nil)
        #expect(model.state.message == "Экспортировано записей: 1, фото: 1")
    }

    @Test func importBringsRecordsAndPhotosBack() async throws {
        let source = FakeDiaryRepository(children: [Child(id: 1, name: "Маша", birthDate: nil)])
        let diaper = try await source.addEvent(Diaper(childId: 1, start: now, kind: .dirty))
        try await source.setEventPhotos(eventId: diaper, edit: PhotoEdit(keep: [], add: [jpeg]))
        let documents = FakeDocumentStore()
        let url = URL(fileURLWithPath: "/tmp/picked-diary.zip")
        _ = try await documents.write(url) { try await exportArchive(repository: source, output: $0, zone: utc) }
        let target = FakeDiaryRepository(children: [Child(id: 1, name: "Маша", birthDate: nil)])
        let model = DiaryViewModel(
            repository: target,
            selection: MemorySelectedChildStore(),
            documents: documents,
            clock: TestClock(date: Date(timeIntervalSince1970: Double(now) / 1000), timeZone: utc)
        )
        _ = await waitUntil { model.state.child != nil }

        await model.importFrom(url)
        #expect(model.state.message == "Импорт: добавлено 1, фото 1")
        #expect(target.photos.values.first?.first?.jpeg == jpeg)
    }
}
