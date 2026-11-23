import Observation
import SwiftUI

@MainActor @Observable final class WeightSheetModel {
    var weightText: String
    let time: TimeAgoPickerModel
    var editTime: Int64
    var note: String
    let isEdit: Bool

    init(initial: Weight?) {
        weightText = initial?.grams.description ?? ""
        time = TimeAgoPickerModel()
        editTime = initial?.start ?? 0
        note = initial?.note ?? ""
        isEdit = initial != nil
    }

    var grams: Int? {
        guard let grams = Int(weightText), Weight.range.contains(grams) else { return nil }
        return grams
    }

    var supportingText: String {
        if let grams, grams >= 1_000 { return "= \(formatWeight(grams))" }
        if grams == nil, !weightText.isEmpty { return "От \(Weight.range.lowerBound) до \(Weight.range.upperBound) г" }
        return "Например, 4350"
    }

    var trimmedNote: String { note.trimmingCharacters(in: .whitespacesAndNewlines) }

    func updateWeight(_ text: String) {
        weightText = String(text.filter(\.isNumber).prefix(5))
    }

    func time(clock: any Clock) -> Int64 {
        resolveTime(isEdit: isEdit, minutesAgo: time.minutesAgo, editTime: editTime, clock: clock)
    }
}

/** Вес вводят в граммах; рядом показывается значение в килограммах. */
struct WeightSheet: View {
    @Environment(\.babyLogTheme) private var theme
    @Environment(\.clock) private var clock
    @Binding var isPresented: Bool
    @State private var model: WeightSheetModel
    let onDismiss: () -> Void
    let onSave: (Int, Int64, String) -> Void

    init(
        isPresented: Binding<Bool>,
        initial: Weight?,
        onDismiss: @escaping () -> Void,
        onSave: @escaping (Int, Int64, String) -> Void
    ) {
        _isPresented = isPresented
        _model = State(initialValue: WeightSheetModel(initial: initial))
        self.onDismiss = onDismiss
        self.onSave = onSave
    }

    var body: some View {
        AppSheet(
            isPresented: $isPresented,
            onDismiss: onDismiss,
            title: "Вес",
            icon: "ic_weight",
            confirm: SheetAction(label: "Сохранить", enabled: model.grams != nil) {
                if let grams = model.grams { onSave(grams, model.time(clock: clock), model.trimmedNote) }
            }
        ) { _ in
            VStack(alignment: .leading, spacing: theme.spacing.xs) {
                EditorTextField(value: $model.weightText, label: "Вес, г", keyboard: .numberPad)
                    .onChange(of: model.weightText) { _, text in model.updateWeight(text) }
                Text(model.supportingText)
                    .appTextStyle(theme.typography.bodySmall)
                    .foregroundStyle(theme.colors.onSurfaceVariant)
            }
            TimeSection(isEdit: model.isEdit, model: model.time, editTime: $model.editTime)
            NoteField(value: $model.note)
        }
    }
}
