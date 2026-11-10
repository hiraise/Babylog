# S2b — Timers, stores, backups, privacy

## Port and implement

- `core/data/Timers.kt` (ADR-0020, 0021), `SelectedChildStore`, `ThemeModeStore`, `HelpStore`,
  `DevelopmentSeenStore` (UserDefaults), `RecoveryNotices`, daily DB copies and corrupt-DB recovery
  (ADR-0033, `DatabaseBackupsTest`).
- `isExcludedFromBackup` on DB, photos, copies; `PrivacyInfo.xcprivacy` (ADR-0050).
- Skip `ShadeStore` (ADR-0042 not on iOS).

## Additional ADRs

- [0020](../../docs/adr/0020-sleep-feeding-exclusive.md), [0021](../../docs/adr/0021-tap-to-stop-timer.md),
  [0033](../../docs/adr/0033-database-backups.md).
