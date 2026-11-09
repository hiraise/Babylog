# iOS port — session plan

> Written in English by the owner's decision (saves tokens for porting sessions). Do not translate.
> Rules: [ADR-0049](../docs/adr/0049-ios-swiftui.md) (stack, shared resources, parity),
> [ADR-0050](../docs/adr/0050-ios-local-data.md) (storage), [CLAUDE.md](../CLAUDE.md) (process).
> This file is the single place for iOS port status: update your session's row in the PR that finishes it.

## How a porting session works

1. Read only: `CLAUDE.md`, ADR-0049, ADR-0050, ADR-0051 (after S1a), your session section below,
   the ADRs it lists, and the Android files it lists (main + their tests). Do not explore the whole repo.
2. Port file by file. **One Swift file per Kotlin file, same base name, mirrored folder**
   (`feature/notes/NotesContent.kt` → `ios/BabyLog/Feature/Notes/NotesContent.swift`). Port the
   Kotlin tests of those files into `ios/BabyLogTests/` the same way. Keep Kotlin KDoc rules as Swift doc comments.
3. Behaviour = Android code. If something cannot be the same on iOS, keep the closest behaviour and
   write it in the doc comment; if it changes user-visible behaviour or data, stop and ask the owner (ADR).
4. Done when `scripts/dev.sh ios` and `scripts/dev.sh rules` pass, the owner approved it on the
   simulator (`scripts/dev.sh ios-run`, show via the iOS Simulator panel), the PR is green (`/pr`).
5. Work in your own worktree (`git worktree add ../kid-ios-<id> -b ios/<id> main`). Sessions marked
   parallel touch disjoint folders; the only shared file is the status table below — resolve conflicts there by keeping both rows.
6. Effort = the session's reasoning effort setting. Size = Android lines to port (main / tests).

## Architecture (S1a turns this into ADR-0051; then this section becomes a link)

- Layout: `ios/BabyLog/{App,Core/{Model,Data,UI},Feature/<Name>}`, tests `ios/BabyLogTests/` mirror it.
- Screen state: `@MainActor @Observable final class <Name>Model` = Android ViewModel; views get the model,
  hold no logic. No Combine. Async work — `async/await`; Room `Flow` → GRDB `ValueObservation` as `AsyncSequence`.
- DI: `AppContainer` (as `AppContainer.kt`) built in `BabyLogApp`, passed via `@Environment`.
  Tests build it on an in-memory `DatabaseQueue`; fakes mirror `testing/Fakes.kt`.
- Time: `Clock` protocol (as `core/ui/Clock.kt`), injected; stored values — epoch millis `Int64` like Android;
  `Calendar(identifier: .gregorian)`, `ru_RU`, Monday first, `TimeZone.current`.
- Text: Russian strings inline in Swift, as in Kotlin (no `.xcstrings`); plurals and `bySex` — ported functions from `Format.kt`.
- Sheets: `AppSheet` = SwiftUI `.sheet` + `presentationDetents` with theme styling (ADR-0018).
  Bottom bar — own component as Android (ADR-0035), not `TabView`, so the design matches.
- Tests: Swift Testing for logic and models; swift-snapshot-testing for views, light + dark, on one pinned
  simulator (`iPhone 17`, the iOS of the pinned Xcode), fixed `ru_RU`, the time zone the Android tests use, fixed `Clock`.
  Interaction logic lives in models and is unit-tested; XCUITest only for help guides (S9b).

## Sessions

Status: `todo` / `wip` / `done (#PR)`.

| ID | Session | Effort | Size | Needs | Parallel with | Status |
| --- | --- | --- | --- | --- | --- | --- |
| S0 | Owner prep | — | — | — | — | partly (Bundle ID done) |
| S1a | Skeleton, tooling, CI | high | — | S0 | — | todo |
| S1b | Theme and core components | medium | 3.9k / 0.6k | S1a | S2a | todo |
| S2a | Model, GRDB schema, repository | high | 1.9k / 0.8k | S1a | S1b | todo |
| S2b | Timers, stores, backups, privacy | high | incl. above | S2a | S3, S6a | todo |
| S3 | Export / import, shared fixtures | high | 0.7k / 0.9k | S2a | S2b, S6a | todo |
| S4a | Main shell, child header, menu | high | 1.4k / 0.7k | S1b, S2b, S3 | S6a, S7a | todo |
| S4b | Today: tiles, timers, day summary | high | 1.2k / 0.9k | S4a | S6a, S7a | todo |
| S4c | Editors part 1 (timer sheets) | medium | 1.3k / 0.5k | S4b | S6b | todo |
| S4d | Editors part 2 (measures, notes) | medium | 1.2k / 0.4k | S4c | S6b | todo |
| S4e | Illness | medium | 0.9k / 0.5k | S4d | S5b, S6b | todo |
| S5a | Diary tab: history, notes, tags | medium | 1.0k / 0.4k | S4d | S5b, S6b | todo |
| S5b | Photos: pick, crop, share | high | 0.8k / 0.3k | S4d | S5a, S4e | todo |
| S6a | Analytics computations | medium | 1.3k / 0.8k | S2a | S2b–S4b | todo |
| S6b | Analytics UI and charts | medium | 1.6k / 0.4k | S6a, S4a | S4c–S5b | todo |
| S7a | Development: data and logic | low | 0.4k / 0.4k | S2a | S2b–S4b | todo |
| S7b | Development: screens, banner, chart | medium | 0.6k / 0.2k | S7a, S4b, S6b | S8 | todo |
| S8 | Theme settings, dark palettes | low | 0.3k / 0.2k | S4a | S7b | todo |
| S9a | Help: topics and screens | medium | 0.7k / 0.4k | all UI | — | todo |
| S9b | Help: interactive guides | high | 0.6k / 0.6k | S9a | — | todo |
| S10 | Release prep and parity audit | medium | — | all | — | todo |

Critical path: S1a → S2a → S2b/S3 → S4a → S4b → S4c → S4d → S5a → (S9a → S9b) → S10.
External TestFlight for the focus group — after S4e and S5a (diary usable, data importable from Android).

### S0 — Owner prep (owner, no session)
- `xcodebuild -downloadPlatform iOS`; Team ID → tell the agent; app record in App Store Connect
  (Bundle ID `com.hiraise.babylog`); metadata that needs no build (category, age rating, privacy, price).

### S1a — Skeleton, tooling, CI (high)
- Write ADR-0051 from «Architecture» above (Proposed → owner accepts), replace that section with a link.
- `ios/BabyLog.xcodeproj` (synchronized folders), `BabyLogApp`, empty root view, `AppContainer`, `Clock`.
  SPM: GRDB, ZIPFoundation, swift-snapshot-testing (test target only). iOS 17, iPhone only, orientations as Android (not locked).
- `ios/Version.xcconfig` = `versionName`/`versionCode`; `scripts/dev.sh rules` checks it.
- Info.plist: `ITSAppUsesNonExemptEncryption = NO`, display name «Дневник малыша», `DEVELOPMENT_TEAM` from S0.
- Shared resources by reference (not copied): `app/src/main/res/font/*.ttf` (`UIAppFonts`),
  `app/src/main/assets/development/*.json`.
- `scripts/dev.sh`: `ios` (build + test on pinned simulator), `ios-goldens`, `ios-run`. Pin the Xcode version in one place.
- `.github/workflows/ios.yml` on a macOS runner, same pinned Xcode; job always runs (stays a required
  check) but exits early when the diff touches nothing in `ios/`, shared resources, `app/schemas/`, `fixtures/`, `scripts/dev.sh`.
- `.github/dependabot.yml`: `swift` ecosystem for `ios/`. `.claude/skills/pr`: iOS check and simulator showing.
- `docs/development.md`: «iOS» section (build, run, goldens). CLAUDE.md table: row for `ios/`.
- Owner after merge: archive and upload to **internal** TestFlight to prove signing works.

### S1b — Theme and core components (medium)
- Port `core/ui/theme/*` (incl. `Oklch.kt` colour math, `DarkPalettes.kt`, `ChartColors`, `TimerColors`,
  `IllnessColors`), `core/ui/components/*` except `chart/` and `LightDarkPreview`, `EventIcons.kt`, `Format.kt`.
- Icons: script `scripts/ios-icons.sh` converts `res/drawable/ic_*.xml` → SVG in `ios/BabyLog/Assets.xcassets`
  (preserve vector data); `rules` fails if generated icons are stale. App icon from `ic_launcher_foreground.xml`.
- Extend `check_design_tokens` to `*.swift` (no raw colours, sizes, fonts outside `Core/UI/Theme`).
- Tests: port `core/ui/theme` tests; snapshots of every component, light + dark.
- ADR: 0010, 0014, 0018, 0032, 0044.

### S2a — Model, GRDB schema, repository (high)
- Port `core/model/*`, `core/data/DiaryRepository.kt`, `core/data/room/*` → GRDB.
- Create the schema of the latest `app/schemas/**/7.json` directly as GRDB migration `v7` (no 1→6 history);
  test compares the created schema with the JSON (tables, columns, types, indices, FKs). Future Room migration → GRDB migration.
- Port `RoomDiaryRepositoryTest`, `core/model` tests, `testing/Fakes.kt`.
- ADR: 0008, 0015, 0016, 0024, 0025, 0027, 0030, 0031, 0040, 0041, 0050.

### S2b — Timers, stores, backups, privacy (high)
- `Timers.kt` (ADR-0020, 0021), `SelectedChildStore`, `ThemeModeStore`, `HelpStore`, `DevelopmentSeenStore`
  (UserDefaults), `RecoveryNotices`, daily DB copies and corrupt-DB recovery (ADR-0033, `DatabaseBackupsTest`).
- `isExcludedFromBackup` on DB, photos, copies; `PrivacyInfo.xcprivacy` (ADR-0050).
- Skip `ShadeStore` (ADR-0042 not on iOS).

### S3 — Export / import, shared fixtures (high)
- Port `feature/transfer/*`: `CsvCodec`, `CsvTransfer`, `DiaryArchive` (ZIPFoundation), `DocumentStore`
  (`fileExporter` / `fileImporter`). UI hooks come in S4a.
- `fixtures/export/`: add an Android test that writes `sample.csv` + `sample.zip` from a fixed dataset and
  fails if they differ from committed files; iOS tests import both and must get the same records, and iOS
  export of the same dataset must equal `sample.csv` byte for byte and `sample.zip` entry by entry.
- ADR: 0009, 0045. `docs/development.md` «Формат экспорта» stays the spec.

### S4a — Main shell, child header, menu (high)
- `MainNavigation.kt`, `DiaryScreen.kt` (header, ⋮ menu: export, import, theme, help placeholders),
  `DiaryUiState`, `DiaryViewModel.kt` (shell part), `DayNavigation`, `RecoverySheet`,
  `feature/children/ChildSelector.kt`, `ChildSheet.kt` (no photo yet).
- ADR: 0035, 0017 (theme switch without flicker), 0033 (recovery sheet).

### S4b — Today: tiles, timers, summary (high)
- `DiaryCards.kt`, `TimerCard`/timer tiles, undo snackbar (ADR-0021), `CryingContext.kt`, `DiaryContent.kt` (Today part),
  `feature/summary/*` (ADR-0026). Port `feature/diary` and `feature/summary` tests for these.

### S4c — Editors part 1 (medium)
- `core/ui/DateTimePickers.kt`, `EventEditor`, `EditorFields`, `SleepSheets`, `FeedingSheets`, `BottleSheet`,
  `CryingSheets`, `CryingChoices`, `DiaperSheet`. Photo fields stubbed until S5b.
- ADR: 0015, 0020, 0025, 0030.

### S4d — Editors part 2 (medium)
- `WeightSheet`, `HeightSheet`, `TemperatureSheet`, `MedicineSheet`, `MedicineSuggestions`, `NoteSheet`, `TagPicker`.
- ADR: 0027, 0031, 0041.

### S4e — Illness (medium)
- `feature/illness/*`, `IllnessSheets`, `HealthHost`, illness card on Today. ADR: 0040.

### S5a — Diary tab (medium)
- History part of `DiaryContent.kt`, `feature/notes/*`. ADR: 0031, 0035.

### S5b — Photos (high)
- `core/ui/photo/*` → `PhotosPicker` + camera via `UIImagePickerController`, no permissions beyond the
  camera usage string; JPEG limits as Android (`EventPhoto.MAX_SIDE`, 512×512 child photo);
  `PhotoCrop*`, `PhotoButton`, `FormPhotos`, `PhotoStrip`, `PhotosField`; share via `ShareLink`.
- ADR: 0024, 0046.

### S6a — Analytics computations (medium)
- Pure logic of `feature/analytics/*`: `AnalyticsModel`, `DailyStats`, `Metrics`, `NightSleep`, `Rhythm`,
  `Growth`, `Crying`, `Tags`, `IllnessAnalytics`. All analytics logic tests. ADR: 0028, 0036.

### S6b — Analytics UI and charts (medium)
- `core/ui/components/chart/*`, `AnalyticsOverview`, `AnalyticsDetail`, `AnalyticsSection`, `AnalyticsCards`,
  `CryingCards`, `TagsCards`, `SleepHelp`, `StatTile` usage. Own chart components, no Swift Charts (ADR-0028). ADR: 0029.

### S7a — Development: data and logic (low)
- `DevelopmentJson`, `TipsSource`, `Tip`, `Milestones`, `DevelopmentAge`, `DevelopmentWeek`, `DevelopmentViewModel`
  logic; reads the shared JSON. Tests from `feature/development`. ADR: 0037.

### S7b — Development: screens (medium)
- `DevelopmentContent`, `TipSheets` (keep the «не заменяет консультацию врача» line and sources), `MilestoneChart`,
  `WeekTipsBanner` on Today. ADR: 0037, 0047 (disclaimer rule).

### S8 — Theme settings (low)
- `ThemeSheet.kt`, palette choice, system / light / dark. ADR: 0017, 0032, 0044.

### S9a — Help: topics and screens (medium)
- `HelpModel`, `HelpTopics*.kt` (text — port verbatim, drop steps about the shade), `HelpScreen`,
  `HelpTopicScreen`, «Новое» by `since` (`HelpStore`). ADR: 0043.

### S9b — Help: interactive guides (high)
- `core/ui/guide/Guide.kt`, `GuideController`, `GuidePanel`, `HelpPointerCard`, `SampleDiary`, `GuideSandbox`
  (in-memory DB), guide targets in all feature views; one XCUITest per guide, as Android guide tests. ADR: 0043.

### S10 — Release prep and parity audit (medium)
- Go through the ADR index and Android UI tests; list gaps; fix small ones, report big ones.
- App Store screenshots via a snapshot test like `ReadmeScreenshotTest`; texts `fastlane/metadata/ios/ru/`
  with the «не медицинское изделие» disclaimer (ADR-0047); `PRIVACY.md` link; README «Установка» for iPhone.
- Owner: external TestFlight → App Store submission.

## Out of scope
- Shade buttons (ADR-0042) and any Live Activity / widget replacement — separate ADR if the owner asks.
