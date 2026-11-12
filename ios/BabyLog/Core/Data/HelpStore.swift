import Foundation

/**
 Что пользователь видел в справке (ADR-0043). Это настройка телефона, а не дневник: в базу и CSV
 не попадает.

 `baseline` — версия первого запуска со справкой: шаги не новее неё «Новым» не помечаются.
 `opened` — на какой версии открывали тему, по id темы. `completed` — id тем, гайд которых прошли
 до конца. `pointerDone` — карточку «Где справка» на «Сегодня» закрыли или уже открывали справку.
 */
struct HelpProgress: Hashable, Sendable {
    var baseline: String? = nil
    var opened: [String: String] = [:]
    var completed: Set<String> = []
    var pointerDone = false
}

protocol HelpStore: Sendable {
    var progress: StoreState<HelpProgress> { get }

    func markOpened(topicId: String, version: String)
    func markCompleted(topicId: String)
    func dismissPointer()
}

/** `version` — текущая версия приложения: при первом запуске со справкой она становится `baseline`. */
final class PrefsHelpStore: HelpStore, @unchecked Sendable {
    let progress: StoreState<HelpProgress>
    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard, version: String) {
        self.defaults = defaults
        if defaults.object(forKey: Self.baselineKey) == nil {
            defaults.set(version, forKey: Self.baselineKey)
        }
        var opened: [String: String] = [:]
        for (key, value) in defaults.dictionaryRepresentation() where key.hasPrefix(Self.openedKey) {
            if let version = value as? String {
                opened[String(key.dropFirst(Self.openedKey.count))] = version
            }
        }
        progress = StoreState(HelpProgress(
            baseline: defaults.string(forKey: Self.baselineKey),
            opened: opened,
            completed: Set(defaults.stringArray(forKey: Self.completedKey) ?? []),
            pointerDone: defaults.bool(forKey: Self.pointerDoneKey)
        ))
    }

    func markOpened(topicId: String, version: String) {
        defaults.set(version, forKey: Self.openedKey + topicId)
        var updated = progress.value
        updated.opened[topicId] = version
        progress.set(updated)
    }

    func markCompleted(topicId: String) {
        var updated = progress.value
        updated.completed.insert(topicId)
        defaults.set(Array(updated.completed), forKey: Self.completedKey)
        progress.set(updated)
    }

    func dismissPointer() {
        defaults.set(true, forKey: Self.pointerDoneKey)
        var updated = progress.value
        updated.pointerDone = true
        progress.set(updated)
    }

    private static let baselineKey = "help_baseline"
    private static let openedKey = "help_opened_"
    private static let completedKey = "help_completed"
    private static let pointerDoneKey = "help_pointer_done"
}
