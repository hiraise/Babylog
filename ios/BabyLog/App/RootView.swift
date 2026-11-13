import SwiftUI

struct RootView: View {
    @Environment(AppContainer.self) private var container
    @Environment(\.colorScheme) private var systemColorScheme
    @State private var model: DiaryViewModel?
    @State private var theme = ThemeChoice()
    @State private var recovery: DatabaseRecovery?

    init(model: DiaryViewModel? = nil) {
        _model = State(initialValue: model)
    }

    var body: some View {
        BabyLogTheme(
            clock: container.clock,
            darkTheme: theme.mode.isDark(systemDark: systemColorScheme == .dark),
            darkPalette: theme.darkPalette
        ) {
            if let model {
                DiaryScreen(
                    model: model,
                    theme: theme,
                    onThemeChange: container.themeMode.select,
                    recovery: recovery,
                    onRecoverySeen: container.recoveryNotices.dismiss
                )
            } else {
                ProgressView()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .task {
            theme = container.themeMode.choice.value
            if model == nil {
                model = DiaryViewModel(
                    repository: container.repository,
                    selection: container.selectedChild,
                    documents: container.documents,
                    clock: container.clock
                )
            }
            for await choice in container.themeMode.choice.stream() {
                theme = choice
            }
        }
        .task {
            recovery = container.recoveryNotices.pending.value
            for await notice in container.recoveryNotices.pending.stream() {
                recovery = notice
            }
        }
    }
}
