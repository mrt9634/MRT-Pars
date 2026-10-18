package com.mrt.jarvis.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import java.util.Locale

class VoiceManager(private val ctx: Context, private val cb: Callback) : 
    TextToSpeech.OnInitListener, RecognitionListener {

    interface Callback { 
        fun onListening()
        fun onResult(text: String)
        fun onError(code: Int)
        fun onTtsReady(persian: Boolean) 
    }

    private val fa = Locale("fa", "IR")
    private var tts: TextToSpeech? = TextToSpeech(ctx, this)
    private var speechService: SpeechService? = null
    private var model: Model? = null
    var ttsPersian = false; private set

    init {
        loadVoskModel()
    }

    private fun loadVoskModel() {
        // مدل باید در پوشه assets/model-fa باشد
        // نام پوشه assets: model-fa
        // نام پوشه داخلی که باز میشه: model
        StorageService.unpack(ctx, "model-fa", "model", { m ->
            model = m
            Log.i("VoiceManager", "Vosk model loaded successfully")
        }, { e ->
            Log.e("VoiceManager", "Failed to load Vosk model", e)
        })
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val r = tts?.setLanguage(fa) ?: TextToSpeech.LANG_NOT_SUPPORTED
            ttsPersian = r >= TextToSpeech.LANG_AVAILABLE
        }
        cb.onTtsReady(ttsPersian)
    }

    fun listen() {
        if (model == null) {
            cb.onError(-2) // Model not ready
            return
        }
        cb.onListening()
        try {
            val recognizer = Recognizer(model, 16000.0f)
            speechService = SpeechService(recognizer, 16000.0f)
            speechService?.startListening(this)
        } catch (e: Exception) {
            Log.e("VoiceManager", "Error starting listening", e)
            cb.onError(-1)
        }
    }

    fun stopListening() {
        speechService?.stop()
        speechService?.shutdown()
        speechService = null
    }

    fun speak(text: String) { 
        if (ttsPersian) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "mrt") 
    }

    fun release() { 
        stopListening()
        tts?.shutdown()
        tts = null 
    }

    // --- Vosk Callbacks ---
    override fun onPartialResult(hypothesis: String?) {}

    override fun onResult(hypothesis: String?) {
        stopListening()
        if (hypothesis != null) {
            try {
                // Vosk خروجی را به صورت JSON می‌دهد: {"text" : "سلام دنیا"}
                val json = JSONObject(hypothesis)
                val text = json.optString("text", "").trim()
                
                if (text.isNotBlank()) {
                    cb.onResult(text)
                } else {
                    cb.onError(7) // متوجه نشدم
                }
            } catch (e: Exception) {
                cb.onError(7)
            }
        } else {
            cb.onError(7)
        }
    }

    override fun onFinalResult(hypothesis: String?) { 
        onResult(hypothesis) 
    }
    
    override fun onError(exception: Exception?) { 
        Log.e("VoiceManager", "Vosk Error", exception)
        stopListening()
        cb.onError(-1) 
    }
    
    override fun onTimeout() { 
        stopListening()
        cb.onError(6) 
    }
}
