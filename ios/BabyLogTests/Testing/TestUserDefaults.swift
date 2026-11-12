import Foundation

func withTestUserDefaults<T>(_ body: (UserDefaults) throws -> T) rethrows -> T {
    let suite = "BabyLogTests.\(UUID().uuidString)"
    let defaults = UserDefaults(suiteName: suite)!
    defer { defaults.removePersistentDomain(forName: suite) }
    return try body(defaults)
}
