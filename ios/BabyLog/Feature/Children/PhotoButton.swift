import SwiftUI

/**
 Фото в листе ребёнка: аватар 96 dp со значком камеры на краю круга. Касание открывает
 `PhotoSourceSheet`. Значок выступает за круг аватара, и обрезать кнопку по кругу нельзя.
 */
struct PhotoButton: View {
    @Environment(\.babyLogTheme) private var theme
    let sex: Sex?
    let photo: ChildPhoto?
    let onClick: () -> Void

    var body: some View {
        Button(action: onClick) {
            ZStack(alignment: .bottomTrailing) {
                Avatar(sex: sex, photo: photo?.jpeg, size: BabyLogSizes.photoAvatar)
                ZStack {
                    Circle().fill(theme.colors.primary)
                    AppIcon("ic_photo_camera", size: BabyLogSizes.chipIcon, tint: theme.colors.onPrimary)
                }
                .frame(width: BabyLogSizes.photoBadge, height: BabyLogSizes.photoBadge)
                .overlay(Circle().stroke(theme.colors.surfaceContainerHigh, lineWidth: BabyLogSizes.photoBadgeBorder))
            }
            .frame(width: BabyLogSizes.photoAvatar, height: BabyLogSizes.photoAvatar)
            .contentShape(Circle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(photo == nil ? "Фото не выбрано" : "Фото ребёнка")
        .accessibilityHint(photo == nil ? "добавить фото" : "изменить фото")
    }
}
