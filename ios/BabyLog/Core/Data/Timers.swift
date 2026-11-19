import Foundation

/** Идущие таймеры ребёнка: сон и кормление не идут вместе (ADR-0020), плач идёт с кормлением (ADR-0030). */
struct ActiveTimers: Hashable, Sendable {
    var sleep: Sleep? = nil
    var feeding: Feeding? = nil
    var crying: Crying? = nil

    static func of(_ events: [any BabyEvent]) -> ActiveTimers {
        ActiveTimers(
            sleep: events.compactMap { $0 as? Sleep }.first(where: \.isActive),
            feeding: events.compactMap { $0 as? Feeding }.first(where: \.isActive),
            crying: events.compactMap { $0 as? Crying }.first(where: \.isActive)
        )
    }
}

/** Начатый плач `cryingId` и сон `sleep`, который он прервал: «Отменить» вернёт этот сон. */
struct StartedCrying: Hashable, Sendable {
    var cryingId: Int64
    var sleep: Sleep?
}

/** Грудь для следующего кормления: другая, чем в последнем кормлении грудью; кормлений ещё не было — левая. */
func nextBreast(events: [any BabyEvent]) -> Breast {
    events.compactMap { $0 as? Feeding }.max { $0.start < $1.start }?.breast.opposite ?? .left
}

/**
 Таймеры сна, кормления и плача — одни правила для экрана и уведомлений (ADR-0042).
 Что заканчивается при старте другого таймера, решает `DiaryRepository` (ADR-0020, ADR-0030);
 здесь — остановка не раньше начала, «Сменить грудь» и возврат остановленного по ошибке (ADR-0021).
 Идущие таймеры берутся из базы, а не с экрана.
 */
final class Timers: Sendable {
    private let repository: any DiaryRepository
    private let clock: any Clock

    init(repository: any DiaryRepository, clock: any Clock) {
        self.repository = repository
        self.clock = clock
    }

    func active(childId: Int64) async throws -> ActiveTimers {
        ActiveTimers.of(try await events(childId: childId))
    }

    /** false — сон уже идёт. */
    @discardableResult
    func startSleep(childId: Int64, start: Int64? = nil) async throws -> Bool {
        try await repository.startSleep(childId: childId, start: start ?? clock.millis())
    }

    /** false — кормление уже идёт. */
    @discardableResult
    func startFeeding(childId: Int64, breast: Breast, start: Int64? = nil) async throws -> Bool {
        try await repository.startFeeding(childId: childId, start: start ?? clock.millis(), breast: breast)
    }

    /** Плач начинается сейчас; nil — он уже идёт. */
    func startCrying(childId: Int64) async throws -> StartedCrying? {
        let sleep = try await active(childId: childId).sleep
        guard let id = try await repository.startCrying(childId: childId, start: clock.millis()) else { return nil }
        return StartedCrying(cryingId: id, sleep: sleep)
    }

    /** Идущий сон заканчивается сейчас, но не раньше своего начала; nil — сон не шёл. */
    func endSleep(childId: Int64) async throws -> Sleep? {
        guard var sleep = try await active(childId: childId).sleep else { return nil }
        sleep.end = endAt(sleep.start)
        try await repository.updateEvent(sleep)
        return sleep
    }

    func endFeeding(childId: Int64) async throws -> Feeding? {
        guard var feeding = try await active(childId: childId).feeding else { return nil }
        feeding.end = endAt(feeding.start)
        try await repository.updateEvent(feeding)
        return feeding
    }

    func endCrying(childId: Int64) async throws -> Crying? {
        guard var crying = try await active(childId: childId).crying else { return nil }
        crying.end = endAt(crying.start)
        try await repository.updateEvent(crying)
        return crying
    }

    /** Идущее кормление заканчивается, с той же секунды начинается кормление другой грудью. */
    func switchBreast(childId: Int64) async throws -> Bool {
        guard let feeding = try await active(childId: childId).feeding else { return false }
        let now = endAt(feeding.start)
        try await repository.inTransaction { transaction in
            var finished = feeding
            finished.end = now
            try transaction.updateEvent(finished)
            try transaction.startFeeding(childId: feeding.childId, start: now, breast: feeding.breast.opposite)
        }
        return true
    }

    /**
     «Отменить» остановку (ADR-0021): сон или кормление `timer` в том виде, как шли до остановки, идут
     дальше — если за это время не начался другой сон или кормление.
     */
    func resume(timer: any BabyEvent) async throws -> Bool {
        let now = try await active(childId: timer.childId)
        if now.sleep != nil || now.feeding != nil { return false }
        try await repository.updateEvent(timer)
        return true
    }

    /**
     «Отменить» начало плача (ADR-0030): плач `cryingId` удаляется, прерванный им сон `sleep` идёт дальше —
     на тех же условиях, что `resume`.
     */
    func cancelCrying(childId: Int64, cryingId: Int64, sleep: Sleep?) async throws {
        if let crying = try await events(childId: childId).first(where: { $0.id == cryingId }) {
            try await repository.deleteEvent(crying)
        }
        if var sleep {
            sleep.end = nil
            _ = try await resume(timer: sleep)
        }
    }

    private func events(childId: Int64) async throws -> [any BabyEvent] {
        for try await events in repository.observeEvents(childId: childId) { return events }
        return []
    }

    private func endAt(_ start: Int64) -> Int64 {
        max(clock.millis(), start)
    }
}
