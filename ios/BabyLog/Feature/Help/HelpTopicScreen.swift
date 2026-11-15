import SwiftUI

/**
 Тема справки (ADR-0043): что делать — шагами, кнопка «Попробовать на примере» и переход к следующей
 теме. «Новое» берётся из `progress` при открытии: отметка «тему открыли» его не гасит, пока экран открыт.
 */
struct HelpTopicScreen: View {
    @Environment(\.babyLogTheme) private var theme
    @State private var newSteps: Set<GuideStep>

    let topic: HelpTopic
    let next: HelpTopic?
    let onTry: (() -> Void)?
    let onOpenTopic: (HelpTopic) -> Void
    let onBack: () -> Void

    init(
        topic: HelpTopic,
        progress: HelpProgress,
        next: HelpTopic?,
        onTry: (() -> Void)? = nil,
        onOpenTopic: @escaping (HelpTopic) -> Void,
        onBack: @escaping () -> Void
    ) {
        self.topic = topic
        self.next = next
        self.onTry = onTry
        self.onOpenTopic = onOpenTopic
        self.onBack = onBack
        _newSteps = State(initialValue: topic.newSteps(progress: progress))
    }

    var body: some View {
        let steps = topic.readingSteps
        HelpSurface {
            HelpHeader(title: topic.title, onBack: onBack)
            ScrollView {
                LazyVStack(alignment: .leading, spacing: theme.spacing.m) {
                    Text(topic.summary)
                        .appTextStyle(theme.typography.bodyMedium)
                        .foregroundStyle(theme.colors.onSurfaceVariant)
                    ForEach(steps.indices, id: \.self) { index in
                        StepRow(number: index + 1, step: steps[index], isNew: newSteps.contains(steps[index]))
                    }
                    VStack(alignment: .leading, spacing: theme.spacing.s) {
                        if let onTry {
                            Button("Попробовать на примере", action: onTry)
                                .buttonStyle(.borderedProminent)
                                .buttonBorderShape(.capsule)
                                .tint(theme.colors.primary)
                                .appTextStyle(theme.typography.labelLarge)
                                .frame(maxWidth: .infinity)
                            Text("Откроется пример дневника — ваши записи не изменятся.")
                                .appTextStyle(theme.typography.bodySmall)
                                .foregroundStyle(theme.colors.onSurfaceVariant)
                        }
                        if let next {
                            Button("Следующая тема: «\(next.title)»") { onOpenTopic(next) }
                                .buttonStyle(.plain)
                                .foregroundStyle(theme.colors.primary)
                                .appTextStyle(theme.typography.labelLarge)
                                .padding(.vertical, theme.spacing.xs)
                        }
                    }
                    .padding(.top, theme.spacing.s)
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

/** Номер шага в кружке и что сделать. */
private struct StepRow: View {
    @Environment(\.babyLogTheme) private var theme

    let number: Int
    let step: GuideStep
    let isNew: Bool

    var body: some View {
        HStack(alignment: .top, spacing: theme.spacing.m) {
            Text("\(number)")
                .appTextStyle(theme.typography.labelLarge)
                .foregroundStyle(theme.colors.onPrimaryContainer)
                .frame(width: BabyLogSizes.helpStepNumber, height: BabyLogSizes.helpStepNumber)
                .background(theme.colors.primaryContainer, in: Circle())
            VStack(alignment: .leading, spacing: theme.spacing.xs) {
                if isNew { NewLabel() }
                Text(step.text)
                    .appTextStyle(theme.typography.bodyLarge)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
    }
}
