package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

import com.example.survey.data.SurveyCalibrationEntity
import com.example.survey.data.SurveyDao
import com.example.survey.data.SurveyMarkerEntity
import com.example.survey.data.SurveyPointEntity
import com.example.survey.data.SurveySessionEntity

@Database(
    entities = [
        PhotoRecordEntity::class,
        RouteEntity::class,
        HighwayProjectEntity::class,
        SmartActivityEntity::class,
        QcRecordEntity::class,
        DailyProjectReportEntity::class,
        SurveySessionEntity::class,
        SurveyPointEntity::class,
        SurveyMarkerEntity::class,
        SurveyCalibrationEntity::class
    ],
    version = 12,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun photoRecordDao(): PhotoRecordDao
    abstract fun routeDao(): RouteDao
    abstract fun highwayProjectDao(): HighwayProjectDao
    abstract fun smartActivityDao(): SmartActivityDao
    abstract fun qcRecordDao(): QcRecordDao
    abstract fun dailyReportRecordDao(): DailyReportRecordDao
    abstract fun surveyDao(): SurveyDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE smart_activities ADD COLUMN subActivityName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE smart_activities ADD COLUMN subFromChainage TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE smart_activities ADD COLUMN subToChainage TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE smart_activities ADD COLUMN subLengthM REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE smart_activities ADD COLUMN subWidthM REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE smart_activities ADD COLUMN subComments TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE smart_activities ADD COLUMN sideMode TEXT NOT NULL DEFAULT 'Auto'")
                db.execSQL("ALTER TABLE smart_activities ADD COLUMN isSideAutoDetected INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE smart_activities ADD COLUMN depthM REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE smart_activities ADD COLUMN subDepthM REAL NOT NULL DEFAULT 0.0")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `survey_sessions` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `name` TEXT NOT NULL,
                        `projectName` TEXT NOT NULL,
                        `highwayName` TEXT NOT NULL,
                        `roadNumber` TEXT NOT NULL,
                        `stretchName` TEXT NOT NULL,
                        `surveyDate` TEXT NOT NULL,
                        `startChainageMetres` INTEGER NOT NULL,
                        `currentChainageMetres` INTEGER NOT NULL,
                        `direction` TEXT NOT NULL,
                        `lane` TEXT NOT NULL,
                        `customLaneName` TEXT NOT NULL,
                        `centrelineOffsetMetres` REAL NOT NULL,
                        `offsetSide` TEXT NOT NULL,
                        `isOffsetAuto` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        `totalDistanceTravelledMetres` REAL NOT NULL,
                        `currentSegmentIndex` INTEGER NOT NULL,
                        `lastLatitude` REAL,
                        `lastLongitude` REAL,
                        `lastAltitude` REAL,
                        `lastSpeedMps` REAL,
                        `lastBearing` REAL,
                        `lastAccuracy` REAL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `lastSavedAt` INTEGER NOT NULL
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `survey_points` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `sessionId` TEXT NOT NULL,
                        `segmentIndex` INTEGER NOT NULL,
                        `latitude` REAL NOT NULL,
                        `longitude` REAL NOT NULL,
                        `altitude` REAL NOT NULL,
                        `accuracy` REAL NOT NULL,
                        `speedMps` REAL NOT NULL,
                        `bearing` REAL NOT NULL,
                        `calculatedChainageMetres` INTEGER NOT NULL,
                        `offsetMetres` REAL NOT NULL,
                        `offsetSide` TEXT NOT NULL,
                        `lane` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `isRelocationBoundary` INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_survey_points_sessionId_timestamp` ON `survey_points` (`sessionId`, `timestamp`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `survey_markers` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `sessionId` TEXT NOT NULL,
                        `chainageMetres` INTEGER NOT NULL,
                        `displayChainage` TEXT NOT NULL,
                        `latitude` REAL NOT NULL,
                        `longitude` REAL NOT NULL,
                        `altitude` REAL NOT NULL,
                        `accuracy` REAL NOT NULL,
                        `markerType` TEXT NOT NULL,
                        `lane` TEXT NOT NULL,
                        `direction` TEXT NOT NULL,
                        `offsetMetres` REAL NOT NULL,
                        `offsetSide` TEXT NOT NULL,
                        `isManuallyCalibrated` INTEGER NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `notes` TEXT NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_survey_markers_sessionId_chainageMetres` ON `survey_markers` (`sessionId`, `chainageMetres`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `survey_calibrations` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `sessionId` TEXT NOT NULL,
                        `markerId` TEXT NOT NULL,
                        `measuredDistanceMetres` REAL NOT NULL,
                        `knownChainageMetres` INTEGER NOT NULL,
                        `driftMetres` REAL NOT NULL,
                        `timestamp` INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_survey_calibrations_sessionId` ON `survey_calibrations` (`sessionId`)")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE survey_sessions ADD COLUMN plannedEndChainageMetres INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE survey_sessions ADD COLUMN roadSide TEXT NOT NULL DEFAULT 'RHS'")
                db.execSQL("ALTER TABLE survey_sessions ADD COLUMN confirmationIntervalMetres INTEGER DEFAULT 2000")
                db.execSQL("ALTER TABLE survey_sessions ADD COLUMN lastConfirmedDistanceM REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE survey_points ADD COLUMN roadSide TEXT NOT NULL DEFAULT 'RHS'")
                db.execSQL("ALTER TABLE survey_markers ADD COLUMN roadSide TEXT NOT NULL DEFAULT 'RHS'")
                db.execSQL("ALTER TABLE survey_calibrations ADD COLUMN checkpointName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE survey_calibrations ADD COLUMN latitude REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE survey_calibrations ADD COLUMN longitude REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE survey_calibrations ADD COLUMN originalChainageMetres INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE photo_records ADD COLUMN subActivity TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE photo_records ADD COLUMN locationName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE photo_records ADD COLUMN weatherTempC REAL DEFAULT NULL")
                db.execSQL("ALTER TABLE photo_records ADD COLUMN weatherCondition TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE photo_records ADD COLUMN weatherHumidity INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE photo_records ADD COLUMN weatherWindSpeedKmh REAL DEFAULT NULL")
                db.execSQL("ALTER TABLE photo_records ADD COLUMN weatherWindDirDeg INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE photo_records ADD COLUMN weatherObservedAtMs INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE photo_records ADD COLUMN weatherSource TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `sr_records` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `recordType` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `parentId` TEXT NOT NULL,
                        `revisionNumber` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        `payloadJson` TEXT NOT NULL,
                        `syncState` TEXT NOT NULL,
                        `createdAtMs` INTEGER NOT NULL,
                        `updatedAtMs` INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sr_records_recordType_projectId` ON `sr_records` (`recordType`, `projectId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sr_records_recordType_parentId` ON `sr_records` (`recordType`, `parentId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sr_records_syncState` ON `sr_records` (`syncState`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `sr_sync_queue` (
                        `operationId` TEXT NOT NULL PRIMARY KEY,
                        `tableName` TEXT NOT NULL,
                        `recordId` TEXT NOT NULL,
                        `registerId` TEXT NOT NULL,
                        `revisionNumber` INTEGER NOT NULL,
                        `operationType` TEXT NOT NULL,
                        `rpcName` TEXT NOT NULL,
                        `payloadJson` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `errorMessage` TEXT NOT NULL,
                        `retryCount` INTEGER NOT NULL,
                        `createdAtMs` INTEGER NOT NULL,
                        `lastAttemptAtMs` INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sr_sync_queue_status_createdAtMs` ON `sr_sync_queue` (`status`, `createdAtMs`)")
            }
        }

        /**
         * Non-destructive migration after removing the Site Register module.
         * Leaves any pre-existing SQLite tables untouched so no user data is deleted.
         */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Intentionally non-destructive: preserve all existing user tables and records.
            }
        }

        /**
         * Non-destructive migration after removing the donation/coffee feature.
         */
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Keep migration path valid for v10 databases; v11->v12 drops any leftover donation tables.
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `donation_audit_logs`")
                db.execSQL("DROP TABLE IF EXISTS `donation_transactions`")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ctecs_highway_camera.db"
                )
                    .addMigrations(
                        MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
                        MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
                        MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12
                    )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
