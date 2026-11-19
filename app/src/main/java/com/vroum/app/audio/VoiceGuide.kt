package com.vroum.app.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class VoiceGuide(context: Context) {

    private var tts: TextToSpeech? = null
    private var isReady = false
    private var pendingUtterance: String? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.FRENCH)
                isReady = result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED
                if (isReady) {
                    tts?.setSpeechRate(1.0f)
                    pendingUtterance?.let {
                        speak(it)
                        pendingUtterance = null
                    }
                }
            }
        }
    }

    fun speak(message: String) {
        if (!isReady) {
            pendingUtterance = message
            return
        }
        tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "vroum_guide_${System.currentTimeMillis()}")
    }

    fun stop() {
        pendingUtterance = null
        tts?.stop()
    }

    fun release() {
        stop()
        tts?.shutdown()
        tts = null
    }
}
