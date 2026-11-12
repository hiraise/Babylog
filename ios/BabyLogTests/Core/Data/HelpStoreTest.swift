import Foundation
import Testing
@testable import BabyLog

struct HelpStoreTest {
    @Test func baselineAndProgressSurviveRestart() {
        withTestUserDefaults { defaults in
            let store = PrefsHelpStore(defaults: defaults, version: "1.11")
            #expect(store.progress.value == HelpProgress(baseline: "1.11"))

            store.markOpened(topicId: "diary", version: "1.11")
            store.markCompleted(topicId: "diary")
            store.dismissPointer()

            let restored = PrefsHelpStore(defaults: defaults, version: "1.12").progress.value
            #expect(restored.baseline == "1.11")
            #expect(restored.opened == ["diary": "1.11"])
            #expect(restored.completed == ["diary"])
            #expect(restored.pointerDone)
        }
    }

    @Test func otherSettingsAreIgnored() {
        withTestUserDefaults { defaults in
            defaults.set(5, forKey: "selected_child_id")
            defaults.set(7, forKey: "help_opened_count")
            let store = PrefsHelpStore(defaults: defaults, version: "1.11")
            #expect(store.progress.value.opened.isEmpty)
        }
    }
}
