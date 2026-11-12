import Foundation

/** Значение настройки и её изменения — аналог `StateFlow` для хранилищ на `UserDefaults`. */
final class StoreState<Value: Sendable>: @unchecked Sendable {
    private let lock = NSLock()
    private var stored: Value
    private var continuations: [UUID: AsyncStream<Value>.Continuation] = [:]

    init(_ value: Value) {
        stored = value
    }

    var value: Value {
        lock.lock()
        defer { lock.unlock() }
        return stored
    }

    func set(_ value: Value) {
        lock.lock()
        stored = value
        let continuations = Array(continuations.values)
        lock.unlock()
        for continuation in continuations { continuation.yield(value) }
    }

    func stream() -> AsyncStream<Value> {
        AsyncStream { continuation in
            let id = UUID()
            lock.lock()
            continuations[id] = continuation
            let value = stored
            lock.unlock()
            continuation.yield(value)
            continuation.onTermination = { @Sendable [weak self] _ in
                self?.lock.lock()
                self?.continuations.removeValue(forKey: id)
                self?.lock.unlock()
            }
        }
    }
}

/** Какой ребёнок выбран на экране. Выбор переживает перезапуск приложения. */
protocol SelectedChildStore: Sendable {
    /** id выбранного ребёнка; nil — ещё не выбирали (показывается первый). */
    var selectedChildId: StoreState<Int64?> { get }

    func select(_ childId: Int64)
}

final class PrefsSelectedChildStore: SelectedChildStore, @unchecked Sendable {
    let selectedChildId: StoreState<Int64?>
    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        let stored = (defaults.object(forKey: Self.key) as? NSNumber)?.int64Value
        selectedChildId = StoreState(stored == Self.none ? nil : stored)
    }

    func select(_ childId: Int64) {
        defaults.set(childId, forKey: Self.key)
        selectedChildId.set(childId)
    }

    private static let key = "selected_child_id"
    private static let none: Int64 = -1
}

/** Выбор ребёнка в примере дневника (ADR-0043): живёт, пока открыт гайд, и настоящий выбор не трогает. */
final class MemorySelectedChildStore: SelectedChildStore, @unchecked Sendable {
    let selectedChildId: StoreState<Int64?>

    init(initial: Int64? = nil) {
        selectedChildId = StoreState(initial)
    }

    func select(_ childId: Int64) {
        selectedChildId.set(childId)
    }
}
