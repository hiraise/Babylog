import SwiftUI

/** Где пользователь в справке (ADR-0043); nil у владельца маршрута — в дневнике. */
enum HelpRoute: Hashable, Identifiable {
    case topics
    case topic(String)
    case guide(String)

    var id: String {
        switch self {
        case .topics: "topics"
        case .topic(let id): "topic:\(id)"
        case .guide(let id): "guide:\(id)"
        }
    }

    var topicId: String? {
        switch self {
        case .topics: nil
        case .topic(let id), .guide(let id): id
        }
    }
}

/**
 Справка поверх дневника (ADR-0043): список тем, экран темы и гайд на примере дневника. «Назад» ведёт
 гайд → тема → список → дневник. Тему отмечает открытой экран темы и гайд, пройденной — конец гайда.
 */
struct HelpHost: View {
    let route: HelpRoute
    let onRoute: (HelpRoute?) -> Void
    let topics: [HelpTopic]
    let progress: HelpProgress
    let onTopicOpened: (HelpTopic) -> Void
    let onTopicCompleted: (HelpTopic) -> Void
    let sessions: GuideSessions
    let theme: ThemeChoice
    let onThemeChange: (ThemeChoice) -> Void

    @State private var sandbox: GuideSandbox?

    var body: some View {
        content
            .onDisappear { sessions.close() }
    }

    @ViewBuilder private var content: some View {
        if let id = route.topicId, let topic = topics.first(where: { $0.id == id }) {
            topicContent(topic)
                .onAppear { onTopicOpened(topic) }
        } else if case .topics = route {
            HelpScreen(
                topics: topics,
                progress: progress,
                onOpenTopic: { onRoute(.topic($0.id)) },
                onStartGuide: { onRoute(.guide($0.id)) },
                onBack: { onRoute(nil) }
            )
        } else {
            Color.clear.onAppear { onRoute(.topics) }
        }
    }

    @ViewBuilder private func topicContent(_ topic: HelpTopic) -> some View {
        let next = topicAfter(topics: topics, topic: topic)
        switch route {
        case .topic:
            HelpTopicScreen(
                topic: topic,
                progress: progress,
                next: next,
                onTry: { onRoute(.guide(topic.id)) },
                onOpenTopic: { onRoute(.topic($0.id)) },
                onBack: { onRoute(.topics) }
            )
            .id(topic.id)
        case .guide:
            if let sandbox, sandbox.topic.id == topic.id {
                GuideScreen(
                    sandbox: sandbox,
                    next: next,
                    onClose: {
                        sessions.close()
                        self.sandbox = nil
                        onRoute(.topic(topic.id))
                    },
                    onRestart: { self.sandbox = sessions.restart(topic) },
                    onNextTopic: {
                        sessions.close()
                        self.sandbox = nil
                        onRoute(.guide($0.id))
                    },
                    theme: theme,
                    onThemeChange: onThemeChange,
                    onCompleted: { onTopicCompleted(topic) }
                )
            } else {
                ProgressView()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .onAppear { sandbox = sessions.open(topic) }
            }
        case .topics:
            EmptyView()
        }
    }
}

/** Гайд: панель шага сверху, под ней настоящий `DiaryScreen` на примере дневника. */
private struct GuideScreen: View {
    @Environment(\.babyLogTheme) private var themeValues
    let sandbox: GuideSandbox
    let next: HelpTopic?
    let onClose: () -> Void
    let onRestart: () -> Void
    let onNextTopic: (HelpTopic) -> Void
    let theme: ThemeChoice
    let onThemeChange: (ThemeChoice) -> Void
    let onCompleted: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            GuidePanel(
                controller: sandbox.controller,
                next: next,
                onClose: onClose,
                onRestart: onRestart,
                onNextTopic: onNextTopic
            )
            if sandbox.ready {
                DiaryScreen(
                    model: sandbox.diary,
                    theme: theme,
                    onThemeChange: onThemeChange,
                    development: sandbox.development,
                    initialSection: sandbox.topic.start.section,
                    example: true
                )
                .id(ObjectIdentifier(sandbox))
            } else {
                ProgressView()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(themeValues.colors.background)
        .environment(\.guide, sandbox.controller)
        .environment(\.clock, sandbox.clock)
        .environment(\.timeZone, sandbox.clock.timeZone)
        .onAppear { if sandbox.controller.finished { onCompleted() } }
        .onChange(of: sandbox.controller.finished) { _, finished in
            if finished { onCompleted() }
        }
    }
}

private extension GuideStart {
    var section: MainSection {
        switch self {
        case .today: .today
        case .diary: .diary
        case .development: .development
        case .analytics: .analytics
        }
    }
}
