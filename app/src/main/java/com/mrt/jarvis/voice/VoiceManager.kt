package com.mrt.jarvis.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Uses the REAL installed Android speech services (e.g. Google, or any offline engine that
 * registers android.speech.RecognitionService). Nothing is faked; if no service exists we say so.
 * Wake word "MRT": designed as an extension point (needs an on-device engine such as Vosk/Porcupine);
 * push-to-talk is the default.
 */
class VoiceManager(private val ctx: Context, private val cb: Callback) : TextToSpeech.OnInitListener {
    interface Callback { fun onListening(); fun onResult(text: String); fun onError(code: Int); fun onTtsReady(persian: Boolean) }

    private val fa = Locale("fa", "IR")
    private var sr: SpeechRecognizer? = null
    private var tts: TextToSpeech? = TextToSpeech(ctx, this)
    var ttsPersian = false; private set

    val recognitionAvailable get() = SpeechRecognizer.isRecognitionAvailable(ctx)

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val r = tts?.setLanguage(fa) ?: TextToSpeech.LANG_NOT_SUPPORTED
            ttsPersian = r >= TextToSpeech.LANG_AVAILABLE
        }
        cb.onTtsReady(ttsPersian)
    }

    fun listen() {
        if (!recognitionAvailable) { cb.onError(-1); return }
        if (sr == null) sr = SpeechRecognizer.createSpeechRecognizer(ctx).apply { setRecognitionListener(listener) }
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        sr?.startListening(i)
    }

    fun speak(text: String) { if (ttsPersian) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "mrt") }
    fun stopSpeaking() { tts?.stop() }

    fun release() { sr?.destroy(); sr = null; tts?.shutdown(); tts = null }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(p: Bundle?) = cb.onListening()
        override fun onResults(b: Bundle?) {
            b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(cb::onResult) ?: cb.onError(7)
        }
        override fun onError(e: Int) = cb.onError(e)
        override fun onBeginningOfSpeech() {}; override fun onRmsChanged(v: Float) {}
        override fun onBufferReceived(b: ByteArray?) {}; override fun onEndOfSpeech() {}
        override fun onPartialResults(b: Bundle?) {}; override fun onEvent(t: Int, b: Bundle?) {}
    }
}
