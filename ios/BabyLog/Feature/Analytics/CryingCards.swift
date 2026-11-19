import SwiftUI

private let cryingRowLabelWidth: CGFloat = 132
private let cryingDurationWidth: CGFloat = 80
private let cryingCountWidth: CGFloat = 28
private let cryingBarHeight: CGFloat = 12
private let cryingMinBar: Float = 0.03

private struct CryingComparisonRow {
    let label: String
    let value: Int64?
    let color: Color
}

/**
 «Перед плачем» (ADR-0030): бодрствование и время после кормления — медиана перед плачем рядом
 с обычной. Подписи в строке, поэтому смысл не держится на одном цвете.
 */
struct CryingBeforeCard: View {
    @Environment(\.babyLogTheme) private var theme
    let model: CryingModel

    var body: some View {
        AppCard {
            VStack(alignment: .leading, spacing: theme.spacing.m) {
                CardHeader(icon: "ic_schedule", title: "Перед плачем", trailing: "по \(model.episodes) \(plural(model.episodes, one: "разу", few: "разам", many: "разам"))")
                if model.awakeBefore == nil && model.sinceFeeding == nil {
                    Text("Сравнение появится, когда рядом с плачем будут записи сна и кормлений")
                        .appTextStyle(theme.typography.bodyMedium)
                        .foregroundStyle(theme.colors.onSurfaceVariant)
                }
                if let awake = model.awakeBefore {
                    CryingComparison(title: "Бодрствование", rows: [
                        CryingComparisonRow(label: "перед плачем", value: awake, color: theme.chartColors.crying),
                        CryingComparisonRow(label: "обычно", value: model.usualAwake, color: theme.colors.outline),
                    ])
                }
                if let feeding = model.sinceFeeding {
                    CryingComparison(title: "После кормления", rows: [
                        CryingComparisonRow(label: "перед плачем", value: feeding, color: theme.chartColors.crying),
                        CryingComparisonRow(label: "между кормлениями", value: model.feedingInterval, color: theme.colors.outline),
                    ])
                }
                if model.awakeBefore != nil && model.usualAwake != nil {
                    Text(longerThanUsualText(model.longerThanUsual, model.awakeEpisodes))
                        .appTextStyle(theme.typography.bodyMedium)
                }
            }
        }
        .frame(maxWidth: .infinity)
    }
}

/** Заголовок и строки-полосы одной шкалы: самая длинная — во всю ширину. */
private struct CryingComparison: View {
    @Environment(\.babyLogTheme) private var theme
    let title: String
    let rows: [CryingComparisonRow]

    var body: some View {
        let maximum = rows.compactMap(\.value).max() ?? 0
        if maximum > 0 {
            VStack(alignment: .leading, spacing: theme.spacing.xs) {
                Text(title).appTextStyle(theme.typography.titleSmall)
                ForEach(rows.indices, id: \.self) { index in
                    let row = rows[index]
                    if let value = row.value {
                        CryingBarRow(
                            label: row.label,
                            fraction: Float(value) / Float(maximum),
                            value: formatDuration(value),
                            color: row.color,
                            valueWidth: cryingDurationWidth
                        )
                    }
                }
            }
        }
    }
}

/** «Причины и что помогало»: метки и способы по числу раз; доля полосы — от всех случаев плача. */
struct CryingCausesCard: View {
    @Environment(\.babyLogTheme) private var theme
    let model: CryingModel

    var body: some View {
        AppCard {
            VStack(alignment: .leading, spacing: theme.spacing.m) {
                CardHeader(icon: "ic_tag", title: "Причины и что помогало", trailing: "по \(model.episodes) \(plural(model.episodes, one: "разу", few: "разам", many: "разам"))")
                if model.reasons.isEmpty && model.helped.isEmpty {
                    Text("Отмечайте после плача, почему он был и что помогло — здесь станет видно, что повторяется")
                        .appTextStyle(theme.typography.bodyMedium)
                        .foregroundStyle(theme.colors.onSurfaceVariant)
                }
                if !model.reasons.isEmpty {
                    causeRows(title: "Почему, по меткам", rows: model.reasons, color: theme.chartColors.crying)
                }
                if !model.helped.isEmpty {
                    causeRows(title: "Что помогло", rows: model.helped, color: theme.colors.secondary)
                }
            }
        }
        .frame(maxWidth: .infinity)
    }

    private func causeRows(title: String, rows: [(tag: String, count: Int)], color: Color) -> some View {
        VStack(alignment: .leading, spacing: theme.spacing.xs) {
            Text(title).appTextStyle(theme.typography.titleSmall)
            ForEach(rows.indices, id: \.self) { index in
                let row = rows[index]
                CryingBarRow(
                    label: row.tag,
                    fraction: Float(row.count) / Float(model.episodes),
                    value: "\(row.count)",
                    color: color,
                    valueWidth: cryingCountWidth
                )
            }
        }
    }
}

/** Строка: подпись, полоса доли ширины, значение справа. VoiceOver читает её целиком. */
private struct CryingBarRow: View {
    @Environment(\.babyLogTheme) private var theme
    let label: String
    let fraction: Float
    let value: String
    let color: Color
    let valueWidth: CGFloat

    var body: some View {
        HStack(spacing: theme.spacing.s) {
            Text(label)
                .appTextStyle(theme.typography.bodySmall)
                .foregroundStyle(theme.colors.onSurfaceVariant)
                .lineLimit(2)
                .truncationMode(.tail)
                .frame(width: cryingRowLabelWidth, alignment: .leading)
            GeometryReader { geometry in
                UnevenRoundedRectangle(topLeadingRadius: 0, bottomLeadingRadius: 0, bottomTrailingRadius: 4, topTrailingRadius: 4)
                    .fill(color)
                    .frame(width: geometry.size.width * CGFloat(min(max(fraction, cryingMinBar), 1)), height: cryingBarHeight)
            }
            .frame(maxWidth: .infinity)
            .frame(height: cryingBarHeight)
            Text(value)
                .appTextStyle(theme.typography.bodyMedium)
                .lineLimit(1)
                .frame(width: valueWidth, alignment: .trailing)
        }
        .frame(maxWidth: .infinity)
        .accessibilityElement(children: .combine)
    }
}
