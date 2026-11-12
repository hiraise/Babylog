import SwiftUI

/**
 Карточка макета (ADR-0010): `surfaceContainerLow`, скругление `medium`, мягкая тень только
 в светлой теме. Используй вместо стандартных Material-карточек для любого блока экрана.
 Нажатие на всю карточку доступно как кнопка; текст читается одной фразой.
 */
struct AppCard<Content: View>: View {
    @Environment(\.babyLogTheme) private var theme

    let onClick: (() -> Void)?
    let onClickLabel: String?
    let contentPadding: EdgeInsets?
    let containerColor: Color?
    let contentColor: Color?
    let content: Content

    init(
        onClick: (() -> Void)? = nil,
        onClickLabel: String? = nil,
        contentPadding: EdgeInsets? = nil,
        containerColor: Color? = nil,
        contentColor: Color? = nil,
        @ViewBuilder content: () -> Content
    ) {
        self.onClick = onClick
        self.onClickLabel = onClickLabel
        self.contentPadding = contentPadding
        self.containerColor = containerColor
        self.contentColor = contentColor
        self.content = content()
    }

    var body: some View {
        Group {
            if let onClick {
                Button(action: onClick) { card }
                    .buttonStyle(.plain)
                    .accessibilityHint(onClickLabel ?? "")
            } else {
                card
            }
        }
    }

    private var card: some View {
        let shape = RoundedRectangle(cornerRadius: theme.shapes.medium, style: .circular)
        let spacing = theme.spacing
        return VStack(alignment: .leading, spacing: 0) { content }
            .padding(contentPadding ?? EdgeInsets(top: spacing.l, leading: spacing.l, bottom: spacing.l, trailing: spacing.l))
            .background(containerColor ?? theme.colors.surfaceContainerLow, in: shape)
            .foregroundStyle(contentColor ?? theme.colors.onSurface)
            .cardShadow(shadow: theme.shadows.card)
            .contentShape(shape)
    }
}

/** Мягкая тень по форме элемента; `nil` в тёмной теме означает отсутствие тени. */
extension View {
    func cardShadow(shadow: AppShadow?) -> some View {
        self.shadow(color: shadow?.color ?? .clear, radius: shadow?.radius ?? 0, x: shadow?.x ?? 0, y: shadow?.y ?? 0)
    }
}
