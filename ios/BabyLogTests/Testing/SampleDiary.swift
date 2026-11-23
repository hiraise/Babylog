import Foundation
@testable import BabyLog

func analyticsAt(_ iso: String) -> Int64 {
    Int64(ISO8601DateFormatter().date(from: iso)!.timeIntervalSince1970 * 1000)
}

let analyticsNow = analyticsAt("2026-10-18T08:20:00Z")
let analyticsZone = TimeZone(secondsFromGMT: 0)!
let analyticsChild = Child(id: 1, name: "Маша", birthDate: analyticsAt("2026-05-23T00:00:00Z"), sex: .girl)

func analyticsSampleEvents() -> [any BabyEvent] {
    sampleMonths(childId: analyticsChild.id, today: toLocalDate(analyticsNow, timeZone: analyticsZone), now: analyticsNow, timeZone: analyticsZone)
}

@MainActor
func makeAnalyticsViewModel() async throws -> DiaryViewModel {
    let repository = FakeDiaryRepository(children: [analyticsChild])
    let events = analyticsSampleEvents()
    for event in events { _ = try await repository.addEvent(event) }
    let clock = TestClock(date: Date(timeIntervalSince1970: Double(analyticsNow) / 1000), timeZone: analyticsZone)
    let model = DiaryViewModel(repository: repository, selection: MemorySelectedChildStore(), documents: FakeDocumentStore(), clock: clock)
    _ = await waitUntil { model.state.events.count == events.count }
    return model
}
