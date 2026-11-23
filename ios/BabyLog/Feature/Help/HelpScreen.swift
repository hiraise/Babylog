import SwiftUI

/**
 Справка (ADR-0043): темы по порядку. «Пройти по порядку» открывает гайд первой непройденной темы,
 касание темы — её экран. Тема с новыми шагами — «Новое», пройденная — галочка.
 */
struct HelpScreen: View {
    @Environment(\.babyLogTheme) private var theme

    let topics: [HelpTopic]
    let progress: HelpProgress
    let onOpenTopic: (HelpTopic) -> Void
    let onStartGuide: ((HelpTopic) -> Void)?
    let onBack: () -> Void

    var body: some View {
        let next = nextUnfinished(topics: topics, progress: progress)
        let started = topics.contains { progress.completed.contains($0.id) }
        HelpSurface {
            HelpHeader(title: "Справка", onBack: onBack)
            ScrollView {
                LazyVStack(spacing: theme.spacing.s) {
                    VStack(alignment: .leading, spacing: theme.spacing.m) {
                        Text(
                            "Каждую тему можно пройти на примере дневника: нажимаете сами, а ваши записи не меняются. " +
                                "Пройдите темы по порядку или откройте ту, что нужна."
                        )
                        .appTextStyle(theme.typography.bodyMedium)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        if let next, let onStartGuide {
                            Button(started ? "Продолжить: «\(next.title)»" : "Пройти по порядку") {
                                onStartGuide(next)
                            }
                            .buttonStyle(.borderedProminent)
                            .buttonBorderShape(.capsule)
                            .tint(theme.colors.primary)
                            .appTextStyle(theme.typography.labelLarge)
                            .frame(maxWidth: .infinity)
                        }
                    }
                    .padding(.bottom, theme.spacing.s)
                    ForEach(topics, id: \.id) { topic in
                        TopicRow(
                            topic: topic,
                            isNew: topic.isNew(progress: progress),
                            completed: progress.completed.contains(topic.id),
                            onClick: { onOpenTopic(topic) }
                        )
                    }
                }
                .padding(EdgeInsets(
                    top: 0,
                    leading: theme.spacing.l,
                    bottom: theme.spacing.xl,
                    trailing: theme.spacing.l
                ))
            }
        }
    }
}

private struct TopicRow: View {
    @Environment(\.babyLogTheme) private var theme

    let topic: HelpTopic
    let isNew: Bool
    let completed: Bool
    let onClick: () -> Void

    var body: some View {
        AppCard(onClick: onClick, onClickLabel: "открыть тему") {
            HStack(alignment: .center, spacing: theme.spacing.m) {
                AppIcon(topic.icon, tint: theme.colors.primary)
                VStack(alignment: .leading, spacing: theme.spacing.xs) {
                    HStack(alignment: .center, spacing: theme.spacing.s) {
                        Text(topic.title).appTextStyle(theme.typography.titleMedium)
                        if isNew { NewLabel() }
                    }
                    Text(topic.summary)
                        .appTextStyle(theme.typography.bodySmall)
                        .foregroundStyle(theme.colors.onSurfaceVariant)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                if completed {
                    AppIcon("ic_check", contentDescription: "пройдено", tint: theme.colors.primary)
                } else {
                    AppIcon("ic_chevron_right")
                }
            }
        }
    }
}

/**
 Фон экрана справки. Задаёт цвет текста и иконок (`onBackground`), поэтому текст читается
 и в тёмной теме. Экраны справки стоят вне основного экрана дневника.
 */
struct HelpSurface<Content: View>: View {
    @Environment(\.babyLogTheme) private var theme
    @ViewBuilder let content: Content

    var body: some View {
        VStack(spacing: 0) { content }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .background(theme.colors.background.ignoresSafeArea())
            .foregroundStyle(theme.colors.onBackground)
    }
}

/** «‹ Справка»: заголовок экрана справки с кнопкой «Назад». */
struct HelpHeader: View {
    @Environment(\.babyLogTheme) private var theme

    let title: String
    let onBack: () -> Void

    var body: some View {
        HStack(spacing: 0) {
            Button(action: onBack) {
                AppIcon("ic_chevron_left", contentDescription: "Назад")
                    .frame(width: BabyLogSizes.touchTarget, height: BabyLogSizes.touchTarget)
            }
            .buttonStyle(.plain)
            Text(title)
                .appTextStyle(theme.typography.headlineSmall)
                .accessibilityAddTraits(.isHeader)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.horizontal, theme.spacing.xs)
        .padding(.vertical, theme.spacing.s)
    }
}

/** Метка «Новое» у темы и шага, которые появились после того, как тему открывали. */
struct NewLabel: View {
    @Environment(\.babyLogTheme) private var theme

    var body: some View {
        Text("Новое")
            .appTextStyle(theme.typography.labelSmall)
            .foregroundStyle(theme.colors.onPrimary)
            .padding(.horizontal, theme.spacing.s)
            .background(theme.colors.primary, in: RoundedRectangle(cornerRadius: theme.shapes.extraSmall))
    }
}
