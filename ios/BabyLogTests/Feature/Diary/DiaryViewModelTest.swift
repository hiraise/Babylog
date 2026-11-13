import Foundation
import Testing
@testable import BabyLog

@MainActor
@Suite(.serialized)
struct DiaryViewModelTest {
    private let now = Date(timeIntervalSince1970: 1_792_058_400.750)
    private let utc = TimeZone(secondsFromGMT: 0)!

    private func model(
        repository: FakeDiaryRepository,
        selection: MemorySelectedChildStore = MemorySelectedChildStore(),
        documents: FakeDocumentStore = FakeDocumentStore()
    ) -> DiaryViewModel {
        DiaryViewModel(
            repository: repository,
            selection: selection,
            documents: documents,
            clock: TestClock(date: now, timeZone: .current)
        )
    }

    @Test func firstChildIsShownUntilAnotherIsSelected() async {
        let repository = FakeDiaryRepository(children: [
            Child(id: 1, name: "Маша", birthDate: nil),
            Child(id: 2, name: "Петя", birthDate: nil),
        ])
        let model = model(repository: repository)
        #expect(await waitUntil { model.state.child?.id == 1 })

        model.selectChild(2)
        #expect(await waitUntil { model.state.child?.id == 2 })
    }

    @Test func recordsBelongToSelectedChildOnly() async throws {
        let repository = FakeDiaryRepository(children: [
            Child(id: 1, name: "Маша", birthDate: nil),
            Child(id: 2, name: "Петя", birthDate: nil),
        ])
        _ = try await repository.addEvent(Feeding(childId: 1, start: 1_000, breast: .left))
        _ = try await repository.addEvent(Diaper(childId: 2, start: 2_000, kind: .wet))
        let model = model(repository: repository)
        #expect(await waitUntil { model.state.events.map(\.childId) == [1] })

        model.selectChild(2)
        #expect(await waitUntil { model.state.events.count == 1 && model.state.events.first is Diaper })
    }

    @Test func newChildIsSelectedWithPhotoAndDuplicateNamesAreIgnored() async {
        let repository = FakeDiaryRepository(children: [
            Child(id: 1, name: "Маша", birthDate: nil),
            Child(id: 2, name: "Петя", birthDate: nil),
        ])
        let model = model(repository: repository)
        #expect(await waitUntil { model.state.child != nil })
        let photo = ChildPhoto(jpeg: Data([1, 2, 3]))

        await model.addChild(name: " Вася ", birthDate: nil, sex: .boy, photo: photo)
        #expect(await waitUntil { model.state.child?.id == 3 })
        #expect(model.state.child == Child(id: 3, name: "Вася", birthDate: nil, sex: .boy, photo: photo))

        await model.addChild(name: "вася", birthDate: nil, sex: nil)
        #expect(model.state.children.count == 3)
    }

    @Test func deletingSelectedChildFallsBackToFirstAndLastChildStays() async {
        let repository = FakeDiaryRepository(children: [
            Child(id: 1, name: "Маша", birthDate: nil),
            Child(id: 2, name: "Петя", birthDate: nil),
        ])
        let model = model(repository: repository)
        #expect(await waitUntil { model.state.child != nil })
        model.selectChild(2)
        await model.deleteChild(model.state.child!)

        #expect(await waitUntil { model.state.child?.id == 1 })
        #expect(model.state.message == "Удалено: Петя")
        await model.deleteChild(model.state.child!)
        #expect(model.state.children.count == 1)
    }

    @Test func headerUsesLatestWeightAndHeight() async throws {
        let repository = FakeDiaryRepository(children: [Child(id: 1, name: "Маша", birthDate: nil)])
        _ = try await repository.addEvent(Weight(childId: 1, start: 1_000, grams: 6_000))
        _ = try await repository.addEvent(Weight(childId: 1, start: 2_000, grams: 6_350))
        _ = try await repository.addEvent(Height(childId: 1, start: 1_500, millimeters: 625))
        let model = model(repository: repository)

        #expect(await waitUntil { model.state.events.count == 3 })
        #expect(model.state.lastWeight?.grams == 6_350)
        #expect(model.state.lastHeight?.millimeters == 625)
    }

    @Test func exportAndImportReportResults() async throws {
        let repository = FakeDiaryRepository(children: [Child(id: 1, name: "Маша", birthDate: nil)])
        _ = try await repository.addEvent(Feeding(childId: 1, start: 1_000, breast: .left))
        let documents = FakeDocumentStore()
        let model = model(repository: repository, documents: documents)
        #expect(await waitUntil { model.state.child != nil })
        let url = URL(fileURLWithPath: "/tmp/diary.zip")

        await model.exportTo(url)
        #expect(model.state.message == "Экспортировано записей: 1")
        #expect(repository.events.count == 1)
        await model.consumeMessage()
        await model.importFrom(url)
        #expect(model.state.message == "Импорт: добавлено 0, дубликатов 1")
    }

    @Test func failedWriteIsMessageNotCrash() async {
        let repository = FakeDiaryRepository(children: [Child(id: 1, name: "Маша", birthDate: nil)])
        repository.writeError = TransferError("database or disk is full")
        let model = model(repository: repository)
        #expect(await waitUntil { model.state.child != nil })
        var edited = model.state.child!
        edited.name = "Петя"

        await model.updateChild(edited)
        #expect(model.state.message?.hasPrefix("Не удалось сохранить") == true)
        #expect(repository.children.first?.name == "Маша")
    }

    @Test func failedReadIsRetriedInsteadOfClosingApp() async throws {
        let repository = FakeDiaryRepository(children: [Child(id: 1, name: "Маша", birthDate: nil)])
        _ = try await repository.addEvent(Feeding(childId: 1, start: 1_000, end: 2_000, breast: .left))
        repository.eventsReadError = TransferError("database disk image is malformed")
        let model = model(repository: repository)
        #expect(await waitUntil { model.state.message?.hasPrefix("Не удалось прочитать дневник") == true })

        repository.eventsReadError = nil
        #expect(await waitUntil { model.state.events.count == 1 })
    }

    @Test func historyDaysIncludeTodayEvenWithoutRecords() {
        let today = Date(timeIntervalSince1970: 1_792_056_000)
        let event = Feeding(childId: 1, start: 1_791_883_200, breast: .left)
        let days = historyDays(events: [event], today: today, timeZone: utc)

        #expect(days == [toLocalDate(event.start, timeZone: utc), toLocalDate(Int64(today.timeIntervalSince1970 * 1000), timeZone: utc)])
    }

    @Test func sleepTimerEndsAtClockTimeTruncatedToSeconds() async {
        let repository = FakeDiaryRepository()
        let model = model(repository: repository)
        #expect(await waitUntil { model.state.child != nil })
        let nowMillis = Int64(now.timeIntervalSince1970 * 1000)

        await model.startSleep(start: nowMillis - 3_600_000)
        await model.startSleep(start: nowMillis)
        #expect(repository.events.count == 1)
        await model.endSleep()

        let sleep = repository.events.first as? Sleep
        #expect(sleep?.end == 1_792_058_400_000)
        #expect(await waitUntil { model.state.activeSleep == nil })
    }

    @Test func switchingBreastSplitsFeedingAtOneSecondAndEndStopsIt() async {
        let repository = FakeDiaryRepository()
        let model = model(repository: repository)
        #expect(await waitUntil { model.state.child != nil })
        let nowMillis = Int64(now.timeIntervalSince1970 * 1000)
        let start = nowMillis - 600_000

        await model.startFeeding(start: start, breast: .left)
        await model.startFeeding(start: nowMillis, breast: .right)
        #expect((repository.events.first as? Feeding)?.breast == .left)
        await model.switchBreast()
        await model.endFeeding()

        let feedings = repository.events.compactMap { $0 as? Feeding }.sorted { $0.start < $1.start }
        #expect(feedings.map(\.breast) == [.left, .right])
        #expect(feedings.map(\.end) == [1_792_058_400_000, 1_792_058_400_000])
        #expect(feedings[1].start == 1_792_058_400_000)
        #expect(await waitUntil { model.state.activeFeeding == nil })
    }

    @Test func sleepAndFeedingNeverRunTogether() async {
        let repository = FakeDiaryRepository()
        let model = model(repository: repository)
        #expect(await waitUntil { model.state.child != nil })
        let nowMillis = Int64(now.timeIntervalSince1970 * 1000)
        let start = nowMillis - 1_800_000

        await model.startFeeding(start: start, breast: .left)
        await model.startSleep(start: start + 600_000)
        #expect(repository.events.compactMap { $0 as? Feeding }.first?.end == 1_792_057_200_000)
        await model.startFeeding(start: start + 1_200_000, breast: .right)

        #expect(await waitUntil { model.state.activeSleep == nil && model.state.activeFeeding?.breast == .right })
    }

    @Test func stoppingTimerByTapCanBeUndone() async {
        let repository = FakeDiaryRepository()
        let model = model(repository: repository)
        #expect(await waitUntil { model.state.child != nil })
        let nowMillis = Int64(now.timeIntervalSince1970 * 1000)
        await model.startSleep(start: nowMillis - 3_600_000)
        await model.endSleep()

        #expect(model.state.message == "Сон: 1 ч")
        guard case .resumeTimer = model.state.messageAction else {
            Issue.record("У остановки таймера нет действия «Отменить»")
            return
        }
        await model.consumeMessage(performed: true)
        #expect(repository.events.compactMap { $0 as? Sleep }.first?.end == nil)
        #expect(model.state.messageAction == nil)
        #expect(await waitUntil { model.state.activeSleep != nil })
    }

    @Test func accidentalCryingStartIsUndoneAndSleepGoesOn() async {
        let repository = FakeDiaryRepository()
        let model = model(repository: repository)
        #expect(await waitUntil { model.state.child != nil })
        let nowMillis = Int64(now.timeIntervalSince1970 * 1000)
        await model.startSleep(start: nowMillis - 3_600_000)
        await model.startCrying()

        #expect(model.state.message == "Плач начат, сон закончен")
        await model.consumeMessage(performed: true)
        #expect(!repository.events.contains { $0 is Crying })
        #expect(await waitUntil { model.state.activeSleep != nil })
    }

    @Test func stoppingCryingDropsItsUndo() async {
        let repository = FakeDiaryRepository()
        let model = model(repository: repository)
        #expect(await waitUntil { model.state.child != nil })
        await model.startCrying()
        #expect(model.state.message == "Плач начат")

        await model.endCrying()
        #expect(model.state.message == nil)
        #expect(model.state.messageAction == nil)
    }

    @Test func fallingAsleepEndsCryingWithoutMessage() async {
        let repository = FakeDiaryRepository()
        let model = model(repository: repository)
        #expect(await waitUntil { model.state.child != nil })
        await model.startCrying()
        await model.consumeMessage()
        await model.startSleep()

        #expect(await waitUntil { model.state.activeCrying == nil && model.state.activeSleep != nil })
        #expect(model.state.message == nil)
    }

    @Test func cryingStartsAtOnceWakesBabyAndCanBeResumedAfterWrongStop() async {
        let repository = FakeDiaryRepository()
        let model = model(repository: repository)
        #expect(await waitUntil { model.state.child != nil })
        let nowMillis = Int64(now.timeIntervalSince1970 * 1000)
        await model.startSleep(start: nowMillis - 3_600_000)
        await model.startCrying()

        let crying = repository.events.compactMap { $0 as? Crying }.first
        #expect(crying?.start == 1_792_058_400_000)
        #expect(repository.events.compactMap { $0 as? Sleep }.first?.end != nil)
        await model.endCrying()
        #expect(await waitUntil { model.state.lastCrying?.id == crying?.id })
        await model.resumeCrying(model.state.lastCrying!)

        #expect(repository.events.compactMap { $0 as? Crying }.first?.start == crying?.start)
        #expect(await waitUntil { model.state.activeCrying?.start == crying?.start })
    }

    @Test func undoDoesNotResumeTimerOnceAnotherOneStarted() async {
        let repository = FakeDiaryRepository()
        let model = model(repository: repository)
        #expect(await waitUntil { model.state.child != nil })
        let nowMillis = Int64(now.timeIntervalSince1970 * 1000)
        await model.startSleep(start: nowMillis - 3_600_000)
        await model.endSleep()
        await model.startFeeding(breast: .left)
        await model.consumeMessage(performed: true)

        #expect(repository.events.compactMap { $0 as? Sleep }.first?.end != nil)
        #expect(await waitUntil { model.state.activeSleep == nil && model.state.activeFeeding?.breast == .left })
    }
}
