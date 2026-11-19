import Foundation
import Testing
@testable import BabyLog

private func recordDate(_ value: String) -> Date {
    let formatter = DateFormatter()
    formatter.calendar = Calendar(identifier: .gregorian)
    formatter.locale = Locale(identifier: "en_US_POSIX")
    formatter.timeZone = TimeZone(secondsFromGMT: 0)
    formatter.dateFormat = "yyyy-MM-dd"
    return formatter.date(from: value)!
}

struct EventRecordTest {
    private let events: [any BabyEvent] = [
        Feeding(id: 1, childId: 7, start: 1_000, end: 1_900, breast: .right, note: "ночь"),
        Feeding(id: 7, childId: 7, start: 11_000, breast: .left),
        Diaper(id: 2, childId: 7, start: 2_000, kind: .mixed),
        Sleep(id: 3, childId: 7, start: 3_000, end: 9_000, note: "дома"),
        Sleep(id: 4, childId: 7, start: 10_000),
        Medicine(id: 5, childId: 7, start: 4_000, name: "Витамин D", note: "1 капля"),
        Weight(id: 6, childId: 7, start: 5_000, grams: 4350),
        Height(id: 10, childId: 7, start: 5_500, millimeters: 625, note: "у врача"),
        Bottle(id: 8, childId: 7, start: 6_000, volumeMl: 120, milk: .formula, note: "докорм"),
        Bottle(id: 9, childId: 7, start: 7_000, volumeMl: 90),
        Crying(id: 11, childId: 7, start: 8_000, end: 8_900, reasons: ["колики", "голод"], helped: ["укачивание"], note: "вечер"),
        Crying(id: 12, childId: 7, start: 12_000),
        Crying(id: 13, childId: 7, start: 13_000, end: 13_500, helped: ["грудь"]),
        Note(id: 14, childId: 7, start: 14_000, tags: ["зубки", "питание мамы"], note: "Тёрла ушко, отказывалась от груди"),
        Note(id: 15, childId: 7, start: 15_000, note: "Первый раз перевернулась"),
        Illness(id: 16, childId: 7, start: 16_000, end: 17_000, names: [IllnessName("ОРВИ"), IllnessName("Отит", since: recordDate("2026-10-10"))], note: "садик"),
        Illness(id: 17, childId: 7, start: 18_000),
        Temperature(id: 18, childId: 7, start: 19_000, tenths: 384, method: .armpit, note: "после сна"),
        Temperature(id: 19, childId: 7, start: 20_000, tenths: 366),
    ]

    @Test func everyTypeSurvivesRecordRoundTrip() {
        for event in events {
            let restored = event.toRecord().toEvent(id: event.id, childId: event.childId)
            #expect(restored.map { AnyHashable($0) } == AnyHashable(event))
        }
    }

    @Test func codesAreCaseInsensitiveAndNormalized() {
        let event = EventRecord(type: "feeding", start: 1_000, end: nil, detail: "left", note: "").toEvent(childId: 1)
        #expect((event as? Feeding)?.breast == .left)
        #expect(event?.toRecord().detail == "LEFT")
        #expect(event?.toRecord().type == EventRecord.feeding)
    }

    @Test func endIsKeptOnlyForTimedEvents() {
        let feeding = EventRecord(type: EventRecord.feeding, start: 1_000, end: 2_000, detail: "LEFT", note: "").toEvent(childId: 1)
        #expect(feeding?.toRecord().end == 2_000)
        let diaper = EventRecord(type: EventRecord.diaper, start: 1_000, end: 2_000, detail: "WET", note: "").toEvent(childId: 1)
        #expect(diaper?.toRecord().end == nil)
    }

    @Test func invalidRecordsAreRejected() {
        let records = [
            EventRecord(type: "BADTYPE", start: 1, end: nil, detail: "X", note: ""),
            EventRecord(type: EventRecord.feeding, start: 1, end: nil, detail: "MIDDLE", note: ""),
            EventRecord(type: EventRecord.diaper, start: 1, end: nil, detail: "", note: ""),
            EventRecord(type: EventRecord.medicine, start: 1, end: nil, detail: " ", note: "без названия"),
            EventRecord(type: EventRecord.weight, start: 1, end: nil, detail: "не число", note: ""),
            EventRecord(type: EventRecord.weight, start: 1, end: nil, detail: "100", note: ""),
            EventRecord(type: EventRecord.weight, start: 1, end: nil, detail: "99999", note: ""),
            EventRecord(type: EventRecord.height, start: 1, end: nil, detail: "62.5", note: ""),
            EventRecord(type: EventRecord.height, start: 1, end: nil, detail: "62", note: ""),
            EventRecord(type: EventRecord.height, start: 1, end: nil, detail: "1501", note: ""),
        ]
        for record in records { #expect(record.toEvent(childId: 1) == nil) }
    }

    @Test func cryingDetailKeepsReasonsAndHelpedApart() {
        let crying = Crying(childId: 1, start: 0, end: 60_000, reasons: ["колики", "усталость"], helped: ["на руках"])
        #expect(crying.toRecord().detail == "колики;усталость|на руках")
        #expect(Crying(childId: 1, start: 0).toRecord().detail == "")
        let parsed = EventRecord(type: "crying", start: 0, end: 60_000, detail: " Колики ;колики;; ЗУБКИ |  белый   шум ", note: "").toEvent(childId: 1) as? Crying
        #expect(parsed?.reasons == ["колики", "зубки"])
        #expect(parsed?.helped == ["белый шум"])
        #expect(parsed?.end == 60_000)
        #expect(EventRecord(type: EventRecord.crying, start: 0, end: 60_000, detail: "а|б|в", note: "").toEvent(childId: 1) == nil)
    }

    @Test func noteDetailContainsTagsNotText() {
        let note = Note(childId: 1, start: 0, tags: ["зубки", "питание мамы"], note: "Тёрла ушко")
        #expect(note.toRecord() == EventRecord(type: "NOTE", start: 0, end: nil, detail: "зубки;питание мамы", note: "Тёрла ушко"))
        let parsed = EventRecord(type: "note", start: 0, end: 5_000, detail: " Зубки ;; ЗУБКИ ; новое ", note: "").toEvent(childId: 1) as? Note
        #expect(parsed?.tags == ["зубки", "новое"])
        let empty = EventRecord(type: "NOTE", start: 0, end: nil, detail: "", note: "").toEvent(childId: 1)
        #expect(empty.map { AnyHashable($0) } == AnyHashable(Note(childId: 1, start: 0)))
    }

    @Test func tagsAreNormalized() {
        #expect(normalizeTag("  Колики;Живот  ") == "колики живот")
        #expect(normalizeTag("а|б") == "а б")
        #expect(normalizeTag(" ; | ") == nil)
        #expect(normalizeTag(String(repeating: "о", count: 50))?.count == maxTagLength)
    }

    @Test func heightAndBottleDetailsMatchAndroid() {
        #expect(Height(childId: 1, start: 0, millimeters: 625).toRecord().detail == "625")
        let height = EventRecord(type: "height", start: 1_000, end: nil, detail: " 500 ", note: "").toEvent(childId: 1) as? Height
        #expect(height?.millimeters == 500)
        #expect(Bottle(childId: 1, start: 0, volumeMl: 120, milk: .formula).toRecord().detail == "120:FORMULA")
        #expect(Bottle(childId: 1, start: 0, volumeMl: 90).toRecord().detail == "90")
        let bottle = EventRecord(type: "bottle", start: 1_000, end: nil, detail: " 60 : breast_milk ", note: "").toEvent(childId: 1) as? Bottle
        #expect(bottle?.volumeMl == 60)
        #expect(bottle?.milk == .breastMilk)
        for detail in ["0", "501", "abc", "120:JUICE", "120:FORMULA:x", ""] {
            #expect(EventRecord(type: EventRecord.bottle, start: 1_000, end: nil, detail: detail, note: "").toEvent(childId: 1) == nil)
        }
    }

    @Test func childNamesCompareIgnoringCaseAndOuterSpaces() {
        let children = [Child(id: 1, name: "Маша", birthDate: nil), Child(id: 2, name: "Петя", birthDate: nil)]
        #expect(isNameTaken(children, name: " маша "))
        #expect(!isNameTaken(children, name: "Маша", exceptId: 1))
        #expect(!isValidChildName("   "))
        #expect(!isValidChildName(String(repeating: "x", count: Child.maxNameLength + 1)))
    }

    @Test func illnessAndTemperatureDetailsAreValidated() {
        let illness = Illness(childId: 1, start: 1_000, names: [IllnessName("ОРВИ"), IllnessName("Отит", since: recordDate("2026-10-10"))])
        #expect(illness.toRecord().detail == "ОРВИ;Отит:2026-10-10")
        let empty = EventRecord(type: EventRecord.illness, start: 1, end: nil, detail: "", note: "").toEvent(childId: 1) as? Illness
        #expect(empty?.names == [])
        let parsed = EventRecord(type: EventRecord.illness, start: 1, end: 2, detail: " орви  ;ОРВИ; ;отит :2026-10-10", note: "").toEvent(childId: 1) as? Illness
        #expect(parsed?.names == [IllnessName("орви"), IllnessName("отит", since: recordDate("2026-10-10"))])
        #expect(parsed?.end == 2)
        for detail in ["ОРВИ:вчера", "ОРВИ:2026-10-10:x"] {
            #expect(EventRecord(type: EventRecord.illness, start: 1, end: nil, detail: detail, note: "").toEvent(childId: 1) == nil)
        }
        for detail in ["38,4", "339", "431", "384:MOUTH"] {
            #expect(EventRecord(type: EventRecord.temperature, start: 1, end: nil, detail: detail, note: "").toEvent(childId: 1) == nil)
        }
        let temperature = EventRecord(type: "temperature", start: 1, end: 5, detail: "384:ear", note: "").toEvent(childId: 1) as? Temperature
        #expect(temperature?.method == .ear)
    }

    @Test func illnessNameNormalizationKeepsCase() {
        #expect(normalizeIllnessName("  ОРВИ ") == "ОРВИ")
        #expect(normalizeIllnessName("кишечная;инфекция") == "кишечная инфекция")
        #expect(normalizeIllnessName("отит:слева") == "отит слева")
        #expect(normalizeIllnessName(" ;: ") == nil)
        #expect(normalizeIllnessName(String(repeating: "а", count: 60))?.count == maxIllnessNameLength)
    }
}
