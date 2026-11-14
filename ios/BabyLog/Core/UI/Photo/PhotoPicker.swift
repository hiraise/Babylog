import PhotosUI
import SwiftUI
import UIKit
import UniformTypeIdentifiers

enum PhotoPickerSource {
    case camera
    case gallery
}

/**
 Камера и галерея без разрешений на хранилище: снимок делает системный `UIImagePickerController`,
 фото выбирается системным `PhotosPicker` и копируется в кэш. Файл в кэше, а не ссылка из пикера:
 обработка должна пережить поворот экрана и перезапуск процесса.

 Держи picker рядом с экраном, который пережидает закрытие листа-меню. `maxItems` больше 1 —
 в галерее можно выбрать несколько фото; лишние отбрасываются с сообщением в `onError`.
 `onPicked` получает пути; файлы после обработки удаляй `deletePhotoFile`.
 */
@MainActor
struct PhotoPicker: View {
    @Binding var source: PhotoPickerSource?
    let maxItems: Int
    let onPicked: ([String]) -> Void
    let onError: (String) -> Void
    @State private var selections: [PhotosPickerItem] = []
    @State private var receiveTask: Task<Void, Never>?

    var body: some View {
        Group {
            Color.clear
                .frame(width: 0, height: 0)
                .onDisappear { receiveTask?.cancel() }
            if source == .gallery {
                Color.clear
                    .photosPicker(
                        isPresented: galleryPresented,
                        selection: $selections,
                        maxSelectionCount: maxItems == 1 ? 1 : max(maxItems, 2),
                        matching: .images
                    )
            } else if source == .camera {
                Color.clear
                    .sheet(isPresented: cameraPresented) {
                        CameraPhotoPicker(onPicked: onPicked, onError: onError, onDismiss: { source = nil })
                    }
            } else {
                EmptyView()
            }
        }
        .onChange(of: source) { _, newSource in
            if newSource == .gallery { selections = [] }
            if newSource == .camera && !UIImagePickerController.isSourceTypeAvailable(.camera) {
                source = nil
                onError("На телефоне нет приложения камеры")
            }
        }
        .onChange(of: selections) { _, items in receive(items) }
    }

    private var galleryPresented: Binding<Bool> {
        Binding(get: { source == .gallery }, set: { if !$0 { source = nil } })
    }

    private var cameraPresented: Binding<Bool> {
        Binding(get: { source == .camera && UIImagePickerController.isSourceTypeAvailable(.camera) }, set: { if !$0 { source = nil } })
    }

    private func receive(_ items: [PhotosPickerItem]) {
        guard !items.isEmpty else { return }
        selections = []
        source = nil
        let limit = max(maxItems, 1)
        if items.count > limit { onError("Можно добавить ещё \(limit) фото — остальные не добавлены") }
        receiveTask = Task {
            var files: [String] = []
            for item in items.prefix(limit) {
                if Task.isCancelled { break }
                if let file = try? await copyToCache(item) { files.append(file) }
            }
            if Task.isCancelled {
                files.forEach { deletePhotoFile(path: $0) }
                return
            }
            if files.count < min(items.count, limit) { onError("Не удалось открыть фото") }
            if !files.isEmpty { onPicked(files) }
        }
    }
}

/** Удаляет файл фото из кэша: оригиналы не храним. */
func deletePhotoFile(path: String) {
    try? FileManager.default.removeItem(atPath: path)
}

/** Новый файл в `cache/photos/` для обработанного фото, ещё не сохранённого в базу. */
func newPhotoCacheFile() -> URL {
    photoCacheFile("pending-\(UUID().uuidString).jpg")
}

func photoCacheFile(_ name: String, cacheDirectory: URL? = nil) -> URL {
    let directory = photoCacheRoot(cacheDirectory).appendingPathComponent(photosCacheDirectory, isDirectory: true)
    try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
    return directory.appendingPathComponent(name)
}

func photoCacheRoot(_ cacheDirectory: URL? = nil) -> URL {
    cacheDirectory ?? FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
}

private func copyToCache(_ item: PhotosPickerItem) async throws -> String {
    guard let photo = try await item.loadTransferable(type: CachedPhoto.self) else { throw PhotoBitmapError.notImage }
    return photo.path
}

private struct CachedPhoto: Transferable {
    let path: String

    static var transferRepresentation: some TransferRepresentation {
        FileRepresentation(contentType: .image, exporting: { photo in
            SentTransferredFile(URL(fileURLWithPath: photo.path))
        }, importing: { received in
            let file = photoCacheFile("gallery-\(UUID().uuidString)")
            try FileManager.default.copyItem(at: received.file, to: file)
            return CachedPhoto(path: file.path)
        })
    }
}

private struct CameraPhotoPicker: UIViewControllerRepresentable {
    let onPicked: ([String]) -> Void
    let onError: (String) -> Void
    let onDismiss: () -> Void

    func makeUIViewController(context: Context) -> UIImagePickerController {
        deletePhotoFile(path: photoCacheFile(cameraFileName).path)
        let controller = UIImagePickerController()
        controller.sourceType = .camera
        controller.cameraCaptureMode = .photo
        controller.delegate = context.coordinator
        return controller
    }

    func updateUIViewController(_ controller: UIImagePickerController, context: Context) {}

    func makeCoordinator() -> Coordinator {
        Coordinator(onPicked: onPicked, onError: onError, onDismiss: onDismiss)
    }

    final class Coordinator: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
        private let onPicked: ([String]) -> Void
        private let onError: (String) -> Void
        private let onDismiss: () -> Void

        init(onPicked: @escaping ([String]) -> Void, onError: @escaping (String) -> Void, onDismiss: @escaping () -> Void) {
            self.onPicked = onPicked
            self.onError = onError
            self.onDismiss = onDismiss
        }

        func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
            let target = photoCacheFile(cameraFileName)
            var result: Result<String, Error>
            do {
                if let source = info[.imageURL] as? URL {
                    try FileManager.default.copyItem(at: source, to: target)
                } else if let image = info[.originalImage] as? UIImage {
                    try image.toJpeg(quality: 95).write(to: target)
                } else {
                    throw PhotoBitmapError.notImage
                }
                result = .success(target.path)
            } catch {
                result = .failure(error)
            }
            onDismiss()
            picker.dismiss(animated: true) {
                switch result {
                case let .success(path): self.onPicked([path])
                case .failure: self.onError("Не удалось открыть фото")
                }
            }
        }

        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            onDismiss()
            picker.dismiss(animated: true)
        }
    }
}

private let cameraFileName = "camera.jpg"
