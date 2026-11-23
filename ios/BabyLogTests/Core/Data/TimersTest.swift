import Foundation
import Testing
@testable import BabyLog

private let timersNow: Int64 = 1_786_003_600_000

struct TimersTest {
    private func make() -> (FakeDiaryRepository, Timers) {
        let repository = FakeDiaryRepository()
        let clock = TestClock(date: Date(timeIntervalSince1970: Double(timersNow) / 1000))
        return (repository, Timers(repository: repository, clock: clock))
    }

    @Test func activeTimersAndNextBreastComeFromStoredEvents() async throws {
        let (repository, timers) = make()
        _ = try await repository.addEvent(Feeding(childId: 1, start: timersNow - 3_600_000, end: timersNow - 3_000_000, breast: .left))
        _ = try await repository.addEvent(Feeding(childId: 1, start: timersNow - 2_000_000, end: timersNow - 1_000_000, breast: .right))
        #expect(nextBreast(events: repository.events) == .left)

        try await timers.startSleep(childId: 1, start: timersNow - 600_000)
        try await timers.startFeeding(childId: 1, breast: .left, start: timersNow - 300_000)
        _ = try await timers.startCrying(childId: 1)
        let active = try await timers.active(childId: 1)
        #expect(active.sleep == nil)
        #expect(active.feeding?.breast == .left)
        #expect(active.crying != nil)
    }

    @Test func endingBeforeStartProducesZeroDurationAndNoTimerEndsTwice() async throws {
        let (repository, timers) = make()
        let start = timersNow + 5_000
        try await timers.startSleep(childId: 1, start: start)
        let ended = try await timers.endSleep(childId: 1)
        #expect(ended?.end == start)
        #expect(try await timers.endSleep(childId: 1) == nil)
        #expect(repository.events.compactMap { $0 as? Sleep }.single?.end == start)
    }

    @Test func switchingBreastSplitsFeedingAtSameSecond() async throws {
        let (repository, timers) = make()
        let start = timersNow - 600_000
        try await timers.startFeeding(childId: 1, breast: .left, start: start)
        #expect(try await timers.switchBreast(childId: 1))
        #expect(try await timers.active(childId: 1).feeding?.breast == .right)

        let feedings = repository.events.compactMap { $0 as? Feeding }.sorted { $0.start < $1.start }
        #expect(feedings.map(\.breast) == [.left, .right])
        #expect(feedings.map(\.end) == [timersNow, nil])
        #expect(feedings.last?.start == timersNow)
        #expect(try await timers.switchBreast(childId: 1))
        #expect(try await timers.endFeeding(childId: 1)?.end == timersNow)
    }

    @Test func stoppingTimerCanBeUndone() async throws {
        let (repository, timers) = make()
        try await timers.startSleep(childId: 1, start: timersNow - 3_600_000)
        guard var stopped = try await timers.endSleep(childId: 1) else {
            Issue.record("Сон не остановился")
            return
        }
        stopped.end = nil
        #expect(try await timers.resume(timer: stopped))
        #expect(try await timers.active(childId: 1).sleep == stopped)
    }

    @Test func undoDoesNotResumeTimerOnceAnotherOneStarted() async throws {
        let (_, timers) = make()
        try await timers.startSleep(childId: 1, start: timersNow - 3_600_000)
        guard var stopped = try await timers.endSleep(childId: 1) else {
            Issue.record("Сон не остановился")
            return
        }
        stopped.end = nil
        try await timers.startFeeding(childId: 1, breast: .left)
        #expect(try await timers.resume(timer: stopped) == false)
        #expect(try await timers.active(childId: 1).sleep == nil)
        #expect(try await timers.active(childId: 1).feeding?.breast == .left)
    }

    @Test func accidentalCryingStartIsUndoneAndSleepGoesOn() async throws {
        let (repository, timers) = make()
        try await timers.startSleep(childId: 1, start: timersNow - 3_600_000)
        let started = try #require(try await timers.startCrying(childId: 1))
        #expect(try await timers.startCrying(childId: 1) == nil)

        try await timers.cancelCrying(childId: 1, cryingId: started.cryingId, sleep: started.sleep)
        #expect(repository.events.allSatisfy { !($0 is Crying) })
        #expect(try await timers.active(childId: 1).sleep == started.sleep)
    }
}

private extension Array {
    var single: Element? { count == 1 ? first : nil }
}
