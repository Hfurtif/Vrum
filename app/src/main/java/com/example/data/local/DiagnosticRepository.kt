package com.example.data.local

import com.example.model.AcousticMetrics
import com.example.model.ConfidenceLevel
import com.example.model.DiagnosticResult
import com.example.model.ProbableCause
import com.example.model.VehicleInfo
import com.example.model.VehicleType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

class DiagnosticRepository(private val dao: DiagnosticDao) {

    val allDiagnostics: Flow<List<DiagnosticResult>> = dao.getAllDiagnostics().map { list ->
        list.map { it.toDomain() }
    }

    suspend fun getById(id: Long): DiagnosticResult? {
        return dao.getDiagnosticById(id)?.toDomain()
    }

    suspend fun save(result: DiagnosticResult): Long {
        return dao.insertDiagnostic(result.toEntity())
    }

    suspend fun deleteById(id: Long) {
        dao.deleteDiagnosticById(id)
    }

    private fun DiagnosticResult.toEntity(): DiagnosticEntity {
        val causesArray = JSONArray().apply {
            probableCauses.forEach { cause ->
                put(JSONObject().apply {
                    put("title", cause.title)
                    put("probability", cause.probabilityLabel)
                    put("explanation", cause.explanation)
                })
            }
        }
        val checksArray = JSONArray().apply {
            selfChecks.forEach { put(it) }
        }
        val sourcesArray = JSONArray().apply {
            perplexitySources.forEach { put(it) }
        }
        return DiagnosticEntity(
            id = id,
            timestampMillis = timestampMillis,
            vehicleType = vehicleInfo.type.name,
            brand = vehicleInfo.brand,
            model = vehicleInfo.model,
            year = vehicleInfo.year,
            displacementCc = vehicleInfo.displacementCc,
            engineType = vehicleInfo.engineType,
            mileage = vehicleInfo.mileage,
            summary = summary,
            confidence = confidence.name,
            probableCausesJson = causesArray.toString(),
            selfChecksJson = checksArray.toString(),
            seeMechanicRecommended = seeMechanicRecommended,
            mechanicAdvice = mechanicAdvice,
            dominantFrequencyHz = acousticMetrics.dominantFrequencyHz,
            estimatedRpm = acousticMetrics.estimatedRpm,
            regularityScore = acousticMetrics.regularityScore,
            regularityLabel = acousticMetrics.regularityLabel,
            abnormalPeaksDetected = acousticMetrics.abnormalPeaksDetected,
            peakCount = acousticMetrics.peakCount,
            idleRmsDb = acousticMetrics.idleRmsDb,
            accelRmsDb = acousticMetrics.accelRmsDb,
            sampleRateHz = acousticMetrics.sampleRateHz,
            durationSeconds = acousticMetrics.durationSeconds,
            geminiDescription = geminiDescription,
            openAiDescription = openAiDescription,
            perplexitySummary = perplexitySummary,
            perplexitySourcesJson = sourcesArray.toString()
        )
    }

    private fun DiagnosticEntity.toDomain(): DiagnosticResult {
        val causes = buildList {
            val arr = runCatching { JSONArray(probableCausesJson) }.getOrNull() ?: JSONArray()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                add(
                    ProbableCause(
                        title = obj.optString("title"),
                        probabilityLabel = obj.optString("probability"),
                        explanation = obj.optString("explanation")
                    )
                )
            }
        }
        val checks = buildList {
            val arr = runCatching { JSONArray(selfChecksJson) }.getOrNull() ?: JSONArray()
            for (i in 0 until arr.length()) {
                val item = arr.optString(i)
                if (item.isNotBlank()) add(item)
            }
        }
        val sources = buildList {
            val arr = runCatching { JSONArray(perplexitySourcesJson) }.getOrNull() ?: JSONArray()
            for (i in 0 until arr.length()) {
                val item = arr.optString(i)
                if (item.isNotBlank()) add(item)
            }
        }
        return DiagnosticResult(
            id = id,
            timestampMillis = timestampMillis,
            vehicleInfo = VehicleInfo(
                type = VehicleType.fromName(vehicleType),
                brand = brand,
                model = model,
                year = year,
                displacementCc = displacementCc,
                engineType = engineType,
                mileage = mileage,
                unknownBrandModel = brand.isBlank() && model.isBlank()
            ),
            summary = summary,
            confidence = ConfidenceLevel.entries.firstOrNull { it.name == confidence }
                ?: ConfidenceLevel.fromText(confidence),
            probableCauses = causes,
            selfChecks = checks,
            seeMechanicRecommended = seeMechanicRecommended,
            mechanicAdvice = mechanicAdvice,
            acousticMetrics = AcousticMetrics(
                dominantFrequencyHz = dominantFrequencyHz,
                estimatedRpm = estimatedRpm,
                regularityScore = regularityScore,
                regularityLabel = regularityLabel,
                abnormalPeaksDetected = abnormalPeaksDetected,
                peakCount = peakCount,
                idleRmsDb = idleRmsDb,
                accelRmsDb = accelRmsDb,
                sampleRateHz = sampleRateHz,
                durationSeconds = durationSeconds
            ),
            geminiDescription = geminiDescription,
            openAiDescription = openAiDescription,
            perplexitySummary = perplexitySummary,
            perplexitySources = sources
        )
    }
}
