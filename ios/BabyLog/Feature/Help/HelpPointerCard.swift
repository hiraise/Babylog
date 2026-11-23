import SwiftUI

/**
 Где справка (ADR-0043): карточка вверху «Сегодня», один раз — и новичку, и обновившемуся.
 Исчезает по «Понятно» или когда справку открыли.
 */
struct HelpPointerCard: View {
    @Environment(\.babyLogTheme) private var theme
    let onOpen: () -> Void
    let onDismiss: () -> Void

    var body: some View {
        AppCard(containerColor: theme.colors.primaryContainer, contentColor: theme.colors.onPrimaryContainer) {
            VStack(spacing: theme.spacing.m) {
                HStack(alignment: .top, spacing: theme.spacing.m) {
                    AppIcon("ic_help")
                    VStack(alignment: .leading, spacing: theme.spacing.xs) {
                        Text("Как пользоваться дневником")
                            .appTextStyle(theme.typography.titleSmall)
                        Text("Справка — в меню ⋮ справа вверху. Каждую тему можно пройти на примере дневника, ваши записи не изменятся.")
                            .appTextStyle(theme.typography.bodySmall)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                HStack(spacing: theme.spacing.s) {
                    Spacer()
                    Button("Понятно", action: onDismiss)
                        .buttonStyle(.plain)
                        .foregroundStyle(theme.colors.onPrimaryContainer)
                        .appTextStyle(theme.typography.labelLarge)
                    Button("Открыть справку", action: onOpen)
                        .buttonStyle(.plain)
                        .foregroundStyle(theme.colors.onPrimaryContainer)
                        .appTextStyle(theme.typography.labelLarge)
                }
            }
        }
        .frame(maxWidth: .infinity)
    }
}
