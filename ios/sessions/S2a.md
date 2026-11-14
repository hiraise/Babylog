# S2a — Model, GRDB schema, repository

## Port and implement

- Port `core/model/*`, `core/data/DiaryRepository.kt`, `core/data/room/*` → GRDB.
- Create the schema of the latest `app/schemas/**/7.json` directly as GRDB migration `v7` (no 1→6 history);
  test compares the created schema with the JSON (tables, columns, types, indices, FKs).
  Future Room migration → GRDB migration.
- Port `RoomDiaryRepositoryTest`, `core/model` tests, `testing/Fakes.kt`.

## Additional ADRs

- [0008](../../docs/adr/0008-data-model-children.md), [0015](../../docs/adr/0015-feeding-timer.md),
  [0016](../../docs/adr/0016-child-sex.md), [0024](../../docs/adr/0024-event-photos.md),
  [0025](../../docs/adr/0025-bottle-feeding.md), [0027](../../docs/adr/0027-height.md),
  [0030](../../docs/adr/0030-crying.md), [0031](../../docs/adr/0031-notes.md),
  [0040](../../docs/adr/0040-illness.md), [0041](../../docs/adr/0041-temperature.md).
