package com.example

import com.example.audio.AcousticAnalyzer
import com.example.audio.WavEncoder
import com.example.model.ConfidenceLevel
import com.example.network.DiagnosisResponseParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VroumUnitTest {

    @Test
    fun `wav encoder writes valid 16khz mono 16bit header`() {
        val samples = ShortArray(1600) { (it % 100).toShort() }
        val wav = WavEncoder.encodePcm16ToWav(samples, 16000)

        assertEquals(44 + samples.size * 2, wav.size)
        assertEquals("RIFF", String(wav.copyOfRange(0, 4)))
        assertEquals("WAVE", String(wav.copyOfRange(8, 12)))
        assertEquals("fmt ", String(wav.copyOfRange(12, 16)))
        assertEquals("data", String(wav.copyOfRange(36, 40)))

        val bb = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(1.toShort(), bb.getShort(20)) // PCM
        assertEquals(1.toShort(), bb.getShort(22)) // Mono
        assertEquals(16000, bb.getInt(24)) // Sample rate
        assertEquals(16.toShort(), bb.getShort(34)) // Bits per sample
    }

    @Test
    fun `acoustic analyzer detects dominant frequency and regularity`() {
        val sampleRate = 16000
        val targetHz = 50.0
        val totalSamples = sampleRate * 4
        val samples = ShortArray(totalSamples) { i ->
            val valDouble = sin(2.0 * PI * targetHz * i / sampleRate) * 12000.0
            valDouble.toInt().toShort()
        }

        val metrics = AcousticAnalyzer.analyze(samples, sampleRate, totalSamples / 2)
        assertTrue(
            "Expected dominant frequency near 50 Hz, got ${metrics.dominantFrequencyHz}",
            metrics.dominantFrequencyHz in 45.0..56.0
        )
        assertTrue("Expected regular signal", metrics.regularityScore >= 0.85)
        assertEquals("Régulier", metrics.regularityLabel)
        assertTrue("Expected RPM near 1500, got ${metrics.estimatedRpm}", metrics.estimatedRpm in 1350..1700)
    }

    @Test
    fun `diagnosis parser parses fenced json diagnosis`() {
        val raw = """
            ```json
            {
              "summary": "Petit cliquetis régulier au niveau du haut moteur.",
              "confidence": "moyen",
              "probable_causes": [
                {
                  "title": "Jeu aux soupapes",
                  "probability": "Probable",
                  "explanation": "Bruit rythmique léger qui suit le régime."
                }
              ],
              "self_checks": ["Vérifier le niveau d'huile moteur à froid."],
              "see_mechanic_recommended": true,
              "mechanic_advice": "À montrer à un garagiste lors de la prochaine visite."
            }
            ```
        """.trimIndent()

        val parsed = DiagnosisResponseParser.parse(raw)
        assertEquals(ConfidenceLevel.MOYEN, parsed.confidence)
        assertEquals(1, parsed.probableCauses.size)
        assertEquals("Jeu aux soupapes", parsed.probableCauses[0].title)
        assertEquals(1, parsed.selfChecks.size)
        assertTrue(parsed.seeMechanicRecommended)
    }

    @Test
    fun `vehicle info validates brand model and unknown bypass`() {
        val empty = com.example.model.VehicleInfo(type = com.example.model.VehicleType.MOTO)
        assertEquals(false, empty.hasBrandAndModel)
        assertEquals(false, empty.canProceedToListen)

        val skipped = empty.copy(unknownBrandModel = true, displacementCc = "689")
        assertEquals(false, skipped.hasBrandAndModel)
        assertEquals(true, skipped.canProceedToListen)
        assertEquals("Moto • 689 cc", skipped.displayTitle())

        val complete = com.example.model.VehicleInfo(
            type = com.example.model.VehicleType.MOTO,
            brand = "Yamaha",
            model = "MT-07",
            year = "2020",
            displacementCc = "689"
        )
        assertEquals(true, complete.hasBrandAndModel)
        assertEquals(true, complete.canProceedToListen)
        assertTrue(complete.searchDescriptor().contains("689cc"))
    }
}
