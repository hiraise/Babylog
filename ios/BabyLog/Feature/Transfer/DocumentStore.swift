import Foundation
import SwiftUI
import UniformTypeIdentifiers

/**
 Чтение и запись файла, который пользователь выбрал в системных `fileImporter` / `fileExporter`.
 ZIPFoundation работает с файлом по URL, поэтому архив с фото не копируется и целиком не загружается в память
 (ADR-0045). Интерфейс отделяет логику импорта от security-scoped URL; в тестах его заменяет фейк.
 */
protocol DocumentStore: Sendable {
    /** Открывает файл `url` на чтение — каждый раз с начала; security-scoped доступ закрывается после `block`. */
    func read<T>(_ url: URL, block: (URL) throws -> T) throws -> T

    /** Открывает файл `url` на запись с нуля; security-scoped доступ закрывается после `block`. */
    func write<T>(_ url: URL, block: (URL) async throws -> T) async throws -> T
}

struct URLDocumentStore: DocumentStore {
    func read<T>(_ url: URL, block: (URL) throws -> T) throws -> T {
        try withAccess(to: url, block: block)
    }

    func write<T>(_ url: URL, block: (URL) async throws -> T) async throws -> T {
        let accessing = url.startAccessingSecurityScopedResource()
        defer { if accessing { url.stopAccessingSecurityScopedResource() } }
        return try await block(url)
    }

    private func withAccess<T>(to url: URL, block: (URL) throws -> T) throws -> T {
        let accessing = url.startAccessingSecurityScopedResource()
        defer { if accessing { url.stopAccessingSecurityScopedResource() } }
        return try block(url)
    }
}

/** Пустой документ для системного `fileExporter`; архив записывается в выбранный URL после закрытия диалога. */
private struct DiaryArchiveDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.zip] }

    init() {}

    init(configuration: ReadConfiguration) throws {}

    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        FileWrapper(regularFileWithContents: Data())
    }
}

extension View {
    /** Системный выбор места экспорта; выбранный URL передаётся в `DocumentStore.write`. */
    func diaryFileExporter(
        isPresented: Binding<Bool>,
        defaultFilename: String,
        onCompletion: @escaping (Result<URL, Error>) -> Void
    ) -> some View {
        fileExporter(
            isPresented: isPresented,
            document: DiaryArchiveDocument(),
            contentTypes: [.zip],
            defaultFilename: defaultFilename,
            onCompletion: onCompletion
        )
    }

    /** Системный выбор архива или CSV; тип не ограничиваем MIME-типом CSV, как на Android. */
    func diaryFileImporter(
        isPresented: Binding<Bool>,
        onCompletion: @escaping (Result<URL, Error>) -> Void
    ) -> some View {
        fileImporter(isPresented: isPresented, allowedContentTypes: [.data], allowsMultipleSelection: false) { result in
            switch result {
            case let .success(urls):
                if let url = urls.first {
                    onCompletion(.success(url))
                } else {
                    onCompletion(.failure(TransferError("файл не выбран")))
                }
            case let .failure(error):
                onCompletion(.failure(error))
            }
        }
    }
}

/** Больше не бывает: дневник за годы — несколько мегабайт CSV. */
let maxImportBytes = 20 * 1024 * 1024

/**
 Текст CSV из `input`. Выбор файла пропускает любой тип: у CSV в разных приложениях разный MIME-тип.
 Поэтому читаем не больше `maxImportBytes` — видео, выбранное по ошибке, не должно кончиться нехваткой
 памяти, — а двоичный файл (нулевой байт в тексте) отклоняем сразу.
 */
func readCsvText(_ input: InputStream) throws -> String {
    input.open()
    defer { input.close() }
    var bytes = Data()
    var buffer = [UInt8](repeating: 0, count: 64 * 1024)
    while true {
        let read = input.read(&buffer, maxLength: buffer.count)
        if read < 0 {
            if let error = input.streamError { throw error }
            break
        }
        if read == 0 { break }
        bytes.append(contentsOf: buffer[0..<read])
        if bytes.count > maxImportBytes { throw TransferError("файл больше 20 МБ — это не дневник BabyLog") }
    }
    return try csvText(from: bytes)
}

func readCsvText(_ url: URL) throws -> String {
    guard let input = InputStream(url: url) else { throw TransferError("Не удалось открыть файл") }
    return try readCsvText(input)
}

func csvText(from bytes: Data) throws -> String {
    if bytes.contains(0) { throw TransferError("это не CSV-файл") }
    return String(decoding: bytes, as: UTF8.self)
}
