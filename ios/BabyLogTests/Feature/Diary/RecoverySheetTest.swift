import Foundation
import SwiftUI
import Testing
@testable import BabyLog

/** Лист после восстановления: называет время копии и предлагает импорт, если копии не было. */
@MainActor
@Suite(.serialized)
struct RecoverySheetTest {
    private let utc = TimeZone(secondsFromGMT: 0)!

    @Test func restoredDiaryNamesBackupTime() {
        let recovery = DatabaseRecovery(at: 1_792_404_000_000, backupTime: 1_792_359_600_000)
        #expect(recoveryTitle(recovery) == "Дневник восстановлен")
        #expect(recoveryText(recovery, timeZone: utc).contains("из копии, которую приложение сделало 18 октября 2026 в 21:40"))
    }

    @Test func withoutBackupDiaryIsNew() {
        let recovery = DatabaseRecovery(at: 1_792_382_400_000, backupTime: nil)
        #expect(recoveryTitle(recovery) == "Дневник начат заново")
        #expect(recoveryText(recovery, timeZone: utc).contains("Копии ещё не было"))
    }

    @Test(arguments: [false, true]) func sheet(dark: Bool) {
        let recovery = DatabaseRecovery(at: 1_792_404_000_000, backupTime: 1_792_359_600_000)
        assertComponentSnapshot(name: dark ? "dark" : "light", style: dark ? .dark : .light) {
            AppSheetContent(
                isPresented: .constant(true),
                title: recoveryTitle(recovery),
                confirm: SheetAction(label: "Понятно", onClick: {}),
                secondary: SheetAction(label: "Импорт из файла", onClick: {})
            ) { _ in
                DatabaseRecoveredSheetContent(recovery: recovery)
            }
            .frame(maxHeight: 400)
        }
    }
}
