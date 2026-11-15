import SnapshotTesting
import SwiftUI
import Testing
import UIKit
@testable import BabyLog

/**
 Лист «Тема» на фоне листа (ADR-0032): в светлой теме — с подсказкой, что палитры видны в тёмной,
 в тёмной — с выбранным «Ночником» в его же цветах.
 */
@MainActor
@Suite(.serialized)
struct ThemeSheetScreenshotTest {
    @Test func light() {
        capture(name: "theme_sheet_light", choice: ThemeChoice(), dark: false)
    }

    @Test func dark() {
        capture(
            name: "theme_sheet_dark",
            choice: ThemeChoice(mode: .dark, darkPalette: .nightlight),
            dark: true
        )
    }

    private func capture(name: String, choice: ThemeChoice, dark: Bool) {
        let root = BabyLogTheme(
            clock: TestClock(date: Date(timeIntervalSince1970: 1_792_058_400), timeZone: TimeZone(secondsFromGMT: 0)!),
            darkTheme: dark,
            darkPalette: choice.darkPalette
        ) {
            ThemeSheetSnapshot(choice: choice)
                .environment(\.locale, Locale(identifier: "ru_RU"))
                .environment(\.timeZone, TimeZone(secondsFromGMT: 0)!)
        }
        let controller = UIHostingController(rootView: root)
        controller.overrideUserInterfaceStyle = dark ? .dark : .light
        controller.view.frame = CGRect(x: 0, y: 0, width: 393, height: 380)
        controller.view.layoutIfNeeded()
        assertSnapshot(of: controller, as: .image, named: name)
    }
}

private struct ThemeSheetSnapshot: View {
    @Environment(\.babyLogTheme) private var theme
    let choice: ThemeChoice

    var body: some View {
        ThemeOptions(theme: choice, onSelect: { _ in })
            .padding(.vertical, theme.spacing.l)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            .background(theme.colors.surfaceContainerHigh)
    }
}
