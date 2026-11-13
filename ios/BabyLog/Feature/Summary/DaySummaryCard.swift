import SwiftUI

/** Строка сводки: иконка, метка («Кормлений:») и значение («5»). */
struct SummaryLine: Equatable, Sendable {
    var icon: String
    var label: String
    var value: String
    var parts: [SummaryPart] = []

    /** Строка так, как её читает VoiceOver: вместо иконок разбивки — их описания. */
    var shown: String {
        let base = "\(label) \(value)"
        guard !parts.isEmpty else { return base }
        return base + " (" + parts.map { "\($0.description)×\($0.count)\($0.suffix)" }.joined(separator: " · ") + ")"
    }
}

/** Часть разбивки: иконка, сколько раз, описание для VoiceOver и суффикс после числа. */
struct SummaryPart: Equatable, Sendable {
    var icon: String
    var count: Int
    var description: String
    var suffix: String = ""
}

extension DaySummary {
    /** Кормления, подгузники, сон, лекарства и — в дни, когда они были, — плач и температура. */
    func lines(sex: Sex?) -> [SummaryLine] {
        let feedingParts: [SummaryPart]
        if bottles == 0 {
            feedingParts = []
        } else {
            feedingParts = [
                breastFeedings > 0 ? SummaryPart(icon: "ic_feeding", count: breastFeedings, description: "грудью") : nil,
                SummaryPart(
                    icon: "ic_bottle",
                    count: bottles,
                    description: "бутылочкой",
                    suffix: ", \(bottleMl)\u{00A0}мл"
                ),
            ].compactMap { $0 }
        }
        let diaperParts = DiaperKind.allCases.compactMap { kind -> SummaryPart? in
            guard let count = diapers[kind], count > 0 else { return nil }
            return SummaryPart(icon: diaperIcon(kind), count: count, description: diaperLabel(kind, sex: sex))
        }
        let sleepValue = sleepMillis > 0 ? formatDuration(sleepMillis) : "0 мин"
        let medicinesCount = medicines.reduce(0) { $0 + $1.count }
        let medicinesValue = String(medicinesCount) + (medicines.isEmpty ? "" : " (" + medicines.map {
            $0.count > 1 ? "\($0.name) ×\($0.count)" : $0.name
        }.joined(separator: ", ") + ")")

        var result = [
            SummaryLine(icon: "ic_feeding", label: "Кормлений:", value: String(feedings), parts: feedingParts),
            SummaryLine(icon: "ic_diaper", label: "Подгузников:", value: String(diaperCount), parts: diaperParts),
            SummaryLine(icon: "ic_sleep", label: "Сон:", value: sleepValue),
            SummaryLine(icon: "ic_medicine", label: "Лекарств:", value: medicinesValue),
        ]
        if cryings > 0 || cryingMillis > 0 {
            let value = cryings > 0
                ? "\(cryings) \(timesWord(cryings)), \(formatDuration(cryingMillis))"
                : formatDuration(cryingMillis)
            result.append(SummaryLine(icon: "ic_crying", label: "Плач:", value: value))
        }
        if let maxTemperatureTenths {
            let value = temperatures == 1
                ? temperatureValue(maxTemperatureTenths)
                : "до \(temperatureValue(maxTemperatureTenths)) (\(temperatures) \(timesWord(temperatures)))"
            result.append(SummaryLine(icon: "ic_thermometer", label: "Температура:", value: value))
        }
        return result
    }
}

private func timesWord(_ n: Int) -> String {
    if (11...14).contains(n % 100) { return "раз" }
    return (2...4).contains(n % 10) ? "раза" : "раз"
}

/**
 Сводка дня; `sex` — пол ребёнка, для «пописал / пописала».
 Заголовка с днём нет: какой это день, видно по вкладке «Сегодня» или выбору дня в «Истории».
 */
struct DaySummaryCard: View {
    @Environment(\.babyLogTheme) private var theme
    @Environment(\.timeZone) private var timeZone

    let events: [any BabyEvent]
    let date: Date
    let now: Int64
    let sex: Sex?

    var body: some View {
        let lines = summarizeDay(events: events, date: date, now: now, timeZone: timeZone).lines(sex: sex)
        AppCard {
            VStack(alignment: .leading, spacing: theme.spacing.s) {
                ForEach(lines, id: \.label) { line in
                    SummaryLineView(line: line)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}

private struct SummaryLineView: View {
    @Environment(\.babyLogTheme) private var theme
    let line: SummaryLine

    var body: some View {
        HStack(alignment: .center, spacing: theme.spacing.s) {
            AppIcon(line.icon, size: 20, tint: theme.colors.primary)
            Text(line.label).fontWeight(.medium) + Text(" \(line.value)")
            if !line.parts.isEmpty {
                Text("(")
                ForEach(Array(line.parts.enumerated()), id: \.offset) { index, part in
                    if index > 0 { Text("·") }
                    AppIcon(part.icon, size: 20, tint: theme.colors.primary)
                    Text("×\(part.count)\(part.suffix)")
                }
                Text(")")
            }
        }
        .appTextStyle(theme.typography.bodyMedium)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(line.shown)
    }
}
