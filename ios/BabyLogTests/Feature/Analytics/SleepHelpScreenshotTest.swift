import SwiftUI
import Testing
@testable import BabyLog

/** Справка «?» карточки «Сон» на фоне листа (ADR-0036). */
@MainActor
@Suite(.serialized)
struct SleepHelpScreenshotTest {
    @Test func sleepHelp() {
        assertComponentSnapshot(name: "sleep_help", style: .light, width: 393, height: 852) {
            SleepHelpOnSheet()
        }
    }
}

private struct SleepHelpOnSheet: View {
    @Environment(\.babyLogTheme) private var theme

    var body: some View {
        VStack(alignment: .leading, spacing: theme.spacing.m) {
            SleepHelp()
        }
        .padding(theme.spacing.l)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(theme.colors.surfaceContainerHigh)
    }
}
