import Foundation
import Testing
@testable import BabyLog

private let illnessModelZone = TimeZone(secondsFromGMT: 0)!
private let illnessModelHour: Int64 = 3_600_000

private func illnessModelAt(_ iso: String) -> Int64 {
    Int64(ISO8601DateFormatter().date(from: iso)!.timeIntervalSince1970 * 1000)
}

private func illnessModelDay(_ value: String) -> Date {
    let formatter = DateFormatter()
    formatter.calendar = Calendar(identifier: .gregorian)
    formatter.locale = Locale(identifier: "en_US_POSIX")
    formatter.timeZone = illnessModelZone
    formatter.dateFormat = "yyyy-MM-dd"
    return formatter.date(from: value)!
}

/** Карточка, экран и «Для врача» болезни (ADR-0040) на истории Маши: ОРВИ с 7 октября, отит на 4-й день. */
struct IllnessModelTest {
    private let illness = Illness(
        id: 1,
        childId: 1,
        start: illnessModelAt("2026-10-07T19:30:00Z"),
        names: [IllnessName("ОРВИ"), IllnessName("Отит", since: illnessModelDay("2026-10-10"))]
    )
    private let now = illnessModelAt("2026-10-10T12:30:00Z")

    /** Две недели до болезни — режим: 8 кормлений, 7 мокрых подгузников, сон 12 ч; 9 октября — болеет. */
    private func events() -> [any BabyEvent] {
        var result: [any BabyEvent] = [illness]
        var id: Int64 = 100
        func nextId() -> Int64 {
            defer { id += 1 }
            return id
        }
        for back in 1...14 {
            let day = illnessModelAt("2026-10-07T00:00:00Z") - Int64(back) * 24 * illnessModelHour
            for index in 0..<8 {
                result.append(Feeding(id: nextId(), childId: 1, start: day + Int64(index) * 3 * illnessModelHour, end: day + Int64(index) * 3 * illnessModelHour + 15 * 60_000, breast: .left))
            }
            for index in 0..<7 {
                result.append(Diaper(id: nextId(), childId: 1, start: day + Int64(index) * 3 * illnessModelHour + illnessModelHour, kind: .wet))
            }
            result.append(Sleep(id: nextId(), childId: 1, start: day + 12 * illnessModelHour, end: day + 24 * illnessModelHour))
        }
        let sick = illnessModelAt("2026-10-09T00:00:00Z")
        for index in 0..<11 {
            result.append(Feeding(id: nextId(), childId: 1, start: sick + Int64(index) * 2 * illnessModelHour, end: sick + Int64(index) * 2 * illnessModelHour + 10 * 60_000, breast: .right))
        }
        for index in 0..<4 {
            result.append(Diaper(id: nextId(), childId: 1, start: sick + Int64(index) * 4 * illnessModelHour, kind: .wet))
        }
        result.append(Diaper(id: nextId(), childId: 1, start: sick + 20 * illnessModelHour, kind: .mixed))
        result.append(Sleep(id: nextId(), childId: 1, start: sick + 13 * illnessModelHour, end: sick + 13 * illnessModelHour + 650 * 60_000))
        result.append(Temperature(id: 2, childId: 1, start: illnessModelAt("2026-10-07T19:30:00Z"), tenths: 384, method: .armpit))
        result.append(Temperature(id: 3, childId: 1, start: illnessModelAt("2026-10-10T03:10:00Z"), tenths: 392))
        result.append(Temperature(id: 4, childId: 1, start: illnessModelAt("2026-10-10T10:40:00Z"), tenths: 388))
        result.append(Medicine(id: 5, childId: 1, start: illnessModelAt("2026-10-10T03:15:00Z"), name: "Ибупрофен", note: "4 мл"))
        result.append(Medicine(id: 6, childId: 1, start: illnessModelAt("2026-10-10T12:00:00Z"), name: "Амоксициллин", note: "5 мл"))
        result.append(Note(id: 7, childId: 1, start: illnessModelAt("2026-10-10T11:30:00Z"), tags: ["врач"], note: "Отит слева"))
        return result
    }

    @Test func cardShowsDayNamesAndLatestTemperatureAndMedicine() {
        let card = illnessCardModel(events: events(), illness: illness, now: now, timeZone: illnessModelZone)
        #expect(card.day == 4)
        #expect(card.names == "ОРВИ, Отит")
        #expect(card.lastTemperature?.tenths == 388)
        #expect(card.lastMedicine?.name == "Амоксициллин")
    }

    @Test func screenListsDaysNewestFirstWithNamesAndStartInTheirDays() {
        let model = illnessScreenModel(events: events(), illness: illness, now: now, timeZone: illnessModelZone)
        #expect(model.days == 4)
        #expect(model.byDay.map(\.number) == [4, 3, 2, 1])
        let today = model.byDay.first!.entries
        #expect(today.compactMap { entry -> Int64? in
            if case let .record(event) = entry { event.id } else { nil }
        } == [6, 7, 4, 5, 3])
        guard case .nameAdded = today.last else {
            Issue.record("В конце сегодняшнего дня должно быть новое название")
            return
        }
        guard case .started = model.byDay.last!.entries.last else {
            Issue.record("В конце первого дня должно быть начало болезни")
            return
        }
        #expect(model.temperatures.count == 3)
    }

    @Test func yesterdayIsComparedWithUsualTwoWeeksBefore() throws {
        let diary = try #require(illnessScreenModel(events: events(), illness: illness, now: now, timeZone: illnessModelZone).diary)
        #expect(diary.yesterday)
        let lines = Dictionary(uniqueKeysWithValues: diary.lines.map { ($0.kind, $0) })
        #expect(lines[.wetDiapers]?.value == 5)
        #expect(lines[.wetDiapers]?.usual == 7)
        #expect(lines[.feedings]?.value == 11)
        #expect(lines[.feedings]?.usual == 8)
        #expect(lines[.crying] == nil)
    }

    @Test func noComparisonOnFirstDay() {
        #expect(illnessScreenModel(
            events: events(),
            illness: illness,
            now: illnessModelAt("2026-10-07T21:00:00Z"),
            timeZone: illnessModelZone
        ).diary == nil)
    }

    @Test func doctorTextHasWhoWhatTemperatureMedicinesDiaryAndNotes() {
        let child = Child(id: 1, name: "Маша", birthDate: illnessModelAt("2026-01-01T00:00:00Z"), sex: .girl)
        let text = doctorText(
            model: illnessScreenModel(events: events(), illness: illness, now: now, timeZone: illnessModelZone),
            child: child,
            lastWeight: Weight(childId: 1, start: 0, grams: 8_900),
            timeZone: illnessModelZone
        )
        let expected = """
        Маша, 9 мес 9 дн., 8,9 кг
        Болеет с 7 окт. (4-й день): ОРВИ, с 10 окт. — Отит.

        Температура: макс. 39,2 (10 окт., 03:10), последняя 38,8 (10 окт., 10:40).
        По дням: 7 окт. — 38,4 · 10 — 39,2

        Лекарства:
        • Ибупрофен 4 мл — 1 раз, последний 10 окт. в 03:15
        • Амоксициллин 5 мл — 1 раз, последний 10 окт. в 12:00

        Вчера: мокрых подгузников 5 (обычно 7), кормлений 11 (обычно 8), сон 10 ч 50 мин (обычно 12 ч).

        Заметки:
        10 окт., 11:30 — Отит слева [врач]
        """
        #expect(text == expected)
    }

    @Test func endedIllnessSaysHowLongItLasted() {
        var ended = illness
        ended.end = illnessModelAt("2026-10-14T20:00:00Z")
        ended.names = []
        let child = Child(id: 1, name: "Маша", birthDate: nil, sex: .girl)
        let text = doctorText(
            model: illnessScreenModel(events: [ended], illness: ended, now: illnessModelAt("2026-10-20T10:00:00Z"), timeZone: illnessModelZone),
            child: child,
            lastWeight: nil,
            timeZone: illnessModelZone
        )
        #expect(text == "Маша\nБолела 7–14 окт. (8 дней).")
    }
}
