import Foundation
import Testing
@testable import BabyLog

/**
 Болезнь (ADR-0040) на эталонных скриншотах: карточка на «Сегодня» ночью 4-го дня и экран болезни
 в светлой и тёмной теме. Обновить — `scripts/dev.sh ios-goldens`.
 */
@MainActor
@Suite(.serialized)
struct IllnessScreenshotTest {
    private let zone = TimeZone(secondsFromGMT: 0)!
    private let hour: Int64 = 3_600_000
    private let masha = Child(id: 1, name: "Маша", birthDate: illnessShotAt("2026-01-01T00:00:00Z"), sex: .girl)
    private let illness = Illness(
        id: 1,
        childId: 1,
        start: illnessShotAt("2026-10-07T19:30:00Z"),
        names: [IllnessName("ОРВИ"), IllnessName("Отит", since: illnessShotDay("2026-10-10"))]
    )

    /**
     История Маши: две недели режима до болезни, ОРВИ с 7 октября, ночью на 4-й день 39,2 и ибупрофен,
     днём врач нашёл отит — антибиотик. `until` — что уже случилось к этому времени.
     */
    private func diary(until: Int64) -> [any BabyEvent] {
        var result: [any BabyEvent] = [
            illness,
            Illness(id: 2, childId: 1, start: illnessShotAt("2026-09-02T09:00:00Z"), end: illnessShotAt("2026-09-06T20:00:00Z"), names: [IllnessName("простуда")]),
            Weight(id: 3, childId: 1, start: illnessShotAt("2026-10-02T09:00:00Z"), grams: 8_900),
        ]
        var id: Int64 = 100
        func nextId() -> Int64 {
            defer { id += 1 }
            return id
        }
        for back in 0...17 {
            let day = illnessShotAt("2026-10-10T00:00:00Z") - Int64(back) * 24 * hour
            let sick = (1...3).contains(back)
            for index in 0..<(sick ? 11 : 8) {
                let start = day + Int64(index) * (sick ? 2 : 3) * hour
                result.append(Feeding(id: nextId(), childId: 1, start: start, end: start + 15 * 60_000, breast: index % 2 == 0 ? .left : .right))
            }
            for index in 0..<(sick ? 5 : 7) {
                result.append(Diaper(id: nextId(), childId: 1, start: day + Int64(index) * 3 * hour + hour, kind: .wet))
            }
            result.append(Sleep(id: nextId(), childId: 1, start: day + 12 * hour, end: day + (sick ? 22 : 24) * hour))
        }
        result.append(Sleep(id: nextId(), childId: 1, start: illnessShotAt("2026-10-10T03:30:00Z"), end: illnessShotAt("2026-10-10T06:00:00Z")))
        for (time, tenths) in [
            ("2026-10-07T19:30:00Z", 384), ("2026-10-07T23:00:00Z", 381), ("2026-10-08T07:00:00Z", 386),
            ("2026-10-08T20:00:00Z", 382), ("2026-10-09T08:00:00Z", 376), ("2026-10-09T15:20:00Z", 380),
            ("2026-10-10T03:10:00Z", 392), ("2026-10-10T07:30:00Z", 381), ("2026-10-10T10:40:00Z", 388),
        ] {
            result.append(Temperature(id: nextId(), childId: 1, start: illnessShotAt(time), tenths: tenths, method: .armpit))
        }
        for time in [
            "2026-10-07T19:40:00Z", "2026-10-08T07:10:00Z", "2026-10-08T20:10:00Z",
            "2026-10-09T21:10:00Z", "2026-10-10T03:15:00Z", "2026-10-10T10:45:00Z",
        ] {
            result.append(Medicine(id: nextId(), childId: 1, start: illnessShotAt(time), name: "Ибупрофен", note: "4 мл"))
        }
        for time in ["2026-10-08T14:00:00Z", "2026-10-09T15:30:00Z"] {
            result.append(Medicine(id: nextId(), childId: 1, start: illnessShotAt(time), name: "Парацетамол", note: "5 мл"))
        }
        result.append(Medicine(id: nextId(), childId: 1, start: illnessShotAt("2026-10-10T12:00:00Z"), name: "Амоксициллин", note: "5 мл"))
        result.append(Note(id: nextId(), childId: 1, start: illnessShotAt("2026-10-08T12:10:00Z"), tags: ["врач"], note: "Врач: ОРВИ, горло красное"))
        result.append(Note(id: nextId(), childId: 1, start: illnessShotAt("2026-10-10T03:30:00Z"), note: "Трёт левое ухо, плачет лёжа"))
        result.append(Note(id: nextId(), childId: 1, start: illnessShotAt("2026-10-10T11:30:00Z"), tags: ["врач"], note: "Врач: отит слева, амоксициллин 7 дней"))
        return result.filter { $0.start <= until }
    }

    @Test func todayWithIllnessCard() async throws {
        let now = illnessShotAt("2026-10-10T06:55:00Z")
        let repository = FakeDiaryRepository(children: [masha])
        for event in diary(until: now) { _ = try await repository.addEvent(event) }
        let clock = TestClock(date: Date(timeIntervalSince1970: Double(now) / 1000), timeZone: zone)
        let model = DiaryViewModel(repository: repository, selection: MemorySelectedChildStore(), documents: FakeDocumentStore(), clock: clock)
        #expect(await waitUntil { model.state.events.count == repository.events.count })

        assertComponentSnapshot(name: "diary_illness", style: .light, clock: clock) {
            DiaryScreen(model: model, theme: ThemeChoice(), onThemeChange: { _ in })
        }
    }

    @Test(arguments: [false, true]) func illnessScreen(dark: Bool) {
        let now = illnessShotAt("2026-10-10T12:30:00Z")
        let clock = TestClock(date: Date(timeIntervalSince1970: Double(now) / 1000), timeZone: zone)
        let model = illnessScreenModel(events: diary(until: now), illness: illness, now: now, timeZone: zone)
        assertComponentSnapshot(name: dark ? "illness_screen_dark" : "illness_screen", style: dark ? .dark : .light, clock: clock) {
            IllnessContent(model: model, sex: .girl, now: now, actions: IllnessActions())
        }
    }
}

private func illnessShotAt(_ iso: String) -> Int64 {
    Int64(ISO8601DateFormatter().date(from: iso)!.timeIntervalSince1970 * 1000)
}

private func illnessShotDay(_ value: String) -> Date {
    let formatter = DateFormatter()
    formatter.calendar = Calendar(identifier: .gregorian)
    formatter.locale = Locale(identifier: "en_US_POSIX")
    formatter.timeZone = TimeZone(secondsFromGMT: 0)
    formatter.dateFormat = "yyyy-MM-dd"
    return formatter.date(from: value)!
}
