import Foundation
import Testing
@testable import BabyLog

private let illnessZone = TimeZone(secondsFromGMT: 0)!
private let illnessHour: Int64 = 3_600_000
private let illnessDay = 24 * illnessHour

private func illnessAt(_ iso: String) -> Int64 {
    Int64(ISO8601DateFormatter().date(from: iso)!.timeIntervalSince1970 * 1000)
}

private func illnessLocalDate(_ value: String) -> Date {
    let formatter = DateFormatter()
    formatter.calendar = Calendar(identifier: .gregorian)
    formatter.locale = Locale(identifier: "en_US_POSIX")
    formatter.timeZone = illnessZone
    formatter.dateFormat = "yyyy-MM-dd"
    return formatter.date(from: value)!
}

struct IllnessesTest {
    private let start = illnessAt("2026-10-07T19:30:00Z")
    private let end = illnessAt("2026-10-14T20:00:00Z")
    private var illness: Illness {
        Illness(id: 1, childId: 1, start: start, end: end, names: [IllnessName("ОРВИ"), IllnessName("Отит", since: illnessLocalDate("2026-10-10"))])
    }

    @Test func recordsInsideIllnessBelongToItByTime() {
        let events: [any BabyEvent] = [
            illness,
            Temperature(id: 2, childId: 1, start: start, tenths: 384),
            Note(id: 3, childId: 1, start: illnessAt("2026-10-10T11:30:00Z"), tags: ["врач"], note: "Отит слева"),
            Medicine(id: 4, childId: 1, start: illnessAt("2026-10-08T07:10:00Z"), name: "Ибупрофен"),
            Temperature(id: 5, childId: 1, start: start - 1, tenths: 369),
            Temperature(id: 6, childId: 1, start: end + illnessHour, tenths: 368),
            Temperature(id: 7, childId: 2, start: start + illnessHour, tenths: 390),
            Sleep(id: 8, childId: 1, start: start + illnessHour, end: start + 2 * illnessHour),
        ]
        #expect(events.illnessRecords(illness, now: end + illnessDay).map(\.id) == [2, 4, 3])
    }

    @Test func goingIllnessCollectsRecordsUpToNow() {
        var going = illness
        going.end = nil
        let events: [any BabyEvent] = [going, Temperature(id: 2, childId: 1, start: end + illnessDay, tenths: 380)]
        #expect(events.illnessRecords(going, now: end + 2 * illnessDay).map(\.id) == [2])
        #expect(events.illnessRecords(going, now: end).map(\.id) == [])
    }

    @Test func courseStartedInIllnessStaysAfterEnd() {
        let antibiotics = (0..<13).map { index in
            Medicine(id: Int64(10 + index), childId: 1, start: illnessAt("2026-10-10T12:00:00Z") + Int64(index) * 12 * illnessHour, name: "Амоксициллин") as any BabyEvent
        }
        let events: [any BabyEvent] = [illness] + antibiotics + [
            Medicine(id: 30, childId: 1, start: illnessAt("2026-10-23T09:00:00Z"), name: "амоксициллин"),
            Medicine(id: 40, childId: 1, start: illnessAt("2026-10-01T09:00:00Z"), name: "Витамин D"),
            Medicine(id: 41, childId: 1, start: illnessAt("2026-10-12T09:00:00Z"), name: "Витамин D"),
            Medicine(id: 42, childId: 1, start: illnessAt("2026-10-15T09:00:00Z"), name: "Витамин D"),
        ]
        let medicines = events.illnessRecords(illness, now: illnessAt("2026-10-30T00:00:00Z")).compactMap { $0 as? Medicine }
        let lastAfterEnd = medicines.filter { $0.start > end }.map(\.start).suffix(1)
        #expect(lastAfterEnd.map { ISO8601DateFormatter().string(from: Date(timeIntervalSince1970: Double($0) / 1000)) } == ["2026-10-16T12:00:00Z"])
        #expect(medicines.filter { $0.name == "Амоксициллин" }.count == 13)
        #expect(medicines.filter { $0.name == "Витамин D" }.map(\.id) == [41])
    }

    @Test func ongoingCoursesNameRecentMedicinesStartedInIllness() {
        var going = illness
        going.end = nil
        let events: [any BabyEvent] = [
            going,
            Medicine(id: 2, childId: 1, start: illnessAt("2026-10-14T12:00:00Z"), name: "Амоксициллин"),
            Medicine(id: 3, childId: 1, start: illnessAt("2026-10-08T07:00:00Z"), name: "Ибупрофен"),
            Medicine(id: 4, childId: 1, start: illnessAt("2026-10-01T09:00:00Z"), name: "Витамин D"),
            Medicine(id: 5, childId: 1, start: illnessAt("2026-10-14T09:00:00Z"), name: "Витамин D"),
        ]
        #expect(events.ongoingCourses(going, now: illnessAt("2026-10-14T21:00:00Z")) == ["Амоксициллин"])
    }

    @Test func illnessMedicinesCountDosesAndKeepLastDoseNote() {
        let events: [any BabyEvent] = [
            illness,
            Medicine(id: 2, childId: 1, start: illnessAt("2026-10-08T07:10:00Z"), name: "ибупрофен", note: "4 мл"),
            Medicine(id: 3, childId: 1, start: illnessAt("2026-10-10T03:15:00Z"), name: "Ибупрофен"),
            Medicine(id: 4, childId: 1, start: illnessAt("2026-10-09T15:30:00Z"), name: "Парацетамол", note: "5 мл"),
        ]
        let medicines = events.illnessMedicines(illness, now: end)
        #expect(medicines.map(\.name) == ["Ибупрофен", "Парацетамол"])
        #expect(medicines.first?.count == 2)
        #expect(medicines.first?.note == "4 мл")
        #expect(medicines.first?.last == illnessAt("2026-10-10T03:15:00Z"))
    }

    @Test func activeAndRecentIllness() {
        var going = illness
        going.id = 9
        going.start = end + 4 * illnessDay
        going.end = nil
        let events: [any BabyEvent] = [illness, going]
        #expect(events.activeIllness() == going)
        let finished: [any BabyEvent] = [illness]
        #expect(finished.activeIllness() == nil)
        #expect(finished.recentIllness(now: end + 4 * illnessDay) == illness)
        #expect(finished.recentIllness(now: end + 8 * illnessDay) == nil)
        #expect(events.recentIllness(now: end + 4 * illnessDay) == nil)
    }

    @Test func daysAndNamesUseCalendarDays() {
        #expect(illness.startDate(illnessZone) == illnessLocalDate("2026-10-07"))
        #expect(illness.dayNumber(illnessLocalDate("2026-10-10"), timeZone: illnessZone) == 4)
        #expect(illness.days(today: illnessLocalDate("2026-10-20"), timeZone: illnessZone) == 8)
        var going = illness
        going.end = nil
        #expect(going.days(today: illnessLocalDate("2026-10-09"), timeZone: illnessZone) == 3)
        #expect(illness.names[0].sinceDate(illness, timeZone: illnessZone) == illnessLocalDate("2026-10-07"))
        #expect(illness.title() == "ОРВИ, Отит")
        let events: [any BabyEvent] = [illness]
        let dates = events.illnessDates(today: illnessLocalDate("2026-10-20"), timeZone: illnessZone)
        #expect(dates.count == 8)
        #expect(dates.max() == illnessLocalDate("2026-10-14"))
    }

    @Test func nameChoicesPutPastNamesFirstWithoutCaseDuplicates() {
        let events: [any BabyEvent] = [
            illness,
            Illness(id: 2, childId: 1, start: start - 30 * illnessDay, end: start - 25 * illnessDay, names: [IllnessName("орви")]),
            Illness(id: 3, childId: 1, start: start - 60 * illnessDay, end: start - 55 * illnessDay, names: [IllnessName("Дерматит")]),
        ]
        let choices = events.illnessNameChoices()
        #expect(Array(choices.prefix(3)) == ["ОРВИ", "Дерматит", "Отит"])
        #expect(choices.filter { $0.lowercased() == "орви" }.count == 1)
        let empty: [any BabyEvent] = []
        #expect(empty.illnessNameChoices() == defaultIllnessNames)
    }
}
