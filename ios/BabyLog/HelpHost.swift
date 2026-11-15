import SwiftUI

/** Где пользователь в справке (ADR-0043); nil у владельца маршрута — в дневнике. */
enum HelpRoute: Hashable, Identifiable {
    case topics
    case topic(String)

    var id: String {
        switch self {
        case .topics: "topics"
        case .topic(let id): "topic:\(id)"
        }
    }
}

/**
 Справка поверх дневника (ADR-0043): список тем и экран темы. «Назад» ведёт тема → список → дневник.
 Экран темы отмечает тему открытой. Интерактивный маршрут подключает сессия S9b.
 */
struct HelpHost: View {
    let route: HelpRoute
    let onRoute: (HelpRoute?) -> Void
    let topics: [HelpTopic]
    let progress: HelpProgress
    let onTopicOpened: (HelpTopic) -> Void
    let onStartGuide: ((HelpTopic) -> Void)?

    init(
        route: HelpRoute,
        onRoute: @escaping (HelpRoute?) -> Void,
        topics: [HelpTopic],
        progress: HelpProgress,
        onTopicOpened: @escaping (HelpTopic) -> Void,
        onStartGuide: ((HelpTopic) -> Void)? = nil
    ) {
        self.route = route
        self.onRoute = onRoute
        self.topics = topics
        self.progress = progress
        self.onTopicOpened = onTopicOpened
        self.onStartGuide = onStartGuide
    }

    var body: some View {
        switch route {
        case .topics:
            HelpScreen(
                topics: topics,
                progress: progress,
                onOpenTopic: { onRoute(.topic($0.id)) },
                onStartGuide: onStartGuide,
                onBack: { onRoute(nil) }
            )
        case .topic(let id):
            if let topic = topics.first(where: { $0.id == id }) {
                HelpTopicScreen(
                    topic: topic,
                    progress: progress,
                    next: topicAfter(topics: topics, topic: topic),
                    onTry: onStartGuide.map { start in { start(topic) } },
                    onOpenTopic: { onRoute(.topic($0.id)) },
                    onBack: { onRoute(.topics) }
                )
                .id(topic.id)
                .onAppear { onTopicOpened(topic) }
            } else {
                Color.clear.onAppear { onRoute(.topics) }
            }
        }
    }
}
