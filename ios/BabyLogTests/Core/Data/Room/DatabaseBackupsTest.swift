import Foundation
import GRDB
import Testing
@testable import BabyLog

private let backupT1: Int64 = 1_786_000_000_000
private let backupT2: Int64 = 1_786_003_600_000

private final class BackupTime: @unchecked Sendable {
    private let lock = NSLock()
    private var millis: Int64

    init(_ millis: Int64) {
        self.millis = millis
    }

    var value: Int64 {
        lock.lock()
        defer { lock.unlock() }
        return millis
    }

    func advance(_ interval: Int64) {
        lock.lock()
        millis += interval
        lock.unlock()
    }
}

/**
 Копии базы и восстановление после повреждения (ADR-0033): на настоящих файлах SQLite и с настоящей
 GRDB — повреждение ловит SQLite, а не тест.
 */
struct DatabaseBackupsTest {
    private let directory = FileManager.default.temporaryDirectory
        .appendingPathComponent("BabyLogTests-\(UUID().uuidString)", isDirectory: true)
    private var databaseURL: URL { directory.appendingPathComponent(AppDatabase.name) }
    private let hour: Int64 = 60 * 60 * 1000

    @Test func snapshotBecomesVerifiedSingleFileBackupOnceADayAndTwoLatestAreKept() throws {
        defer { try? FileManager.default.removeItem(at: directory) }
        let time = BackupTime(1_786_000_000_000)
        let backups = makeBackups(now: { time.value })
        try writeDiary(rows: 3)
        let first = try #require(try backups.snapshot())
        backups.verify(first)
        #expect(backups.backups().map(\.time) == [time.value])
        #expect(try rowsIn(backups.backups()[0].file) == 3)

        time.advance(23 * hour)
        #expect(try backups.snapshot() == nil)
        time.advance(2 * hour)
        backups.verify(try #require(try backups.snapshot()))
        time.advance(25 * hour)
        backups.verify(try #require(try backups.snapshot()))
        #expect(backups.backups().count == 2)
        #expect(backups.backups().first?.time == time.value)
        #expect(try FileManager.default.contentsOfDirectory(atPath: directory.appendingPathComponent("backups").path).count == 2)
    }

    @Test func damagedSnapshotIsThrownAwayAndOlderBackupStays() throws {
        defer { try? FileManager.default.removeItem(at: directory) }
        let time = BackupTime(1_786_000_000_000)
        let backups = makeBackups(now: { time.value })
        try writeDiary(rows: 1)
        backups.verify(try #require(try backups.snapshot()))
        let good = try #require(backups.backups().first)

        time.advance(25 * hour)
        let snapshot = try #require(try backups.snapshot())
        try Data(repeating: 0x5A, count: 8192).write(to: snapshot.appendingPathComponent(AppDatabase.name))
        backups.verify(snapshot)

        #expect(backups.backups() == [good])
        #expect(!FileManager.default.fileExists(atPath: snapshot.path))
    }

    @Test func corruptedDatabaseIsRestoredFromBackupInsteadOfBeingDeleted() async throws {
        defer { try? FileManager.default.removeItem(at: directory) }
        let notices = FakeRecoveryNotices()
        let background = DispatchQueue(label: "BabyLogTests.DatabaseBackups")
        let now: Int64 = 1_786_000_000_000
        let first = Diaper(childId: 1, start: backupT1, kind: .wet)
        try await useApp(notices: notices, background: background, now: { now }) { repository in
            _ = try await repository.addEvent(first)
        }
        try await useApp(notices: notices, background: background, now: { now }) { repository in
            _ = try await repository.addEvent(Diaper(childId: 1, start: backupT2, kind: .dirty))
        }
        let backupTime = try #require(makeBackups(now: { now }).backups().first?.time)

        try corrupt(databaseURL)
        try await useApp(notices: notices, background: background, now: { now }) { repository in
            let children = try await repository.allChildren()
            let events = try await repository.allEvents()
            #expect(children.map(\.name) == [Child.defaultName])
            #expect(events.map(\.start) == [backupT1])
        }
        let pending = try #require(notices.pending.value)
        #expect(pending == DatabaseRecovery(at: now, backupTime: backupTime))
        let corrupted = directory.appendingPathComponent("db-corrupted")
        let enumerator = FileManager.default.enumerator(at: corrupted, includingPropertiesForKeys: nil)
        #expect(enumerator?.allObjects.contains { ($0 as? URL)?.lastPathComponent == AppDatabase.name } == true)
    }

    @Test func withoutBackupDiaryStartsAnewAndMessageSaysSo() async throws {
        defer { try? FileManager.default.removeItem(at: directory) }
        let notices = FakeRecoveryNotices()
        let background = DispatchQueue(label: "BabyLogTests.DatabaseBackups")
        let now: Int64 = 1_786_000_000_000
        try await useApp(notices: notices, background: background, now: { now }) { repository in
            _ = try await repository.addEvent(Diaper(childId: 1, start: backupT1, kind: .wet))
        }
        try? FileManager.default.removeItem(at: directory.appendingPathComponent("backups"))

        try corrupt(databaseURL)
        try await useApp(notices: notices, background: background, now: { now }) { repository in
            let events = try await repository.allEvents()
            #expect(events.isEmpty)
        }
        #expect(notices.pending.value?.backupTime == nil)
        #expect(notices.pending.value != nil)
    }

    @Test func databasePhotosAndBackupsAreExcludedFromSystemBackups() async throws {
        defer { try? FileManager.default.removeItem(at: directory) }
        let notices = FakeRecoveryNotices()
        let background = DispatchQueue(label: "BabyLogTests.DatabaseBackups")
        let now: Int64 = 1_786_000_000_000
        try await useApp(notices: notices, background: background, now: { now }) { _ in }
        #expect(try isExcluded(databaseURL))
        #expect(try isExcluded(directory.appendingPathComponent("photos")))

        try await useApp(notices: notices, background: background, now: { now }) { _ in }
        let backup = try #require(makeBackups(now: { now }).backups().first)
        #expect(try isExcluded(backup.file))
    }

    private func makeBackups(now: @escaping @Sendable () -> Int64) -> DatabaseBackups {
        DatabaseBackups(database: databaseURL, notices: FakeRecoveryNotices(), now: now)
    }

    private func useApp(
        notices: any RecoveryNotices,
        background: DispatchQueue,
        now: @escaping @Sendable () -> Int64,
        block: (RoomDiaryRepository) async throws -> Void
    ) async throws {
        let database = try AppDatabase.build(at: databaseURL, notices: notices, background: background, now: now)
        do {
            try await block(RoomDiaryRepository(database: database))
            try database.close()
            background.sync {}
        } catch {
            try? database.close()
            throw error
        }
    }

    private func writeDiary(rows: Int) throws {
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let queue = try DatabaseQueue(path: databaseURL.path)
        try queue.write { db in
            try db.execute(sql: "CREATE TABLE notes (id INTEGER PRIMARY KEY, text TEXT NOT NULL)")
            for index in 0..<rows {
                try db.execute(sql: "INSERT INTO notes (text) VALUES (?)", arguments: ["запись \(index)"])
            }
        }
        try queue.close()
    }

    private func rowsIn(_ file: URL) throws -> Int {
        let queue = try DatabaseQueue(path: file.path)
        defer { try? queue.close() }
        return try queue.read { try Int.fetchOne($0, sql: "SELECT COUNT(*) FROM notes") ?? 0 }
    }

    private func corrupt(_ file: URL) throws {
        let size = try file.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? 0
        try Data(repeating: 0x5A, count: size).write(to: file)
        for suffix in ["-wal", "-shm"] {
            try? FileManager.default.removeItem(at: URL(fileURLWithPath: file.path + suffix))
        }
    }

    private func isExcluded(_ url: URL) throws -> Bool {
        try url.resourceValues(forKeys: [.isExcludedFromBackupKey]).isExcludedFromBackup == true
    }
}
