import SwiftUI

/**
 Откуда часы ночи (ADR-0036): опросник BISQ и исследование сна детей 17 стран по нему.
 Ссылки — DOI: адрес статьи не меняется, даже если журнал переедет.
 */
let sleepSources = [
    (title: "Sadeh A., Pediatrics, 2004 — опросник BISQ", url: "https://doi.org/10.1542/peds.113.6.e570"),
    (title: "Mindell J. A. и др., Sleep Medicine, 2010 — сон малышей в 17 странах", url: "https://doi.org/10.1016/j.sleep.2009.04.012"),
]

/** Справка «?» на карточке «Сон»: как сон делится на ночной и дневной. */
struct SleepHelpSheet: View {
    @Binding var isPresented: Bool
    let onDismiss: () -> Void

    var body: some View {
        AppSheet(isPresented: $isPresented, onDismiss: onDismiss, title: "Сон ночью и днём", icon: "ic_help") { _ in
            SleepHelp()
        }
    }
}

/**
 Текст справки без листа — его снимает скриншот-тест. Источник открывается в браузере только
 по нажатию: само приложение в сеть не ходит (ADR-0002).
 */
struct SleepHelp: View {
    @Environment(\.babyLogTheme) private var theme

    var body: some View {
        Text("Ночью — сон с \(nightStartHour):00 до \(nightEndHour):00, днём — с \(nightEndHour):00 до \(nightStartHour):00. Так ночь и день заданы в опроснике детского сна BISQ — по нему сравнивали сон малышей в 17 странах:")
            .appTextStyle(theme.typography.bodyMedium)
        ForEach(sleepSources.indices, id: \.self) { index in
            let source = sleepSources[index]
            Link(source.title, destination: URL(string: source.url)!)
                .appTextStyle(theme.typography.bodyMedium)
                .foregroundStyle(theme.colors.primary)
                .underline()
        }
        Text("Сон, который переходит через \(nightEndHour):00 или \(nightStartHour):00, делится: сон с 18:30 до 20:00 — это 30 мин днём и 1 ч ночью.")
            .appTextStyle(theme.typography.bodyMedium)
        Text("Столбик — сон за сутки с полуночи до полуночи, как в сводке дня: ночная часть в нём — утро до \(nightEndHour):00 и вечер после \(nightStartHour):00. Средние — по прошедшим суткам, в которые вели дневник.")
            .appTextStyle(theme.typography.bodyMedium)
    }
}
