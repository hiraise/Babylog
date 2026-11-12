import Foundation

/** Тема приложения (ADR-0017): как в системе, всегда светлая или всегда тёмная. */
enum ThemeMode: String, CaseIterable, Hashable, Sendable {
    case system = "SYSTEM"
    case light = "LIGHT"
    case dark = "DARK"
}

/**
 Палитра тёмной темы (ADR-0032); светлая тема одна. Цвета палитр — `darkColors` в `Core/UI/Theme/`,
 `classic` — прежняя зелёная тёмная тема по макету.
 */
enum DarkPalette: String, CaseIterable, Hashable, Sendable {
    case dusk = "DUSK"
    case nightlight = "NIGHTLIGHT"
    case sage = "SAGE"
    case powder = "POWDER"
    case classic = "CLASSIC"

    /** Палитра по умолчанию — «Классика» (ADR-0044). */
    static let defaultPalette = DarkPalette.classic
}

/** Выбор темы: режим (ADR-0017) и палитра на случай, когда тема на экране тёмная (ADR-0032). */
struct ThemeChoice: Hashable, Sendable {
    var mode: ThemeMode = .system
    var darkPalette: DarkPalette = .defaultPalette
}

extension ThemeMode {
    /** Тёмная ли тема на экране: выбранная в приложении или, при `system`, системная `systemDark`. */
    func isDark(systemDark: Bool) -> Bool {
        switch self {
        case .system: systemDark
        case .light: false
        case .dark: true
        }
    }
}

/**
 Выбранная тема; переживает перезапуск. Это настройка телефона, а не дневника: в базу и CSV
 она не попадает.
 */
protocol ThemeModeStore: Sendable {
    var choice: StoreState<ThemeChoice> { get }

    func select(_ choice: ThemeChoice)
}

final class PrefsThemeModeStore: ThemeModeStore, @unchecked Sendable {
    let choice: StoreState<ThemeChoice>
    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        let mode = defaults.string(forKey: Self.modeKey).flatMap(ThemeMode.init(rawValue:)) ?? .system
        let palette = defaults.string(forKey: Self.paletteKey).flatMap(DarkPalette.init(rawValue:)) ?? .defaultPalette
        choice = StoreState(ThemeChoice(mode: mode, darkPalette: palette))
    }

    func select(_ choice: ThemeChoice) {
        defaults.set(choice.mode.rawValue, forKey: Self.modeKey)
        defaults.set(choice.darkPalette.rawValue, forKey: Self.paletteKey)
        self.choice.set(choice)
    }

    private static let modeKey = "theme_mode"
    private static let paletteKey = "dark_palette"
}
