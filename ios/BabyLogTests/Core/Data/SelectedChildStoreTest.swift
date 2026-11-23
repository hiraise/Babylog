import Foundation
import Testing
@testable import BabyLog

struct SelectedChildStoreTest {
    @Test func selectionSurvivesRestartAndMemoryStoreDoesNotTouchIt() {
        withTestUserDefaults { defaults in
            let store = PrefsSelectedChildStore(defaults: defaults)
            #expect(store.selectedChildId.value == nil)

            store.select(7)
            let memory = MemorySelectedChildStore(initial: 2)
            memory.select(3)

            #expect(store.selectedChildId.value == 7)
            #expect(PrefsSelectedChildStore(defaults: defaults).selectedChildId.value == 7)
            #expect(memory.selectedChildId.value == 3)
        }
    }

    @Test func selectionHasCurrentValueAndEmitsChanges() async {
        let store = MemorySelectedChildStore()
        var values = store.selectedChildId.stream().makeAsyncIterator()
        let initial = await values.next()
        #expect(initial != nil)
        #expect(initial! == nil)

        store.select(5)
        let selected = await values.next()
        #expect(selected == 5)
    }
}
