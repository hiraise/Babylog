import Observation
import SwiftUI

@MainActor @Observable final class ChildSheetModel {
    var name = "" {
        didSet {
            if name.count > Child.maxNameLength { name = String(name.prefix(Child.maxNameLength)) }
        }
    }
    var birthDate: Int64?
    var sex: Sex?
    var photo: ChildPhoto?

    let children: [Child]
    let initialId: Int64?

    init(initial: Child?, children: [Child]) {
        name = initial?.name ?? ""
        birthDate = initial?.birthDate
        sex = initial?.sex
        photo = initial?.photo
        initialId = initial?.id
        self.children = children
    }

    var taken: Bool {
        isNameTaken(children, name: name, exceptId: initialId)
    }

    var valid: Bool {
        isValidChildName(name) && !taken
    }

    var trimmedName: String {
        name.trimmingCharacters(in: .whitespacesAndNewlines)
    }
}

/**
 Добавление (`initial` = nil) или правка ребёнка: имя, дата рождения, пол и фото.
 Имя обязательно и не должно совпадать с именем другого ребёнка; выбранный пол можно поменять, но не снять.
 */
struct ChildSheet: View {
    @Environment(\.babyLogTheme) private var theme
    @Environment(\.timeZone) private var timeZone
    @State private var model: ChildSheetModel
    @State private var pickBirth = false
    @State private var photoMenu = false
    @State private var pickerSource: PhotoPickerSource?
    @State private var cropSource: String?
    @State private var photoError: String?

    @Binding var isPresented: Bool
    let initial: Child?
    let onDismiss: () -> Void
    let onSave: (String, Int64?, Sex?, ChildPhoto?) -> Void
    let onDelete: (() -> Void)?

    init(
        isPresented: Binding<Bool>,
        initial: Child?,
        children: [Child],
        onDismiss: @escaping () -> Void,
        onSave: @escaping (String, Int64?, Sex?, ChildPhoto?) -> Void,
        onDelete: (() -> Void)? = nil
    ) {
        _isPresented = isPresented
        _model = State(initialValue: ChildSheetModel(initial: initial, children: children))
        self.initial = initial
        self.onDismiss = onDismiss
        self.onSave = onSave
        self.onDelete = onDelete
    }

    var body: some View {
        AppSheet(
            isPresented: $isPresented,
            onDismiss: onDismiss,
            title: initial == nil ? "Новый ребёнок" : "Ребёнок",
            icon: "ic_child",
            confirm: SheetAction(label: "Сохранить", enabled: model.valid) {
                onSave(model.trimmedName, model.birthDate, model.sex, model.photo)
            }
        ) { closer in
            VStack(spacing: theme.spacing.s) {
                PhotoButton(sex: model.sex, photo: model.photo) { photoMenu = true }
                    .frame(maxWidth: .infinity, alignment: .center)
                if let photoError {
                    Text(photoError)
                        .appTextStyle(theme.typography.bodySmall)
                        .foregroundStyle(theme.colors.error)
                }
            }
            EditorTextField(value: $model.name, label: "Имя", isError: model.taken)
                .textInputAutocapitalization(.words)
            if model.taken {
                Text("Такое имя уже есть")
                    .appTextStyle(theme.typography.bodySmall)
                    .foregroundStyle(theme.colors.error)
            }
            VStack(alignment: .leading, spacing: theme.spacing.s) {
                Text("Дата рождения:")
                    .appTextStyle(theme.typography.titleSmall)
                ChoiceChip(
                    selected: model.birthDate != nil,
                    label: model.birthDate.map { formatDate($0, timeZone: timeZone) } ?? "указать",
                    icon: "ic_calendar"
                ) {
                    pickBirth = true
                }
            }
            VStack(alignment: .leading, spacing: theme.spacing.s) {
                Text("Пол:")
                    .appTextStyle(theme.typography.titleSmall)
                HStack(spacing: theme.spacing.s) {
                    ChoiceChip(selected: model.sex == .boy, label: "Мальчик") { model.sex = .boy }
                    ChoiceChip(selected: model.sex == .girl, label: "Девочка") { model.sex = .girl }
                }
            }
            if let onDelete {
                Button("Удалить ребёнка…") { closer.close(then: onDelete) }
                    .buttonStyle(.plain)
                    .foregroundStyle(theme.colors.error)
                    .appTextStyle(theme.typography.labelLarge)
                    .frame(minHeight: BabyLogSizes.touchTarget)
            }
            if pickBirth {
                BirthDateSheet(
                    isPresented: $pickBirth,
                    initial: model.birthDate,
                    onDismiss: { pickBirth = false },
                    onSave: {
                        model.birthDate = $0
                        pickBirth = false
                    }
                )
            }
            PhotoPicker(
                source: $pickerSource,
                maxItems: 1,
                onPicked: { paths in
                    photoError = nil
                    cropSource = paths.first
                },
                onError: { photoError = $0 }
            )
            if photoMenu {
                PhotoSourceSheet(
                    isPresented: $photoMenu,
                    onCamera: {
                        photoMenu = false
                        photoError = nil
                        pickerSource = .camera
                    },
                    onGallery: {
                        photoMenu = false
                        photoError = nil
                        pickerSource = .gallery
                    },
                    onDismiss: { photoMenu = false },
                    onRemove: model.photo == nil ? nil : {
                        model.photo = nil
                        photoError = nil
                    }
                )
            }
            if let cropSource {
                PhotoCropSheet(
                    isPresented: Binding(get: { self.cropSource != nil }, set: { if !$0 { finishCrop() } }),
                    source: cropSource,
                    onDismiss: finishCrop,
                    onDone: {
                        model.photo = $0
                        finishCrop()
                    }
                )
            }
        }
    }

    private func finishCrop() {
        if let cropSource { deletePhotoFile(path: cropSource) }
        cropSource = nil
    }
}
