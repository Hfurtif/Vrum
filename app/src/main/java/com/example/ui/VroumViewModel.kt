package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.audio.EngineAudioRecorder
import com.example.audio.VoiceGuide
import com.example.data.local.DiagnosticRepository
import com.example.data.local.VroumDatabase
import com.example.model.ApiKeys
import com.example.model.DiagnosticResult
import com.example.model.VehicleInfo
import com.example.model.VehicleType
import com.example.network.DiagnosticApiException
import com.example.network.DiagnosticPipeline
import com.example.network.PipelineStep
import com.example.security.KeyStorage
import com.example.security.SecureKeyStorage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class HomeTab {
    LISTEN,
    HISTORY
}

sealed interface EngineSessionState {
    data object Idle : EngineSessionState
    data class Recording(
        val phase: Int,
        val secondsRemaining: Int,
        val totalSecondsRemaining: Int = 30,
        val totalDurationSeconds: Int = 30,
        val instructionText: String,
        val amplitude: Float,
        val isTooNoisy: Boolean
    ) : EngineSessionState

    data class Analyzing(
        val currentStep: PipelineStep,
        val completedSteps: Set<PipelineStep>
    ) : EngineSessionState

    data class Error(
        val message: String,
        val isKeyError: Boolean
    ) : EngineSessionState
}

data class VroumUiState(
    val apiKeys: ApiKeys = ApiKeys(),
    val vehicleInfo: VehicleInfo = VehicleInfo(),
    val activeTab: HomeTab = HomeTab.LISTEN,
    val sessionState: EngineSessionState = EngineSessionState.Idle,
    val activeResult: DiagnosticResult? = null,
    val micPermissionDeniedMessage: String? = null
)

class VroumViewModel(application: Application) : AndroidViewModel(application) {

    private val keyStorage: KeyStorage = SecureKeyStorage(application)
    private val repository: DiagnosticRepository = DiagnosticRepository(
        VroumDatabase.getInstance(application).diagnosticDao()
    )
    private val recorder = EngineAudioRecorder()
    private val pipeline = DiagnosticPipeline(application)
    private val voiceGuide = VoiceGuide(application)

    private val _uiState = MutableStateFlow(
        VroumUiState(apiKeys = loadInitialKeys())
    )
    val uiState: StateFlow<VroumUiState> = _uiState.asStateFlow()

    private fun loadInitialKeys(): ApiKeys {
        val stored = keyStorage.loadKeys()
        val envGemini = BuildConfig.GEMINI_API_KEY.trim()
            .takeIf { it.isNotEmpty() && it != "MY_GEMINI_API_KEY" }.orEmpty()
        val envOpenAi = BuildConfig.OPENAI_API_KEY.trim()
            .takeIf { it.isNotEmpty() && it != "MY_OPENAI_API_KEY" }.orEmpty()
        val merged = stored.copy(
            geminiKey = stored.geminiKey.ifBlank { envGemini },
            openAiKey = stored.openAiKey.ifBlank { envOpenAi }
        )
        if (merged != stored && (merged.geminiKey.isNotBlank() || merged.openAiKey.isNotBlank())) {
            keyStorage.saveKeys(merged)
        }
        return merged
    }

    val history: StateFlow<List<DiagnosticResult>> = repository.allDiagnostics
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private var sessionJob: Job? = null

    fun hasRequiredKeys(): Boolean = _uiState.value.apiKeys.hasRequiredKeys

    fun saveApiKeys(keys: ApiKeys) {
        keyStorage.saveKeys(keys)
        _uiState.update { it.copy(apiKeys = keyStorage.loadKeys()) }
    }

    fun selectVehicleType(type: VehicleType) {
        _uiState.update { it.copy(vehicleInfo = it.vehicleInfo.copy(type = type)) }
    }

    fun updateVehicleFields(
        brand: String? = null,
        model: String? = null,
        year: String? = null,
        displacementCc: String? = null,
        mileage: String? = null
    ) {
        _uiState.update { state ->
            val current = state.vehicleInfo
            val nextBrand = brand ?: current.brand
            val nextModel = model ?: current.model
            val resetUnknown = if (brand != null || model != null) false else current.unknownBrandModel
            state.copy(
                vehicleInfo = current.copy(
                    brand = nextBrand,
                    model = nextModel,
                    year = year ?: current.year,
                    displacementCc = displacementCc ?: current.displacementCc,
                    mileage = mileage ?: current.mileage,
                    unknownBrandModel = resetUnknown
                )
            )
        }
    }

    fun setUnknownBrandModel(unknown: Boolean) {
        _uiState.update { state ->
            state.copy(
                vehicleInfo = state.vehicleInfo.copy(
                    unknownBrandModel = unknown,
                    brand = if (unknown) "" else state.vehicleInfo.brand,
                    model = if (unknown) "" else state.vehicleInfo.model
                )
            )
        }
    }

    fun selectHomeTab(tab: HomeTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun onMicPermissionDenied() {
        _uiState.update {
            it.copy(
                micPermissionDeniedMessage =
                    "L'autorisation du micro est refusée. Vroum en a besoin pour écouter ton moteur. " +
                        "Active le micro dans les réglages ou réessaie."
            )
        }
    }

    fun clearMicPermissionError() {
        _uiState.update { it.copy(micPermissionDeniedMessage = null) }
    }

    fun startListeningAndDiagnose(onDiagnosticReady: (Long) -> Unit) {
        if (_uiState.value.sessionState is EngineSessionState.Recording ||
            _uiState.value.sessionState is EngineSessionState.Analyzing
        ) {
            return
        }

        _uiState.update { it.copy(micPermissionDeniedMessage = null) }
        val keys = _uiState.value.apiKeys
        val vehicle = _uiState.value.vehicleInfo

        if (!keys.hasRequiredKeys) {
            _uiState.update {
                it.copy(
                    sessionState = EngineSessionState.Error(
                        message = "Les clés Gemini et OpenAI sont obligatoires. Configure-les via l'icône ⚙️.",
                        isKeyError = true
                    )
                )
            }
            return
        }

        sessionJob?.cancel()
        sessionJob = viewModelScope.launch {
            var lastSpokenPhase = 0
            try {
                val phase1Text = "Moteur au ralenti, ne touchez à rien"
                val phase2Text = "Accélérez doucement jusqu'à 4000 tr/min, puis relâchez"
                val phase3Text = "Laissez revenir au ralenti, puis coupez le moteur"
                val phase4Text = "Coupez le moteur"

                _uiState.update {
                    it.copy(
                        sessionState = EngineSessionState.Recording(
                            phase = 1,
                            secondsRemaining = 10,
                            totalSecondsRemaining = 30,
                            totalDurationSeconds = 30,
                            instructionText = phase1Text,
                            amplitude = 0f,
                            isTooNoisy = false
                        )
                    )
                }
                lastSpokenPhase = 1
                voiceGuide.speak(phase1Text)

                val recorded = recorder.recordGuidedSession(
                    idleDurationSeconds = 10,
                    accelDurationSeconds = 10,
                    cooldownDurationSeconds = 10,
                    onPhaseChanged = { phase, remaining, totalRemaining, totalDuration ->
                        val text = when (phase) {
                            1 -> phase1Text
                            2 -> phase2Text
                            3 -> phase3Text
                            else -> phase4Text
                        }
                        if (phase != lastSpokenPhase) {
                            lastSpokenPhase = phase
                            val speechText = when (phase) {
                                2 -> "Accélérez doucement jusqu'à 4000 tours par minute, puis relâchez"
                                3 -> phase3Text
                                4 -> phase4Text
                                else -> text
                            }
                            voiceGuide.speak(speechText)
                        }
                        _uiState.update { state ->
                            val currentRec = state.sessionState as? EngineSessionState.Recording
                            state.copy(
                                sessionState = EngineSessionState.Recording(
                                    phase = phase,
                                    secondsRemaining = remaining,
                                    totalSecondsRemaining = totalRemaining,
                                    totalDurationSeconds = totalDuration,
                                    instructionText = text,
                                    amplitude = currentRec?.amplitude ?: 0f,
                                    isTooNoisy = currentRec?.isTooNoisy ?: false
                                )
                            )
                        }
                    },
                    onAmplitudeUpdate = { amp, noisy ->
                        _uiState.update { state ->
                            val currentRec = state.sessionState as? EngineSessionState.Recording
                            if (currentRec != null) {
                                state.copy(
                                    sessionState = currentRec.copy(
                                        amplitude = amp,
                                        isTooNoisy = noisy
                                    )
                                )
                            } else {
                                state
                            }
                        }
                    }
                )

                // Short pause so user hears "Coupez le moteur" cleanly before spinner appears
                delay(1100)

                val completed = mutableSetOf<PipelineStep>()
                _uiState.update {
                    it.copy(
                        sessionState = EngineSessionState.Analyzing(
                            currentStep = PipelineStep.LOCAL_DSP,
                            completedSteps = emptySet()
                        )
                    )
                }

                val result = pipeline.runDiagnosis(
                    keys = keys,
                    vehicleInfo = vehicle,
                    audio = recorded,
                    onStepProgress = { step ->
                        _uiState.update { state ->
                            val prev = (state.sessionState as? EngineSessionState.Analyzing)?.currentStep
                            if (prev != null && prev != step) {
                                completed.add(prev)
                            }
                            state.copy(
                                sessionState = EngineSessionState.Analyzing(
                                    currentStep = step,
                                    completedSteps = completed.toSet()
                                )
                            )
                        }
                    }
                )

                val savedId = repository.save(result)
                val savedResult = result.copy(id = savedId)

                _uiState.update {
                    it.copy(
                        sessionState = EngineSessionState.Idle,
                        activeResult = savedResult
                    )
                }
                onDiagnosticReady(savedId)
            } catch (e: DiagnosticApiException) {
                val isKeyIssue = e is DiagnosticApiException.InvalidApiKey ||
                    e is DiagnosticApiException.InsufficientCredit
                _uiState.update {
                    it.copy(
                        sessionState = EngineSessionState.Error(
                            message = e.message ?: "Une erreur est survenue pendant l'analyse.",
                            isKeyError = isKeyIssue
                        )
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                _uiState.update { it.copy(sessionState = EngineSessionState.Idle) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        sessionState = EngineSessionState.Error(
                            message = e.localizedMessage
                                ?: "Impossible d'enregistrer ou d'analyser le bruit moteur.",
                            isKeyError = false
                        )
                    )
                }
            }
        }
    }

    fun cancelSession() {
        sessionJob?.cancel()
        sessionJob = null
        voiceGuide.stop()
        _uiState.update { it.copy(sessionState = EngineSessionState.Idle) }
    }

    fun dismissError() {
        _uiState.update { it.copy(sessionState = EngineSessionState.Idle) }
    }

    fun openHistoryItem(result: DiagnosticResult) {
        _uiState.update { it.copy(activeResult = result) }
    }

    fun loadDiagnosticById(id: Long) {
        val current = _uiState.value.activeResult
        if (current?.id == id) return
        viewModelScope.launch {
            val found = repository.getById(id)
            if (found != null) {
                _uiState.update { it.copy(activeResult = found) }
            }
        }
    }

    fun deleteDiagnostic(id: Long) {
        viewModelScope.launch {
            repository.deleteById(id)
        }
    }

    override fun onCleared() {
        super.onCleared()
        sessionJob?.cancel()
        voiceGuide.release()
    }
}
