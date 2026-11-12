import Foundation
import Testing
import ZIPFoundation
@testable import BabyLog

private let exportFixtureZone = TimeZone(identifier: "Europe/Moscow")!

struct ExportFixturesTest {
    private let directory = URL(fileURLWithPath: #filePath)
        .deletingLastPathComponent()
        .deletingLastPathComponent()
        .deletingLastPathComponent()
        .deletingLastPathComponent()
        .deletingLastPathComponent()
        .appendingPathComponent("fixtures/export", isDirectory: true)

    @Test func sharedCSVAndArchiveImportSameRecords() async throws {
        let csvURL = directory.appendingPathComponent("sample.csv")
        let zipURL = directory.appendingPathComponent("sample.zip")
        let csvStore = FakeDocumentStore(files: [csvURL: try Data(contentsOf: csvURL)])
        let zipStore = FakeDocumentStore(files: [zipURL: try Data(contentsOf: zipURL)])
        let fromCsv = FakeDiaryRepository()
        let fromZip = FakeDiaryRepository()
        let csvResult = try await importFile(repository: fromCsv, documents: csvStore, url: csvURL, defaultChildId: 1)
        let zipResult = try await importFile(repository: fromZip, documents: zipStore, url: zipURL, defaultChildId: 1)

        #expect(csvResult.added == 11)
        #expect(zipResult.added == 11)
        #expect(csvResult.photosSkipped == 4)
        #expect(zipResult.photosAdded == 3)
        #expect(zipResult.childPhotosSet == 1)
        #expect(zipResult.photosSkipped == 0)
        #expect(fixtureSnapshot(fromCsv) == fixtureSnapshot(fromZip))
        #expect(fromZip.children.first { $0.name == "Маша" }?.photo?.jpeg == testJpeg(1))
        let diaper = fromZip.events.first { $0 is Diaper }!
        #expect(fromZip.photos[diaper.id]?.map(\.jpeg) == [testJpeg(2), testJpeg(3)])
    }

    @Test func exportMatchesSharedFixturesEntryByEntry() async throws {
        let output = URL(fileURLWithPath: "/fixture-export.zip")
        let store = FakeDocumentStore()
        let export = try await store.write(output) { url in
            try await exportArchive(repository: fixtureDiary(), output: url, zone: exportFixtureZone)
        }
        #expect(export == DiaryExport(events: 11, photos: 4))

        let expectedBytes = try Data(contentsOf: directory.appendingPathComponent("sample.zip"))
        let expected = try archiveEntries(expectedBytes)
        let actual = try archiveEntries(store.files[output]!)
        #expect(actual.map(\.0) == expected.map(\.0))
        #expect(actual.map(\.1) == expected.map(\.1))
        #expect(try actual[0].1 == Data(contentsOf: directory.appendingPathComponent("sample.csv")))
        #expect(try archiveCompression(store.files[output]!) == archiveCompression(expectedBytes))
    }

    private func fixtureDiary() async throws -> FakeDiaryRepository {
        let repository = FakeDiaryRepository(children: [
            Child(
                id: 1,
                name: "Маша",
                birthDate: fixtureMs("2026-05-20 00:00:00"),
                sex: .girl,
                photo: ChildPhoto(jpeg: testJpeg(1))
            ),
            Child(id: 2, name: "Петя, младший", birthDate: nil),
        ])
        _ = try await repository.addEvent(Medicine(childId: 2, start: fixtureMs("2026-08-15 09:00:00"), name: "Витамин D", note: "1 капля"))
        _ = try await repository.addEvent(Weight(childId: 1, start: fixtureMs("2026-08-15 10:00:00"), grams: 4350, note: "после кормления"))
        _ = try await repository.addEvent(Height(childId: 1, start: fixtureMs("2026-08-15 10:05:00"), millimeters: 545, note: "у врача"))
        _ = try await repository.addEvent(Sleep(childId: 1, start: fixtureMs("2026-08-15 13:00:00"), end: fixtureMs("2026-08-15 14:00:00")))
        _ = try await repository.addEvent(Feeding(
            childId: 1,
            start: fixtureMs("2026-08-15 14:30:00"),
            end: fixtureMs("2026-08-15 14:45:00"),
            breast: .left,
            note: "a, \"b\""
        ))
        let diaper = try await repository.addEvent(Diaper(childId: 2, start: fixtureMs("2026-08-15 15:10:00"), kind: .dirty))
        try await repository.setEventPhotos(eventId: diaper, edit: PhotoEdit(keep: [], add: [testJpeg(2), testJpeg(3)]))
        let note = try await repository.addEvent(Note(
            childId: 2,
            start: fixtureMs("2026-08-15 15:10:00"),
            tags: ["питание мамы", "колики"],
            note: "Днём ела капусту, проверить"
        ))
        try await repository.setEventPhotos(eventId: note, edit: PhotoEdit(keep: [], add: [testJpeg(4)]))
        _ = try await repository.addEvent(Bottle(childId: 1, start: fixtureMs("2026-08-15 17:00:00"), volumeMl: 120, milk: .formula))
        _ = try await repository.addEvent(Crying(
            childId: 1,
            start: fixtureMs("2026-08-15 18:32:00"),
            end: fixtureMs("2026-08-15 18:56:00"),
            reasons: ["колики", "усталость"],
            helped: ["укачивание"]
        ))
        _ = try await repository.addEvent(Illness(
            childId: 1,
            start: fixtureMs("2026-10-07 19:30:00"),
            end: fixtureMs("2026-10-14 20:00:00"),
            names: [IllnessName("ОРВИ"), IllnessName("Отит", since: fixtureDate("2026-10-10"))]
        ))
        _ = try await repository.addEvent(Temperature(
            childId: 1,
            start: fixtureMs("2026-10-10 03:10:00"),
            tenths: 392,
            method: .armpit
        ))
        return repository
    }
}

private struct FixtureSnapshot: Equatable {
    let children: [Child]
    let records: [EventRecord]

    static func == (lhs: Self, rhs: Self) -> Bool {
        lhs.children.map { $0.name } == rhs.children.map { $0.name }
            && lhs.children.map { $0.birthDate } == rhs.children.map { $0.birthDate }
            && lhs.children.map { $0.sex } == rhs.children.map { $0.sex }
            && lhs.records == rhs.records
    }
}

private func fixtureSnapshot(_ repository: FakeDiaryRepository) -> FixtureSnapshot {
    FixtureSnapshot(children: repository.children, records: repository.events.map { $0.toRecord() })
}

private func fixtureMs(_ value: String) -> Int64 {
    CsvCodec.parseTime(value, zone: exportFixtureZone)!
}

private func fixtureDate(_ value: String) -> Date {
    let formatter = DateFormatter()
    formatter.calendar = Calendar(identifier: .gregorian)
    formatter.locale = Locale(identifier: "en_US_POSIX")
    formatter.timeZone = TimeZone(secondsFromGMT: 0)
    formatter.dateFormat = "yyyy-MM-dd"
    return formatter.date(from: value)!
}

private func archiveCompression(_ data: Data) throws -> [Bool] {
    try Archive(data: data, accessMode: .read).map(\.isCompressed)
}
