# S4a — Main shell, child header, menu

## Port and implement

- `feature/diary/MainNavigation.kt`, `DiaryScreen.kt` (header, ⋮ menu: export, import, theme, help placeholders),
  `DiaryUiState`, `DiaryViewModel.kt` (shell part), `DayNavigation`, `RecoverySheet`.
- `feature/children/ChildSelector.kt`, `ChildSheet.kt` (no photo yet).
- Port the tests covering these shell components and ViewModel behaviour.

## Additional ADRs

- [0035](../../docs/adr/0035-bottom-navigation.md), [0017](../../docs/adr/0017-theme-mode.md)
  (theme switch without flicker), [0033](../../docs/adr/0033-database-backups.md) (recovery sheet).
