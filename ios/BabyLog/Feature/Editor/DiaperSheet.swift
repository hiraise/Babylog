import Observation
import SwiftUI

@MainActor @Observable final class DiaperSheetModel {
    var kind: DiaperKind?
    let time: TimeAgoPickerModel
    var editTime: Int64
    var note: String
    let isEdit: Bool
    let photos: FormPhotosState

    init(initial: Diaper?, initialPhotos: [EventPhoto]) {
        kind = initial?.kind
        time = TimeAgoPickerModel()
        editTime = initial?.start ?? 0
        note = initial?.note ?? ""
        isEdit = initial != nil
        photos = FormPhotosState(initialPhotos: initialPhotos)
    }

    var valid: Bool { kind != nil && !photos.busy }
    var trimmedNote: String { note.trimmingCharacters(in: .whitespacesAndNewlines) }

    func time(clock: any Clock) -> Int64 {
        resolveTime(isEdit: isEdit, minutesAgo: time.minutesAgo, editTime: editTime, clock: clock)
    }

    /** Вызывать только по «Сохранить»: новые файлы после переноса в `PhotoEdit` удаляются. */
    var photoEdit: PhotoEdit { photos.takeEdit() }
}

/** Подгузник: вид, время, комментарий и фото; подпись и фото отправляет системный `ShareLink`. */
struct DiaperSheet: View {
    @Environment(\.clock) private var clock
    @Environment(\.timeZone) private var timeZone
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
            onDismiss: {
                model.photos.discardPending()
                onDismiss()
            },
            title: "Подгузник",
            icon: "ic_diaper",
            confirm: SheetAction(label: "Сохранить", enabled: model.valid) {
                if let kind = model.kind { onSave(kind, model.time(clock: clock), model.trimmedNote, model.photoEdit) }
            },
            secondaryContent: model.kind.map { kind in
                AnyView(PhotoShareLink(
                    text: diaperShareText(
                        child: child,
                        kind: kind,
                        time: model.time(clock: clock),
                        note: model.trimmedNote,
                        today: toLocalDate(clock.millis(), timeZone: timeZone),
                        timeZone: timeZone
                    ),
                    jpegs: model.photos.jpegs(),
                    enabled: model.valid
                ))
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
            PhotosField(state: model.photos)
            PhotoSheets(state: model.photos)
        }
    }
}
