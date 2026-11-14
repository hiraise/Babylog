# iOS port — session plan

> Written in English by the owner's decision (saves tokens for porting sessions). Do not translate.
> Common workflow and architecture: [ios/AGENTS.md](AGENTS.md).
> Canonical repository rules: [AGENTS.md](../AGENTS.md); do not reread them through `CLAUDE.md`.
> This file is the single place for iOS port status: update your session's row in the PR that finishes it.

## Roadmap

Implementation status: `todo` / `wip` / `done (#PR)`. S0 is owner-only: `partly (…)` or `done`.
Effort = the session's reasoning effort setting. Size = Android lines to port (main / tests).
Each session name links to its only complete specification; do not duplicate it here.

| ID | Session | Effort | Size | Needs | Parallel with | Status |
| --- | --- | --- | --- | --- | --- | --- |
| S0 | [Owner prep](sessions/S0.md) | — | — | — | — | done |
| S1a | [Skeleton, tooling, CI](sessions/S1a.md) | high | — | S0 | — | done (#64) |
| S1b | [Theme and core components](sessions/S1b.md) | medium | 3.9k / 0.6k | S1a | S2a | done (#65) |
| S2a | [Model, GRDB schema, repository](sessions/S2a.md) | high | 1.9k / 0.8k | S1a | S1b | done (#67) |
| S2b | [Timers, stores, backups, privacy](sessions/S2b.md) | high | incl. above | S2a | S3, S6a | done (#68) |
| S3 | [Export / import, shared fixtures](sessions/S3.md) | high | 0.7k / 0.9k | S2a | S2b, S6a | done (#69) |
| S4a | [Main shell, child header, menu](sessions/S4a.md) | high | 1.4k / 0.7k | S1b, S2b, S3 | S6a, S7a | done (#70) |
| S4b | [Today: tiles, timers, day summary](sessions/S4b.md) | high | 1.2k / 0.9k | S4a | S6a, S7a | done (#71) |
| S4c | [Editors part 1 (timer sheets)](sessions/S4c.md) | medium | 1.3k / 0.5k | S4b | S6b | done (#75) |
| S4d | [Editors part 2 (measures, notes)](sessions/S4d.md) | medium | 1.2k / 0.4k | S4c | S6b | done (#76) |
| S4e | [Illness](sessions/S4e.md) | medium | 0.9k / 0.5k | S4d | S5b, S6b | done (#77) |
| S5a | [Diary tab: history, notes, tags](sessions/S5a.md) | medium | 1.0k / 0.4k | S4d | S5b, S6b | done (#78) |
| S5b | [Photos: pick, crop, share](sessions/S5b.md) | high | 0.8k / 0.3k | S4d | S5a, S4e | done (#79) |
| S6a | [Analytics computations](sessions/S6a.md) | medium | 1.3k / 0.8k | S2a | S2b–S4b | wip |
| S6b | [Analytics UI and charts](sessions/S6b.md) | medium | 1.6k / 0.4k | S6a, S4a | S4c–S5b | todo |
| S7a | [Development: data and logic](sessions/S7a.md) | low | 0.4k / 0.4k | S2a | S2b–S4b | todo |
| S7b | [Development: screens, banner, chart](sessions/S7b.md) | medium | 0.6k / 0.2k | S7a, S4b, S6b | S8 | todo |
| S8 | [Theme settings, dark palettes](sessions/S8.md) | low | 0.3k / 0.2k | S4a | S7b | todo |
| S9a | [Help: topics and screens](sessions/S9a.md) | medium | 0.7k / 0.4k | all UI | — | todo |
| S9b | [Help: interactive guides](sessions/S9b.md) | high | 0.6k / 0.6k | S9a | — | todo |
| S10 | [Release prep and parity audit](sessions/S10.md) | medium | — | all | — | todo |

Critical path: S1a → S2a → S2b/S3 → S4a → S4b → S4c → S4d → S5a → (S9a → S9b) → S10.
External TestFlight for the focus group — after S4e and S5a (diary usable, data importable from Android).
