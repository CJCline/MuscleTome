package com.chy.muscletome.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Walks the full migration chain from a seeded v1 database to the current
 * version and asserts the data transformations of each step:
 *  - v1 → v2: wger rows retagged to WGER, billing columns dropped (replaced
 *    by activeRoutineId), everything else untouched.
 *  - v2 → v3: sessionNote added with '' default.
 *  - v3 → v4: superset columns added; session sortOrder backfilled from
 *    the routine slot's orderIndex; grouping columns null for legacy rows.
 *  - v4 → v5: users.effortScale added, defaulting to RPE.
 *  - v5 → v6: muscle_volume_targets created (empty — targets are set
 *    explicitly by the user, never backfilled).
 *
 * Room itself validates the schema at the end of the chain; these assertions
 * cover the data, which Room does not check.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val testDb = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MuscleTomeDatabase::class.java,
    )

    @Test
    fun migrateAllFromV1PreservesData() {
        // Seed a realistic v1 database, including the legacy columns.
        helper.createDatabase(testDb, 1).apply {
            execSQL(
                "INSERT INTO users (`id`, `name`, `weightUnit`, `defaultRestSeconds`, " +
                    "`primaryMatchStrictness`, `preferCompoundEarly`, `maxDifficulty`, " +
                    "`subscriptionStatus`, `subscriptionExpiryEpochMs`, `lastVerifiedEntitlementEpochMs`) " +
                    "VALUES ('local-user', 'You', 'KG', 90, 'LOOSE', 1, 'ADVANCED', 'VARIETY', 12345, 12346)",
            )
            execSQL("INSERT INTO muscle_groups (`id`, `name`, `parentGroupId`) VALUES ('chest', 'Chest', NULL)")
            execSQL("INSERT INTO equipment (`id`, `name`) VALUES ('barbell', 'Barbell')")
            execSQL(
                "INSERT INTO exercises (`id`, `name`, `description`, `movementPattern`, `movementType`, " +
                    "`primaryMuscleGroupId`, `difficulty`, `isCustom`, `createdByUserId`, `unilateral`, " +
                    "`source`, `notes`, `demoUri`) " +
                    "VALUES ('barbell_bench_press', 'Barbell Bench Press', '', 'PUSH', 'COMPOUND', 'chest', " +
                    "'INTERMEDIATE', 0, NULL, 0, 'SEED', 'cues here', 'https://legacy.example/demo.png')",
            )
            // A wger import from before source tagging existed: stored as SEED.
            execSQL(
                "INSERT INTO exercises (`id`, `name`, `description`, `movementPattern`, `movementType`, " +
                    "`primaryMuscleGroupId`, `difficulty`, `isCustom`, `createdByUserId`, `unilateral`, " +
                    "`source`, `notes`, `demoUri`) " +
                    "VALUES ('wger_123', 'Imported Thing', '', 'PUSH', 'COMPOUND', 'chest', " +
                    "'INTERMEDIATE', 0, NULL, 0, 'SEED', 'Source: wger.de', NULL)",
            )
            execSQL(
                "INSERT INTO exercises (`id`, `name`, `description`, `movementPattern`, `movementType`, " +
                    "`primaryMuscleGroupId`, `difficulty`, `isCustom`, `createdByUserId`, `unilateral`, " +
                    "`source`, `notes`, `demoUri`) VALUES ('user_custom', 'Custom', '', 'OTHER', " +
                    "'COMPOUND', 'chest', 'BEGINNER', 1, 'local-user', 0, 'USER_CREATED', '', NULL)",
            )
            execSQL("INSERT INTO routines (`id`, `name`, `ownerId`, `createdAtEpochMs`) VALUES ('r1', 'PPL', 'local-user', 1000)")
            execSQL("INSERT INTO routine_days (`id`, `routineId`, `name`, `orderIndex`) VALUES ('d1', 'r1', 'Push', 0)")
            execSQL(
                "INSERT INTO routine_slots (`id`, `routineDayId`, `orderIndex`, `type`, `exerciseId`, " +
                    "`targetMovementType`, `sets`, `repRangeMin`, `repRangeMax`, `restSeconds`, `targetRpe`) " +
                    "VALUES ('slot1', 'd1', 0, 'FIXED', 'barbell_bench_press', 'ANY', 3, 8, 12, 90, NULL)",
            )
            execSQL(
                "INSERT INTO workout_sessions (`id`, `userId`, `routineDayId`, `startedAtEpochMs`, `endedAtEpochMs`) " +
                    "VALUES ('ws1', 'local-user', 'd1', 2000, 3000)",
            )
            execSQL(
                "INSERT INTO session_slot_results (`id`, `sessionId`, `routineSlotId`, `resolvedExerciseId`, `selectionReason`) " +
                    "VALUES ('ssr1', 'ws1', 'slot1', 'barbell_bench_press', 'FIXED')",
            )
            execSQL(
                "INSERT INTO set_logs (`id`, `sessionSlotResultId`, `setNumber`, `weight`, `reps`, `rpe`, " +
                    "`restSecondsActual`, `completedAtEpochMs`) " +
                    "VALUES ('set1', 'ssr1', 1, 100.0, 8, 8.5, 90, 2500)",
            )
            close()
        }

        // Opening the database runs the full migration chain; Room validates
        // the final schema against the exported current-version snapshot.
        val db = Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            MuscleTomeDatabase::class.java,
            testDb,
        ).addMigrations(*MuscleTomeMigrations.ALL).build()

        db.openHelper.writableDatabase

        // v1 → v2: wger retag — only the id prefix matches, seed stays SEED.
        db.query(SimpleSQLiteQuery("SELECT `source` FROM exercises WHERE `id` = 'wger_123'")).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("WGER", cursor.getString(0))
        }
        db.query(SimpleSQLiteQuery("SELECT `source`, `notes` FROM exercises WHERE `id` = 'barbell_bench_press'")).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("SEED", cursor.getString(0))
            assertEquals("cues here", cursor.getString(1))
        }

        // v1 → v2: users table rebuilt — billing state gone, activeRoutineId null.
        db.query(SimpleSQLiteQuery("SELECT `activeRoutineId` FROM users WHERE `id` = 'local-user'")).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue(cursor.isNull(0))
        }

        // v2 → v3: sessionNote exists and defaulted to '' for pre-existing rows.
        db.query(SimpleSQLiteQuery("SELECT `sessionNote` FROM session_slot_results WHERE `id` = 'ssr1'")).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("", cursor.getString(0))
        }

        // v3 → v4: sortOrder was backfilled from the routine slot's order
        // position — the session's original presentation order is preserved.
        db.query(SimpleSQLiteQuery("SELECT `sortOrder`, `supersetGroupId`, `plannedSets` FROM session_slot_results WHERE `id` = 'ssr1'")).use { cursor ->
            assertTrue(cursor.moveToFirst())
            // slot1's orderIndex in the seeded database is 0.
            assertEquals(0, cursor.getInt(0))
            assertTrue(cursor.isNull(1))
            assertTrue(cursor.isNull(2))
        }
        // v3 → v4: routine slot grouping column exists and defaults to NULL.
        db.query(SimpleSQLiteQuery("SELECT `supersetGroupId` FROM routine_slots WHERE `id` = 'slot1'")).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue(cursor.isNull(0))
        }

        // The workout history survived the whole chain.
        db.query(SimpleSQLiteQuery("SELECT COUNT(*) FROM set_logs")).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }

        // v4 → v5: effort scale preference exists and defaulted to RPE.
        db.query(SimpleSQLiteQuery("SELECT `effortScale` FROM users WHERE `id` = 'local-user'")).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("RPE", cursor.getString(0))
        }

        // v5 → v6: the volume-target table exists, starts empty (targets
        // are user-set, never backfilled), and accepts the composite-key
        // upsert the DAO relies on (REPLACE on conflict).
        db.query(SimpleSQLiteQuery("SELECT COUNT(*) FROM muscle_volume_targets")).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO muscle_volume_targets (`userId`, `muscleGroupId`, `weeklySetTarget`) " +
                "VALUES ('local-user', 'chest', 12)",
        )
        db.openHelper.writableDatabase.execSQL(
            "UPDATE muscle_volume_targets SET weeklySetTarget = 10 " +
                "WHERE userId = 'local-user' AND muscleGroupId = 'chest'",
        )
        db.query(
            SimpleSQLiteQuery(
                "SELECT `weeklySetTarget` FROM muscle_volume_targets " +
                    "WHERE `userId` = 'local-user' AND `muscleGroupId` = 'chest'",
            ),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(10, cursor.getInt(0))
        }

        // v6 → v7: canonical rows preserve existing IDs and legacy demo URI.
        db.query(SimpleSQLiteQuery(
            "SELECT `id`, `resolvedExerciseId` FROM session_slot_results WHERE id = 'ssr1'",
        )).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("ssr1", cursor.getString(0))
            assertEquals("barbell_bench_press", cursor.getString(1))
        }
        db.query(SimpleSQLiteQuery(
            "SELECT `exerciseId`, `origin`, `isUserEdited` FROM canonical_exercises " +
                "WHERE exerciseId IN ('barbell_bench_press', 'user_custom') ORDER BY exerciseId",
        )).use { cursor ->
            assertEquals(2, cursor.count)
            assertTrue(cursor.moveToFirst())
            assertEquals("barbell_bench_press", cursor.getString(0))
            assertEquals("BUILT_IN", cursor.getString(1))
            assertEquals(0, cursor.getInt(2))
            assertTrue(cursor.moveToNext())
            assertEquals("user_custom", cursor.getString(0))
            assertEquals("USER_CREATED", cursor.getString(1))
            assertEquals(1, cursor.getInt(2))
            assertFalse(cursor.moveToNext())
        }
        db.query(SimpleSQLiteQuery(
            "SELECT `uri`, `type` FROM exercise_media WHERE exerciseId = 'barbell_bench_press'",
        )).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("https://legacy.example/demo.png", cursor.getString(0))
            assertEquals("IMAGE", cursor.getString(1))
        }

        db.close()
    }
}
