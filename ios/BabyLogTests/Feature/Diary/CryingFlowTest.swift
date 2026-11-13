import Foundation
import Testing
@testable import BabyLog

@MainActor
@Suite(.serialized)
struct CryingFlowTest {
    private let clock = TestClock(date: Date(timeIntervalSince1970: 1_792_057_200), timeZone: .current)

    private func model(repository: FakeDiaryRepository) -> DiaryViewModel {
        DiaryViewModel(
            repository: repository,
            selection: MemorySelectedChildStore(),
            documents: FakeDocumentStore(),
            clock: clock
        )
    }

    @Test func stoppedCryingKeepsDetailsInSameRecord() async {
        let repository = FakeDiaryRepository(children: [Child(id: 1, name: "Маша", birthDate: nil, sex: .girl)])
        let model = model(repository: repository)
        #expect(await waitUntil { model.state.child != nil })
        await model.startCrying()
        let stopped = await model.endCrying()

        #expect(stopped != nil)
        await model.saveCryingDetails(stopped!, start: stopped!.start, reasons: ["колики"], helped: ["укачивание"], note: "")
        #expect(await waitUntil { model.state.lastCrying?.reasons == ["колики"] })
        #expect(model.state.lastCrying?.helped == ["укачивание"])
        #expect(model.state.lastCrying?.isActive == false)
    }

    @Test func accidentalStartIsUndoneFromMessage() async {
        let repository = FakeDiaryRepository()
        let model = model(repository: repository)
        #expect(await waitUntil { model.state.child != nil })
        await model.startCrying()
        await model.consumeMessage(performed: true)

        #expect(!repository.events.contains { $0 is Crying })
    }

    @Test func cryingStoppedByMistakeGoesOn() async {
        let repository = FakeDiaryRepository()
        let model = model(repository: repository)
        #expect(await waitUntil { model.state.child != nil })
        await model.startCrying()
        let stopped = await model.endCrying()
        await model.resumeCrying(stopped!)

        #expect(await waitUntil { model.state.activeCrying?.id == stopped?.id })
    }

    @Test func fallingAsleepWhileCryingReturnsDetailsWithoutResume() async {
        let repository = FakeDiaryRepository()
        let model = model(repository: repository)
        #expect(await waitUntil { model.state.child != nil })
        await model.startCrying()
        #expect(await waitUntil { model.state.activeCrying != nil })
        let stopped = await model.startSleep(start: clock.millis())

        #expect(stopped?.isActive == false)
        #expect(await waitUntil { model.state.activeSleep != nil })
        #expect(model.state.activeCrying == nil)
        await model.saveCryingDetails(stopped!, start: stopped!.start, reasons: ["колики"], helped: [], note: "")
        #expect(await waitUntil { model.state.lastCrying?.reasons == ["колики"] })
    }
}
