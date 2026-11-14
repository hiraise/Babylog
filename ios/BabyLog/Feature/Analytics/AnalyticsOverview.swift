import SwiftUI

/** Сколько последних суток «Ритма дня» видно на обзоре; вся неделя — на экране раздела. */
private let rhythmPreviewDays = 3
/** Столбиков в мини-графике не больше этого: у «всего времени» недель может быть много. */
private let sparkColumns = 30

/** Плитка обзора: что показывает и какой раздел открывает. */
private enum Tile {
    case metric(MetricModel)
    case growth(AnalyticsSection, GrowthModel)
    case medicines([MedicineLine])
    case tags(TagsModel)
}

private func overviewTiles(_ model: AnalyticsModel) -> [Tile] {
    var result = model.metrics.filter { $0.metric != .bottles }.map(Tile.metric)
    if let weight = model.weight { result.append(.growth(.weight, weight)) }
    if let height = model.height { result.append(.growth(.height, height)) }
    if !model.medicines.isEmpty { result.append(.medicines(model.medicines)) }
    if let tags = model.tags { result.append(.tags(tags)) }
    return result
}

/**
 Обзор «Аналитики» (ADR-0029): выбор периода, «Ритм дня» за последние сутки и плитки показателей
 по две в ряд. Касание ритма или плитки открывает раздел; прокрутка сохраняется при возврате.
 */
struct AnalyticsOverview: View {
    @Environment(\.babyLogTheme) private var theme

    let model: AnalyticsModel
    let period: AnalyticsPeriod
    let onPeriod: (AnalyticsPeriod) -> Void
    let onOpen: (AnalyticsSection) -> Void

    var body: some View {
        let tiles = overviewTiles(model)
        ScrollView {
            LazyVStack(spacing: theme.spacing.m) {
                PeriodPicker(period: period, onSelect: onPeriod, illnessNote: model.illnessNote)
                if !model.hasRecords {
                    HintCard(text: "Графики появятся, когда в дневнике будут записи.\nОтмечайте сон, кормления и подгузники на вкладке «Сегодня».")
                }
                if let rhythm = model.rhythm {
                    RhythmPreviewCard(model: rhythm, onOpen: { onOpen(.rhythm) })
                }
                let rows = tiles.chunked(into: 2)
                ForEach(rows.indices, id: \.self) { rowIndex in
                    let pair = rows[rowIndex]
                    HStack(spacing: theme.spacing.s) {
                        ForEach(pair.indices, id: \.self) { index in
                            TileView(tile: pair[index], onOpen: onOpen)
                                .frame(maxWidth: .infinity, maxHeight: .infinity)
                        }
                        if pair.count == 1 {
                            Spacer().frame(maxWidth: .infinity)
                        }
                    }
                    .frame(maxWidth: .infinity)
                }
            }
            .padding(analyticsListPadding(theme))
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

private struct TileView: View {
    @Environment(\.babyLogTheme) private var theme

    let tile: Tile
    let onOpen: (AnalyticsSection) -> Void

    @ViewBuilder var body: some View {
        switch tile {
        case let .metric(metric):
            StatTile(
                icon: metric.metric.icon,
                title: metric.metric.title,
                value: metric.value,
                unit: metric.unit,
                note: metric.delta,
                onClick: { onOpen(metric.metric.section) }
            ) {
                SparkBars(
                    bars: metric.columns.suffix(sparkColumns).map { column in
                        ChartBar(
                            parts: column.parts.map { BarPart(value: $0.value, color: theme.chartColors.of($0.series)) },
                            label: "",
                            description: column.caption,
                            partial: column.partial,
                            band: column.ill
                        )
                    },
                    maxValue: metric.maxValue,
                    bandColor: theme.colors.illnessBand
                )
            }
        case let .growth(section, growth):
            let weight = section == .weight
            StatTile(
                icon: weight ? "ic_weight" : "ic_height",
                title: section.title,
                value: growth.value,
                unit: growth.date,
                note: growth.rate,
                onClick: { onOpen(section) }
            ) {
                SparkLine(points: growth.points, color: weight ? theme.chartColors.weight : theme.chartColors.height)
            }
        case let .medicines(lines):
            let total = lines.reduce(0) { $0 + $1.count }
            let first = lines.first!
            StatTile(
                icon: "ic_medicine",
                title: AnalyticsSection.medicines.title,
                value: "\(total)",
                unit: plural(total, one: "приём", few: "приёма", many: "приёмов"),
                note: first.name + (lines.count > 1 ? " и ещё \(lines.count - 1)" : ""),
                onClick: { onOpen(.medicines) }
            ) {
                Text(first.last)
                    .appTextStyle(theme.typography.bodySmall)
                    .foregroundStyle(theme.colors.onSurfaceVariant)
                    .lineLimit(2)
                    .truncationMode(.tail)
            }
        case let .tags(tags):
            let top = tags.frequencies
            StatTile(
                icon: "ic_tag",
                title: AnalyticsSection.tags.title,
                value: top.first.map { "\($0.tag) ×\($0.count)" } ?? "—",
                unit: top.count > 1 ? "\(top[1].tag) ×\(top[1].count)" : top.isEmpty ? "меток за период нет" : nil,
                note: tags.counts(),
                onClick: { onOpen(.tags) }
            )
        }
    }
}

/** «Ритм дня» на обзоре: последние трое суток; нажимается вся карточка, а не отдельные строки. */
private struct RhythmPreviewCard: View {
    @Environment(\.babyLogTheme) private var theme
    let model: RhythmModel
    let onOpen: () -> Void

    var body: some View {
        AppCard(onClick: onOpen, onClickLabel: "открыть ритм за неделю") {
            VStack(alignment: .leading, spacing: theme.spacing.s) {
                CardHeader(icon: "ic_schedule", title: AnalyticsSection.rhythm.title, trailing: "все \(rhythmDays) дней ›")
                DayTimeline(
                    rows: timelineRows(Array(model.days.prefix(rhythmPreviewDays)), chart: theme.chartColors),
                    dotColor: theme.colors.onSurfaceVariant,
                    onRowClick: nil,
                    onClickLabel: nil
                )
                if let summary = model.summary {
                    Text(summary)
                        .appTextStyle(theme.typography.bodySmall)
                        .foregroundStyle(theme.colors.onSurfaceVariant)
                }
            }
        }
        .frame(maxWidth: .infinity)
    }
}

private extension Array {
    func chunked(into size: Int) -> [[Element]] {
        stride(from: 0, to: count, by: size).map { Array(self[$0..<Swift.min($0 + size, count)]) }
    }
}
