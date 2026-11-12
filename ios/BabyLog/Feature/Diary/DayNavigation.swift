import SwiftUI

/** Дни с записями плюс сегодня, по возрастанию: по ним листается история. */
func historyDays(events: [any BabyEvent], today: Date, timeZone: TimeZone = .current) -> [Date] {
    var calendar = Calendar(identifier: .gregorian)
    calendar.timeZone = timeZone
    let days = events.map { toLocalDate($0.start, timeZone: timeZone) } + [calendar.startOfDay(for: today)]
    return Array(Set(days)).sorted()
}

struct DayNavRow: View {
    @Environment(\.babyLogTheme) private var theme
    @Environment(\.timeZone) private var timeZone

    let selectedDate: Date
    let today: Date
    let hasPrev: Bool
    let hasNext: Bool
    let onPrev: () -> Void
    let onNext: () -> Void
    let onPick: () -> Void

    var body: some View {
        HStack(spacing: 0) {
            Button(action: onPrev) {
                AppIcon("ic_chevron_left", contentDescription: "Предыдущий день")
                    .frame(width: BabyLogSizes.touchTarget, height: BabyLogSizes.touchTarget)
            }
            .disabled(!hasPrev)
            Button(action: onPick) {
                HStack(spacing: theme.spacing.s) {
                    AppIcon("ic_calendar")
                    Text(formatDayHeader(selectedDate, today: today, timeZone: timeZone))
                        .appTextStyle(theme.typography.titleMedium)
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)
                        .truncationMode(.tail)
                }
                .frame(maxWidth: .infinity)
            }
            .buttonStyle(.plain)
            .foregroundStyle(theme.colors.primary)
            Button(action: onNext) {
                AppIcon("ic_chevron_right", contentDescription: "Следующий день")
                    .frame(width: BabyLogSizes.touchTarget, height: BabyLogSizes.touchTarget)
            }
            .disabled(!hasNext)
        }
        .padding(.top, theme.spacing.xs)
    }
}

/** Список дней с записями (новые сверху) и числом записей в каждом; тап по дню выбирает его. */
struct DaySelectorSheet: View {
    @Environment(\.babyLogTheme) private var theme
    @Environment(\.timeZone) private var timeZone

    @Binding var isPresented: Bool
    let days: [Date]
    let events: [any BabyEvent]
    let selectedDate: Date
    let today: Date
    let onDismiss: () -> Void
    let onPick: (Date) -> Void

    var body: some View {
        let counts = Dictionary(grouping: events, by: { toLocalDate($0.start, timeZone: timeZone) }).mapValues(\.count)
        AppSheet(
            isPresented: $isPresented,
            onDismiss: onDismiss,
            title: "Выбрать день",
            icon: "ic_calendar",
            scrollable: false,
            edgeToEdge: true
        ) { closer in
            ScrollView {
                LazyVStack(spacing: 0) {
                    ForEach(days.sorted(by: >), id: \.self) { day in
                        Button {
                            closer.close { onPick(day) }
                        } label: {
                            HStack(spacing: theme.spacing.m) {
                                Text(formatDayHeader(day, today: today, timeZone: timeZone))
                                    .appTextStyle(theme.typography.bodyLarge)
                                    .fontWeight(day == selectedDate ? .semibold : nil)
                                    .foregroundStyle(day == selectedDate ? theme.colors.primary : theme.colors.onSurface)
                                Spacer()
                                Text(String(counts[day] ?? 0))
                                    .appTextStyle(theme.typography.bodyMedium)
                                    .foregroundStyle(theme.colors.onSurfaceVariant)
                            }
                            .padding(.vertical, theme.spacing.m)
                            .padding(.horizontal, theme.spacing.l)
                            .frame(maxWidth: .infinity, minHeight: BabyLogSizes.touchTarget)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityAddTraits(day == selectedDate ? [.isSelected] : [])
                    }
                }
            }
            .frame(maxHeight: BabyLogSizes.daySelectorMaxHeight)
        }
    }
}
