import Foundation
import Testing
@testable import BabyLog

/** Кэш фото: папки отправок «Поделиться» живут сутки, свежие не трогаются. */
struct PhotoCacheTest {
    private let day: Int64 = 24 * 60 * 60 * 1000

    @Test func staleShareFoldersAreRemovedWithTheirPhotosFreshOnesStay() throws {
        let cache = FileManager.default.temporaryDirectory.appendingPathComponent("PhotoCacheTest-\(UUID().uuidString)", isDirectory: true)
        defer { try? FileManager.default.removeItem(at: cache) }
        let now = 10 * day
        let old = try shareFolder(cache: cache, name: "old", modified: now - 2 * day)
        let fresh = try shareFolder(cache: cache, name: "fresh", modified: now - 60_000)

        sweepPhotoCache(now: now, cacheDirectory: cache)

        #expect(!FileManager.default.fileExists(atPath: old.path))
        #expect(FileManager.default.fileExists(atPath: fresh.appendingPathComponent("photo-1.jpg").path))
    }

    @Test func shareGetsTextAndSeparateJpegFiles() throws {
        let cache = FileManager.default.temporaryDirectory.appendingPathComponent("PhotoCacheTest-\(UUID().uuidString)", isDirectory: true)
        defer { try? FileManager.default.removeItem(at: cache) }
        let share = try shareWithPhotos(text: "Подпись", jpegs: [Data([1, 2]), Data([3])], cacheDirectory: cache)
        #expect(share.text == "Подпись")
        #expect(share.items.map(\.lastPathComponent) == ["photo-1.jpg", "photo-2.jpg"])
        #expect(try Data(contentsOf: share.items[0]) == Data([1, 2]))
        #expect(try Data(contentsOf: share.items[1]) == Data([3]))
    }

    private func shareFolder(cache: URL, name: String, modified: Int64) throws -> URL {
        let directory = cache.appendingPathComponent("share/\(name)", isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        try Data([1, 2, 3]).write(to: directory.appendingPathComponent("photo-1.jpg"))
        let date = Date(timeIntervalSince1970: Double(modified) / 1000)
        try FileManager.default.setAttributes([.modificationDate: date], ofItemAtPath: directory.path)
        return directory
    }
}
