package com.example.network

import com.example.model.ConfidenceLevel
import com.example.model.ProbableCause
import org.json.JSONObject

data class ParsedChefDiagnosis(
    val summary: String,
    val confidence: ConfidenceLevel,
    val probableCauses: List<ProbableCause>,
    val selfChecks: List<String>,
    val seeMechanicRecommended: Boolean,
    val mechanicAdvice: String
)

object DiagnosisResponseParser {

    fun parse(rawText: String): ParsedChefDiagnosis {
        val cleaned = extractJsonBlock(rawText)
        if (cleaned != null) {
            try {
                val json = JSONObject(cleaned)
                val summary = json.optString("summary").ifBlank {
                    json.optString("resume").ifBlank { rawText.take(400) }
                }
                val confidenceRaw = json.optString("confidence").ifBlank {
                    json.optString("niveau_confiance", "faible")
                }
                val confidence = ConfidenceLevel.fromText(confidenceRaw)

                val causesList = mutableListOf<ProbableCause>()
                val causesArr = json.optJSONArray("probable_causes")
                    ?: json.optJSONArray("causes_probables")
                if (causesArr != null) {
                    for (i in 0 until causesArr.length()) {
                        val obj = causesArr.optJSONObject(i) ?: continue
                        val title = obj.optString("title").ifBlank { obj.optString("titre") }
                        val prob = obj.optString("probability").ifBlank {
                            obj.optString("probabilite", "Possible")
                        }
                        val exp = obj.optString("explanation").ifBlank {
                            obj.optString("explication")
                        }
                        if (title.isNotBlank()) {
                            causesList.add(ProbableCause(title, prob, exp))
                        }
                    }
                }

                val checksList = mutableListOf<String>()
                val checksArr = json.optJSONArray("self_checks")
                    ?: json.optJSONArray("verifications")
                if (checksArr != null) {
                    for (i in 0 until checksArr.length()) {
                        val item = checksArr.optString(i).trim()
                        if (item.isNotBlank()) checksList.add(item)
                    }
                }

                val seeMechanic = json.optBoolean("see_mechanic_recommended", true)
                val mechanicAdvice = json.optString("mechanic_advice").ifBlank {
                    if (seeMechanic) {
                        "À voir chez un mécanicien pour confirmer l'origine exacte du bruit."
                    } else {
                        "Surveille l'évolution du bruit. En cas de doute, passe chez un mécanicien."
                    }
                }

                return ParsedChefDiagnosis(
                    summary = summary,
                    confidence = confidence,
                    probableCauses = causesList,
                    selfChecks = checksList,
                    seeMechanicRecommended = seeMechanic,
                    mechanicAdvice = mechanicAdvice
                )
            } catch (_: Exception) {
                // Fall through to plain text fallback
            }
        }

        val lower = rawText.lowercase()
        val confidence = when {
            lower.contains("élevé") || lower.contains("eleve") -> ConfidenceLevel.ELEVE
            lower.contains("moyen") -> ConfidenceLevel.MOYEN
            else -> ConfidenceLevel.FAIBLE
        }
        return ParsedChefDiagnosis(
            summary = rawText.trim(),
            confidence = confidence,
            probableCauses = listOf(
                ProbableCause(
                    title = "Analyse détaillée ci-dessus",
                    probabilityLabel = "Estimation",
                    explanation = "Consulte le résumé et les vérifications recommandées."
                )
            ),
            selfChecks = listOf("Vérifier les niveaux (huile, liquide de refroidissement) moteur froid."),
            seeMechanicRecommended = true,
            mechanicAdvice = "À voir chez un mécanicien si le bruit persiste."
        )
    }

    private fun extractJsonBlock(text: String): String? {
        val trimmed = text.trim()
        val fenceRegex = Regex("```(?:json)?\\s*(\\{[\\s\\S]*\\})\\s*```", RegexOption.IGNORE_CASE)
        fenceRegex.find(trimmed)?.groupValues?.getOrNull(1)?.let { return it }

        val firstBrace = trimmed.indexOf('{')
        val lastBrace = trimmed.lastIndexOf('}')
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return trimmed.substring(firstBrace, lastBrace + 1)
        }
        return null
    }
}
