package com.example.netraai.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

class TtsManager(context: Context) {
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var currentLocale: Locale = Locale.forLanguageTag("en-IN")
    private val pendingUtterances = mutableListOf<String>()
    private var onLastUtteranceDone: (() -> Unit)? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(currentLocale)
                if ((result == TextToSpeech.LANG_MISSING_DATA) || (result == TextToSpeech.LANG_NOT_SUPPORTED)) {
                    Log.e("TtsManager", "Language $currentLocale not supported, defaulting to US")
                    tts?.language = Locale.US
                }
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        if (utteranceId == "last_utterance") {
                            onLastUtteranceDone?.invoke()
                            onLastUtteranceDone = null
                        }
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        if (utteranceId == "last_utterance") {
                            onLastUtteranceDone?.invoke()
                            onLastUtteranceDone = null
                        }
                    }
                })
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

    fun speak(text: String, interrupt: Boolean = false, utteranceId: String? = null, onDone: (() -> Unit)? = null) {
        synchronized(this) {
            if (!isInitialized) {
                synchronized(pendingUtterances) {
                    pendingUtterances.add(text)
                }
                return
            }
        }
        if (onDone != null) {
            onLastUtteranceDone = onDone
        }
        val queueMode = if (interrupt) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        tts?.speak(text, queueMode, null, utteranceId ?: "default_utterance")
    }

    private fun speakInternal(text: String, interrupt: Boolean) {
        val queueMode = if (interrupt) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        tts?.speak(text, queueMode, null, "default_utterance")
    }

    fun stop() {
        tts?.stop()
        synchronized(pendingUtterances) {
            pendingUtterances.clear()
        }
        onLastUtteranceDone = null
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
        stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}
