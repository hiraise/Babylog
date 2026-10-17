# ADR-0006: Стек: Kotlin, Compose и Room в одном модуле; сборка на AGP 9 и JDK 17+

- **Статус:** Принято
- **Дата:** 2026-10-15
- **Затрагивает:** `build.gradle.kts`, `app/build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `gradle/wrapper/`, выбор JDK в `scripts/dev.sh`
- **Заменяет:** [ADR-0003](0003-tech-stack.md)

## Контекст

ADR-0003 закрепил compileSdk 34 и JDK 17. Android Studio 2026.2 поставляется с JDK 25, на
котором прежний тулчейн (Gradle 8.5, AGP 8.2, Kotlin 1.9) не запускается, а свежим
библиотекам AndroidX нужен compileSdk 37. Тулчейн обновлён, состав стека прежний.

## Решение

- Язык — Kotlin. UI — Jetpack Compose (Material 3) без XML-разметки экранов; одна `MainActivity`.
- Данные — Room (через KSP) поверх SQLite; модель данных — [ADR-0008](0008-data-model-children.md).
- Один Gradle-модуль `:app`. Структура пакетов, состояние экранов и сборка зависимостей —
  [ADR-0007](0007-architecture.md).
- Kotlin компилирует сам AGP (built-in Kotlin): не применяй `org.jetbrains.kotlin.android`
  в модулях. В корневом `build.gradle.kts` он объявлен с `apply false` только ради версии
  Kotlin; `org.jetbrains.kotlin.plugin.compose` держи той же версии.
- Обновляй Gradle, AGP, Kotlin и KSP вместе: KSP должен поддерживать выбранный Kotlin,
  а Android Studio — выбранный AGP.
- Gradle запускается на JDK 17+, байткод — Java 17 (`compileOptions`, `jvmTarget`).
  Путь к JDK не коммить: `org.gradle.java.home` в `gradle.properties` ломает сборку на
  других машинах. JDK задают `JAVA_HOME`, Android Studio или `scripts/dev.sh`.
- minSdk 26 (Android 8.0), targetSdk 34, compileSdk — какой требуют зависимости (сейчас 37).
  targetSdk поднимай отдельным изменением с проверкой UI на устройстве: с 35 Android
  включает edge-to-edge.
- Новую библиотеку подключай, только если без неё задачу разумно не решить, и вместе с ADR.
  Обновление версий без изменения поведения ADR не требует. Иконки — векторы
  `res/drawable/ic_*.xml`, библиотек иконок нет ([ADR-0010](0010-design-system.md)).
- Debug-сборка — отдельное приложение (`applicationIdSuffix = ".debug"`) со своей базой:
  отладка не трогает настоящие записи. Инструменты Compose для Android Studio
  (`ui-tooling`) подключай только в debug.

## Последствия

- Навигационной библиотеки, DI-фреймворка, сетевого стека и многомодульности нет
  сознательно; их появление — повод для нового ADR.
- Проект открывается в Android Studio с поддержкой AGP 9.4 (2026.2 и новее). Когда Studio
  перейдёт на новый JDK, Gradle может понадобиться обновить вслед.

## Проверка

- `scripts/dev.sh check` (`./gradlew testDebugUnitTest assembleDebug`) проходит.
- После обновления Room файлы в `app/schemas/` не меняются; изменились — нужна миграция
  ([ADR-0008](0008-data-model-children.md)).
