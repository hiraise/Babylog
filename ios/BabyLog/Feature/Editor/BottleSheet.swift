import Observation
import SwiftUI

private let quickVolumes = [60, 90, 120, 150]

@MainActor @Observable final class BottleSheetModel {
    var volume: String
    var milk: Milk?
    var time: Int64
    var note: String

    init(initial: Bottle) {
        volume = initial.volumeMl.description
        milk = initial.milk
        time = initial.start
        note = initial.note
    }

    var volumeMl: Int? { bottleVolume(volume) }
    var trimmedNote: String { note.trimmingCharacters(in: .whitespacesAndNewlines) }
}

/** Правка бутылочки: объём, что внутри, время и комментарий. */
struct BottleSheet: View {
    @Binding var isPresented: Bool
    @State private var model: BottleSheetModel
    let onDismiss: () -> Void
    let onSave: (Int, Milk?, Int64, String) -> Void

    init(
        isPresented: Binding<Bool>,
        initial: Bottle,
        onDismiss: @escaping () -> Void,
        onSave: @escaping (Int, Milk?, Int64, String) -> Void
    ) {
        _isPresented = isPresented
        _model = State(initialValue: BottleSheetModel(initial: initial))
        self.onDismiss = onDismiss
        self.onSave = onSave
    }

    var body: some View {
        AppSheet(
            isPresented: $isPresented,
            onDismiss: onDismiss,
            title: "Бутылочка",
            icon: "ic_bottle",
            confirm: SheetAction(label: "Сохранить", enabled: model.volumeMl != nil) {
                if let volume = model.volumeMl { onSave(volume, model.milk, model.time, model.trimmedNote) }
            }
        ) { _ in
            BottleFields(volume: $model.volume, milk: $model.milk)
            DateTimeField(label: "Когда:", value: model.time, onChange: { model.time = $0 })
            NoteField(value: $model.note)
        }
    }
}

/** Объём вводят числом или быстрым чипом; повторное касание содержимого снимает выбор. */
struct BottleFields: View {
    @Environment(\.babyLogTheme) private var theme
    @Binding var volume: String
    @Binding var milk: Milk?

    private var invalid: Bool { !volume.isEmpty && bottleVolume(volume) == nil }

    var body: some View {
        VStack(alignment: .leading, spacing: theme.spacing.m) {
            OptionChips(
                options: quickVolumes.map { ($0, "\($0) мл") },
                selected: Int(volume),
                onSelect: { volume = $0.description }
            )
            VStack(alignment: .leading, spacing: theme.spacing.xs) {
                EditorTextField(value: $volume, label: "Объём", suffix: "мл", keyboard: .numberPad, isError: invalid)
                    .onChange(of: volume) { _, typed in
                        let filtered = String(typed.filter(\.isNumber).prefix(3))
                        if filtered != typed { volume = filtered }
                    }
                if invalid {
                    Text("От \(Bottle.range.lowerBound) до \(Bottle.range.upperBound) мл")
                        .appTextStyle(theme.typography.bodySmall)
                        .foregroundStyle(theme.colors.error)
                }
            }
            VStack(alignment: .leading, spacing: theme.spacing.xs) {
                Text("Что в бутылочке:").appTextStyle(theme.typography.titleSmall)
                FlowRow(spacing: theme.spacing.s) {
                    ForEach(Milk.allCases, id: \.self) { option in
                        ChoiceChip(
                            selected: milk == option,
                            label: milkLabel(option).prefix(1).uppercased() + milkLabel(option).dropFirst()
                        ) {
                            milk = milk == option ? nil : option
                        }
                    }
                }
            }
        }
    }
}

/** Объём из текста поля; вне допустимого диапазона или не число — nil. */
func bottleVolume(_ text: String) -> Int? {
    guard let volume = Int(text), Bottle.range.contains(volume) else { return nil }
    return volume
}
