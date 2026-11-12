import Foundation
import Testing
import ZIPFoundation
@testable import BabyLog

private let diaryArchiveFile = URL(fileURLWithPath: "/baby-diary.zip")

/** «JPEG» для импорта: начало файла JPEG, затем байты `tag` — само изображение импорт не разбирает. */
func testJpeg(_ tag: Int, size: Int = 64) -> Data {
    Data([0xFF, 0xD8, 0xFF, 0xE0]) + Data(repeating: UInt8(tag), count: size)
}

/** Файлы архива по порядку: имя → содержимое. */
func archiveEntries(_ data: Data) throws -> [(String, Data)] {
    let archive = try Archive(data: data, accessMode: .read)
    var result: [(String, Data)] = []
    for entry in archive {
        var bytes = Data()
        try archive.extract(entry) { bytes.append($0) }
        result.append((entry.path, bytes))
    }
    return result
}

/** Архив экспорта с фото (ADR-0045): что в нём лежит и как импорт возвращает фото записям и детям. */
struct DiaryArchiveTest {
    private func ms(_ value: String) -> Int64 { CsvCodec.parseTime(value)! }

    /** Дневник на «старом телефоне»: фото у Маши, у подгузника два фото, у заметки Пети — одно. */
    private func sourceDiary() async throws -> FakeDiaryRepository {
        let repository = FakeDiaryRepository(children: [
            Child(id: 1, name: "Маша", birthDate: nil, sex: .girl, photo: ChildPhoto(jpeg: testJpeg(1))),
            Child(id: 2, name: "Петя", birthDate: nil),
        ])
        _ = try await repository.addEvent(Sleep(childId: 1, start: ms("2026-08-15 13:00:00"), end: ms("2026-08-15 14:00:00")))
        let diaper = try await repository.addEvent(Diaper(childId: 1, start: ms("2026-08-15 15:10:00"), kind: .dirty))
        try await repository.setEventPhotos(eventId: diaper, edit: PhotoEdit(keep: [], add: [testJpeg(2), testJpeg(3)]))
        let note = try await repository.addEvent(Note(childId: 2, start: ms("2026-08-15 15:10:00"), note: "сыпь, см. фото"))
        try await repository.setEventPhotos(eventId: note, edit: PhotoEdit(keep: [], add: [testJpeg(4)]))
        return repository
    }

    private func exported(_ repository: FakeDiaryRepository) async throws -> FakeDocumentStore {
        let store = FakeDocumentStore()
        _ = try await store.write(diaryArchiveFile) { output in
            try await exportArchive(repository: repository, output: output)
        }
        return store
    }

    private func photosOf(_ repository: FakeDiaryRepository, where predicate: (any BabyEvent) -> Bool) -> [Data] {
        let event = repository.events.filter(predicate).first!
        return (repository.photos[event.id] ?? []).map(\.jpeg)
    }

    @Test func archiveStartsWithCSVAndNamesPhotosByRecordTime() async throws {
        let store = FakeDocumentStore()
        let export = try await store.write(diaryArchiveFile) { output in
            try await exportArchive(repository: sourceDiary(), output: output)
        }
        #expect(export == DiaryExport(events: 3, photos: 4))
        #expect(export.describe() == "Экспортировано записей: 3, фото: 4")
        let entries = try archiveEntries(store.files[diaryArchiveFile]!)
        #expect(entries.map(\.0) == [
            "diary.csv",
            "photos/child_1.jpg",
            "photos/2026-08-15_15-10-00_1.jpg",
            "photos/2026-08-15_15-10-00_2.jpg",
            "photos/2026-08-15_15-10-00_3.jpg",
        ])
        let csv = String(decoding: entries[0].1, as: UTF8.self)
        #expect(csv.contains("DIAPER,2026-08-15 15:10:00,,DIRTY,,Маша,photos/2026-08-15_15-10-00_1.jpg;photos/2026-08-15_15-10-00_2.jpg\r\n"))
        #expect(csv.contains("PROFILE,,,GIRL,,Маша,photos/child_1.jpg\r\n"))
    }

    @Test func photosMoveToAnotherPhoneWithTheirRecords() async throws {
        let store = try await exported(sourceDiary())
        let target = FakeDiaryRepository()
        let result = try await importFile(repository: target, documents: store, url: diaryArchiveFile, defaultChildId: 1)
        #expect(result.added == 3)
        #expect(result.photosAdded == 3)
        #expect(result.childPhotosSet == 1)
        #expect(result.photosSkipped == 0)
        #expect(result.describe() == "Импорт: добавлено 3, новых детей 2, фото 3, фото детей 1")
        #expect(target.children.first { $0.name == "Маша" }?.photo?.jpeg == testJpeg(1))
        #expect(photosOf(target, where: { $0 is Diaper }) == [testJpeg(2), testJpeg(3)])
        #expect(photosOf(target, where: { $0 is Note }) == [testJpeg(4)])
        #expect(store.opened == 2)
    }

    @Test func archiveReturnsPhotosToRecordsMovedEarlierWithoutThem() async throws {
        let source = try await sourceDiary()
        let target = FakeDiaryRepository()
        let csv = CsvCodec.encode(children: try await source.allChildren(), events: try await source.allEvents())
        _ = try await importCsv(repository: target, text: csv, defaultChildId: 1)
        let result = try await importFile(repository: target, documents: exported(source), url: diaryArchiveFile, defaultChildId: 1)
        #expect(result.added == 0)
        #expect(result.duplicates == 3)
        #expect(result.photosAdded == 3)
        #expect(result.photosToExisting == 3)
        #expect(result.childPhotosSet == 1)
        #expect(result.describe() == "Импорт: добавлено 0, дубликатов 3, фото 3 (к прежним записям — 3), фото детей 1")
        #expect(photosOf(target, where: { $0 is Diaper }) == [testJpeg(2), testJpeg(3)])
    }

    @Test func recordsWithPhotosAndChildrenWithPhotoKeepTheirOwn() async throws {
        let store = try await exported(sourceDiary())
        let target = FakeDiaryRepository(children: [
            Child(id: 1, name: "Маша", birthDate: nil, photo: ChildPhoto(jpeg: testJpeg(9))),
        ])
        _ = try await importFile(repository: target, documents: store, url: diaryArchiveFile, defaultChildId: 1)
        let again = try await importFile(repository: target, documents: store, url: diaryArchiveFile, defaultChildId: 1)
        #expect(again.duplicates == 3)
        #expect(again.photosAdded == 0)
        #expect(again.childPhotosSet == 0)
        #expect(store.opened == 3)
        #expect(photosOf(target, where: { $0 is Diaper }).count == 2)
        #expect(target.children.first { $0.name == "Маша" }?.photo?.jpeg == testJpeg(9))
    }

    @Test func csvTakenOutOfArchiveImportsRecordsAndAsksForArchive() async throws {
        let csv = try archiveEntries(try await exported(sourceDiary()).files[diaryArchiveFile]!)[0].1
        let csvURL = URL(fileURLWithPath: "/diary.csv")
        let store = FakeDocumentStore(files: [csvURL: csv])
        let result = try await importFile(repository: FakeDiaryRepository(), documents: store, url: csvURL, defaultChildId: 1)
        #expect(result.added == 3)
        #expect(result.photosSkipped == 4)
        #expect(result.describe().hasSuffix(", фото не загружены (4) — они в архиве .zip, импортируйте его"))
        #expect(store.opened == 1)
    }

    @Test func archiveRepackedIntoFolderStillImports() async throws {
        let archive = makeTestZip([
            ("__MACOSX/Дневник/._diary.csv", Data([0, 5, 22, 7])),
            ("Дневник/diary.csv", Data(diaryCsv("photos/a.jpg").utf8)),
            ("Дневник/photos/a.jpg", testJpeg(5)),
        ])
        let store = FakeDocumentStore(files: [diaryArchiveFile: archive])
        let target = FakeDiaryRepository()
        let result = try await importFile(repository: target, documents: store, url: diaryArchiveFile, defaultChildId: 1)
        #expect(result.added == 1)
        #expect(result.photosAdded == 1)
        #expect(photosOf(target, where: { $0 is Diaper }) == [testJpeg(5)])
    }

    @Test func archiveRepackedOnWindowsWithCyrillicFolderImports() async throws {
        let archive = makeTestZip([
            ("Дневник/diary.csv", Data(diaryCsv("photos/a.jpg").utf8)),
            ("Дневник/photos/a.jpg", testJpeg(5)),
        ], pathEncoding: .windowsCP1251)
        let store = FakeDocumentStore(files: [diaryArchiveFile: archive])
        let result = try await importFile(repository: FakeDiaryRepository(), documents: store, url: diaryArchiveFile, defaultChildId: 1)
        #expect(result.photosAdded == 1)
    }

    @Test func photosKeepOrderOfCSVWhateverOrderInArchive() async throws {
        let archive = makeTestZip([
            ("diary.csv", Data(diaryCsv("photos/a.jpg;photos/b.jpg").utf8)),
            ("photos/b.jpg", testJpeg(7)),
            ("photos/a.jpg", testJpeg(6)),
        ])
        let store = FakeDocumentStore(files: [diaryArchiveFile: archive])
        let target = FakeDiaryRepository()
        _ = try await importFile(repository: target, documents: store, url: diaryArchiveFile, defaultChildId: 1)
        #expect(photosOf(target, where: { $0 is Diaper }) == [testJpeg(6), testJpeg(7)])
    }

    @Test func missingTooBigOrNotJpegPhotosAreSkippedAndRecordStays() async throws {
        let archive = makeTestZip([
            ("diary.csv", Data(diaryCsv("photos/big.jpg;photos/text.jpg;photos/missing.jpg").utf8)),
            ("photos/big.jpg", testJpeg(1, size: EventPhoto.maxBytes + 1)),
            ("photos/text.jpg", Data("не фото".utf8)),
        ])
        let store = FakeDocumentStore(files: [diaryArchiveFile: archive])
        let target = FakeDiaryRepository()
        let result = try await importFile(repository: target, documents: store, url: diaryArchiveFile, defaultChildId: 1)
        #expect(result.added == 1)
        #expect(result.photosAdded == 0)
        #expect(result.photosSkipped == 3)
        #expect(result.describe().hasSuffix(", не загружено фото 3"))
        #expect(target.photos.isEmpty)
    }

    @Test func brokenArchiveStopsImportWithClearMessage() async throws {
        let source = try await sourceDiary()
        let note = source.events.first { $0 is Note }!
        try await source.setEventPhotos(eventId: note.id, edit: PhotoEdit(keep: [], add: [testJpeg(4, size: 50_000)]))
        let whole = try await exported(source).files[diaryArchiveFile]!
        let store = FakeDocumentStore(files: [diaryArchiveFile: whole.dropLast(30_000)])
        do {
            _ = try await importFile(repository: FakeDiaryRepository(), documents: store, url: diaryArchiveFile, defaultChildId: 1)
            Issue.record("Оборванный архив должен отклоняться")
        } catch let error as TransferError {
            #expect(error.message.hasPrefix("архив"))
        }
    }

    @Test func archiveWithoutDiaryCSVIsRejected() async throws {
        let archive = makeTestZip([("photos/a.jpg", testJpeg(1))])
        let store = FakeDocumentStore(files: [diaryArchiveFile: archive])
        do {
            _ = try await importFile(repository: FakeDiaryRepository(), documents: store, url: diaryArchiveFile, defaultChildId: 1)
            Issue.record("Архив без diary.csv должен отклоняться")
        } catch let error as TransferError {
            #expect(error.message == "в архиве нет diary.csv — это не дневник BabyLog")
        }
    }

    @Test func fakeStoreKeepsPhotoOrder() async throws {
        let repository = FakeDiaryRepository()
        let id = try await repository.addEvent(Diaper(childId: 1, start: 0, kind: .wet))
        try await repository.addEventPhoto(eventId: id, position: 1, jpeg: testJpeg(2))
        try await repository.addEventPhoto(eventId: id, position: 0, jpeg: testJpeg(1))
        var iterator = repository.observeEventPhotos(eventId: id).makeAsyncIterator()
        let photos = try await iterator.next() ?? []
        #expect(photos.map(\.jpeg) == [testJpeg(1), testJpeg(2)])
    }

    private func diaryCsv(_ photos: String) -> String {
        "\(CsvCodec.header)\r\nPROFILE,,,,,Маша,\r\nDIAPER,2026-08-15 15:10:00,,WET,,Маша,\(photos)\r\n"
    }
}

/** ZIP STORED с выбранной кодировкой имён: ZIPFoundation при записи всегда ставит UTF-8. */
private func makeTestZip(_ files: [(String, Data)], pathEncoding: String.Encoding = .utf8) -> Data {
    var zip = Data()
    var central = Data()
    var offset: UInt32 = 0
    for (name, bytes) in files {
        let nameData = name.data(using: pathEncoding)!
        let checksum = testCRC32(bytes)
        let flags: UInt16 = pathEncoding == .utf8 ? 0x0800 : 0
        var local = Data()
        local.appendLittleEndian(UInt32(0x04034B50))
        local.appendLittleEndian(UInt16(20))
        local.appendLittleEndian(flags)
        local.appendLittleEndian(UInt16(0))
        local.appendLittleEndian(UInt16(0))
        local.appendLittleEndian(UInt16(0))
        local.appendLittleEndian(checksum)
        local.appendLittleEndian(UInt32(bytes.count))
        local.appendLittleEndian(UInt32(bytes.count))
        local.appendLittleEndian(UInt16(nameData.count))
        local.appendLittleEndian(UInt16(0))
        local.append(nameData)
        local.append(bytes)
        zip.append(local)

        central.appendLittleEndian(UInt32(0x02014B50))
        central.appendLittleEndian(UInt16(20))
        central.appendLittleEndian(UInt16(20))
        central.appendLittleEndian(flags)
        central.appendLittleEndian(UInt16(0))
        central.appendLittleEndian(UInt16(0))
        central.appendLittleEndian(UInt16(0))
        central.appendLittleEndian(checksum)
        central.appendLittleEndian(UInt32(bytes.count))
        central.appendLittleEndian(UInt32(bytes.count))
        central.appendLittleEndian(UInt16(nameData.count))
        central.appendLittleEndian(UInt16(0))
        central.appendLittleEndian(UInt16(0))
        central.appendLittleEndian(UInt16(0))
        central.appendLittleEndian(UInt16(0))
        central.appendLittleEndian(UInt32(0))
        central.appendLittleEndian(offset)
        central.append(nameData)
        offset += UInt32(local.count)
    }
    let centralOffset = zip.count
    zip.append(central)
    zip.appendLittleEndian(UInt32(0x06054B50))
    zip.appendLittleEndian(UInt16(0))
    zip.appendLittleEndian(UInt16(0))
    zip.appendLittleEndian(UInt16(files.count))
    zip.appendLittleEndian(UInt16(files.count))
    zip.appendLittleEndian(UInt32(central.count))
    zip.appendLittleEndian(UInt32(centralOffset))
    zip.appendLittleEndian(UInt16(0))
    return zip
}

private func testCRC32(_ data: Data) -> UInt32 {
    var crc: UInt32 = 0xFFFFFFFF
    for byte in data {
        crc ^= UInt32(byte)
        for _ in 0..<8 { crc = (crc >> 1) ^ (crc & 1 == 1 ? 0xEDB88320 : 0) }
    }
    return crc ^ 0xFFFFFFFF
}

private extension Data {
    mutating func appendLittleEndian<T: FixedWidthInteger>(_ value: T) {
        Swift.withUnsafeBytes(of: value.littleEndian) { append(contentsOf: $0) }
    }
}
