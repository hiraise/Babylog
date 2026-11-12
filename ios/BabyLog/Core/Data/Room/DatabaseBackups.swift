import Foundation
import GRDB

struct DatabaseRecovery: Hashable, Sendable {
    var at: Int64
    var backupTime: Int64?
}

protocol RecoveryNotices: Sendable {
    func report(_ recovery: DatabaseRecovery)
}

struct IgnoredRecoveryNotices: RecoveryNotices {
    func report(_ recovery: DatabaseRecovery) {}
}

/** Проверенная копия базы `file`, снятая в момент `time`. */
struct DatabaseBackup: Hashable, Sendable {
    var file: URL
    var time: Int64
}

/** Ежедневные копии и восстановление после повреждения (ADR-0033, ADR-0050). */
final class DatabaseBackups: @unchecked Sendable {
    private let database: URL
    private let backupDir: URL
    private let corruptedDir: URL
    private let notices: any RecoveryNotices
    private let now: @Sendable () -> Int64
    private let lock = NSLock()

    init(database: URL, notices: any RecoveryNotices, now: @escaping @Sendable () -> Int64) {
        self.database = database
        let root = database.deletingLastPathComponent()
        backupDir = root.appendingPathComponent("backups", isDirectory: true)
        corruptedDir = root.appendingPathComponent("db-corrupted", isDirectory: true)
        self.notices = notices
        self.now = now
    }

    /** Проверенные копии, свежие первыми. */
    func backups() -> [DatabaseBackup] {
        let files = (try? FileManager.default.contentsOfDirectory(at: backupDir, includingPropertiesForKeys: nil)) ?? []
        return files.compactMap { file in
            guard let match = try? NSRegularExpression(pattern: "^babylog-(\\d+)\\.db$")
                .firstMatch(in: file.lastPathComponent, range: NSRange(file.lastPathComponent.startIndex..., in: file.lastPathComponent)),
                let range = Range(match.range(at: 1), in: file.lastPathComponent),
                let time = Int64(file.lastPathComponent[range]) else { return nil }
            return DatabaseBackup(file: file, time: time)
        }.sorted { $0.time > $1.time }
    }

    /** Снимок файлов до открытия базы; nil — копия не нужна или для неё нет места. */
    func snapshot() throws -> URL? {
        lock.lock()
        defer { lock.unlock() }
        let manager = FileManager.default
        guard manager.fileExists(atPath: database.path) else { return nil }
        let time = now()
        if let last = backups().first, time - last.time < Self.backupInterval { return nil }
        let files = databaseFiles().filter { manager.fileExists(atPath: $0.path) && $0.lastPathComponent.hasSuffix("-shm") == false }
        try manager.createDirectory(at: backupDir, withIntermediateDirectories: true)
        let size = files.reduce(0) { total, file in
            total + (Int64((try? file.resourceValues(forKeys: [.fileSizeKey]).fileSize) ?? 0))
        }
        let capacity = try backupDir.resourceValues(forKeys: [.volumeAvailableCapacityForImportantUsageKey])
            .volumeAvailableCapacityForImportantUsage ?? Int64.max
        guard capacity >= size + Self.freeSpaceReserve else { return nil }
        let directory = backupDir.appendingPathComponent("snapshot-\(time)", isDirectory: true)
        try manager.createDirectory(at: directory, withIntermediateDirectories: true)
        for file in files {
            let target = directory.appendingPathComponent(file.lastPathComponent)
            if manager.fileExists(atPath: target.path) { try manager.removeItem(at: target) }
            try manager.copyItem(at: file, to: target)
        }
        excludeFromBackup(directory)
        return directory
    }

    /** Снимок → один проверенный файл; испорченный снимок удаляется, прежние копии остаются. */
    func verify(_ directory: URL) {
        lock.lock()
        defer { lock.unlock() }
        guard let time = Int64(directory.lastPathComponent.replacingOccurrences(of: "snapshot-", with: "")) else { return }
        let file = directory.appendingPathComponent(database.lastPathComponent)
        let healthy = (try? checkAndMerge(file)) ?? false
        let manager = FileManager.default
        if healthy {
            let target = backupDir.appendingPathComponent("babylog-\(time).db")
            try? manager.removeItem(at: target)
            if (try? manager.moveItem(at: file, to: target)) != nil {
                excludeFromBackup(target)
                for backup in backups().dropFirst(Self.keepBackups) { try? manager.removeItem(at: backup.file) }
            }
        }
        try? manager.removeItem(at: directory)
    }

    /** Откладывает повреждённые файлы и восстанавливает свежую копию. */
    func recover() throws {
        lock.lock()
        defer { lock.unlock() }
        let manager = FileManager.default
        let time = now()
        try? manager.removeItem(at: corruptedDir)
        let aside = corruptedDir.appendingPathComponent(String(time), isDirectory: true)
        try manager.createDirectory(at: aside, withIntermediateDirectories: true)
        for file in databaseFiles() where manager.fileExists(atPath: file.path) {
            let target = aside.appendingPathComponent(file.lastPathComponent)
            do {
                try manager.moveItem(at: file, to: target)
            } catch {
                try manager.copyItem(at: file, to: target)
                try manager.removeItem(at: file)
            }
        }
        excludeFromBackup(corruptedDir)
        let backup = backups().first
        if let backup {
            try manager.copyItem(at: backup.file, to: database)
            excludeFromBackup(database)
        }
        notices.report(DatabaseRecovery(at: time, backupTime: backup?.time))
    }

    private func checkAndMerge(_ file: URL) throws -> Bool {
        guard FileManager.default.fileExists(atPath: file.path) else { return false }
        let queue = try DatabaseQueue(path: file.path)
        defer { try? queue.close() }
        return try queue.writeWithoutTransaction { db in
            try db.execute(sql: "PRAGMA journal_mode = DELETE")
            return try String.fetchOne(db, sql: "PRAGMA quick_check") == "ok"
        }
    }

    private func databaseFiles() -> [URL] {
        ["", "-wal", "-shm", "-journal"].map { URL(fileURLWithPath: database.path + $0) }
    }

    private static let backupInterval: Int64 = 24 * 60 * 60 * 1000
    private static let keepBackups = 2
    private static let freeSpaceReserve: Int64 = 100 * 1024 * 1024
}

func excludeFromBackup(_ url: URL) {
    guard FileManager.default.fileExists(atPath: url.path) else { return }
    var values = URLResourceValues()
    values.isExcludedFromBackup = true
    try? (url as NSURL).setResourceValue(values.isExcludedFromBackup, forKey: .isExcludedFromBackupKey)
}
