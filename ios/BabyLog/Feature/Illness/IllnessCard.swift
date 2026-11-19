import SwiftUI

private let illnessIconSize: CGFloat = 28
/** Лекарство давали так давно — пишем, когда это было, а не «… назад»: сутки «назад» не считают. */
private let illnessAgoLimitMillis: Int64 = 24 * 60 * 60 * 1000

/**
 Карточка идущей болезни на «Сегодня» (ADR-0040), над таймерами: день болезни и названия, последняя
 температура и сколько прошло с последнего лекарства — ночью это главное. Только факты: без доз,
 норм и оценок. Касание карточки, кроме кнопок, открывает экран болезни (`onOpen`); кнопки — температура,
 лекарство и заметка: за ними и открывают «Сегодня», пока ребёнок болеет.
 */
struct IllnessCard: View {
    @Environment(\.babyLogTheme) private var theme
    @Environment(\.timeZone) private var timeZone

    let model: IllnessCardModel
    let now: Int64
    let onOpen: () -> Void
    let onTemperature: () -> Void
    let onMedicine: () -> Void
    let onNote: () -> Void

    var body: some View {
        let today = toLocalDate(now, timeZone: timeZone)
        let start = model.illness.startDate(timeZone)
        AppCard(
            contentPadding: EdgeInsets(
                top: theme.spacing.m,
                leading: theme.spacing.m,
                bottom: theme.spacing.m,
                trailing: theme.spacing.m
            ),
            containerColor: theme.colors.illnessContainer,
            contentColor: theme.colors.onTertiaryContainer
        ) {
            VStack(alignment: .leading, spacing: theme.spacing.s) {
                Button(action: onOpen) {
                    VStack(alignment: .leading, spacing: theme.spacing.s) {
                        HStack(spacing: theme.spacing.m) {
                            AppIcon("ic_illness", size: illnessIconSize, tint: theme.colors.tertiary)
                            VStack(alignment: .leading, spacing: 0) {
                                Text("Болеет · \(model.day)-й день")
                                    .appTextStyle(theme.typography.titleMedium)
                                let since = start == today
                                    ? "с \(formatClock(model.illness.start, timeZone: timeZone))"
                                    : "с \(formatWeekdayDay(start, timeZone: timeZone))"
                                Text("\(model.names ?? "без названия") · \(since)")
                                    .appTextStyle(theme.typography.bodySmall)
                                    .foregroundStyle(theme.colors.tertiary)
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            AppIcon("ic_chevron_right", tint: theme.colors.tertiary)
                        }
                        if let temperature = model.lastTemperature {
                            Text("Температура \(temperatureValue(temperature.tenths)) \(illnessWhenText(temperature.start, now: now, timeZone: timeZone))")
                                .appTextStyle(theme.typography.bodyMedium)
                        }
                        if let medicine = model.lastMedicine {
                            Text(medicineText(medicine))
                                .appTextStyle(theme.typography.bodyMedium)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityHint("открыть болезнь")
                HStack(spacing: theme.spacing.s) {
                    IllnessCardButton(label: "Температура", onClick: onTemperature)
                    IllnessCardButton(label: "Лекарство", onClick: onMedicine)
                    IllnessCardButton(label: "Заметка", onClick: onNote)
                }
            }
        }
    }

    private func medicineText(_ medicine: IllnessMedicine) -> String {
        if now - medicine.last < illnessAgoLimitMillis {
            return "\(medicine.name) — \(formatAgo(now - medicine.last)), в \(formatClock(medicine.last, timeZone: timeZone))"
        }
        let when = illnessWhenText(medicine.last, now: now, timeZone: timeZone)
        return "\(medicine.name) — \(when.hasPrefix("в ") ? String(when.dropFirst(2)) : when)"
    }
}

/** «в 03:10», «вчера в 21:00», «9 окт. в 21:00». */
private func illnessWhenText(_ time: Int64, now: Int64, timeZone: TimeZone) -> String {
    let day = toLocalDate(time, timeZone: timeZone)
    let today = toLocalDate(now, timeZone: timeZone)
    if day == today { return "в \(formatClock(time, timeZone: timeZone))" }
    return "\(formatShortDay(day, today: today, timeZone: timeZone)) в \(formatClock(time, timeZone: timeZone))"
}

/** Кнопка карточки — светлая на фоне болезни: три в ряд, подпись уменьшается, если не влезает. */
private struct IllnessCardButton: View {
    @Environment(\.babyLogTheme) private var theme
    let label: String
    let onClick: () -> Void

    var body: some View {
        Button(action: onClick) {
            Text(label)
                .appTextStyle(theme.typography.labelLarge)
                .lineLimit(1)
                .minimumScaleFactor(0.75)
                .frame(maxWidth: .infinity)
                .padding(.horizontal, theme.spacing.xs)
        }
        .buttonStyle(.borderedProminent)
        .buttonBorderShape(.roundedRectangle(radius: theme.shapes.large))
        .tint(theme.colors.surfaceContainerLow)
        .foregroundStyle(theme.colors.onSurface)
    }
}
