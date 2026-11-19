import SwiftUI

@main
struct BabyLogApp: App {
    private let container: AppContainer
    private let initialHelpRoute: HelpRoute?

    init() {
        if let topic = Self.guideTopic {
            let database = try! AppDatabase.inMemory()
            container = AppContainer(repository: RoomDiaryRepository(database: database))
            initialHelpRoute = .guide(topic)
        } else {
            container = AppContainer()
            initialHelpRoute = nil
        }
    }

    var body: some Scene {
        WindowGroup {
            RootView(initialHelpRoute: initialHelpRoute)
                .environment(container)
        }
    }

    /** XCUITest открывает гайд сразу и не трогает настоящую базу и настройки дневника. */
    private static var guideTopic: String? {
        let arguments = ProcessInfo.processInfo.arguments
        guard let index = arguments.firstIndex(of: "-guide"), arguments.indices.contains(index + 1) else { return nil }
        return arguments[index + 1]
    }
}
