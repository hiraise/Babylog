import SwiftUI

/**
 Панель гайда над примером дневника (ADR-0043): что это пример, шаг из скольких, текст шага и его
 кнопка, если шаг засчитывается кнопкой. В конце — «Готово», «Ещё раз» и следующая тема.
 ✕ закрывает пример. Текст шага объявляет VoiceOver, когда шаг сменился.
 */
struct GuidePanel: View {
    @Environment(\.babyLogTheme) private var theme
    let controller: GuideController
    let next: HelpTopic?
    let onClose: () -> Void
    let onRestart: () -> Void
    let onNextTopic: (HelpTopic) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: theme.spacing.s) {
            HStack {
                Text("Пример · ничего не сохранится")
                    .appTextStyle(theme.typography.labelMedium)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Button(action: onClose) {
                    AppIcon("ic_close", contentDescription: "Закрыть пример", tint: theme.colors.onPrimaryContainer)
                        .frame(width: BabyLogSizes.touchTarget, height: BabyLogSizes.touchTarget)
                }
                .buttonStyle(.plain)
            }
            VStack(alignment: .leading, spacing: theme.spacing.s) {
                if let step = controller.step {
                    Text("\(controller.topic.title) · шаг \(controller.index + 1) из \(controller.topic.steps.count)")
                        .appTextStyle(theme.typography.labelLarge)
                    ProgressView(value: Double(controller.index), total: Double(controller.topic.steps.count))
                        .tint(theme.colors.primary)
                    Text(step.text)
                        .appTextStyle(theme.typography.bodyLarge)
                        .accessibilityLabel(step.text)
                        .accessibilityAddTraits(.updatesFrequently)
                    if case .next = step.done {
                        Button(step.button ?? "Далее", action: controller.press)
                            .buttonStyle(.borderedProminent)
                            .buttonBorderShape(.capsule)
                            .tint(theme.colors.primary)
                            .appTextStyle(theme.typography.labelLarge)
                    } else {
                        Button("Пропустить шаг", action: controller.skip)
                            .buttonStyle(.plain)
                            .foregroundStyle(theme.colors.onPrimaryContainer)
                            .appTextStyle(theme.typography.labelLarge)
                    }
                } else {
                    Text("Готово: тема «\(controller.topic.title)» пройдена")
                        .appTextStyle(theme.typography.titleMedium)
                        .accessibilityAddTraits(.updatesFrequently)
                    HStack(spacing: theme.spacing.s) {
                        Button("Ещё раз", action: onRestart)
                            .buttonStyle(.bordered)
                            .buttonBorderShape(.capsule)
                            .tint(theme.colors.primary)
                            .appTextStyle(theme.typography.labelLarge)
                        if let next {
                            Button("Дальше: «\(next.title)»") { onNextTopic(next) }
                                .buttonStyle(.borderedProminent)
                                .buttonBorderShape(.capsule)
                                .tint(theme.colors.primary)
                                .appTextStyle(theme.typography.labelLarge)
                        } else {
                            Button("К справке", action: onClose)
                                .buttonStyle(.borderedProminent)
                                .buttonBorderShape(.capsule)
                                .tint(theme.colors.primary)
                                .appTextStyle(theme.typography.labelLarge)
                        }
                    }
                }
            }
            .padding(.trailing, theme.spacing.m)
        }
        .padding(.leading, theme.spacing.l)
        .padding(.trailing, theme.spacing.xs)
        .padding(.bottom, theme.spacing.l)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(theme.colors.primaryContainer)
        .clipShape(UnevenRoundedRectangle(
            bottomLeadingRadius: theme.shapes.large,
            bottomTrailingRadius: theme.shapes.large
        ))
        .foregroundStyle(theme.colors.onPrimaryContainer)
    }
}
