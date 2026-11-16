package com.example.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.audio.AcousticAnalyzer
import com.example.audio.RecordedEngineAudio
import com.example.model.ApiKeys
import com.example.model.ConfidenceLevel
import com.example.model.DiagnosticResult
import com.example.model.VehicleInfo
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout

enum class PipelineStep(val label: String) {
    LOCAL_DSP("Calcul acoustique local (Hz, tr/min, régularité)"),
    GEMINI_EAR("Écoute audio par Gemini"),
    PERPLEXITY_SEARCH("Recherche pannes connues sur le web"),
    OPENAI_CHEF("Synthèse mécanique par OpenAI")
}

class DiagnosticPipeline(
    private val context: Context,
    private val aiClients: EngineAiClients = EngineAiClients()
) {

    suspend fun runDiagnosis(
        keys: ApiKeys,
        vehicleInfo: VehicleInfo,
        audio: RecordedEngineAudio,
        onStepProgress: (PipelineStep) -> Unit
    ): DiagnosticResult {
        if (!isNetworkAvailable()) {
            throw DiagnosticApiException.NoInternet()
        }
        if (keys.geminiKey.isBlank()) {
            throw DiagnosticApiException.InvalidApiKey("Gemini")
        }
        if (keys.openAiKey.isBlank()) {
            throw DiagnosticApiException.InvalidApiKey("OpenAI")
        }

        return try {
            // Maximum total analysis duration: 2 minutes (120,000 ms)
            withTimeout(120_000L) {
                onStepProgress(PipelineStep.LOCAL_DSP)
                val metrics = AcousticAnalyzer.analyze(
                    samples = audio.samples,
                    idleEndSample = audio.idleEndSampleIndex
                )

                val optionalWarnings = mutableListOf<String>()

                onStepProgress(PipelineStep.GEMINI_EAR)
                val (geminiDesc, perplexityRes) = coroutineScope {
                    val geminiDeferred = async {
                        aiClients.describeAudioWithGemini(keys.geminiKey, audio.wavBytes)
                    }

                    val perplexityDeferred = if (keys.perplexityKey.isNotBlank() && vehicleInfo.hasBrandAndModel) {
                        async {
                            try {
                                onStepProgress(PipelineStep.PERPLEXITY_SEARCH)
                                aiClients.searchKnownFaultsWithPerplexity(keys.perplexityKey, vehicleInfo)
                            } catch (e: DiagnosticApiException) {
                                optionalWarnings.add("Recherche Perplexity ignorée : ${e.message}")
                                null
                            }
                        }
                    } else {
                        null
                    }

                    geminiDeferred.await() to perplexityDeferred?.await()
                }

                onStepProgress(PipelineStep.OPENAI_CHEF)
                val openAiResult = aiClients.diagnoseWithOpenAi(
                    apiKey = keys.openAiKey,
                    vehicleInfo = vehicleInfo,
                    metrics = metrics,
                    geminiDescription = geminiDesc,
                    perplexityResult = perplexityRes
                )

                val adjustedConfidence = if (!vehicleInfo.hasBrandAndModel) {
                    when (openAiResult.confidence) {
                        ConfidenceLevel.ELEVE -> ConfidenceLevel.MOYEN
                        ConfidenceLevel.MOYEN -> ConfidenceLevel.FAIBLE
                        ConfidenceLevel.FAIBLE -> ConfidenceLevel.FAIBLE
                    }
                } else {
                    openAiResult.confidence
                }

                DiagnosticResult(
                    timestampMillis = System.currentTimeMillis(),
                    vehicleInfo = vehicleInfo,
                    summary = openAiResult.summary,
                    confidence = adjustedConfidence,
                    probableCauses = openAiResult.probableCauses,
                    selfChecks = openAiResult.selfChecks,
                    seeMechanicRecommended = openAiResult.seeMechanicRecommended,
                    mechanicAdvice = openAiResult.mechanicAdvice,
                    acousticMetrics = metrics,
                    geminiDescription = geminiDesc,
                    openAiDescription = null,
                    perplexitySummary = perplexityRes?.content,
                    perplexitySources = perplexityRes?.sources ?: emptyList(),
                    optionalWarnings = optionalWarnings
                )
            }
        } catch (_: TimeoutCancellationException) {
            throw DiagnosticApiException.AnalysisTimeout()
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
