import Observation
import SwiftUI

private let minute: Int64 = 60_000
private let earlierOptions = [(0, "Нет"), (5, "5 мин"), (10, "10 мин"), (15, "15 мин")]

@MainActor @Observable final class CryingDoneSheetModel {
    let crying: Crying
    var earlier = 0
    var reasons: [String]
    var helped: [String]
    var note: String

    init(crying: Crying) {
        self.crying = crying
        reasons = crying.reasons
        helped = crying.helped
        note = crying.note
    }

    var start: Int64 { crying.start - Int64(earlier) * minute }
    var end: Int64 { crying.end ?? crying.start }
    var duration: Int64 { end - start }
    var trimmedNote: String { note.trimmingCharacters(in: .whitespacesAndNewlines) }
}

@MainActor @Observable final class CryingEditSheetModel {
    var start: Int64
    var end: Int64?
    var reasons: [String]
    var helped: [String]
    var note: String

    init(initial: Crying) {
        start = initial.start
        end = initial.end
        reasons = initial.reasons
        helped = initial.helped
        note = initial.note
    }

    var valid: Bool { end == nil || end! >= start }
    var trimmedNote: String { note.trimmingCharacters(in: .whitespacesAndNewlines) }
}

/** Лист после остановки плача: длительность, более раннее начало, контекст, причины, помощь и комментарий. */
struct CryingDoneSheet: View {
    @Binding var isPresented: Bool
    @State private var model: CryingDoneSheetModel
    let context: CryingContext?
    let choices: CryingChoices
    let sex: Sex?
    let onResume: (() -> Void)?
    let onDismiss: () -> Void
    let onSave: (Int64, [String], [String], String) -> Void

    init(
        isPresented: Binding<Bool>,
        crying: Crying,
        context: CryingContext?,
        choices: CryingChoices,
        sex: Sex?,
        onResume: (() -> Void)?,
        onDismiss: @escaping () -> Void,
        onSave: @escaping (Int64, [String], [String], String) -> Void
    ) {
        _isPresented = isPresented
        _model = State(initialValue: CryingDoneSheetModel(crying: crying))
        self.context = context
        self.choices = choices
        self.sex = sex
        self.onResume = onResume
        self.onDismiss = onDismiss
        self.onSave = onSave
    }

    var body: some View {
        AppSheet(
            isPresented: $isPresented,
            onDismiss: onDismiss,
            title: "Плач · \(formatDuration(model.duration))",
            icon: "ic_crying",
            confirm: SheetAction(label: "Сохранить") {
                onSave(model.start, model.reasons, model.helped, model.trimmedNote)
            },
            secondary: onResume.map { SheetAction(label: "Ещё плачет", onClick: $0) }
        ) { _ in
            CryingDoneFields(
                crying: model.crying,
                start: model.start,
                earlier: model.earlier,
                onEarlier: { model.earlier = $0 },
                context: context,
                choices: choices,
                sex: sex,
                reasons: model.reasons,
                onReasons: { model.reasons = $0 },
                helped: model.helped,
                onHelped: { model.helped = $0 },
                note: model.note,
                onNote: { model.note = $0 }
            )
        }
    }
}

/** Содержимое листа «Плач закончился» отдельно от листа для скриншот-тестов. */
struct CryingDoneFields: View {
    @Environment(\.babyLogTheme) private var theme
    @Environment(\.timeZone) private var timeZone
    let crying: Crying
    let start: Int64
    let earlier: Int
    let onEarlier: (Int) -> Void
    let context: CryingContext?
    let choices: CryingChoices
    let sex: Sex?
    let reasons: [String]
    let onReasons: ([String]) -> Void
    let helped: [String]
    let onHelped: ([String]) -> Void
    let note: String
    let onNote: (String) -> Void

    var body: some View {
        Text("\(formatClock(start, timeZone: timeZone))–\(formatClock(crying.end ?? crying.start, timeZone: timeZone))")
            .appTextStyle(theme.typography.bodyMedium)
            .foregroundStyle(theme.colors.onSurfaceVariant)
        StartedEarlier(earlier: earlier, onEarlier: onEarlier)
        if let context { CryingContextCard(context: context, sex: sex) }
        CryingDetails(
            reasons: reasons,
            onReasons: onReasons,
            helped: helped,
            onHelped: onHelped,
            choices: choices
        )
        NoteField(value: Binding(get: { note }, set: onNote))
    }
}

private struct StartedEarlier: View {
    @Environment(\.babyLogTheme) private var theme
    let earlier: Int
    let onEarlier: (Int) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: theme.spacing.xs) {
            Text("Начался раньше, чем нажали?").appTextStyle(theme.typography.titleSmall)
            FlowRow(spacing: theme.spacing.s) {
                ForEach(earlierOptions, id: \.0) { minutes, label in
                    ChoiceChip(selected: earlier == minutes, label: label) { onEarlier(minutes) }
                }
            }
        }
    }
}

/** Правка плача: начало, конец завершённого плача, причины, что помогло и комментарий. */
struct CryingEditSheet: View {
    @Environment(\.babyLogTheme) private var theme
    @Binding var isPresented: Bool
    @State private var model: CryingEditSheetModel
    let choices: CryingChoices
    let onDismiss: () -> Void
    let onSave: (Int64, Int64?, [String], [String], String) -> Void

    init(
        isPresented: Binding<Bool>,
        initial: Crying,
        choices: CryingChoices,
        onDismiss: @escaping () -> Void,
        onSave: @escaping (Int64, Int64?, [String], [String], String) -> Void
    ) {
        _isPresented = isPresented
        _model = State(initialValue: CryingEditSheetModel(initial: initial))
        self.choices = choices
        self.onDismiss = onDismiss
        self.onSave = onSave
    }

    var body: some View {
        AppSheet(
            isPresented: $isPresented,
            onDismiss: onDismiss,
            title: "Плач",
            icon: "ic_crying",
            confirm: SheetAction(label: "Сохранить", enabled: model.valid) {
                onSave(model.start, model.end, model.reasons, model.helped, model.trimmedNote)
            }
        ) { _ in
            DateTimeField(label: "Начался:", value: model.start, onChange: { model.start = $0 })
            if model.end != nil {
                DateTimeField(label: "Закончился:", value: model.end!, onChange: { model.end = $0 })
                if !model.valid {
                    Text("Конец плача не может быть раньше начала")
                        .appTextStyle(theme.typography.bodySmall)
                        .foregroundStyle(theme.colors.error)
                }
            }
            CryingDetails(
                reasons: model.reasons,
                onReasons: { model.reasons = $0 },
                helped: model.helped,
                onHelped: { model.helped = $0 },
                choices: choices
            )
            NoteField(value: $model.note)
        }
    }
}

private struct CryingDetails: View {
    let reasons: [String]
    let onReasons: ([String]) -> Void
    let helped: [String]
    let onHelped: ([String]) -> Void
    let choices: CryingChoices

    var body: some View {
        CryingTagPicker(
            title: "Почему, как думаете?",
            choices: choices.reasons.shown,
            selected: reasons,
            onSelect: onReasons,
            addLabel: "своя метка",
            other: choices.reasons.other,
            otherTitle: "Метки заметок:"
        )
        CryingTagPicker(
            title: "Что помогло?",
            choices: choices.helped,
            selected: helped,
            onSelect: onHelped,
            addLabel: "своё"
        )
    }
}

private struct CryingTagPicker: View {
    @Environment(\.babyLogTheme) private var theme
    @State private var custom = ""
    let title: String
    let choices: [String]
    let selected: [String]
    let onSelect: ([String]) -> Void
    let addLabel: String
    var other: [String] = []
    var otherTitle: String? = nil

    var body: some View {
        VStack(alignment: .leading, spacing: theme.spacing.xs) {
            Text(title).appTextStyle(theme.typography.titleSmall)
            FlowRow(spacing: theme.spacing.s) {
                ForEach(unique(choices + selected), id: \.self) { tag in
                    ChoiceChip(selected: selected.contains(tag), label: tag, multiple: true) {
                        onSelect(toggleSelection(tag, in: selected))
                    }
                }
            }
            EditorTextField(value: $custom, label: addLabel, onSubmit: addCustom)
            if !other.isEmpty {
                if let otherTitle {
                    Text(otherTitle)
                        .appTextStyle(theme.typography.bodySmall)
                        .foregroundStyle(theme.colors.onSurfaceVariant)
                }
                FlowRow(spacing: theme.spacing.s) {
                    ForEach(other, id: \.self) { tag in
                        HintChip(label: tag) {
                            custom = tag
                            addCustom()
                        }
                    }
                }
            }
        }
    }

    private func addCustom() {
        guard let tag = normalizeTag(custom) else { return }
        if !selected.contains(tag) { onSelect(selected + [tag]) }
        custom = ""
    }
}

private struct CryingContextCard: View {
    @Environment(\.babyLogTheme) private var theme
    let context: CryingContext
    let sex: Sex?

    private var lines: [(icon: String, text: String, usual: Int64?)] {
        var lines: [(icon: String, text: String, usual: Int64?)] = []
        if let awake = context.awakeMillis {
            let longer = context.usualAwakeMillis.map { Double(awake) > Double($0) * longerThanUsual } ?? false
            lines.append((
                "ic_sleep",
                "\(bySex(sex, masculine: "Не спал", feminine: "Не спала")) \(formatDuration(awake))",
                longer ? context.usualAwakeMillis : nil
            ))
        }
        if let since = context.sinceFeedingMillis {
            let feeding = context.feeding.map { " · \($0)" } ?? ""
            lines.append((
                context.feedingByBottle ? "ic_bottle" : "ic_feeding",
                "\(bySex(sex, masculine: "Ел", feminine: "Ела")) \(formatAgo(since))\(feeding)",
                nil
            ))
        }
        if let since = context.sinceDiaperMillis {
            let diaper = context.diaper.map { " · \(diaperLabel($0, sex: sex))" } ?? ""
            lines.append((
                context.diaper.map(diaperIcon) ?? "ic_diaper",
                "Подгузник \(formatAgo(since))\(diaper)",
                nil
            ))
        }
        return lines
    }

    var body: some View {
        if !lines.isEmpty {
            AppCard(containerColor: theme.colors.surfaceContainerHighest) {
                VStack(alignment: .leading, spacing: theme.spacing.s) {
                    HStack {
                        Text("Что было до").appTextStyle(theme.typography.titleSmall)
                        Spacer()
                        Text("из дневника")
                            .appTextStyle(theme.typography.bodySmall)
                            .foregroundStyle(theme.colors.onSurfaceVariant)
                    }
                    ForEach(lines.indices, id: \.self) { index in
                        let line = lines[index]
                        HStack(alignment: .center, spacing: theme.spacing.m) {
                            AppIcon(line.icon, size: BabyLogSizes.contextIcon, tint: theme.colors.primary)
                            VStack(alignment: .leading) {
                                Text(line.text).appTextStyle(theme.typography.bodyMedium)
                                if let usual = line.usual {
                                    Text("обычно \(formatDuration(usual))")
                                        .appTextStyle(theme.typography.bodySmall)
                                        .foregroundStyle(theme.colors.tertiary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

func toggleSelection(_ value: String, in selected: [String]) -> [String] {
    selected.contains(value) ? selected.filter { $0 != value } : selected + [value]
}

private func unique(_ values: [String]) -> [String] {
    var seen = Set<String>()
    return values.filter { seen.insert($0).inserted }
}
