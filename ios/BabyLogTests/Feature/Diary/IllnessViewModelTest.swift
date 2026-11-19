import Foundation
import Testing
@testable import BabyLog

/** Болезнь и температура на главном экране (ADR-0040, ADR-0041): начать, закончить, вернуть, названия. */
@MainActor
@Suite(.serialized)
struct IllnessViewModelTest {
    private let now = illnessViewModelAt("2026-10-10T03:10:00Z")
    private let zone = TimeZone(secondsFromGMT: 0)!

    private func model(repository: FakeDiaryRepository) -> DiaryViewModel {
        DiaryViewModel(
            repository: repository,
            selection: MemorySelectedChildStore(),
            documents: FakeDocumentStore(),
            clock: TestClock(date: Date(timeIntervalSince1970: Double(now) / 1000), timeZone: zone)
        )
    }

    @Test func illnessStartsWithNamesFromItsStartAndTemperatureNow() async throws {
        let repository = FakeDiaryRepository(children: [Child(id: 1, name: "Маша", birthDate: nil)])
        _ = try await repository.addEvent(Temperature(id: 50, childId: 1, start: now - 86_400_000, tenths: 366, method: .ear))
        let model = model(repository: repository)
        #expect(await waitUntil { model.state.child != nil && model.state.lastTemperature != nil })

        await model.startIllness(start: now - 3_600_000, names: ["ОРВИ"], note: "из сада", temperatureTenths: 384)
        #expect(await waitUntil { model.state.activeIllness != nil && model.state.lastTemperature?.tenths == 384 })
        let illness = try #require(model.state.activeIllness)
        #expect(illness.start == now - 3_600_000)
        #expect(illness.names == [IllnessName("ОРВИ")])
        #expect(illness.note == "из сада")
        #expect(model.state.lastTemperature?.start == now)
        // Место — как в прошлый раз: тем же градусником
        #expect(model.state.lastTemperature?.method == .ear)
    }

    @Test func secondIllnessDoesNotStartWhileOneGoesOn() async {
        let repository = FakeDiaryRepository(children: [Child(id: 1, name: "Маша", birthDate: nil)])
        let model = model(repository: repository)
        #expect(await waitUntil { model.state.child != nil })
        await model.startIllness(start: now - 1_000, names: [], note: "", temperatureTenths: nil)
        #expect(await waitUntil { model.state.activeIllness != nil })

        await model.startIllness(start: now, names: ["отит"], note: "", temperatureTenths: 390)
        #expect(repository.events.compactMap { $0 as? Illness }.count == 1)
        // Замер второго листа не сохраняется: его лист не начал болезнь
        #expect(model.state.lastTemperature == nil)
    }

    @Test func temperatureSwitchStartsIllnessFromMeasurement() async {
        let repository = FakeDiaryRepository(children: [Child(id: 1, name: "Маша", birthDate: nil)])
        let model = model(repository: repository)
        #expect(await waitUntil { model.state.child != nil })

        await model.saveTemperature(Temperature(childId: 1, start: now - 60_000, tenths: 384), startIllness: true)
        #expect(await waitUntil { model.state.activeIllness?.start == now - 60_000 })
        #expect(model.state.lastTemperature?.tenths == 384)
    }

    @Test func recoveredIllnessEndsAndMayComeBack() async throws {
        let repository = FakeDiaryRepository(children: [Child(id: 1, name: "Маша", birthDate: nil)])
        let model = model(repository: repository)
        #expect(await waitUntil { model.state.child != nil })
        await model.startIllness(start: now - 5 * 86_400_000, names: [], note: "", temperatureTenths: nil)
        #expect(await waitUntil { model.state.activeIllness != nil })
        let illness = try #require(model.state.activeIllness)

        await model.recoverIllness(illness, end: now - 86_400_000)
        #expect(await waitUntil { model.state.activeIllness == nil })
        #expect(repository.events.compactMap { $0 as? Illness }.single?.end == now - 86_400_000)
        await model.resumeIllness(illness)
        #expect(await waitUntil { model.state.activeIllness?.id == illness.id })
    }

    @Test func endBeforeStartIsMovedToStart() async throws {
        let repository = FakeDiaryRepository(children: [Child(id: 1, name: "Маша", birthDate: nil)])
        let model = model(repository: repository)
        #expect(await waitUntil { model.state.child != nil })
        await model.startIllness(start: now - 1_000, names: [], note: "", temperatureTenths: nil)
        #expect(await waitUntil { model.state.activeIllness != nil })

        await model.recoverIllness(try #require(model.state.activeIllness), end: now - 10_000)
        #expect(await waitUntil { model.state.activeIllness == nil })
        #expect(repository.events.compactMap { $0 as? Illness }.single?.end == now - 1_000)
    }

    @Test func namesAreAddedChangedAndRemovedOnSameIllness() async throws {
        let repository = FakeDiaryRepository(children: [Child(id: 1, name: "Маша", birthDate: nil)])
        let model = model(repository: repository)
        #expect(await waitUntil { model.state.child != nil })
        await model.startIllness(start: now - 3 * 86_400_000, names: ["ОРВИ"], note: "", temperatureTenths: nil)
        #expect(await waitUntil { model.state.activeIllness != nil })
        let illness = try #require(model.state.activeIllness)
        let day = illnessViewModelDay("2026-10-10")
        let otitis = IllnessName("Отит", since: day)

        await model.saveIllnessName(illness, old: nil, name: otitis)
        #expect(await waitUntil { model.state.activeIllness?.names.count == 2 })
        // Повтор без учёта регистра не добавляется
        await model.saveIllnessName(model.state.activeIllness!, old: nil, name: IllnessName("орви"))
        #expect(model.state.activeIllness?.names == [IllnessName("ОРВИ"), otitis])
        let moved = IllnessName("Отит", since: illnessViewModelDay("2026-10-09"))
        await model.saveIllnessName(model.state.activeIllness!, old: otitis, name: moved)
        #expect(await waitUntil { model.state.activeIllness?.names.contains(moved) == true })
        await model.removeIllnessName(model.state.activeIllness!, name: IllnessName("ОРВИ"))
        #expect(await waitUntil { model.state.activeIllness?.names == [moved] })
    }
}

private func illnessViewModelAt(_ iso: String) -> Int64 {
    Int64(ISO8601DateFormatter().date(from: iso)!.timeIntervalSince1970 * 1000)
}

private func illnessViewModelDay(_ value: String) -> Date {
    let formatter = DateFormatter()
    formatter.calendar = Calendar(identifier: .gregorian)
    formatter.locale = Locale(identifier: "en_US_POSIX")
    formatter.timeZone = TimeZone(secondsFromGMT: 0)
    formatter.dateFormat = "yyyy-MM-dd"
    return formatter.date(from: value)!
}

private extension Array {
    var single: Element? { count == 1 ? first : nil }
}
