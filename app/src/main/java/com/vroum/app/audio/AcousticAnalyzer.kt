package com.vroum.app.audio

import com.vroum.app.model.AcousticMetrics
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object AcousticAnalyzer {

    private const val FFT_SIZE = 4096

    fun computeRmsDb(samples: ShortArray, startIndex: Int = 0, endIndex: Int = samples.size): Double {
        val safeStart = max(0, startIndex)
        val safeEnd = min(samples.size, endIndex)
        if (safeEnd <= safeStart) return -90.0

        var sumSquares = 0.0
        for (i in safeStart until safeEnd) {
            val norm = samples[i] / 32768.0
            sumSquares += norm * norm
        }
        val rms = sqrt(sumSquares / (safeEnd - safeStart))
        return if (rms < 1e-6) -90.0 else 20.0 * log10(rms)
    }

    fun analyze(
        samples: ShortArray,
        sampleRate: Int = WavEncoder.SAMPLE_RATE,
        idleEndSample: Int = samples.size / 2
    ): AcousticMetrics {
        if (samples.isEmpty()) {
            return AcousticMetrics(
                dominantFrequencyHz = 0.0,
                estimatedRpm = 0,
                regularityScore = 0.0,
                regularityLabel = "Signal insuffisant",
                abnormalPeaksDetected = false,
                peakCount = 0,
                idleRmsDb = -90.0,
                accelRmsDb = -90.0,
                sampleRateHz = sampleRate,
                durationSeconds = 0.0
            )
        }

        val splitIndex = idleEndSample.coerceIn(1, samples.size)
        val idleSamples = samples.copyOfRange(0, splitIndex)
        val accelSamples = if (splitIndex < samples.size) {
            samples.copyOfRange(splitIndex, samples.size)
        } else {
            samples
        }

        val idleRmsDb = computeRmsDb(idleSamples)
        val accelRmsDb = computeRmsDb(accelSamples)

        val dominantFreqHz = estimateDominantFrequency(idleSamples, sampleRate)
        val estimatedRpm = estimateRpmFromFrequency(dominantFreqHz)

        val (regularityScore, regularityLabel) = computeRegularity(idleSamples, sampleRate)
        val peakCount = detectAbnormalPeaks(samples, sampleRate)
        val abnormalPeaksDetected = peakCount >= 3

        return AcousticMetrics(
            dominantFrequencyHz = (dominantFreqHz * 10.0).roundToInt() / 10.0,
            estimatedRpm = estimatedRpm,
            regularityScore = (regularityScore * 100.0).roundToInt() / 100.0,
            regularityLabel = regularityLabel,
            abnormalPeaksDetected = abnormalPeaksDetected,
            peakCount = peakCount,
            idleRmsDb = (idleRmsDb * 10.0).roundToInt() / 10.0,
            accelRmsDb = (accelRmsDb * 10.0).roundToInt() / 10.0,
            sampleRateHz = sampleRate,
            durationSeconds = (samples.size.toDouble() / sampleRate * 10.0).roundToInt() / 10.0
        )
    }

    internal fun estimateDominantFrequency(samples: ShortArray, sampleRate: Int): Double {
        if (samples.size < 512) return 0.0

        val hopSize = FFT_SIZE / 2
        val numBins = FFT_SIZE / 2
        val accumulatedMagnitudes = DoubleArray(numBins)
        var frameCount = 0

        val real = DoubleArray(FFT_SIZE)
        val imag = DoubleArray(FFT_SIZE)

        var offset = 0
        while (offset + FFT_SIZE <= samples.size || frameCount == 0) {
            real.fill(0.0)
            imag.fill(0.0)

            val available = min(FFT_SIZE, samples.size - offset)
            if (available <= 0) break

            var mean = 0.0
            for (i in 0 until available) {
                mean += samples[offset + i]
            }
            mean /= available

            for (i in 0 until available) {
                val window = 0.54 - 0.46 * cos(2.0 * PI * i / (FFT_SIZE - 1))
                real[i] = ((samples[offset + i] - mean) / 32768.0) * window
            }

            fft(real, imag)

            for (bin in 1 until numBins) {
                accumulatedMagnitudes[bin] += hypot(real[bin], imag[bin])
            }
            frameCount++
            offset += hopSize
            if (samples.size < FFT_SIZE) break
        }

        if (frameCount == 0) return 0.0

        val minBin = max(1, ((20.0 * FFT_SIZE) / sampleRate).roundToInt())
        val maxBin = min(numBins - 1, ((2500.0 * FFT_SIZE) / sampleRate).roundToInt())
        var bestBin = minBin
        var bestMag = -1.0
        for (bin in minBin..maxBin) {
            if (accumulatedMagnitudes[bin] > bestMag) {
                bestMag = accumulatedMagnitudes[bin]
                bestBin = bin
            }
        }
        return if (bestMag <= 1e-6) 0.0 else (bestBin.toDouble() * sampleRate) / FFT_SIZE
    }

    internal fun estimateRpmFromFrequency(dominantFreqHz: Double): Int {
        if (dominantFreqHz <= 0.0) return 0
        var fundamental = dominantFreqHz
        while (fundamental > 160.0) {
            fundamental /= 2.0
        }
        val estimated = (fundamental * 30.0).roundToInt()
        return estimated.coerceIn(550, 9000)
    }

    private fun computeRegularity(samples: ShortArray, sampleRate: Int): Pair<Double, String> {
        val frameSize = max(1, (sampleRate * 0.05).roundToInt())
        val energies = mutableListOf<Double>()
        var idx = 0
        while (idx + frameSize <= samples.size) {
            var sumSq = 0.0
            for (i in idx until idx + frameSize) {
                val n = samples[i] / 32768.0
                sumSq += n * n
            }
            energies.add(sqrt(sumSq / frameSize))
            idx += frameSize
        }
        if (energies.size < 4) return 0.0 to "Signal insuffisant"

        val mean = energies.average()
        if (mean < 1e-4) return 0.0 to "Signal très faible"

        val variance = energies.map { (it - mean) * (it - mean) }.average()
        val cv = sqrt(variance) / mean
        val score = (1.0 - cv).coerceIn(0.0, 1.0)

        val label = when {
            score >= 0.78 -> "Régulier"
            score >= 0.55 -> "Légère irrégularité"
            else -> "Irrégulier"
        }
        return score to label
    }

    private fun detectAbnormalPeaks(samples: ShortArray, sampleRate: Int): Int {
        val frameSize = max(1, (sampleRate * 0.02).roundToInt())
        val framePeaks = mutableListOf<Double>()
        var idx = 0
        while (idx + frameSize <= samples.size) {
            var maxAbs = 0.0
            for (i in idx until idx + frameSize) {
                val absVal = kotlin.math.abs(samples[i] / 32768.0)
                if (absVal > maxAbs) maxAbs = absVal
            }
            framePeaks.add(maxAbs)
            idx += frameSize
        }
        if (framePeaks.size < 10) return 0

        val sorted = framePeaks.sorted()
        val median = sorted[sorted.size / 2]
        if (median < 0.005) return 0

        val threshold = max(median * 3.0, 0.25)
        var abnormalCount = 0
        var lastPeakFrame = -10
        for (i in framePeaks.indices) {
            if (framePeaks[i] > threshold && (i - lastPeakFrame) >= 5) {
                abnormalCount++
                lastPeakFrame = i
            }
        }
        return abnormalCount
    }

    private fun fft(real: DoubleArray, imag: DoubleArray) {
        val n = real.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tmpR = real[i]
                real[i] = real[j]
                real[j] = tmpR
                val tmpI = imag[i]
                imag[i] = imag[j]
                imag[j] = tmpI
            }
        }

        var len = 2
        while (len <= n) {
            val angle = -2.0 * PI / len
            val wLenR = cos(angle)
            val wLenI = sin(angle)
            var i = 0
            while (i < n) {
                var wR = 1.0
                var wI = 0.0
                for (k in 0 until len / 2) {
                    val uR = real[i + k]
                    val uI = imag[i + k]
                    val tR = real[i + k + len / 2] * wR - imag[i + k + len / 2] * wI
                    val tI = real[i + k + len / 2] * wI + imag[i + k + len / 2] * wR
                    real[i + k] = uR + tR
                    imag[i + k] = uI + tI
                    real[i + k + len / 2] = uR - tR
                    imag[i + k + len / 2] = uI - tI
                    val nextWR = wR * wLenR - wI * wLenI
                    wI = wR * wLenI + wI * wLenR
                    wR = nextWR
                }
                i += len
            }
            len = len shl 1
        }
    }
}
