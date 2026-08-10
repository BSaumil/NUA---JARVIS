package com.nua.assistant.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

private const val FINAL_UTTERANCE_PREFIX = "nua_final_"

/** Thin wrapper over Android's built-in SpeechRecognizer (STT) and TextToSpeech (TTS). */
@Singleton
class VoiceManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private var textToSpeech: TextToSpeech? = null
    private var ttsReady = false
    private var speechRecognizer: SpeechRecognizer? = null
    private var onFinalSpeechDone: (() -> Unit)? = null

    init {
        textToSpeech = TextToSpeech(context) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit
                    override fun onStop(utteranceId: String?, interrupted: Boolean) = Unit

                    override fun onDone(utteranceId: String?) {
                        if (utteranceId?.startsWith(FINAL_UTTERANCE_PREFIX) == true) {
                            onFinalSpeechDone?.invoke()
                        }
                    }

                    @Deprecated("Deprecated in Java", ReplaceWith(""))
                    override fun onError(utteranceId: String?) = Unit
                })
            }
        }
    }

    /**
     * Speaks [text] in [language]'s voice. Falls back to English if the device/TTS
     * engine has no voice data for that language (common for Gujarati and Haryanvi in
     * particular — see NuaLanguage's doc comment).
     *
     * [flush] interrupts anything currently queued (the default, for a fresh reply);
     * pass false to append after what's already queued, used when speaking a streamed
     * reply sentence-by-sentence as chunks arrive.
     */
    fun speak(text: String, language: NuaLanguage = NuaLanguage.ENGLISH, flush: Boolean = true) {
        if (!ttsReady || text.isBlank()) return
        val locale = Locale.forLanguageTag(language.speechTag)
        val result = textToSpeech?.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            textToSpeech?.setLanguage(Locale.forLanguageTag(NuaLanguage.ENGLISH.speechTag))
        }
        val queueMode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        textToSpeech?.speak(text, queueMode, null, "nua_utterance_${System.currentTimeMillis()}")
    }

    /**
     * Queues a silent marker after whatever's currently queued, so its onDone fires
     * exactly when the whole reply has finished speaking — regardless of how many real
     * `speak()` chunks made it up (streamed sentence-by-sentence or a single call).
     * Call this once after every reply, streamed or not. Used for continuous
     * conversation mode via [setOnFinalSpeechDoneListener].
     */
    fun markReplyComplete() {
        if (!ttsReady) return
        textToSpeech?.playSilentUtterance(1, TextToSpeech.QUEUE_ADD, "$FINAL_UTTERANCE_PREFIX${System.currentTimeMillis()}")
    }

    /** Called once a reply finishes speaking end to end (see [markReplyComplete]). Used for continuous conversation mode. */
    fun setOnFinalSpeechDoneListener(listener: (() -> Unit)?) {
        onFinalSpeechDone = listener
    }

    fun stopSpeaking() {
        textToSpeech?.stop()
    }

    /**
     * [language] is a recognition hint, not a filter — SpeechRecognizer needs one
     * locale to bias toward per call. [onTone] fires once, right before [onResult],
     * with a coarse read of the utterance's energy/variance (see VoiceProsody) — not
     * used for anything unless the caller wires it into a directive.
     */
    fun startListening(
        language: NuaLanguage = NuaLanguage.ENGLISH,
        onResult: (String) -> Unit,
        onError: (String) -> Unit,
        onTone: (VoiceTone) -> Unit = {},
    ) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("Speech recognition isn't available on this device.")
            return
        }

        stopListening()
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer = recognizer

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.speechTag)
        }

        val rmsSamples = mutableListOf<Float>()

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                onTone(VoiceProsody.classify(rmsSamples))
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (text != null) onResult(text) else onError("Didn't catch that.")
            }

            override fun onError(error: Int) {
                onError("Speech recognition error (code $error)")
            }

            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) {
                rmsSamples.add(rmsdB)
            }
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })

        recognizer.startListening(intent)
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    fun release() {
        stopListening()
        onFinalSpeechDone = null
        textToSpeech?.shutdown()
        textToSpeech = null
    }
}
