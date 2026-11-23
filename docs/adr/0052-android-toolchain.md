# ADR-0052: Android-стек — Kotlin, Compose и Room; targetSdk 36, тесты и CI на JDK 21+

- **Статус:** Принято
- **Дата:** 2026-11-23
- **Затрагивает:** `*.gradle.kts`, `gradle.properties`, `gradle/wrapper/`, `scripts/dev.sh`, `.github/workflows/ci.yml`, `.github/workflows/release.yml`, `docs/development.md`, AGENTS.md «Сборка и проверка»
- **Заменяет:** [ADR-0006](0006-tech-stack-agp9.md) целиком и пункт [ADR-0011](0011-ci-github-actions.md) о JDK 17 в CI

## Контекст

Google Play требует `targetSdk` 36, а Robolectric 4.18.1 запускает соответствующий тестовый SDK только
на Java 21. Прежний CI с JDK 17 падает при создании sandbox. Заодно фиксируем актуальный стек вместо
ADR-0006, у которого иначе накапливаются частичные замены.

## Решение

- Язык — Kotlin, UI — Jetpack Compose с Material 3, данные — Room поверх SQLite. Один Gradle-модуль
  `:app`; пакеты и слои — по [ADR-0007](0007-architecture.md).
- Kotlin компилирует сам AGP (built-in Kotlin): не применяй `org.jetbrains.kotlin.android` в модулях.
  `org.jetbrains.kotlin.plugin.compose` держи той же версии Kotlin.
- Обновляй Gradle, AGP, Kotlin и KSP вместе: KSP должен поддерживать выбранный Kotlin, а Android Studio — AGP.
- Запускай Gradle, `check`, CI и релизную сборку на JDK 21 или новее. Если `JAVA_HOME` ниже 21,
  `scripts/dev.sh` берёт JDK из Android Studio. В `ci.yml` и `release.yml` устанавливай Temurin 21.
- Байткод приложения и тестов остаётся Java 17 (`compileOptions`, `jvmTarget`).
- `minSdk` 26 (Android 8.0), `targetSdk` 36, `compileSdk` — какой требуют зависимости (сейчас 37).
  Следующий `targetSdk` поднимай отдельным изменением с проверкой UI: с 35 Android включает edge-to-edge.
- Новую библиотеку подключай, только если без неё задачу разумно не решить, и вместе с ADR. Обновление
  версий без изменения поведения ADR не требует. Иконки — векторы `res/drawable/ic_*.xml`.
- Debug-сборка — отдельное приложение `com.hiraise.babylog.debug` со своей базой. Инструменты Compose
  для Android Studio подключай только в debug.

## Последствия

- Проект открывается в Android Studio с поддержкой AGP 9.4 (2026.2 и новее).
- JDK 17 достаточен для компиляции без Robolectric SDK 36, но не для штатных проверок проекта.

## Проверка

- `scripts/dev.sh check` и `scripts/dev.sh build` на JDK 21+ зелёные; такой же JDK использует CI.

## Альтернативы

- Оставить CI на JDK 17 и тестировать SDK 35 — проверка не совпадала бы с целевым поведением приложения.
