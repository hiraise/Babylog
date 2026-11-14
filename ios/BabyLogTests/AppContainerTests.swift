import Foundation
import Testing
@testable import BabyLog

struct AppContainerTests {
    @Test func containerUsesInjectedClock() {
        let date = Date(timeIntervalSince1970: 1_700_000_000)
        let container = AppContainer(
            clock: TestClock(date: date),
            repository: FakeDiaryRepository(),
            documents: FakeDocumentStore()
        )

        #expect(container.clock.now() == date)
    }
}
