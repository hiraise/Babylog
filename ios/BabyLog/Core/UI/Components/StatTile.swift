import SwiftUI

/** Плитка обзора «Аналитики»: название, главное число, пояснение, сравнение и мини-график внизу. */
struct StatTile<Chart: View>: View {
    @Environment(\.babyLogTheme) private var theme

    let icon: String
    let title: String
    let value: String
    let unit: String?
    let note: String?
    let onClick: () -> Void
    let chart: Chart?

    init(
        icon: String,
        title: String,
        value: String,
        unit: String? = nil,
        note: String? = nil,
        onClick: @escaping () -> Void,
        @ViewBuilder chart: () -> Chart
    ) {
        self.icon = icon
        self.title = title
        self.value = value
        self.unit = unit
        self.note = note
        self.onClick = onClick
        self.chart = chart()
    }

    var body: some View {
        let spacing = theme.spacing
        AppCard(
            onClick: onClick,
            onClickLabel: "подробнее",
            contentPadding: EdgeInsets(top: spacing.m, leading: spacing.m, bottom: spacing.m, trailing: spacing.m)
        ) {
            VStack(alignment: .leading, spacing: spacing.xs) {
                HStack(spacing: spacing.s) {
                    AppIcon(icon, size: BabyLogSizes.chipIcon, tint: theme.colors.primary)
                    Text(title)
                        .appTextStyle(theme.typography.titleSmall)
                        .lineLimit(1)
                        .truncationMode(.tail)
                }
                VStack(alignment: .leading, spacing: 0) {
                    Text(value)
                        .appTextStyle(theme.typography.headlineSmall)
                        .lineLimit(1)
                        .minimumScaleFactor(0.7)
                    if let unit {
                        Text(unit)
                            .appTextStyle(theme.typography.bodySmall)
                            .foregroundStyle(theme.colors.onSurfaceVariant)
                            .lineLimit(1)
                    }
                    if let note {
                        Text(note)
                            .appTextStyle(theme.typography.bodySmall)
                            .foregroundStyle(theme.colors.onSurfaceVariant)
                            .lineLimit(2)
                            .truncationMode(.tail)
                    }
                }
                if let chart {
                    Spacer(minLength: 0)
                    chart
                }
            }
            .frame(maxHeight: .infinity)
        }
    }
}

extension StatTile where Chart == EmptyView {
    init(
        icon: String,
        title: String,
        value: String,
        unit: String? = nil,
        note: String? = nil,
        onClick: @escaping () -> Void
    ) {
        self.icon = icon
        self.title = title
        self.value = value
        self.unit = unit
        self.note = note
        self.onClick = onClick
        self.chart = nil
    }
}
