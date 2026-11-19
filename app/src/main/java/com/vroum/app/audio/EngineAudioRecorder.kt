package com.vroum.app.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

data class RecordedEngineAudio(
    val samples: ShortArray,
    val wavBytes: ByteArray,
    val idleEndSampleIndex: Int,
    val peakAmbientWarningTriggered: Boolean
)

class EngineAudioRecorder {

    companion object {
        const val RECORD_SECONDS = 30
        const val PHASE_SECONDS = RECORD_SECONDS / 3
        const val SAMPLE_RATE = WavEncoder.SAMPLE_RATE
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        // Threshold above which ambient/wind/chatter noise risks masking subtle engine ticks
        private const val EXCESSIVE_NOISE_DB = -9.5
    }

    @SuppressLint("MissingPermission")
    suspend fun recordGuidedSession(
        idleDurationSeconds: Int = PHASE_SECONDS,
        accelDurationSeconds: Int = PHASE_SECONDS,
        cooldownDurationSeconds: Int = RECORD_SECONDS - (PHASE_SECONDS * 2),
        onPhaseChanged: (
            phase: Int,
            secondsRemaining: Int,
            totalSecondsRemaining: Int,
            totalDurationSeconds: Int
        ) -> Unit,
        onAmplitudeUpdate: (normalizedAmplitude: Float, isTooNoisy: Boolean) -> Unit
    ): RecordedEngineAudio = withContext(Dispatchers.IO) {
        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        val bufferSize = maxOf(minBufferSize, SAMPLE_RATE / 2)

        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        )

        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            throw IllegalStateException("Impossible d'initialiser le microphone.")
        }

        val totalSeconds = idleDurationSeconds + accelDurationSeconds + cooldownDurationSeconds
        val targetTotalSamples = SAMPLE_RATE * totalSeconds
        val idleSamplesTarget = SAMPLE_RATE * idleDurationSeconds
        val captured = ShortArray(targetTotalSamples)
        var capturedCount = 0

        val readBuffer = ShortArray(1024)
        var highNoiseFrames = 0
        var ambientWarningTriggered = false

        try {
            audioRecord.startRecording()
            val startTimeMs = System.currentTimeMillis()
            var lastReportedSecond = -1

            while (currentCoroutineContext().isActive && capturedCount < targetTotalSamples) {
                val read = audioRecord.read(readBuffer, 0, readBuffer.size)
                if (read > 0) {
                    val remaining = targetTotalSamples - capturedCount
                    val toCopy = minOf(read, remaining)
                    System.arraycopy(readBuffer, 0, captured, capturedCount, toCopy)
                    capturedCount += toCopy

                    var sumSq = 0.0
                    var maxAmp = 0
                    for (i in 0 until read) {
                        val s = readBuffer[i].toInt()
                        sumSq += (s / 32768.0) * (s / 32768.0)
                        val a = abs(s)
                        if (a > maxAmp) maxAmp = a
                    }
                    val rms = sqrt(sumSq / read)
                    val db = if (rms < 1e-6) -90.0 else 20.0 * log10(rms)
                    val normalized = (maxAmp / 32768f).coerceIn(0f, 1f)

                    val elapsedSec = ((System.currentTimeMillis() - startTimeMs) / 1000).toInt()
                    val isIdlePhase = capturedCount < idleSamplesTarget
                    if (isIdlePhase && db > EXCESSIVE_NOISE_DB) {
                        highNoiseFrames++
                    } else {
                        highNoiseFrames = maxOf(0, highNoiseFrames - 1)
                    }
                    val tooNoisyNow = highNoiseFrames >= 8
                    if (tooNoisyNow) ambientWarningTriggered = true

                    withContext(Dispatchers.Main) {
                        onAmplitudeUpdate(normalized, tooNoisyNow)
                        if (elapsedSec != lastReportedSecond) {
                            lastReportedSecond = elapsedSec
                            val totalRemaining = (totalSeconds - elapsedSec).coerceAtLeast(1)
                            when {
                                elapsedSec < idleDurationSeconds -> {
                                    onPhaseChanged(
                                        1,
                                        (idleDurationSeconds - elapsedSec).coerceAtLeast(1),
                                        totalRemaining,
                                        totalSeconds
                                    )
                                }
                                elapsedSec < idleDurationSeconds + accelDurationSeconds -> {
                                    val accelElapsed = elapsedSec - idleDurationSeconds
                                    onPhaseChanged(
                                        2,
                                        (accelDurationSeconds - accelElapsed).coerceAtLeast(1),
                                        totalRemaining,
                                        totalSeconds
                                    )
                                }
                                else -> {
                                    val coolElapsed = elapsedSec - idleDurationSeconds - accelDurationSeconds
                                    onPhaseChanged(
                                        3,
                                        (cooldownDurationSeconds - coolElapsed).coerceAtLeast(1),
                                        totalRemaining,
                                        totalSeconds
                                    )
                                }
                            }
                        }
                    }
                } else if (read < 0) {
                    throw IllegalStateException("Erreur de lecture audio ($read).")
                }
            }

            withContext(Dispatchers.Main) {
                onPhaseChanged(4, 0, 0, totalSeconds)
            }
        } finally {
            try {
                if (audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    audioRecord.stop()
                }
            } catch (_: Exception) {
            }
            audioRecord.release()
        }

        val finalSamples = if (capturedCount == targetTotalSamples) {
            captured
        } else {
            captured.copyOfRange(0, capturedCount)
        }
        val wavBytes = WavEncoder.encodePcm16ToWav(finalSamples, SAMPLE_RATE)
        val idleEnd = minOf(idleSamplesTarget, finalSamples.size)

        RecordedEngineAudio(
            samples = finalSamples,
            wavBytes = wavBytes,
            idleEndSampleIndex = idleEnd,
            peakAmbientWarningTriggered = ambientWarningTriggered
        )
    }
}
