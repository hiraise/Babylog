import Foundation
import Testing
@testable import BabyLog

struct ThemeModeTest {
    @Test func chosenThemeOverridesSystemOne() {
        #expect(ThemeMode.system.isDark(systemDark: true))
        #expect(!ThemeMode.system.isDark(systemDark: false))
        #expect(!ThemeMode.light.isDark(systemDark: true))
        #expect(ThemeMode.dark.isDark(systemDark: false))
    }

    @Test func choiceSurvivesRestart() {
        withTestUserDefaults { defaults in
            let choice = ThemeChoice(mode: .dark, darkPalette: .sage)
            PrefsThemeModeStore(defaults: defaults).select(choice)
            #expect(PrefsThemeModeStore(defaults: defaults).choice.value == choice)
        }
    }

    @Test func unknownStoredValuesFallBackToDefaults() {
        withTestUserDefaults { defaults in
            defaults.set("UNKNOWN", forKey: "theme_mode")
            defaults.set("UNKNOWN", forKey: "dark_palette")
            #expect(PrefsThemeModeStore(defaults: defaults).choice.value == ThemeChoice())
        }
    }
}
