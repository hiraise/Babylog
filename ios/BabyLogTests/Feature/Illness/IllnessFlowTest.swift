import Foundation
import Testing
@testable import BabyLog

/**
 Болезнь на главном экране (ADR-0040): «Здоровье» → «Заболела» → «Начать» — карточка на «Сегодня»;
 касание карточки — экран болезни, «Назад» возвращает; «Выздоровела» там закрывает болезнь, и карточка
 пропадает. Температура со «Заболела» начинает болезнь с замера (ADR-0041). Нажатия проверяются моделями.
 */
@MainActor
@Suite(.serialized)
struct IllnessFlowTest {
    private let now = illnessFlowAt("2026-10-10T06:55:00Z")
    private let zone = TimeZone(secondsFromGMT: 0)!

    private func viewModel(repository: FakeDiaryRepository) -> DiaryViewModel {
        DiaryViewModel(
            repository: repository,
            selection: MemorySelectedChildStore(),
            documents: FakeDocumentStore(),
            clock: TestClock(date: Date(timeIntervalSince1970: Double(now) / 1000), timeZone: zone)
        )
    }

    @Test func illnessStartsFromHealthTileAndEndsOnItsScreen() async throws {
        let repository = FakeDiaryRepository(children: [Child(id: 1, name: "Маша", birthDate: nil, sex: .girl)])
        let viewModel = viewModel(repository: repository)
        #expect(await waitUntil { viewModel.state.child != nil })
        let sheet = IllnessStartSheetModel(nameChoices: defaultIllnessNames, lastTemperature: nil, now: now)
        sheet.tags.pick("ОРВИ")
        await viewModel.startIllness(
            start: sheet.start(clock: TestClock(date: Date(timeIntervalSince1970: Double(now) / 1000), timeZone: zone)),
            names: sheet.names,
            note: sheet.trimmedNote,
            temperatureTenths: nil
        )

        #expect(await waitUntil { viewModel.state.activeIllness != nil })
        let illness = try #require(viewModel.state.activeIllness)
        #expect(illness.names == [IllnessName("ОРВИ")])
        #expect(illnessCardModel(events: viewModel.state.events, illness: illness, now: now, timeZone: zone).day == 1)

        let recovered = IllnessRecoveredSheetModel(illness: illness, now: now, timeZone: zone)
        #expect(recovered.days == 1)
        #expect(recovered.end == now)
        await viewModel.recoverIllness(illness, end: recovered.end)
        #expect(await waitUntil { viewModel.state.activeIllness == nil })
        #expect(repository.events.compactMap { $0 as? Illness }.first?.end != nil)
    }

    @Test func temperatureWithSwitchStartsIllnessFromMeasurement() async throws {
        let repository = FakeDiaryRepository(children: [Child(id: 1, name: "Маша", birthDate: nil, sex: .girl)])
        let viewModel = viewModel(repository: repository)
        #expect(await waitUntil { viewModel.state.child != nil })
        let sheet = TemperatureSheetModel(initial: nil, last: nil, offerIllness: true)
        sheet.updateText("38,4")
        sheet.startIllness = true

        await viewModel.saveTemperature(
            Temperature(childId: 1, start: sheet.time(clock: TestClock(date: Date(timeIntervalSince1970: Double(now) / 1000), timeZone: zone)), tenths: try #require(sheet.tenths)),
            startIllness: sheet.shouldStartIllness
        )
        #expect(await waitUntil { viewModel.state.activeIllness != nil })
        let temperature = try #require(repository.events.compactMap { $0 as? Temperature }.first)
        #expect(temperature.tenths == 384)
        #expect(viewModel.state.activeIllness?.start == temperature.start)
        #expect(illnessCardModel(events: viewModel.state.events, illness: viewModel.state.activeIllness!, now: now, timeZone: zone).day == 1)
    }

    @Test func noIllnessWithoutSwitch() async {
        let repository = FakeDiaryRepository(children: [Child(id: 1, name: "Маша", birthDate: nil, sex: .girl)])
        let viewModel = viewModel(repository: repository)
        #expect(await waitUntil { viewModel.state.child != nil })
        let sheet = TemperatureSheetModel(initial: nil, last: nil, offerIllness: true)

        await viewModel.saveTemperature(
            Temperature(childId: 1, start: now, tenths: sheet.tenths!),
            startIllness: sheet.shouldStartIllness
        )
        #expect(await waitUntil { viewModel.state.lastTemperature != nil })
        #expect(viewModel.state.lastTemperature?.tenths == 366)
        #expect(viewModel.state.activeIllness == nil)
    }
}

private func illnessFlowAt(_ iso: String) -> Int64 {
    Int64(ISO8601DateFormatter().date(from: iso)!.timeIntervalSince1970 * 1000)
}
