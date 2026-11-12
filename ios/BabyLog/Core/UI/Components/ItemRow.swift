import SwiftUI

/**
 Строка «иконка — текст — действие» для истории. Сама строка не нажимается и не имеет фона:
 клади её в `AppCard`. Пустые подзаголовок и заметка не занимают место; заметка — курсивом.
 */
struct ItemRow<Trailing: View>: View {
    @Environment(\.babyLogTheme) private var theme

    let icon: String
    let title: String
    let subtitle: String?
    let note: String?
    let trailing: Trailing?

    init(
        icon: String,
        title: String,
        subtitle: String? = nil,
        note: String? = nil,
        @ViewBuilder trailing: () -> Trailing
    ) {
        self.icon = icon
        self.title = title
        self.subtitle = subtitle
        self.note = note
        self.trailing = trailing()
    }

    var body: some View {
        HStack(alignment: .center, spacing: theme.spacing.m) {
            AppIcon(icon, tint: theme.colors.primary)
            VStack(alignment: .leading, spacing: 0) {
                Text(title).appTextStyle(theme.typography.bodyLarge)
                if let subtitle, !subtitle.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    Text(subtitle)
                        .appTextStyle(theme.typography.bodySmall)
                        .foregroundStyle(theme.colors.onSurfaceVariant)
                }
                if let note, !note.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    Text(note)
                        .appTextStyle(theme.typography.bodySmall)
                        .italic()
                        .foregroundStyle(theme.colors.onSurfaceVariant)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            if let trailing {
                trailing
            }
        }
    }
}

extension ItemRow where Trailing == EmptyView {
    init(icon: String, title: String, subtitle: String? = nil, note: String? = nil) {
        self.init(icon: icon, title: title, subtitle: subtitle, note: note, trailing: { EmptyView() })
    }
}
