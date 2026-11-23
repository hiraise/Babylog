import Observation
import SwiftUI

enum FeedingChoice: Hashable, Sendable {
    case left
    case right
    case bottle

    var label: String {
        switch self {
        case .left: "Левая грудь"
        case .right: "Правая грудь"
        case .bottle: "Бутылочка"
        }
    }

    var breast: Breast? {
        switch self {
        case .left: .left
        case .right: .right
        case .bottle: nil
        }
    }
}

@MainActor @Observable final class FeedingStartSheetModel {
    var choice: FeedingChoice?
    var volume: String
    var milk: Milk?
    let time: TimeAgoPickerModel

    init(activeSleep: Sleep?, lastBottle: Bottle?) {
        volume = lastBottle?.volumeMl.description ?? ""
        milk = lastBottle?.milk
        time = TimeAgoPickerModel(finishing: activeSleep.map { [FinishingTimer(what: "Сон", start: $0.start)] } ?? [])
    }

    var volumeMl: Int? { bottleVolume(volume) }
    var valid: Bool { choice == .bottle ? volumeMl != nil : choice != nil }
}

@MainActor @Observable final class FeedingEditSheetModel {
    var breast: Breast
    var start: Int64
    var end: Int64?
    var note: String

    init(initial: Feeding) {
        breast = initial.breast
        start = initial.start
        end = initial.end
        note = initial.note
    }

    var valid: Bool { end == nil || end! >= start }
    var trimmedNote: String { note.trimmingCharacters(in: .whitespacesAndNewlines) }

    func changeStart(_ newStart: Int64) {
        if end == start { end = newStart }
        start = newStart
    }
}

/** Начало кормления грудью — таймер; бутылочка — разовая запись с объёмом и содержимым. */
struct FeedingStartSheet: View {
    @Environment(\.clock) private var clock
    @Binding var isPresented: Bool
    @State private var model: FeedingStartSheetModel
    let onDismiss: () -> Void
    let onSave: (Breast, Int64) -> Void
    let onSaveBottle: (Int, Milk?, Int64) -> Void

    init(
        isPresented: Binding<Bool>,
        activeSleep: Sleep?,
        lastBottle: Bottle?,
        onDismiss: @escaping () -> Void,
        onSave: @escaping (Breast, Int64) -> Void,
        onSaveBottle: @escaping (Int, Milk?, Int64) -> Void
    ) {
        _isPresented = isPresented
        _model = State(initialValue: FeedingStartSheetModel(activeSleep: activeSleep, lastBottle: lastBottle))
        self.onDismiss = onDismiss
        self.onSave = onSave
        self.onSaveBottle = onSaveBottle
    }

    var body: some View {
        AppSheet(
            isPresented: $isPresented,
            onDismiss: onDismiss,
            title: "Кормление",
            icon: "ic_feeding",
            confirm: SheetAction(
                label: model.choice == .bottle ? "Сохранить" : "Запустить таймер",
                enabled: model.valid
            ) {
                let time = resolveTime(isEdit: false, minutesAgo: model.time.minutesAgo, editTime: 0, clock: clock)
                if model.choice == .bottle, let volume = model.volumeMl {
                    onSaveBottle(volume, model.milk, time)
                } else if let breast = model.choice?.breast {
                    onSave(breast, time)
                }
            }
        ) { _ in
            OptionChips(
                options: FeedingChoice.allCases.map { ($0, $0.label) },
                selected: model.choice,
                onSelect: { model.choice = $0 }
            )
            if model.choice == .bottle {
                BottleFields(volume: $model.volume, milk: $model.milk)
            }
            TimeAgoPicker(model: model.time)
        }
    }
}

/** Правка кормления: грудь, начало и, у законченного кормления, конец. */
struct FeedingEditSheet: View {
    @Environment(\.babyLogTheme) private var theme
    @Binding var isPresented: Bool
    @State private var model: FeedingEditSheetModel
    let onDismiss: () -> Void
    let onSave: (Breast, Int64, Int64?, String) -> Void

    init(
        isPresented: Binding<Bool>,
        initial: Feeding,
        onDismiss: @escaping () -> Void,
        onSave: @escaping (Breast, Int64, Int64?, String) -> Void
    ) {
        _isPresented = isPresented
        _model = State(initialValue: FeedingEditSheetModel(initial: initial))
        self.onDismiss = onDismiss
        self.onSave = onSave
    }

    var body: some View {
        AppSheet(
            isPresented: $isPresented,
            onDismiss: onDismiss,
            title: "Кормление",
            icon: "ic_feeding",
            confirm: SheetAction(label: "Сохранить", enabled: model.valid) {
                onSave(model.breast, model.start, model.end, model.trimmedNote)
            }
        ) { _ in
            OptionChips(
                options: [(Breast.left, "Левая грудь"), (Breast.right, "Правая грудь")],
                selected: model.breast,
                onSelect: { model.breast = $0 }
            )
            DateTimeField(label: "Начало:", value: model.start, onChange: model.changeStart)
            if model.end != nil {
                DateTimeField(label: "Конец:", value: model.end!, onChange: { model.end = $0 })
                if !model.valid {
                    Text("Конец кормления не может быть раньше начала")
                        .appTextStyle(theme.typography.bodySmall)
                        .foregroundStyle(theme.colors.error)
                }
            }
            NoteField(value: $model.note)
        }
    }
}

extension FeedingChoice: CaseIterable {}
