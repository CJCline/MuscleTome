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

