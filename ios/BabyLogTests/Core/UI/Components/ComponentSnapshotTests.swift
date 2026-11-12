import SnapshotTesting
import SwiftUI
import Testing
import UIKit
@testable import BabyLog

@MainActor
@Suite(.serialized)
struct ComponentSnapshotTests {
    @Test(arguments: [false, true]) func appCard(dark: Bool) {
        assertComponentSnapshot(name: dark ? "dark" : "light", style: dark ? .dark : .light) {
            AppCard {
                Text("Сегодня").appTextStyle(BabyLogTypeScale.titleMedium)
                Text("Кормлений: 6 · последнее: левая, 25 мин назад")
                    .appTextStyle(BabyLogTypeScale.bodyMedium)
                Text("Сон: 9 ч 40 мин").appTextStyle(BabyLogTypeScale.bodyMedium)
            }
            AppCard(onClick: {}, onClickLabel: "изменить данные ребёнка") {
                Text("Маша").appTextStyle(BabyLogTypeScale.titleMedium)
                Text("Нажимаемая карточка")
                    .appTextStyle(BabyLogTypeScale.bodyMedium)
            }
        }
    }

    @Test(arguments: [false, true]) func appIcon(dark: Bool) {
        assertComponentSnapshot(name: dark ? "dark" : "light", style: dark ? .dark : .light) {
            LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 5), spacing: 12) {
                ForEach(AllIcons, id: \.self) { name in
                    VStack(spacing: 4) {
                        AppIcon(name)
                        Text(name.replacingOccurrences(of: "ic_", with: ""))
                            .appTextStyle(BabyLogTypeScale.labelSmall)
                            .lineLimit(1)
                            .minimumScaleFactor(0.7)
                    }
                }
            }
        }
    }

    @Test(arguments: [false, true]) func actionTile(dark: Bool) {
        assertComponentSnapshot(name: dark ? "dark" : "light", style: dark ? .dark : .light) {
            ForEach([false, true], id: \.self) { highlight in
                HStack(spacing: 8) {
                    ActionTile(
                        icon: "ic_diaper",
                        label: "Подгузник",
                        caption: highlight ? "3 ч 20 мин назад" : "25 мин назад",
                        highlight: highlight
                    ) {}
                    ActionTile(icon: "ic_first_aid", label: "Здоровье") {}
                    ActionTile(icon: "ic_weight", label: "Вес", caption: "вчера") {}
                    ActionTile(icon: "ic_height", label: "Рост", caption: "15 окт.") {}
                }
            }
        }
    }

    @Test(arguments: [false, true]) func choiceChip(dark: Bool) {
        assertComponentSnapshot(name: dark ? "dark" : "light", style: dark ? .dark : .light) {
            HStack(spacing: 8) {
                ChoiceChip(selected: true, label: "Пописал", icon: "ic_diaper_wet") {}
                ChoiceChip(selected: false, label: "Покакал", icon: "ic_diaper_dirty") {}
                ChoiceChip(selected: false, label: "И то, и то", icon: "ic_diaper_mixed") {}
            }
            HStack(spacing: 8) {
                ChoiceChip(selected: false, label: "Сейчас") {}
                ChoiceChip(selected: true, label: "−5 мин") {}
                ChoiceChip(selected: true, label: "Плач", icon: "ic_check", multiple: true) {}
            }
            HStack(spacing: 8) {
                ChoiceChip(selected: false, label: "Недоступен", enabled: false) {}
                ChoiceChip(selected: true, label: "Выбран", enabled: false) {}
            }
        }
    }

    @Test(arguments: [false, true]) func hintChip(dark: Bool) {
        assertComponentSnapshot(name: dark ? "dark" : "light", style: dark ? .dark : .light) {
            HStack(spacing: 8) {
                HintChip(label: "Витамин D") {}
                HintChip(label: "Нурофен") {}
            }
        }
    }

    @Test(arguments: [false, true]) func itemRow(dark: Bool) {
        assertComponentSnapshot(name: dark ? "dark" : "light", style: dark ? .dark : .light) {
            AppCard(onClick: {}, onClickLabel: "изменить запись") {
                ItemRow(
                    icon: "ic_medicine",
                    title: "Лекарство · Витамин D",
                    subtitle: "09:15",
                    note: "3 капли после еды"
                ) {
                    Button(action: {}) {
                        AppIcon("ic_delete", contentDescription: "Удалить")
                    }
                }
            }
            AppCard {
                ItemRow(icon: "ic_sleep", title: "Сон · идёт", subtitle: "с 13:40")
            }
        }
    }

    @Test(arguments: [false, true]) func avatar(dark: Bool) {
        assertComponentSnapshot(name: dark ? "dark" : "light", style: dark ? .dark : .light) {
            ForEach([Sex?.none, .boy, .girl], id: \.self) { sex in
                HStack(spacing: 12) {
                    Avatar(sex: sex, size: 32)
                    Avatar(sex: sex)
                    Avatar(sex: sex, size: 56)
                }
            }
        }
    }

    @Test(arguments: [false, true]) func statTile(dark: Bool) {
        assertComponentSnapshot(name: dark ? "dark" : "light", style: dark ? .dark : .light) {
            HStack(spacing: 12) {
                StatTile(
                    icon: "ic_sleep",
                    title: "Сон",
                    value: "12 ч 32 мин",
                    unit: "в сутки",
                    note: "−1 ч 5 мин к прошлым 30 дням",
                    onClick: {}
                ) {
                    StatChartSample()
                }
                StatTile(
                    icon: "ic_feeding",
                    title: "Кормление",
                    value: "6",
                    unit: "в сутки",
                    note: "+1 к прошлым 30 дням",
                    onClick: {}
                ) {
                    StatChartSample()
                }
            }
        }
    }

    @Test(arguments: [false, true]) func timerTile(dark: Bool) {
        let start: Int64 = 1_786_000_000_000
        assertComponentSnapshot(name: dark ? "dark" : "light", style: dark ? .dark : .light) {
            HStack(spacing: 8) {
                TimerTile(
                    icon: "ic_sleep",
                    label: "Сон",
                    runningSince: nil,
                    now: start,
                    startLabel: "начать сон",
                    stopLabel: "проснулся",
                    idleCaption: "не спит 1 ч 20 мин",
                    onStart: {},
                    onStop: {}
                )
                TimerTile(
                    icon: "ic_feeding",
                    label: "Кормление",
                    runningSince: start,
                    now: start + 754_000,
                    startLabel: "начать кормление",
                    stopLabel: "закончить кормление",
                    runningLabel: "левая грудь",
                    corner: AnyView(TimerCornerButton(icon: "ic_swap", description: "Сменить грудь", onClick: {})),
                    onStart: {},
                    onStop: {}
                )
            }
        }
    }

    @Test(arguments: [false, true]) func wideTimerTile(dark: Bool) {
        let start: Int64 = 1_786_000_000_000
        assertComponentSnapshot(name: dark ? "dark" : "light", style: dark ? .dark : .light) {
            WideTimerTile(
                icon: "ic_crying",
                label: "Плач",
                runningSince: nil,
                now: start,
                startLabel: "начать плач",
                stopLabel: "закончить плач",
                idleCaption: "не плачет 20 мин",
                onStart: {},
                onStop: {}
            )
            WideTimerTile(
                icon: "ic_crying",
                label: "Плач",
                runningSince: start,
                now: start + 754_000,
                startLabel: "начать плач",
                stopLabel: "закончить плач",
                onStart: {},
                onStop: {}
            )
        }
    }

    @Test(arguments: [false, true]) func appSheet(dark: Bool) {
        assertComponentSnapshot(name: dark ? "dark" : "light", style: dark ? .dark : .light) {
            AppSheetContent(
                isPresented: .constant(true),
                title: "Новая заметка",
                icon: "ic_note",
                confirm: SheetAction(label: "Сохранить", onClick: {}),
                scrollable: true,
                edgeToEdge: false,
                secondary: nil
            ) { _ in
                Text("Текст заметки").appTextStyle(BabyLogTypeScale.bodyLarge)
                HStack(spacing: 8) {
                    HintChip(label: "Первый зуб") {}
                    HintChip(label: "Прогулка") {}
                }
            }
            .frame(maxHeight: 400)
        }
    }

    @Test(arguments: [false, true]) func sheetMenuItem(dark: Bool) {
        assertComponentSnapshot(name: dark ? "dark" : "light", style: dark ? .dark : .light) {
            SheetMenuItem(icon: "ic_add_child", label: "Добавить ребёнка", onClick: {})
            SheetMenuItem(icon: "ic_help", label: "Справка", badge: true, onClick: {})
            SheetMenuItem(icon: "ic_export", label: "Экспорт в файл", onClick: {})
        }
    }
}

private struct StatChartSample: View {
    @Environment(\.babyLogTheme) private var theme

    var body: some View {
        HStack(alignment: .bottom, spacing: theme.spacing.xs) {
            ForEach([12, 24, 18, 32, 26], id: \.self) { height in
                RoundedRectangle(cornerRadius: theme.shapes.extraSmall)
                    .fill(theme.chartColors.sleep)
                    .frame(width: theme.spacing.s, height: CGFloat(height))
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}
