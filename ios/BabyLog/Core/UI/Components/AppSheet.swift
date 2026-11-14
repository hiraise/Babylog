import SwiftUI

/** Основная кнопка листа; `destructive` делает действие красным. */
struct SheetAction {
    let label: String
    let enabled: Bool
    let destructive: Bool
    let onClick: () -> Void

    init(label: String, enabled: Bool = true, destructive: Bool = false, onClick: @escaping () -> Void) {
        self.label = label
        self.enabled = enabled
        self.destructive = destructive
        self.onClick = onClick
    }
}

/** Сначала закрывает лист с анимацией, затем выполняет действие. */
@MainActor
struct SheetCloser {
    @Binding var isPresented: Bool
    var beforeDismiss: () -> Void = {}

    func close(then action: @escaping () -> Void = {}) {
        beforeDismiss()
        isPresented = false
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3, execute: action)
    }
}

/**
 Нижний лист — единственный вид всплывающего окна (ADR-0018). Внизу справа — «Отмена»
 и необязательное основное действие. `secondary` заменяет «Отмена» действием, а `secondaryContent` —
 системным элементом вроде `ShareLink`. `edgeToEdge` убирает боковые отступы содержимого для меню;
 `scrollable = false` используй для содержимого, которое прокручивается само.
 */
struct AppSheet<Content: View>: View {
    @Environment(\.babyLogTheme) private var theme
    @Binding var isPresented: Bool
    @State private var actionDismissal = false

    let onDismiss: () -> Void
    let title: String?
    let icon: String?
    let confirm: SheetAction?
    let scrollable: Bool
    let edgeToEdge: Bool
    let secondary: SheetAction?
    let secondaryContent: AnyView?
    let content: (SheetCloser) -> Content

    init(
        isPresented: Binding<Bool>,
        onDismiss: @escaping () -> Void,
        title: String? = nil,
        icon: String? = nil,
        confirm: SheetAction? = nil,
        scrollable: Bool = true,
        edgeToEdge: Bool = false,
        secondary: SheetAction? = nil,
        secondaryContent: AnyView? = nil,
        @ViewBuilder content: @escaping (SheetCloser) -> Content
    ) {
        _isPresented = isPresented
        self.onDismiss = onDismiss
        self.title = title
        self.icon = icon
        self.confirm = confirm
        self.scrollable = scrollable
        self.edgeToEdge = edgeToEdge
        self.secondary = secondary
        self.secondaryContent = secondaryContent
        self.content = content
    }

    var body: some View {
        Color.clear
            .sheet(isPresented: $isPresented, onDismiss: {
                if !actionDismissal { onDismiss() }
                actionDismissal = false
            }) {
                AppSheetContent(
                    isPresented: $isPresented,
                    title: title,
                    icon: icon,
                    confirm: confirm,
                    scrollable: scrollable,
                    edgeToEdge: edgeToEdge,
                    secondary: secondary,
                    secondaryContent: secondaryContent,
                    onActionDismiss: { actionDismissal = true },
                    content: content
                )
                .environment(\.babyLogTheme, theme)
                .presentationDetents([.large])
                .presentationBackground(theme.colors.surfaceContainerHigh)
                .presentationCornerRadius(theme.shapes.extraLarge)
            }
    }
}

struct AppSheetContent<Content: View>: View {
    @Environment(\.babyLogTheme) private var theme
    @Binding var isPresented: Bool

    let title: String?
    let icon: String?
    let confirm: SheetAction?
    let scrollable: Bool
    let edgeToEdge: Bool
    let secondary: SheetAction?
    let secondaryContent: AnyView?
    let onActionDismiss: () -> Void
    let content: (SheetCloser) -> Content

    init(
        isPresented: Binding<Bool>,
        title: String? = nil,
        icon: String? = nil,
        confirm: SheetAction? = nil,
        scrollable: Bool = true,
        edgeToEdge: Bool = false,
        secondary: SheetAction? = nil,
        secondaryContent: AnyView? = nil,
        onActionDismiss: @escaping () -> Void = {},
        @ViewBuilder content: @escaping (SheetCloser) -> Content
    ) {
        _isPresented = isPresented
        self.title = title
        self.icon = icon
        self.confirm = confirm
        self.scrollable = scrollable
        self.edgeToEdge = edgeToEdge
        self.secondary = secondary
        self.secondaryContent = secondaryContent
        self.onActionDismiss = onActionDismiss
        self.content = content
    }

    var body: some View {
        let spacing = theme.spacing
        let closer = SheetCloser(isPresented: $isPresented, beforeDismiss: onActionDismiss)
        VStack(alignment: .leading, spacing: 0) {
            if let title {
                HStack(spacing: spacing.m) {
                    if let icon {
                        AppIcon(icon, tint: theme.colors.primary)
                    }
                    Text(title).appTextStyle(theme.typography.headlineSmall)
                }
                .padding(.horizontal, spacing.l)
                .padding(.bottom, spacing.m)
            }
            Group {
                if scrollable {
                    ScrollView { sheetContent(closer) }
                } else {
                    sheetContent(closer)
                }
            }
            .frame(maxHeight: .infinity, alignment: .top)
            if let confirm {
                HStack(spacing: spacing.s) {
                    Spacer()
                    if let secondaryContent {
                        secondaryContent
                    } else if let secondary {
                        Button(secondary.label) { closer.close(then: secondary.onClick) }
                            .disabled(!secondary.enabled)
                            .buttonStyle(.plain)
                            .foregroundStyle(theme.colors.primary)
                            .appTextStyle(theme.typography.labelLarge)
                    } else {
                        Button("Отмена") { closer.close() }
                            .buttonStyle(.plain)
                            .foregroundStyle(theme.colors.primary)
                            .appTextStyle(theme.typography.labelLarge)
                    }
                    Button(confirm.label) { closer.close(then: confirm.onClick) }
                        .disabled(!confirm.enabled)
                        .buttonStyle(.borderedProminent)
                        .buttonBorderShape(.capsule)
                        .tint(confirm.destructive ? theme.colors.error : theme.colors.primary)
                        .appTextStyle(theme.typography.labelLarge)
                }
                .padding(.horizontal, spacing.l)
                .padding(.top, spacing.m)
            }
            Spacer().frame(height: spacing.l)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(theme.colors.surfaceContainerHigh)
        .foregroundStyle(theme.colors.onSurface)
    }

    @ViewBuilder private func sheetContent(_ closer: SheetCloser) -> some View {
        VStack(alignment: .leading, spacing: edgeToEdge ? 0 : theme.spacing.m) {
            content(closer)
        }
        .padding(.horizontal, edgeToEdge ? 0 : theme.spacing.l)
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/** Пункт листа-меню: нажимается вся строка, `badge` показывает точку нового. */
struct SheetMenuItem: View {
    @Environment(\.babyLogTheme) private var theme

    let icon: String
    let label: String
    let onClick: () -> Void
    let tint: Color?
    let badge: Bool

    init(icon: String, label: String, tint: Color? = nil, badge: Bool = false, onClick: @escaping () -> Void) {
        self.icon = icon
        self.label = label
        self.tint = tint
        self.badge = badge
        self.onClick = onClick
    }

    var body: some View {
        Button(action: onClick) {
            HStack(spacing: theme.spacing.m) {
                AppIcon(icon, tint: tint ?? theme.colors.primary)
                Text(label).appTextStyle(theme.typography.bodyLarge)
                if badge {
                    Circle().fill(theme.colors.primary).frame(width: BabyLogSizes.badge, height: BabyLogSizes.badge)
                }
                Spacer(minLength: 0)
            }
            .padding(.horizontal, theme.spacing.l)
            .frame(maxWidth: .infinity, minHeight: BabyLogSizes.menuMinHeight, alignment: .leading)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}
