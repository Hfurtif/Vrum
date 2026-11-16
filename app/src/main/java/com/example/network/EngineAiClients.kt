package com.example.network

import android.util.Base64
import com.example.model.AcousticMetrics
import com.example.model.VehicleInfo
import kotlinx.coroutines.Dispatchers
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

data class PerplexityResult(
    val content: String,
    val sources: List<String>
)

class EngineAiClients {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(80, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .callTimeout(95, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    companion object {
        const val ACOUSTIC_EAR_PROMPT =
            "Tu es un expert en acoustique moteur. Décris précisément ce que tu entends : " +
                "régularité du ralenti, ratés, cliquetis, claquements, sifflements, " +
                "bruits métalliques, changements au moment de l'accélération. " +
                "Ne donne aucun diagnostic, décris seulement le son avec un maximum de détails."
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
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent")
            .addHeader("x-goog-api-key", apiKey.trim())
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        val bodyString = executeRequest("Gemini", request)
        val root = JSONObject(bodyString)
        val candidates = root.optJSONArray("candidates")
        val first = candidates?.optJSONObject(0)
        val parts = first?.optJSONObject("content")?.optJSONArray("parts")
        val text = buildString {
            if (parts != null) {
                for (i in 0 until parts.length()) {
                    val partText = parts.optJSONObject(i)?.optString("text").orEmpty()
                    if (partText.isNotBlank()) append(partText).append("\n")
                }
            }
        }.trim()

        if (text.isBlank()) {
            throw DiagnosticApiException.ProviderError("Gemini", "Réponse vide reçue.")
        }
        text
    }

    suspend fun searchKnownFaultsWithPerplexity(
        apiKey: String,
        vehicleInfo: VehicleInfo
    ): PerplexityResult = withContext(Dispatchers.IO) {
        val descriptor = vehicleInfo.searchDescriptor()
        val query = "Quelles sont les pannes moteur courantes, le type de moteur " +
            "(monocylindre, bicylindre, 4 cylindres, 2 temps ou 4 temps) " +
            "et leurs symptômes sonores pour $descriptor ?"
        val payload = JSONObject().apply {
            put("model", "sonar")
            put("temperature", 0.2)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", "Tu es un assistant technique automobile et moto. Réponds de façon concise et factuelle en français.")
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", query)
                })
            })
        }

        val request = Request.Builder()
            .url("https://api.perplexity.ai/chat/completions")
            .addHeader("Authorization", "Bearer ${apiKey.trim()}")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        val bodyString = executeRequest("Perplexity", request)
        val root = JSONObject(bodyString)
        val content = root.optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content")
            .orEmpty()
            .trim()

        val sources = buildList {
            val citations = root.optJSONArray("citations")
            if (citations != null) {
                for (i in 0 until citations.length()) {
                    val url = citations.optString(i).trim()
                    if (url.isNotBlank()) add(url)
                }
            }
        }
        PerplexityResult(content = content, sources = sources)
    }

    suspend fun diagnoseWithOpenAi(
        apiKey: String,
        vehicleInfo: VehicleInfo,
        metrics: AcousticMetrics,
        geminiDescription: String,
        perplexityResult: PerplexityResult?
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
            if (perplexityResult != null && perplexityResult.content.isNotBlank()) {
                appendLine()
                appendLine("=== PANNES CONNUES DU MODÈLE (PERPLEXITY WEB) ===")
                appendLine(perplexityResult.content)
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

        val root = JSONObject(rawResponse)
        val text = root.optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content")
            .orEmpty()
            .trim()

        if (text.isBlank()) {
            throw DiagnosticApiException.ProviderError("OpenAI", "Réponse vide reçue.")
        }
        DiagnosisResponseParser.parse(text)
    }

    private fun callOpenAiChat(
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

    private fun executeRequest(
        provider: String,
        request: Request,
        allowModelFallback: Boolean = false
    ): String {
        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    return body
                }

                val code = response.code
                val lowerBody = body.lowercase()

                if (allowModelFallback && (code == 404 || (code == 400 && lowerBody.contains("model")))) {
                    throw ModelNotFoundException()
                }

                when (code) {
                    401, 403 -> {
                        if (lowerBody.contains("quota") || lowerBody.contains("credit") || lowerBody.contains("billing")) {
                            throw DiagnosticApiException.InsufficientCredit(provider)
                        }
                        throw DiagnosticApiException.InvalidApiKey(provider)
                    }
                    402, 429 -> throw DiagnosticApiException.InsufficientCredit(provider)
                    400 -> {
                        if (lowerBody.contains("api_key_invalid") || lowerBody.contains("invalid api key")) {
                            throw DiagnosticApiException.InvalidApiKey(provider)
                        }
                        val msg = extractErrorMessage(body) ?: "Requête refusée (HTTP 400)."
                        throw DiagnosticApiException.ProviderError(provider, msg)
                    }
                    else -> {
                        val msg = extractErrorMessage(body) ?: "Erreur serveur (HTTP $code)."
                        throw DiagnosticApiException.ProviderError(provider, msg)
                    }
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
        } catch (_: SocketTimeoutException) {
            throw DiagnosticApiException.AnalysisTimeout()
        } catch (e: IOException) {
            throw DiagnosticApiException.ProviderError(provider, e.localizedMessage ?: "Erreur réseau")
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
