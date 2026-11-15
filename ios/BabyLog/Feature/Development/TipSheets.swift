import SwiftUI

/** Иконка темы совета; у «когда показать педиатру» — стетоскоп, какой бы ни была тема. */
extension Tip {
    var icon: String {
        kind == .doctor ? "ic_stethoscope" : category.icon
    }
}

extension TipCategory {
    var icon: String {
        switch self {
        case .sleep: "ic_sleep"
        case .feeding: "ic_feeding"
        case .development: "ic_development"
        case .safety: "ic_shield"
        case .crying: "ic_crying"
        case .health: "ic_health"
        }
    }

    var order: Int {
        Self.allCases.firstIndex(of: self) ?? 0
    }
}

/** Возраст совета целыми месяцами: «для 0–2 мес» — с месяца начала до месяца конца включительно. */
func tipAgeText(_ tip: Tip) -> String {
    let from = Int((Double(tip.ages.lowerBound) / daysPerMonth).rounded(.down))
    let to = Int((Double(tip.ages.upperBound + 1) / daysPerMonth).rounded(.up))
    return "для \(from)–\(to) мес"
}

/** Строка совета в списке: иконка темы, заголовок, краткий текст; нажимается вся строка. */
struct TipRow: View {
    @Environment(\.babyLogTheme) private var theme

    let tip: Tip
    let onOpen: () -> Void

    var body: some View {
        Button(action: onOpen) {
            ItemRow(icon: tip.icon, title: tip.title, subtitle: tip.summary) {
                AppIcon("ic_chevron_right")
            }
            .padding(.vertical, theme.spacing.s)
            .frame(maxWidth: .infinity, alignment: .leading)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityHint("открыть совет")
    }
}

/**
 Строка «когда показать педиатру» в жёлтом блоке: без иконки — стетоскоп уже в заголовке блока,
 а зелёная иконка строки спорила бы с его цветом. Текст — цвета содержимого блока.
 */
struct DoctorTipRow: View {
    @Environment(\.babyLogTheme) private var theme

    let tip: Tip
    let onOpen: () -> Void

    var body: some View {
        Button(action: onOpen) {
            HStack(spacing: theme.spacing.m) {
                VStack(alignment: .leading, spacing: 0) {
                    Text(tip.title).appTextStyle(theme.typography.bodyLarge)
                    Text(tip.summary).appTextStyle(theme.typography.bodySmall)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                AppIcon("ic_chevron_right")
            }
            .padding(.vertical, theme.spacing.s)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityHint("открыть совет")
    }
}

/** Совет целиком — нижний лист (ADR-0018). */
struct TipSheet: View {
    @Binding var isPresented: Bool
    let tip: Tip
    let onDismiss: () -> Void

    var body: some View {
        AppSheet(isPresented: $isPresented, onDismiss: onDismiss, title: tip.title, icon: tip.icon) { _ in
            TipDetails(tip: tip)
        }
    }
}

/**
 Текст совета (ADR-0037): тема и возраст, краткий текст, подробности, пункты, сразу после них —
 «Источники расходятся», если позиции разные (тексты ссылаются на этот блок «см. ниже»), затем
 источники ссылками и дата проверки. Ссылку открывает браузер только по нажатию, само приложение
 в сеть не ходит (ADR-0002); браузера нет — нажатие ничего не делает.
 */
struct TipDetails: View {
    @Environment(\.babyLogTheme) private var theme

    let tip: Tip

    var body: some View {
        let colors = theme.colors
        let typography = theme.typography
        VStack(alignment: .leading, spacing: theme.spacing.m) {
            let kind = tip.kind == .doctor ? "Когда показать педиатру" : tip.category.title
            Text("\(kind) · \(tipAgeText(tip))")
                .appTextStyle(typography.bodySmall)
                .foregroundStyle(colors.onSurfaceVariant)
            Text(tip.summary).appTextStyle(typography.titleSmall)
            Text(tip.body).appTextStyle(typography.bodyMedium)
            if !tip.points.isEmpty {
                VStack(alignment: .leading, spacing: theme.spacing.xs) {
                    ForEach(tip.points, id: \.self) { point in
                        HStack(alignment: .top, spacing: theme.spacing.s) {
                            Text("•")
                                .appTextStyle(typography.bodyMedium)
                                .foregroundStyle(colors.primary)
                            Text(point).appTextStyle(typography.bodyMedium)
                        }
                    }
                }
            }
            if !tip.disagreement.isEmpty {
                VStack(alignment: .leading, spacing: theme.spacing.xs) {
                    Text("Источники расходятся").appTextStyle(typography.titleSmall)
                    ForEach(tip.disagreement, id: \.source) { position in
                        (Text(position.source).fontWeight(.semibold) + Text(" — \(position.position)"))
                            .appTextStyle(typography.bodyMedium)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                }
                .padding(theme.spacing.m)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(colors.surfaceContainerHighest, in: RoundedRectangle(cornerRadius: theme.shapes.medium, style: .circular))
            }
            TipSources(tip: tip)
            Text("Проверено \(reviewedText(tip.reviewedAt)). Справочная информация, не заменяет консультацию врача.")
                .appTextStyle(typography.bodySmall)
                .foregroundStyle(colors.onSurfaceVariant)
        }
    }
}

private struct TipSources: View {
    @Environment(\.babyLogTheme) private var theme

    let tip: Tip

    var body: some View {
        VStack(alignment: .leading, spacing: theme.spacing.s) {
            Text("Источники").appTextStyle(theme.typography.titleSmall)
            ForEach(tip.sources, id: \.url) { source in
                VStack(alignment: .leading, spacing: 0) {
                    if let url = URL(string: source.url) {
                        Link(source.name, destination: url)
                            .appTextStyle(theme.typography.bodyMedium)
                            .foregroundStyle(theme.colors.primary)
                            .underline()
                    } else {
                        Text(source.name).appTextStyle(theme.typography.bodyMedium)
                    }
                    Text(source.section)
                        .appTextStyle(theme.typography.bodySmall)
                        .foregroundStyle(theme.colors.onSurfaceVariant)
                }
            }
        }
    }
}

/**
 Все советы недели — нижний лист с чипами тем: «Все» и темы, которые есть на этой неделе.
 Касание совета закрывает лист и открывает совет (`onOpen`).
 */
struct AllTipsSheet: View {
    @Environment(\.babyLogTheme) private var theme
    @Binding var isPresented: Bool
    @State private var category: TipCategory?

    let week: DevelopmentWeek
    let onOpen: (Tip) -> Void
    let onDismiss: () -> Void

    var body: some View {
        let categories = week.allTips.map(\.category).uniqued().sorted { $0.order < $1.order }
        AppSheet(isPresented: $isPresented, onDismiss: onDismiss, title: "Советы на \(weekAccusative(week.week))", icon: "ic_lightbulb") { closer in
            FlowRow(spacing: theme.spacing.s) {
                ChoiceChip(selected: category == nil, label: "Все") { category = nil }
                ForEach(categories, id: \.self) { option in
                    ChoiceChip(selected: category == option, label: option.title) { category = option }
                }
            }
            VStack(alignment: .leading, spacing: 0) {
                ForEach(week.allTips.filter { category == nil || $0.category == category }, id: \.id) { tip in
                    TipRow(tip: tip) {
                        closer.close { onOpen(tip) }
                    }
                }
            }
        }
    }
}

private func reviewedText(_ date: Date) -> String {
    let formatter = DateFormatter()
    formatter.calendar = Calendar(identifier: .gregorian)
    formatter.locale = Locale(identifier: "en_US_POSIX")
    formatter.timeZone = TimeZone(secondsFromGMT: 0)
    formatter.dateFormat = "dd.MM.yyyy"
    return formatter.string(from: date)
}

private extension Array where Element: Hashable {
    func uniqued() -> [Element] {
        var seen = Set<Element>()
        return filter { seen.insert($0).inserted }
    }
}

/** Ссылка на источник таблицы или совета; открывает браузер только по нажатию (ADR-0002). */
struct SourceLink: View {
    @Environment(\.babyLogTheme) private var theme

    let source: TipSource

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(linkText).appTextStyle(theme.typography.bodySmall)
            Text(source.section)
                .appTextStyle(theme.typography.bodySmall)
                .foregroundStyle(theme.colors.onSurfaceVariant)
        }
    }

    private var linkText: AttributedString {
        var text = AttributedString("Источник: \(source.name)")
        if let range = text.range(of: source.name), let url = URL(string: source.url) {
            text[range].link = url
            text[range].foregroundColor = theme.colors.primary
            text[range].underlineStyle = .single
        }
        return text
    }
}
