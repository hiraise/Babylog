import Foundation

/**
 Темы справки по порядку прохождения (ADR-0043): сначала плитки «Сегодня» (`HelpTopicsToday.swift`),
 потом разделы и меню (`HelpTopicsSections.swift`). Справка — главное описание того, что делает
 пользователь: новая функция или изменённый жест приходит с шагом в своей теме и с `since` —
 версией, в которой шаг появился. У каждой темы есть UI-тест, который проходит её гайд до конца.
 */
let HELP_TOPICS: [HelpTopic] = [
    SLEEP_FEEDING,
    RECORDS,
    CRYING,
    NOTES,
    HEALTH,
    DIARY,
    ANALYTICS,
    DEVELOPMENT,
    CHILDREN,
    SETTINGS,
]

/** Сколько записей типа `T`: шаг «добавьте запись» засчитывается, когда их стало больше. */
func countOf<T: BabyEvent>(_ type: T.Type, in events: [any BabyEvent]) -> Int {
    events.count { $0 is T }
}

/** Записей типа `T` стало больше, чем в начале шага. */
func added<T: BabyEvent>(_ type: T.Type) -> StepDone {
    .records { now, atStart in
        countOf(T.self, in: now) > countOf(T.self, in: atStart)
    }
}
