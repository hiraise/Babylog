import Observation
import SwiftUI

private let noIllnessId: Int64 = -1

/**
 Какие листы здоровья и болезни открыты (ADR-0040): флаги и id записей.
 `nameIndex` — правка названия болезни `nameIllnessId` по номеру; -1 — новое.
 */
@MainActor @Observable final class HealthSheets {
    var menu = false
    var startIllness = false
    var temperature = false
    var nameIllnessId: Int64 = noIllnessId
    var nameIndex = -1
    var recoverId: Int64 = noIllnessId
    var doctorId: Int64 = noIllnessId

    func editName(_ illness: Illness, index: Int) {
        nameIllnessId = illness.id
        nameIndex = index
    }
}

/**
 Листы здоровья и болезни главного экрана (ADR-0040): «Здоровье» (лекарство, температура, «Заболел(а)»
 или идущая болезнь), начало болезни, температура, название, «Выздоровел(а)» и «Для врача».
 Лист лекарства открывает экран (`onAddMedicine`): он общий с плиткой и карточкой болезни.
 */
struct HealthSheetsHost: View {
    @Environment(\.timeZone) private var timeZone
    @Bindable var sheets: HealthSheets
    let state: DiaryUiState
    let child: Child
    let now: Int64
    let model: DiaryViewModel
    let onAddMedicine: () -> Void
    let onOpenIllness: (Illness) -> Void

    var body: some View {
        let active = state.activeIllness
        if sheets.menu {
            HealthSheet(
                isPresented: $sheets.menu,
                state: state,
                now: now,
                onDismiss: { sheets.menu = false },
                onMedicine: onAddMedicine,
                onTemperature: { sheets.temperature = true },
                onIllness: {
                    if let active { onOpenIllness(active) } else { sheets.startIllness = true }
                }
            )
        }
        if sheets.startIllness {
            IllnessStartSheet(
                isPresented: $sheets.startIllness,
                sex: child.sex,
                recent: state.events.recentIllness(now: now),
                nameChoices: state.illnessNames,
                lastTemperature: state.lastTemperature,
                now: now,
                onDismiss: { sheets.startIllness = false },
                onStart: { start, names, note, tenths in
                    sheets.startIllness = false
                    Task { await model.startIllness(start: start, names: names, note: note, temperatureTenths: tenths) }
                },
                onResume: { illness in
                    sheets.startIllness = false
                    Task { await model.resumeIllness(illness) }
                }
            )
        }
        if sheets.temperature {
            TemperatureSheet(
                isPresented: $sheets.temperature,
                initial: nil,
                last: state.lastTemperature,
                sex: child.sex,
                offerIllness: active == nil,
                onDismiss: { sheets.temperature = false },
                onSave: { tenths, method, time, note, startIllness in
                    sheets.temperature = false
                    Task {
                        await model.saveTemperature(
                            Temperature(childId: child.id, start: time, tenths: tenths, method: method, note: note),
                            startIllness: startIllness
                        )
                    }
                },
                onSaveWithMedicine: active == nil ? nil : { tenths, method, time, note in
                    sheets.temperature = false
                    Task {
                        await model.saveTemperature(
                            Temperature(childId: child.id, start: time, tenths: tenths, method: method, note: note),
                            startIllness: false
                        )
                        onAddMedicine()
                    }
                }
            )
        }
        if let illness = illnessById(sheets.nameIllnessId) {
            let old = illness.names.indices.contains(sheets.nameIndex) ? illness.names[sheets.nameIndex] : nil
            IllnessNameSheet(
                isPresented: Binding(
                    get: { sheets.nameIllnessId != noIllnessId },
                    set: { if !$0 { closeName() } }
                ),
                illness: illness,
                initial: old,
                choices: state.illnessNames,
                now: now,
                onDismiss: closeName,
                onSave: { name in
                    closeName()
                    Task { await model.saveIllnessName(illness, old: old, name: name) }
                },
                onRemove: old.map { name in
                    {
                        closeName()
                        Task { await model.removeIllnessName(illness, name: name) }
                    }
                }
            )
        }
        if let illness = illnessById(sheets.recoverId) {
            IllnessRecoveredSheet(
                isPresented: Binding(
                    get: { sheets.recoverId != noIllnessId },
                    set: { if !$0 { sheets.recoverId = noIllnessId } }
                ),
                illness: illness,
                sex: child.sex,
                course: state.events.ongoingCourses(illness, now: now),
                now: now,
                onDismiss: { sheets.recoverId = noIllnessId },
                onSave: { end in
                    sheets.recoverId = noIllnessId
                    Task { await model.recoverIllness(illness, end: end) }
                }
            )
        }
        if let illness = illnessById(sheets.doctorId) {
            DoctorSheet(
                isPresented: Binding(
                    get: { sheets.doctorId != noIllnessId },
                    set: { if !$0 { sheets.doctorId = noIllnessId } }
                ),
                text: doctorText(
                    model: illnessScreenModel(events: state.events, illness: illness, now: now, timeZone: timeZone),
                    child: child,
                    lastWeight: state.lastWeight,
                    timeZone: timeZone
                ),
                onDismiss: { sheets.doctorId = noIllnessId }
            )
        }
    }

    private func illnessById(_ id: Int64) -> Illness? {
        state.events.compactMap { $0 as? Illness }.first { $0.id == id }
    }

    private func closeName() {
        sheets.nameIllnessId = noIllnessId
        sheets.nameIndex = -1
    }
}

/**
 Лист «Здоровье» (ADR-0040) за плиткой на «Сегодня»: лекарство, температура и «Заболел(а)», а пока
 болеет — «Болезнь · N-й день»: она открывает экран болезни. Под каждой строкой — когда было в последний
 раз: ночью по ним сверяются. Пол — для слова, имени нет.
 */
struct HealthSheet: View {
    @Binding var isPresented: Bool
    let state: DiaryUiState
    let now: Int64
    let onDismiss: () -> Void
    let onMedicine: () -> Void
    let onTemperature: () -> Void
    let onIllness: () -> Void

    var body: some View {
        AppSheet(
            isPresented: $isPresented,
            onDismiss: onDismiss,
            title: "Здоровье",
            icon: "ic_first_aid",
            edgeToEdge: true
        ) { closer in
            HealthSheetContent(
                state: state,
                now: now,
                onMedicine: { closer.close(then: onMedicine) },
                onTemperature: { closer.close(then: onTemperature) },
                onIllness: { closer.close(then: onIllness) }
            )
        }
    }
}

struct HealthSheetContent: View {
    @Environment(\.timeZone) private var timeZone
    let state: DiaryUiState
    let now: Int64
    let onMedicine: () -> Void
    let onTemperature: () -> Void
    var onIllness: () -> Void = {}

    var body: some View {
        let illness = state.activeIllness
        let today = toLocalDate(now, timeZone: timeZone)
        VStack(spacing: 0) {
            HealthRow(
                icon: "ic_medicine",
                title: "Лекарство",
                caption: state.lastMedicine.map { "\($0.name) — \(at($0.start))" },
                onClick: onMedicine
            )
            HealthRow(
                icon: "ic_thermometer",
                title: "Температура",
                caption: state.lastTemperature.map { "в прошлый раз \(at($0.start)) — \(temperatureValue($0.tenths))" },
                onClick: onTemperature
            )
            HealthRow(
                icon: "ic_illness",
                title: illness.map { "Болезнь · \($0.dayNumber(today, timeZone: timeZone))-й день" }
                    ?? bySex(state.child?.sex, masculine: "Заболел", feminine: "Заболела"),
                caption: illness.map { $0.names.map(\.name).joined(separator: ", ") }
                    .map { $0.isEmpty ? "без названия" : $0 } ??
                    "температура, лекарства и заметки соберутся в одну болезнь",
                onClick: onIllness,
                illnessTone: true
            )
        }
    }

    private func at(_ time: Int64) -> String {
        "\(formatShortDay(toLocalDate(time, timeZone: timeZone), today: toLocalDate(now, timeZone: timeZone), timeZone: timeZone)) в \(formatClock(time, timeZone: timeZone))"
    }
}

/** Строка листа «Здоровье»: иконка в круге, название и под ним — когда было в последний раз. */
private struct HealthRow: View {
    @Environment(\.babyLogTheme) private var theme
    let icon: String
    let title: String
    let caption: String?
    let onClick: () -> Void
    var illnessTone = false

    var body: some View {
        Button(action: onClick) {
            HStack(spacing: theme.spacing.m) {
                AppIcon(icon, tint: illnessTone ? theme.colors.onTertiaryContainer : theme.colors.onPrimaryContainer)
                    .frame(width: BabyLogSizes.healthRowIcon, height: BabyLogSizes.healthRowIcon)
                    .background(illnessTone ? theme.colors.tertiaryContainer : theme.colors.primaryContainer, in: Circle())
                VStack(alignment: .leading) {
                    Text(title).appTextStyle(theme.typography.bodyLarge)
                    if let caption {
                        Text(caption)
                            .appTextStyle(theme.typography.bodySmall)
                            .foregroundStyle(theme.colors.onSurfaceVariant)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                AppIcon("ic_chevron_right", tint: theme.colors.onSurfaceVariant)
            }
            .padding(.horizontal, theme.spacing.l)
            .padding(.vertical, theme.spacing.s)
            .frame(maxWidth: .infinity, minHeight: BabyLogSizes.rowMinHeight)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

/**
 Экран болезни `illness` внутри раздела (ADR-0040, ADR-0035): модель — из записей ребёнка,
 действия открывают листы `sheets`, правку записи (`onEditRecord`) и лист лекарства (`onAddMedicine`).
 */
struct IllnessScreen: View {
    @Environment(\.timeZone) private var timeZone
    let illness: Illness
    let state: DiaryUiState
    let child: Child
    let now: Int64
    @Bindable var sheets: HealthSheets
    let model: DiaryViewModel
    let onBack: () -> Void
    let onEditRecord: (any BabyEvent) -> Void
    let onAddMedicine: () -> Void

    var body: some View {
        IllnessContent(
            model: illnessScreenModel(events: state.events, illness: illness, now: now, timeZone: timeZone),
            sex: child.sex,
            now: now,
            actions: IllnessActions(
                onBack: onBack,
                onEdit: { onEditRecord(illness) },
                onAddName: { sheets.editName(illness, index: -1) },
                onEditName: { name in
                    sheets.editName(illness, index: illness.names.firstIndex(of: name) ?? -1)
                },
                onRecover: { sheets.recoverId = illness.id },
                onResume: !illness.isActive && state.activeIllness == nil ? {
                    Task { await model.resumeIllness(illness) }
                } : nil,
                onDoctor: { sheets.doctorId = illness.id },
                onAddTemperature: { sheets.temperature = true },
                onAddMedicine: onAddMedicine,
                onEditRecord: onEditRecord
            )
        )
    }
}
