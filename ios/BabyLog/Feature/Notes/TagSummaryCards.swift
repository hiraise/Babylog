import SwiftUI

private let tagCellHeight: CGFloat = 34
private let tagCellRadius: CGFloat = 9
private let tagDotSize: CGFloat = 4
private let swatchSize: CGFloat = 12
private let swatchRadius: CGFloat = 4
private let timeStripHeight: CGFloat = 24
private let timeTrackHeight: CGFloat = 16
private let startDotDiameter: CGFloat = 10
private let dayMinutes = 24 * 60
private let hourLabels = [0, 6, 12, 18, 24]

/**
 Сводка метки (ADR-0031) над записями с ней: календарь, время суток, что было до, что помогало
 и какие метки рядом. Карточки без данных не показываются: у метки только из заметок нет плача.
 */
struct TagSummaryItems: View {
    @Environment(\.babyLogTheme) private var theme
    let summary: TagSummary
    let sex: Sex?
    let records: Int
    let onTag: (String) -> Void

    var body: some View {
        TagDaysCard(summary: summary)
        if summary.cryings > 0 {
            TagTimeCard(summary: summary)
        }
        if summary.awakeKnown > 0 || summary.sinceFeeding != nil {
            TagBeforeCard(summary: summary, sex: sex)
        }
        if !summary.helped.isEmpty || !summary.together.isEmpty {
            TagHelpedCard(summary: summary, onTag: onTag)
        }
        Text("Записи · \(records)")
            .appTextStyle(theme.typography.titleSmall)
            .foregroundStyle(theme.colors.onSurfaceVariant)
            .padding(.horizontal, theme.spacing.xs)
            .frame(maxWidth: .infinity, alignment: .leading)
            .accessibilityAddTraits(.isHeader)
    }
}

/** Заголовок карточки сводки и справа — пояснение мелко. */
private struct SummaryHeader: View {
    @Environment(\.babyLogTheme) private var theme
    let title: String
    var trailing: String?

    var body: some View {
        HStack(spacing: theme.spacing.s) {
            Text(title)
                .appTextStyle(theme.typography.titleMedium)
                .frame(maxWidth: .infinity, alignment: .leading)
                .accessibilityAddTraits(.isHeader)
            if let trailing {
                Text(trailing)
                    .appTextStyle(theme.typography.bodySmall)
                    .foregroundStyle(theme.colors.onSurfaceVariant)
                    .multilineTextAlignment(.trailing)
            }
        }
    }
}

/**
 Метка и сколько раз она была, календарь последних `tagWeeks` недель: сутки с плачем — цветом
 ряда «плач», заметка — точкой. Сегодня — жирным, будущие сутки — пустые.
 */
private struct TagDaysCard: View {
    @Environment(\.babyLogTheme) private var theme
    @Environment(\.timeZone) private var timeZone
    let summary: TagSummary

    var body: some View {
        let crying = theme.chartColors.crying
        let counts = [
            summary.cryings > 0 ? "плач \(summary.cryings) \(plural(summary.cryings, one: "раз", few: "раза", many: "раз"))" : nil,
            summary.notes > 0 ? "\(summary.notes) \(plural(summary.notes, one: "заметка", few: "заметки", many: "заметок"))" : nil,
        ].compactMap { $0 }.joined(separator: " · ")
        let cryingDates = summary.days.filter { $0.cryings > 0 }.map { formatTagDay($0.date, timeZone: timeZone) }
        let noteDates = summary.days.filter(\.note).map { formatTagDay($0.date, timeZone: timeZone) }
        let descriptions = [
            cryingDates.isEmpty ? nil : "Плач с меткой: \(cryingDates.joined(separator: ", "))",
            noteDates.isEmpty ? nil : "заметки: \(noteDates.joined(separator: ", "))",
        ].compactMap { $0 }
        let description = descriptions.isEmpty
            ? "За \(tagWeeks) недели записей с меткой нет"
            : descriptions.joined(separator: "; ")

        AppCard {
            VStack(spacing: theme.spacing.s) {
                SummaryHeader(title: summary.tag, trailing: counts)
                Text("По дням · последние \(tagWeeks) недели")
                    .appTextStyle(theme.typography.bodySmall)
                    .foregroundStyle(theme.colors.onSurfaceVariant)
                    .frame(maxWidth: .infinity, alignment: .leading)
                VStack(spacing: theme.spacing.xs) {
                    LazyVGrid(columns: tagColumns, spacing: theme.spacing.xs) {
                        ForEach(["Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"], id: \.self) { day in
                            Text(day)
                                .appTextStyle(theme.typography.labelSmall)
                                .foregroundStyle(theme.colors.onSurfaceVariant)
                                .frame(maxWidth: .infinity)
                        }
                    }
                    LazyVGrid(columns: tagColumns, spacing: theme.spacing.xs) {
                        ForEach(summary.days, id: \.date) { day in
                            let cried = day.cryings > 0
                            ZStack {
                                RoundedRectangle(cornerRadius: tagCellRadius)
                                    .fill(cried ? crying : theme.colors.surfaceContainerHigh)
                                if !day.future {
                                    VStack(spacing: 0) {
                                        Text(String(dayNumber(day.date, timeZone: timeZone)))
                                            .appTextStyle(theme.typography.labelSmall)
                                            .fontWeight(day.today ? .bold : nil)
                                            .foregroundStyle(cried ? theme.colors.surface : theme.colors.onSurfaceVariant)
                                        Circle()
                                            .fill(!day.note ? .clear : cried ? theme.colors.surface : theme.colors.onSurface)
                                            .frame(width: tagDotSize, height: tagDotSize)
                                    }
                                }
                            }
                            .frame(height: tagCellHeight)
                        }
                    }
                }
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(description)
                HStack(spacing: theme.spacing.l) {
                    HStack(spacing: theme.spacing.xs) {
                        RoundedRectangle(cornerRadius: swatchRadius)
                            .fill(crying)
                            .frame(width: swatchSize, height: swatchSize)
                        Text("плач")
                            .appTextStyle(theme.typography.bodySmall)
                            .foregroundStyle(theme.colors.onSurfaceVariant)
                    }
                    HStack(spacing: theme.spacing.xs) {
                        Circle()
                            .fill(theme.colors.onSurface)
                            .frame(width: tagDotSize, height: tagDotSize)
                        Text("заметка")
                            .appTextStyle(theme.typography.bodySmall)
                            .foregroundStyle(theme.colors.onSurfaceVariant)
                    }
                }
            }
        }
        .frame(maxWidth: .infinity)
    }
}

private var tagColumns: [GridItem] {
    Array(repeating: GridItem(.flexible(), spacing: 4), count: 7)
}

private func formatTagDay(_ date: Date, timeZone: TimeZone) -> String {
    let formatter = DateFormatter()
    formatter.locale = RU
    formatter.calendar = Calendar(identifier: .gregorian)
    formatter.timeZone = timeZone
    formatter.dateFormat = "d MMM"
    return formatter.string(from: date)
}

private func dayNumber(_ date: Date, timeZone: TimeZone) -> Int {
    var calendar = Calendar(identifier: .gregorian)
    calendar.timeZone = timeZone
    return calendar.component(.day, from: date)
}

/**
 Когда начинался плач с меткой: точки на шкале суток, «чаще» — подложкой и словами справа.
 Полупрозрачные точки ложатся друг на друга, и где плакал чаще, цвет гуще.
 */
private struct TagTimeCard: View {
    @Environment(\.babyLogTheme) private var theme
    let summary: TagSummary

    var body: some View {
        let crying = theme.chartColors.crying
        let trailing = summary.peak.map { "\($0.count) из \(summary.cryings) — с \($0.from) до \($0.to) ч" } ?? "в разное время"
        let description = summary.cryingStarts.sorted().map {
            String(format: "%02d:%02d", $0 / 60, $0 % 60)
        }.joined(separator: ", ")

        AppCard {
            VStack(spacing: theme.spacing.s) {
                SummaryHeader(title: "Время суток", trailing: trailing)
                Canvas { context, size in
                    let top = (size.height - timeTrackHeight) / 2
                    let track = CGRect(x: 0, y: top, width: size.width, height: timeTrackHeight)
                    context.fill(
                        Path(roundedRect: track, cornerRadius: swatchRadius),
                        with: .color(theme.colors.surfaceContainerHigh)
                    )
                    if let peak = summary.peak {
                        let from = Double(peak.from) / 24
                        let to = peak.to > peak.from ? Double(peak.to) / 24 : 1
                        context.fill(
                            Path(CGRect(x: from * size.width, y: top, width: (to - from) * size.width, height: timeTrackHeight)),
                            with: .color(crying.opacity(0.22))
                        )
                        if peak.to <= peak.from && peak.to > 0 {
                            context.fill(
                                Path(CGRect(x: 0, y: top, width: Double(peak.to) / 24 * size.width, height: timeTrackHeight)),
                                with: .color(crying.opacity(0.22))
                            )
                        }
                    }
                    for minute in summary.cryingStarts {
                        let x = Double(minute) / Double(dayMinutes) * size.width
                        let rect = CGRect(
                            x: x - startDotDiameter / 2,
                            y: size.height / 2 - startDotDiameter / 2,
                            width: startDotDiameter,
                            height: startDotDiameter
                        )
                        context.fill(Path(ellipseIn: rect), with: .color(crying.opacity(0.55)))
                    }
                }
                .frame(height: timeStripHeight)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("Начало плача: \(description)")
                HStack {
                    ForEach(hourLabels, id: \.self) { hour in
                        Text(String(hour))
                            .appTextStyle(theme.typography.bodySmall)
                            .foregroundStyle(theme.colors.onSurfaceVariant)
                        if hour != hourLabels.last { Spacer() }
                    }
                }
            }
        }
        .frame(maxWidth: .infinity)
    }
}

/** Что было перед плачем с меткой: бодрствование против обычного и сколько прошло после кормления. */
private struct TagBeforeCard: View {
    @Environment(\.babyLogTheme) private var theme
    let summary: TagSummary
    let sex: Sex?

    var body: some View {
        let awake = bySex(sex, masculine: "не спал", feminine: "не спала")
        AppCard {
            VStack(spacing: theme.spacing.s) {
                SummaryHeader(title: "Что было до", trailing: "плач с меткой")
                if summary.awakeKnown > 0 {
                    BeforeLine(
                        icon: "ic_sleep",
                        text: summary.longerAwake == 0
                            ? "Ни разу перед плачем бодрствование не было дольше обычного"
                            : summary.longerAwake == summary.awakeKnown
                                ? "Каждый раз перед плачем \(awake) дольше обычного"
                                : "В \(summary.longerAwake) из \(summary.awakeKnown) раз \(awake) дольше обычного"
                    )
                }
                if let sinceFeeding = summary.sinceFeeding {
                    BeforeLine(icon: "ic_feeding", text: "После кормления — обычно через \(formatDuration(sinceFeeding))")
                }
                Text("Это совпадения в записях, а не причины")
                    .appTextStyle(theme.typography.bodySmall)
                    .foregroundStyle(theme.colors.onSurfaceVariant)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
        .frame(maxWidth: .infinity)
    }
}

private struct BeforeLine: View {
    @Environment(\.babyLogTheme) private var theme
    let icon: String
    let text: String

    var body: some View {
        HStack(spacing: theme.spacing.m) {
            AppIcon(icon, size: BabyLogSizes.contextIcon, tint: theme.colors.primary)
            Text(text).appTextStyle(theme.typography.bodyMedium)
        }
    }
}

/**
 Что помогало при плаче с меткой и какие метки стояли рядом — подписями с числом раз.
 Метка из «Часто вместе» нажимается и открывает свою сводку.
 */
private struct TagHelpedCard: View {
    @Environment(\.babyLogTheme) private var theme
    let summary: TagSummary
    let onTag: (String) -> Void

    var body: some View {
        AppCard {
            VStack(spacing: theme.spacing.s) {
                if !summary.helped.isEmpty {
                    SummaryHeader(title: "Что помогало")
                    FlowRow(spacing: theme.spacing.s, rowSpacing: theme.spacing.s) {
                        ForEach(summary.helped, id: \.name) { item in
                            TagLabel(tag: item.name, count: item.count)
                        }
                    }
                }
                if !summary.together.isEmpty {
                    SummaryHeader(title: "Часто вместе")
                    FlowRow(spacing: theme.spacing.s) {
                        ForEach(summary.together, id: \.name) { item in
                            TagLabel(tag: item.name, count: item.count, onClick: { onTag(item.name) })
                        }
                    }
                }
            }
        }
        .frame(maxWidth: .infinity)
    }
}
