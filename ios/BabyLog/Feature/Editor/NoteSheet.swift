import Observation
import SwiftUI

@MainActor @Observable final class NoteSheetModel {
    var text: String
    let tags: TagPickerModel
    let time: TimeAgoPickerModel
    var editTime: Int64
    let isEdit: Bool
    let initialPhotos: [EventPhoto]

    init(initial: Note?, tagOptions: TagChoices, initialPhotos: [EventPhoto]) {
        text = initial?.note ?? ""
        tags = TagPickerModel(
            title: "Метки",
            options: tagOptions.shown,
            selected: initial?.tags ?? [],
            addLabel: "своя метка",
            other: tagOptions.other,
            otherTitle: "Метки плача:"
        )
        time = TimeAgoPickerModel()
        editTime = initial?.start ?? 0
        isEdit = initial != nil
        self.initialPhotos = initialPhotos
    }

    /** «Сохранить» доступно, когда есть текст, метка или фото: пустая заметка ничего не говорит. */
    var filled: Bool {
        !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || !tags.selected.isEmpty || !initialPhotos.isEmpty
    }

    var trimmedText: String { text.trimmingCharacters(in: .whitespacesAndNewlines) }

    func time(clock: any Clock) -> Int64 {
        resolveTime(isEdit: isEdit, minutesAgo: time.minutesAgo, editTime: editTime, clock: clock)
    }

    /** Фото редактируются в S5b; до неё существующие фото сохраняются без изменений. */
    var photoEdit: PhotoEdit { PhotoEdit(keep: initialPhotos.map(\.id), add: []) }
}

/** Заметка (ADR-0031): текст, метки, время и сохранение существующих фото; выбор фото подключается в S5b. */
struct NoteSheet: View {
    @Environment(\.clock) private var clock
    @Binding var isPresented: Bool
    @State private var model: NoteSheetModel
    let onDismiss: () -> Void
    let onSave: (String, [String], Int64, PhotoEdit) -> Void

    init(
        isPresented: Binding<Bool>,
        initial: Note?,
        tagOptions: TagChoices,
        initialPhotos: [EventPhoto],
        onDismiss: @escaping () -> Void,
        onSave: @escaping (String, [String], Int64, PhotoEdit) -> Void
    ) {
        _isPresented = isPresented
        _model = State(initialValue: NoteSheetModel(initial: initial, tagOptions: tagOptions, initialPhotos: initialPhotos))
        self.onDismiss = onDismiss
        self.onSave = onSave
    }

    var body: some View {
        AppSheet(
            isPresented: $isPresented,
            onDismiss: onDismiss,
            title: "Заметка",
            icon: "ic_note",
            confirm: SheetAction(label: "Сохранить", enabled: model.filled) {
                onSave(model.trimmedText, model.tags.selected, model.time(clock: clock), model.photoEdit)
            }
        ) { _ in
            EditorTextField(
                value: $model.text,
                label: "Что заметили",
                axis: .vertical,
                autofocus: !model.isEdit
            )
            .frame(minHeight: BabyLogSizes.noteTextMinHeight)
            TagPicker(model: model.tags)
            TimeSection(isEdit: model.isEdit, model: model.time, editTime: $model.editTime)
        }
    }
}
