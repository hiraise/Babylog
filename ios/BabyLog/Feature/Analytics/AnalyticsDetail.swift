import SwiftUI

private let measurementChangeWidth: CGFloat = 72

/**
 Экран раздела «Аналитики» (ADR-0029): «‹ Название», выбор периода, карточки ADR-0028;
 у веса и роста под графиком — список замеров. Нет данных раздела — подсказка.
 */
struct AnalyticsDetail: View {
    @Environment(\.babyLogTheme) private var theme
    @State private var sleepHelp = false

    let section: AnalyticsSection
    let model: AnalyticsModel
    let period: AnalyticsPeriod
    let onPeriod: (AnalyticsPeriod) -> Void
    let selection: String?
    let onSelection: (String?) -> Void
    let onOpenDay: (Date) -> Void
    let onBack: () -> Void
    let onOpenTag: (String) -> Void

    var body: some View {
        ScrollView {
            LazyVStack(spacing: theme.spacing.m) {
                DetailHeader(title: section.title, onBack: onBack)
                if section.usesPeriod {
                    PeriodPicker(period: period, onSelect: onPeriod, illnessNote: model.illnessNote)
                }
                sectionContent
            }
            .padding(analyticsListPadding(theme))
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .guideTarget(.analyticsDetail)
        .background(SleepHelpSheet(isPresented: $sleepHelp, onDismiss: { sleepHelp = false }))
    }

    @ViewBuilder private var sectionContent: some View {
        switch section {
        case .rhythm:
            if let rhythm = model.rhythm {
                RhythmCard(model: rhythm, onOpenDay: onOpenDay)
            } else {
                HintCard(text: "За последнюю неделю записей сна, кормлений и подгузников нет")
            }
        case .feedings, .sleep, .diapers, .crying:
            let metrics = model.metrics.filter { $0.metric.section == section }
            if metrics.isEmpty {
                HintCard(text: "Записей пока нет")
            }
            ForEach(metrics.indices, id: \.self) { index in
                let metric = metrics[index]
                let prefix = "\(metric.metric):"
                let selected = selection?.hasPrefix(prefix) == true ? Int(selection!.dropFirst(prefix.count)) : nil
                MetricCard(
                    model: metric,
                    selected: selected,
                    onSelect: { index in onSelection(index.map { prefix + "\($0)" }) },
                    onOpenDay: onOpenDay,
                    onHelp: metric.metric == .sleep ? { sleepHelp = true } : nil
                )
            }
            if section == .sleep, let sleep = model.illnessSleep {
                IllnessSleepCard(model: sleep)
            }
            if section == .crying, let crying = model.crying {
                CryingBeforeCard(model: crying)
                CryingCausesCard(model: crying)
            }
        case .weight, .height:
            let weight = section == .weight
            if let growth = weight ? model.weight : model.height {
                GrowthCard(
                    model: growth,
                    icon: weight ? "ic_weight" : "ic_height",
                    title: section.title,
                    color: weight ? theme.chartColors.weight : theme.chartColors.height
                )
                MeasurementsCard(title: weight ? "Взвешивания" : "Замеры", entries: growth.history)
            } else {
                HintCard(text: weight ? "Взвешиваний пока нет" : "Замеров роста пока нет")
            }
        case .medicines:
            if model.medicines.isEmpty {
                HintCard(text: "Лекарств за этот период нет")
            } else {
                AnalyticsMedicinesCard(lines: model.medicines)
            }
        case .tags:
            if let tags = model.tags {
                TagsFrequencyCard(model: tags, byWeeks: period == .all, onOpenTag: onOpenTag)
                if !tags.routine.isEmpty {
                    TagsRoutineCard(model: tags)
                }
                TagsFootnote()
            } else {
                HintCard(text: "Меток пока нет: их ставят после плача и в заметках")
            }
        }
    }
}

/** «‹ Название»: стрелка возвращает к обзору; название — заголовок для VoiceOver. */
private struct DetailHeader: View {
    @Environment(\.babyLogTheme) private var theme
    let title: String
    let onBack: () -> Void

    var body: some View {
        HStack {
            Button(action: onBack) {
                AppIcon("ic_chevron_left", contentDescription: "Назад к обзору")
                    .frame(width: BabyLogSizes.touchTarget, height: BabyLogSizes.touchTarget)
            }
            .buttonStyle(.plain)
            Text(title)
                .appTextStyle(theme.typography.headlineSmall)
                .accessibilityAddTraits(.isHeader)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/** Замеры веса или роста, свежие сверху: дата, значение и разница с предыдущим замером. */
private struct MeasurementsCard: View {
    @Environment(\.babyLogTheme) private var theme
    let title: String
    let entries: [GrowthEntry]

    var body: some View {
        AppCard {
            VStack(spacing: theme.spacing.s) {
                CardHeader(icon: "ic_calendar", title: title, trailing: "к прошлому замеру")
                ForEach(entries.indices, id: \.self) { index in
                    let entry = entries[index]
                    HStack {
                        Text(entry.date)
                            .appTextStyle(theme.typography.bodyLarge)
                            .frame(maxWidth: .infinity, alignment: .leading)
                        Text(entry.value).appTextStyle(theme.typography.titleMedium)
                        Text(entry.change ?? "")
                            .appTextStyle(theme.typography.bodyMedium)
                            .foregroundStyle(theme.colors.onSurfaceVariant)
                            .frame(width: measurementChangeWidth, alignment: .trailing)
                    }
                    .accessibilityElement(children: .combine)
                }
            }
        }
        .frame(maxWidth: .infinity)
    }
}
