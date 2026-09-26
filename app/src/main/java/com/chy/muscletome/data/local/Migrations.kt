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

    val ALL: Array<Migration> = arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
    )
}
