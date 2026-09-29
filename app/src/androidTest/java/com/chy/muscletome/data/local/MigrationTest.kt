package com.chy.muscletome.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(), MuscleTomeDatabase::class.java,
    )

    @Test fun migrate9To10SeedsFamiliesAndCreatesMediaCache() {
        val db = helper.createDatabase("migration-9-10", 9)
        // Exercise with a legacy family assignment + one media row, in v9 shape.
        db.execSQL(
            "INSERT INTO canonical_exercises (exerciseId, primaryMuscleGroupId, instructions, movementFamilyId, origin, isUserEdited) " +
                "VALUES ('barbell_bench_press', 'chest', '', 'bench_press', 'BUILT_IN', 0)",
        )
        db.close()

        helper.runMigrationsAndValidate("migration-9-10", 10, true, MuscleTomeMigrations.MIGRATION_9_10).use { migrated ->
            migrated.query(SimpleSQLiteQuery("SELECT id, displayName, normalizedKey, origin FROM movement_families ORDER BY id")).use { cursor ->
                val rows = mutableListOf<List<String>>()
                while (cursor.moveToNext()) rows += listOf(cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getString(3))
                assertEquals(8, rows.size)
                assertTrue(rows.containsAll(listOf(
                    listOf("squat", "Squat", "squat", "BUILT_IN"),
                    listOf("bench_press", "Bench Press", "bench press", "BUILT_IN"),
                    listOf("plank", "Plank", "plank", "BUILT_IN"),
                )))
            }
            // Family assignment survives.
            migrated.query(SimpleSQLiteQuery("SELECT movementFamilyId FROM canonical_exercises WHERE exerciseId = 'barbell_bench_press'")).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("bench_press", cursor.getString(0))
            }
            // Media cache table exists with the expected shape.
            migrated.query(SimpleSQLiteQuery("PRAGMA table_info(exercise_media_cache)")).use { cursor ->
                val columns = mutableSetOf<String>()
                while (cursor.moveToNext()) columns += cursor.getString(1)
                assertTrue(columns.containsAll(setOf("mediaId", "localPath", "fetchedAtEpochMs", "bytes", "licenseCheckedAtEpochMs")))
            }
        }
    }

    @Test fun migrateAllFromV1PreservesData() {
        val db = helper.createDatabase("migration-test", 1)
        db.execSQL("INSERT INTO users (id, name, weightUnit, defaultRestSeconds, primaryMatchStrictness, preferCompoundEarly, maxDifficulty, subscriptionStatus) VALUES ('local-user', 'You', 'KG', 90, 'STRICT', 1, 'ADVANCED', 'FREE')")
        db.execSQL("INSERT INTO muscle_groups (id, name, parentGroupId) VALUES ('chest', 'Chest', NULL)")
        db.execSQL("INSERT INTO exercises (id, name, description, movementPattern, movementType, primaryMuscleGroupId, difficulty, isCustom, createdByUserId, unilateral, source, notes, demoUri) VALUES ('barbell_bench_press', 'Bench Press', '', 'PUSH', 'COMPOUND', 'chest', 'INTERMEDIATE', 0, NULL, 0, 'SEED', '', 'https://legacy.example/demo.png')")
        db.close()

        helper.runMigrationsAndValidate("migration-test", 10, true, *MuscleTomeMigrations.ALL).use { migrated ->
            migrated.query(SimpleSQLiteQuery("SELECT movementFamilyId FROM canonical_exercises WHERE exerciseId = 'barbell_bench_press'")).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("bench_press", cursor.getString(0))
            }
            migrated.query(SimpleSQLiteQuery("SELECT uri FROM exercise_media WHERE exerciseId = 'barbell_bench_press'")).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("https://legacy.example/demo.png", cursor.getString(0))
            }
            migrated.query(SimpleSQLiteQuery("SELECT COUNT(*) FROM pending_exercise_imports")).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
            migrated.query(SimpleSQLiteQuery("PRAGMA table_info(exercise_import_resolutions)")).use { cursor ->
                val columns = mutableSetOf<String>()
                while (cursor.moveToNext()) columns += cursor.getString(1)
                assertTrue("sourceKey" in columns)
                assertTrue("externalExerciseId" in columns)
            }
        }
    }
}
