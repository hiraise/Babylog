import Observation
import SwiftUI

@MainActor @Observable final class MedicineSheetModel {
    var name: String
    let time: TimeAgoPickerModel
    var editTime: Int64
    var note: String
    var autoNote = ""
    let isEdit: Bool
    let pastMedicines: [PastMedicine]
    let illnessMedicines: [IllnessMedicine]

    init(initial: Medicine?, pastMedicines: [PastMedicine], illnessMedicines: [IllnessMedicine] = []) {
        name = initial?.name ?? ""
        time = TimeAgoPickerModel()
        editTime = initial?.start ?? 0
        note = initial?.note ?? ""
        isEdit = initial != nil
        self.pastMedicines = pastMedicines
        self.illnessMedicines = illnessMedicines
    }

    var suggestions: [PastMedicine] { medicineSuggestions(pastMedicines, query: name) }
    var valid: Bool { !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
    var trimmedName: String { name.trimmingCharacters(in: .whitespacesAndNewlines) }
    var trimmedNote: String { note.trimmingCharacters(in: .whitespacesAndNewlines) }

    func pick(_ suggestion: PastMedicine) {
        name = suggestion.name
        note = noteAfterSuggestion(note: note, autoNote: autoNote, picked: suggestion)
        autoNote = suggestion.note
    }

    func time(clock: any Clock) -> Int64 {
        resolveTime(isEdit: isEdit, minutesAgo: time.minutesAgo, editTime: editTime, clock: clock)
    }
}

/**
 Лист лекарства. Касание подсказки подставляет и комментарий, если свой ещё не набран.
 `illnessMedicines` — факты о приёмах в идущей болезни, без советов о дозах и интервалах.
 */
struct MedicineSheet: View {
    @Environment(\.clock) private var clock
    @Binding var isPresented: Bool
    @State private var model: MedicineSheetModel
    let onDismiss: () -> Void
    let onSave: (String, Int64, String) -> Void

    init(
        isPresented: Binding<Bool>,
        initial: Medicine?,
        pastMedicines: [PastMedicine],
        illnessMedicines: [IllnessMedicine] = [],
        onDismiss: @escaping () -> Void,
        onSave: @escaping (String, Int64, String) -> Void
    ) {
        _isPresented = isPresented
        _model = State(initialValue: MedicineSheetModel(
            initial: initial,
            pastMedicines: pastMedicines,
            illnessMedicines: illnessMedicines
        ))
        self.onDismiss = onDismiss
        self.onSave = onSave
    }

    var body: some View {
        AppSheet(
            isPresented: $isPresented,
            onDismiss: onDismiss,
            title: "Лекарство",
            icon: "ic_medicine",
            confirm: SheetAction(label: "Сохранить", enabled: model.valid) {
                onSave(model.trimmedName, model.time(clock: clock), model.trimmedNote)
            }
        ) { _ in
            MedicineNameField(value: $model.name, suggestions: model.suggestions, onPick: model.pick)
            if !model.isEdit, !model.illnessMedicines.isEmpty {
                IllnessMedicinesCard(medicines: model.illnessMedicines, typed: model.name)
            }
            TimeSection(isEdit: model.isEdit, model: model.time, editTime: $model.editTime)
            NoteField(value: $model.note, label: "Комментарий (доза, причина…)")
        }
    }
}

/** Поле названия, под ним — подсказки чипами, а не закрывающим форму выпадающим меню. */
struct MedicineNameField: View {
    @Environment(\.babyLogTheme) private var theme
    @Binding var value: String
    let suggestions: [PastMedicine]
    let onPick: (PastMedicine) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: theme.spacing.xs) {
            EditorTextField(value: $value, label: "Название")
            if !suggestions.isEmpty {
                FlowRow(spacing: theme.spacing.s) {
                    ForEach(suggestions, id: \.self) { suggestion in
                        HintChip(label: suggestion.name) { onPick(suggestion) }
                    }
                }
            }
        }
    }
}

/** «В эту болезнь»: набранное лекарство первым, не больше трёх строк с фактами из дневника. */
private struct IllnessMedicinesCard: View {
    @Environment(\.babyLogTheme) private var theme
    @Environment(\.clock) private var clock
    @Environment(\.timeZone) private var timeZone
    let medicines: [IllnessMedicine]
    let typed: String

    private var shown: [IllnessMedicine] {
        let key = typed.trimmingCharacters(in: .whitespacesAndNewlines).lowercased().replacingOccurrences(of: "ё", with: "е")
        return medicines.enumerated().sorted { left, right in
            let leftMatch = !key.isEmpty && normalized(left.element.name) == key
            let rightMatch = !key.isEmpty && normalized(right.element.name) == key
            return leftMatch == rightMatch ? left.offset < right.offset : leftMatch && !rightMatch
        }.prefix(3).map(\.element)
    }

    var body: some View {
        let now = clock.millis()
        AppCard(containerColor: theme.colors.surfaceContainerHighest) {
            VStack(alignment: .leading, spacing: theme.spacing.s) {
                HStack {
                    Text("В эту болезнь").appTextStyle(theme.typography.titleSmall)
                    Spacer()
                    Text("из дневника")
                        .appTextStyle(theme.typography.bodySmall)
                        .foregroundStyle(theme.colors.onSurfaceVariant)
                }
                ForEach(shown, id: \.self) { medicine in
                    HStack(alignment: .top, spacing: theme.spacing.m) {
                        AppIcon("ic_medicine", size: BabyLogSizes.contextIcon, tint: theme.colors.primary)
                        VStack(alignment: .leading) {
                            Text("\(medicine.name) — \(formatAgo(now - medicine.last))")
                                .appTextStyle(theme.typography.bodyMedium)
                            Text(details(medicine, now: now))
                                .appTextStyle(theme.typography.bodySmall)
                                .foregroundStyle(theme.colors.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    private func details(_ medicine: IllnessMedicine, now: Int64) -> String {
        [
            "\(formatShortDay(toLocalDate(medicine.last, timeZone: timeZone), today: toLocalDate(now, timeZone: timeZone), timeZone: timeZone)) в \(formatClock(medicine.last, timeZone: timeZone))",
            medicine.note.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : medicine.note,
            "за болезнь \(medicine.count) \(plural(medicine.count, one: "раз", few: "раза", many: "раз"))"
        ].compactMap { $0 }.joined(separator: " · ")
    }

    private func normalized(_ name: String) -> String {
        name.lowercased().replacingOccurrences(of: "ё", with: "е")
    }
}
