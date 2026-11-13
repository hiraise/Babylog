import SwiftUI

/** Действия экрана болезни: правка дат и названий, «Выздоровел(а)», «Для врача» и новые записи болезни. */
struct IllnessActions {
    var onBack: () -> Void = {}
    var onEdit: () -> Void = {}
    var onAddName: () -> Void = {}
    var onEditName: (IllnessName) -> Void = { _ in }
    var onRecover: () -> Void = {}
    var onResume: (() -> Void)? = nil
    var onDoctor: () -> Void = {}
    var onAddTemperature: () -> Void = {}
    var onAddMedicine: () -> Void = {}
    var onEditRecord: (any BabyEvent) -> Void = { _ in }
}

/**
 Экран болезни (ADR-0040) — внутри раздела, «Назад» возвращает к нему. Сверху то, что нужно сразу:
 сколько болеет, названия с днями, «Для врача» и «Выздоровел(а)» — их не ищут в конце ленты. Ниже —
 температура с приёмами лекарств, лекарства, «по дневнику» против обычного и сутки болезни, свежие сверху.
 Касание записи — её правка (`IllnessActions.onEditRecord`).
 */
struct IllnessContent: View {
    @Environment(\.babyLogTheme) private var theme
    let model: IllnessScreenModel
    let sex: Sex?
    let now: Int64
    let actions: IllnessActions

    var body: some View {
        ScrollView {
            LazyVStack(spacing: theme.spacing.m) {
                IllnessHeader(actions: actions)
                IllnessSummaryCard(model: model, sex: sex, actions: actions)
                TemperatureCard(
                    model: model,
                    now: now,
                    onAdd: model.illness.isActive ? actions.onAddTemperature : nil
                )
                MedicinesCard(
                    medicines: model.medicines,
                    now: now,
                    onAdd: model.illness.isActive ? actions.onAddMedicine : nil
                )
                if let diary = model.diary { DiaryCard(diary: diary) }
                Text("По дням")
                    .appTextStyle(theme.typography.titleMedium)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.top, theme.spacing.s)
                    .accessibilityAddTraits(.isHeader)
                ForEach(model.byDay, id: \.date) { day in
                    VStack(alignment: .leading, spacing: theme.spacing.s) {
                        Text("\(formatDayHeader(day.date, today: model.today)) · \(day.number)-й день")
                            .appTextStyle(theme.typography.titleSmall)
                            .foregroundStyle(theme.colors.onSurfaceVariant)
                            .padding(.horizontal, theme.spacing.xs)
                            .accessibilityAddTraits(.isHeader)
                        DayCard(day: day, sex: sex, onEdit: actions.onEditRecord)
                    }
                }
            }
            .padding(EdgeInsets(
                top: theme.spacing.m,
                leading: theme.spacing.l,
                bottom: theme.spacing.xl,
                trailing: theme.spacing.l
            ))
        }
    }
}

/** «‹ Болезнь» и карандаш — правка начала, конца и комментария. */
private struct IllnessHeader: View {
    @Environment(\.babyLogTheme) private var theme
    let actions: IllnessActions

    var body: some View {
        HStack(spacing: 0) {
            Button(action: actions.onBack) {
                AppIcon("ic_chevron_left", contentDescription: "Назад")
                    .frame(width: BabyLogSizes.touchTarget, height: BabyLogSizes.touchTarget)
            }
            .buttonStyle(.plain)
            Text("Болезнь")
                .appTextStyle(theme.typography.headlineSmall)
                .frame(maxWidth: .infinity, alignment: .leading)
                .accessibilityAddTraits(.isHeader)
            Button(action: actions.onEdit) {
                AppIcon("ic_edit", contentDescription: "Изменить даты и комментарий")
                    .frame(width: BabyLogSizes.touchTarget, height: BabyLogSizes.touchTarget)
            }
            .buttonStyle(.plain)
        }
    }
}

/**
 Шапка на фоне болезни: «Болеет · 4-й день» (прошлая — «Болела 8 дней»), с какого дня, названия
 с днём «с» (касание — правка), «+ название», «Для врача» и «Выздоровел(а)»; у прошлой — «Ещё болеет»,
 если закрыли по ошибке и другой болезни нет.
 */
private struct IllnessSummaryCard: View {
    @Environment(\.babyLogTheme) private var theme
    @Environment(\.timeZone) private var timeZone
    let model: IllnessScreenModel
    let sex: Sex?
    let actions: IllnessActions

    var body: some View {
        let illness = model.illness
        let first = illness.startDate(timeZone)
        let endDay = illnessCalendar(timeZone).date(byAdding: .day, value: model.days - 1, to: first) ?? first
        AppCard(containerColor: theme.colors.illnessContainer, contentColor: theme.colors.onTertiaryContainer) {
            VStack(alignment: .leading, spacing: theme.spacing.s) {
                Text(illness.isActive
                    ? "Болеет · \(model.days)-й день"
                    : "\(bySex(sex, masculine: "Болел", feminine: "Болела")) \(model.days) \(plural(model.days, one: "день", few: "дня", many: "дней"))")
                    .appTextStyle(theme.typography.headlineSmall)
                Text(illness.isActive
                    ? "с \(formatWeekdayDay(first, timeZone: timeZone)), \(formatClock(illness.start, timeZone: timeZone))"
                    : formatDayRange(first: first, last: endDay, timeZone: timeZone))
                    .appTextStyle(theme.typography.bodyMedium)
                    .foregroundStyle(theme.colors.tertiary)
                FlowRow(spacing: theme.spacing.s, rowSpacing: theme.spacing.xs) {
                    ForEach(illness.names, id: \.self) { name in
                        IllnessNameChip(
                            name: name.name,
                            since: "с \(formatDayRange(first: name.sinceDate(illness, timeZone: timeZone), last: name.sinceDate(illness, timeZone: timeZone), timeZone: timeZone))"
                        ) { actions.onEditName(name) }
                    }
                    Button(illness.names.isEmpty ? "+ Название" : "+ название", action: actions.onAddName)
                        .buttonStyle(.plain)
                        .foregroundStyle(theme.colors.primary)
                        .appTextStyle(theme.typography.labelLarge)
                        .frame(minHeight: BabyLogSizes.touchTarget)
                }
                HStack(spacing: theme.spacing.s) {
                    Button(action: actions.onDoctor) {
                        HStack(spacing: theme.spacing.xs) {
                            AppIcon("ic_share")
                            Text("Для врача")
                        }
                        .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered)
                    .tint(theme.colors.tertiary)
                    if illness.isActive {
                        Button(bySex(sex, masculine: "Выздоровел", feminine: "Выздоровела"), action: actions.onRecover)
                            .buttonStyle(.borderedProminent)
                            .tint(theme.colors.primary)
                    } else if let onResume = actions.onResume {
                        Button("Ещё болеет", action: onResume)
                            .buttonStyle(.plain)
                            .foregroundStyle(theme.colors.primary)
                            .frame(maxWidth: .infinity)
                    }
                }
            }
        }
    }
}

/** Название болезни пилюлей на фоне карточки: «ОРВИ  с 7 окт.»; касание — правка или «Убрать». */
private struct IllnessNameChip: View {
    @Environment(\.babyLogTheme) private var theme
    let name: String
    let since: String
    let onClick: () -> Void

    var body: some View {
        Button(action: onClick) {
            HStack(spacing: theme.spacing.s) {
                Text(name).appTextStyle(theme.typography.labelLarge)
                Text(since)
                    .appTextStyle(theme.typography.bodySmall)
                    .foregroundStyle(theme.colors.onSurfaceVariant)
            }
            .padding(.horizontal, theme.spacing.m)
            .padding(.vertical, theme.spacing.s)
            .frame(minHeight: BabyLogSizes.touchTarget)
            .background(theme.colors.surfaceContainerLow, in: RoundedRectangle(cornerRadius: theme.shapes.medium))
            .contentShape(RoundedRectangle(cornerRadius: theme.shapes.medium))
        }
        .buttonStyle(.plain)
        .accessibilityHint("изменить название")
    }
}
