package com.vroum.app.network

import android.util.Base64
import com.vroum.app.model.AcousticMetrics
import com.vroum.app.model.VehicleIdentificationResult
import com.vroum.app.model.VehicleInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

data class DeepSeekResult(
    val content: String,
    val sources: List<String> = emptyList()
)

class EngineAiClients {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(80, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .callTimeout(95, TimeUnit.SECONDS)
        .build()

    private val deepSeekClient: OkHttpClient = client.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    companion object {
        private const val GEMINI_MODEL = "gemini-2.5-flash"

        const val ACOUSTIC_EAR_PROMPT =
            "Tu es un expert en acoustique moteur. Décris précisément ce que tu entends : " +
                "régularité du ralenti, ratés, cliquetis, claquements, sifflements, " +
                "bruits métalliques, changements au moment de l'accélération. " +
                "Ne donne aucun diagnostic, décris seulement le son avec un maximum de détails."

        const val VEHICLE_PHOTO_PROMPT =
            "Identifie cette moto (ou ce véhicule) : marque, modèle, année approximative, " +
                "cylindrée et type de moteur probable. Réponds en JSON avec ces champs et un niveau " +
                "de certitude (faible, moyen, élevé). Si tu hésites entre plusieurs modèles, donne-les tous.\n\n" +
                "Structure JSON stricte :\n" +
                "{\n" +
                "  \"type_vehicule\": \"moto | voiture | autre\",\n" +
                "  \"marque\": \"...\",\n" +
                "  \"modele\": \"...\",\n" +
                "  \"annee_approximative\": \"...\",\n" +
                "  \"cylindree\": \"...\",\n" +
                "  \"type_moteur\": \"...\",\n" +
                "  \"niveau_certitude\": \"faible | moyen | élevé\",\n" +
                "  \"suggestions\": [\n" +
                "    {\n" +
                "      \"marque\": \"...\",\n" +
                "      \"modele\": \"...\",\n" +
                "      \"annee_approximative\": \"...\",\n" +
                "      \"cylindree\": \"...\",\n" +
                "      \"type_moteur\": \"...\"\n" +
                "    }\n" +
                "  ]\n" +
                "}"
    }

    suspend fun identifyVehicleFromPhotoWithGemini(
        apiKey: String,
        jpegBytes: ByteArray
    ): VehicleIdentificationResult = withContext(Dispatchers.IO) {
        val base64Image = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
        val payload = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", VEHICLE_PHOTO_PROMPT))
                        put(JSONObject().put("inline_data", JSONObject().apply {
                            put("mime_type", "image/jpeg")
                            put("data", base64Image)
                        }))
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.2)
                put("responseMimeType", "application/json")
            })
        }

        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$GEMINI_MODEL:generateContent")
            .addHeader("x-goog-api-key", apiKey.trim())
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        val bodyString = executeRequest("Gemini", request)
        val text = extractGeminiResponseText(bodyString)
        DiagnosisResponseParser.parseVehicleIdentificationOrThrow(text)
    }

    suspend fun describeAudioWithGemini(
        apiKey: String,
        wavBytes: ByteArray
    ): String = withContext(Dispatchers.IO) {
        val base64Audio = Base64.encodeToString(wavBytes, Base64.NO_WRAP)
        val payload = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", ACOUSTIC_EAR_PROMPT))
                        put(JSONObject().put("inline_data", JSONObject().apply {
                            put("mime_type", "audio/wav")
                            put("data", base64Audio)
                        }))
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.2)
            })
        }

        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$GEMINI_MODEL:generateContent")
            .addHeader("x-goog-api-key", apiKey.trim())
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        val bodyString = executeRequest("Gemini", request)
        extractGeminiResponseText(bodyString)
    }

    private fun extractGeminiResponseText(bodyString: String): String {
        val root = try {
            JSONObject(bodyString)
        } catch (e: Exception) {
            throw DiagnosticApiException.InvalidResponse(
                provider = "Gemini",
                details = "Réponse JSON racine invalide : ${bodyString.take(280)}"
            )
        }
        val blockReason = root.optJSONObject("promptFeedback")?.optString("blockReason").orEmpty()
        val candidates = root.optJSONArray("candidates")
        val firstCandidate = candidates?.optJSONObject(0)
        val finishReason = firstCandidate?.optString("finishReason").orEmpty()
        val parts = firstCandidate
            ?.optJSONObject("content")
            ?.optJSONArray("parts")

        val text = buildString {
            if (parts != null) {
                for (i in 0 until parts.length()) {
                    val part = parts.optJSONObject(i) ?: continue
                    if (part.optBoolean("thought", false)) continue
                    val partText = part.optString("text").orEmpty()
                    if (partText.isNotBlank()) append(partText).append("\n")
                }
            }
        }.trim()

        if (text.isBlank()) {
            val reasonDetails = buildList {
                if (blockReason.isNotBlank()) add("blockReason=$blockReason")
                if (finishReason.isNotBlank()) add("finishReason=$finishReason")
                add("body=${bodyString.take(250)}")
            }.joinToString(" | ")
            throw DiagnosticApiException.InvalidResponse(
                provider = "Gemini",
                details = reasonDetails
            )
        }
        return text
    }

    /**
     * Text-only mechanical knowledge query via DeepSeek.
     * Never sends images or audio, and never claims live web search.
     */
    suspend fun searchKnownFaultsWithDeepSeek(
        apiKey: String,
        vehicleInfo: VehicleInfo
    ): DeepSeekResult = withContext(Dispatchers.IO) {
        val descriptor = vehicleInfo.searchDescriptor()
        val query = "Quelles sont les pannes moteur courantes, le type de moteur " +
            "(monocylindre, bicylindre, 4 cylindres, 2 temps ou 4 temps) " +
            "et leurs symptômes sonores pour $descriptor ?"
        val payload = JSONObject().apply {
            put("model", "deepseek-chat")
            put("temperature", 0.2)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", "Tu es un assistant technique automobile et moto. Donne les pannes courantes connues et leurs symptômes sonores en français, de façon concise et factuelle.")
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", query)
                })
            })
        }

        val request = Request.Builder()
            .url("https://api.deepseek.com/chat/completions")
            .addHeader("Authorization", "Bearer ${apiKey.trim()}")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        val bodyString = executeRequest("DeepSeek", request, httpClient = deepSeekClient)
        val root = try {
            JSONObject(bodyString)
        } catch (_: Exception) {
            throw DiagnosticApiException.InvalidResponse("DeepSeek", bodyString.take(240))
        }
        val content = root.optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content")
            .orEmpty()
            .trim()

        DeepSeekResult(content = content, sources = emptyList())
    }

    suspend fun diagnoseWithOpenAi(
        apiKey: String,
        vehicleInfo: VehicleInfo,
        metrics: AcousticMetrics,
        geminiDescription: String,
        deepSeekResult: DeepSeekResult?
    ): ParsedChefDiagnosis = withContext(Dispatchers.IO) {
        val systemPrompt =
            "Tu es un mécanicien professionnel expert en ${vehicleInfo.type.promptLabel}. " +
                "Tu reçois la description d'un bruit de moteur. Donne les causes probables classées par probabilité, " +
                "un niveau de confiance honnête, et des vérifications simples à faire. " +
                "Ne prétends jamais à une certitude. Réponds en français simple."

        val userPrompt = buildString {
            appendLine("=== INFORMATIONS VÉHICULE ===")
            appendLine("Type : ${vehicleInfo.type.label}")
            if (vehicleInfo.hasBrandAndModel) {
                appendLine("Marque : ${vehicleInfo.brand.trim()}")
                appendLine("Modèle : ${vehicleInfo.model.trim()}")
            } else {
                appendLine("Marque et modèle : Non renseignés (analyse générale demandée)")
            }
            if (vehicleInfo.year.isNotBlank()) appendLine("Année : ${vehicleInfo.year.trim()}")
            if (vehicleInfo.displacementCc.isNotBlank()) appendLine("Cylindrée : ${vehicleInfo.displacementCc.trim()} cc")
            if (vehicleInfo.engineType.isNotBlank()) appendLine("Type de moteur probable : ${vehicleInfo.engineType.trim()}")
            if (vehicleInfo.mileage.isNotBlank()) appendLine("Kilométrage : ${vehicleInfo.mileage.trim()} km")
            appendLine()
            appendLine("=== CONSIGNES D'ADAPTATION MÉCANIQUE ===")
            if (vehicleInfo.hasBrandAndModel) {
                appendLine(
                    "Identifie le type de moteur correspondant à ce véhicule " +
                        "(monocylindre, bicylindre, 3 ou 4 cylindres, 2 temps ou 4 temps) " +
                        "à partir de la marque, du modèle, de l'année et de la cylindrée. " +
                        "Adapte ton analyse acoustique à cette architecture moteur précise et à ses pannes connues."
                )
            } else {
                appendLine(
                    "IMPORTANT : La marque et le modèle ne sont pas connus. " +
                        "Fais une analyse générale adaptée au type de véhicule (${vehicleInfo.type.promptLabel})" +
                        "${if (vehicleInfo.displacementCc.isNotBlank()) " et à la cylindrée (${vehicleInfo.displacementCc.trim()} cc)" else ""}. " +
                        "Par manque d'identification précise du moteur (monocylindre, bicylindre, 4 cylindres, 2 temps ou 4 temps), " +
                        "baisse obligatoirement le niveau de confiance (\"faible\" de préférence, ou au maximum \"moyen\")."
                )
            }
            appendLine()
            appendLine("=== DONNÉES ACOUSTIQUES MESURÉES LOCALEMENT ===")
            appendLine("- Fréquence dominante au ralenti : ${metrics.dominantFrequencyHz} Hz")
            appendLine("- Régime moteur estimé : ~${metrics.estimatedRpm} tr/min")
            appendLine("- Régularité du rythme : ${metrics.regularityLabel} (score ${(metrics.regularityScore * 100).toInt()}%)")
            appendLine("- Pics transitoires anormaux : ${if (metrics.abnormalPeaksDetected) "Oui (${metrics.peakCount} pics détectés)" else "Non (${metrics.peakCount} pics isolés)"}")
            appendLine("- Niveau sonore ralenti : ${metrics.idleRmsDb} dBFS | Accélération : ${metrics.accelRmsDb} dBFS")
            appendLine()
            appendLine("=== DESCRIPTION AUDIO (GEMINI) ===")
            appendLine(geminiDescription)
            if (deepSeekResult != null && deepSeekResult.content.isNotBlank()) {
                appendLine()
                appendLine("=== PANNES COURANTES (CONNAISSANCES DE L'IA - DEEPSEEK) ===")
                appendLine(deepSeekResult.content)
            }
            appendLine()
            appendLine("Réponds UNIQUEMENT avec un objet JSON valide respectant exactement cette structure :")
            appendLine("""
                {
                  "summary": "Diagnostic global clair en 2 ou 3 phrases simples sans jargon (en précisant le type de moteur si identifié).",
                  "confidence": "faible | moyen | élevé",
                  "probable_causes": [
                    {
                      "title": "Nom simple de la cause",
                      "probability": "Probable / Possible / Peu probable",
                      "explanation": "Explication courte et rassurante en français simple."
                    }
                  ],
                  "self_checks": [
                    "Vérification simple et sans danger à faire soi-même"
                  ],
                  "see_mechanic_recommended": true,
                  "mechanic_advice": "Conseil clair pour savoir s'il faut aller chez un mécanicien (ex: À voir chez un mécanicien rapidement / Lors de la prochaine révision)."
                }
            """.trimIndent())
        }

        val rawResponse = try {
            callOpenAiChat(apiKey, "gpt-4o", systemPrompt, userPrompt)
        } catch (_: ModelNotFoundException) {
            callOpenAiChat(apiKey, "gpt-4o-mini", systemPrompt, userPrompt)
        }

        val root = try {
            JSONObject(rawResponse)
        } catch (_: Exception) {
            throw DiagnosticApiException.InvalidResponse("OpenAI", rawResponse.take(240))
        }
        val text = root.optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content")
            .orEmpty()
            .trim()

        if (text.isBlank()) {
            throw DiagnosticApiException.InvalidResponse("OpenAI", "Réponse vide reçue.")
        }
        DiagnosisResponseParser.parse(text)
    }

    private suspend fun callOpenAiChat(
        apiKey: String,
        model: String,
        systemPrompt: String,
        userPrompt: String
    ): String {
        val payload = JSONObject().apply {
            put("model", model)
            put("temperature", 0.2)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", userPrompt)
                })
            })
        }

        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .addHeader("Authorization", "Bearer ${apiKey.trim()}")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        return executeRequest("OpenAI", request, allowModelFallback = true)
    }

    private class ModelNotFoundException : Exception()

    private suspend fun executeRequest(
        provider: String,
        request: Request,
        allowModelFallback: Boolean = false,
        httpClient: OkHttpClient = client
    ): String {
        var attempt = 0
        while (true) {
            try {
                httpClient.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        return body
                    }

                    val code = response.code
                    val lowerBody = body.lowercase()
                    val apiMessage = extractErrorMessage(body) ?: body.take(320).ifBlank { "HTTP $code" }

                    if ((code == 500 || code == 503) && attempt == 0) {
                        attempt++
                        delay(2000L)
                        return@use
                    }

                    if (allowModelFallback && (code == 404 || (code == 400 && lowerBody.contains("model")))) {
                        throw ModelNotFoundException()
                    }

                    when (code) {
                        500, 503 -> throw DiagnosticApiException.ServerOverloaded(provider, code, apiMessage)
                        401, 403 -> {
                            if (lowerBody.contains("quota") || lowerBody.contains("credit") || lowerBody.contains("billing")) {
                                throw DiagnosticApiException.InsufficientCredit(provider, code, apiMessage)
                            }
                            throw DiagnosticApiException.InvalidApiKey(provider, code, apiMessage)
                        }
                        402, 429 -> throw DiagnosticApiException.InsufficientCredit(provider, code, apiMessage)
                        400 -> {
                            if (lowerBody.contains("api_key") ||
                                lowerBody.contains("invalid api key") ||
                                lowerBody.contains("api key") ||
                                lowerBody.contains("permission") ||
                                lowerBody.contains("unauthorized")
                            ) {
                                throw DiagnosticApiException.InvalidApiKey(provider, code, apiMessage)
                            }
                            throw DiagnosticApiException.ProviderError(
                                provider = provider,
                                details = "Requête refusée (HTTP 400).",
                                httpCode = code,
                                apiDetails = apiMessage
                            )
                        }
                        else -> throw DiagnosticApiException.ProviderError(
                            provider = provider,
                            details = "Erreur serveur (HTTP $code).",
                            httpCode = code,
                            apiDetails = apiMessage
                        )
                    }
                }
            } catch (e: DiagnosticApiException) {
                throw e
            } catch (e: ModelNotFoundException) {
                throw e
            } catch (_: UnknownHostException) {
                throw DiagnosticApiException.NoInternet()
            } catch (_: ConnectException) {
                throw DiagnosticApiException.NoInternet()
            } catch (e: SocketTimeoutException) {
                throw DiagnosticApiException.AnalysisTimeout(e.localizedMessage)
            } catch (e: IOException) {
                throw DiagnosticApiException.ProviderError(
                    provider = provider,
                    details = e.localizedMessage ?: "Erreur réseau",
                    apiDetails = e.localizedMessage
                )
            }
        }
    }

    private fun extractErrorMessage(body: String): String? {
        return try {
            val json = JSONObject(body)
            json.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
                ?: json.optString("message").takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }
}
