# ADR-0022: Нажатия и ввод на экранах проверяются UI-тестами Compose на JVM (`ui-test-junit4`)

- **Статус:** Принято
- **Дата:** 2026-10-17
- **Затрагивает:** `app/build.gradle.kts` (`testImplementation`, `debugImplementation`), `app/src/test/`, `testing/ComposeTesting.kt`
- **Заменяет:** часть [ADR-0013](0013-jvm-android-tests.md) о списке тестовых библиотек

## Контекст

Скриншот-тесты ([ADR-0013](0013-jvm-android-tests.md)) сверяют только вид экрана. Что делают
нажатие и ввод (подставилась ли подсказка, что ушло в «Сохранить»), проверяли вручную на
эмуляторе, которым владелец пользуется одновременно с агентом.

## Решение

- Нажатия, ввод текста и то, что после них появляется и пропадает, проверяй UI-тестами Compose
  на JVM: `createComposeRule()` под `RobolectricTestRunner`, в `app/src/test`.
- Библиотека — `testImplementation("androidx.compose.ui:ui-test-junit4")` (Apache-2.0), версия —
  из того же `compose-bom`, что у приложения. Остальные тестовые библиотеки — по ADR-0013.
- `ui-test-manifest` не подключай: он добавляет `ComponentActivity` в debug APK. Этот экран на
  время теста регистрирует `ComponentActivityRule` из `testing/ComposeTesting.kt` — ставь его
  правилом с `order = 0`, а `createComposeRule()` — с `order = 1`.
- Ищи узлы так, как их видят пользователь и TalkBack: по тексту, подписи поля, действию
  (`onNodeWithText`, `hasText`, `hasSetTextAction`). `Modifier.testTag` в код приложения не добавляй.
- Результат проверяй по состоянию узлов (`EditableText`, `TextSelectionRange`) и по тому, что
  получили колбэки (`onSave`), а не по скриншоту.

## Последствия

- Эмулятор остаётся для того, что на JVM не проверить: системная клавиатура и выбор файла,
  поворот, настоящий телефон.

## Проверка

- `scripts/dev.sh rules` (`check_adr_rules`) падает, если в `*.gradle.kts` есть `ui-test-manifest`.

## Альтернативы

- Инструментальные тесты на эмуляторе в CI — медленно и нестабильно (ADR-0013).
- `debugImplementation("androidx.compose.ui:ui-test-manifest")`, как советует документация Android, —
  лишний экран в debug APK, который ставят и на телефон.
