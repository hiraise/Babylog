import SwiftUI

private let chipDateFormat = "d MMM yyyy"

/** Поле «дата + время»: подпись и два чипа, открывающие пикеры в нижних листах. Значение — epoch millis. */
struct DateTimeField: View {
    @Environment(\.babyLogTheme) private var theme
    @Environment(\.timeZone) private var timeZone
    @State private var showDate = false
    @State private var showTime = false
    @State private var draft = Date()

    let label: String
    let value: Int64
    let onChange: (Int64) -> Void

    var body: some View {
        let date = Date(timeIntervalSince1970: Double(value) / 1000)
        VStack(alignment: .leading, spacing: theme.spacing.xs) {
            Text(label).appTextStyle(theme.typography.titleSmall)
            FlowRow(spacing: theme.spacing.s) {
                ChoiceChip(selected: false, label: formatChipDate(date, timeZone: timeZone), icon: "ic_calendar") {
                    draft = date
                    showDate = true
                }
                ChoiceChip(selected: false, label: formatClock(value, timeZone: timeZone), icon: "ic_schedule") {
                    draft = date
                    showTime = true
                }
            }
        }
        if showDate {
            DateSheet(
                isPresented: $showDate,
                initial: date,
                confirmLabel: "ОК",
                onDismiss: { showDate = false }
            ) { picked in
                onChange(replacingDate(value, with: picked, timeZone: timeZone))
                showDate = false
            }
        }
        if showTime {
            AppSheet(
                isPresented: $showTime,
                onDismiss: { showTime = false },
                title: "Время",
                icon: "ic_schedule",
                confirm: SheetAction(label: "ОК") {
                    onChange(replacingTime(value, with: draft, timeZone: timeZone))
                    showTime = false
                },
                scrollable: false
            ) { _ in
                DatePicker("Время", selection: $draft, displayedComponents: .hourAndMinute)
                    .datePickerStyle(.wheel)
                    .labelsHidden()
                    .environment(\.locale, Locale(identifier: "ru_RU"))
                    .environment(\.timeZone, timeZone)
                    .frame(maxWidth: .infinity)
            }
        }
    }
}

/** Выбор даты рождения малыша. `initial` и результат — epoch millis локальной полуночи. */
struct BirthDateSheet: View {
    @Environment(\.timeZone) private var timeZone
    @Binding var isPresented: Bool
    let initial: Int64?
    let onDismiss: () -> Void
    let onSave: (Int64) -> Void

    var body: some View {
        DateSheet(
            isPresented: $isPresented,
            initial: initial.map { Date(timeIntervalSince1970: Double($0) / 1000) },
            confirmLabel: "Сохранить",
            onDismiss: onDismiss
        ) { date in
            var calendar = Calendar(identifier: .gregorian)
            calendar.timeZone = timeZone
            onSave(Int64(calendar.startOfDay(for: date).timeIntervalSince1970 * 1000))
        }
    }
}

/** Выбор дня не позже сегодняшнего: «с какого дня» название болезни, когда выздоровел. */
struct DayPickSheet: View {
    @Binding var isPresented: Bool
    let initial: Date?
    let onDismiss: () -> Void
    let onPick: (Date) -> Void

    var body: some View {
        DateSheet(isPresented: $isPresented, initial: initial, confirmLabel: "ОК", onDismiss: onDismiss, onPick: onPick)
    }
}

/** Календарь в нижнем листе; будущие дни недоступны, потому что дневник описывает уже случившееся. */
private struct DateSheet: View {
    @Environment(\.clock) private var clock
    @Environment(\.timeZone) private var timeZone
    @Binding var isPresented: Bool
    @State private var draft: Date
    @State private var selected: Bool

    let initial: Date?
    let confirmLabel: String
    let onDismiss: () -> Void
    let onPick: (Date) -> Void

    init(
        isPresented: Binding<Bool>,
        initial: Date?,
        confirmLabel: String,
        onDismiss: @escaping () -> Void,
        onPick: @escaping (Date) -> Void
    ) {
        _isPresented = isPresented
        _draft = State(initialValue: initial ?? Date())
        _selected = State(initialValue: initial != nil)
        self.initial = initial
        self.confirmLabel = confirmLabel
        self.onDismiss = onDismiss
        self.onPick = onPick
    }

    var body: some View {
        AppSheet(
            isPresented: $isPresented,
            onDismiss: onDismiss,
            confirm: SheetAction(label: confirmLabel, enabled: selected) {
                onPick(draft)
                isPresented = false
            },
            scrollable: false,
            edgeToEdge: true
        ) { _ in
            DatePicker("Дата", selection: $draft, in: ...clock.now(), displayedComponents: .date)
                .datePickerStyle(.graphical)
                .labelsHidden()
                .environment(\.locale, Locale(identifier: "ru_RU"))
                .environment(\.timeZone, timeZone)
                .onChange(of: draft) { _, _ in selected = true }
                .frame(maxWidth: .infinity)
        }
    }
}

func replacingDate(_ value: Int64, with date: Date, timeZone: TimeZone) -> Int64 {
    var calendar = Calendar(identifier: .gregorian)
    calendar.timeZone = timeZone
    let current = Date(timeIntervalSince1970: Double(value) / 1000)
    let day = calendar.dateComponents([.year, .month, .day], from: date)
    let time = calendar.dateComponents([.hour, .minute, .second], from: current)
    var components = DateComponents()
    components.year = day.year
    components.month = day.month
    components.day = day.day
    components.hour = time.hour
    components.minute = time.minute
    components.second = time.second
    return Int64((calendar.date(from: components) ?? current).timeIntervalSince1970 * 1000)
}

func replacingTime(_ value: Int64, with time: Date, timeZone: TimeZone) -> Int64 {
    var calendar = Calendar(identifier: .gregorian)
    calendar.timeZone = timeZone
    let current = Date(timeIntervalSince1970: Double(value) / 1000)
    let day = calendar.dateComponents([.year, .month, .day], from: current)
    let clock = calendar.dateComponents([.hour, .minute], from: time)
    var components = DateComponents()
    components.year = day.year
    components.month = day.month
    components.day = day.day
    components.hour = clock.hour
    components.minute = clock.minute
    components.second = 0
    return Int64((calendar.date(from: components) ?? current).timeIntervalSince1970 * 1000)
}

private func formatChipDate(_ date: Date, timeZone: TimeZone) -> String {
    let formatter = DateFormatter()
    formatter.locale = Locale(identifier: "ru_RU")
    formatter.calendar = Calendar(identifier: .gregorian)
    formatter.timeZone = timeZone
    formatter.dateFormat = chipDateFormat
    return formatter.string(from: date)
}

/** Чипы переносятся по строкам, сохраняя собственную ширину и доступную высоту нажатия. */
struct FlowRow: Layout {
    var spacing: CGFloat = 8
    var rowSpacing: CGFloat = 0

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        measure(proposal: proposal, subviews: subviews).size
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let result = measure(proposal: ProposedViewSize(width: bounds.width, height: bounds.height), subviews: subviews)
        for (index, subview) in subviews.enumerated() {
            subview.place(
                at: CGPoint(x: bounds.minX + result.positions[index].x, y: bounds.minY + result.positions[index].y),
                proposal: .unspecified
            )
        }
    }

    private func measure(proposal: ProposedViewSize, subviews: Subviews) -> (size: CGSize, positions: [CGPoint]) {
        guard !subviews.isEmpty else { return (.zero, []) }
        let naturalWidth = subviews.reduce(CGFloat.zero) { $0 + $1.sizeThatFits(.unspecified).width + spacing } - spacing
        let width = proposal.width ?? naturalWidth
        var positions: [CGPoint] = []
        var x: CGFloat = 0
        var y: CGFloat = 0
        var rowHeight: CGFloat = 0
        var rowWidth: CGFloat = 0
        for subview in subviews {
            let size = subview.sizeThatFits(.unspecified)
            if x > 0, x + size.width > width {
                rowWidth = max(rowWidth, x - spacing)
                x = 0
                y += rowHeight + rowSpacing
                rowHeight = 0
            }
            positions.append(CGPoint(x: x, y: y))
            x += size.width + spacing
            rowHeight = max(rowHeight, size.height)
        }
        rowWidth = max(rowWidth, x - spacing)
        return (CGSize(width: proposal.width ?? rowWidth, height: y + rowHeight), positions)
    }
}
