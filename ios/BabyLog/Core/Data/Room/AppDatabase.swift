import Foundation
import GRDB

/** База дневника. Схема меняется только новой миграцией GRDB вслед за Room (ADR-0050). */
final class AppDatabase: @unchecked Sendable {
    static let name = "babylog.db"
    static let version = 7

    let queue: DatabaseQueue

    private init(queue: DatabaseQueue) {
        self.queue = queue
    }

    /** Открывает базу приложения с ежедневной копией и восстановлением из неё. */
    static func build(
        at databaseURL: URL? = nil,
        notices: any RecoveryNotices = IgnoredRecoveryNotices(),
        background: DispatchQueue = .global(),
        now: @escaping @Sendable () -> Int64 = { Int64(Date().timeIntervalSince1970 * 1000) }
    ) throws -> AppDatabase {
        let url = databaseURL ?? FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent(name)
        let root = url.deletingLastPathComponent()
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        let photos = root.appendingPathComponent("photos", isDirectory: true)
        try FileManager.default.createDirectory(at: photos, withIntermediateDirectories: true)
        excludeFromBackup(photos)
        let backups = DatabaseBackups(database: url, notices: notices, now: now)
        let opener = SafeOpenHelperFactory(backups: backups, background: background)
        opener.beforeOpen()
        let database = try opener.open { try make(path: url.path, createFirstChild: true) }
        for suffix in ["", "-wal", "-shm", "-journal"] {
            excludeFromBackup(URL(fileURLWithPath: url.path + suffix))
        }
        excludeFromBackup(url.deletingLastPathComponent().appendingPathComponent("backups"))
        return database
    }

    /** База только в памяти и без первого ребёнка: данные для гайдов кладёт сам гайд. */
    static func inMemory() throws -> AppDatabase {
        try make(path: nil, createFirstChild: false)
    }

    private static func make(path: String?, createFirstChild: Bool) throws -> AppDatabase {
        var configuration = Configuration()
        configuration.foreignKeysEnabled = true
        let queue = try path.map { try DatabaseQueue(path: $0, configuration: configuration) } ?? DatabaseQueue(configuration: configuration)
        try diaryMigrator(createFirstChild: createFirstChild).migrate(queue)
        return AppDatabase(queue: queue)
    }

    func close() throws {
        try queue.close()
    }
}
