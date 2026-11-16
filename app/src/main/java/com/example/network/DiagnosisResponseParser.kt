package com.example.network

import com.example.model.ConfidenceLevel
import com.example.model.ProbableCause
import com.example.model.VehicleCandidateSuggestion
import com.example.model.VehicleIdentificationResult
import com.example.model.VehicleType
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

    fun parseVehicleIdentification(rawText: String): VehicleIdentificationResult {
        val cleaned = extractJsonBlock(rawText)
            ?: return VehicleIdentificationResult(confidence = ConfidenceLevel.FAIBLE)

        return try {
            val json = JSONObject(cleaned)
            val brand = cleanField(
                json.optString("marque").ifBlank { json.optString("brand") }
            )
            val model = cleanField(
                json.optString("modele").ifBlank {
                    json.optString("modèle").ifBlank { json.optString("model") }
                }
            )
            val year = cleanField(
                json.optString("annee_approximative").ifBlank {
                    json.optString("année_approximative").ifBlank {
                        json.optString("annee").ifBlank { json.optString("year") }
                    }
                }
            )
            val displacement = normalizeDisplacement(
                cleanField(
                    json.optString("cylindree").ifBlank {
                        json.optString("cylindrée").ifBlank { json.optString("displacement_cc") }
                    }
                )
            )
            val engineType = cleanField(
                json.optString("type_moteur").ifBlank {
                    json.optString("type_de_moteur").ifBlank { json.optString("engine_type") }
                }
            )
            val vehicleTypeRaw = cleanField(
                json.optString("type_vehicule").ifBlank { json.optString("vehicle_type") }
            ).lowercase()
            val vehicleType = when {
                vehicleTypeRaw.contains("moto") || vehicleTypeRaw.contains("scooter") -> VehicleType.MOTO
                vehicleTypeRaw.contains("voiture") || vehicleTypeRaw.contains("auto") -> VehicleType.VOITURE
                vehicleTypeRaw.isNotEmpty() -> VehicleType.AUTRE
                else -> null
            }

            val confidenceRaw = cleanField(
                json.optString("niveau_certitude").ifBlank {
                    json.optString("certitude").ifBlank {
                        json.optString("confidence").ifBlank { "faible" }
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
                    val sBrand = cleanField(obj.optString("marque").ifBlank { obj.optString("brand") })
                    val sModel = cleanField(
                        obj.optString("modele").ifBlank {
                            obj.optString("modèle").ifBlank { obj.optString("model") }
                        }
                    )
                    val sYear = cleanField(
                        obj.optString("annee_approximative").ifBlank {
                            obj.optString("annee").ifBlank { obj.optString("year") }
                        }
                    )
                    val sCc = normalizeDisplacement(
                        cleanField(
                            obj.optString("cylindree").ifBlank {
                                obj.optString("cylindrée").ifBlank { obj.optString("displacement_cc") }
                            }
                        )
                    )
                    val sEngine = cleanField(
                        obj.optString("type_moteur").ifBlank { obj.optString("engine_type") }
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
                    val rawLine = cleanField(suggestionsArr.optString(i))
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

            // Ensure primary guess is also included if distinct and confidence is low or alternatives exist
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
            VehicleIdentificationResult(confidence = ConfidenceLevel.FAIBLE)
        }
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
