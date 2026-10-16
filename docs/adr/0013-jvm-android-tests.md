# ADR-0013: Миграции, Room и экраны проверяются тестами на JVM (Robolectric, room-testing, Roborazzi), а не вручную на эмуляторе

- **Статус:** Принято
- **Дата:** 2026-10-16
- **Затрагивает:** `app/src/test/`, `app/src/test/screenshots/`, `app/build.gradle.kts` (`testImplementation`, `testOptions`, `sourceSets`, `roborazzi`), `build.gradle.kts`, `scripts/dev.sh` (`check`, `goldens`), CLAUDE.md «Сборка и проверка»

## Контекст

Миграции базы, обновление поверх старой версии и вид экранов агенты проверяли вручную на
эмуляторе: скриншоты в контексте модели дорогие, а проверка не повторяется сама. Тесты
[ADR-0007](0007-architecture.md) идут на JVM и Android-код не запускали.

## Решение

- Код, которому нужен Android (Room, Compose), тестируй на JVM через Robolectric
  (`@RunWith(RobolectricTestRunner::class)`), в `app/src/test`. Эмулятор в CI не нужен.
- Тестовые библиотеки — только в `testImplementation`: `org.robolectric:robolectric` (MIT),
  `androidx.room:room-testing`, `io.github.takahirom.roborazzi:roborazzi` и `roborazzi-compose`
  (Apache-2.0), плагин `io.github.takahirom.roborazzi`.
- Каждую миграцию покрывай в `MigrationTest`: `runMigrationsAndValidate` против схемы из
  `app/schemas/` и открытие старой базы через `AppDatabase.build` с проверкой всех записей.
  Схемы подключены к assets варианта debug — тестам на JVM доступны только они.
- Правила хранения из [ADR-0008](0008-data-model-children.md) проверяй на настоящей Room
  (`RoomDiaryRepositoryTest`), логику экранов — на фейках из `testing/Fakes.kt`.
- Экраны сверяй со скриншотами: `captureRoboImage` с `@GraphicsMode(NATIVE)`,
  `RobolectricDeviceQualifiers.Pixel5`, фиксированными часами и поясом UTC. Эталоны — PNG в
  `app/src/test/screenshots/`.
- `scripts/dev.sh check` и CI запускают `verifyRoborazziDebug`: все unit-тесты плюс сверку
  скриншотов. Допуск — 1 % пикселей: Mac и Linux сглаживают шрифты по-разному.
- Меняешь UI намеренно — перезапиши эталоны (`scripts/dev.sh goldens`) и закоммить изменившиеся
  PNG в том же PR. Новый экран или состояние — новый скриншот-тест.
- Картинки сравнения смотрит человек (артефакт `reports` в CI). Агенту достаточно имени
  упавшего теста.

## Последствия

- Обновление поверх старой версии и вид экранов проверяются на каждом PR без эмулятора.
- Первый запуск качает Robolectric-сборку Android (~100 МБ с Maven Central).
- Эталоны занимают ~200 КБ на скриншот; правка темы меняет их все разом.

## Проверка

- `scripts/dev.sh check` проходит; на подменённом эталоне падает тест с его именем.

## Альтернативы

- Инструментальные тесты на эмуляторе в CI — +5–10 минут на прогон и нестабильность.
- Paparazzi — тоже скриншоты на JVM, но без Robolectric: не подходит для Room и миграций, второй стек.
