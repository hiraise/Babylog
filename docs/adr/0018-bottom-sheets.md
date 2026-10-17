# ADR-0018: Всплывающие окна — только нижние листы `AppSheet`

- **Статус:** Принято
- **Дата:** 2026-10-16
- **Затрагивает:** `core/ui/components/AppSheet.kt`, `core/ui/DateTimePickers.kt`, `feature/editor/*Sheet*.kt`, `feature/children/ChildSheet.kt`, `feature/diary/` (`DiaryScreen.kt`, `DayNavigation.kt`, `ThemeSheet.kt`), `scripts/dev.sh` (`check_adr_rules`)

## Контекст

Формы записей, меню ⋮, выбор дня, подтверждения и пикеры были диалогами по центру экрана и
выпадающим меню. Владелец хочет нижние листы: до них дотягивается большой палец, а приложением
часто пользуются одной рукой с ребёнком на руках.

## Решение

- Любое всплывающее окно делай нижним листом `AppSheet`: формы, меню, выбор из списка,
  подтверждения удаления, пикеры даты и времени. `AlertDialog`, `BasicAlertDialog`,
  `DatePickerDialog`, `DropdownMenu` и `Dialog` из `androidx.compose.ui.window` не используй.
- Форма — `AppSheet(title, icon, confirm = SheetAction(…))`: справа внизу «Отмена» и основная
  кнопка; необратимое действие — `SheetAction(destructive = true)`, красная кнопка.
- Меню и списки выбора — `AppSheet(edgeToEdge = true)` без `confirm`; пункт меню —
  `SheetMenuItem`. Выбор закрывает лист через `SheetCloser.close { … }`: сначала анимация,
  потом действие.
- Содержимое, которое прокручивается само (`LazyColumn`, `DatePicker`, `TimePicker`), клади
  в лист с `scrollable = false`; календарь — с `edgeToEdge = true`, он не уже 360 dp.
- Лист поверх листа допустим (пикер даты из формы): «Назад» закрывает верхний.
- Композиции листов называй `XxxSheet`, файлы — `XxxSheet.kt` (`XxxSheets.kt`, если их несколько).

## Последствия

- Отмена — ещё и свайп вниз, тап мимо листа и «Назад»; все они вызывают `onDismiss`.
- С клавиатурой лист поднимается над ней, а содержимое прокручивается — кнопки остаются видны.

## Проверка

- `scripts/dev.sh rules` (`check_adr_rules`) падает на `AlertDialog(`, `DatePickerDialog(`,
  `DropdownMenu(` и импорт `androidx.compose.ui.window.Dialog` в коде приложения.

## Альтернативы

- Диалоги для подтверждений, как советует Material 3, — владелец выбрал единый вид для всех окон.
