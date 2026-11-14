import SwiftUI
import UIKit

/**
 Аватар ребёнка: фото в круге, а без него или при ошибке декодирования — иконка по полу
 на `primaryContainer`. Аватар декоративный: имя ребёнка всегда показывай рядом текстом.
 */
struct Avatar: View {
    @Environment(\.babyLogTheme) private var theme

    let sex: Sex?
    let photo: Data?
    let size: CGFloat

    init(sex: Sex?, photo: Data? = nil, size: CGFloat = BabyLogSizes.avatar) {
        self.sex = sex
        self.photo = photo
        self.size = size
    }

    var body: some View {
        ZStack {
            if let photo, let image = UIImage(data: photo) {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFill()
            } else {
                AppIcon(avatarIcon(sex), size: size * 0.55, tint: theme.colors.onPrimaryContainer)
            }
        }
        .frame(width: size, height: size)
        .background(theme.colors.primaryContainer)
        .clipShape(Circle())
        .accessibilityHidden(true)
    }
}

/** Мальчик — малыш в кепке, девочка — малышка с бантиком; без пола — нейтральный малыш. */
func avatarIcon(_ sex: Sex?) -> String {
    switch sex {
    case .boy: "ic_child_boy"
    case .girl: "ic_child_girl"
    case nil: "ic_child"
    }
}
