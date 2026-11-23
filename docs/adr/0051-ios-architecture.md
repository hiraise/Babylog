# ADR-0051: Архитектура iOS — SwiftUI с Observable-моделями, ручным DI и часами Clock

- **Статус:** Принято
- **Дата:** 2026-11-10
- **Затрагивает:** `ios/BabyLog/`, `ios/BabyLogTests/`, `ios/BabyLog.xcodeproj`

## Контекст

iOS-версия повторяет Android-приложение, но использует SwiftUI вместо Compose и GRDB вместо Room.
Нужны одни правила устройства кода и тестов, чтобы перенос оставался предсказуемым и не зависел от платформенных фреймворков в логике.

## Решение

- Держи код в `ios/BabyLog/{App,Core/{Model,Data,UI},Feature/<Name>}`; тесты в `ios/BabyLogTests/` повторяют структуру кода. На каждый Kotlin-файл — один Swift-файл с тем же именем и KDoc-правилами в doc-комментариях.
- Состояние экрана храни в `@MainActor @Observable final class <Name>Model` — аналоге Android ViewModel. View получает модель и не содержит логики. Combine не используй.
- Асинхронную работу пиши на `async/await`; Room `Flow` заменяй GRDB `ValueObservation`, представленным как `AsyncSequence`.
- Собирай зависимости вручную в `AppContainer`, как в `AppContainer.kt`: создавай контейнер в `BabyLogApp` и передавай через `@Environment`. Тесты создают его с `DatabaseQueue` в памяти; fakes повторяют `testing/Fakes.kt`.
- Внедряй протокол `Clock`, как `core/ui/Clock.kt`. Храни время в epoch millis `Int64`, как на Android; используй `Calendar(identifier: .gregorian)`, локаль `ru_RU`, первым днём недели — понедельник, часовой пояс — `TimeZone.current`.
- Русские строки пиши прямо в Swift, как в Kotlin, без `.xcstrings`; множественные числа и `bySex` переноси функциями из `Format.kt`.
- Всплывающие окна делай через `AppSheet` на SwiftUI `.sheet` и `presentationDetents` со стилем темы ([ADR-0018](0018-bottom-sheets.md)). Нижнюю панель реализуй своим компонентом по Android-эталону, не `TabView` ([ADR-0035](0035-bottom-navigation.md)).
- Логику и модели проверяй Swift Testing, экраны — swift-snapshot-testing в светлой и тёмной теме на одном закреплённом симуляторе `iPhone 17` с iOS из закреплённого Xcode. Фиксируй `ru_RU`, часовой пояс Android-тестов и `Clock`. Нажатия и ввод проверяй через модели; XCUITest используй только для гайдов справки (S9b).

## Последствия

Новые функции сначала получают Android-эталон и его тесты, затем зеркальный Swift-файл и такие же сценарии проверки.

## Проверка

`scripts/dev.sh ios` собирает приложение и запускает тесты на закреплённом симуляторе; `ios-goldens` перезаписывает эталоны.

## Альтернативы

- Combine и сторонний DI-фреймворк — лишний второй подход к состоянию и зависимостям.
- `TabView` для главной навигации — не повторяет компоновку и поведение нижней панели Android.
