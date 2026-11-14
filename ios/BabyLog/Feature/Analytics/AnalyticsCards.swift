import SwiftUI

private let analyticsHeaderIconSize: CGFloat = 20
private let legendIconSize: CGFloat = 18
private let swatchSize: CGFloat = 10
private let legendDotSize: CGFloat = 4
private let pinGlyphSize: CGFloat = 12
private let pinHead: CGFloat = 3
private let pinStroke: CGFloat = 2

extension ChartColors {
    func of(_ series: Series) -> Color {
        switch series {
        case .feeding: feeding
        case .nightSleep: sleep
        case .daySleep: daySleep
        case .diaperWet: diaperWet
        case .diaperMixed: diaperMixed
        case .diaperDirty: diaperDirty
        case .crying: crying
        }
    }
}

extension Metric {
    var icon: String {
        switch self {
        case .feedings: "ic_feeding"
        case .bottles: "ic_bottle"
        case .sleep: "ic_sleep"
        case .diapers: "ic_diaper"
        case .crying: "ic_crying"
        }
    }
}

/** Заголовок карточки: иконка, название и справа — пояснение мелко. */
struct CardHeader: View {
    @Environment(\.babyLogTheme) private var theme
    let icon: String
    let title: String
    let trailing: String?

    var body: some View {
        HStack(spacing: theme.spacing.s) {
            AppIcon(icon, size: analyticsHeaderIconSize, tint: theme.colors.primary)
            Text(title)
                .appTextStyle(theme.typography.titleMedium)
                .frame(maxWidth: .infinity, alignment: .leading)
                .accessibilityAddTraits(.isHeader)
            if let trailing {
                Text(trailing)
                    .appTextStyle(theme.typography.bodySmall)
                    .foregroundStyle(theme.colors.onSurfaceVariant)
            }
        }
    }
}

/** Главное число карточки, рядом — к чему оно, под ним — пояснение. */
private struct Headline: View {
    @Environment(\.babyLogTheme) private var theme
    let value: String
    let unit: String
    let note: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .firstTextBaseline, spacing: theme.spacing.s) {
                Text(value).appTextStyle(theme.typography.headlineMedium)
                Text(unit)
                    .appTextStyle(theme.typography.bodyMedium)
                    .foregroundStyle(theme.colors.onSurfaceVariant)
            }
            if let note {
                Text(note)
                    .appTextStyle(theme.typography.bodyMedium)
                    .foregroundStyle(theme.colors.onSurfaceVariant)
            }
        }
    }
}

/** Булавка заметки для легенды — как на шкале «Ритма дня». */
private struct PinGlyph: View {
    @Environment(\.babyLogTheme) private var theme

    var body: some View {
        Canvas { context, size in
            let head = CGPoint(x: size.width / 2, y: pinHead)
            var line = Path()
            line.move(to: head)
            line.addLine(to: CGPoint(x: size.width / 2, y: size.height))
            context.stroke(line, with: .color(theme.colors.onSurface), lineWidth: pinStroke)
            context.fill(Path(ellipseIn: CGRect(x: head.x - pinHead, y: head.y - pinHead, width: 2 * pinHead, height: 2 * pinHead)), with: .color(theme.colors.onSurface))
        }
        .frame(width: pinGlyphSize, height: pinGlyphSize)
    }
}

private struct Swatch: View {
    let color: Color

    var body: some View {
        RoundedRectangle(cornerRadius: 2)
            .fill(color)
            .frame(width: swatchSize, height: swatchSize)
    }
}

/**
 Карточка-график величины за период: число, сравнение, легенда, столбики. Касание столбика
 показывает его итоги под графиком, у суток — с «В историю». `onHelp` объясняет легенду.
 */
struct MetricCard: View {
    @Environment(\.babyLogTheme) private var theme

    let model: MetricModel
    let selected: Int?
    let onSelect: (Int?) -> Void
    let onOpenDay: (Date) -> Void
    var onHelp: (() -> Void)? = nil

    var body: some View {
        AppCard {
            VStack(alignment: .leading, spacing: theme.spacing.s) {
                CardHeader(icon: model.metric.icon, title: model.metric.title, trailing: model.delta)
                Headline(value: model.value, unit: model.unit, note: model.note)
                if !model.legend.isEmpty {
                    HStack {
                        FlowRow(spacing: theme.spacing.m, rowSpacing: theme.spacing.xs) {
                            ForEach(model.legend.indices, id: \.self) { index in
                                let item = model.legend[index]
                                HStack(spacing: theme.spacing.xs) {
                                    Swatch(color: theme.chartColors.of(item.series))
                                    if let kind = item.kind {
                                        AppIcon(diaperIcon(kind), size: legendIconSize, tint: theme.colors.onSurfaceVariant)
                                    }
                                    Text(item.text)
                                        .appTextStyle(theme.typography.bodyMedium)
                                        .foregroundStyle(theme.colors.onSurfaceVariant)
                                }
                                .accessibilityElement(children: .ignore)
                                .accessibilityLabel("\(item.label) \(item.text)")
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        if let onHelp {
                            Button(action: onHelp) {
                                AppIcon("ic_help", contentDescription: "Как считается: \(model.metric.title.lowercased())", tint: theme.colors.onSurfaceVariant)
                                    .frame(width: BabyLogSizes.touchTarget, height: BabyLogSizes.touchTarget)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
                BarChart(
                    bars: model.columns.map { column in
                        ChartBar(
                            parts: column.parts.map { BarPart(value: $0.value, color: theme.chartColors.of($0.series)) },
                            label: column.label,
                            description: column.caption,
                            partial: column.partial,
                            band: column.ill
                        )
                    },
                    maxValue: model.maxValue,
                    maxLabel: model.maxLabel,
                    midLabel: model.midLabel,
                    selected: selected,
                    onSelect: onSelect,
                    bandColor: theme.colors.illnessBand
                )
                if let selected, model.columns.indices.contains(selected) {
                    let column = model.columns[selected]
                    HStack {
                        Text(column.caption)
                            .appTextStyle(theme.typography.bodyMedium)
                            .frame(maxWidth: .infinity, alignment: .leading)
                        if let date = column.date {
                            Button("В историю") { onOpenDay(date) }
                                .buttonStyle(.plain)
                                .foregroundStyle(theme.colors.primary)
                                .appTextStyle(theme.typography.labelLarge)
                        }
                    }
                }
            }
        }
        .frame(maxWidth: .infinity)
    }
}

/**
 Строки `DayTimeline`: сон, кормления грудью и плач — полосами, остальные кормления — рисками,
 подгузники — точками, заметки — булавками. Плач рисуется поверх сна с обводкой.
 */
func timelineRows(_ days: [RhythmDay], chart: ChartColors) -> [TimelineRow] {
    days.map { day in
        TimelineRow(
            label: day.label,
            bands: day.nightSleeps.map { TimelineBand(from: $0.from, to: $0.to, color: chart.sleep) }
                + day.daySleeps.map { TimelineBand(from: $0.from, to: $0.to, color: chart.daySleep) }
                + day.feedings.map { TimelineBand(from: $0.from, to: $0.to, color: chart.feeding, ring: true) }
                + day.cryings.map { TimelineBand(from: $0.from, to: $0.to, color: chart.crying, ring: true) },
            ticks: day.instantFeedings.map { TimelineTick(at: $0, color: chart.feeding) },
            dots: day.diapers,
            end: day.end,
            description: day.description,
            pins: day.notes
        )
    }
}

/** «Ритм дня» за неделю: касание строки открывает сутки в «Истории». */
struct RhythmCard: View {
    @Environment(\.babyLogTheme) private var theme
    let model: RhythmModel
    let onOpenDay: (Date) -> Void

    var body: some View {
        AppCard {
            VStack(alignment: .leading, spacing: theme.spacing.s) {
                CardHeader(icon: "ic_schedule", title: "Ритм дня", trailing: "последние \(rhythmDays) дней")
                DayTimeline(
                    rows: timelineRows(model.days, chart: theme.chartColors),
                    dotColor: theme.colors.onSurfaceVariant,
                    onRowClick: { onOpenDay(model.days[$0].date) },
                    onClickLabel: "открыть в истории"
                )
                FlowRow(spacing: theme.spacing.m, rowSpacing: theme.spacing.xs) {
                    legend(color: theme.chartColors.sleep, text: "ночной сон")
                    legend(color: theme.chartColors.daySleep, text: "дневной сон")
                    legend(color: theme.chartColors.feeding, text: "кормление")
                    if model.days.contains(where: { !$0.cryings.isEmpty }) {
                        legend(color: theme.chartColors.crying, text: "плач")
                    }
                    HStack(spacing: theme.spacing.xs) {
                        Circle().fill(theme.colors.onSurfaceVariant).frame(width: legendDotSize, height: legendDotSize)
                        Text("подгузник")
                            .appTextStyle(theme.typography.bodySmall)
                            .foregroundStyle(theme.colors.onSurfaceVariant)
                    }
                    if model.days.contains(where: { !$0.notes.isEmpty }) {
                        HStack(spacing: theme.spacing.xs) {
                            PinGlyph()
                            Text("заметка")
                                .appTextStyle(theme.typography.bodySmall)
                                .foregroundStyle(theme.colors.onSurfaceVariant)
                        }
                    }
                }
                if let summary = model.summary {
                    Text(summary).appTextStyle(theme.typography.bodyMedium)
                }
            }
        }
        .frame(maxWidth: .infinity)
    }

    private func legend(color: Color, text: String) -> some View {
        HStack(spacing: theme.spacing.xs) {
            Swatch(color: color)
            Text(text)
                .appTextStyle(theme.typography.bodySmall)
                .foregroundStyle(theme.colors.onSurfaceVariant)
        }
    }
}

/** Вес или рост за всё время: последний замер, прибавка, линия по замерам. */
struct GrowthCard: View {
    @Environment(\.babyLogTheme) private var theme
    let model: GrowthModel
    let icon: String
    let title: String
    let color: Color

    var body: some View {
        AppCard {
            VStack(alignment: .leading, spacing: theme.spacing.s) {
                CardHeader(icon: icon, title: title, trailing: "за всё время")
                Headline(value: model.value, unit: model.date, note: model.note)
                if model.points.count >= 2 {
                    LineChart(
                        points: model.points,
                        xMax: model.xMax,
                        yMin: model.yMin,
                        yMax: model.yMax,
                        yTicks: model.yTicks.map { AxisTick(value: $0.value, label: $0.label) },
                        xTicks: model.xTicks.map { AxisTick(value: $0.value, label: $0.label) },
                        color: color,
                        description: model.description
                    )
                }
            }
        }
        .frame(maxWidth: .infinity)
    }
}

/** Лекарства за период; название отличается от карточки лекарств на экране болезни. */
struct AnalyticsMedicinesCard: View {
    @Environment(\.babyLogTheme) private var theme
    let lines: [MedicineLine]

    var body: some View {
        AppCard {
            VStack(alignment: .leading, spacing: theme.spacing.m) {
                CardHeader(icon: "ic_medicine", title: "Лекарства", trailing: nil)
                ForEach(lines.indices, id: \.self) { index in
                    let line = lines[index]
                    VStack(alignment: .leading, spacing: 0) {
                        Text(line.name).appTextStyle(theme.typography.bodyLarge)
                        Text(line.info)
                            .appTextStyle(theme.typography.bodyMedium)
                            .foregroundStyle(theme.colors.onSurfaceVariant)
                    }
                    .accessibilityElement(children: .combine)
                }
            }
        }
        .frame(maxWidth: .infinity)
    }
}
