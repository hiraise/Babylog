# BabyLog iOS port — agent rules

> Written in English by the owner's decision to save porting-session tokens. Do not translate.
> The canonical repository rules are in the root `AGENTS.md`; do not reread them through `CLAUDE.md`.

## Session workflow

1. Start exactly one implementation session with the `ios-port` skill and its ID. Read this file and
   `ios/sessions/<ID>.md`; do not read other session specifications or explore the whole repository.
2. Read the common ADRs below, plus the additional ADRs and Android files listed in the session
   specification. Android paths are relative to `app/src/main/java/com/kid/babylog/`
   unless given as repository paths; their tests live under the corresponding `app/src/test/` tree.
3. Work sequentially in the main checkout. Confirm `git status` is clean, then create the branch:
   `git fetch && git switch -c ios/<id> origin/release/v1.11`.
   Never discard or overwrite another task's uncommitted changes. Do not create worktrees.
4. Port file by file: **one Swift file per Kotlin file, same base name, mirrored folder**
   (`feature/notes/NotesContent.kt` → `ios/BabyLog/Feature/Notes/NotesContent.swift`). Port the
   Kotlin tests into `ios/BabyLogTests/` the same way. Keep Kotlin KDoc rules as Swift doc comments.
5. Behaviour equals the Android code. If identical behaviour is impossible, keep the closest behaviour
   and document why. If user-visible behaviour or data changes, stop and ask the owner about an ADR.
6. Done when `scripts/dev.sh ios` and `scripts/dev.sh rules` pass, the owner approved the build on
   the simulator (`scripts/dev.sh ios-run`, shown through the iOS Simulator panel), and the PR is green.
   Use the `pr` skill; open the PR with `--base release/v1.11` only after owner approval.
7. The whole port ships in release 1.11. Branch from the release branch, not `main`. After approval
   and green CI, merge into the release branch only through the `release` skill, section «Пока PR открыт»,
   when the owner asks.
8. Run one session at a time in the main checkout. Update only your session's row in `ios/PLAN.md`
   in the PR that finishes it.
9. Do not launch subagents for routine exploration: the session specification is the file map.
   Any required content verification, including `tips-verifier`, follows the ADRs instead.

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

## Common ADRs

- [ADR-0049](../docs/adr/0049-ios-swiftui.md): stack, shared resources, parity.
- [ADR-0050](../docs/adr/0050-ios-local-data.md): storage.
- ADR-0051 after S1a: iOS architecture.

## Out of scope

- Shade buttons (ADR-0042) and any Live Activity / widget replacement — separate ADR if the owner asks.
