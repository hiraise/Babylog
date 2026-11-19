import SwiftUI

/**
 «Болезни» в «Заметках» (ADR-0040): идущая — под «Сейчас», на фоне болезни; прошлые — по месяцам
 начала, свежие сверху. Касание карточки открывает экран болезни.
 */
struct IllnessEntriesView: View {
    @Environment(\.babyLogTheme) private var theme
    @Environment(\.timeZone) private var timeZone
    let entries: [IllnessEntry]
    let today: Date
    let onOpen: (Illness) -> Void

    var body: some View {
        let groups = illnessGroups(entries: entries, today: today, timeZone: timeZone)
        ForEach(groups, id: \.header) { group in
            Text(group.header)
                .appTextStyle(theme.typography.titleSmall)
                .foregroundStyle(theme.colors.onSurfaceVariant)
                .padding(.horizontal, theme.spacing.xs)
                .frame(maxWidth: .infinity, alignment: .leading)
                .accessibilityAddTraits(.isHeader)
            ForEach(group.entries, id: \.illness.id) { entry in
                IllnessEntryCard(entry: entry, onOpen: { onOpen(entry.illness) })
            }
        }
    }
}

private func illnessGroups(
    entries: [IllnessEntry],
    today: Date,
    timeZone: TimeZone
) -> [(header: String, entries: [IllnessEntry])] {
    var calendar = Calendar(identifier: .gregorian)
    calendar.timeZone = timeZone
    let thisYear = calendar.component(.year, from: today)
    var groups: [(header: String, entries: [IllnessEntry])] = []
    let active = entries.filter(\.illness.isActive)
    if !active.isEmpty { groups.append(("Сейчас", active)) }
    for entry in entries where !entry.illness.isActive {
        let start = entry.illness.startDate(timeZone)
        let formatter = DateFormatter()
        formatter.locale = RU
        formatter.calendar = calendar
        formatter.timeZone = timeZone
        formatter.dateFormat = calendar.component(.year, from: start) == thisYear ? "LLLL" : "LLLL yyyy"
        let raw = formatter.string(from: start)
        let header = raw.prefix(1).uppercased() + raw.dropFirst()
        if let index = groups.firstIndex(where: { $0.header == header }) {
            groups[index].entries.append(entry)
        } else {
            groups.append((header, [entry]))
        }
    }
    return groups
}

/**
 Карточка болезни: идущая — «Болеет · 3-й день», под ней названия и с какого дня; прошлая — названия
 («Болезнь» без них), дни, самая высокая температура и чем лечили.
 */
private struct IllnessEntryCard: View {
    @Environment(\.babyLogTheme) private var theme
    let entry: IllnessEntry
    let onOpen: () -> Void

    var body: some View {
        let active = entry.illness.isActive
        let muted = active ? theme.colors.tertiary : theme.colors.onSurfaceVariant
        let max = entry.maxTenths.map { "макс. \(temperatureValue($0))" }
        let title = active ? "Болеет · \(entry.days)-й день" : entry.title ?? "Болезнь"
        let lines = active
            ? [[entry.title ?? "без названия", entry.range, max].compactMap { $0 }.joined(separator: " · ")]
            : [
                [entry.range, "\(entry.days) \(plural(entry.days, one: "день", few: "дня", many: "дней"))", max]
                    .compactMap { $0 }.joined(separator: " · "),
                entry.medicines.isEmpty ? nil : entry.medicines.joined(separator: ", "),
            ].compactMap { $0 }

        AppCard(
            onClick: onOpen,
            onClickLabel: "открыть болезнь",
            contentPadding: EdgeInsets(
                top: theme.spacing.m,
                leading: theme.spacing.l,
                bottom: theme.spacing.m,
                trailing: theme.spacing.l
            ),
            containerColor: active ? theme.colors.illnessContainer : theme.colors.surfaceContainerLow,
            contentColor: active ? theme.colors.onTertiaryContainer : theme.colors.onSurface
        ) {
            HStack(alignment: .top, spacing: theme.spacing.m) {
                AppIcon("ic_illness", tint: active ? theme.colors.tertiary : theme.colors.primary)
                VStack(alignment: .leading, spacing: 0) {
                    Text(title).appTextStyle(theme.typography.bodyLarge)
                    ForEach(lines, id: \.self) { line in
                        Text(line)
                            .appTextStyle(theme.typography.bodySmall)
                            .foregroundStyle(muted)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                AppIcon("ic_chevron_right", tint: muted)
            }
        }
        .frame(maxWidth: .infinity)
    }
}
