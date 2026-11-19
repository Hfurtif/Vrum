package com.vroum.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [DiagnosticEntity::class], version = 4, exportSchema = false)
abstract class VroumDatabase : RoomDatabase() {
    abstract fun diagnosticDao(): DiagnosticDao

    companion object {
        @Volatile
        private var INSTANCE: VroumDatabase? = null

        internal val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE diagnostics ADD COLUMN displacementCc TEXT NOT NULL DEFAULT ''")
            }
        }

        internal val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE diagnostics ADD COLUMN engineType TEXT NOT NULL DEFAULT ''")
            }
        }

        internal val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS diagnostics_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        timestampMillis INTEGER NOT NULL,
                        vehicleType TEXT NOT NULL,
                        brand TEXT NOT NULL,
                        model TEXT NOT NULL,
                        year TEXT NOT NULL,
                        displacementCc TEXT NOT NULL,
                        engineType TEXT NOT NULL,
                        mileage TEXT NOT NULL,
                        summary TEXT NOT NULL,
                        confidence TEXT NOT NULL,
                        probableCausesJson TEXT NOT NULL,
                        selfChecksJson TEXT NOT NULL,
                        seeMechanicRecommended INTEGER NOT NULL,
                        mechanicAdvice TEXT NOT NULL,
                        dominantFrequencyHz REAL NOT NULL,
                        estimatedRpm INTEGER NOT NULL,
                        regularityScore REAL NOT NULL,
                        regularityLabel TEXT NOT NULL,
                        abnormalPeaksDetected INTEGER NOT NULL,
                        peakCount INTEGER NOT NULL,
                        idleRmsDb REAL NOT NULL,
                        accelRmsDb REAL NOT NULL,
                        sampleRateHz INTEGER NOT NULL,
                        durationSeconds REAL NOT NULL,
                        geminiDescription TEXT NOT NULL,
                        openAiDescription TEXT,
                        deepSeekSummary TEXT,
                        deepSeekSourcesJson TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO diagnostics_new (
                        id, timestampMillis, vehicleType, brand, model, year, displacementCc,
                        engineType, mileage, summary, confidence, probableCausesJson, selfChecksJson,
                        seeMechanicRecommended, mechanicAdvice, dominantFrequencyHz, estimatedRpm,
                        regularityScore, regularityLabel, abnormalPeaksDetected, peakCount,
                        idleRmsDb, accelRmsDb, sampleRateHz, durationSeconds, geminiDescription,
                        openAiDescription, deepSeekSummary, deepSeekSourcesJson
                    )
                    SELECT
                        id, timestampMillis, vehicleType, brand, model, year, displacementCc,
                        engineType, mileage, summary, confidence, probableCausesJson, selfChecksJson,
                        seeMechanicRecommended, mechanicAdvice, dominantFrequencyHz, estimatedRpm,
                        regularityScore, regularityLabel, abnormalPeaksDetected, peakCount,
                        idleRmsDb, accelRmsDb, sampleRateHz, durationSeconds, geminiDescription,
                        openAiDescription, perplexitySummary, perplexitySourcesJson
                    FROM diagnostics
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE diagnostics")
                db.execSQL("ALTER TABLE diagnostics_new RENAME TO diagnostics")
            }
        }

        fun getInstance(context: Context): VroumDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    VroumDatabase::class.java,
                    "vroum_diagnostics.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
