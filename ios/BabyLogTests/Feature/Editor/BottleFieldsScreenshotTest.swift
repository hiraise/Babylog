import SwiftUI
import Testing
@testable import BabyLog

@MainActor
@Suite(.serialized)
struct BottleFieldsScreenshotTest {
    @Test(arguments: [false, true]) func bottleIconAndFields(dark: Bool) {
        assertComponentSnapshot(
            name: dark ? "dark" : "light",
            style: dark ? .dark : .light,
            width: 360,
            height: 1_100
        ) {
            BottleFieldsSnapshotContent()
        }
    }

    @Test func volumeAcceptsOnlyRangeFromFiveToFiveHundred() {
        #expect(bottleVolume("") == nil)
        #expect(bottleVolume("120") == 120)
        #expect(bottleVolume("4") == nil)
        #expect(bottleVolume("501") == nil)
    }
}

private struct BottleFieldsSnapshotContent: View {
    @Environment(\.babyLogTheme) private var theme

    var body: some View {
        HStack(spacing: 12) {
            AppIcon("ic_bottle", tint: theme.colors.primary)
            AppIcon("ic_bottle", size: 48, tint: theme.colors.primary)
        }
        BottleFields(volume: .constant(""), milk: .constant(nil))
        BottleFields(volume: .constant("120"), milk: .constant(.formula))
        BottleFields(volume: .constant("600"), milk: .constant(nil))
    }
}
