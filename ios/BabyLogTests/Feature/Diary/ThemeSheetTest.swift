import Foundation
import Testing
@testable import BabyLog

/** Лист «Тема»: режим и палитра тёмной темы выбираются независимо (ADR-0017, ADR-0032). */
@MainActor
struct ThemeSheetTest {
    @Test func paletteIsChosenWithoutTouchingMode() {
        var selected: ThemeChoice?
        let model = ThemeSheetModel(choice: ThemeChoice(mode: .dark)) { selected = $0 }

        #expect(model.choice.darkPalette == .classic)
        model.select(palette: .nightlight)

        #expect(selected == ThemeChoice(mode: .dark, darkPalette: .nightlight))
        #expect(model.choice.darkPalette == .nightlight)
    }

    @Test func modeIsChosenWithoutTouchingPalette() {
        var selected: ThemeChoice?
        let model = ThemeSheetModel(choice: ThemeChoice(mode: .system, darkPalette: .classic)) { selected = $0 }

        model.select(mode: .dark)

        #expect(selected == ThemeChoice(mode: .dark, darkPalette: .classic))
        #expect(model.choice.darkPalette == .classic)
    }

    @Test func lightThemeExplainsWhenPalettesShow() {
        let model = ThemeSheetModel(choice: ThemeChoice(mode: .light)) { _ in }
        #expect(model.showsPaletteHint(systemDark: false))

        model.select(mode: .dark)

        #expect(!model.showsPaletteHint(systemDark: false))
    }
}
