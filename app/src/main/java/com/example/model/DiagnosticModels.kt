package com.example.model

enum class VehicleType(val label: String, val promptLabel: String) {
    MOTO("Moto", "moto"),
    VOITURE("Voiture", "voiture"),
    AUTRE("Autre", "autre");

    companion object {
        fun fromName(name: String): VehicleType =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: VOITURE
    }
}

data class VehicleInfo(
    val type: VehicleType = VehicleType.VOITURE,
    val brand: String = "",
    val model: String = "",
    val year: String = "",
    val displacementCc: String = "",
    val mileage: String = "",
    val unknownBrandModel: Boolean = false
) {
    val hasBrandAndModel: Boolean
        get() = !unknownBrandModel && brand.trim().isNotEmpty() && model.trim().isNotEmpty()

    val canProceedToListen: Boolean
        get() = unknownBrandModel || hasBrandAndModel

    fun displayTitle(): String {
        val parts = buildList {
            if (!unknownBrandModel) {
                if (brand.trim().isNotEmpty()) add(brand.trim())
                if (model.trim().isNotEmpty()) add(model.trim())
            }
            if (displacementCc.trim().isNotEmpty()) add("${displacementCc.trim()} cc")
            if (year.trim().isNotEmpty()) add(year.trim())
        }
        return if (parts.isEmpty()) {
            if (unknownBrandModel) "${type.label} • Modèle non précisé" else type.label
        } else {
            "${type.label} • ${parts.joinToString(" ")}"
        }
    }

    fun searchDescriptor(): String {
        val parts = buildList {
            if (brand.trim().isNotEmpty()) add(brand.trim())
            if (model.trim().isNotEmpty()) add(model.trim())
            if (year.trim().isNotEmpty()) add(year.trim())
            if (displacementCc.trim().isNotEmpty()) add("${displacementCc.trim()}cc")
        }
        return if (parts.isEmpty()) "moteur ${type.promptLabel}" else parts.joinToString(" ")
    }
}

data class ApiKeys(
    val geminiKey: String = "",
    val openAiKey: String = "",
    val perplexityKey: String = ""
) {
    val hasRequiredKeys: Boolean
        get() = geminiKey.trim().isNotEmpty() && openAiKey.trim().isNotEmpty()
}

enum class ConfidenceLevel(val label: String) {
    FAIBLE("Faible"),
    MOYEN("Moyen"),
    ELEVE("Élevé");

    companion object {
        fun fromText(raw: String): ConfidenceLevel {
            val normalized = raw.lowercase()
                .replace("é", "e")
                .replace("è", "e")
            return when {
                normalized.contains("elev") || normalized.contains("haut") || normalized.contains("fort") -> ELEVE
                normalized.contains("moyen") -> MOYEN
                else -> FAIBLE
            }
        }
    }
}

data class ProbableCause(
    val title: String,
    val probabilityLabel: String,
    val explanation: String
)

data class AcousticMetrics(
    val dominantFrequencyHz: Double,
    val estimatedRpm: Int,
    val regularityScore: Double,
    val regularityLabel: String,
    val abnormalPeaksDetected: Boolean,
    val peakCount: Int,
    val idleRmsDb: Double,
    val accelRmsDb: Double,
    val sampleRateHz: Int = 16000,
    val durationSeconds: Double = 30.0
)

data class DiagnosticResult(
    val id: Long = 0L,
    val timestampMillis: Long = System.currentTimeMillis(),
    val vehicleInfo: VehicleInfo,
    val summary: String,
    val confidence: ConfidenceLevel,
    val probableCauses: List<ProbableCause>,
    val selfChecks: List<String>,
    val seeMechanicRecommended: Boolean,
    val mechanicAdvice: String,
    val acousticMetrics: AcousticMetrics,
    val geminiDescription: String,
    val openAiDescription: String? = null,
    val perplexitySummary: String? = null,
    val perplexitySources: List<String> = emptyList(),
    val optionalWarnings: List<String> = emptyList()
)
