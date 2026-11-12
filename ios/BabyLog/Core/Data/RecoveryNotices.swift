import Foundation

/** База была повреждена и восстановлена (ADR-0033) в момент `at`; `backupTime` — время копии, nil — копии не было. */
struct DatabaseRecovery: Hashable, Sendable {
    var at: Int64
    var backupTime: Int64?
}

/**
 Сообщение о восстановлении базы. Ждёт, пока пользователь его не закроет, — и после перезапуска: сбой
 может закрыть приложение раньше, чем сообщение покажут.
 */
protocol RecoveryNotices: Sendable {
    /** Непрочитанное сообщение; nil — нечего показывать. */
    var pending: StoreState<DatabaseRecovery?> { get }

    func report(_ recovery: DatabaseRecovery)
    func dismiss()
}

final class PrefsRecoveryNotices: RecoveryNotices, @unchecked Sendable {
    let pending: StoreState<DatabaseRecovery?>
    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        let at = (defaults.object(forKey: Self.atKey) as? NSNumber)?.int64Value
        let backup = (defaults.object(forKey: Self.backupTimeKey) as? NSNumber)?.int64Value
        pending = StoreState(at.flatMap { at in
            at == Self.none ? nil : DatabaseRecovery(at: at, backupTime: backup == Self.none ? nil : backup)
        })
    }

    func report(_ recovery: DatabaseRecovery) {
        defaults.set(recovery.at, forKey: Self.atKey)
        defaults.set(recovery.backupTime ?? Self.none, forKey: Self.backupTimeKey)
        // Синхронная запись: сообщение не должно потеряться, даже если приложение сейчас закроется.
        defaults.synchronize()
        pending.set(recovery)
    }

    func dismiss() {
        defaults.removeObject(forKey: Self.atKey)
        defaults.removeObject(forKey: Self.backupTimeKey)
        pending.set(nil)
    }

    private static let atKey = "db_recovered_at"
    private static let backupTimeKey = "db_recovered_from_backup"
    private static let none: Int64 = -1
}

final class IgnoredRecoveryNotices: RecoveryNotices, @unchecked Sendable {
    let pending = StoreState<DatabaseRecovery?>(nil)

    func report(_ recovery: DatabaseRecovery) {}
    func dismiss() {}
}
