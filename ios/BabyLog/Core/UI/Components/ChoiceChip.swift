import SwiftUI

/**
 Чип одного или множественного выбора. Невыбранный — фон карточки и второстепенный текст,
 выбранный — `primary`/`onPrimary`; рамки нет, тень только в светлой теме и если чип доступен.
 Одиночный чип означает выбор одного значения; множественный позволяет снять выбор повторным касанием.
 */
struct ChoiceChip: View {
    @Environment(\.babyLogTheme) private var theme

    let selected: Bool
    let onClick: () -> Void
    let label: String
    let icon: String?
    let enabled: Bool
    let multiple: Bool

    init(
        selected: Bool,
        label: String,
        icon: String? = nil,
        enabled: Bool = true,
        multiple: Bool = false,
        onClick: @escaping () -> Void
    ) {
        self.selected = selected
        self.label = label
        self.icon = icon
        self.enabled = enabled
        self.multiple = multiple
        self.onClick = onClick
    }

    var body: some View {
        let colors = theme.colors
        let foreground: Color = !enabled
            ? colors.onSurfaceVariant.opacity(0.38)
            : selected ? colors.onPrimary : colors.onSurfaceVariant
        let background: Color = !enabled && selected
            ? colors.onSurface.opacity(0.12)
            : selected ? colors.primary : colors.surfaceContainerLow
        Button(action: onClick) {
            HStack(spacing: theme.spacing.s) {
                if let icon {
                    AppIcon(icon, size: BabyLogSizes.chipIcon)
                }
                Text(label)
                    .appTextStyle(theme.typography.labelLarge)
                    .lineLimit(1)
                    .truncationMode(.tail)
            }
            .foregroundStyle(foreground)
            .padding(.horizontal, theme.spacing.m)
            .frame(minHeight: BabyLogSizes.chipHeight)
            .background(background, in: RoundedRectangle(cornerRadius: theme.shapes.small, style: .circular))
            .cardShadow(shadow: enabled ? theme.shadows.card : nil)
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .frame(minWidth: BabyLogSizes.touchTarget, minHeight: BabyLogSizes.touchTarget)
        .accessibilityAddTraits(selected ? [.isSelected] : [])
    }
}
