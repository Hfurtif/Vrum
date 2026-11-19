package com.vroum.app

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.vroum.app.audio.AcousticAnalyzer
import com.vroum.app.audio.EngineAudioRecorder
import com.vroum.app.audio.WavEncoder
import com.vroum.app.model.ApiKeys
import com.vroum.app.model.ConfidenceLevel
import com.vroum.app.model.VehicleInfo
import com.vroum.app.model.VehicleType
import com.vroum.app.network.DiagnosisResponseParser
import com.vroum.app.network.DiagnosticApiException
import com.vroum.app.photo.VehiclePhotoHelper
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.max
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
        val empty = VehicleInfo(type = VehicleType.MOTO)
        assertFalse(empty.hasBrandAndModel)
        assertFalse(empty.canProceedToListen)

        val skipped = empty.copy(unknownBrandModel = true, displacementCc = "689")
        assertFalse(skipped.hasBrandAndModel)
        assertTrue(skipped.canProceedToListen)
        assertEquals("Moto • 689 cc", skipped.displayTitle())

        val complete = VehicleInfo(
            type = VehicleType.MOTO,
            brand = "Yamaha",
            model = "MT-07",
            year = "2020",
            displacementCc = "689"
        )
        assertTrue(complete.hasBrandAndModel)
        assertTrue(complete.canProceedToListen)
        assertTrue(complete.searchDescriptor().contains("689cc"))
    }

    @Test
    fun `vehicle identification parser handles high confidence and unknown fields`() {
        val raw = """
            ```json
            {
              "type_vehicule": "moto",
              "marque": "Yamaha",
              "modele": "MT-07",
              "annee_approximative": "2020",
              "cylindree": "689 cc",
              "type_moteur": "Bicylindre en ligne 4 temps",
              "niveau_certitude": "élevé",
              "champ_inconnu_extra": "valeur ignorée",
              "suggestions": []
            }
            ```
        """.trimIndent()

        val result = DiagnosisResponseParser.parseVehicleIdentificationOrThrow(raw)
        assertEquals(VehicleType.MOTO, result.vehicleType)
        assertEquals("Yamaha", result.brand)
        assertEquals("MT-07", result.model)
        assertEquals("2020", result.year)
        assertEquals("689", result.displacementCc)
        assertEquals("Bicylindre en ligne 4 temps", result.engineType)
        assertEquals(ConfidenceLevel.ELEVE, result.confidence)
        assertEquals("C'est une Yamaha MT-07 2020 ?", result.confirmationQuestion())
    }

    @Test
    fun `vehicle identification parser tolerates null fields and keeps multiple suggestions`() {
        val raw = """
            {
              "type_vehicule": "moto",
              "marque": "Kawasaki",
              "modele": "Z650",
              "annee_approximative": null,
              "cylindree": "649",
              "type_moteur": "Bicylindre parallèle 4 temps",
              "niveau_certitude": "faible",
              "suggestions": [
                {
                  "marque": "Kawasaki",
                  "modele": "Z900",
                  "annee_approximative": "2020",
                  "cylindree": "948",
                  "type_moteur": "4 cylindres en ligne"
                }
              ]
            }
        """.trimIndent()

        val result = DiagnosisResponseParser.parseVehicleIdentificationOrThrow(raw)
        assertEquals(ConfidenceLevel.FAIBLE, result.confidence)
        assertEquals("", result.year)
        assertEquals(2, result.suggestions.size)
        assertEquals("Z650", result.suggestions[0].model)
        assertEquals("Z900", result.suggestions[1].model)
    }

    @Test(expected = DiagnosticApiException.InvalidResponse::class)
    fun `vehicle identification parser throws invalid response on malformed text`() {
        DiagnosisResponseParser.parseVehicleIdentificationOrThrow("Ceci n'est pas du JSON valide")
    }

    @Test
    fun `vehicle photo helper scales longest side to max 1024px and produces jpeg`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val tempFile = File(context.cacheDir, "test_moto_large.jpg")
        val sourceBitmap = Bitmap.createBitmap(2400, 1200, Bitmap.Config.ARGB_8888)
        FileOutputStream(tempFile).use { fos ->
            sourceBitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
        }
        sourceBitmap.recycle()

        val prepared = VehiclePhotoHelper.preparePhotoForGemini(
            context = context,
            uri = Uri.fromFile(tempFile)
        )
        assertTrue("Expected non-empty JPEG bytes", prepared.jpegBytes.isNotEmpty())
        val decoded = BitmapFactory.decodeByteArray(prepared.jpegBytes, 0, prepared.jpegBytes.size)
        val longestSide = max(decoded.width, decoded.height)
        assertTrue("Expected longest side <= 1024 px, got $longestSide", longestSide <= 1024)
        decoded.recycle()
        tempFile.delete()
    }

    @Test
    fun `api keys require gemini and openai while deepseek stays optional`() {
        assertFalse(ApiKeys(geminiKey = "g", openAiKey = "", deepSeekKey = "d").hasRequiredKeys)
        assertFalse(ApiKeys(geminiKey = "", openAiKey = "o", deepSeekKey = "d").hasRequiredKeys)
        assertTrue(ApiKeys(geminiKey = "g", openAiKey = "o", deepSeekKey = "").hasRequiredKeys)
        assertEquals(30, EngineAudioRecorder.RECORD_SECONDS)
    }
}
