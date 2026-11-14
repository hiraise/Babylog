import Foundation
import Testing
@testable import BabyLog

struct DevelopmentSeenStoreTest {
    @Test func seenWeekIsKeptPerChildAndSurvivesRestart() {
        withTestUserDefaults { defaults in
            let store = PrefsDevelopmentSeenStore(defaults: defaults)
            #expect(store.seenWeeks.value.isEmpty)

            store.markSeen(childId: 1, week: 21)
            store.markSeen(childId: 2, week: 3)
            store.markSeen(childId: 1, week: 22)

            #expect(store.seenWeeks.value == [1: 22, 2: 3])
            #expect(PrefsDevelopmentSeenStore(defaults: defaults).seenWeeks.value == [1: 22, 2: 3])
        }
    }

    @Test func otherSettingsInSameFileAreIgnored() {
        withTestUserDefaults { defaults in
            defaults.set(5, forKey: "selected_child_id")
            #expect(PrefsDevelopmentSeenStore(defaults: defaults).seenWeeks.value.isEmpty)
        }
    }

    @Test func memoryStoreDoesNotTouchRealDiary() {
        withTestUserDefaults { defaults in
            let memory = MemoryDevelopmentSeenStore()
            memory.markSeen(childId: 1, week: 4)
            #expect(PrefsDevelopmentSeenStore(defaults: defaults).seenWeeks.value.isEmpty)
            #expect(memory.seenWeeks.value == [1: 4])
        }
    }
}
