import SwiftUI
import Testing
@testable import BabyLog

@MainActor
@Suite(.serialized)
struct CryingSheetScreenshotTest {
    @Test(arguments: [false, true]) func cryingDone(dark: Bool) {
        let crying = Crying(id: 1, childId: 1, start: at("2026-10-15T17:32:00Z"), end: at("2026-10-15T17:56:00Z"))
        assertComponentSnapshot(
            name: dark ? "dark" : "light",
            style: dark ? .dark : .light,
            height: 1_200
        ) {
            CryingDoneFields(
                crying: crying,
                start: crying.start - 5 * 60_000,
                earlier: 5,
                onEarlier: { _ in },
                context: CryingContext(
                    awakeMillis: 135 * 60_000,
                    usualAwakeMillis: 90 * 60_000,
                    sinceFeedingMillis: 70 * 60_000,
                    feeding: "левая, 20 мин",
                    feedingByBottle: false,
                    sinceDiaperMillis: 40 * 60_000,
                    diaper: .wet
                ),
                choices: cryingChoices(events: []),
                sex: .girl,
                reasons: ["колики", "срыгивание"],
                onReasons: { _ in },
                helped: ["укачивание"],
                onHelped: { _ in },
                note: "",
                onNote: { _ in }
            )
        }
    }

    private func at(_ iso: String) -> Int64 {
        Int64(ISO8601DateFormatter().date(from: iso)!.timeIntervalSince1970 * 1000)
    }
}
