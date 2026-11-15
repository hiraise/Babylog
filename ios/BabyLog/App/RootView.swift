import SwiftUI

struct RootView: View {
    @Environment(AppContainer.self) private var container
    @Environment(\.colorScheme) private var systemColorScheme
    @State private var model: DiaryViewModel?
    @State private var development: DevelopmentViewModel?
    @State private var theme = ThemeChoice()
    @State private var recovery: DatabaseRecovery?
    @State private var helpRoute: HelpRoute?
    @State private var helpProgress = HelpProgress()
    @State private var guideSessions: GuideSessions?

    init(model: DiaryViewModel? = nil, initialHelpRoute: HelpRoute? = nil) {
        _model = State(initialValue: model)
        _helpRoute = State(initialValue: initialHelpRoute)
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
                    development: development,
                    recovery: recovery,
                    onRecoverySeen: container.recoveryNotices.dismiss,
                    onOpenHelp: openHelp,
                    helpBadge: helpBadge(topics: HELP_TOPICS, progress: helpProgress),
                    helpPointer: helpProgress.pointerDone ? nil : AnyView(HelpPointerCard(
                        onOpen: openHelp,
                        onDismiss: container.help.dismissPointer
                    ))
                )
                .fullScreenCover(item: $helpRoute) { route in
                    if let guideSessions {
                        HelpHost(
                            route: route,
                            onRoute: { helpRoute = $0 },
                            topics: HELP_TOPICS,
                            progress: helpProgress,
                            onTopicOpened: {
                                container.help.markOpened(topicId: $0.id, version: appVersion)
                            },
                            onTopicCompleted: { container.help.markCompleted(topicId: $0.id) },
                            sessions: guideSessions,
                            theme: theme,
                            onThemeChange: container.themeMode.select
                        )
                    } else {
                        ProgressView()
                    }
                }
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
            if development == nil {
                development = DevelopmentViewModel(source: AssetTipsSource(), seen: container.developmentSeen)
            }
            if guideSessions == nil {
                guideSessions = GuideSessions { topic in
                    GuideSandbox(
                        topic: topic,
                        tips: AssetTipsSource(),
                        documents: container.documents,
                        baseClock: container.clock
                    )
                }
            }
            for await choice in container.themeMode.choice.stream() {
                theme = choice
            }
        }
        .task {
            helpProgress = container.help.progress.value
            for await progress in container.help.progress.stream() {
                helpProgress = progress
            }
        }
        .task(priority: .background) {
            let now = container.clock.millis()
            await Task.detached { sweepPhotoCache(now: now) }.value
        }
        .task {
            recovery = container.recoveryNotices.pending.value
            for await notice in container.recoveryNotices.pending.stream() {
                recovery = notice
            }
        }
    }

    private var appVersion: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? ""
    }

    private func openHelp() {
        container.help.dismissPointer()
        helpRoute = .topics
    }
}
