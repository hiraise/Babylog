# ADR-0010: Оформление задаёт `BabyLogTheme`; UI берёт цвета, шрифты, формы и отступы только из темы

- **Статус:** Принято
- **Дата:** 2026-10-16
- **Заменяет:** часть [ADR-0006](0006-tech-stack-agp9.md) о `material-icons-core`
- **Затрагивает:** `core/ui/theme/`, `core/ui/components/`, `core/ui/EventIcons.kt`, UI в `feature/*/`, `res/font/`, `res/drawable/ic_*.xml`, `res/values/`, `res/values-night/` и `res/values-v27/` (`themes.xml`, `colors.xml`, `bools.xml`), `assets/licenses/`, `app/build.gradle.kts`, `scripts/dev.sh`

## Контекст

Приложение оформляется по макету Figma «HiMom» владельца (его копия — файл `8WW8mvsIvlcLW9XBZTfJ4g`,
доступен только ему); автор макета разрешил использовать его стиль в проекте. Макет — исходник:
стиль из него извлечён один раз. Без единой темы агенты подбирают цвета и размеры на месте,
и стиль расползается.

## Решение

- Источник истины — код в `core/ui/theme/`: `Color.kt` (светлая и тёмная `ColorScheme`), `Type.kt`
  (`Typography`), `Shape.kt` (`Shapes`), `Spacing.kt` (`LocalSpacing`), `Shadows.kt` (`LocalShadows`:
  мягкая тень карточек и чипов, в тёмной теме её нет), `Theme.kt` (`BabyLogTheme`). Расходится с макетом —
  верен код.
- Раскладывай значения макета по ролям Material 3, а не заменяй его: диалоги, пикеры и чипы
  получают стиль из темы.
- Не используй динамический цвет (`dynamic*ColorScheme`): цвета одинаковы на всех устройствах.
- Каждая пара «текст — фон» даёт контраст не ниже 4,5:1. Поэтому акцент светлой темы темнее
  макетного (`#348333` вместо `#49B848`), второстепенный текст — `#6E6E6E` вместо `#B0B0B0`.
- Пиши `Color(0x…)`, `.sp`, `FontFamily` и `Font(` только в `core/ui/theme/`. Экраны берут цвета из
  `MaterialTheme.colorScheme`, стили текста — из `MaterialTheme.typography`.
- Шрифты: текст — Inter; заголовки, таймер и кнопки (`display*`, `headline*`, `labelLarge`) —
  Nunito ExtraBold. Подключай шрифты только файлами в `res/font/` под SIL OFL, с текстом лицензии
  в репозитории. Загружаемые шрифты (`ui-text-google-fonts`) не подключай: им нужна сеть
  ([ADR-0002](0002-standalone-local-data.md)).
- Скругления (`Shape.kt`): поля ввода, меню и snackbar — `extraSmall` 11 dp; чипы — `small` 18 dp;
  карточки — `medium` 18 dp; FAB, диалоги, пикеры и нижние листы — `large` и `extraLarge`.
  Кнопки Material 3 форму из `Shapes` не берут: они скруглены по высоте («пилюля»), у кнопки
  высотой 40 dp радиус 20 dp.
- Отступы бери из `LocalSpacing` (4, 8, 12, 16, 24, 32 dp). Литерал `dp` в экране допустим для
  размера элемента (иконка, аватар), но не для `padding` и `spacedBy`.
- Повторяющийся элемент (карточка с мягкой тенью, кнопка действия, строка записи) оформи
  composable в `core/ui/components/` и собирай экраны из них.
- Иконки — Material Symbols Rounded (Apache-2.0) векторами `res/drawable/ic_*.xml` через `AppIcon`,
  начертание и источник — в его KDoc. Библиотеки иконок (`material-icons-core`, `-extended`)
  не подключай. Логотип, название, фото и иконки iconmonstr из макета HiMom не переносятся.

## Последствия

- Смена стиля — правка `core/ui/theme/`; экраны не меняются.
- Цвет для нового смысла (например, типа записи) добавляется ролью в тему, а не литералом в экран.
- Поля ввода — только `OutlinedTextField`, карточки — `AppCard`: у `TextField` с заливкой и у `Card`
  из Material 3 фон `surfaceContainerHighest`, текст `primary` и `error` на нём ниже 4,5:1.
- Шрифты увеличивают APK примерно на 1 МБ.

## Проверка

- `scripts/dev.sh check` (`check_design_tokens`) падает, если вне `core/ui/theme/` есть `Color(0x`,
  `.sp`, `FontFamily`/`Font(`, `dynamic*ColorScheme` или отступ (`padding`, `PaddingValues`,
  `spacedBy`, `Spacer`, в том числе многострочный) с литералом `dp`.
- `ThemeContrastTest` проверяет контраст пар палитры, что заданы все роли `ColorScheme` и что
  `surfaceTint` равен `surface`; `ThemeWindowTest` — что цвет окна в `colors.xml` равен `background` темы.
- Изменения UI сверяй на эмуляторе в светлой и тёмной теме (`scripts/dev.sh screenshot`).

## Альтернативы

- Baloo, как в макете, — нет кириллицы.
- `#49B848` и `#B0B0B0` в светлой теме — контраст 2,5:1 и 2,2:1.
- Своя дизайн-система поверх `foundation` — пришлось бы заново писать диалоги и пикеры.
- Отдельный модуль или репозиторий дизайн-системы — у неё один потребитель ([ADR-0007](0007-architecture.md)).
