import Foundation
import Testing
@testable import BabyLog

/**
 Плитки быстрых записей на ширине телефона 360 dp. Над ними ряд плача с «Заметкой»: «Заметка» точно над
 «Ростом»; идёт плач больше часа — «1:23:45» и «с …» влезают в три четверти ряда; плача не было
 или под плиткой длинная подпись в две строки. Подгузник меняли 25 мин назад; 3 ч 20 мин назад —
 подпись выделена; столько же, но малыш спит — не выделена; не меняли — без подписи. Под весом и
 ростом — дата замера: вчера и две недели назад, сегодня и в прошлом году (в две строки), не мерили.
 Светлая и тёмная тема. Плитки ряда одной высоты при любой подписи.
 */
@MainActor
@Suite(.serialized)
struct QuickActionsScreenshotTest {
    private let now = quickActionsAt("2026-10-15T10:00:00Z")
    private let minute: Int64 = 60_000
    private let day: Int64 = 24 * 60 * 60_000

    @Test(arguments: [false, true]) func cryingRowWithNoteDiaperCaptionAndMeasurementDates(dark: Bool) {
        let clock = TestClock(
            date: Date(timeIntervalSince1970: Double(now) / 1000),
            timeZone: TimeZone(secondsFromGMT: 0)!
        )
        let lastCrying = Crying(childId: 1, start: now - 145 * minute, end: now - 80 * minute)
        assertComponentSnapshot(
            name: dark ? "quick_actions_dark" : "quick_actions",
            style: dark ? .dark : .light,
            width: 393,
            height: 900,
            clock: clock
        ) {
            CryingNoteRow(
                activeCrying: Crying(childId: 1, start: now - 5_025_000),
                lastCrying: nil,
                now: now,
                onStartCrying: {},
                onEndCrying: {},
                onAddNote: {}
            )
            CryingNoteRow(
                activeCrying: nil,
                lastCrying: nil,
                now: now,
                onStartCrying: {},
                onEndCrying: {},
                onAddNote: {}
            )
            CryingNoteRow(
                activeCrying: nil,
                lastCrying: lastCrying,
                now: now,
                onStartCrying: {},
                onEndCrying: {},
                onAddNote: {}
            )
            QuickActions(
                lastDiaper: now - 25 * minute,
                lastWeight: now - day,
                lastHeight: now - 14 * day,
                sleeping: false,
                now: now,
                onAdd: { _ in },
                onHealth: {}
            )
            QuickActions(
                lastDiaper: now - 200 * minute,
                lastWeight: now,
                lastHeight: now - 400 * day,
                sleeping: false,
                now: now,
                illnessDay: 3,
                onAdd: { _ in },
                onHealth: {}
            )
            QuickActions(
                lastDiaper: now - 200 * minute,
                lastWeight: nil,
                lastHeight: nil,
                sleeping: true,
                now: now,
                onAdd: { _ in },
                onHealth: {}
            )
            QuickActions(
                lastDiaper: nil,
                lastWeight: nil,
                lastHeight: nil,
                sleeping: false,
                now: now,
                onAdd: { _ in },
                onHealth: {}
            )
        }
    }
}

private func quickActionsAt(_ iso: String) -> Int64 {
    Int64(ISO8601DateFormatter().date(from: iso)!.timeIntervalSince1970 * 1000)
}
