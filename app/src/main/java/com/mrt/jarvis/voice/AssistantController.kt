package com.mrt.jarvis.voice

import android.app.Activity
import android.content.Intent
import android.media.AudioManager
import android.view.KeyEvent
import androidx.lifecycle.LifecycleCoroutineScope
import com.mrt.jarvis.MainActivity
import com.mrt.jarvis.R
import com.mrt.jarvis.ai.CommandRouter
import com.mrt.jarvis.ai.Intent as AiIntent // <-- تغییر نام برای جلوگیری از تداخل
import com.mrt.jarvis.ai.Net
import com.mrt.jarvis.ai.OpenAIService
import com.mrt.jarvis.gps.GpsActivity
import com.mrt.jarvis.music.MusicActivity
import com.mrt.jarvis.settings.AppPrefs
import com.mrt.jarvis.settings.SettingsActivity
import com.mrt.jarvis.vehicle.VehicleDataRepository
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

class AssistantController(
    private val act: Activity, 
    private val scope: LifecycleCoroutineScope,
    private val voice: () -> VoiceManager?, 
    private val show: (String) -> Unit
) {
    private val am = act.getSystemService(AudioManager::class.java)
    private val nf = NumberFormat.getInstance(Locale("fa", "IR")).apply { maximumFractionDigits = 1 }

    fun handle(text: String) {
        val r = CommandRouter.route(text)
        val v = VehicleDataRepository.current()
        
        fun num(x: Float?, id: Int) = if (x == null) act.getString(R.string.r_unavail) else act.getString(id, nf.format(x))
        
        val reply: String? = when (r.intent) {
            // --- باز کردن صفحات ---
            AiIntent.OPEN_MUSIC -> {
                act.startActivity(Intent(act, MusicActivity::class.java))
                act.getString(R.string.r_opening_music)
            }
            AiIntent.OPEN_GPS -> {
                act.startActivity(Intent(act, GpsActivity::class.java))
                act.getString(R.string.r_opening_nav)
            }
            AiIntent.OPEN_DASH -> {
                act.startActivity(Intent(act, MainActivity::class.java))
                act.getString(R.string.r_opening_dash)
            }
            AiIntent.OPEN_SETTINGS -> {
                act.startActivity(Intent(act, SettingsActivity::class.java))
                act.getString(R.string.r_opening_settings)
            }

            // --- اطلاعات خودرو ---
            AiIntent.SPEED -> num(v.speedKmh?.let { Math.round(it).toFloat() }, R.string.r_speed)
            AiIntent.RPM -> num(v.rpm?.let { Math.round(it).toFloat() }, R.string.r_rpm)
            AiIntent.ENGINE_TEMP -> num(v.engineTempC, R.string.r_temp)
            AiIntent.BATTERY -> num(v.batteryVolt, R.string.r_batt)
            AiIntent.FUEL -> num(v.fuelPercent, R.string.r_fuel)
            
            // --- کنترل مدیا ---
            AiIntent.MEDIA_PLAY -> media(KeyEvent.KEYCODE_MEDIA_PLAY)
            AiIntent.MEDIA_PAUSE -> media(KeyEvent.KEYCODE_MEDIA_PAUSE)
            AiIntent.MEDIA_NEXT -> media(KeyEvent.KEYCODE_MEDIA_NEXT)
            AiIntent.MEDIA_PREV -> media(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            AiIntent.VOL_UP -> vol(AudioManager.ADJUST_RAISE)
            AiIntent.VOL_DOWN -> vol(AudioManager.ADJUST_LOWER)
            AiIntent.MUTE -> vol(AudioManager.ADJUST_TOGGLE_MUTE)
            
            // --- سیستم ---
            AiIntent.STOP_VOICE -> { 
                voice()?.stopListening() 
                null 
            }
            AiIntent.GENERAL -> { 
                cloud(text) 
                null 
            }
            else -> null
        }
        reply?.let(::say)
    }

    private fun cloud(text: String) {
        val p = AppPrefs(act)
        if (!p.aiEnabled || p.apiKey.isBlank()) return say(act.getString(R.string.r_no_ai))
        if (!Net.online(act)) return say(act.getString(R.string.r_no_internet))
        
        show("…")
        scope.launch {
            OpenAIService(p.apiKey, p.aiModel).ask(act.getString(R.string.ai_system_prompt), text)
                .onSuccess(::say)
                .onFailure { say(act.getString(R.string.r_ai_error)) }
        }
    }
    
    private fun say(s: String) { 
        show(s)
        if (AppPrefs(act).ttsEnabled) {
            voice()?.speak(s) 
        }
    }
    
    private fun media(code: Int): String {
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        return act.getString(R.string.r_ok)
    }
    
    private fun vol(dir: Int): String { 
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, AudioManager.FLAG_SHOW_UI)
        return act.getString(R.string.r_ok) 
    }
}
