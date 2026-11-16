package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "diagnostics")
data class DiagnosticEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val timestampMillis: Long,
    val vehicleType: String,
    val brand: String,
    val model: String,
    val year: String,
    val displacementCc: String,
    val engineType: String = "",
    val mileage: String,
    val summary: String,
    val confidence: String,
    val probableCausesJson: String,
    val selfChecksJson: String,
    val seeMechanicRecommended: Boolean,
    val mechanicAdvice: String,
    val dominantFrequencyHz: Double,
    val estimatedRpm: Int,
    val regularityScore: Double,
    val regularityLabel: String,
    val abnormalPeaksDetected: Boolean,
    val peakCount: Int,
    val idleRmsDb: Double,
    val accelRmsDb: Double,
    val sampleRateHz: Int,
    val durationSeconds: Double,
    val geminiDescription: String,
    val openAiDescription: String?,
    val perplexitySummary: String?,
    val perplexitySourcesJson: String
)
