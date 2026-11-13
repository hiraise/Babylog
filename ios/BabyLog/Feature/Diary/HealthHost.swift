import SwiftUI

/**
 Лист «Здоровье» за плиткой на «Сегодня»: лекарство и температура с последними значениями.
 Строка начала или открытия болезни подключается вместе с экраном болезни в S4e.
 */
struct HealthSheet: View {
    @Binding var isPresented: Bool
    let state: DiaryUiState
    let now: Int64
    let onDismiss: () -> Void
    let onMedicine: () -> Void
    let onTemperature: () -> Void

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
                onTemperature: { closer.close(then: onTemperature) }
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

    var body: some View {
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

    var body: some View {
        Button(action: onClick) {
            HStack(spacing: theme.spacing.m) {
                AppIcon(icon, tint: theme.colors.onPrimaryContainer)
                    .frame(width: BabyLogSizes.healthRowIcon, height: BabyLogSizes.healthRowIcon)
                    .background(theme.colors.primaryContainer, in: Circle())
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
