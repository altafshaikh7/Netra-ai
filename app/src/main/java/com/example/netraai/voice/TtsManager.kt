package com.example.netraai.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class TtsManager(context: Context) {
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var currentLocale: Locale = Locale.forLanguageTag("en-IN")
    private val pendingUtterances = mutableListOf<String>()

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(currentLocale)
                if ((result == TextToSpeech.LANG_MISSING_DATA) || (result == TextToSpeech.LANG_NOT_SUPPORTED)) {
                    Log.e("TtsManager", "Language $currentLocale not supported, defaulting to US")
                    tts?.language = Locale.US
                }
                isInitialized = synchronized(this) {
                    true
                }
                synchronized(pendingUtterances) {
                    for (utterance in pendingUtterances) {
                        speakInternal(utterance, interrupt = false)
                    }
                    pendingUtterances.clear()
                }
            } else {
                Log.e("TtsManager", "TTS initialization failed")
            }
        }
    }

    fun speak(text: String, interrupt: Boolean = false) {
        synchronized(this) {
            if (!isInitialized) {
                synchronized(pendingUtterances) {
                    pendingUtterances.add(text)
                }
                return
            }
        }
        speakInternal(text, interrupt)
    }

    private fun speakInternal(text: String, interrupt: Boolean) {
        val queueMode = if (interrupt) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        tts?.speak(text, queueMode, null, null)
    }

    fun setLanguage(language: String) {
        currentLocale = when (language.lowercase()) {
            "hi", "hindi" -> Locale.forLanguageTag("hi-IN")
            else -> Locale.forLanguageTag("en-IN")
        }
        tts?.language = currentLocale
    }

    val isSpeaking: Boolean
        get() = tts?.isSpeaking == true

    fun setSpeechRate(rate: Float) {
        tts?.setSpeechRate(rate)
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}
