import Observation
import SwiftUI

private let illnessSheetDayMillis: Int64 = 86_400_000
private let maxNameHints = 5

/** Когда началась болезнь: сейчас, сутки назад или своё время. */
enum IllnessStartChoice: CaseIterable {
    case now
    case yesterday
    case other

    var label: String {
        switch self {
        case .now: "Сейчас"
        case .yesterday: "Вчера"
        case .other: "Раньше"
        }
    }
}

@MainActor @Observable final class IllnessStartSheetModel {
    var resume = false
    var startChoice: IllnessStartChoice = .now
    var customStart: Int64
    let tags: TagPickerModel
    var note = ""
    var withTemperature = false
    var temperature: String

    init(nameChoices: [String], lastTemperature: Temperature?, now: Int64) {
        customStart = now - 2 * illnessSheetDayMillis
        tags = TagPickerModel(
            title: "Что это, если знаете",
            options: nameChoices,
            selected: [],
            addLabel: "своё",
            normalize: normalizeIllnessName,
            maxLength: maxIllnessNameLength
        )
        temperature = temperatureValue(lastTemperature?.tenths ?? 366)
    }

    var tenths: Int? { parseTemperature(temperature) }
    var canStart: Bool { !withTemperature || tenths != nil }
    var names: [String] { tags.selected }
    var trimmedNote: String { note.trimmingCharacters(in: .whitespacesAndNewlines) }

    func start(clock: any Clock) -> Int64 {
        switch startChoice {
        case .now: clock.millis()
        case .yesterday: clock.millis() - illnessSheetDayMillis
        case .other: min(customStart, clock.millis())
        }
    }
}

/**
 Лист «Заболел(а)» (ADR-0040): всё, кроме «Начать», — по желанию. Когда началось, что это (названия
 `nameChoices` — прошлые и частые; можно несколько, можно потом), температура сейчас и комментарий.
 Прошлая болезнь `recent` кончилась недавно — сначала вопрос: новая болезнь или прошлая вернулась
 (`onResume` — у неё снова нет конца). Пол `sex` — для слова, имени в листе нет.
 */
struct IllnessStartSheet: View {
    @Environment(\.clock) private var clock
    @Environment(\.timeZone) private var timeZone
    @Environment(\.babyLogTheme) private var theme
    @Binding var isPresented: Bool
    @State private var model: IllnessStartSheetModel
    let sex: Sex?
    let recent: Illness?
    let onDismiss: () -> Void
    let onStart: (Int64, [String], String, Int?) -> Void
    let onResume: (Illness) -> Void

    init(
        isPresented: Binding<Bool>,
        sex: Sex?,
        recent: Illness?,
        nameChoices: [String],
        lastTemperature: Temperature?,
        now: Int64,
        onDismiss: @escaping () -> Void,
        onStart: @escaping (Int64, [String], String, Int?) -> Void,
        onResume: @escaping (Illness) -> Void
    ) {
        _isPresented = isPresented
        _model = State(initialValue: IllnessStartSheetModel(nameChoices: nameChoices, lastTemperature: lastTemperature, now: now))
        self.sex = sex
        self.recent = recent
        self.onDismiss = onDismiss
        self.onStart = onStart
        self.onResume = onResume
    }

    var body: some View {
        let resumed = model.resume ? recent : nil
        AppSheet(
            isPresented: $isPresented,
            onDismiss: onDismiss,
            title: bySex(sex, masculine: "Заболел", feminine: "Заболела"),
            icon: "ic_illness",
            confirm: resumed.map { illness in
                SheetAction(label: "Продолжить") { onResume(illness) }
            } ?? SheetAction(label: "Начать", enabled: model.canStart) {
                onStart(model.start(clock: clock), model.names, model.trimmedNote, model.withTemperature ? model.tenths : nil)
            }
        ) { _ in
            if let recent {
                RelapseChoice(recent: recent, resume: model.resume, now: clock.millis(), timeZone: timeZone) {
                    model.resume = $0
                }
            }
            if resumed == nil {
                Text("Всё ниже — по желанию: можно сразу «Начать».")
                    .appTextStyle(theme.typography.bodyMedium)
                    .foregroundStyle(theme.colors.onSurfaceVariant)
                VStack(alignment: .leading, spacing: theme.spacing.xs) {
                    Text("Когда началось?").appTextStyle(theme.typography.titleSmall)
                    OptionChips(
                        options: IllnessStartChoice.allCases.map { ($0, $0.label) },
                        selected: model.startChoice,
                        onSelect: { model.startChoice = $0 }
                    )
                    if model.startChoice == .other {
                        DateTimeField(label: "Началось:", value: model.customStart) { model.customStart = $0 }
                    }
                }
                TagPicker(model: model.tags)
                Text("Название можно добавить потом, когда скажет врач, и не одно.")
                    .appTextStyle(theme.typography.bodySmall)
                    .foregroundStyle(theme.colors.onSurfaceVariant)
                if model.withTemperature {
                    Text("Температура").appTextStyle(theme.typography.titleSmall)
                    TemperatureField(text: $model.temperature, previous: nil)
                        .onChange(of: model.temperature) { _, text in model.temperature = temperatureInput(text) }
                } else {
                    Button("+ Температура") { model.withTemperature = true }
                        .buttonStyle(.plain)
                        .foregroundStyle(theme.colors.primary)
                        .appTextStyle(theme.typography.labelLarge)
                }
                NoteField(value: $model.note)
            }
        }
    }
}

/**
 Прошлая болезнь `recent` кончилась недавно: новая — своя запись, или прошлая вернулась — у неё
 снова нет конца, а дни между ними войдут в неё. Решает человек: по записям этого не понять.
 */
private struct RelapseChoice: View {
    @Environment(\.babyLogTheme) private var theme
    let recent: Illness
    let resume: Bool
    let now: Int64
    let timeZone: TimeZone
    let onResume: (Bool) -> Void

    var body: some View {
        let ended = recent.end ?? now
        let what = recent.title().map { "— \($0) — " } ?? ""
        AppCard(containerColor: theme.colors.surfaceContainerHighest) {
            Text("Прошлая болезнь \(what)закончилась \(formatChipDay(toLocalDate(ended, timeZone: timeZone), today: toLocalDate(now, timeZone: timeZone), timeZone: timeZone)).")
                .appTextStyle(theme.typography.bodyMedium)
        }
        VStack(spacing: theme.spacing.xs) {
            RadioRow(
                selected: !resume,
                title: "Новая болезнь",
                caption: "своя, с новыми названиями; прошлая останется как была"
            ) { onResume(false) }
            RadioRow(
                selected: resume,
                title: "Прошлая вернулась",
                caption: "болезнь с \(formatChipDay(recent.startDate(timeZone), today: toLocalDate(now, timeZone: timeZone), timeZone: timeZone)) продолжится, дни между ними войдут в неё"
            ) { onResume(true) }
        }
    }
}

private struct RadioRow: View {
    @Environment(\.babyLogTheme) private var theme
    let selected: Bool
    let title: String
    let caption: String
    let onClick: () -> Void

    var body: some View {
        Button(action: onClick) {
            HStack(alignment: .center, spacing: theme.spacing.s) {
                Image(systemName: selected ? "largecircle.fill.circle" : "circle")
                    .foregroundStyle(theme.colors.primary)
                    .frame(width: BabyLogSizes.touchTarget, height: BabyLogSizes.touchTarget)
                VStack(alignment: .leading, spacing: 0) {
                    Text(title).appTextStyle(theme.typography.bodyLarge)
                    Text(caption)
                        .appTextStyle(theme.typography.bodySmall)
                        .foregroundStyle(theme.colors.onSurfaceVariant)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .frame(maxWidth: .infinity)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? [.isSelected] : [])
    }
}

@MainActor @Observable final class IllnessNameSheetModel {
    var name: String
    /** nil — с начала болезни. */
    var sinceDay: Date?
    var picking = false
    let illness: Illness
    let initial: IllnessName?
    let choices: [String]
    let now: Int64
    let timeZone: TimeZone
    let today: Date
    let first: Date
    let last: Date

    init(illness: Illness, initial: IllnessName?, choices: [String], now: Int64, timeZone: TimeZone) {
        self.illness = illness
        self.initial = initial
        self.choices = choices
        self.now = now
        self.timeZone = timeZone
        name = initial?.name ?? ""
        today = toLocalDate(now, timeZone: timeZone)
        first = illness.startDate(timeZone)
        last = illness.lastDate(today: today, timeZone: timeZone)
        sinceDay = initial?.since ?? (illness.isActive ? today : nil)
    }

    var normalized: String? { normalizeIllnessName(name) }
    var others: [String] { illness.names.filter { $0 != initial }.map { $0.name.lowercased() } }
    var taken: Bool { normalized.map { others.contains($0.lowercased()) } ?? false }
    var hints: [String] {
        let typed = name.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        return choices.filter {
            !others.contains($0.lowercased()) && $0.lowercased() != typed && (typed.isEmpty || $0.lowercased().hasPrefix(typed))
        }.prefix(maxNameHints).map { $0 }
    }
    var options: [(day: Date?, label: String)] {
        var result: [(Date?, String)] = [(nil, "с начала, \(formatDayRange(first: first, last: first, timeZone: timeZone))")]
        if last > first { result.append((last, formatChipDay(last, today: today, timeZone: timeZone))) }
        if let previous = illnessCalendar(timeZone).date(byAdding: .day, value: -1, to: last), previous > first {
            result.append((previous, formatChipDay(previous, today: today, timeZone: timeZone)))
        }
        return result
    }
    var custom: Bool { sinceDay != nil && !options.contains(where: { $0.day == sinceDay }) }

    func updateName(_ text: String) { name = String(text.prefix(maxIllnessNameLength)) }
    func save() -> IllnessName? {
        guard let normalized, !taken else { return nil }
        let since = sinceDay.flatMap { $0 > first ? $0 : nil }
        return IllnessName(normalized, since: since)
    }
    func pick(_ day: Date) {
        sinceDay = day <= first ? nil : min(day, last)
        picking = false
    }
}

/**
 Название болезни (ADR-0040): новое или правка `initial` — тогда «Убрать» вместо «Отмены». Подсказки —
 `choices` без названий, которые у болезни уже есть. «С какого дня» — сегодня, вчера, с начала болезни
 или другой день внутри неё: отит находят на 4-й день, а ОРВИ стоит с начала.
 */
struct IllnessNameSheet: View {
    @Environment(\.babyLogTheme) private var theme
    @Binding var isPresented: Bool
    @State private var model: IllnessNameSheetModel
    let onDismiss: () -> Void
    let onSave: (IllnessName) -> Void
    let onRemove: (() -> Void)?

    init(
        isPresented: Binding<Bool>,
        illness: Illness,
        initial: IllnessName?,
        choices: [String],
        now: Int64,
        onDismiss: @escaping () -> Void,
        onSave: @escaping (IllnessName) -> Void,
        onRemove: (() -> Void)? = nil
    ) {
        _isPresented = isPresented
        _model = State(initialValue: IllnessNameSheetModel(illness: illness, initial: initial, choices: choices, now: now, timeZone: .current))
        self.onDismiss = onDismiss
        self.onSave = onSave
        self.onRemove = onRemove
    }

    var body: some View {
        AppSheet(
            isPresented: $isPresented,
            onDismiss: onDismiss,
            title: model.initial == nil ? "Название болезни" : "Название",
            icon: "ic_illness",
            confirm: SheetAction(label: model.initial == nil ? "Добавить" : "Сохранить", enabled: model.normalized != nil && !model.taken) {
                if let name = model.save() { onSave(name) }
            },
            secondary: onRemove.map { SheetAction(label: "Убрать", onClick: $0) }
        ) { _ in
            if let title = model.illness.title() {
                Text("Уже есть: \(title)")
                    .appTextStyle(theme.typography.bodyMedium)
                    .foregroundStyle(theme.colors.onSurfaceVariant)
            }
            EditorTextField(value: $model.name, label: "Название", isError: model.taken)
                .onChange(of: model.name) { _, text in model.updateName(text) }
            if model.taken {
                Text("Такое название уже есть")
                    .appTextStyle(theme.typography.bodySmall)
                    .foregroundStyle(theme.colors.error)
            }
            if !model.hints.isEmpty {
                FlowRow(spacing: theme.spacing.s) {
                    ForEach(model.hints, id: \.self) { hint in
                        HintChip(label: hint) { model.name = hint }
                    }
                }
            }
            VStack(alignment: .leading, spacing: theme.spacing.xs) {
                Text("С какого дня").appTextStyle(theme.typography.titleSmall)
                FlowRow(spacing: theme.spacing.s) {
                    ForEach(model.options.indices, id: \.self) { index in
                        let option = model.options[index]
                        ChoiceChip(selected: model.sinceDay == option.day, label: option.label) {
                            model.sinceDay = option.day
                        }
                    }
                    ChoiceChip(
                        selected: model.custom,
                        label: model.custom ? formatChipDay(model.sinceDay!, today: model.today) : "Другой день"
                    ) { model.picking = true }
                }
            }
        }
        if model.picking {
            DayPickSheet(isPresented: $model.picking, initial: model.sinceDay ?? model.last, onDismiss: { model.picking = false }) {
                model.pick($0)
            }
        }
    }
}

@MainActor @Observable final class IllnessRecoveredSheetModel {
    var daysAgo = 0
    var picking = false
    let illness: Illness
    let now: Int64
    let timeZone: TimeZone
    let today: Date
    let first: Date

    init(illness: Illness, now: Int64, timeZone: TimeZone) {
        self.illness = illness
        self.now = now
        self.timeZone = timeZone
        today = toLocalDate(now, timeZone: timeZone)
        first = illness.startDate(timeZone)
    }

    var endDay: Date {
        max(illnessCalendar(timeZone).date(byAdding: .day, value: -daysAgo, to: today) ?? first, first)
    }
    var end: Int64 { max(now - Int64(daysAgo) * illnessSheetDayMillis, illness.start) }
    var days: Int { illness.dayNumber(endDay, timeZone: timeZone) }

    func pick(_ day: Date) {
        let selected = max(day, first)
        daysAgo = max(illnessCalendar(timeZone).dateComponents([.day], from: selected, to: today).day ?? 0, 0)
        picking = false
    }
}

/**
 «Выздоровел(а)» (ADR-0040): когда — сегодня, вчера, позавчера или другой день, не раньше начала.
 Конец — в это же время суток выбранного дня. `course` — лекарства, начатые в болезни и ещё идущие:
 их приёмы до конца курса останутся в болезни.
 */
struct IllnessRecoveredSheet: View {
    @Environment(\.babyLogTheme) private var theme
    @Binding var isPresented: Bool
    @State private var model: IllnessRecoveredSheetModel
    let sex: Sex?
    let course: [String]
    let onDismiss: () -> Void
    let onSave: (Int64) -> Void

    init(
        isPresented: Binding<Bool>,
        illness: Illness,
        sex: Sex?,
        course: [String],
        now: Int64,
        onDismiss: @escaping () -> Void,
        onSave: @escaping (Int64) -> Void
    ) {
        _isPresented = isPresented
        _model = State(initialValue: IllnessRecoveredSheetModel(illness: illness, now: now, timeZone: .current))
        self.sex = sex
        self.course = course
        self.onDismiss = onDismiss
        self.onSave = onSave
    }

    var body: some View {
        AppSheet(
            isPresented: $isPresented,
            onDismiss: onDismiss,
            title: bySex(sex, masculine: "Выздоровел", feminine: "Выздоровела"),
            icon: "ic_illness",
            confirm: SheetAction(label: bySex(sex, masculine: "Выздоровел", feminine: "Выздоровела")) {
                onSave(model.end)
            }
        ) { _ in
            if let title = model.illness.title() {
                Text(title).appTextStyle(theme.typography.bodyMedium).foregroundStyle(theme.colors.tertiary)
            }
            VStack(alignment: .leading, spacing: theme.spacing.xs) {
                Text("Когда?").appTextStyle(theme.typography.titleSmall)
                FlowRow(spacing: theme.spacing.s) {
                    ForEach((0...2).filter { ago in
                        (illnessCalendar(model.timeZone).date(byAdding: .day, value: -ago, to: model.today) ?? model.first) >= model.first
                    }, id: \.self) { ago in
                        let day = illnessCalendar(model.timeZone).date(byAdding: .day, value: -ago, to: model.today) ?? model.first
                        ChoiceChip(selected: model.daysAgo == ago, label: formatChipDay(day, today: model.today, timeZone: model.timeZone)) {
                            model.daysAgo = ago
                        }
                    }
                    ChoiceChip(
                        selected: model.daysAgo > 2,
                        label: model.daysAgo > 2 ? formatChipDay(model.endDay, today: model.today, timeZone: model.timeZone) : "Другой день"
                    ) { model.picking = true }
                }
            }
            AppCard(containerColor: theme.colors.surfaceContainerHighest) {
                Text("\(bySex(sex, masculine: "Болел", feminine: "Болела")) \(model.days) \(plural(model.days, one: "день", few: "дня", many: "дней")) · \(formatDayRange(first: model.first, last: model.endDay, timeZone: model.timeZone))")
                    .appTextStyle(theme.typography.titleMedium)
            }
            if !course.isEmpty {
                Text("\(course.joined(separator: ", ")) ещё даёте? Приёмы до конца курса тоже покажутся в этой болезни.")
                    .appTextStyle(theme.typography.bodySmall)
                    .foregroundStyle(theme.colors.onSurfaceVariant)
            }
        }
        if model.picking {
            DayPickSheet(isPresented: $model.picking, initial: model.endDay, onDismiss: { model.picking = false }) {
                model.pick($0)
            }
        }
    }
}

@MainActor @Observable final class IllnessEditSheetModel {
    var start: Int64
    var end: Int64?
    var note: String

    init(initial: Illness) {
        start = initial.start
        end = initial.end
        note = initial.note
    }

    var valid: Bool { end.map { $0 >= start } ?? true }
    var trimmedNote: String { note.trimmingCharacters(in: .whitespacesAndNewlines) }
}

/** Правка болезни: начало, конец (у закончившейся) и комментарий; названия правят на её экране. */
struct IllnessEditSheet: View {
    @Environment(\.babyLogTheme) private var theme
    @Binding var isPresented: Bool
    @State private var model: IllnessEditSheetModel
    let onDismiss: () -> Void
    let onSave: (Int64, Int64?, String) -> Void

    init(isPresented: Binding<Bool>, initial: Illness, onDismiss: @escaping () -> Void, onSave: @escaping (Int64, Int64?, String) -> Void) {
        _isPresented = isPresented
        _model = State(initialValue: IllnessEditSheetModel(initial: initial))
        self.onDismiss = onDismiss
        self.onSave = onSave
    }

    var body: some View {
        AppSheet(
            isPresented: $isPresented,
            onDismiss: onDismiss,
            title: "Болезнь",
            icon: "ic_illness",
            confirm: SheetAction(label: "Сохранить", enabled: model.valid) {
                onSave(model.start, model.end, model.trimmedNote)
            }
        ) { _ in
            DateTimeField(label: "Началась:", value: model.start) { model.start = $0 }
            if let end = model.end {
                DateTimeField(label: "Закончилась:", value: end) { model.end = $0 }
                if !model.valid {
                    Text("Конец болезни не может быть раньше начала")
                        .appTextStyle(theme.typography.bodySmall)
                        .foregroundStyle(theme.colors.error)
                }
            }
            NoteField(value: $model.note)
        }
    }
}
