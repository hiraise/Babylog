import Foundation
import Testing
@testable import BabyLog

/** Главный экран на эталонных скриншотах в светлой и тёмной теме (ADR-0051). */
@MainActor
@Suite(.serialized)
struct DiaryScreenshotTest {
    @Test(arguments: [false, true]) func shell(dark: Bool) async throws {
        let model = try await makeModel()
        await assertShell(model: model, dark: dark, section: .today, name: dark ? "diary_shell_dark" : "diary_shell_light")
    }

    @Test func diaryDay() async throws {
        let model = try await makeModel()
        await assertShell(model: model, dark: false, section: .diary, name: "diary_day")
    }

    private func makeModel() async throws -> DiaryViewModel {
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
        let clock = TestClock(date: Date(timeIntervalSince1970: Double(at("2026-10-15T10:00:00Z")) / 1000), timeZone: TimeZone(secondsFromGMT: 0)!)
        let model = DiaryViewModel(
            repository: repository,
            selection: MemorySelectedChildStore(),
            documents: FakeDocumentStore(),
            clock: clock
        )
        _ = await waitUntil { model.state.lastWeight != nil && model.state.lastHeight != nil }
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
