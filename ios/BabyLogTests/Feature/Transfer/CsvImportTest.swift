import Foundation
import Testing
@testable import BabyLog

struct CsvImportTest {
    private func ms(_ value: String) -> Int64 { CsvCodec.parseTime(value)! }

    /** CSV всего дневника — как `diary.csv` в архиве экспорта, но без фото. */
    private func csvOf(_ repository: FakeDiaryRepository) async throws -> String {
        CsvCodec.encode(children: try await repository.allChildren(), events: try await repository.allEvents())
    }

    private var legacyFile: String {
        "\u{FEFF}type,start,end,detail,note\r\n"
            + "PROFILE,2026-05-20 00:00:00,,,\r\n"
            + "FEEDING,2026-08-15 10:00:00,,LEFT,\r\n"
            + "SLEEP,2026-08-15 11:00:00,2026-08-15 12:00:00,,\r\n"
    }

    @Test func legacyFileGoesToDefaultChild() async throws {
        let repository = FakeDiaryRepository(children: [
            Child(id: 1, name: "Маша", birthDate: nil),
            Child(id: 2, name: "Петя", birthDate: nil),
        ])
        let result = try await importCsv(repository: repository, text: legacyFile, defaultChildId: 2)
        #expect(result.added == 2)
        #expect(result.birthDatesSet == 1)
        #expect(result.childrenAdded == 0)
        #expect(Set(repository.events.map(\.childId)) == [2])
        #expect(repository.children.first { $0.id == 2 }?.birthDate == ms("2026-05-20 00:00:00"))
        #expect(repository.children.first { $0.id == 1 }?.birthDate == nil)
    }

    @Test func childColumnMatchesNamesIgnoringCaseAndCreatesChildren() async throws {
        let repository = FakeDiaryRepository(children: [Child(id: 1, name: "Маша", birthDate: nil)])
        let csv = "type,start,end,detail,note,child\r\n"
            + "PROFILE,,,,,Петя\r\n"
            + "DIAPER,2026-08-15 10:00:00,,WET,, маша \r\n"
            + "DIAPER,2026-08-15 10:05:00,,DIRTY,,Петя\r\n"
            + "DIAPER,2026-08-15 10:10:00,,MIXED,,Вася\r\n"
        let result = try await importCsv(repository: repository, text: csv, defaultChildId: 1)
        #expect(result.added == 3)
        #expect(result.childrenAdded == 2)
        #expect(result.errors == 0)
        #expect(repository.children.map(\.name) == ["Маша", "Петя", "Вася"])
        #expect(repository.children.first { $0.name == "Петя" }?.birthDate == nil)
        let kinds = repository.events.compactMap { $0 as? Diaper }.map(\.kind)
        let names = repository.events.map { event in repository.children.first { $0.id == event.childId }!.name }
        #expect(kinds == [.wet, .dirty, .mixed])
        #expect(names == ["Маша", "Петя", "Вася"])
    }

    @Test func reimportingOwnExportChangesNothing() async throws {
        let repository = FakeDiaryRepository(children: [
            Child(id: 1, name: "Маша", birthDate: ms("2026-05-20 00:00:00")),
            Child(id: 2, name: "Петя", birthDate: nil),
        ])
        let start = ms("2026-08-15 10:00:00")
        _ = try await repository.addEvent(Feeding(childId: 1, start: start, end: start + 600_000, breast: .left, note: "a, \"b\""))
        _ = try await repository.addEvent(Feeding(childId: 2, start: start, end: start, breast: .left, note: "a, \"b\""))
        _ = try await repository.addEvent(Sleep(childId: 2, start: ms("2026-08-15 11:00:00"), end: ms("2026-08-15 12:30:00")))
        _ = try await repository.addEvent(Feeding(childId: 1, start: ms("2026-08-15 13:00:00"), breast: .right))
        let result = try await importCsv(repository: repository, text: csvOf(repository), defaultChildId: 1)
        #expect(result == ImportResult(duplicates: 4))
        #expect(repository.events.count == 4)
    }

    private struct Snapshot: Hashable {
        let name: String
        let birthDate: Int64?
        let record: EventRecord
    }

    private func snapshot(_ repository: FakeDiaryRepository) -> Set<Snapshot> {
        Set(repository.events.map { event in
            let child = repository.children.first { $0.id == event.childId }!
            return Snapshot(name: child.name, birthDate: child.birthDate, record: event.toRecord())
        })
    }

    @Test func exportImportsIntoAnotherPhoneWithSameChildrenAndRecords() async throws {
        let source = FakeDiaryRepository(children: [
            Child(id: 1, name: "Маша", birthDate: ms("2026-05-20 00:00:00")),
            Child(id: 2, name: "Петя", birthDate: nil),
        ])
        _ = try await source.addEvent(Diaper(childId: 2, start: ms("2026-08-16 08:00:00"), kind: .wet))
        _ = try await source.addEvent(Feeding(childId: 1, start: ms("2026-08-15 10:00:00"), end: ms("2026-08-15 10:20:00"), breast: .right))
        let target = FakeDiaryRepository()
        _ = try await importCsv(repository: target, text: csvOf(source), defaultChildId: 1)
        #expect(snapshot(source) == snapshot(target))
    }

    @Test func feedingWithoutEndFromOldFileHasNoDurationAndNoTimer() async throws {
        let repository = FakeDiaryRepository()
        _ = try await importCsv(repository: repository, text: legacyFile, defaultChildId: 1)
        let feeding = repository.events.compactMap { $0 as? Feeding }.first
        #expect(feeding?.end == ms("2026-08-15 10:00:00"))
        #expect(feeding?.end == feeding?.start)
        let again = try await importCsv(repository: repository, text: legacyFile, defaultChildId: 1)
        #expect(again.duplicates == 2)
    }

    @Test func bottleRowsAreImportedWithVolumeAndMilk() async throws {
        let repository = FakeDiaryRepository()
        let csv = "type,start,end,detail,note,child\n"
            + "BOTTLE,2026-08-15 10:00:00,,120:FORMULA,докорм,Малыш\n"
            + "BOTTLE,2026-08-15 13:00:00,,90,,Малыш\n"
        let result = try await importCsv(repository: repository, text: csv, defaultChildId: 1)
        #expect(result.added == 2)
        let bottles = repository.events.compactMap { $0 as? Bottle }
        #expect(bottles.map(\.volumeMl) == [120, 90])
        #expect(bottles.map(\.milk) == [.formula, nil])
    }

    @Test func cryingRowsKeepReasonsAndHelpedAndRunningCryingIsSkipped() async throws {
        let repository = FakeDiaryRepository()
        let csv = "type,start,end,detail,note,child\n"
            + "CRYING,2026-08-15 18:30:00,2026-08-15 18:55:00,колики;голод|укачивание,вечер,Малыш\n"
            + "CRYING,2026-08-15 21:00:00,,,,Малыш\n"
        let result = try await importCsv(repository: repository, text: csv, defaultChildId: 1)
        #expect(result.added == 1)
        #expect(result.skippedActiveCrying == 1)
        let crying = repository.events.compactMap { $0 as? Crying }.first
        #expect(crying?.reasons == ["колики", "голод"])
        #expect(crying?.helped == ["укачивание"])
        #expect(crying?.end == ms("2026-08-15 18:55:00"))
    }

    @Test func noteRowsKeepTagsAndTextWithCommas() async throws {
        let repository = FakeDiaryRepository()
        let csv = "type,start,end,detail,note,child\n"
            + "NOTE,2026-08-15 21:50:00,,питание мамы;колики,\"Днём ела капусту, проверить\",Малыш\n"
        let result = try await importCsv(repository: repository, text: csv, defaultChildId: 1)
        #expect(result.added == 1)
        let note = repository.events.compactMap { $0 as? Note }.first
        #expect(note?.tags == ["питание мамы", "колики"])
        #expect(note?.note == "Днём ела капусту, проверить")
        #expect(note?.start == ms("2026-08-15 21:50:00"))
    }

    @Test func duplicatesInsideOneFileAreCountedOnce() async throws {
        let repository = FakeDiaryRepository()
        let row = "FEEDING,2026-08-15 10:00:00,,LEFT,\r\n"
        let result = try await importCsv(repository: repository, text: row + row, defaultChildId: 1)
        #expect(result.added == 1)
        #expect(result.duplicates == 1)
    }

    @Test func sameRecordForDifferentChildrenIsNotDuplicate() async throws {
        let repository = FakeDiaryRepository(children: [
            Child(id: 1, name: "Маша", birthDate: nil),
            Child(id: 2, name: "Петя", birthDate: nil),
        ])
        let csv = "FEEDING,2026-08-15 10:00:00,,LEFT,,Маша\r\nFEEDING,2026-08-15 10:00:00,,LEFT,,Петя\r\n"
        let result = try await importCsv(repository: repository, text: csv, defaultChildId: 1)
        #expect(result.added == 2)
    }

    @Test func activeSleepAndInvalidRowsAreSkippedWithoutCreatingChildren() async throws {
        let repository = FakeDiaryRepository()
        let csv = "SLEEP,2026-08-15 13:00:00,,,,Вася\r\n"
            + "UNKNOWN,2026-08-15 13:00:00,,,,Вася\r\n"
            + "FEEDING,мусор,,LEFT,,Вася\r\n"
            + "PROFILE,мусор,,,,Вася\r\n"
            + "PROFILE,,,,\r\n"
        let result = try await importCsv(repository: repository, text: csv, defaultChildId: 1)
        #expect(result.skippedActiveSleep == 1)
        #expect(result.errors == 4)
        #expect(result.added == 0)
        #expect(repository.children.map(\.name) == [Child.defaultName])
    }

    @Test func sexFromFileIsSetOnlyWhenMissingAndOldFilesLeaveItAlone() async throws {
        let repository = FakeDiaryRepository(children: [
            Child(id: 1, name: "Маша", birthDate: nil),
            Child(id: 2, name: "Петя", birthDate: nil, sex: .boy),
        ])
        let csv = "PROFILE,,,GIRL,,Маша\r\nPROFILE,,,girl,,Петя\r\nPROFILE,,,BOY,,Вася\r\n"
        let result = try await importCsv(repository: repository, text: csv, defaultChildId: 1)
        #expect(result.sexesSet == 1)
        #expect(result.sexConflicts == 1)
        #expect(result.childrenAdded == 1)
        #expect(repository.children.map(\.sex) == [.girl, .boy, .boy])
        _ = try await importCsv(repository: repository, text: legacyFile, defaultChildId: 1)
        #expect(repository.children.first?.sex == .girl)
    }

    @Test func unknownSexCodeIsErrorAndCreatesNoChild() async throws {
        let repository = FakeDiaryRepository()
        let result = try await importCsv(repository: repository, text: "PROFILE,,,MAYBE,,Вася\r\n", defaultChildId: 1)
        #expect(result.errors == 1)
        #expect(repository.children.map(\.name) == [Child.defaultName])
    }

    @Test func sexSurvivesExportToAnotherPhone() async throws {
        let source = FakeDiaryRepository(children: [Child(id: 1, name: "Маша", birthDate: nil, sex: .girl)])
        let target = FakeDiaryRepository()
        _ = try await importCsv(repository: target, text: csvOf(source), defaultChildId: 1)
        #expect(target.children.first { $0.name == "Маша" }?.sex == .girl)
    }

    @Test func differentBirthDateDoesNotOverwriteCurrentOne() async throws {
        let repository = FakeDiaryRepository(children: [Child(id: 1, name: "Маша", birthDate: ms("2026-05-21 00:00:00"))])
        let result = try await importCsv(repository: repository, text: "PROFILE,2026-05-20 00:00:00,,,,Маша\r\n", defaultChildId: 1)
        #expect(result.birthDateConflicts == 1)
        #expect(repository.children.first?.birthDate == ms("2026-05-21 00:00:00"))
    }

    @Test func unclosedQuoteStopsWholeImportAndNamesItsLine() async throws {
        let repository = FakeDiaryRepository(children: [Child(id: 1, name: "Маша", birthDate: nil)])
        let file = "type,start,end,detail,note,child\r\n"
            + "DIAPER,2026-08-15 15:10:00,,WET,,Маша\r\n"
            + "DIAPER,2026-08-15 16:10:00,,WET,\"кавычку забыли закрыть,Маша\r\n"
            + "DIAPER,2026-08-15 17:10:00,,WET,,Маша\r\n"
        do {
            _ = try await importCsv(repository: repository, text: file, defaultChildId: 1)
            Issue.record("Импорт с незакрытой кавычкой должен упасть")
        } catch let error as TransferError {
            #expect(error.message == "в строке 3 кавычка не закрыта — исправьте файл")
        }
        #expect(repository.events.isEmpty)
    }

    @Test func fieldLongerThanAnyRecordIsErrorNotGiantNote() async throws {
        let repository = FakeDiaryRepository(children: [Child(id: 1, name: "Маша", birthDate: nil)])
        let file = "NOTE,2026-08-15 21:50:00,,," + String(repeating: "а", count: CsvCodec.maxFieldLength + 1)
            + ",Маша\r\nDIAPER,2026-08-15 15:10:00,,WET,,Маша\r\n"
        let result = try await importCsv(repository: repository, text: file, defaultChildId: 1)
        #expect(result.errors == 1)
        #expect(result.added == 1)
        #expect(repository.events.first is Diaper)
    }

    @Test func illnessAndTemperatureRowsKeepNamesDaysAndMethod() async throws {
        let repository = FakeDiaryRepository()
        let csv = "type,start,end,detail,note,child\n"
            + "ILLNESS,2026-10-07 19:30:00,2026-10-14 20:00:00,ОРВИ;Отит:2026-10-10,из сада,Малыш\n"
            + "TEMPERATURE,2026-10-10 03:10:00,,392:ARMPIT,,Малыш\n"
            + "TEMPERATURE,2026-10-10 07:30,,381,,Малыш\n"
        let result = try await importCsv(repository: repository, text: csv, defaultChildId: 1)
        #expect(result.added == 3)
        let illness = repository.events.compactMap { $0 as? Illness }.first
        #expect(illness?.names.map(\.name) == ["ОРВИ", "Отит"])
        #expect(illness?.names[1].since == importDate("2026-10-10"))
        #expect(illness?.end == ms("2026-10-14 20:00:00"))
        #expect(illness?.note == "из сада")
        let temperatures = repository.events.compactMap { $0 as? Temperature }.sorted { $0.start < $1.start }
        #expect(temperatures.map(\.tenths) == [392, 381])
        #expect(temperatures.map(\.method) == [.armpit, nil])
    }

    @Test func goingIllnessMovesToNewPhoneButNotAsSecondGoingOne() async throws {
        let going = "type,start,end,detail,note,child\nILLNESS,2026-10-18 08:00:00,,,,Малыш\n"
        let repository = FakeDiaryRepository()
        let first = try await importCsv(repository: repository, text: going, defaultChildId: 1)
        let duplicate = try await importCsv(repository: repository, text: going, defaultChildId: 1)
        #expect(first.added == 1)
        #expect(duplicate.duplicates == 1)
        let other = "type,start,end,detail,note,child\nILLNESS,2026-10-19 08:00:00,,ОРВИ,,Малыш\n"
        let result = try await importCsv(repository: repository, text: other, defaultChildId: 1)
        #expect(result.added == 0)
        #expect(result.skippedActiveIllness == 1)
        #expect(repository.events.compactMap { $0 as? Illness }.count == 1)
    }
}

private func importDate(_ value: String) -> Date {
    let formatter = DateFormatter()
    formatter.calendar = Calendar(identifier: .gregorian)
    formatter.locale = Locale(identifier: "en_US_POSIX")
    formatter.timeZone = TimeZone(secondsFromGMT: 0)
    formatter.dateFormat = "yyyy-MM-dd"
    return formatter.date(from: value)!
}
