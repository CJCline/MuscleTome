# Canonical Exercise and Import Contract (Phase 1)

## Identity and references

- `CanonicalExercise.id` is the stable exercise ID used by MuscleTome. Existing
  workout logs, routine slots, selection history, and other references continue
  to use the current `ExerciseEntity.id`; this phase does not rename or rewrite
  any persisted IDs.
- For compatibility, the current wger adapter uses a stable, source-namespaced
  ID (`wger_<external-id>`) already used by persisted wger `ExerciseEntity` rows.
  Future adapters may use a source-namespaced external ID only when it is stable
  and collision-safe; otherwise they must allocate a MuscleTome-generated ID.
  Never use an unqualified provider ID, and do not change existing IDs without
  a separately planned migration that preserves references.
- A source identity is the pair `(sourceKey, externalExerciseId)`. Re-importing
  that exact pair resolves to its existing canonical exercise ID even if source
  content changes. The source identity remains separate provenance; it does not
  replace or rewrite the canonical ID.
- One canonical exercise may have multiple source identities. Each meaningful
  variation remains its own canonical exercise with its own ID and may link to
  a shared `movementFamilyId` without being merged with its family members.
- A movement family is a curated, stable-ID grouping of comparable variations,
  not a muscle group or generic movement-pattern bucket. Family IDs are stored
  separately from display labels/localized names. Prefer explicit provider or
  curated assignments; name matching is a conservative fallback and unknown or
  ambiguous standalone movements stay ungrouped.

## Matching and protection

- Exact source identity is the only automatic identity match.
- Cross-source records with the same normalized display name and at least one
  corroborating match (movement pattern, equipment, or muscle targets) are
  review candidates. They are not automatically merged or overwritten.
- A similar name without corroborating attributes is not a candidate; import
  may create a distinct exercise. This deliberately errs toward preserving
  variations over destructive deduplication.
- Re-import decisions identify whether the existing exercise is user-created
  or locally edited. Adapters/import persistence must preserve those local edits
  rather than replacing them with provider values.
- Blank names cannot be normalized by the wger adapter and are rejected as
  unusable identity; missing optional descriptions, instructions, movement
  metadata, targets, equipment, provenance, or media do not invalidate a
  normalized record. Unmapped values can be accompanied by diagnostics.

## Media and legacy compatibility

- `ExerciseMedia` is a zero-or-more domain collection with media type, optional
  URI/reference, source, attribution/creator, and license metadata. Phase 1
  records references only; it does not fetch, bundle, or display external media.
- Existing `ExerciseEntity.demoUri` remains unchanged and continues to serve
  current callers. During the next schema/data migration phase, a nonblank
  `demoUri` should be mapped to one image-media row, preserving the URI and
  associating it with the exercise's known source where available. Do not clear
  the legacy column until that migration is verified.
- Built-in, imported, and user-created origin are represented distinctly in
  the normalized model. Existing persisted `ExerciseSource`, `isCustom`, and
  `createdByUserId` remain authoritative until a later migration establishes
  their canonical storage mapping.

## Import boundary

- Source adapters parse provider records and emit `NormalizedExerciseImport`;
  the normalized contract itself does not depend on wger types.
- `WgerImportAdapter` is an example mapping only. It is not wired into current
  persistence in this phase, so existing wger import behavior remains intact.
- The normalized exercise supports optional description/instructions,
  movement pattern/type, difficulty, unilateral flag, primary and secondary
  muscles, equipment, family ID, provenance, multiple media records, and
  diagnostics. Equipment/muscle sets may be empty and the primary target may
  be absent when the source value is unmapped.

## Schema and backup compatibility

Canonical exercise metadata persists `movementFamilyId` alongside the exercise
bundle. Backup documents include canonical metadata, source identities, pending
review rows, and resolution records with defaults so older backups remain
readable. Schema migrations must preserve exercise IDs and existing assignments.

Merge preserves the existing canonical exercise fields and adds source identity
provenance; media is unioned by source/URI while retaining attribution and
license data. Review candidates are deduplicated by source identity. A recorded
Keep Both, Merge, or Discard resolution suppresses repeat prompts for that
identity; resolving decisions are exported/restored with backup data.

# Phase 4 — Supported Sources and Catalog Expansion (bundled)

## Approved source registry

| | wger | free-exercise-db |
|---|---|---|
| `sourceKey` | `wger` | `free_exercise_db` |
| Identity | `(wger, <numeric wger id>)`; canonical id `wger_<id>` (legacy) | `(free_exercise_db, <dataset id>)`; canonical id `fedb_<id>` |
| Delivery | User-triggered network import (Settings) | Bundled curated asset, offline, deterministic |
| License | CC, per-entry (author + license preserved per record) | Unlicense (public domain) |
| Attribution | Per record: author + license in sourceAttribution/media | `free-exercise-db (yuhonas)`; courtesy credit to Ollie Jennings' exercises.json |
| Media | Main image URL (reference only, requires connectivity) | `https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/<id>/<n>.jpg` (reference only, requires connectivity) |
| Refresh | User re-triggers import; exact identity re-import is idempotent | Asset revision (sha256 of records) stored in `catalog_import` prefs; import runs only when revision changes |
| Provenance | `sourceUrl` = wger exerciseinfo endpoint | `sourceUrl` = GitHub repo record reference |

Unknown source keys are never labeled as a known provider: persist maps
unrecognized keys to the generic `EXTERNAL` `ExerciseSource`.

## free-exercise-db normalization rules (curated mapping)

Provider-native values are retained in diagnostics when a mapping is not exact.
Missing optional values remain absent with a diagnostic; nothing is invented.

- **Identity**: dataset `id` (underscore-cased, unique in dataset) is the
  external ID; canonical id is `fedb_<id>`. Blank `name` rejects the record as
  unusable identity.
- **Name**: `name`, trimmed.
- **Instructions**: `instructions` array, order preserved. Empty is allowed
  (diagnostic `missing_instructions`).
- **Muscles**: primary = first entry of `primaryMuscles` (dataset has exactly
  one multi-primary record; remaining entries join secondary). Mapping:
  `quadriceps→quads, shoulders→shoulders, abdominals→abs, chest→chest,
  hamstrings→hamstrings, triceps→triceps, biceps→biceps, lats→lats,
  middle back→rhomboids, calves→calves, lower back→back, glutes→glutes,
  traps→traps`. Unmapped (`forearms, neck, abductors, adductors`) records are
  **skipped by the bundled gate** (diagnostic `unmapped_muscle`); they are
  valid imports if ever imported deliberately.
- **Equipment**: `barbell→barbell, dumbbell→dumbbell, cable→cable,
  body only→bodyweight, machine→machine, kettlebells→kettlebell, bands→band,
  e-z curl bar→barbell`. Unmapped (`other, medicine ball, exercise ball,
  foam roll`) → empty set + diagnostic `unmapped_equipment`. `null`
  (unspecified) → empty set + diagnostic `unspecified_equipment` (never
  fabricated as bodyweight). Known limitation: exercises with an empty
  equipment set are treated as always-available by the selection engine.
- **Movement pattern**: the shared conservative name heuristics
  (`MovementHeuristics`, also used by wger) take precedence because they encode
  MuscleTome's taxonomy (squat/hinge/…) which the dataset's `force`
  (push/pull/static force direction) cannot express; `force` is only a fallback
  (`pull→PULL, push→PUSH`) when the name yields `OTHER`.
- **Movement type**: `mechanic` maps `isolation→ISOLATION, compound→COMPOUND`;
  null defaults to `COMPOUND` (documented default, same as `ExerciseEntity`).
- **Difficulty**: `level` maps `beginner→BEGINNER, intermediate→INTERMEDIATE,
  expert→ADVANCED`.
- **Unilateral**: not provided by the dataset; always null (never inferred from
  a name).
- **Family**: conservative `MovementFamilies.familyId(name, pattern)` fallback
  only; unknown stays null.
- **Description**: dataset has none; null.
- **Media**: every image becomes an `IMAGE` `ExerciseMedia` row with the
  raw.githubusercontent URI, `sourceKey=free_exercise_db`, attribution
  `free-exercise-db (yuhonas)`, license `Unlicense` + URL. References only —
  no fetch/bundle/display guarantee offline.
- **Diagnostics**: every unmapped or absent value above emits an
  `ImportDiagnostic` with the native source value.

## Bundled batch and refresh policy

- **Batch scope (approved)**: categories `strength`, `powerlifting`,
  `olympic weightlifting` — 627 of 876 records with mappable primary muscles;
  30 in-scope records skipped (21 forearms, 5 neck, 2 adductors, 2 abductors).
- The asset envelope records a `revision` = sha256 of the record list; the
  importer persists the applied revision and re-imports only on change.
- Import is per-record transactional: a malformed record cannot abort the
  batch, but the revision is only marked applied when no record failed
  (failed records retry on next launch; all writes are identity-idempotent).
- Five records match seed exercises by normalized name + corroborating
  attributes (Barbell Curl, Dumbbell Bench Press, Leg Press, Plank, Romanian
  Deadlift) and enter the import review queue per the candidate rule. No
  automatic merge.
- Re-running the pipeline never changes stable canonical IDs, never rewrites
  references, and preserves local edits per the merge/protection rules above.

## Known limitations (documented omissions)

- `shoulders` maps to the parent `shoulders` muscle group (dataset lumps delt
  heads); specific delt-head slots match these records only via the engine's
  widened-target fallback.
- No unilateral data; no descriptions; images require connectivity (consistent
  with wger media behavior).
- Dataset `id`s are name-derived and stable only as long as the upstream
  dataset keeps them stable; the `(sourceKey, externalExerciseId)` pair is the
  import identity and canonical IDs are never rewritten.
- The curation script (`tools/curate_free_exercise_db.py`) uses the network at
  dev time only; runtime import reads the committed asset.

