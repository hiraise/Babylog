# S1a — Skeleton, tooling, CI

## Deliverables

- Write ADR-0051 from «Architecture» in `ios/AGENTS.md` (Proposed → owner accepts), then replace that section with a link.
- `ios/BabyLog.xcodeproj` (synchronized folders), `BabyLogApp`, empty root view, `AppContainer`, `Clock`.
  SPM: GRDB, ZIPFoundation, swift-snapshot-testing (test target only). iOS 17, iPhone only, orientations as Android (not locked).
- `ios/Version.xcconfig` = `versionName`/`versionCode`; `scripts/dev.sh rules` checks it.
- Info.plist: `ITSAppUsesNonExemptEncryption = NO`, display name «Дневник малыша».
- Signing per ADR-0049: `DEVELOPMENT_TEAM` (from S0) in `ios/Signing.xcconfig`, optional untracked
  `ios/Local.xcconfig` included after it; `.gitignore`: `ios/Local.xcconfig`, `xcuserdata/`, `*.p12`, `*.cer`,
  `*.mobileprovision`, `*.p8`, `*.xcarchive`. CI and tests build with `CODE_SIGNING_ALLOWED=NO`.
- Shared resources by reference (not copied): `app/src/main/res/font/*.ttf` (`UIAppFonts`),
  `app/src/main/assets/development/*.json`.
- `scripts/dev.sh`: `ios` (build + test on pinned simulator), `ios-goldens`, `ios-run`. Pin the Xcode version in one place.
- `.github/workflows/ios.yml` on a macOS runner, same pinned Xcode; job always runs (stays a required
  check) but exits early when the diff touches nothing in `ios/`, shared resources, `app/schemas/`, `fixtures/`, `scripts/dev.sh`.
- `.github/dependabot.yml`: `swift` ecosystem for `ios/`. `.agents/skills/pr`: iOS check and simulator showing.
- `docs/development.md`: «iOS» section (build, run, goldens).
- Owner after merge: archive and upload to **internal** TestFlight to prove signing works.

## Android references

- `AppContainer.kt` and `core/ui/Clock.kt` for DI and time conventions.
