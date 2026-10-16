package com.mrt.jarvis.voice

import android.app.Activity
import android.media.AudioManager
import android.view.KeyEvent
import androidx.lifecycle.LifecycleCoroutineScope
import com.mrt.jarvis.R
import com.mrt.jarvis.ai.*
import com.mrt.jarvis.can.CanState
import com.mrt.jarvis.diagnostics.DiagnosticsActivity
import com.mrt.jarvis.settings.AppPrefs
import com.mrt.jarvis.settings.SettingsActivity
import com.mrt.jarvis.vehicle.VehicleDataRepository
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

/** Hybrid assistant: local intents answered instantly offline; cloud only for general questions. */
class AssistantController(private val act: Activity, private val scope: LifecycleCoroutineScope,
                          private val voice: () -> VoiceManager?, private val show: (String) -> Unit) {
    private val am = act.getSystemService(AudioManager::class.java)
    private val nf = NumberFormat.getInstance(Locale("fa", "IR")).apply { maximumFractionDigits = 1 }

    fun handle(text: String) {
        val r = CommandRouter.route(text)
        val v = VehicleDataRepository.current()
        fun num(x: Float?, id: Int) = if (x == null) act.getString(R.string.r_unavail) else act.getString(id, nf.format(x))
        val reply: String? = when (r.intent) {
            Intent.SPEED -> num(v.speedKmh?.let { Math.round(it).toFloat() }, R.string.r_speed)
            Intent.RPM -> num(v.rpm?.let { Math.round(it).toFloat() }, R.string.r_rpm)
            Intent.ENGINE_TEMP -> num(v.engineTempC, R.string.r_temp)
            Intent.BATTERY -> num(v.batteryVolt, R.string.r_batt)
            Intent.FUEL -> num(v.fuelPercent, R.string.r_fuel)
            Intent.CAN_STATUS -> VehicleDataRepository.canStats().let {
                if (it.state == CanState.CONNECTED) act.getString(R.string.r_can_ok, it.provider) else act.getString(R.string.r_can_no) }
            Intent.MEDIA_PLAY -> media(KeyEvent.KEYCODE_MEDIA_PLAY)
            Intent.MEDIA_PAUSE -> media(KeyEvent.KEYCODE_MEDIA_PAUSE)
            Intent.MEDIA_NEXT -> media(KeyEvent.KEYCODE_MEDIA_NEXT)
            Intent.MEDIA_PREV -> media(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            Intent.VOL_UP -> vol(AudioManager.ADJUST_RAISE)
            Intent.VOL_DOWN -> vol(AudioManager.ADJUST_LOWER)
            Intent.MUTE -> vol(AudioManager.ADJUST_TOGGLE_MUTE)
            Intent.HOME -> act.getString(R.string.r_ok)
            Intent.SETTINGS -> { act.startActivity(android.content.Intent(act, SettingsActivity::class.java)); act.getString(R.string.r_ok) }
            Intent.DIAGNOSTICS -> { act.startActivity(android.content.Intent(act, DiagnosticsActivity::class.java)); act.getString(R.string.r_ok) }
            Intent.NAVIGATE -> { runCatching { act.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse("geo:0,0?q=" + android.net.Uri.encode(text))).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                act.getString(R.string.r_ok) }
            Intent.STOP_VOICE -> { voice()?.stopSpeaking(); null }
            Intent.GENERAL -> { cloud(text); null }
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
                .onSuccess(::say).onFailure { say(act.getString(R.string.r_ai_error)) }
        }
    }
    private fun say(s: String) { show(s); if (AppPrefs(act).ttsEnabled) voice()?.speak(s) }
    private fun media(code: Int): String {
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code)); am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        return act.getString(R.string.r_ok)
    }
    private fun vol(dir: Int): String { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, AudioManager.FLAG_SHOW_UI); return act.getString(R.string.r_ok) }
}
