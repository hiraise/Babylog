import SwiftUI

/**
 Плитка быстрого действия: акцентная иконка над короткой подписью на нажимаемой `AppCard`.
 Подпись уменьшается до 3/4 размера и только затем обрезается. `caption` — до двух строк;
 `highlight` выделяет важное пояснение акцентом, но смысл всегда есть в самом тексте.
 */
struct ActionTile: View {
    @Environment(\.babyLogTheme) private var theme

    let icon: String
    let label: String
    let onClick: () -> Void
    let caption: String?
    let highlight: Bool

    init(icon: String, label: String, caption: String? = nil, highlight: Bool = false, onClick: @escaping () -> Void) {
        self.icon = icon
        self.label = label
        self.caption = caption
        self.highlight = highlight
        self.onClick = onClick
    }

    var body: some View {
        let spacing = theme.spacing
        AppCard(
            onClick: onClick,
            contentPadding: EdgeInsets(top: 0, leading: spacing.xs, bottom: 0, trailing: spacing.xs)
        ) {
            VStack(spacing: spacing.xs) {
                AppIcon(icon, size: BabyLogSizes.actionIcon, tint: theme.colors.primary)
                Text(label)
                    .appTextStyle(theme.typography.labelMedium)
                    .multilineTextAlignment(.center)
                    .lineLimit(1)
                    .minimumScaleFactor(0.75)
                    .truncationMode(.tail)
                if let caption {
                    Text(caption)
                        .appTextStyle(highlight ? theme.typography.labelMedium : theme.typography.bodySmall)
                        .foregroundStyle(highlight ? theme.colors.primary : theme.colors.onSurfaceVariant)
                        .multilineTextAlignment(.center)
                        .lineLimit(2)
                        .truncationMode(.tail)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            .frame(minHeight: BabyLogSizes.actionMinHeight)
            .padding(.vertical, spacing.m)
        }
    }
}
