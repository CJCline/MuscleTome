# Exercise Library

This document is the source of truth for how MuscleTome's exercise catalog
works: supported sources, identity and dedup rules, the movement-family model,
custom exercises, and media attribution. For the import pipeline details see
[canonical-exercise-import.md](canonical-exercise-import.md).

## Supported sources

| Source | `sourceKey` | Delivery | License |
|---|---|---|---|
| Seed catalog | — (BUILT_IN) | Bundled, seeded on first launch | App's own content |
| wger | `wger` | User-triggered network import (Settings) | CC, per-entry (author + license preserved) |
| free-exercise-db | `free_exercise_db` | Bundled curated asset (`assets/catalog/free_exercise_db.json`), revision-gated | Unlicense (public domain) |
| User | — (USER_CREATED) | Created in-app (Library → New exercise) | User's own content |

Every exercise has one canonical `exerciseId`. Exercises originating from a
source carry an `(sourceKey, externalExerciseId)` identity pair used for
dedup: re-importing the same record updates in place and never duplicates.
Canonical IDs are stable: `fedb_<dataset-id>` for free-exercise-db,
`wger_<wger-id>` (legacy) for wger, `user_<uuid>` for user-created.

## Identity and dedup rules

1. Exact `(sourceKey, externalExerciseId)` match → update in place
   (user-edited fields are protected, see below).
2. Exact normalized-name match across sources → queued for manual review
   (Merge / Keep Both / Discard). A recorded resolution suppresses repeat
   prompts; resolutions travel with backups.
3. No match → create with the source's canonical ID scheme.

### User-edit protection

Rows with `origin = USER_CREATED` or `isUserEdited = true` are never
overwritten by imports. A matching import produces a `Protected` result; the
user's fields (including notes and family assignment) win.

## Movement families

Families group comparable variations (e.g. "Barbell Back Squat" and "Goblet
Squat" both under **Squat**). Families are persisted rows in the
`movement_families` table (schema v10):

- `id`, `displayName`, `normalizedKey` (unique), `createdAtEpochMs`, `origin`
  (`BUILT_IN` for the eight legacy families — squat, bench_press, row,
  deadlift, overhead_press, curl, lunge, plank — `USER_CREATED` otherwise).
- Family names are normalized (trim, lowercase, collapse non-alphanumerics)
  before dedup: creating "Bench  Press!" when "Bench Press" exists surfaces the
  existing family instead of creating a duplicate.
- Assignment is optional. A custom exercise with no family is still valid,
  searchable, and selectable.
- `movementFamilyId` lives in the canonical-exercise ID namespace, separate
  from exercise IDs.

Imports assign families via the conservative name heuristics in
`MovementFamilies.familyId` (`MovementHeuristics`); a user's explicit
assignment always takes precedence and is never rewritten by an import.

## Custom exercises

Create: Library → **New exercise**. Required: name (unique) and primary muscle.
Everything else — description, form steps, secondary muscles, equipment,
movement type/pattern, difficulty, unilateral, family, media URI — optional.

Edit (Phase 5): the detail screen shows Edit/Delete actions only for
`USER_CREATED` / user-edited exercises. Editing rewrites the editable fields
in place: the canonical ID, source identities, and any routine/log references
stay untouched, and `isUserEdited` is forced on so future imports protect the
record.

Delete (documented decision): blocked with an explanation when the exercise
is referenced by routine slots or logged sessions — the app never orphans a
reference. Unreferenced custom exercises are hard-deleted (cascading to
canonical metadata, media, and cached images).

## Media attribution and licensing

Every imported media row stores `uri`, `sourceKey`, `attribution`, `creator`,
`licenseName`, and `licenseUrl`. The detail screen shows a distinct
attribution line (creator/attribution/license, deduplicated) for every image.

Images display from the network by default. On-demand offline caching is
available under a license allowlist (Unlicense, CC0, MIT, Apache-2.0, CC BY,
CC BY-SA) with a 256 MB capped, oldest-first-evicted cache. See
[canonical-exercise-import.md](canonical-exercise-import.md#phase-5--on-demand-media-cache)
for the full cache rules.

## Adding a new source adapter

1. **Parse**: create `data/remote/<Source>Models.kt` with `@Serializable`
   DTOs mirroring the upstream schema (one record type + an envelope).
2. **Normalize**: create `data/remote/<Source>ImportAdapter.kt` implementing
   the pure `normalize()` contract that produces a `NormalizedExerciseImport`.
   Blank names must be rejected (unusable identity). Reuse
   `MovementHeuristics` for pattern/type so cross-source corroboration stays
   consistent; add a curated muscle/equipment map and log unmapped values as
   diagnostics rather than inventing data. Media carries full provenance
   (attribution/creator/license) from the source.
3. **Register**: add the `sourceKey` to `ExerciseSources` (append-only enum
   value in `Enums.kt` — TEXT-stored, so old rows keep resolving). If the
   catalog is bundled, generate the asset with a `tools/curate_<source>.py`
   script (filter → sort → sha256 revision → envelope JSON) and add a
   revision-gated importer following `BundledCatalogImporter`.
4. **Test**: unit tests for the adapter (normal, sparse, unmapped taxonomy,
   blank-name rejection, determinism) plus a provenance round-trip extension
   in `BackupDocumentTest`. Batch behavior is verified via
   `CanonicalExerciseRepository.importBatch` instrumentation tests.

Source registry documentation lives in
[canonical-exercise-import.md](canonical-exercise-import.md#phase-4--supported-sources-and-catalog-expansion-bundled).
