import Observation
import SwiftUI

@MainActor @Observable final class DiaperSheetModel {
    var kind: DiaperKind?
    let time: TimeAgoPickerModel
    var editTime: Int64
    var note: String
    let isEdit: Bool
    let initialPhotos: [EventPhoto]

    init(initial: Diaper?, initialPhotos: [EventPhoto]) {
        kind = initial?.kind
        time = TimeAgoPickerModel()
        editTime = initial?.start ?? 0
        note = initial?.note ?? ""
        isEdit = initial != nil
        self.initialPhotos = initialPhotos
    }

    var valid: Bool { kind != nil }
    var trimmedNote: String { note.trimmingCharacters(in: .whitespacesAndNewlines) }

    func time(clock: any Clock) -> Int64 {
        resolveTime(isEdit: isEdit, minutesAgo: time.minutesAgo, editTime: editTime, clock: clock)
    }

    /** Фото редактируются в S5b; до неё существующие фото сохраняются без изменений. */
    var photoEdit: PhotoEdit { PhotoEdit(keep: initialPhotos.map(\.id), add: []) }
}

/** Подгузник: вид, время и комментарий. Поля фото и отправка с фото подключаются в S5b. */
struct DiaperSheet: View {
    @Environment(\.clock) private var clock
    @Binding var isPresented: Bool
    @State private var model: DiaperSheetModel
    let child: Child
    let onDismiss: () -> Void
    let onSave: (DiaperKind, Int64, String, PhotoEdit) -> Void

    init(
        isPresented: Binding<Bool>,
        initial: Diaper?,
        child: Child,
        initialPhotos: [EventPhoto],
        onDismiss: @escaping () -> Void,
        onSave: @escaping (DiaperKind, Int64, String, PhotoEdit) -> Void
    ) {
        _isPresented = isPresented
        _model = State(initialValue: DiaperSheetModel(initial: initial, initialPhotos: initialPhotos))
        self.child = child
        self.onDismiss = onDismiss
        self.onSave = onSave
    }

    var body: some View {
        AppSheet(
            isPresented: $isPresented,
            onDismiss: onDismiss,
            title: "Подгузник",
            icon: "ic_diaper",
            confirm: SheetAction(label: "Сохранить", enabled: model.valid) {
                if let kind = model.kind { onSave(kind, model.time(clock: clock), model.trimmedNote, model.photoEdit) }
            }
        ) { _ in
            OptionChips(
                options: DiaperKind.allCases.map {
                    ($0, diaperLabel($0, sex: child.sex).prefix(1).uppercased() + diaperLabel($0, sex: child.sex).dropFirst())
                },
                selected: model.kind,
                onSelect: { model.kind = $0 },
                icon: diaperIcon
            )
            TimeSection(isEdit: model.isEdit, model: model.time, editTime: $model.editTime)
            NoteField(value: $model.note)
        }
    }
}
