package com.vroum.app.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.vroum.app.audio.AcousticAnalyzer
import com.vroum.app.audio.RecordedEngineAudio
import com.vroum.app.model.ApiKeys
import com.vroum.app.model.ConfidenceLevel
import com.vroum.app.model.DiagnosticResult
import com.vroum.app.model.VehicleInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

enum class PipelineStep(val label: String) {
    LOCAL_DSP("Calcul acoustique local (Hz, tr/min, régularité)"),
    GEMINI_EAR("Écoute audio par Gemini"),
    DEEPSEEK_SEARCH("Pannes courantes (connaissances de l'IA)"),
    OPENAI_CHEF("Synthèse mécanique par OpenAI")
}

class DiagnosticPipeline(
    private val context: Context,
    private val aiClients: EngineAiClients = EngineAiClients()
) {

    companion object {
        const val DEEPSEEK_TIMEOUT_MS = 15_000L
    }

    suspend fun runDiagnosis(
        keys: ApiKeys,
        vehicleInfo: VehicleInfo,
        audio: RecordedEngineAudio,
        prefetchedDeepSeekResult: DeepSeekResult? = null,
        prefetchedDeepSeekWarning: String? = null,
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
            withTimeout(120_000L) {
                onStepProgress(PipelineStep.LOCAL_DSP)
                val metrics = AcousticAnalyzer.analyze(
                    samples = audio.samples,
                    idleEndSample = audio.idleEndSampleIndex
                )

                val optionalWarnings = mutableListOf<String>()
                if (!prefetchedDeepSeekWarning.isNullOrBlank()) {
                    optionalWarnings.add(prefetchedDeepSeekWarning)
                }

                onStepProgress(PipelineStep.GEMINI_EAR)
                val (geminiDesc, deepSeekRes) = coroutineScope {
                    val geminiDeferred = async {
                        aiClients.describeAudioWithGemini(keys.geminiKey, audio.wavBytes)
                    }

                    val shouldFetchDeepSeek = prefetchedDeepSeekResult == null &&
                        prefetchedDeepSeekWarning == null &&
                        keys.deepSeekKey.isNotBlank() &&
                        vehicleInfo.hasBrandAndModel

                    val deepSeekDeferred = if (shouldFetchDeepSeek) {
                        async {
                            try {
                                onStepProgress(PipelineStep.DEEPSEEK_SEARCH)
                                val res = withTimeoutOrNull(DEEPSEEK_TIMEOUT_MS) {
                                    aiClients.searchKnownFaultsWithDeepSeek(keys.deepSeekKey, vehicleInfo)
                                }
                                if (res == null) {
                                    optionalWarnings.add(
                                        "Pannes courantes (connaissances de l'IA) ignorées : délai de 15 s dépassé."
                                    )
                                }
                                res
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                optionalWarnings.add(
                                    "Pannes courantes (connaissances de l'IA) ignorées : ${e.message ?: "erreur réseau"}"
                                )
                                null
                            }
                        }
                    } else {
                        null
                    }

                    geminiDeferred.await() to (prefetchedDeepSeekResult ?: deepSeekDeferred?.await())
                }

                onStepProgress(PipelineStep.OPENAI_CHEF)
                val openAiResult = aiClients.diagnoseWithOpenAi(
                    apiKey = keys.openAiKey,
                    vehicleInfo = vehicleInfo,
                    metrics = metrics,
                    geminiDescription = geminiDesc,
                    deepSeekResult = deepSeekRes
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
                    deepSeekSummary = deepSeekRes?.content,
                    deepSeekSources = emptyList(),
                    optionalWarnings = optionalWarnings
                )
            }
        } catch (_: TimeoutCancellationException) {
            throw DiagnosticApiException.AnalysisTimeout()
        }
    }

    fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
