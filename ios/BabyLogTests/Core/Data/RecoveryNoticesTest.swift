import Foundation
import Testing
@testable import BabyLog

struct RecoveryNoticesTest {
    @Test func noticeWaitsForDismissalAcrossRestarts() {
        withTestUserDefaults { defaults in
            let notices = PrefsRecoveryNotices(defaults: defaults)
            #expect(notices.pending.value == nil)

            let recovery = DatabaseRecovery(at: 1_786_000_000_000, backupTime: 1_785_000_000_000)
            notices.report(recovery)
            #expect(PrefsRecoveryNotices(defaults: defaults).pending.value == recovery)

            notices.dismiss()
            #expect(notices.pending.value == nil)
            #expect(PrefsRecoveryNotices(defaults: defaults).pending.value == nil)
        }
    }

    @Test func recoveryWithoutBackupIsAlsoStored() {
        withTestUserDefaults { defaults in
            let recovery = DatabaseRecovery(at: 1_786_000_000_000, backupTime: nil)
            PrefsRecoveryNotices(defaults: defaults).report(recovery)
            #expect(PrefsRecoveryNotices(defaults: defaults).pending.value == recovery)
        }
    }
}
