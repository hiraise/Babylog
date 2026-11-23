import SwiftUI
import Testing
@testable import BabyLog

@MainActor
struct AppSheetTests {
    @Test func closerDismissesBeforeRunningAction() async {
        var presented = true
        var markedAsAction = false
        var actionRan = false
        let binding = Binding<Bool>(get: { presented }, set: { presented = $0 })
        let closer = SheetCloser(isPresented: binding, beforeDismiss: { markedAsAction = true })

        closer.close { actionRan = true }
        #expect(markedAsAction)
        #expect(!presented)
        #expect(!actionRan)
        try? await Task.sleep(for: .milliseconds(400))
        #expect(actionRan)
    }
}
