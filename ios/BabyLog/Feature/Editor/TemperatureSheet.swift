import Observation
import SwiftUI

/** Ширина поля температуры: «38,4» крупно и место под «°C». */
private let temperatureFieldWidth: CGFloat = 140
/** С чего начинается значение, если прошлого замера нет: обычная температура подмышкой. */
private let defaultTenths = 366

/**
 Значение из поля температуры (ADR-0041): «38,4», «38.4» и «384» — 38,4 °C, «38» — 38,0 °C. Вне
 допустимого диапазона или неполное («3», «38,») — nil.
 */
func parseTemperature(_ text: String) -> Int? {
    let value = text.trimmingCharacters(in: .whitespacesAndNewlines).replacingOccurrences(of: ".", with: ",")
    guard !value.isEmpty, !value.hasSuffix(",") else { return nil }
    let tenths: Int?
    if value.contains(",") {
        let parts = value.split(separator: ",", maxSplits: 1, omittingEmptySubsequences: false)
        guard parts.count == 2, parts[1].count == 1, let whole = Int(parts[0]), let fraction = Int(parts[1]) else {
            return nil
        }
        tenths = whole * 10 + fraction
    } else {
        guard let number = Int(value) else { return nil }
        tenths = number < 100 ? number * 10 : number
    }
    guard let tenths, Temperature.range.contains(tenths) else { return nil }
    return tenths
}

/** Набор в поле: цифры и одна запятая (точку клавиатуры меняем на запятую), после запятой — одна цифра. */
func temperatureInput(_ text: String) -> String {
    let cleaned = text.replacingOccurrences(of: ".", with: ",").filter { $0.isNumber || $0 == "," }
    guard let comma = cleaned.firstIndex(of: ",") else { return String(cleaned.prefix(3)) }
    let whole = cleaned[..<comma].prefix(2)
    let fraction = cleaned[cleaned.index(after: comma)...].filter(\.isNumber).prefix(1)
    return "\(whole),\(fraction)"
}

@MainActor @Observable final class TemperatureSheetModel {
    var text: String
    var method: TemperatureMethod?
    let time: TimeAgoPickerModel
    var editTime: Int64
    var note: String
    var startIllness = false
    let isEdit: Bool
    let offerIllness: Bool
    let previous: Temperature?

    init(initial: Temperature?, last: Temperature?, offerIllness: Bool) {
        text = temperatureValue(initial?.tenths ?? last?.tenths ?? defaultTenths)
        method = initial?.method ?? last?.method
        time = TimeAgoPickerModel()
        editTime = initial?.start ?? 0
        note = initial?.note ?? ""
        isEdit = initial != nil
        self.offerIllness = offerIllness
        previous = initial == nil ? last : nil
    }

    var tenths: Int? { parseTemperature(text) }
    var trimmedNote: String { note.trimmingCharacters(in: .whitespacesAndNewlines) }
    var shouldStartIllness: Bool { offerIllness && startIllness }

    func updateText(_ text: String) {
        self.text = temperatureInput(text)
    }

    func step(_ delta: Int) {
        let current = tenths ?? defaultTenths
        text = temperatureValue(min(max(current + delta, Temperature.range.lowerBound), Temperature.range.upperBound))
    }

    func selectMethod(_ selected: TemperatureMethod) {
        method = method == selected ? nil : selected
    }

    func time(clock: any Clock) -> Int64 {
        resolveTime(isEdit: isEdit, minutesAgo: time.minutesAgo, editTime: editTime, clock: clock)
    }
}

/**
 Лист температуры (ADR-0041): число с клавиатуры, «−» и «+» по 0,1, где мерили, когда и комментарий.
 Значение и место начинаются с прошлого замера. Порога для начала болезни нет.
 */
struct TemperatureSheet: View {
    @Environment(\.babyLogTheme) private var theme
    @Environment(\.clock) private var clock
    @Binding var isPresented: Bool
    @State private var model: TemperatureSheetModel
    let sex: Sex?
    let onDismiss: () -> Void
    let onSave: (Int, TemperatureMethod?, Int64, String, Bool) -> Void
    let onSaveWithMedicine: ((Int, TemperatureMethod?, Int64, String) -> Void)?

    init(
        isPresented: Binding<Bool>,
        initial: Temperature?,
        last: Temperature?,
        sex: Sex?,
        offerIllness: Bool,
        onDismiss: @escaping () -> Void,
        onSave: @escaping (Int, TemperatureMethod?, Int64, String, Bool) -> Void,
        onSaveWithMedicine: ((Int, TemperatureMethod?, Int64, String) -> Void)? = nil
    ) {
        _isPresented = isPresented
        _model = State(initialValue: TemperatureSheetModel(initial: initial, last: last, offerIllness: offerIllness))
        self.sex = sex
        self.onDismiss = onDismiss
        self.onSave = onSave
        self.onSaveWithMedicine = onSaveWithMedicine
    }

    var body: some View {
        AppSheet(
            isPresented: $isPresented,
            onDismiss: onDismiss,
            title: "Температура",
            icon: "ic_thermometer",
            confirm: SheetAction(label: "Сохранить", enabled: model.tenths != nil) {
                if let tenths = model.tenths {
                    onSave(tenths, model.method, model.time(clock: clock), model.trimmedNote, model.shouldStartIllness)
                }
            },
            secondary: !model.isEdit ? onSaveWithMedicine.map { save in
                SheetAction(label: "И лекарство", enabled: model.tenths != nil) {
                    if let tenths = model.tenths {
                        save(tenths, model.method, model.time(clock: clock), model.trimmedNote)
                    }
                }
            } : nil
        ) { _ in
            TemperatureField(text: $model.text, previous: model.previous)
                .onChange(of: model.text) { _, text in model.updateText(text) }
            VStack(alignment: .leading, spacing: theme.spacing.xs) {
                Text("Где мерили").appTextStyle(theme.typography.titleSmall)
                OptionChips(
                    options: TemperatureMethod.allCases.map { ($0, temperatureMethodLabel($0)) },
                    selected: model.method,
                    onSelect: model.selectMethod
                )
            }
            TimeSection(isEdit: model.isEdit, model: model.time, editTime: $model.editTime)
            if model.offerIllness, !model.isEdit {
                Toggle(isOn: $model.startIllness) {
                    HStack(spacing: theme.spacing.m) {
                        AppIcon("ic_illness", tint: theme.colors.tertiary)
                        VStack(alignment: .leading) {
                            Text(bySex(sex, masculine: "Заболел", feminine: "Заболела"))
                                .appTextStyle(theme.typography.bodyLarge)
                            Text("болезнь начнётся с этого замера")
                                .appTextStyle(theme.typography.bodySmall)
                                .foregroundStyle(theme.colors.onSurfaceVariant)
                        }
                    }
                }
                .tint(theme.colors.tertiary)
            }
            NoteField(value: $model.note)
        }
    }
}

/** Значение крупно посередине, по краям — «−» и «+» на 0,1; под ним — прошлый замер. */
struct TemperatureField: View {
    @Environment(\.babyLogTheme) private var theme
    @Environment(\.clock) private var clock
    @Environment(\.timeZone) private var timeZone
    @Binding var text: String
    let previous: Temperature?

    var body: some View {
        VStack(spacing: theme.spacing.xs) {
            HStack(spacing: theme.spacing.l) {
                stepButton(icon: "ic_remove", label: "Меньше на 0,1", delta: -1)
                EditorTextField(
                    value: $text,
                    label: "",
                    suffix: "°C",
                    keyboard: .decimalPad,
                    isError: parseTemperature(text) == nil
                )
                .multilineTextAlignment(.center)
                .frame(width: temperatureFieldWidth)
                .accessibilityLabel("Температура")
                stepButton(icon: "ic_add", label: "Больше на 0,1", delta: 1)
            }
            if let hint {
                Text(hint)
                    .appTextStyle(theme.typography.bodySmall)
                    .foregroundStyle(theme.colors.onSurfaceVariant)
            }
        }
        .frame(maxWidth: .infinity)
    }

    private var hint: String? {
        if parseTemperature(text) == nil { return "От 34,0 до 43,0 °C" }
        guard let previous else { return nil }
        let now = clock.millis()
        return "в прошлый раз \(formatShortDay(toLocalDate(previous.start, timeZone: timeZone), today: toLocalDate(now, timeZone: timeZone), timeZone: timeZone)) в \(formatClock(previous.start, timeZone: timeZone)) — \(temperatureValue(previous.tenths))"
    }

    private func stepButton(icon: String, label: String, delta: Int) -> some View {
        Button {
            let current = parseTemperature(text) ?? defaultTenths
            text = temperatureValue(min(max(current + delta, Temperature.range.lowerBound), Temperature.range.upperBound))
        } label: {
            AppIcon(icon, contentDescription: label, tint: theme.colors.primary)
                .frame(width: BabyLogSizes.touchTarget, height: BabyLogSizes.touchTarget)
                .background(theme.colors.surfaceContainerHighest, in: Circle())
        }
        .buttonStyle(.plain)
    }
}
