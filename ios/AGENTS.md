# BabyLog iOS port — agent rules

> Written in English by the owner's decision to save porting-session tokens. Do not translate.
> The canonical repository rules are in the root `AGENTS.md`; do not reread them through `CLAUDE.md`.

## Session workflow

1. Start exactly one implementation session with the `ios-port` skill and its ID. Read this file and
   `ios/sessions/<ID>.md`; do not read other session specifications or explore the whole repository.
2. Read the common ADRs below, plus the additional ADRs and Android files listed in the session
   specification. Android paths are relative to `app/src/main/java/com/kid/babylog/`
   unless given as repository paths; their tests live under the corresponding `app/src/test/` tree.
3. Work in a dedicated worktree. If it does not exist yet, create it and copy `local.properties`:
   `git fetch && git worktree add ../kid-ios-<id> -b ios/<id> origin/release/v1.11`.
   Check `git status` before work; never touch another session's dirty worktree.
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
8. Parallel sessions touch disjoint folders. The only shared file is `ios/PLAN.md`; resolve status-table
   conflicts by keeping both rows. Update only your session's row in the PR that finishes it.
9. Do not launch subagents for routine exploration: the session specification is the file map.
   Any required content verification, including `tips-verifier`, follows the ADRs instead.

## Architecture

See [ADR-0051](../docs/adr/0051-ios-architecture.md).

## Common ADRs

- [ADR-0049](../docs/adr/0049-ios-swiftui.md): stack, shared resources, parity.
- [ADR-0050](../docs/adr/0050-ios-local-data.md): storage.
- [ADR-0051](../docs/adr/0051-ios-architecture.md): iOS architecture.

## Out of scope

- Shade buttons (ADR-0042) and any Live Activity / widget replacement — separate ADR if the owner asks.
