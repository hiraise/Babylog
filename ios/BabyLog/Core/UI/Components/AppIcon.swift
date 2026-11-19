import SwiftUI

/**
 Иконка приложения из сгенерированного SVG-набора — Material Symbols Rounded (ADR-0010).
 Начертание: Rounded, FILL 0, wght 300, GRAD 0, opsz 24. Свои значки рисуются по ADR-0014.
 `contentDescription == nil` делает декоративную иконку недоступной для VoiceOver.
 */
struct AppIcon: View {
    let name: String
    let contentDescription: String?
    let size: CGFloat
    let tint: Color?

    init(
        _ name: String,
        contentDescription: String? = nil,
        size: CGFloat = BabyLogSizes.icon,
        tint: Color? = nil
    ) {
        self.name = name
        self.contentDescription = contentDescription
        self.size = size
        self.tint = tint
    }

    var body: some View {
        let image = Image(name)
            .renderingMode(.template)
            .resizable()
            .aspectRatio(contentMode: .fit)
            .frame(width: size, height: size)
        return Group {
            if let tint {
                image.foregroundStyle(tint)
            } else {
                image
            }
        }
        .accessibilityLabel(contentDescription ?? "")
        .accessibilityHidden(contentDescription == nil)
    }
}

/** Витрина всех иконок для превью и проверки ресурсов. */
let AllIcons = [
    "ic_add", "ic_add_a_photo", "ic_add_child", "ic_analytics", "ic_bottle", "ic_calendar",
    "ic_check", "ic_chevron_left", "ic_chevron_right", "ic_child", "ic_child_boy", "ic_child_girl",
    "ic_close", "ic_crying", "ic_delete", "ic_development", "ic_diaper", "ic_diaper_dirty",
    "ic_diaper_mixed", "ic_diaper_wet", "ic_diary", "ic_edit", "ic_expand", "ic_export",
    "ic_feeding", "ic_first_aid", "ic_health", "ic_height", "ic_help", "ic_illness", "ic_import",
    "ic_lightbulb", "ic_medicine", "ic_more", "ic_note", "ic_notification", "ic_notifications",
    "ic_photo_camera", "ic_photo_library", "ic_remove", "ic_schedule", "ic_share", "ic_shield",
    "ic_sleep", "ic_stethoscope", "ic_swap", "ic_tag", "ic_theme", "ic_theme_dark", "ic_theme_light",
    "ic_thermometer", "ic_today", "ic_weight",
]
