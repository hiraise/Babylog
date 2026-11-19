import Foundation
import GRDB

/** До первого открытия делает снимок; при `SQLITE_CORRUPT`/`SQLITE_NOTADB` восстанавливает копию, не удаляя дневник. */
final class SafeOpenHelperFactory: @unchecked Sendable {
    private let backups: DatabaseBackups
    private let background: DispatchQueue
    private let lock = NSLock()
    private var snapshotTaken = false

    init(backups: DatabaseBackups, background: DispatchQueue) {
        self.backups = backups
        self.background = background
    }

    func beforeOpen() {
        lock.lock()
        defer { lock.unlock() }
        guard !snapshotTaken else { return }
        snapshotTaken = true
        // Копия — не повод не открыть дневник: сбой снимка пропускаем.
        if let directory = try? backups.snapshot() {
            background.async { self.backups.verify(directory) }
        }
    }

    func open<T>(_ operation: () throws -> T) throws -> T {
        do {
            return try operation()
        } catch let error as DatabaseError where error.resultCode.primaryResultCode == .SQLITE_CORRUPT
            || error.resultCode.primaryResultCode == .SQLITE_NOTADB {
            try backups.recover()
            return try operation()
        }
    }
}
