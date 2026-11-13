import Observation
import SwiftUI

private let timeOptions = [("Сейчас", 0), ("−5 мин", 5), ("−15 мин", 15), ("−30 мин", 30), ("−1 ч", 60)]

/** Своё время — не больше суток назад: давнее записывают правкой, с датой и временем. */
let maxMinutesAgo = 24 * 60 - 1

/** Таймер, который закончится, когда начнётся новый. Конец не раньше его начала. */
struct FinishingTimer: Hashable, Sendable {
    var what: String
    var start: Int64
}

/** Состояние выбора «когда это было»: чип, своё число минут и идущие часы под формой. */
@MainActor @Observable final class TimeAgoPickerModel {
    var minutesAgo: Int
    var custom: String?
    var now: Int64?
    let finishing: [FinishingTimer]

    init(minutesAgo: Int = 0, now: Int64? = nil, finishing: [FinishingTimer] = []) {
        self.minutesAgo = minutesAgo
        self.now = now
        self.finishing = finishing
    }

    func select(_ minutes: Int) {
        custom = nil
        minutesAgo = minutes
    }

    func beginCustom() {
        custom = ""
        minutesAgo = 0
    }

    func updateCustom(_ typed: String) {
        custom = customMinutesInput(typed)
        minutesAgo = customMinutes(custom ?? "")
    }

    func tick(clock: any Clock) {
        now = clock.millis()
    }

    func time(now: Int64? = nil) -> Int64 {
        (now ?? self.now ?? 0) - Int64(minutesAgo) * 60_000
    }

    var hint: String {
        customHint(custom, minutesAgo: minutesAgo)
    }
}

/** «Когда»: сейчас, N минут назад или своё число; под времени — завершаемые новым событием таймеры. */
struct TimeAgoPicker: View {
    @Environment(\.babyLogTheme) private var theme
    @Environment(\.clock) private var clock
    @Environment(\.timeZone) private var timeZone
    @State private var model: TimeAgoPickerModel

    init(model: TimeAgoPickerModel) {
        _model = State(initialValue: model)
    }

    var body: some View {
        let currentNow = model.now ?? clock.millis()
        let time = model.time(now: currentNow)
        VStack(alignment: .leading, spacing: theme.spacing.xs) {
            Text("Когда:").appTextStyle(theme.typography.titleSmall)
            FlowRow(spacing: theme.spacing.s) {
                ForEach(timeOptions, id: \.1) { label, minutes in
                    ChoiceChip(selected: model.custom == nil && model.minutesAgo == minutes, label: label) {
                        model.select(minutes)
                    }
                }
                CustomMinutesChip(text: model.custom, onText: model.updateCustom)
            }
            Text("Время: \(formatClock(time, timeZone: timeZone))\(model.hint)")
                .appTextStyle(theme.typography.bodySmall)
                .foregroundStyle(theme.colors.onSurfaceVariant)
            ForEach(model.finishing, id: \.self) { timer in
                Text("\(timer.what) закончится в \(formatClock(max(timer.start, time), timeZone: timeZone))")
                    .appTextStyle(theme.typography.bodySmall)
                    .foregroundStyle(theme.colors.onSurfaceVariant)
            }
        }
        .onAppear { model.tick(clock: clock) }
        .task {
            while !Task.isCancelled {
                model.tick(clock: clock)
                try? await Task.sleep(for: .seconds(1))
            }
        }
    }
}

/** Чип «Другое» превращается на месте в поле минут, поэтому лист не меняет высоту. */
private struct CustomMinutesChip: View {
    @Environment(\.babyLogTheme) private var theme
    @FocusState private var focused: Bool
    let text: String?
    let onText: (String) -> Void

    var body: some View {
        if let text {
            HStack(spacing: 0) {
                Text("−")
                TextField("Минут назад", text: Binding(get: { text }, set: onText))
                    .keyboardType(.numberPad)
                    .focused($focused)
                    .frame(minWidth: BabyLogSizes.icon)
                    .fixedSize()
                    .accessibilityLabel("Минут назад")
                Text(" мин")
            }
            .appTextStyle(theme.typography.labelLarge)
            .foregroundStyle(theme.colors.onPrimary)
            .padding(.horizontal, theme.spacing.m)
            .frame(minWidth: BabyLogSizes.touchTarget, minHeight: BabyLogSizes.chipHeight)
            .background(theme.colors.primary, in: RoundedRectangle(cornerRadius: theme.shapes.small))
            .cardShadow(shadow: theme.shadows.card)
            .onAppear { focused = true }
        } else {
            ChoiceChip(selected: false, label: "Другое") { onText("") }
        }
    }
}

/** Ввод своих минут: только цифры, не больше четырёх. */
func customMinutesInput(_ text: String) -> String {
    String(text.filter(\.isNumber).prefix(4))
}

/** Пустой ввод — сейчас; значение больше суток ограничивается сутками без минуты. */
func customMinutes(_ text: String) -> Int {
    min(Int(text) ?? 0, maxMinutesAgo)
}

/** Для своих минут от часа добавляется длительность, за пределами суток — пояснение об ограничении. */
func customHint(_ text: String?, minutesAgo: Int) -> String {
    guard let text else { return "" }
    if (Int(text) ?? 0) > maxMinutesAgo { return " · не больше суток" }
    return minutesAgo >= 60 ? " · \(formatDuration(Int64(minutesAgo) * 60_000)) назад" : ""
}

/** Добавление использует чипы «N минут назад», редактирование — полноценные дату и время. */
struct TimeSection: View {
    let isEdit: Bool
    let model: TimeAgoPickerModel
    @Binding var editTime: Int64

    var body: some View {
        if isEdit {
            DateTimeField(label: "Когда:", value: editTime, onChange: { editTime = $0 })
        } else {
            TimeAgoPicker(model: model)
        }
    }
}

/** Время записи: при правке — выбранное, при добавлении — «сейчас минус N минут». */
func resolveTime(isEdit: Bool, minutesAgo: Int, editTime: Int64, clock: any Clock) -> Int64 {
    isEdit ? editTime : clock.millis() - Int64(minutesAgo) * 60_000
}

struct OptionChips<T: Hashable>: View {
    @Environment(\.babyLogTheme) private var theme
    let options: [(value: T, label: String)]
    let selected: T?
    let onSelect: (T) -> Void
    let icon: (T) -> String?

    init(
        options: [(value: T, label: String)],
        selected: T?,
        onSelect: @escaping (T) -> Void,
        icon: @escaping (T) -> String? = { _ in nil }
    ) {
        self.options = options
        self.selected = selected
        self.onSelect = onSelect
        self.icon = icon
    }

    var body: some View {
        FlowRow(spacing: theme.spacing.s) {
            ForEach(options.indices, id: \.self) { index in
                let option = options[index]
                ChoiceChip(selected: selected == option.value, label: option.label, icon: icon(option.value)) {
                    onSelect(option.value)
                }
            }
        }
    }
}

struct NoteField: View {
    @Binding var value: String
    let label: String

    init(value: Binding<String>, label: String = "Комментарий (необязательно)") {
        _value = value
        self.label = label
    }

    var body: some View {
        EditorTextField(value: $value, label: label, axis: .vertical)
    }
}

/** Поле редактора в стиле Material: рамка и подпись на её верхней границе, цвета только из темы. */
struct EditorTextField: View {
    @Environment(\.babyLogTheme) private var theme
    @Binding var value: String
    let label: String
    var suffix: String? = nil
    var axis: Axis = .horizontal
    var keyboard: UIKeyboardType = .default
    var isError = false
    var onSubmit: () -> Void = {}

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: theme.shapes.extraSmall, style: .circular)
        ZStack(alignment: .topLeading) {
            HStack(spacing: theme.spacing.s) {
                TextField("", text: $value, axis: axis)
                    .textFieldStyle(.plain)
                    .textInputAutocapitalization(.sentences)
                    .keyboardType(keyboard)
                    .tint(theme.colors.primary)
                    .appTextStyle(theme.typography.bodyLarge)
                    .onSubmit(onSubmit)
                if let suffix {
                    Text(suffix)
                        .appTextStyle(theme.typography.bodyLarge)
                        .foregroundStyle(theme.colors.onSurfaceVariant)
                }
            }
            .padding(.horizontal, theme.spacing.m)
            .frame(maxWidth: .infinity, minHeight: BabyLogSizes.textFieldMinHeight, alignment: .leading)
            .background(theme.colors.surfaceContainerHigh, in: shape)
            .overlay(shape.stroke(isError ? theme.colors.error : theme.colors.onSurfaceVariant, lineWidth: 1))
            Text(label)
                .appTextStyle(theme.typography.bodySmall)
                .foregroundStyle(isError ? theme.colors.error : theme.colors.onSurfaceVariant)
                .padding(.horizontal, theme.spacing.xs)
                .background(theme.colors.surfaceContainerHigh)
                .padding(.leading, theme.spacing.m)
                .offset(y: -theme.spacing.xs)
        }
        .padding(.top, theme.spacing.xs)
    }
}
