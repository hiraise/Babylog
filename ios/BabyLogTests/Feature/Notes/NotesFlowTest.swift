import Foundation
import Testing
@testable import BabyLog

/**
 Вид «Заметки» (ADR-0031): заметка с меткой появляется в ленте, метка открывает сводку,
 «Назад» снимает фильтр, а плач за неделю ведёт в «Аналитику». Нажатия проверяются моделями.
 */
@MainActor
@Suite(.serialized)
struct NotesFlowTest {
    private let zone = TimeZone(secondsFromGMT: 0)!
    private let now = notesFlowAt("2026-10-15T10:00:00Z")
    private var clock: TestClock { TestClock(date: Date(timeIntervalSince1970: Double(now) / 1000), timeZone: zone) }

    private func repository() async throws -> FakeDiaryRepository {
        let repository = FakeDiaryRepository(children: [Child(id: 1, name: "Маша", birthDate: nil, sex: .girl)])
        _ = try await repository.addEvent(Feeding(
            id: 100,
            childId: 1,
            start: notesFlowAt("2026-10-14T08:00:00Z"),
            end: notesFlowAt("2026-10-14T08:20:00Z"),
            breast: .left
        ))
        _ = try await repository.addEvent(Crying(
            id: 101,
            childId: 1,
            start: notesFlowAt("2026-10-14T18:30:00Z"),
            end: notesFlowAt("2026-10-14T18:50:00Z"),
            reasons: ["колики"]
        ))
        _ = try await repository.addEvent(Note(
            id: 102,
            childId: 1,
            start: notesFlowAt("2026-10-14T11:20:00Z"),
            tags: ["новое"],
            note: "Перевернулась!"
        ))
        return repository
    }

    @Test func newNoteWithTagAppearsInFeedAndTagShowsSummary() async throws {
        let repository = try await repository()
        let model = DiaryViewModel(
            repository: repository,
            selection: MemorySelectedChildStore(),
            documents: FakeDocumentStore(),
            clock: clock
        )
        #expect(await waitUntil { model.state.events.count == 3 })
        let sheet = NoteSheetModel(
            initial: nil,
            tagOptions: noteTagChoices(events: model.state.events),
            initialPhotos: []
        )
        sheet.text = "Тёрла ушко"
        sheet.tags.pick("зубки")

        await model.saveEvent(
            Note(
                childId: 1,
                start: sheet.time(clock: clock),
                tags: sheet.tags.selected,
                note: sheet.trimmedText
            ),
            photos: sheet.photoEdit
        )
        #expect(await waitUntil { model.state.events.count == 4 })
        let note = try #require(model.state.events.compactMap { $0 as? Note }.first { $0.note == "Тёрла ушко" })
        #expect(note.tags == ["зубки"])

        var navigation = DiaryNavigationState(section: .diary, diaryView: .notes)
        navigation.openTag("зубки")
        let tagged = notesFeed(events: model.state.events, filter: navigation.notesFilter, timeZone: zone)
        #expect(tagged.flatMap { $0.records.map(\.id) } == [note.id])
        #expect(tagSummary(events: model.state.events, tag: "зубки", now: now, timeZone: zone).notes == 1)
        #expect(!tagged.contains { $0.records.contains { ($0 as? Note)?.note == "Перевернулась!" } })

        navigation.back()
        #expect(navigation.notesFilter == .all)
        #expect(notesFeed(events: model.state.events, filter: navigation.notesFilter, timeZone: zone)
            .contains { $0.records.contains { ($0 as? Note)?.note == "Перевернулась!" } })
    }

    @Test func cryingOfWeekLeadsToAnalytics() async throws {
        let repository = try await repository()
        let week = cryingWeek(events: repository.events, now: now, timeZone: zone)
        #expect(week?.perDay == "2 мин")
        #expect(week?.note == "0,1 раза в сутки")

        var navigation = DiaryNavigationState(section: .diary, diaryView: .notes)
        navigation.openCryingAnalytics()
        #expect(navigation.section == .analytics)
    }

    @Test func deletingFeedRecordRemovesItFromRepository() async throws {
        let repository = try await repository()
        let model = DiaryViewModel(
            repository: repository,
            selection: MemorySelectedChildStore(),
            documents: FakeDocumentStore(),
            clock: clock
        )
        #expect(await waitUntil { model.state.events.count == 3 })
        let note = try #require(model.state.events.compactMap { $0 as? Note }.first)

        await model.deleteEvent(note)
        #expect(await waitUntil { model.state.events.count == 2 })
        #expect(!repository.events.contains { $0.id == note.id })
    }

    @Test func filterSurvivesStateRestorationWithoutConfusingTags() {
        let filters: [NotesFilter] = [.all, .cryingOnly, .notesOnly, .illnesses, .tag("плач"), .tag("#")]
        for filter in filters {
            #expect(NotesFilter(savedKey: filter.savedKey) == filter)
        }
    }
}

private func notesFlowAt(_ iso: String) -> Int64 {
    Int64(ISO8601DateFormatter().date(from: iso)!.timeIntervalSince1970 * 1000)
}
