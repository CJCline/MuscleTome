package com.chy.muscletome.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * All Room migrations, registered via `addMigrations(*ALL)` in `DatabaseModule`.
 *
 * There is intentionally **no** destructive fallback: bumping the database
 * version without adding a matching migration here crashes at runtime
 * instead of silently wiping a user's workout history.
 *
 * Workflow for any entity/converter change:
 *  1. Bump `MuscleTomeDatabase`'s `version`.
 *  2. Add the matching `Migration` here with the exact SQL.
 *  3. Rebuild — KSP writes the new schema JSON to `app/schemas/`; commit it
 *     together with the code change (CI fails if the JSON drifts).
 */
object MuscleTomeMigrations {

    /**
     * v1 → v2:
     *  - `exercises.source`: wger imports (id prefix `wger_`) were stored as
     *    SEED before source tagging existed — retag them as WGER so library
     *    filters, attribution and a future "reset seed catalog" can tell them
     *    apart from the bundled seed data.
     *  - `users`: drop the unused subscription entitlement columns
     *    (`subscriptionStatus`, `subscriptionExpiryEpochMs`,
     *    `lastVerifiedEntitlementEpochMs`) — nothing gates on them yet, and
     *    dead billing state must not leak into device backups — and add the
     *    `activeRoutineId` pointer that scopes Home's "Up next" to one routine.
     */
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "UPDATE exercises SET source = 'WGER' WHERE SUBSTR(id, 1, 5) = 'wger_'",
            )
            // Table rebuild rather than ALTER TABLE DROP COLUMN: DROP COLUMN
            // needs SQLite 3.35 (Android 15+), but minSdk is 26.
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `_new_users` (" +
                    "`id` TEXT NOT NULL, `name` TEXT NOT NULL, `weightUnit` TEXT NOT NULL, " +
                    "`defaultRestSeconds` INTEGER NOT NULL, `primaryMatchStrictness` TEXT NOT NULL, " +
                    "`preferCompoundEarly` INTEGER NOT NULL, `maxDifficulty` TEXT NOT NULL, " +
                    "`activeRoutineId` TEXT, PRIMARY KEY(`id`))",
            )
            db.execSQL(
                "INSERT INTO `_new_users` (`id`, `name`, `weightUnit`, `defaultRestSeconds`, " +
                    "`primaryMatchStrictness`, `preferCompoundEarly`, `maxDifficulty`) " +
                    "SELECT `id`, `name`, `weightUnit`, `defaultRestSeconds`, " +
                    "`primaryMatchStrictness`, `preferCompoundEarly`, `maxDifficulty` FROM `users`",
            )
            db.execSQL("DROP TABLE `users`")
            db.execSQL("ALTER TABLE `_new_users` RENAME TO `users`")
        }
    }

    /**
     * v2 → v3: session-specific exercise notes. `session_slot_results` gains
     * a `sessionNote` column — remarks about the exercise *in this workout*,
     * kept apart from the shared "exercise cues" in `exercises.notes`.
     */
    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE `session_slot_results` " +
                    "ADD COLUMN `sessionNote` TEXT NOT NULL DEFAULT ''",
            )
        }
    }

    /**
     * v3 → v4: superset/circuit support.
     *  - `routine_slots.supersetGroupId`: planned grouping, by id (not
     *    adjacency) so reordering rows never breaks a group.
     *  - `session_slot_results.supersetGroupId`: session snapshot of the
     *    grouping; ad-hoc supersets created mid-workoot live here only.
     *  - `session_slot_results.sortOrder`: explicit result ordering decoupled
     *    from the (editable) routine slot order; backfilled from it.
     *  - `session_slot_results.plannedSets`: planned sets for ad-hoc rows
     *    with no routine slot; null for slot-backed rows.
     */
    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE `routine_slots` ADD COLUMN `supersetGroupId` TEXT",
            )
            db.execSQL(
                "ALTER TABLE `session_slot_results` ADD COLUMN `supersetGroupId` TEXT",
            )
            db.execSQL(
                "ALTER TABLE `session_slot_results` ADD COLUMN `sortOrder` INTEGER NOT NULL DEFAULT 0",
            )
            db.execSQL(
                "ALTER TABLE `session_slot_results` ADD COLUMN `plannedSets` INTEGER",
            )
            // Preserve the order the user trained in: the session used to be
            // presented by slot orderIndex, so snapshot that into sortOrder.
            // Orphaned results (slot deleted) fall back to the far end.
            db.execSQL(
                "UPDATE `session_slot_results` SET `sortOrder` = " +
                    "COALESCE((SELECT `orderIndex` FROM `routine_slots` WHERE `routine_slots`.`id` = `session_slot_results`.`routineSlotId`), 1000000)",
            )
        }
    }

    /**
     * v4 → v5: effort scale preference. `users.effortScale` picks which
     * scale (RPE or RIR) the workout UI speaks; RPE stays the stored canon
     * (RIR is derived as 10 − RPE), so this is presentation-only state.
     */
    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE `users` ADD COLUMN `effortScale` TEXT NOT NULL DEFAULT 'RPE'",
            )
        }
    }

    /**
     * v5 → v6: weekly volume targets per muscle. `muscle_volume_targets`
     * holds the user's weekly set goal for a muscle group; no row = no
     * target. Volume history itself is derived from set_logs and never
     * stored — only the goal lives here.
     */
    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `muscle_volume_targets` (" +
                    "`userId` TEXT NOT NULL, `muscleGroupId` TEXT NOT NULL, " +
                    "`weeklySetTarget` INTEGER, " +
                    "PRIMARY KEY(`userId`, `muscleGroupId`), " +
                    "FOREIGN KEY(`userId`) REFERENCES `users`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE , " +
                    "FOREIGN KEY(`muscleGroupId`) REFERENCES `muscle_groups`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_muscle_volume_targets_userId` " +
                    "ON `muscle_volume_targets` (`userId`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_muscle_volume_targets_muscleGroupId` " +
                    "ON `muscle_volume_targets` (`muscleGroupId`)",
            )
        }
    }

    val ALL: Array<Migration> = arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6,
    )
}
