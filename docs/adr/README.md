# Архитектурные решения (ADR)

Здесь фиксируются архитектурные и важные системные решения проекта. Проект разрабатывают
AI-агенты, поэтому ADR пишутся в первую очередь для них — коротко, с проверяемыми
правилами, — и во вторую очередь для людей. Когда нужен ADR и как его писать — в
[CLAUDE.md](../../CLAUDE.md); шаблон — [template.md](template.md).

Статусы: «Предложено» → «Принято» → «Заменено» или «Отменено». Принятые ADR обязательны;
заменённые и отменённые хранятся для истории — действует тот ADR, который заменил.

| ADR | Решение | Статус | Затрагивает |
| --- | --- | --- | --- |
| [0001](0001-open-source-non-commercial.md) | Проект открытый (GPL-3.0-or-later) и некоммерческий | Принято | весь репозиторий, `LICENSE`, зависимости |
| [0002](0002-standalone-local-data.md) | Автономность: данные только на устройстве, сеть — только в интеграциях, включённых пользователем | Принято | `AndroidManifest.xml`, сетевой код, интеграции |
| [0003](0003-tech-stack.md) | Стек: Kotlin, Jetpack Compose и Room в одном модуле, минимум зависимостей | Заменено [0006](0006-tech-stack-agp9.md) | `*.gradle.kts`, структура пакетов |
| [0004](0004-data-model.md) | Единая таблица событий, время с точностью до секунды, схема меняется только миграциями | Заменено [0008](0008-data-model-children.md) | `data/`, `app/schemas/`, `MainViewModel.kt` |
| [0005](0005-csv-exchange-format.md) | CSV — формат экспорта и импорта: обратная совместимость, импорт только добавляет | Заменено [0009](0009-csv-with-children.md) | `data/CsvCodec.kt`, импорт и экспорт, README «Формат CSV» |
| [0006](0006-tech-stack-agp9.md) | Стек: Kotlin, Compose и Room в одном модуле; сборка на AGP 9 и JDK 17+ | Принято | `*.gradle.kts`, `gradle/wrapper/`, `scripts/dev.sh` |
| [0007](0007-architecture.md) | Архитектура: пакеты по функциям, данные за одним репозиторием, состояние экрана во ViewModel | Принято | структура пакетов, `AppContainer.kt`, `app/src/test/` |
| [0008](0008-data-model-children.md) | Несколько детей: таблица `children`, все записи в `events` с привязкой к ребёнку | Принято | `core/model/`, `core/data/`, `app/schemas/` |
| [0009](0009-csv-with-children.md) | CSV с колонкой `child` — формат экспорта и импорта; импорт только добавляет | Принято | `feature/transfer/`, README «Формат CSV» |
| [0010](0010-design-system.md) | Оформление задаёт `BabyLogTheme`: цвета, шрифты, формы и отступы только из темы, Material 3 без динамического цвета | Принято | `core/ui/theme/`, `core/ui/components/`, `core/ui/EventIcons.kt`, UI в `feature/*/`, `res/font/`, `res/drawable/ic_*.xml`, `res/values/`, `res/values-night/` и `res/values-v27/` (`themes.xml`, `colors.xml`, `bools.xml`), `assets/licenses/`, `app/build.gradle.kts`, `scripts/dev.sh` |
