import Observation
import SwiftUI

/** Лист роста — как лист веса, но рост вводят в сантиметрах с десятыми: «62,5» (`heightInput`). */
@MainActor @Observable final class HeightSheetModel {
    var heightText: String
    let time: TimeAgoPickerModel
    var editTime: Int64
    var note: String
    let isEdit: Bool

    init(initial: Height?) {
        heightText = initial.map { heightCentimeters($0.millimeters) } ?? ""
        time = TimeAgoPickerModel()
        editTime = initial?.start ?? 0
        note = initial?.note ?? ""
        isEdit = initial != nil
    }

    var millimeters: Int? { parseHeight(heightText) }
    var trimmedNote: String { note.trimmingCharacters(in: .whitespacesAndNewlines) }

    func updateHeight(_ text: String) {
        heightText = heightInput(text)
    }

    func time(clock: any Clock) -> Int64 {
        resolveTime(isEdit: isEdit, minutesAgo: time.minutesAgo, editTime: editTime, clock: clock)
    }
}

struct HeightSheet: View {
    @Environment(\.babyLogTheme) private var theme
    @Environment(\.clock) private var clock
    @Binding var isPresented: Bool
    @State private var model: HeightSheetModel
    let onDismiss: () -> Void
    let onSave: (Int, Int64, String) -> Void

    init(
        isPresented: Binding<Bool>,
        initial: Height?,
        onDismiss: @escaping () -> Void,
        onSave: @escaping (Int, Int64, String) -> Void
    ) {
        _isPresented = isPresented
        _model = State(initialValue: HeightSheetModel(initial: initial))
        self.onDismiss = onDismiss
        self.onSave = onSave
    }

    var body: some View {
        AppSheet(
            isPresented: $isPresented,
            onDismiss: onDismiss,
            title: "Рост",
            icon: "ic_height",
            confirm: SheetAction(label: "Сохранить", enabled: model.millimeters != nil) {
                if let millimeters = model.millimeters {
                    onSave(millimeters, model.time(clock: clock), model.trimmedNote)
                }
            }
        ) { _ in
            VStack(alignment: .leading, spacing: theme.spacing.xs) {
                EditorTextField(value: $model.heightText, label: "Рост, см", keyboard: .decimalPad)
                    .onChange(of: model.heightText) { _, text in model.updateHeight(text) }
                Text(model.millimeters == nil && !model.heightText.isEmpty
                    ? "От \(heightCentimeters(Height.range.lowerBound)) до \(formatHeight(Height.range.upperBound))"
                    : "Например, 62,5")
                    .appTextStyle(theme.typography.bodySmall)
                    .foregroundStyle(theme.colors.onSurfaceVariant)
            }
            TimeSection(isEdit: model.isEdit, model: model.time, editTime: $model.editTime)
            NoteField(value: $model.note)
        }
    }
}

/**
 Ввод роста: сантиметры и не больше одной цифры после запятой — «62», «62,5». Точка становится
 запятой (на части клавиатур запятой нет), остальное отбрасывается.
 */
func heightInput(_ text: String) -> String {
    let normalized = text.replacingOccurrences(of: ".", with: ",").filter { $0.isNumber || $0 == "," }
    let parts = normalized.split(separator: ",", maxSplits: 1, omittingEmptySubsequences: false)
    let whole = String(parts.first ?? "").prefix(3)
    guard normalized.contains(",") else { return String(whole) }
    let fraction = parts.count > 1 ? String(parts[1].filter(\.isNumber).prefix(1)) : ""
    return "\(whole),\(fraction)"
}

/** Миллиметры из ввода `heightInput`: «62,5» → 625, «62» и «62,» → 620; пусто или вне диапазона — nil. */
func parseHeight(_ text: String) -> Int? {
    let parts = text.split(separator: ",", maxSplits: 1, omittingEmptySubsequences: false)
    guard let whole = Int(parts.first ?? "") else { return nil }
    let tenths = parts.count > 1 ? Int(parts[1]) ?? 0 : 0
    let millimeters = whole * 10 + tenths
    return Height.range.contains(millimeters) ? millimeters : nil
}
