package com.mrt.jarvis

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.SpeechRecognizer
import android.view.*
import android.widget.*
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.mrt.jarvis.ai.Net
import com.mrt.jarvis.can.CanState
import com.mrt.jarvis.dashboard.*
import com.mrt.jarvis.diagnostics.DiagnosticsActivity
import com.mrt.jarvis.settings.AppPrefs
import com.mrt.jarvis.settings.SettingsActivity
import com.mrt.jarvis.system.CrashHandler
import com.mrt.jarvis.system.HardwareProfiler
import com.mrt.jarvis.system.InitReboot
import com.mrt.jarvis.system.LogManager
import com.mrt.jarvis.vehicle.VehicleData
import com.mrt.jarvis.voice.AssistantController
import com.mrt.jarvis.voice.VoiceManager
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

/**
 * Dashboard. Startup priority: 1) UI is drawn immediately, 2) CAN starts via the ViewModel,
 * 3) vehicle data flows in, 4) voice/TTS is initialized after a short delay, 5) AI is only used on demand.
 * Nothing here waits for the Internet.
 */
class MainActivity : AppCompatActivity(), VoiceManager.Callback {
    private val vm: DashboardViewModel by viewModels()
    private lateinit var prefs: AppPrefs
    private lateinit var gSpeed: GaugeView
    private lateinit var gRpm: GaugeView
    private lateinit var tvAssistant: TextView
    private lateinit var gaugeRow: LinearLayout
    private lateinit var infoPanel: View
    private val tiles = HashMap<Int, TextView>()
    private var voice: VoiceManager? = null
    private var ttsOk: Boolean? = null
    private lateinit var assistant: AssistantController
    private val h = Handler(Looper.getMainLooper())
    private val fa = Locale("fa", "IR")
    private val nf = NumberFormat.getInstance(fa).apply { maximumFractionDigits = 1 }
    private var last = VehicleData()
    private val status = object : Runnable { override fun run() { updateStatus(); h.postDelayed(this, 3000) } }

    /** System clock: minute ticks + manual time/date/timezone changes. Works offline. */
    private val timeRx = object : BroadcastReceiver() { override fun onReceive(c: Context, i: Intent) = updateClock() }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b); setContentView(R.layout.activity_main)
        prefs = AppPrefs(this)
        gaugeRow = findViewById(R.id.gaugeRow); infoPanel = findViewById(R.id.infoPanel)
        gSpeed = findViewById(R.id.gSpeed)
        gRpm = findViewById<GaugeView>(R.id.gRpm).apply { maxValue = 8000f
            label = getString(R.string.rpm); unit = getString(R.string.rpm_unit); formatter = { "%.1f".format(it / 1000f) } }
        tvAssistant = findViewById(R.id.tvAssistant)
        val bottom = findViewById<LinearLayout>(R.id.bottom)
        listOf(R.string.fuel, R.string.engine_temp, R.string.battery, R.string.trip, R.string.outside_temp).forEach { id ->
            val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                setBackgroundResource(R.drawable.glass); val p = dp(8); setPadding(p, p, p, p) }
            box.addView(TextView(this).apply { setText(id); setTextAppearance(R.style.Label) })
            val v = TextView(this).apply { setTextAppearance(R.style.Value); text = getString(R.string.dash) }
            box.addView(v); tiles[id] = v
            bottom.addView(box, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(dp(4), 0, dp(4), 0) })
        }
        findViewById<View>(R.id.btnSettings).setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        findViewById<View>(R.id.btnSettings).setOnLongClickListener { startActivity(Intent(this, DiagnosticsActivity::class.java)); true }
        findViewById<View>(R.id.btnMic).setOnClickListener { startVoice() }
        assistant = AssistantController(this, lifecycleScope, { voice }) { tvAssistant.text = it }

        vm.vehicle.observe(this) { v -> last = v; safe("bind") { bind(v) } }
        vm.can.observe(this) { safe("can") { updateCan() } }

        // Priority 4: voice output initialized after the dashboard is visible (only to learn TTS availability).
        h.postDelayed({ if (voice == null) voice = VoiceManager(this, this) }, 4000)
        // First launch: profile hardware in background, then the one-time init reboot / message.
        if (!HardwareProfiler.isDone(this)) Thread {
            runCatching { HardwareProfiler.run(applicationContext) }.onFailure { LogManager.e(this, "profile", it) }
            runOnUiThread { firstLaunchFinished() }
        }.apply { priority = Thread.MIN_PRIORITY }.start()
    }

    private fun firstLaunchFinished() {
        if (isFinishing || !InitReboot.pending(this)) return
        if (InitReboot.tryReboot(this)) return
        AlertDialog.Builder(this).setTitle(R.string.init_title).setMessage(R.string.init_done_reboot)
            .setPositiveButton(R.string.ok, null).show()
    }

    // ---------- layout ----------
    private fun applyLayout() {
        val side = when (prefs.layout) { "left" -> "left"; "right" -> "right"; "custom" -> prefs.customSide; else -> null }
        infoPanel.visibility = if (side == null) View.GONE else View.VISIBLE
        if (side != null) {
            gaugeRow.removeView(infoPanel)
            gaugeRow.addView(infoPanel, if (side == "left") 0 else gaugeRow.childCount)
        }
        val custom = prefs.layout == "custom"
        val showClock = !custom || prefs.showInfoClock
        val showDate = !custom || prefs.showInfoDate
        val showVeh = !custom || prefs.showInfoVehicle
        findViewById<View>(R.id.analogClock).visibility = if (showClock && prefs.analogClock) View.VISIBLE else View.GONE
        findViewById<View>(R.id.tvInfoClock).visibility = if (showClock && !prefs.analogClock) View.VISIBLE else View.GONE
        findViewById<View>(R.id.tvInfoDate).visibility = if (showDate && prefs.showDate) View.VISIBLE else View.GONE
        findViewById<View>(R.id.tvInfoDay).visibility = if (showDate && prefs.showDay) View.VISIBLE else View.GONE
        findViewById<View>(R.id.tvInfoVehicle).visibility = if (showVeh) View.VISIBLE else View.GONE
        val mph = prefs.speedUnit == "mph"
        gSpeed.apply { maxValue = (if (mph) prefs.speedMax * 0.621f else prefs.speedMax.toFloat())
            label = getString(R.string.speed); unit = getString(if (mph) R.string.mph else R.string.kmh) }
        gRpm.redlineFrom = prefs.redline.toFloat()
        bind(last)
    }

    // ---------- data ----------
    private fun bind(v: VehicleData) {
        val mph = prefs.speedUnit == "mph"; val fahr = prefs.tempUnit == "f"
        gSpeed.setValue(v.speedKmh?.let { if (mph) it * 0.621f else it }); gRpm.setValue(v.rpm)
        fun f(x: Float?, suf: String) = x?.let { nf.format(it) + suf } ?: getString(R.string.dash)
        fun t(x: Float?) = f(x?.let { if (fahr) it * 9 / 5 + 32 else it }, if (fahr) "°F" else "°C")
        tiles[R.string.fuel]?.text = f(v.fuelPercent, "٪")
        tiles[R.string.engine_temp]?.text = t(v.engineTempC)
        tiles[R.string.battery]?.text = f(v.batteryVolt, "V")
        tiles[R.string.trip]?.text = f(v.tripKm, " km")
        tiles[R.string.outside_temp]?.text = t(v.outsideTempC)
        findViewById<TextView>(R.id.tvGear).text = v.gear ?: ""
        findViewById<StatusIndicatorsView>(R.id.indicators).bind(v)
        findViewById<View>(R.id.tvDemo).visibility = if (v.isDemo) View.VISIBLE else View.GONE
        if (infoPanel.visibility == View.VISIBLE) findViewById<TextView>(R.id.tvInfoVehicle).text =
            "${getString(R.string.odometer)}: ${f(v.odometerKm, " km")}\n" +
            "${getString(R.string.avg_speed)}: ${f(v.avgSpeed, "")}\n" +
            "${getString(R.string.consumption)}: ${f(v.avgConsumption, " L/100")}\n" +
            "${getString(R.string.outside_temp)}: ${t(v.outsideTempC)}"
    }

    private fun updateClock() {
        val now = Date()
        val tf = SimpleDateFormat(if (prefs.clock24) "HH:mm" else "hh:mm a", fa)
        val df = SimpleDateFormat("dd/MM/yyyy", fa).apply { calendar = GregorianCalendar() }
        val dayf = SimpleDateFormat("EEEE", fa)
        val time = tf.format(now)
        findViewById<TextView>(R.id.tvTime).text = time
        findViewById<TextView>(R.id.tvTopDate).text = if (prefs.showDate && infoPanel.visibility != View.VISIBLE)
            df.format(now) + (if (prefs.showDay) "  " + dayf.format(now) else "") else ""
        findViewById<TextView>(R.id.tvInfoClock).text = time
        findViewById<TextView>(R.id.tvInfoDate).text = df.format(now)
        findViewById<TextView>(R.id.tvInfoDay).text = dayf.format(now)
        findViewById<View>(R.id.analogClock).invalidate()
    }

    private fun updateCan() {
        val cs = vm.can.value
        findViewById<TextView>(R.id.tvCan).apply {
            when (cs?.state) {
                CanState.CONNECTED -> { text = "CAN " + getString(R.string.can_connected); setTextColor(getColor(R.color.green)) }
                CanState.CONNECTING -> { setText(R.string.can_connecting); setTextColor(getColor(R.color.text_dim)) }
                else -> { setText(R.string.can_disconnected); setTextColor(getColor(R.color.amber)) }
            }
        }
    }

    private fun updateStatus() {
        val on = Net.online(this)
        findViewById<TextView>(R.id.tvNet).text = getString(R.string.internet) + " " + getString(if (on) R.string.online else R.string.offline)
        val gps = runCatching { getSystemService(LocationManager::class.java).isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)
        findViewById<TextView>(R.id.tvGps).text = getString(R.string.gps) + " " + getString(if (gps) R.string.online else R.string.offline)
        @Suppress("DEPRECATION") val bt = runCatching { BluetoothAdapter.getDefaultAdapter()?.isEnabled == true }.getOrDefault(false)
        findViewById<TextView>(R.id.tvBt).text = getString(R.string.bluetooth) + " " + getString(if (bt) R.string.online else R.string.offline)
        val mic = packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE) && SpeechRecognizer.isRecognitionAvailable(this)
        stat(R.id.tvMic, mic, R.string.mic_ok, R.string.mic_unavailable)
        ttsOk?.let { stat(R.id.tvVoiceOut, it, R.string.voice_out_ok, R.string.voice_out_unavailable) }
        stat(R.id.tvAi, on && prefs.aiEnabled && prefs.apiKey.isNotBlank(), R.string.ai_online, R.string.ai_offline)
        updateCan()
    }
    private fun stat(id: Int, ok: Boolean, okText: Int, badText: Int) = findViewById<TextView>(id).apply {
        setText(if (ok) okText else badText); setTextColor(getColor(if (ok) R.color.text_dim else R.color.amber)) }

    // ---------- voice ----------
    private fun startVoice() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1); return }
        if (voice == null) voice = VoiceManager(this, this)
        safe("listen") { voice?.listen() }
    }
    override fun onRequestPermissionsResult(r: Int, p: Array<out String>, g: IntArray) {
        super.onRequestPermissionsResult(r, p, g); if (g.firstOrNull() == PackageManager.PERMISSION_GRANTED) startVoice()
    }

    // Steering-wheel keys: only the configured voice key is consumed; volume/media/call pass through to the factory system.
    override fun onKeyDown(code: Int, e: KeyEvent): Boolean {
        if (code == prefs.swcVoiceKey || code == KeyEvent.KEYCODE_VOICE_ASSIST || code == KeyEvent.KEYCODE_SEARCH) { startVoice(); return true }
        return super.onKeyDown(code, e)
    }

    override fun onListening() { tvAssistant.setText(R.string.listening) }
    override fun onResult(text: String) { tvAssistant.text = text; safe("assistant") { assistant.handle(text) } }
    override fun onError(code: Int) { tvAssistant.setText(if (code == -1) R.string.r_no_stt else R.string.r_not_understood) }
    override fun onTtsReady(persian: Boolean) { runOnUiThread { ttsOk = persian; HardwareProfiler.setTts(this, persian); updateStatus() } }

    // ---------- lifecycle ----------
    override fun onResume() {
        super.onResume(); immersive()
        safe("layout") { applyLayout() }
        registerReceiver(timeRx, IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK); addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED); addAction(Intent.ACTION_DATE_CHANGED) })
        updateClock(); h.post(status)
        findViewById<TextView>(R.id.tvAssistant).let { if (CrashHandler.safeMode(this)) it.setText(R.string.safe_banner) }
        val br = prefs.brightness
        window.attributes = window.attributes.apply { screenBrightness = if (br >= 0) br / 100f else -1f }
    }
    override fun onPause() { runCatching { unregisterReceiver(timeRx) }; h.removeCallbacks(status); super.onPause() }
    override fun onDestroy() { h.removeCallbacksAndMessages(null); voice?.release(); voice = null; super.onDestroy() }
    override fun onWindowFocusChanged(f: Boolean) { super.onWindowFocusChanged(f); if (f) immersive() }
    @Suppress("DEPRECATION") private fun immersive() {
        if (prefs.keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (!prefs.fullscreen) { window.decorView.systemUiVisibility = 0; return }
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    /** Self-recovery: a failing part is logged and skipped; the dashboard always stays on screen. */
    private inline fun safe(where: String, block: () -> Unit) {
        try { block() } catch (e: Exception) { LogManager.e(this, "UI $where", e) }
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
