import Observation
import SwiftUI

@MainActor @Observable final class SleepEditSheetModel {
    var start: Int64
    var end: Int64?
    var note: String

    init(initial: Sleep) {
        start = initial.start
        end = initial.end
        note = initial.note
    }

    var valid: Bool { end == nil || end! >= start }
    var trimmedNote: String { note.trimmingCharacters(in: .whitespacesAndNewlines) }
}

/** Начало сна: запускает таймер и заранее показывает, когда закончатся идущие кормление и плач. */
struct SleepStartSheet: View {
    @Binding var isPresented: Bool
    @Environment(\.clock) private var clock
    @State private var time: TimeAgoPickerModel

    let onDismiss: () -> Void
    let onSave: (Int64) -> Void

    init(
        isPresented: Binding<Bool>,
        activeFeeding: Feeding?,
        activeCrying: Crying?,
        onDismiss: @escaping () -> Void,
        onSave: @escaping (Int64) -> Void
    ) {
        _isPresented = isPresented
        var finishing: [FinishingTimer] = []
        if let activeFeeding { finishing.append(FinishingTimer(what: "Кормление", start: activeFeeding.start)) }
        if let activeCrying { finishing.append(FinishingTimer(what: "Плач", start: activeCrying.start)) }
        _time = State(initialValue: TimeAgoPickerModel(finishing: finishing))
        self.onDismiss = onDismiss
        self.onSave = onSave
    }

    var body: some View {
        AppSheet(
            isPresented: $isPresented,
            onDismiss: onDismiss,
            title: "Сон",
            icon: "ic_sleep",
            confirm: SheetAction(label: "Запустить таймер") {
                onSave(resolveTime(isEdit: false, minutesAgo: time.minutesAgo, editTime: 0, clock: clock))
            }
        ) { _ in
            TimeAgoPicker(model: time)
        }
    }
}

/** Правка записи сна: начало и, для завершённого сна, конец. */
struct SleepEditSheet: View {
    @Environment(\.babyLogTheme) private var theme
    @Binding var isPresented: Bool
    @State private var model: SleepEditSheetModel
    let sex: Sex?
    let onDismiss: () -> Void
    let onSave: (Int64, Int64?, String) -> Void

    init(
        isPresented: Binding<Bool>,
        initial: Sleep,
        sex: Sex?,
        onDismiss: @escaping () -> Void,
        onSave: @escaping (Int64, Int64?, String) -> Void
    ) {
        _isPresented = isPresented
        _model = State(initialValue: SleepEditSheetModel(initial: initial))
        self.sex = sex
        self.onDismiss = onDismiss
        self.onSave = onSave
    }

    var body: some View {
        AppSheet(
            isPresented: $isPresented,
            onDismiss: onDismiss,
            title: "Сон",
            icon: "ic_sleep",
            confirm: SheetAction(label: "Сохранить", enabled: model.valid) {
                onSave(model.start, model.end, model.trimmedNote)
            }
        ) { _ in
            DateTimeField(
                label: bySex(sex, masculine: "Уснул:", feminine: "Уснула:"),
                value: model.start,
                onChange: { model.start = $0 }
            )
            if model.end != nil {
                DateTimeField(
                    label: bySex(sex, masculine: "Проснулся:", feminine: "Проснулась:"),
                    value: model.end!,
                    onChange: { model.end = $0 }
                )
                if !model.valid {
                    Text("Конец сна не может быть раньше начала")
                        .appTextStyle(theme.typography.bodySmall)
                        .foregroundStyle(theme.colors.error)
                }
            }
            NoteField(value: $model.note)
        }
    }
}
