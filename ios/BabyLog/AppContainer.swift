import Foundation
import Observation

/// Ручная сборка зависимостей: весь граф приложения в одном месте, без DI-фреймворка (ADR-0007).
@Observable final class AppContainer {
    /// Системное время в текущем часовом поясе устройства (пояс может смениться на ходу).
    let clock: any Clock
    let selectedChild: any SelectedChildStore
    let themeMode: any ThemeModeStore
    let help: any HelpStore
    let developmentSeen: any DevelopmentSeenStore
    let recoveryNotices: any RecoveryNotices
    let repository: any DiaryRepository
    let documents: any DocumentStore

    init(
        clock: any Clock = SystemClock(),
        selectedChild: any SelectedChildStore = PrefsSelectedChildStore(),
        themeMode: any ThemeModeStore = PrefsThemeModeStore(),
        help: any HelpStore = PrefsHelpStore(
            version: Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? ""
        ),
        developmentSeen: any DevelopmentSeenStore = PrefsDevelopmentSeenStore(),
        recoveryNotices: any RecoveryNotices = PrefsRecoveryNotices(),
        repository: (any DiaryRepository)? = nil,
        documents: any DocumentStore = URLDocumentStore()
    ) {
        self.clock = clock
        self.selectedChild = selectedChild
        self.themeMode = themeMode
        self.help = help
        self.developmentSeen = developmentSeen
        self.recoveryNotices = recoveryNotices
        if let repository {
            self.repository = repository
        } else {
            let database = try! AppDatabase.build(notices: recoveryNotices, now: { clock.millis() })
            self.repository = RoomDiaryRepository(database: database)
        }
        self.documents = documents
    }
}
