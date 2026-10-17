# Архитектурные решения (ADR)

Здесь фиксируются архитектурные и важные системные решения проекта. Проект разрабатывают
AI-агенты, поэтому ADR пишутся в первую очередь для них — коротко, с проверяемыми
правилами, — и во вторую очередь для людей. Когда нужен ADR — в [CLAUDE.md](../../CLAUDE.md);
как его писать и шаблон — [template.md](template.md).

Статусы: «Предложено» → «Принято» → «Заменено» или «Отменено». Принятые ADR обязательны;
заменённые и отменённые хранятся для истории — действует тот ADR, который заменил.

| ADR | Решение | Статус | Затрагивает |
| --- | --- | --- | --- |
| [0001](0001-open-source-non-commercial.md) | Проект открытый (GPL-3.0-or-later) и некоммерческий | Принято | `LICENSE`, зависимости, секреты |
| [0002](0002-standalone-local-data.md) | Автономность: данные только на устройстве, сеть — только в интеграциях, включённых пользователем | Принято | `AndroidManifest.xml`, сетевой код, интеграции |
| [0003](0003-tech-stack.md) | Стек: Kotlin, Jetpack Compose и Room в одном модуле, минимум зависимостей | Заменено [0006](0006-tech-stack-agp9.md) | `*.gradle.kts`, структура пакетов |
| [0004](0004-data-model.md) | Единая таблица событий, время с точностью до секунды, схема меняется только миграциями | Заменено [0008](0008-data-model-children.md) | `data/`, `app/schemas/`, `MainViewModel.kt` |
| [0005](0005-csv-exchange-format.md) | CSV — формат экспорта и импорта: обратная совместимость, импорт только добавляет | Заменено [0009](0009-csv-with-children.md) | `data/CsvCodec.kt`, импорт и экспорт, README «Формат CSV» |
| [0006](0006-tech-stack-agp9.md) | Стек: Kotlin, Compose и Room в одном модуле; сборка на AGP 9 и JDK 17+ | Принято | `*.gradle.kts`, `gradle.properties`, `gradle/wrapper/`, выбор JDK в `scripts/dev.sh` |
| [0007](0007-architecture.md) | Архитектура: пакеты по функциям, данные за одним репозиторием, состояние экрана во ViewModel | Принято | структура пакетов, `AppContainer.kt`, `app/src/test/` |
| [0008](0008-data-model-children.md) | Несколько детей: таблица `children`, все записи в `events` с привязкой к ребёнку | Принято | `core/model/`, `core/data/`, `app/schemas/` |
| [0009](0009-csv-with-children.md) | CSV с колонкой `child` — формат экспорта и импорта; импорт только добавляет | Принято | `feature/transfer/`, README «Формат CSV» |
| [0010](0010-design-system.md) | Оформление задаёт `BabyLogTheme`: цвета, шрифты, формы и отступы только из темы, Material 3 без динамического цвета | Принято | UI в `core/ui/` и `feature/*/`, `res/font/`, `res/drawable/`, `res/values*/`, библиотеки шрифтов и иконок |
| [0011](0011-ci-github-actions.md) | Проверки — в `scripts/dev.sh`, на GitHub Actions для каждого PR; `main` меняется только через PR с зелёным CI | Принято | `.github/`, `scripts/dev.sh`, CLAUDE.md «Сборка и проверка», README «Разработка» |
| [0012](0012-release-signing.md) | Релизы — APK, подписанный ключом проекта в CI, публикуется в GitHub Releases по тегу `v<versionName>` | Принято | `app/build.gradle.kts` (подпись, версия), `.github/workflows/release.yml`, `scripts/dev.sh release`, README «Установка на телефон» |
| [0013](0013-jvm-android-tests.md) | Миграции, Room и экраны проверяются тестами на JVM (Robolectric, room-testing, Roborazzi), а не вручную на эмуляторе | Принято | `app/src/test/`, `app/src/test/screenshots/`, `app/build.gradle.kts`, `scripts/dev.sh` (`check`, `goldens`) |
| [0014](0014-own-icons.md) | Иконку, которой нет в Material Symbols, рисуем сами в его начертании | Принято | `res/drawable/ic_*.xml`, README «Лицензия» |
| [0015](0015-feeding-timer.md) | Кормление — таймер с началом и концом, «Сменить грудь» — новая запись встык; прежние кормления — без длительности | Принято | `Feeding`, `startFeeding`, `switchBreast`, сессии в `feature/summary/`, `FEEDING` в CSV |
| [0016](0016-child-sex.md) | Пол ребёнка — `children.sex` и `detail` строки PROFILE в CSV; слова по роду — `bySex`, без пола — мужской род | Принято | `Child.sex`, `PROFILE` в CSV, слова о ребёнке в UI (`bySex`) |
| [0017](0017-theme-mode.md) | Тема выбирается в приложении (как в системе / светлая / тёмная) и меняется без пересоздания окна | Принято | `MainActivity.kt`, `AndroidManifest.xml`, `ThemeModeStore.kt`, `Theme.kt`, AppCompat |
| [0018](0018-bottom-sheets.md) | Всплывающие окна — только нижние листы `AppSheet`: формы, меню, подтверждения, пикеры | Принято | `AppSheet.kt`, `DateTimePickers.kt`, всплывающие окна в `core/ui/` и `feature/*/` |
| [0019](0019-diary-tabs.md) | Главный экран — ребёнок в шапке («Дневник Маши», лист «Дети»), вкладки «Сегодня» (таймеры, плитки, сводка) и «История» | Принято | `DiaryScreen.kt`, `DiaryContent.kt`, `ChildSelector.kt`, `Avatar.kt`, `diaryTitle` |
| [0020](0020-sleep-feeding-exclusive.md) | Сон и кормление не идут одновременно: начало одного заканчивает другое | Принято | `startSleep`, `startFeeding`, `testing/Fakes.kt`, листы старта в `feature/editor/` |
| [0021](0021-tap-to-stop-timer.md) | Сон и кормление — плитки в один ряд; касание начинает и заканчивает таймер, «Отменить» в снекбаре возвращает его | Принято | `TimerTile`, `DiaryCards.kt`, отмена остановки в `DiaryViewModel.kt` |
| [0025](0025-bottle-feeding.md) | Бутылочка — разовая запись `Bottle` (тип `BOTTLE`, объём в мл и что внутри), заканчивает идущий сон | Принято | `Bottle`, `EventRecord.kt`, `addBottle`, `FeedingStartSheet`, сводка, `feature/transfer/`, README «Формат CSV» |
| [0026](0026-summary-day-totals.md) | Сводка дня — только итоги дня; что сейчас и что было последним — на плитках | Принято | `DaySummary`, `DaySummaryCard`, подписи плиток в `DiaryCards.kt`, `DiaryUiState.lastFeeding` |
