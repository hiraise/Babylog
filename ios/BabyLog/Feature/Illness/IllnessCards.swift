import SwiftUI

private let illnessHeaderIconSize: CGFloat = 20
private let illnessRowIconSize: CGFloat = 20
private let illnessTimeWidth: CGFloat = 44

/** Заголовок карточки: иконка, название и справа мелко — пояснение. */
private struct IllnessCardTitle: View {
    @Environment(\.babyLogTheme) private var theme
    let icon: String
    let title: String
    var trailing: String? = nil

    var body: some View {
        HStack(spacing: theme.spacing.s) {
            AppIcon(icon, size: illnessHeaderIconSize, tint: theme.colors.primary)
            Text(title)
                .appTextStyle(theme.typography.titleMedium)
                .frame(maxWidth: .infinity, alignment: .leading)
                .accessibilityAddTraits(.isHeader)
            if let trailing {
                Text(trailing)
                    .appTextStyle(theme.typography.bodySmall)
                    .foregroundStyle(theme.colors.onSurfaceVariant)
            }
        }
    }
}

/** «сегодня в 03:10», «9 окт. в 15:30». */
private func illnessAt(_ time: Int64, now: Int64, timeZone: TimeZone) -> String {
    "\(formatShortDay(toLocalDate(time, timeZone: timeZone), today: toLocalDate(now, timeZone: timeZone), timeZone: timeZone)) в \(formatClock(time, timeZone: timeZone))"
}

/**
 Температура (ADR-0041): последняя крупно, самая высокая — справа, под ними график со всеми замерами
 и приёмами лекарств. `onAdd` — новый замер, у идущей болезни.
 */
struct TemperatureCard: View {
    @Environment(\.babyLogTheme) private var theme
    @Environment(\.timeZone) private var timeZone
    let model: IllnessScreenModel
    let now: Int64
    let onAdd: (() -> Void)?

    var body: some View {
        let temperatures = model.temperatures
        let maximum = temperatures.max { $0.tenths == $1.tenths ? $0.start < $1.start : $0.tenths < $1.tenths }
        let last = temperatures.max { $0.start < $1.start }
        AppCard {
            VStack(alignment: .leading, spacing: theme.spacing.s) {
                IllnessCardTitle(
                    icon: "ic_thermometer",
                    title: "Температура",
                    trailing: maximum.map { "макс. \(temperatureValue($0.tenths)) · \(illnessAt($0.start, now: now, timeZone: timeZone))" }
                )
                if let last, let maximum {
                    HStack(alignment: .firstTextBaseline, spacing: theme.spacing.s) {
                        Text(temperatureValue(last.tenths)).appTextStyle(theme.typography.headlineMedium)
                        Text(illnessAt(last.start, now: now, timeZone: timeZone))
                            .appTextStyle(theme.typography.bodyMedium)
                            .foregroundStyle(theme.colors.onSurfaceVariant)
                    }
                    let to = max(model.illness.end ?? now, model.medicines.map(\.last).max() ?? 0, last.start)
                    IllnessTemperatureChart(
                        temperatures: temperatures,
                        medicines: model.medicines.sorted { ($0.times.first ?? 0) < ($1.times.first ?? 0) },
                        illness: model.illness,
                        from: model.illness.start,
                        to: to,
                        timeZone: timeZone
                    )
                    .accessibilityLabel("Температура: \(temperatures.count) \(plural(temperatures.count, one: "замер", few: "замера", many: "замеров")), самая высокая \(formatTemperature(maximum.tenths)), последняя \(formatTemperature(last.tenths)) \(illnessAt(last.start, now: now, timeZone: timeZone))")
                } else {
                    Text("Температуру не записывали")
                        .appTextStyle(theme.typography.bodyMedium)
                        .foregroundStyle(theme.colors.onSurfaceVariant)
                }
                if let onAdd {
                    Button("+ Температура", action: onAdd)
                        .buttonStyle(.plain)
                        .foregroundStyle(theme.colors.primary)
                        .appTextStyle(theme.typography.labelLarge)
                }
            }
        }
    }
}

private struct IllnessTemperatureChart: View {
    @Environment(\.babyLogTheme) private var theme
    let temperatures: [Temperature]
    let medicines: [IllnessMedicine]
    let illness: Illness
    let from: Int64
    let to: Int64
    let timeZone: TimeZone

    var body: some View {
        let days = illnessChartDays(illness: illness, to: to, timeZone: timeZone)
        let minimum = Double((temperatures.map(\.tenths).min() ?? 360) - 3) / 10
        let maximum = Double((temperatures.map(\.tenths).max() ?? 400) + 3) / 10
        VStack(spacing: theme.spacing.xs) {
            Canvas { context, size in
                let duration = max(to - from, 1)
                func x(_ time: Int64) -> CGFloat { CGFloat(time - from) / CGFloat(duration) * size.width }
                func y(_ tenths: Int) -> CGFloat {
                    size.height * (1 - CGFloat((Double(tenths) / 10 - minimum) / max(maximum - minimum, 0.1)))
                }
                for day in days {
                    var grid = Path()
                    grid.move(to: CGPoint(x: x(day.time), y: 0))
                    grid.addLine(to: CGPoint(x: x(day.time), y: size.height))
                    context.stroke(grid, with: .color(theme.colors.outlineVariant), lineWidth: 0.5)
                }
                for name in illness.names {
                    guard let since = name.since, since > illness.startDate(timeZone) else { continue }
                    let millis = Int64(since.timeIntervalSince1970 * 1000)
                    var marker = Path()
                    marker.move(to: CGPoint(x: x(millis), y: 0))
                    marker.addLine(to: CGPoint(x: x(millis), y: size.height))
                    context.stroke(marker, with: .color(theme.colors.tertiary), style: StrokeStyle(lineWidth: 1, dash: [3, 3]))
                }
                let sorted = temperatures.sorted { $0.start < $1.start }
                var line = Path()
                for (index, temperature) in sorted.enumerated() {
                    let point = CGPoint(x: x(temperature.start), y: y(temperature.tenths))
                    if index == 0 { line.move(to: point) } else { line.addLine(to: point) }
                    context.fill(Path(ellipseIn: CGRect(x: point.x - 3, y: point.y - 3, width: 6, height: 6)), with: .color(theme.chartColors.temperature))
                }
                if sorted.count > 1 {
                    context.stroke(line, with: .color(theme.chartColors.temperature), lineWidth: 2)
                }
                for medicine in medicines {
                    for time in medicine.times {
                        context.fill(
                            Path(ellipseIn: CGRect(x: x(time) - 2, y: size.height - 5, width: 4, height: 4)),
                            with: .color(theme.colors.primary)
                        )
                    }
                }
            }
            .frame(height: BabyLogSizes.illnessChartHeight)
            GeometryReader { geometry in
                let duration = max(to - from, 1)
                ForEach(days.indices, id: \.self) { index in
                    Text(days[index].label)
                        .appTextStyle(theme.typography.bodySmall)
                        .foregroundStyle(theme.colors.onSurfaceVariant)
                        .position(
                            x: min(max(CGFloat(days[index].time - from) / CGFloat(duration) * geometry.size.width, 18), geometry.size.width - 18),
                            y: 8
                        )
                }
            }
            .frame(height: BabyLogSizes.illnessChartLabelsHeight)
            if !medicines.isEmpty {
                Text(medicines.map(\.name).joined(separator: " · "))
                    .appTextStyle(theme.typography.bodySmall)
                    .foregroundStyle(theme.colors.primary)
            }
        }
    }
}

/** Сутки на шкале графика: начало каждых суток болезни (первые — с её начала) и подпись «ср 7». */
private func illnessChartDays(illness: Illness, to: Int64, timeZone: TimeZone) -> [(time: Int64, label: String)] {
    let first = illness.startDate(timeZone)
    let last = toLocalDate(to, timeZone: timeZone)
    let formatter = DateFormatter()
    formatter.calendar = Calendar(identifier: .gregorian)
    formatter.locale = RU
    formatter.timeZone = timeZone
    formatter.dateFormat = "EE d"
    var result: [(Int64, String)] = []
    var date = first
    while date <= last {
        result.append((max(Int64(date.timeIntervalSince1970 * 1000), illness.start), formatter.string(from: date)))
        guard let next = illnessCalendar(timeZone).date(byAdding: .day, value: 1, to: date) else { break }
        date = next
    }
    return result
}

/** Лекарства болезни: название и доза, сколько раз и когда последний — только факты. `onAdd` — новый приём. */
struct MedicinesCard: View {
    @Environment(\.babyLogTheme) private var theme
    @Environment(\.timeZone) private var timeZone
    let medicines: [IllnessMedicine]
    let now: Int64
    let onAdd: (() -> Void)?

    var body: some View {
        AppCard {
            VStack(alignment: .leading, spacing: theme.spacing.s) {
                IllnessCardTitle(icon: "ic_medicine", title: "Лекарства")
                if medicines.isEmpty {
                    Text("Лекарства не записывали")
                        .appTextStyle(theme.typography.bodyMedium)
                        .foregroundStyle(theme.colors.onSurfaceVariant)
                }
                ForEach(medicines.indices, id: \.self) { index in
                    let medicine = medicines[index]
                    if index > 0 { Divider().overlay(theme.colors.outlineVariant) }
                    let name = [medicine.name, medicine.note.trimmingCharacters(in: .whitespacesAndNewlines)]
                        .filter { !$0.isEmpty }.joined(separator: " · ")
                    Text(name).appTextStyle(theme.typography.bodyLarge)
                    Text("\(medicine.count) \(plural(medicine.count, one: "раз", few: "раза", many: "раз")) · последний \(illnessAt(medicine.last, now: now, timeZone: timeZone))" + (now - medicine.last < 24 * 60 * 60_000 ? ", \(formatAgo(now - medicine.last))" : ""))
                        .appTextStyle(theme.typography.bodySmall)
                        .foregroundStyle(theme.colors.onSurfaceVariant)
                }
                if let onAdd {
                    Button("+ Лекарство", action: onAdd)
                        .buttonStyle(.plain)
                        .foregroundStyle(theme.colors.primary)
                        .appTextStyle(theme.typography.labelLarge)
                }
            }
        }
    }
}

/**
 «Вчера по дневнику» у идущей болезни, «В дни болезни» у прошлой: подгузники с мочой, кормления, сон
 и плач — рядом «обычно» за две недели до болезни, если было с чем сравнить.
 */
struct DiaryCard: View {
    @Environment(\.babyLogTheme) private var theme
    let diary: DiaryComparison

    var body: some View {
        AppCard {
            VStack(alignment: .leading, spacing: theme.spacing.s) {
                IllnessCardTitle(
                    icon: "ic_diary",
                    title: diary.yesterday ? "Вчера по дневнику" : "В дни болезни в среднем",
                    trailing: diary.lines.contains { $0.usual != nil } ? "обычно — за 2 недели до болезни" : nil
                )
                ForEach(diary.lines, id: \.kind) { line in
                    let content: (icon: String, title: String) = {
                        switch line.kind {
                        case .wetDiapers: ("ic_diaper", "Мокрых подгузников")
                        case .feedings: ("ic_feeding", "Кормлений")
                        case .sleep: ("ic_sleep", "Сон")
                        case .crying: ("ic_crying", "Плач")
                        }
                    }()
                    HStack(spacing: theme.spacing.m) {
                        AppIcon(content.icon, size: illnessRowIconSize, tint: theme.colors.primary)
                        VStack(alignment: .leading, spacing: 0) {
                            Text("\(content.title) — \(line.format(line.value))")
                                .appTextStyle(theme.typography.bodyMedium)
                            if let usual = line.usual {
                                Text("обычно \(line.format(usual))")
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

/**
 Сутки болезни: записи свежие сверху — время, иконка, что было. Начало, конец и новое название —
 цветом болезни. Касание записи — правка (`onEdit`).
 */
struct DayCard: View {
    @Environment(\.babyLogTheme) private var theme
    @Environment(\.timeZone) private var timeZone
    let day: IllnessDay
    let sex: Sex?
    let onEdit: (any BabyEvent) -> Void

    var body: some View {
        AppCard(contentPadding: EdgeInsets(top: theme.spacing.xs, leading: 0, bottom: theme.spacing.xs, trailing: 0)) {
            if day.entries.isEmpty {
                Text("Записей нет")
                    .appTextStyle(theme.typography.bodyMedium)
                    .foregroundStyle(theme.colors.onSurfaceVariant)
                    .padding(.horizontal, theme.spacing.l)
                    .padding(.vertical, theme.spacing.s)
            }
            ForEach(day.entries.indices, id: \.self) { index in
                let entry = day.entries[index]
                if index > 0 {
                    Divider().overlay(theme.colors.outlineVariant).padding(.horizontal, theme.spacing.l)
                }
                if case let .record(event) = entry {
                    Button { onEdit(event) } label: { DayEntryRow(entry: entry, sex: sex, timeZone: timeZone) }
                        .buttonStyle(.plain)
                        .accessibilityHint("изменить запись")
                } else {
                    DayEntryRow(entry: entry, sex: sex, timeZone: timeZone)
                }
            }
        }
    }
}

private struct DayEntryRow: View {
    @Environment(\.babyLogTheme) private var theme
    let entry: DayEntry
    let sex: Sex?
    let timeZone: TimeZone

    var body: some View {
        let line = illnessEntryLine(entry, sex: sex, timeZone: timeZone)
        HStack(alignment: .top, spacing: theme.spacing.s) {
            Text(entry.time.map { formatClock($0, timeZone: timeZone) } ?? "")
                .appTextStyle(theme.typography.bodyMedium)
                .foregroundStyle(theme.colors.onSurfaceVariant)
                .frame(width: illnessTimeWidth, alignment: .leading)
            AppIcon(line.icon, size: illnessRowIconSize, tint: line.illnessTone ? theme.colors.tertiary : theme.colors.primary)
            Text(line.text)
                .appTextStyle(theme.typography.bodyMedium)
                .foregroundStyle(line.illnessTone ? theme.colors.tertiary : theme.colors.onSurface)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.horizontal, theme.spacing.l)
        .padding(.vertical, theme.spacing.s)
        .frame(maxWidth: .infinity, alignment: .leading)
        .contentShape(Rectangle())
    }
}

/** Иконка, текст строки суток и «цвет болезни» — у начала, конца и нового названия. */
private func illnessEntryLine(_ entry: DayEntry, sex: Sex?, timeZone: TimeZone) -> (icon: String, text: String, illnessTone: Bool) {
    switch entry {
    case .started:
        ("ic_illness", bySex(sex, masculine: "Заболел", feminine: "Заболела"), true)
    case .ended:
        ("ic_illness", bySex(sex, masculine: "Выздоровел", feminine: "Выздоровела"), true)
    case let .nameAdded(name):
        ("ic_add", "Название: \(name.name)", true)
    case let .record(event):
        (eventIcon(event), illnessRecordText(event, sex: sex, timeZone: timeZone), false)
    }
}

/** Запись болезни словами: «38,8 °C · подмышкой», «Ибупрофен · 4 мл», текст заметки с метками. */
private func illnessRecordText(_ event: any BabyEvent, sex: Sex?, timeZone: TimeZone) -> String {
    switch event {
    case let event as Temperature:
        return formatTemperature(event.tenths) + (event.method.map { " · \(temperatureMethodLabel($0))" } ?? "")
    case let event as Medicine:
        return [event.name, event.note.trimmingCharacters(in: .whitespacesAndNewlines)].filter { !$0.isEmpty }.joined(separator: " · ")
    case let event as Note:
        let text = [event.note.trimmingCharacters(in: .whitespacesAndNewlines), event.tags.joined(separator: ", ")]
            .filter { !$0.isEmpty }.joined(separator: " · ")
        return text.isEmpty ? "Заметка" : text
    default:
        return "\(eventTitle(event, sex: sex)) · \(eventTimeLine(event, timeZone: timeZone))"
    }
}
