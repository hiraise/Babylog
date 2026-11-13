import Foundation
import Testing
@testable import BabyLog

/** Главный экран на эталонных скриншотах в светлой и тёмной теме (ADR-0051). */
@MainActor
@Suite(.serialized)
struct DiaryScreenshotTest {
    @Test(arguments: [false, true]) func dayWithRecords(dark: Bool) async throws {
        let model = try await makeModel()
        await assertShell(model: model, dark: dark, section: .today, name: dark ? "diary_day_dark" : "diary_day_light")
    }

    @Test func feedingInProgress() async throws {
        let model = try await makeModel(feedingNow: true)
        await assertShell(model: model, dark: false, section: .today, name: "diary_feeding")
    }

    @Test func cryingInProgress() async throws {
        let model = try await makeModel(cryingNow: true)
        await assertShell(model: model, dark: false, section: .today, name: "diary_crying")
    }

    @Test func firstLaunch() async throws {
        let repository = FakeDiaryRepository()
        let model = DiaryViewModel(
            repository: repository,
            selection: MemorySelectedChildStore(),
            documents: FakeDocumentStore(),
            clock: TestClock(date: Date(timeIntervalSince1970: Double(at("2026-10-15T10:00:00Z")) / 1000), timeZone: TimeZone(secondsFromGMT: 0)!)
        )
        _ = await waitUntil { model.state.child != nil }
        await assertShell(model: model, dark: false, section: .today, name: "diary_first_launch")
    }

    @Test func diaryDay() async throws {
        let model = try await makeModel()
        await assertShell(model: model, dark: false, section: .diary, name: "diary_day")
    }

    /** Двое детей; у Маши за сегодня записи всех типов и идёт сон либо кормление или плач. */
    private func makeModel(feedingNow: Bool = false, cryingNow: Bool = false) async throws -> DiaryViewModel {
        let repository = FakeDiaryRepository(children: [
            Child(id: 1, name: "Маша", birthDate: at("2026-05-20T00:00:00Z"), sex: .girl),
            Child(id: 2, name: "Петя", birthDate: nil, sex: .boy),
        ])
        _ = try await repository.addEvent(Height(id: 9, childId: 1, start: at("2026-10-01T09:00:00Z"), millimeters: 625))
        _ = try await repository.addEvent(Weight(id: 1, childId: 1, start: at("2026-10-15T06:10:00Z"), grams: 6_350))
        _ = try await repository.addEvent(Feeding(
            id: 2,
            childId: 1,
            start: at("2026-10-15T06:30:00Z"),
            end: at("2026-10-15T06:45:00Z"),
            breast: .left
        ))
        _ = try await repository.addEvent(Diaper(id: 3, childId: 1, start: at("2026-10-15T07:05:00Z"), kind: .mixed))
        _ = try await repository.addEvent(Medicine(id: 4, childId: 1, start: at("2026-10-15T07:20:00Z"), name: "Витамин D", note: "1 капля"))
        _ = try await repository.addEvent(Sleep(
            id: 5,
            childId: 1,
            start: at("2026-10-15T07:40:00Z"),
            end: at("2026-10-15T08:55:00Z")
        ))
        _ = try await repository.addEvent(Feeding(
            id: 6,
            childId: 1,
            start: at("2026-10-15T09:00:00Z"),
            end: at("2026-10-15T09:35:00Z"),
            breast: .right,
            note: "долго"
        ))
        if !feedingNow && !cryingNow {
            _ = try await repository.addEvent(Sleep(id: 7, childId: 1, start: at("2026-10-15T09:20:00Z")))
        }
        if feedingNow {
            _ = try await repository.addEvent(Feeding(id: 8, childId: 1, start: at("2026-10-15T09:48:00Z"), breast: .left))
        }
        if cryingNow {
            _ = try await repository.addEvent(Crying(id: 10, childId: 1, start: at("2026-10-15T09:52:00Z")))
        }
        let clock = TestClock(date: Date(timeIntervalSince1970: Double(at("2026-10-15T10:00:00Z")) / 1000), timeZone: TimeZone(secondsFromGMT: 0)!)
        let model = DiaryViewModel(
            repository: repository,
            selection: MemorySelectedChildStore(),
            documents: FakeDocumentStore(),
            clock: clock
        )
        _ = await waitUntil { model.state.events.count == repository.events.count }
        return model
    }

    private func assertShell(model: DiaryViewModel, dark: Bool, section: MainSection, name: String) async {
        let clock = TestClock(date: Date(timeIntervalSince1970: Double(at("2026-10-15T10:00:00Z")) / 1000), timeZone: TimeZone(secondsFromGMT: 0)!)
        assertComponentSnapshot(name: name, style: dark ? .dark : .light, clock: clock) {
            DiaryScreen(
                model: model,
                theme: ThemeChoice(),
                onThemeChange: { _ in },
                initialSection: section
            )
        }
    }

    private func at(_ iso: String) -> Int64 {
        Int64(ISO8601DateFormatter().date(from: iso)!.timeIntervalSince1970 * 1000)
    }
}
