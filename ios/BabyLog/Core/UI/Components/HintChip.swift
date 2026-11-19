import SwiftUI

/** Чип-подсказка подставляет готовое значение; для VoiceOver это кнопка, а не выбор. */
struct HintChip: View {
    @Environment(\.babyLogTheme) private var theme

    let label: String
    let onClick: () -> Void

    init(label: String, onClick: @escaping () -> Void) {
        self.label = label
        self.onClick = onClick
    }

    var body: some View {
        Button(action: onClick) {
            Text(label)
                .appTextStyle(theme.typography.labelLarge)
                .foregroundStyle(theme.colors.onSurfaceVariant)
                .lineLimit(1)
                .truncationMode(.tail)
                .padding(.horizontal, theme.spacing.m)
                .frame(minHeight: BabyLogSizes.chipHeight)
                .background(
                    theme.colors.surfaceContainerLow,
                    in: RoundedRectangle(cornerRadius: theme.shapes.small, style: .circular)
                )
                .cardShadow(shadow: theme.shadows.card)
        }
        .buttonStyle(.plain)
        .frame(minWidth: BabyLogSizes.touchTarget, minHeight: BabyLogSizes.touchTarget)
    }
}
