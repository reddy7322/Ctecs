package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        PhotoRecordEntity::class,
        RouteEntity::class,
        HighwayProjectEntity::class,
        SmartActivityEntity::class,
        QcRecordEntity::class,
        DailyProjectReportEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun photoRecordDao(): PhotoRecordDao
    abstract fun routeDao(): RouteDao
    abstract fun highwayProjectDao(): HighwayProjectDao
    abstract fun smartActivityDao(): SmartActivityDao
    abstract fun qcRecordDao(): QcRecordDao
    abstract fun dailyReportRecordDao(): DailyReportRecordDao

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

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ctecs_highway_camera.db"
                )
                    .addMigrations(MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
