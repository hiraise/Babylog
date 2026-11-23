import Foundation
import Testing
@testable import BabyLog

/** Лента «Заметок» в светлой и тёмной теме и сводка метки «колики» (ADR-0031). */
@MainActor
@Suite(.serialized)
struct NotesScreenshotTest {
    private let now = notesScreenshotAt("2026-10-18T08:20:00Z")
    private let zone = TimeZone(secondsFromGMT: 0)!

    @Test func feedLight() async throws {
        let model = try await makeModel()
        assertNotes(model: model, dark: false, filter: .all, name: "notes_feed")
    }

    @Test func feedDark() async throws {
        let model = try await makeModel()
        assertNotes(model: model, dark: true, filter: .all, name: "notes_dark")
    }

    @Test func tagSummary() async throws {
        let model = try await makeModel()
        assertNotes(model: model, dark: false, filter: .tag("колики"), name: "notes_tag")
    }

    private func makeModel() async throws -> DiaryViewModel {
        let repository = FakeDiaryRepository(children: [
            Child(id: 1, name: "Маша", birthDate: notesScreenshotAt("2026-05-20T00:00:00Z"), sex: .girl),
        ])
        let events: [any BabyEvent] = [
            Sleep(id: 1, childId: 1, start: notesScreenshotAt("2026-10-17T06:00:00Z"), end: notesScreenshotAt("2026-10-17T08:00:00Z")),
            Sleep(id: 2, childId: 1, start: notesScreenshotAt("2026-10-17T09:00:00Z"), end: notesScreenshotAt("2026-10-17T10:00:00Z")),
            Sleep(id: 3, childId: 1, start: notesScreenshotAt("2026-10-17T11:00:00Z"), end: notesScreenshotAt("2026-10-17T12:00:00Z")),
            Feeding(id: 4, childId: 1, start: notesScreenshotAt("2026-10-17T14:00:00Z"), end: notesScreenshotAt("2026-10-17T14:20:00Z"), breast: .left),
            Crying(
                id: 5,
                childId: 1,
                start: notesScreenshotAt("2026-10-17T15:00:00Z"),
                end: notesScreenshotAt("2026-10-17T15:20:00Z"),
                reasons: ["колики", "усталость"],
                helped: ["укачивание"],
                note: "Долго не могли успокоить"
            ),
            Note(id: 6, childId: 1, start: notesScreenshotAt("2026-10-17T11:20:00Z"), tags: ["новое"], note: "Перевернулась!"),
            Crying(
                id: 7,
                childId: 1,
                start: notesScreenshotAt("2026-10-16T19:00:00Z"),
                end: notesScreenshotAt("2026-10-16T19:20:00Z"),
                reasons: ["колики"],
                helped: ["укачивание", "грудь"]
            ),
            Note(
                id: 8,
                childId: 1,
                start: notesScreenshotAt("2026-10-16T21:50:00Z"),
                tags: ["питание мамы", "колики"],
                note: "Ела капусту"
            ),
            Crying(
                id: 9,
                childId: 1,
                start: notesScreenshotAt("2026-10-15T20:30:00Z"),
                end: notesScreenshotAt("2026-10-15T20:50:00Z"),
                reasons: ["колики"],
                helped: ["тёплое на живот"]
            ),
        ]
        for event in events { _ = try await repository.addEvent(event) }
        let clock = TestClock(date: Date(timeIntervalSince1970: Double(now) / 1000), timeZone: zone)
        let model = DiaryViewModel(
            repository: repository,
            selection: MemorySelectedChildStore(),
            documents: FakeDocumentStore(),
            clock: clock
        )
        _ = await waitUntil { model.state.events.count == events.count }
        return model
    }

    private func assertNotes(model: DiaryViewModel, dark: Bool, filter: NotesFilter, name: String) {
        let clock = TestClock(date: Date(timeIntervalSince1970: Double(now) / 1000), timeZone: zone)
        assertComponentSnapshot(name: name, style: dark ? .dark : .light, clock: clock) {
            DiaryScreen(
                model: model,
                theme: ThemeChoice(),
                onThemeChange: { _ in },
                initialSection: .diary,
                initialDiaryView: .notes,
                initialNotesFilter: filter
            )
        }
    }
}

private func notesScreenshotAt(_ iso: String) -> Int64 {
    Int64(ISO8601DateFormatter().date(from: iso)!.timeIntervalSince1970 * 1000)
}
