# S1b — Theme and core components

## Port and implement

- Port `core/ui/theme/*` (including `Oklch.kt` colour math, `DarkPalettes.kt`, `ChartColors`, `TimerColors`,
  `IllnessColors`), `core/ui/components/*` except `chart/` and `LightDarkPreview`, `EventIcons.kt`, `Format.kt`.
- Icons: script `scripts/ios-icons.sh` converts `app/src/main/res/drawable/ic_*.xml` → SVG in
  `ios/BabyLog/Assets.xcassets` (preserve vector data); `rules` fails if generated icons are stale.
  App icon from `ic_launcher_foreground.xml`.
- Extend `check_design_tokens` to `*.swift` (no raw colours, sizes, fonts outside `Core/UI/Theme`).
- Tests: port `core/ui/theme` tests; snapshots of every component, light + dark.

## Additional ADRs

- [0010](../../docs/adr/0010-design-system.md), [0014](../../docs/adr/0014-own-icons.md),
  [0018](../../docs/adr/0018-bottom-sheets.md), [0032](../../docs/adr/0032-dark-palettes.md),
  [0044](../../docs/adr/0044-dark-classic-default.md).
