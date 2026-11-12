import Foundation
import ZIPFoundation

/**
 Архив экспорта (ADR-0045): первым `diary.csv` по ADR-0009, затем фото в `photos/`. Колонка `photos` в CSV
 связывает строку с файлами фото.
 */
let archiveCSV = "diary.csv"
private let photosDirectory = "photos/"

struct DiaryExport: Equatable, Sendable {
    let events: Int
    let photos: Int
}

extension DiaryExport {
    func describe() -> String {
        var text = "Экспортировано записей: \(events)"
        if photos > 0 { text += ", фото: \(photos)" }
        return text
    }
}

private struct ExportSnapshot: Sendable {
    let children: [Child]
    let events: [any BabyEvent]
    let photoIds: [Int64: [Int64]]
}

private struct EventPhotoFile {
    let photoId: Int64
    let name: String
}

/**
 Весь дневник с фото — архивом в `output`. Дети, записи и список фото читаются одной транзакцией, а сами фото —
 по одному после неё: всё сразу в память не поместится, а долгая транзакция задержала бы запись в дневник.
 Фото, удалённое, пока шёл экспорт, в архив не попадёт — импорт посчитает его не загруженным.
 */
func exportArchive(
    repository: any DiaryRepository,
    output: URL,
    zone: TimeZone = .current
) async throws -> DiaryExport {
    let snapshot = try await repository.inTransaction { transaction in
        ExportSnapshot(
            children: try transaction.allChildren(),
            events: try transaction.allEvents(),
            photoIds: try transaction.photoIds()
        )
    }
    var files = PhotoFiles(zone: zone)
    var childFiles: [Int64: String] = [:]
    for (index, child) in snapshot.children.enumerated() where child.photo != nil {
        childFiles[child.id] = files.child(index: index)
    }
    var eventFiles: [Int64: [EventPhotoFile]] = [:]
    for event in snapshot.events {
        guard let photoIds = snapshot.photoIds[event.id] else { continue }
        eventFiles[event.id] = photoIds.map { EventPhotoFile(photoId: $0, name: files.event(event)) }
    }

    if FileManager.default.fileExists(atPath: output.path) { try FileManager.default.removeItem(at: output) }
    let archive = try Archive(url: output, accessMode: .create)
    let childPhotoNames = childFiles
    let eventPhotoNames = eventFiles.mapValues { $0.map(\.name) }
    let csv = CsvCodec.encode(
        children: snapshot.children,
        events: snapshot.events,
        childPhotos: childPhotoNames,
        eventPhotos: eventPhotoNames,
        zone: zone
    )
    try addEntry(archive, path: archiveCSV, data: Data(csv.utf8), compressionMethod: .deflate)
    var photos = 0
    for child in snapshot.children {
        guard let jpeg = child.photo?.jpeg, let name = childFiles[child.id] else { continue }
        try addEntry(archive, path: name, data: jpeg)
        photos += 1
    }
    for event in snapshot.events {
        for file in eventFiles[event.id] ?? [] {
            guard let jpeg = try await repository.photoJpeg(file.photoId) else { continue }
            try addEntry(
                archive,
                path: file.name,
                data: jpeg,
                modificationDate: Date(timeIntervalSince1970: Double(event.start) / 1000)
            )
            photos += 1
        }
    }
    return DiaryExport(events: snapshot.events.count, photos: photos)
}

/** Данные пишутся частями; JPEG уже сжат, поэтому для него применяется STORED без дополнительного сжатия. */
private func addEntry(
    _ archive: Archive,
    path: String,
    data: Data,
    modificationDate: Date = Date(),
    compressionMethod: CompressionMethod = .none
) throws {
    try archive.addEntry(
        with: path,
        type: .file,
        uncompressedSize: Int64(data.count),
        modificationDate: modificationDate,
        compressionMethod: compressionMethod,
        provider: { position, size in
            let start = Int(position)
            let end = min(start + size, data.count)
            return start < end ? data.subdata(in: start..<end) : Data()
        }
    )
}

/**
 Имена файлов фото — только ASCII: кириллицу в именах понимают не все распаковщики. Фото записи — по её
 началу (`photos/2026-08-15_15-10-00_1.jpg`): в папке они идут по времени; фото ребёнка — по его порядку.
 */
private struct PhotoFiles {
    let zone: TimeZone
    private var used: [String: Int] = [:]

    func child(index: Int) -> String {
        "\(photosDirectory)child_\(index + 1).jpg"
    }

    mutating func event(_ event: any BabyEvent) -> String {
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = zone
        formatter.dateFormat = "yyyy-MM-dd_HH-mm-ss"
        let base = photosDirectory + formatter.string(from: Date(timeIntervalSince1970: Double(event.start) / 1000))
        let number = (used[base] ?? 0) + 1
        used[base] = number
        return "\(base)_\(number).jpg"
    }
}

/**
 Импорт файла `url` (ADR-0045): архива экспорта с фото или CSV (ADR-0009) — архив узнаётся по сигнатуре.
 Архив читается дважды: сначала CSV, затем фото, — копии файла на диске не нужно. Всё — одной транзакцией:
 если архив не дочитался, дневник не меняется.
 */
func importFile(
    repository: any DiaryRepository,
    documents: any DocumentStore,
    url: URL,
    defaultChildId: Int64
) async throws -> ImportResult {
    let file = try documents.read(url) { try readImportFile($0) }
    guard let csvDir = file.csvDir else {
        return try await importCsv(repository: repository, text: file.text, defaultChildId: defaultChildId)
    }
    return try await repository.inTransaction { transaction in
        let importer = CsvImporter(repository: transaction, defaultChildId: defaultChildId)
        try importer.run(file.text)
        if !importer.wantedPhotos.isEmpty {
            try documents.read(url) { source in
                try readArchivePhotos(source, csvDir: csvDir, wanted: importer.wantedPhotos, onPhoto: importer.addPhoto)
            }
        }
        return importer.finish(archive: true)
    }
}

/** Текст CSV из файла импорта; `csvDir` — папка `diary.csv` в архиве (обычно корень, ""), nil — файл не архив. */
struct ImportFile {
    let text: String
    let csvDir: String?
}

/** Первое чтение: CSV как есть или `diary.csv` из архива. Архив, распакованный и собранный заново, — тоже: CSV в папке. */
func readImportFile(_ url: URL) throws -> ImportFile {
    let handle = try FileHandle(forReadingFrom: url)
    let head = try handle.read(upToCount: 4) ?? Data()
    try handle.close()
    if !isZip(head) { return ImportFile(text: try readCsvText(url), csvDir: nil) }
    return try readingArchive {
        let archive = try Archive(url: url, accessMode: .read)
        for entry in archive where entry.type != .directory && isArchiveCsv(entry.path) {
            let text = try readCsvText(archive: archive, entry: entry)
            return ImportFile(text: text, csvDir: String(entry.path.dropLast(archiveCSV.count)))
        }
        throw TransferError("в архиве нет \(archiveCSV) — это не дневник BabyLog")
    }
}

/**
 Второе чтение: фото из `wanted` (пути от папки CSV `csvDir`) — в `onPhoto` по одному. Файл больше
 `EventPhoto.maxBytes` или не JPEG приходит как nil: целиком в память его не читаем.
 */
func readArchivePhotos(
    _ url: URL,
    csvDir: String,
    wanted: Set<String>,
    onPhoto: (String, Data?) throws -> Void
) throws {
    try readingArchive {
        let archive = try Archive(url: url, accessMode: .read)
        for entry in archive {
            if entry.type == .directory || !entry.path.hasPrefix(csvDir) { continue }
            let name = String(entry.path.dropFirst(csvDir.count))
            if wanted.contains(name) { try onPhoto(name, readJpeg(archive: archive, entry: entry)) }
        }
    }
}

private func isZip(_ head: Data) -> Bool {
    let bytes = [UInt8](head)
    guard bytes.count == 4, bytes[0] == 0x50, bytes[1] == 0x4B else { return false }
    // Локальный заголовок файла или пустой архив.
    return (bytes[2] == 3 && bytes[3] == 4) || (bytes[2] == 5 && bytes[3] == 6)
}

/** `diary.csv` в корне или в папке; служебная папка архиватора macOS — не дневник. */
private func isArchiveCsv(_ name: String) -> Bool {
    name == archiveCSV || (name.hasSuffix("/\(archiveCSV)") && !name.hasPrefix("__MACOSX/"))
}

private enum PhotoReadError: Error {
    case tooLarge
}

private func readJpeg(archive: Archive, entry: Entry) throws -> Data? {
    var bytes = Data()
    do {
        try archive.extract(entry, bufferSize: 64 * 1024) { chunk in
            bytes.append(chunk)
            if bytes.count > EventPhoto.maxBytes { throw PhotoReadError.tooLarge }
        }
    } catch PhotoReadError.tooLarge {
        return nil
    }
    let jpeg = bytes.count >= 3 && bytes[bytes.startIndex] == 0xFF
        && bytes[bytes.index(after: bytes.startIndex)] == 0xD8
        && bytes[bytes.index(bytes.startIndex, offsetBy: 2)] == 0xFF
    return jpeg ? bytes : nil
}

private func readCsvText(archive: Archive, entry: Entry) throws -> String {
    var bytes = Data()
    try archive.extract(entry, bufferSize: 64 * 1024) { chunk in
        bytes.append(chunk)
        if bytes.count > maxImportBytes { throw TransferError("файл больше 20 МБ — это не дневник BabyLog") }
    }
    return try csvText(from: bytes)
}

/** Оборванный или испорченный архив — понятное сообщение вместо текста исключения ZIP. */
private func readingArchive<T>(_ block: () throws -> T) throws -> T {
    do {
        return try block()
    } catch let error as TransferError {
        throw error
    } catch {
        throw TransferError("архив повреждён или оборван — сохраните его заново")
    }
}
