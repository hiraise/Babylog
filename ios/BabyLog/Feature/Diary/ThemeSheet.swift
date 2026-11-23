import SwiftUI

/** Круг образца палитры. */
private let swatchSize: CGFloat = 44

/** Точка акцента в круге образца; у выбранного она больше и с галочкой. */
private let accentDotSize: CGFloat = 16
private let selectedDotSize: CGFloat = 24

@MainActor @Observable final class ThemeSheetModel {
    private(set) var choice: ThemeChoice
    private let onSelect: (ThemeChoice) -> Void

    init(choice: ThemeChoice, onSelect: @escaping (ThemeChoice) -> Void) {
        self.choice = choice
        self.onSelect = onSelect
    }

    func select(mode: ThemeMode) {
        var choice = choice
        choice.mode = mode
        select(choice)
    }

    func select(palette: DarkPalette) {
        var choice = choice
        choice.darkPalette = palette
        select(choice)
    }

    func showsPaletteHint(systemDark: Bool) -> Bool {
        !choice.mode.isDark(systemDark: systemDark)
    }

    func update(_ choice: ThemeChoice) {
        self.choice = choice
    }

    private func select(_ choice: ThemeChoice) {
        self.choice = choice
        onSelect(choice)
    }
}

/**
 Выбор темы в нижнем листе: три строки режима в одну колонку (ADR-0017), под ними — палитры
 тёмной темы (ADR-0032). Выбор применяется сразу, лист остаётся открытым.
 */
struct ThemeSheet: View {
    @Binding var isPresented: Bool
    let theme: ThemeChoice
    let onSelect: (ThemeChoice) -> Void
    let onDismiss: () -> Void

    var body: some View {
        AppSheet(
            isPresented: $isPresented,
            onDismiss: onDismiss,
            title: "Тема",
            icon: "ic_theme",
            edgeToEdge: true
        ) { _ in
            ThemeOptions(theme: theme, onSelect: onSelect)
        }
    }
}

/**
 Содержимое листа «Тема» без самого листа — его снимают скриншот-тесты. Режим — строки с
 радиокнопкой, нажимается вся строка. Палитры — ряд образцов с подписями: палитр пять, строками
 лист вышел бы вдвое длиннее. Пока тема на экране светлая, под заголовком палитр — что они видны
 только в тёмной: иначе выбор ничего не меняет на глазах.
 */
struct ThemeOptions: View {
    @Environment(\.babyLogTheme) private var themeValues
    @Environment(\.colorScheme) private var systemColorScheme
    @State private var model: ThemeSheetModel

    let theme: ThemeChoice

    init(theme: ThemeChoice, onSelect: @escaping (ThemeChoice) -> Void) {
        self.theme = theme
        _model = State(initialValue: ThemeSheetModel(choice: theme, onSelect: onSelect))
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            VStack(spacing: 0) {
                ForEach(ThemeMode.allCases, id: \.self) { option in
                    ThemeRow(
                        icon: themeIcon(option),
                        title: themeLabel(option),
                        subtitle: option == .system ? "Светлая или тёмная — как в телефоне" : nil,
                        selected: option == model.choice.mode
                    ) {
                        model.select(mode: option)
                    }
                }
            }
            VStack(alignment: .leading, spacing: 0) {
                Text("Цвета тёмной темы")
                    .appTextStyle(themeValues.typography.titleSmall)
                if model.showsPaletteHint(systemDark: systemColorScheme == .dark) {
                    Text("Видны, когда тема тёмная")
                        .appTextStyle(themeValues.typography.bodySmall)
                        .foregroundStyle(themeValues.colors.onSurfaceVariant)
                }
            }
            .padding(.leading, themeValues.spacing.l)
            .padding(.trailing, themeValues.spacing.l)
            .padding(.top, themeValues.spacing.l)
            .padding(.bottom, themeValues.spacing.s)
            HStack(spacing: 0) {
                ForEach(DarkPalette.allCases, id: \.self) { palette in
                    PaletteSwatch(
                        palette: palette,
                        selected: palette == model.choice.darkPalette
                    ) {
                        model.select(palette: palette)
                    }
                    .frame(maxWidth: .infinity)
                }
            }
            .padding(.horizontal, themeValues.spacing.s)
        }
        .onChange(of: theme) { _, choice in
            model.update(choice)
        }
    }
}

/** Строка листа во всю ширину: иконка, подпись с пояснением и радиокнопка справа. */
private struct ThemeRow: View {
    @Environment(\.babyLogTheme) private var theme

    let icon: String
    let title: String
    let subtitle: String?
    let selected: Bool
    let onClick: () -> Void

    var body: some View {
        Button(action: onClick) {
            HStack(spacing: theme.spacing.m) {
                AppIcon(icon, tint: theme.colors.primary)
                VStack(alignment: .leading, spacing: 0) {
                    Text(title).appTextStyle(theme.typography.bodyLarge)
                    if let subtitle {
                        Text(subtitle)
                            .appTextStyle(theme.typography.bodySmall)
                            .foregroundStyle(theme.colors.onSurfaceVariant)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                Image(systemName: selected ? "largecircle.fill.circle" : "circle")
                    .resizable()
                    .aspectRatio(contentMode: .fit)
                    .frame(width: BabyLogSizes.icon, height: BabyLogSizes.icon)
                    .foregroundStyle(theme.colors.primary)
                    .frame(width: BabyLogSizes.touchTarget, height: BabyLogSizes.touchTarget)
                    .accessibilityHidden(true)
            }
            .padding(.horizontal, theme.spacing.l)
            .padding(.vertical, theme.spacing.s)
            .frame(maxWidth: .infinity, minHeight: BabyLogSizes.menuMinHeight)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? [.isSelected] : [])
    }
}

/**
 Образец палитры: круг цвета её фона с точкой акцента, под ним название. Выбранный обведён
 акцентом текущей темы, а точка становится галочкой — выбор виден и без различения цветов.
 Цвета образца — из палитры, которую он показывает, а не из текущей темы.
 */
private struct PaletteSwatch: View {
    @Environment(\.babyLogTheme) private var theme

    let palette: DarkPalette
    let selected: Bool
    let onClick: () -> Void

    var body: some View {
        let shown = darkColors(palette)
        Button(action: onClick) {
            VStack(spacing: theme.spacing.xs) {
                ZStack {
                    Circle().fill(shown.background)
                    if selected {
                        AppIcon("ic_check", size: selectedDotSize, tint: shown.onPrimary)
                            .background(shown.primary, in: Circle())
                    } else {
                        Circle()
                            .fill(shown.primary)
                            .frame(width: accentDotSize, height: accentDotSize)
                    }
                }
                .frame(width: swatchSize, height: swatchSize)
                .overlay(Circle().stroke(theme.colors.outlineVariant, lineWidth: 1))
                .padding(theme.spacing.xs)
                .overlay(Circle().stroke(selected ? theme.colors.primary : .clear, lineWidth: 2))
                Text(paletteLabel(palette))
                    .appTextStyle(theme.typography.bodySmall)
                    .foregroundStyle(selected ? theme.colors.onSurface : theme.colors.onSurfaceVariant)
                    .lineLimit(1)
            }
            .padding(.vertical, theme.spacing.s)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? [.isSelected] : [])
    }
}

private func themeIcon(_ mode: ThemeMode) -> String {
    switch mode {
    case .system: "ic_theme"
    case .light: "ic_theme_light"
    case .dark: "ic_theme_dark"
    }
}

private func themeLabel(_ mode: ThemeMode) -> String {
    switch mode {
    case .system: "Как в системе"
    case .light: "Светлая"
    case .dark: "Тёмная"
    }
}

private func paletteLabel(_ palette: DarkPalette) -> String {
    switch palette {
    case .dusk: "Сумерки"
    case .nightlight: "Ночник"
    case .sage: "Шалфей"
    case .powder: "Пудра"
    case .classic: "Классика"
    }
}
