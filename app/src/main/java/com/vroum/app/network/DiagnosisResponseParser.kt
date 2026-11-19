package com.vroum.app.network

import com.vroum.app.model.ConfidenceLevel
import com.vroum.app.model.ProbableCause
import com.vroum.app.model.VehicleCandidateSuggestion
import com.vroum.app.model.VehicleIdentificationResult
import com.vroum.app.model.VehicleType
import org.json.JSONArray
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
                val summary = safeOptString(json, "summary").ifBlank {
                    safeOptString(json, "resume").ifBlank { rawText.take(400) }
                }
                val confidenceRaw = safeOptString(json, "confidence").ifBlank {
                    safeOptString(json, "niveau_confiance").ifBlank { "faible" }
                }
                val confidence = ConfidenceLevel.fromText(confidenceRaw)

                val causesList = mutableListOf<ProbableCause>()
                val causesArr = json.optJSONArray("probable_causes")
                    ?: json.optJSONArray("causes_probables")
                if (causesArr != null) {
                    for (i in 0 until causesArr.length()) {
                        val obj = causesArr.optJSONObject(i) ?: continue
                        val title = safeOptString(obj, "title").ifBlank { safeOptString(obj, "titre") }
                        val prob = safeOptString(obj, "probability").ifBlank {
                            safeOptString(obj, "probabilite").ifBlank { "Possible" }
                        }
                        val exp = safeOptString(obj, "explanation").ifBlank {
                            safeOptString(obj, "explication")
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
                        val item = checksArr.optString(i, "").trim()
                        if (item.isNotBlank() && !item.equals("null", ignoreCase = true)) {
                            checksList.add(item)
                        }
                    }
                }

                val seeMechanic = json.optBoolean("see_mechanic_recommended", true)
                val mechanicAdvice = safeOptString(json, "mechanic_advice").ifBlank {
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
        val trimmed = text
            .trim()
            .removePrefix("\uFEFF")
        val fenceRegex = Regex("```(?:json)?\\s*([\\[{][\\s\\S]*[\\]}])\\s*```", RegexOption.IGNORE_CASE)
        val fenced = fenceRegex.find(trimmed)?.groupValues?.getOrNull(1)?.trim()
        val candidate = fenced ?: trimmed

        // If Gemini returned an array containing the object, unwrap the first JSON object
        val firstBrace = candidate.indexOf('{')
        val lastBrace = candidate.lastIndexOf('}')
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return candidate.substring(firstBrace, lastBrace + 1)
        }
        return null
    }

    fun parseVehicleIdentification(rawText: String): VehicleIdentificationResult {
        val cleaned = extractJsonBlock(rawText)
            ?: return VehicleIdentificationResult(confidence = ConfidenceLevel.FAIBLE)
        return parseVehicleIdentificationFromJson(cleaned)
            ?: VehicleIdentificationResult(confidence = ConfidenceLevel.FAIBLE)
    }

    fun parseVehicleIdentificationOrThrow(rawText: String): VehicleIdentificationResult {
        val cleaned = extractJsonBlock(rawText)
            ?: throw DiagnosticApiException.InvalidResponse(
                provider = "Gemini",
                details = "Aucun objet JSON valide trouvé dans la réponse : ${rawText.take(280)}"
            )
        return parseVehicleIdentificationFromJson(cleaned)
            ?: throw DiagnosticApiException.InvalidResponse(
                provider = "Gemini",
                details = "JSON illisible : ${cleaned.take(280)}"
            )
    }

    private fun parseVehicleIdentificationFromJson(cleanedJson: String): VehicleIdentificationResult? {
        return try {
            val json = JSONObject(cleanedJson)
            val brand = cleanField(
                safeOptString(json, "marque").ifBlank { safeOptString(json, "brand") }
            )
            val model = cleanField(
                safeOptString(json, "modele").ifBlank {
                    safeOptString(json, "modèle").ifBlank { safeOptString(json, "model") }
                }
            )
            val year = cleanField(
                safeOptString(json, "annee_approximative").ifBlank {
                    safeOptString(json, "année_approximative").ifBlank {
                        safeOptString(json, "annee").ifBlank { safeOptString(json, "year") }
                    }
                }
            )
            val displacement = normalizeDisplacement(
                cleanField(
                    safeOptString(json, "cylindree").ifBlank {
                        safeOptString(json, "cylindrée").ifBlank { safeOptString(json, "displacement_cc") }
                    }
                )
            )
            val engineType = cleanField(
                safeOptString(json, "type_moteur").ifBlank {
                    safeOptString(json, "type_de_moteur").ifBlank { safeOptString(json, "engine_type") }
                }
            )
            val vehicleTypeRaw = cleanField(
                safeOptString(json, "type_vehicule").ifBlank { safeOptString(json, "vehicle_type") }
            ).lowercase()
            val vehicleType = when {
                vehicleTypeRaw.contains("moto") || vehicleTypeRaw.contains("scooter") -> VehicleType.MOTO
                vehicleTypeRaw.contains("voiture") || vehicleTypeRaw.contains("auto") -> VehicleType.VOITURE
                vehicleTypeRaw.isNotEmpty() -> VehicleType.AUTRE
                else -> null
            }

            val confidenceRaw = cleanField(
                safeOptString(json, "niveau_certitude").ifBlank {
                    safeOptString(json, "certitude").ifBlank {
                        safeOptString(json, "confidence").ifBlank { "faible" }
                    }
                }
            )
            val confidence = ConfidenceLevel.fromText(confidenceRaw)

            val suggestionsArr = json.optJSONArray("suggestions")
                ?: json.optJSONArray("autres_modeles")
                ?: json.optJSONArray("modeles_possibles")
                ?: json.optJSONArray("alternatives")
                ?: JSONArray()

            val parsedSuggestions = mutableListOf<VehicleCandidateSuggestion>()
            for (i in 0 until suggestionsArr.length()) {
                val obj = suggestionsArr.optJSONObject(i)
                if (obj != null) {
                    val sBrand = cleanField(
                        safeOptString(obj, "marque").ifBlank { safeOptString(obj, "brand") }
                    )
                    val sModel = cleanField(
                        safeOptString(obj, "modele").ifBlank {
                            safeOptString(obj, "modèle").ifBlank { safeOptString(obj, "model") }
                        }
                    )
                    val sYear = cleanField(
                        safeOptString(obj, "annee_approximative").ifBlank {
                            safeOptString(obj, "annee").ifBlank { safeOptString(obj, "year") }
                        }
                    )
                    val sCc = normalizeDisplacement(
                        cleanField(
                            safeOptString(obj, "cylindree").ifBlank {
                                safeOptString(obj, "cylindrée").ifBlank { safeOptString(obj, "displacement_cc") }
                            }
                        )
                    )
                    val sEngine = cleanField(
                        safeOptString(obj, "type_moteur").ifBlank { safeOptString(obj, "engine_type") }
                    )
                    if (sBrand.isNotBlank() && sModel.isNotBlank()) {
                        parsedSuggestions.add(
                            VehicleCandidateSuggestion(
                                brand = sBrand,
                                model = sModel,
                                year = sYear,
                                displacementCc = sCc,
                                engineType = sEngine,
                                vehicleType = vehicleType
                            )
                        )
                    }
                } else {
                    val rawLine = cleanField(suggestionsArr.optString(i, ""))
                    val parts = rawLine.split(" ").filter { it.isNotBlank() }
                    if (parts.size >= 2) {
                        parsedSuggestions.add(
                            VehicleCandidateSuggestion(
                                brand = parts.first(),
                                model = parts.drop(1).joinToString(" "),
                                vehicleType = vehicleType
                            )
                        )
                    }
                }
            }

            val distinctSuggestions = buildList {
                if (brand.isNotBlank() && model.isNotBlank() &&
                    (confidence == ConfidenceLevel.FAIBLE || parsedSuggestions.isNotEmpty())
                ) {
                    add(
                        VehicleCandidateSuggestion(
                            brand = brand,
                            model = model,
                            year = year,
                            displacementCc = displacement,
                            engineType = engineType,
                            vehicleType = vehicleType
                        )
                    )
                }
                parsedSuggestions.forEach { cand ->
                    val exists = any {
                        it.brand.equals(cand.brand, ignoreCase = true) &&
                            it.model.equals(cand.model, ignoreCase = true) &&
                            it.year.equals(cand.year, ignoreCase = true)
                    }
                    if (!exists) add(cand)
                }
            }

            VehicleIdentificationResult(
                vehicleType = vehicleType,
                brand = brand,
                model = model,
                year = year,
                displacementCc = displacement,
                engineType = engineType,
                confidence = confidence,
                suggestions = distinctSuggestions
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun safeOptString(json: JSONObject, key: String): String {
        if (!json.has(key) || json.isNull(key)) return ""
        return json.optString(key, "").trim()
    }

    private fun cleanField(raw: String): String {
        val trimmed = raw.trim()
        val lower = trimmed.lowercase()
        return if (
            trimmed.isEmpty() ||
            lower == "null" ||
            lower == "inconnu" ||
            lower == "inconnue" ||
            lower == "n/a" ||
            lower == "non déterminé" ||
            lower == "non determine" ||
            lower == "?"
        ) {
            ""
        } else {
            trimmed
        }
    }

    private fun normalizeDisplacement(raw: String): String {
        if (raw.isBlank()) return ""
        val match = Regex("(\\d{2,4})").find(raw)
        return match?.groupValues?.getOrNull(1) ?: raw
    }
}
