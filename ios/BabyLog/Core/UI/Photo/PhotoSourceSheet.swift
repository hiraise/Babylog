import SwiftUI

/**
 Лист «Фото»: снять, выбрать из галереи, а если `onRemove` задан — убрать. Пункт закрывает лист;
 камеру и галерею открывает `PhotoPicker` из view, которое этот лист пережидает.
 */
struct PhotoSourceSheet: View {
    @Binding var isPresented: Bool
    let onCamera: () -> Void
    let onGallery: () -> Void
    let onDismiss: () -> Void
    let onRemove: (() -> Void)?

    init(
        isPresented: Binding<Bool>,
        onCamera: @escaping () -> Void,
        onGallery: @escaping () -> Void,
        onDismiss: @escaping () -> Void,
        onRemove: (() -> Void)? = nil
    ) {
        _isPresented = isPresented
        self.onCamera = onCamera
        self.onGallery = onGallery
        self.onDismiss = onDismiss
        self.onRemove = onRemove
    }

    var body: some View {
        AppSheet(
            isPresented: $isPresented,
            onDismiss: onDismiss,
            title: "Фото",
            icon: "ic_photo_camera",
            edgeToEdge: true
        ) { closer in
            SheetMenuItem(icon: "ic_photo_camera", label: "Сделать фото") { closer.close(then: onCamera) }
            SheetMenuItem(icon: "ic_photo_library", label: "Выбрать из галереи") { closer.close(then: onGallery) }
            if let onRemove {
                SheetMenuItem(icon: "ic_delete", label: "Убрать фото", tint: theme.colors.error) { closer.close(then: onRemove) }
            }
        }
    }

    @Environment(\.babyLogTheme) private var theme
}
