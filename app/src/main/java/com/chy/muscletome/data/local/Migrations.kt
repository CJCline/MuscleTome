package com.chy.muscletome.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object MuscleTomeMigrations {
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("UPDATE exercises SET source = 'WGER' WHERE SUBSTR(id, 1, 5) = 'wger_'")
            db.execSQL("CREATE TABLE IF NOT EXISTS `_new_users` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `weightUnit` TEXT NOT NULL, `defaultRestSeconds` INTEGER NOT NULL, `primaryMatchStrictness` TEXT NOT NULL, `preferCompoundEarly` INTEGER NOT NULL, `maxDifficulty` TEXT NOT NULL, `activeRoutineId` TEXT, PRIMARY KEY(`id`))")
            db.execSQL("INSERT INTO `_new_users` (`id`, `name`, `weightUnit`, `defaultRestSeconds`, `primaryMatchStrictness`, `preferCompoundEarly`, `maxDifficulty`) SELECT `id`, `name`, `weightUnit`, `defaultRestSeconds`, `primaryMatchStrictness`, `preferCompoundEarly`, `maxDifficulty` FROM `users`")
            db.execSQL("DROP TABLE `users`")
            db.execSQL("ALTER TABLE `_new_users` RENAME TO `users`")
        }
    }
    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `session_slot_results` ADD COLUMN `sessionNote` TEXT NOT NULL DEFAULT ''")
        }
    }
    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `routine_slots` ADD COLUMN `supersetGroupId` TEXT")
            db.execSQL("ALTER TABLE `session_slot_results` ADD COLUMN `supersetGroupId` TEXT")
            db.execSQL("ALTER TABLE `session_slot_results` ADD COLUMN `sortOrder` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `session_slot_results` ADD COLUMN `plannedSets` INTEGER")
            db.execSQL("UPDATE `session_slot_results` SET `sortOrder` = COALESCE((SELECT `orderIndex` FROM `routine_slots` WHERE `routine_slots`.`id` = `session_slot_results`.`routineSlotId`), 1000000)")
        }
    }
    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `users` ADD COLUMN `effortScale` TEXT NOT NULL DEFAULT 'RPE'")
        }
    }
    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `muscle_volume_targets` (`userId` TEXT NOT NULL, `muscleGroupId` TEXT NOT NULL, `weeklySetTarget` INTEGER, PRIMARY KEY(`userId`, `muscleGroupId`), FOREIGN KEY(`userId`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`muscleGroupId`) REFERENCES `muscle_groups`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_muscle_volume_targets_userId` ON `muscle_volume_targets` (`userId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_muscle_volume_targets_muscleGroupId` ON `muscle_volume_targets` (`muscleGroupId`)")
        }
    }
    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `canonical_exercises` (`exerciseId` TEXT NOT NULL, `primaryMuscleGroupId` TEXT, `instructions` TEXT NOT NULL DEFAULT '', `movementFamilyId` TEXT, `origin` TEXT NOT NULL DEFAULT 'BUILT_IN', `isUserEdited` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`exerciseId`), FOREIGN KEY(`exerciseId`) REFERENCES `exercises`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`primaryMuscleGroupId`) REFERENCES `muscle_groups`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_canonical_exercises_primaryMuscleGroupId` ON `canonical_exercises` (`primaryMuscleGroupId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_canonical_exercises_movementFamilyId` ON `canonical_exercises` (`movementFamilyId`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `exercise_secondary_targets` (`exerciseId` TEXT NOT NULL, `muscleGroupId` TEXT NOT NULL, PRIMARY KEY(`exerciseId`, `muscleGroupId`), FOREIGN KEY(`exerciseId`) REFERENCES `exercises`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`muscleGroupId`) REFERENCES `muscle_groups`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_exercise_secondary_targets_muscleGroupId` ON `exercise_secondary_targets` (`muscleGroupId`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `exercise_instructions` (`exerciseId` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, `instruction` TEXT NOT NULL, PRIMARY KEY(`exerciseId`, `sortOrder`), FOREIGN KEY(`exerciseId`) REFERENCES `exercises`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_exercise_instructions_exerciseId` ON `exercise_instructions` (`exerciseId`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `exercise_equipment_links` (`exerciseId` TEXT NOT NULL, `equipmentId` TEXT NOT NULL, PRIMARY KEY(`exerciseId`, `equipmentId`), FOREIGN KEY(`exerciseId`) REFERENCES `exercises`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`equipmentId`) REFERENCES `equipment`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_exercise_equipment_links_equipmentId` ON `exercise_equipment_links` (`equipmentId`)")
            db.execSQL("INSERT OR IGNORE INTO `exercise_equipment_links` (`exerciseId`, `equipmentId`) SELECT `exerciseId`, `equipmentId` FROM `exercise_equipment`")
            db.execSQL("CREATE TABLE IF NOT EXISTS `exercise_media` (`id` TEXT NOT NULL, `exerciseId` TEXT NOT NULL, `type` TEXT NOT NULL, `uri` TEXT NOT NULL, `sourceKey` TEXT, `attribution` TEXT, `creator` TEXT, `licenseName` TEXT, `licenseUrl` TEXT, `sortOrder` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`id`), FOREIGN KEY(`exerciseId`) REFERENCES `exercises`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_exercise_media_exerciseId` ON `exercise_media` (`exerciseId`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `exercise_source_identities` (`sourceKey` TEXT NOT NULL, `externalExerciseId` TEXT NOT NULL, `exerciseId` TEXT NOT NULL, `sourceUrl` TEXT, `importedAtEpochMs` INTEGER, `updatedAtEpochMs` INTEGER, PRIMARY KEY(`sourceKey`, `externalExerciseId`), FOREIGN KEY(`exerciseId`) REFERENCES `exercises`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_exercise_source_identities_exerciseId` ON `exercise_source_identities` (`exerciseId`)")
            db.execSQL("INSERT INTO `canonical_exercises` (`exerciseId`, `primaryMuscleGroupId`, `instructions`, `movementFamilyId`, `origin`, `isUserEdited`) SELECT `id`, `primaryMuscleGroupId`, '', NULL, CASE WHEN `isCustom` = 1 OR `source` = 'USER_CREATED' THEN 'USER_CREATED' WHEN `source` = 'WGER' THEN 'IMPORTED' ELSE 'BUILT_IN' END, CASE WHEN `isCustom` = 1 OR `source` = 'USER_CREATED' THEN 1 ELSE 0 END FROM `exercises`")
            db.execSQL("INSERT OR IGNORE INTO `exercise_secondary_targets` (`exerciseId`, `muscleGroupId`) SELECT `exerciseId`, `muscleGroupId` FROM `exercise_secondary_muscles`")
            db.execSQL("INSERT OR IGNORE INTO `exercise_media` (`id`, `exerciseId`, `type`, `uri`, `sourceKey`, `attribution`, `creator`, `licenseName`, `licenseUrl`, `sortOrder`) SELECT 'legacy_demo_' || `id`, `id`, 'IMAGE', `demoUri`, CASE WHEN `source` = 'WGER' THEN 'wger' ELSE NULL END, `notes`, NULL, NULL, NULL, 0 FROM `exercises` WHERE `demoUri` IS NOT NULL AND TRIM(`demoUri`) != ''")
            db.execSQL("INSERT OR IGNORE INTO `exercise_source_identities` (`sourceKey`, `externalExerciseId`, `exerciseId`, `sourceUrl`, `importedAtEpochMs`, `updatedAtEpochMs`) SELECT 'wger', SUBSTR(`id`, 6), `id`, NULL, NULL, NULL FROM `exercises` WHERE `source` = 'WGER' AND SUBSTR(`id`, 1, 5) = 'wger_'")
        }
    }
    private val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `pending_exercise_imports` (`id` TEXT NOT NULL, `sourceKey` TEXT, `externalExerciseId` TEXT, `displayName` TEXT NOT NULL, `pattern` TEXT, `muscleIdsJson` TEXT NOT NULL, `equipmentIdsJson` TEXT NOT NULL, `payloadJson` TEXT NOT NULL, `candidateExerciseIdsJson` TEXT NOT NULL, `createdAtEpochMs` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS `exercise_import_resolutions` (`pendingImportId` TEXT NOT NULL, `action` TEXT NOT NULL, `resolvedExerciseId` TEXT, `resolvedAtEpochMs` INTEGER NOT NULL, PRIMARY KEY(`pendingImportId`), FOREIGN KEY(`resolvedExerciseId`) REFERENCES `exercises`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_exercise_import_resolutions_resolvedExerciseId` ON `exercise_import_resolutions` (`resolvedExerciseId`)")
            db.execSQL("UPDATE canonical_exercises SET movementFamilyId = CASE WHEN exerciseId IN ('squat', 'leg_press', 'leg_extension') THEN 'squat' WHEN exerciseId IN ('barbell_bench_press', 'dumbbell_bench_press', 'push_up', 'cable_fly') THEN 'bench_press' WHEN exerciseId IN ('barbell_row', 'pull_up', 'lat_pulldown') THEN 'row' WHEN exerciseId IN ('deadlift', 'romanian_deadlift') THEN 'deadlift' WHEN exerciseId = 'overhead_press' THEN 'overhead_press' WHEN exerciseId = 'barbell_curl' THEN 'curl' WHEN exerciseId = 'plank' THEN 'plank' ELSE movementFamilyId END")
            db.execSQL("UPDATE canonical_exercises SET movementFamilyId = NULL WHERE movementFamilyId NOT IN ('squat', 'bench_press', 'row', 'deadlift', 'overhead_press', 'curl', 'lunge', 'plank')")
        }
    }
    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
}
