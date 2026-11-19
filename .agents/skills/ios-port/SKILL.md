---
name: ios-port
description: Execute exactly one BabyLog iOS porting session by its ID
whenToUse: When the user asks to execute an iOS porting step such as S1a, S2a, or S10
argument-hint: "[session ID, for example S1a]"
---

# Execute one iOS porting session

Session ID: `$ARGUMENTS`.

If the ID is missing, is not exactly one ID, is `S0`, or has no matching file, stop and ask the owner for a valid implementation session ID.

1. Read `ios/AGENTS.md` and only `ios/sessions/<ID>.md`. Do not read `CLAUDE.md`, other session files, or explore the whole repository.
2. Work sequentially in the clean main checkout, on a branch created from the base in `ios/AGENTS.md`. Never discard another task's uncommitted changes and do not create worktrees.
3. In `ios/PLAN.md`, read only the status table and roadmap. Check the session's `Needs`; stop and report a blocker if a required session is not `done`.
4. Read the common ADRs named in `ios/AGENTS.md`, the additional ADRs in the session file, and only the Android sources and tests named there.
5. Implement the complete session, including its tests and documentation. Keep Android as the behaviour reference and preserve the mirrored file layout.
6. Run targeted checks while working. Run the complete session checks once the implementation is ready; do not mark work done with failing or missing checks.
7. Update only this session's status row in `ios/PLAN.md` in the PR that finishes it. Keep status out of the session specification file.
8. Use the `pr` skill for the owner demonstration, PR, and CI. Open the PR against the base from `ios/AGENTS.md`, only after the owner's approval. Do not merge unless the owner explicitly asks and the project process allows it.
9. Finish with a short report: implemented scope, checks, PR, remaining blockers. Later sessions consume the Git history and status table, not a handoff transcript.
