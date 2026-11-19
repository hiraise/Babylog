import SwiftUI

/**
 Полоса советов на «Сегодня» (ADR-0035): под сводкой дня, чтобы кнопки записей оставались наверху.
 Касание открывает раздел «Развитие». «Новые» — пока советы этой недели не открывали (`hasNewTips`).
 */
struct WeekTipsBanner: View {
    @Environment(\.babyLogTheme) private var theme

    let week: DevelopmentWeek
    let isNew: Bool
    let onClick: () -> Void

    var body: some View {
        AppCard(
            onClick: onClick,
            onClickLabel: "открыть «Развитие»",
            containerColor: theme.colors.primaryContainer,
            contentColor: theme.colors.onPrimaryContainer
        ) {
            HStack(spacing: theme.spacing.m) {
                AppIcon("ic_lightbulb")
                VStack(alignment: .leading, spacing: theme.spacing.xs) {
                    HStack(spacing: theme.spacing.s) {
                        Text("Советы на \(weekAccusative(week.week))")
                            .appTextStyle(theme.typography.titleSmall)
                        if isNew {
                            Text("новые")
                                .appTextStyle(theme.typography.labelSmall)
                                .foregroundStyle(theme.colors.onPrimary)
                                .padding(.horizontal, theme.spacing.s)
                                .background(theme.colors.primary, in: RoundedRectangle(cornerRadius: theme.shapes.extraSmall, style: .circular))
                        }
                    }
                    Text(bannerSummary(week)).appTextStyle(theme.typography.bodySmall)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                AppIcon("ic_chevron_right")
            }
        }
    }
}

/** «Сон, кормление, безопасность · 7 советов»: темы недели по порядку и сколько всего советов. */
func bannerSummary(_ week: DevelopmentWeek) -> String {
    var seen = Set<TipCategory>()
    let topics = week.allTips.map(\.category).filter { seen.insert($0).inserted }
        .sorted { $0.order < $1.order }
        .enumerated()
        .map { index, category in index == 0 ? category.title : category.title.lowercased() }
    let count = week.allTips.count
    return "\(topics.joined(separator: ", ")) · \(count) \(plural(count, one: "совет", few: "совета", many: "советов"))"
}
