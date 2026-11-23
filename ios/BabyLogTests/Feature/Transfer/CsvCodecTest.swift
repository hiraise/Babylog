import Foundation
import Testing
@testable import BabyLog

struct CsvCodecTest {
    private func ms(_ value: String) -> Int64 { CsvCodec.parseTime(value)! }
    private var masha: Child { Child(id: 1, name: "Маша", birthDate: ms("2026-05-20 00:00:00")) }
    private var petya: Child { Child(id: 2, name: "Петя, младший", birthDate: nil) }

    /** Строки данных (без заголовка и PROFILE) → записи с id ребёнка по имени. */
    private func decode(_ csv: String, children: [Child]) throws -> [any BabyEvent] {
        try CsvCodec.parseRows(csv).dropFirst().filter { $0[0] != CsvCodec.profileRow }.map { row in
            let child = children.first { $0.name == CsvCodec.childName(row) }!
            return CsvCodec.rowToRecord(row)!.toEvent(childId: child.id)!
        }
    }

    @Test func roundTripPreservesAllFieldsAndChildren() throws {
        let events: [any BabyEvent] = [
            Feeding(childId: 1, start: ms("2026-08-15 14:30:00"), end: ms("2026-08-15 14:45:10"), breast: .left),
            Diaper(childId: 2, start: ms("2026-08-15 15:10:05"), kind: .mixed, note: "обычный"),
            Sleep(childId: 1, start: ms("2026-08-15 13:00:00"), end: ms("2026-08-15 14:20:59")),
            Medicine(childId: 2, start: ms("2026-08-15 09:00:00"), name: "Витамин D", note: "1 капля"),
            Weight(childId: 1, start: ms("2026-08-15 10:00:00"), grams: 4350, note: "после кормления"),
            Height(childId: 1, start: ms("2026-08-15 10:05:00"), millimeters: 545, note: "у врача"),
        ]
        let restored = try decode(CsvCodec.encode(children: [masha, petya], events: events), children: [masha, petya])
        #expect(restored.map { AnyHashable($0) } == events.map { AnyHashable($0) })
    }

    @Test func exportStartsWithBOMAndImportSkipsIt() throws {
        let csv = CsvCodec.encode(children: [masha], events: [])
        #expect(csv.hasPrefix("\u{FEFF}" + CsvCodec.header))
        #expect(try CsvCodec.parseRows(csv)[0] == CsvCodec.header.components(separatedBy: ","))
    }

    @Test func headerAndProfileRowsDescribeEveryChild() throws {
        let rows = try CsvCodec.parseRows(CsvCodec.encode(children: [masha, petya], events: []))
        #expect(rows[0] == CsvCodec.header.components(separatedBy: ","))
        #expect(rows[1] == ["PROFILE", "2026-05-20 00:00:00", "", "", "", "Маша", ""])
        #expect(rows[2] == ["PROFILE", "", "", "", "", "Петя, младший", ""])
    }

    @Test func profileRowKeepsSexInDetail() throws {
        var child = masha
        child.sex = .girl
        let rows = try CsvCodec.parseRows(CsvCodec.encode(children: [child], events: []))
        #expect(rows[1] == ["PROFILE", "2026-05-20 00:00:00", "", "GIRL", "", "Маша", ""])
    }

    @Test func escapingSurvivesCommasQuotesAndNewlines() throws {
        let tricky = "запятая, \"кавычки\"\nи перенос"
        let event = Medicine(childId: 2, start: ms("2026-08-15 09:00:00"), name: "Нурофен, детский", note: tricky)
        let decoded = try decode(CsvCodec.encode(children: [petya], events: [event]), children: [petya]).first as? Medicine
        #expect(decoded?.name == "Нурофен, детский")
        #expect(decoded?.note == tricky)
    }

    @Test func parseTimeAcceptsTSeparatorAndMissingSeconds() {
        #expect(ms("2026-08-15 14:30:00") == CsvCodec.parseTime("2026-08-15T14:30:00"))
        #expect(ms("2026-08-15 14:30:00") == CsvCodec.parseTime("2026-08-15 14:30"))
        #expect(CsvCodec.parseTime("не дата") == nil)
        #expect(CsvCodec.parseTime("") == nil)
    }

    @Test func rowsWithoutRequiredColumnsOrWithBadTimeAreRejected() {
        #expect(CsvCodec.rowToRecord(["FEEDING", "мусор", "", "LEFT", ""]) == nil)
        #expect(CsvCodec.rowToRecord(["SLEEP", "2026-08-15 09:00:00", "мусор", "", ""]) == nil)
        #expect(CsvCodec.rowToRecord(["FEEDING"]) == nil)
    }

    @Test func legacyFileWithoutChildColumnIsParsed() throws {
        let csv = "\u{FEFF}type,start,end,detail,note\r\nPROFILE,2026-05-20 00:00:00,,,\r\nfeeding,2026-08-15 10:00:00,,left,\r\n"
        let rows = try CsvCodec.parseRows(csv)
        #expect(rows.count == 3)
        #expect(CsvCodec.childName(rows[2]) == "")
        let event = CsvCodec.rowToRecord(rows[2])!.toEvent(childId: 1) as? Feeding
        #expect(event?.breast == .left)
    }

    @Test func quoteInTheMiddleOfAFieldIsALiteral() throws {
        let csv = "MEDICINE,2026-08-10 12:00,,Нурофен шприц 5\",температура\r\n"
            + "SLEEP,2026-08-10 13:00,2026-08-10 14:00,,\r\n"
        let rows = try CsvCodec.parseRows(csv)
        #expect(rows.count == 2)
        let record = CsvCodec.rowToRecord(rows[0])!
        #expect(record.detail == "Нурофен шприц 5\"")
        #expect(record.note == "температура")
    }

    @Test func activeSleepExportsWithEmptyEnd() {
        let csv = CsvCodec.encode(children: [masha], events: [Sleep(childId: 1, start: ms("2026-08-15 13:00:00"))])
        #expect(csv.contains("SLEEP,2026-08-15 13:00:00,,,,Маша,\r\n"))
    }

    @Test func photosColumnListsArchiveFilesOfChildAndEachRecord() throws {
        let diaper = Diaper(id: 7, childId: 1, start: ms("2026-08-15 15:10:00"), kind: .wet)
        let csv = CsvCodec.encode(
            children: [masha],
            events: [diaper],
            childPhotos: [1: "photos/child_1.jpg"],
            eventPhotos: [7: ["photos/a.jpg", "photos/b.jpg"]]
        )
        let rows = try CsvCodec.parseRows(csv)
        #expect(CsvCodec.photoNames(rows[1]) == ["photos/child_1.jpg"])
        #expect(CsvCodec.photoNames(rows[2]) == ["photos/a.jpg", "photos/b.jpg"])
        #expect(csv.contains("DIAPER,2026-08-15 15:10:00,,WET,,Маша,photos/a.jpg;photos/b.jpg\r\n"))
    }

    @Test func rowsOfEarlierVersionsHaveNoPhotos() throws {
        let rows = try CsvCodec.parseRows("type,start,end,detail,note,child\r\nDIAPER,2026-08-15 15:10:00,,WET,,Маша\r\n")
        #expect(CsvCodec.photoNames(rows[1]) == [])
        #expect(CsvCodec.photoNames(["DIAPER", "", "", "", "", "Маша", " ; "]) == [])
    }
}
