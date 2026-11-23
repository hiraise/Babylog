import SwiftUI
import UIKit

/**
 Фото записи в форме: миниатюры 72 dp в ряд с прокруткой и плитка «добавить фото»
 в конце, пока можно добавить ещё (`canAdd`). Пока новые фото обрабатываются (`processing`), на
 плитке индикатор. Касание миниатюры — `onOpen` с её номером. `jpegOf` — байты фото формы.

 Миниатюры декодируются сразу, без фонового потока: JPEG уменьшается уже при декодировании,
 а скриншот-тест видит их без ожидания.
 */
struct PhotoStrip: View {
    @Environment(\.babyLogTheme) private var theme
    let photos: [FormPhoto]
    let jpegOf: (FormPhoto) -> Data?
    let canAdd: Bool
    let processing: Bool
    let onAdd: () -> Void
    let onOpen: (Int) -> Void

    var body: some View {
        ScrollView(.horizontal) {
            HStack(spacing: theme.spacing.s) {
                ForEach(photos.indices, id: \.self) { index in
                    let photo = photos[index]
                    let image = jpegOf(photo).flatMap { decodeSampled(jpeg: $0, minSidePx: thumbnailPixels) }
                    Button { onOpen(index) } label: {
                        ZStack {
                            theme.colors.surfaceVariant
                            if let image {
                                Image(uiImage: image)
                                    .resizable()
                                    .scaledToFill()
                            }
                        }
                        .frame(width: BabyLogSizes.photoThumbnail, height: BabyLogSizes.photoThumbnail)
                        .clipped()
                    }
                    .buttonStyle(.plain)
                    .frame(width: BabyLogSizes.photoThumbnail, height: BabyLogSizes.photoThumbnail)
                    .background(theme.colors.surfaceVariant)
                    .clipShape(RoundedRectangle(cornerRadius: theme.shapes.small))
                    .accessibilityLabel("Фото \(index + 1) из \(photos.count)")
                    .accessibilityHint("открыть фото")
                }
                if canAdd || processing {
                    Button(action: onAdd) {
                        ZStack {
                            if processing {
                                ProgressView()
                                    .frame(width: BabyLogSizes.icon, height: BabyLogSizes.icon)
                            } else {
                                AppIcon("ic_add_a_photo", tint: theme.colors.primary)
                            }
                        }
                        .frame(width: BabyLogSizes.photoThumbnail, height: BabyLogSizes.photoThumbnail)
                        .overlay(RoundedRectangle(cornerRadius: theme.shapes.small).stroke(theme.colors.outlineVariant, lineWidth: BabyLogSizes.photoThumbnailBorder))
                    }
                    .buttonStyle(.plain)
                    .disabled(processing)
                    .accessibilityLabel(processing ? "Фото добавляется" : "Добавить фото")
                    .accessibilityHint(processing ? "" : "добавить фото")
                }
            }
        }
    }
}

private var thumbnailPixels: Int { Int(BabyLogSizes.photoThumbnail * UIScreen.main.scale) }

/**
 Фото крупно, во всю ширину листа. «Убрать фото» убирает его из формы, а из базы — по «Сохранить»
 записи: передумали — «Отмена» в форме вернёт всё как было.
 */
struct PhotoViewSheet: View {
    @Environment(\.babyLogTheme) private var theme
    @Binding var isPresented: Bool
    let jpeg: Data?
    let onRemove: () -> Void
    let onDismiss: () -> Void

    var body: some View {
        let image = jpeg.flatMap { decodeSampled(jpeg: $0, minSidePx: Int.max) }
        AppSheet(
            isPresented: $isPresented,
            onDismiss: onDismiss,
            title: "Фото",
            icon: "ic_photo_camera",
            confirm: SheetAction(label: "Убрать фото", destructive: true, onClick: onRemove)
        ) { _ in
            if let image {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFit()
                    .frame(maxWidth: .infinity, maxHeight: BabyLogSizes.photoViewMaxHeight)
                    .clipShape(RoundedRectangle(cornerRadius: theme.shapes.medium))
                    .accessibilityLabel("Фото")
            } else {
                Text("Не удалось открыть фото")
                    .foregroundStyle(theme.colors.error)
            }
        }
    }
}
