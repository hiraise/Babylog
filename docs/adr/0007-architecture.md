# ADR-0007: Архитектура: пакеты по функциям, данные за одним репозиторием, состояние экрана во ViewModel

- **Статус:** Принято
- **Дата:** 2026-10-15
- **Затрагивает:** структура пакетов `app/src/main/java/com/kid/babylog/`, `AppContainer.kt`, тесты `app/src/test/`
- **Заменяет:** часть [ADR-0006](0006-tech-stack-agp9.md) о структуре пакетов и состоянии

## Контекст

Проект растёт, и меняют его AI-агенты. Правка должна затрагивать предсказуемый набор небольших
файлов и проверяться быстрыми тестами на JVM, без эмулятора.

## Решение

- Пакеты:
  - `core/model` — доменная модель: `BabyEvent` (sealed), `Child`; строковые коды — только в `EventRecord.kt`;
  - `core/data` — `DiaryRepository` (единственный доступ к данным), `SelectedChildStore`; Room — в `core/data/room`;
  - `core/ui` — тема, `LocalClock`, форматирование, общие пикеры; компоненты и иконки записей —
    `core/ui/components`, `EventIcons.kt` ([ADR-0010](0010-design-system.md));
  - `feature/<функция>` — UI, ViewModel и чистая логика одной функции: `diary` (главный экран),
    `children`, `editor`, `summary`, `transfer`.
- `feature` зависит только от `core`. Функции не импортируют друг друга, кроме `feature/diary`:
  он собирает остальные на главном экране. Room виден только в `core/data/room`.
- Зависимости собирает вручную `AppContainer`, DI-фреймворка нет. ViewModel получает их
  через конструктор и фабрику из `AppContainer`.
- ViewModel отдаёт один `StateFlow<XxxUiState>` с данными экрана; UI вызывает методы ViewModel.
  Эфемерное состояние UI (открытый диалог, меню) живёт в Compose (`rememberSaveable`).
- Экран делится на `XxxScreen` (подключение к ViewModel и диалоги) и UI без собственного
  состояния (`XxxContent`, карточки), который получает всё параметрами.
- ViewModel и логика не зависят от Android (`Context`, `Uri`): платформу закрывают интерфейсы
  (`DocumentStore`, `SelectedChildStore`). Текущее время берётся только из `java.time.Clock`
  (`AppContainer.clock`, в UI — `LocalClock`).
- Расчёты и правила (сводка, импорт, проверки) — чистые функции в пакете функции, а не
  в composable и не во ViewModel.
- `when` по `BabyEvent` и enum-ам модели пишется без `else`.
- Файл не длиннее ~300 строк; вырос — раздели по смыслу.
- Тесты — на JVM в `app/src/test` с фейками из `testing/Fakes.kt`; для ViewModel —
  `kotlinx-coroutines-test` (только в тестах, Apache-2.0).

## Последствия

- Новый тип записи: класс в `BabyEvent.kt` и коды в `EventRecord.kt`, остальные места покажет
  компилятор (форматирование, редактор, сводка).
- Новая функция — пакет `feature/<имя>` по образцу `summary` (логика и карточка) или `diary`
  (экран с ViewModel).
- Второй экран потребует навигации (Navigation Compose) и отдельного ADR.

## Проверка

- `System.currentTimeMillis` встречается только в `AppContainer.kt`.
- `androidx.room` импортируется только в `core/data/room/`.
- `grep -n "^import android\." feature/*/*ViewModel.kt` ничего не находит.

## Альтернативы

- Многомодульная Clean Architecture (`:core:*`, `:feature:*`, Hilt) — избыточна для одного
  экрана; пакеты один в один переносятся в модули, если понадобится.
- MVI-библиотека (Orbit, Circuit) — лишняя зависимость: однонаправленный поток данных
  получается и без неё.
