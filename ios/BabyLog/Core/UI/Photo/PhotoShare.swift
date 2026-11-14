import SwiftUI
import UIKit

/**
 Фото записи из файла `source` (снимок или копия из галереи) — JPEG не больше `EventPhoto.maxSide`
 по длинной стороне, в новом файле кэша; путь к нему. Исходник не трогает.
 */
func prepareEventPhoto(source: String) throws -> String {
    let bitmap = try loadUpright(path: source, maxSide: EventPhoto.maxSide * 2).scaledToFit(EventPhoto.maxSide)
    let file = newPhotoCacheFile()
    try fittingJpeg(bitmap: bitmap).write(to: file)
    return file.path
}

/**
 JPEG не больше `maxBytes`: строка базы с фото должна уместиться в окно курсора SQLite (2 МБ), иначе запись
 не прочитается. Очень детальное фото сжимается сильнее, а если не влезло и так — уменьшается на четверть.
 */
func fittingJpeg(bitmap: UIImage, maxBytes: Int = EventPhoto.maxBytes) -> Data {
    var current = bitmap
    while true {
        for quality in jpegQualities {
            let data = current.toJpeg(quality: quality)
            if data.count <= maxBytes { return data }
        }
        current = current.resizedPixels(width: max(1, current.pixelWidth * 3 / 4), height: max(1, current.pixelHeight * 3 / 4))
    }
}

/** Текст и файлы одной отправки. Папка у каждой отправки своя: получатель может читать вложения не сразу. */
struct PhotoShare: Equatable {
    let text: String
    let items: [URL]
}

/**
 Данные для системного `ShareLink`: `text` и фото `jpegs` уходят только по нажатию и только в приложение,
 которое выберет пользователь. Фото копируются в `cache/share/`; старые копии удаляет `sweepPhotoCache`.
 */
func shareWithPhotos(text: String, jpegs: [Data], cacheDirectory: URL? = nil) throws -> PhotoShare {
    let directory = photoCacheRoot(cacheDirectory)
        .appendingPathComponent(shareCacheDirectory, isDirectory: true)
        .appendingPathComponent(UUID().uuidString, isDirectory: true)
    try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
    let items = try jpegs.enumerated().map { index, jpeg in
        let file = directory.appendingPathComponent("photo-\(index + 1).jpg")
        try jpeg.write(to: file)
        return file
    }
    return PhotoShare(text: text, items: items)
}

/** Системное «Поделиться» подписью и всеми фото формы; пока копии готовятся, действие недоступно. */
struct PhotoShareLink: View {
    @Environment(\.babyLogTheme) private var theme
    let text: String
    let jpegs: [Data]
    let enabled: Bool
    @State private var share: PhotoShare?

    var body: some View {
        Group {
            if let share {
                if share.items.isEmpty {
                    ShareLink(item: text) { label }
                } else {
                    ShareLink(items: share.items, message: Text(text)) { label }
                }
            } else {
                label.disabled(true)
            }
        }
        .disabled(!enabled)
        .task(id: jpegs) {
            share = nil
            share = try? shareWithPhotos(text: text, jpegs: jpegs)
        }
    }

    private var label: some View {
        Text("Поделиться")
            .foregroundStyle(theme.colors.primary)
            .appTextStyle(theme.typography.labelLarge)
    }
}

/**
 Удаляет из кэша фото старше суток: снимки и копии, которые не дошли до базы (форму закрыли, процесс
 убит), и копии для «Поделиться». Вызывается при запуске приложения.
 */
func sweepPhotoCache(now: Int64, cacheDirectory: URL? = nil) {
    let root = photoCacheRoot(cacheDirectory)
    for name in [photosCacheDirectory, shareCacheDirectory] {
        let directory = root.appendingPathComponent(name, isDirectory: true)
        guard let files = try? FileManager.default.contentsOfDirectory(at: directory, includingPropertiesForKeys: [.contentModificationDateKey]) else { continue }
        for file in files {
            guard let modified = try? file.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate else { continue }
            let modifiedMillis = Int64(modified.timeIntervalSince1970 * 1000)
            if now - modifiedMillis > stalePhotoMillis { try? FileManager.default.removeItem(at: file) }
        }
    }
}

/** Качество JPEG по очереди: первое, которое уложилось в `EventPhoto.maxBytes`. */
private let jpegQualities = [85, 70, 50]
private let stalePhotoMillis: Int64 = 24 * 60 * 60 * 1000
private let shareCacheDirectory = "share"
let photosCacheDirectory = "photos"
