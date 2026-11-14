import Observation
import SwiftUI

/**
 Фото в листе записи — у подгузника и заметки: что сейчас в форме, сколько ещё
 готовится, ошибка, открыт ли лист «Фото» и какое фото смотрят крупно.
 В базу фото попадают по «Сохранить» (`takeEdit`); закрытый без сохранения лист выбрасывает новые.
 */
@MainActor @Observable final class FormPhotosState {
    var photos: [FormPhoto]
    var error: String?
    var processing = 0
    var menuOpen = false
    var viewing: Int?
    @ObservationIgnored private let saved: [Int64: Data]

    init(initialPhotos: [EventPhoto]) {
        photos = initialPhotos.map { .saved(id: $0.id) }
        saved = Dictionary(uniqueKeysWithValues: initialPhotos.map { ($0.id, $0.jpeg) })
    }

    /** Сколько фото ещё можно добавить. */
    var remaining: Int { EventPhoto.maxPerEvent - photos.count }

    /** Новые фото ещё уменьшаются: «Сохранить» и «Поделиться» ждут. */
    var busy: Bool { processing > 0 }

    func jpegOf(photo: FormPhoto) -> Data? {
        switch photo {
        case let .saved(id): saved[id]
        case let .pending(path): readPhoto(path: path)
        }
    }

    /** Байты всех фото формы — для «Поделиться». */
    func jpegs() -> [Data] { photos.compactMap(jpegOf) }

    /** Фото для сохранения вместе с записью; файлы новых фото после этого не нужны. */
    func takeEdit() -> PhotoEdit {
        let edit = photos.toEdit(read: readPhoto)
        discardPending()
        return edit
    }

    /** Новые фото, не дошедшие до базы, — не храним. */
    func discardPending() {
        for photo in photos {
            if case let .pending(path) = photo { deletePhotoFile(path: path) }
        }
    }
}

/** «Фото:» и строка миниатюр с «+»; ошибка — под ней. Кладётся в содержимое листа записи. */
struct PhotosField: View {
    @Environment(\.babyLogTheme) private var theme
    let state: FormPhotosState
    let title: String

    init(state: FormPhotosState, title: String = "Фото:") {
        self.state = state
        self.title = title
    }

    var body: some View {
        VStack(alignment: .leading, spacing: theme.spacing.s) {
            Text(title).appTextStyle(theme.typography.titleSmall)
            PhotoStrip(
                photos: state.photos,
                jpegOf: state.jpegOf,
                canAdd: state.remaining > 0,
                processing: state.busy,
                onAdd: { state.menuOpen = true },
                onOpen: { state.viewing = $0 }
            )
            if let error = state.error {
                Text(error)
                    .appTextStyle(theme.typography.bodySmall)
                    .foregroundStyle(theme.colors.error)
            }
        }
    }
}

/**
 Лист «Фото» (камера или галерея) и просмотр фото показываются из содержимого уже открытого листа
 записи: на iOS это даёт им активную иерархию presentation. Выбор фото живёт, пока открыт лист записи, —
 лист «Фото» закрывается раньше, чем камера вернёт снимок.
 */
@MainActor
struct PhotoSheets: View {
    @Bindable var state: FormPhotosState
    @State private var source: PhotoPickerSource?
    @State private var processingTask: Task<Void, Never>?

    var body: some View {
        PhotoPicker(source: $source, maxItems: max(state.remaining, 1), onPicked: receive, onError: { state.error = $0 })
        if state.menuOpen {
            PhotoSourceSheet(
                isPresented: $state.menuOpen,
                onCamera: {
                    state.menuOpen = false
                    source = .camera
                },
                onGallery: {
                    state.menuOpen = false
                    source = .gallery
                },
                onDismiss: { state.menuOpen = false }
            )
        }
        if let viewing = state.viewing, state.photos.indices.contains(viewing) {
            let photo = state.photos[viewing]
            PhotoViewSheet(
                isPresented: Binding(get: { state.viewing != nil }, set: { if !$0 { state.viewing = nil } }),
                jpeg: state.jpegOf(photo: photo),
                onRemove: {
                    if case let .pending(path) = photo { deletePhotoFile(path: path) }
                    state.photos.removeAll { $0 == photo }
                    state.viewing = nil
                },
                onDismiss: { state.viewing = nil }
            )
        }
        Color.clear
            .frame(width: 0, height: 0)
            .onDisappear { processingTask?.cancel() }
    }

    private func receive(paths: [String]) {
        state.error = nil
        state.processing += paths.count
        processingTask = Task {
            let prepared = await Task.detached(priority: .userInitiated) {
                paths.compactMap { path in
                    defer { deletePhotoFile(path: path) }
                    return try? prepareEventPhoto(source: path)
                }
            }.value
            state.processing -= paths.count
            if Task.isCancelled {
                prepared.forEach { deletePhotoFile(path: $0) }
                return
            }
            if prepared.count < paths.count { state.error = "Не удалось открыть фото" }
            state.photos += prepared.map { .pending(path: $0) }
        }
    }
}

private func readPhoto(path: String) -> Data? {
    try? Data(contentsOf: URL(fileURLWithPath: path))
}
