package com.mrt.jarvis

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.mrt.jarvis.dashboard.BmwGaugeView
import com.mrt.jarvis.dashboard.CornerGaugeView
import com.mrt.jarvis.dashboard.SpeedLimitView
import com.mrt.jarvis.dashboard.DashboardViewModel
import com.mrt.jarvis.diagnostics.DiagnosticsActivity
import com.mrt.jarvis.settings.AppPrefs
import com.mrt.jarvis.settings.SettingsActivity
import com.mrt.jarvis.system.HardwareProfiler
import com.mrt.jarvis.system.LogManager
import com.mrt.jarvis.vehicle.VehicleData
import com.mrt.jarvis.voice.AssistantController
import com.mrt.jarvis.voice.VoiceManager
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity(), VoiceManager.Callback {
    private val vm: DashboardViewModel by viewModels()
    private lateinit var prefs: AppPrefs
    
    // ویوهای جدید داشبورد BMW
    private lateinit var gSpeed: BmwGaugeView
    private lateinit var gRpm: BmwGaugeView
    private lateinit var gFuel: CornerGaugeView
    private lateinit var gTemp: CornerGaugeView
    private lateinit var limitSign: SpeedLimitView
    
    private lateinit var tvSpeedBig: TextView
    private lateinit var tvDriveMode: TextView
    private lateinit var tvGear: TextView
    private lateinit var tvAssistant: TextView
    
    private var voice: VoiceManager? = null
    private var ttsOk: Boolean? = null
    private lateinit var assistant: AssistantController
    private val h = Handler(Looper.getMainLooper())
    private val fa = Locale("fa", "IR")
    private val nf = NumberFormat.getInstance(fa).apply { maximumFractionDigits = 0 }
    private var last = VehicleData()

    private val timeRx = object : BroadcastReceiver() { 
        override fun onReceive(c: Context, i: Intent) = updateClock() 
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContentView(R.layout.activity_main)
        prefs = AppPrefs(this)
        
        // پیدا کردن ویوها از XML جدید
        gSpeed = findViewById(R.id.gSpeed)
        gRpm = findViewById(R.id.gRpm)
        gFuel = findViewById(R.id.gFuel)
        gTemp = findViewById(R.id.gTemp)
        limitSign = findViewById(R.id.limitSign)
        tvSpeedBig = findViewById(R.id.tvSpeedBig)
        tvDriveMode = findViewById(R.id.tvDriveMode)
        tvGear = findViewById(R.id.tvGear)
        tvAssistant = findViewById(R.id.tvAssistant)

        // تنظیمات گیج دور موتور (حالت BMW)
        gRpm.apply {
            maxValue = 8000f
            majorStep = 1000f
            accentColor = 0xFFE5484D.toInt() // رنگ قرمز برای دور موتور BMW
            numberFormat = { (it / 1000f).toInt().toString() }
        }
        
        // تنظیمات بنزین و دمای آب
        gFuel.side = CornerGaugeView.Side.LEFT
        gFuel.startLabel = "E"
        gFuel.endLabel = "F"
        
        gTemp.side = CornerGaugeView.Side.RIGHT
        gTemp.startLabel = "C"
        gTemp.endLabel = "H"

        // دکمه‌ها
        findViewById<View>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        
        // اینجا پرانتز اصلاح شد:
        findViewById<View>(R.id.btnSettings).setOnLongClickListener {
            startActivity(Intent(this, DiagnosticsActivity::class.java))
            true
        }
        
        findViewById<View>(R.id.btnMic).setOnClickListener { startVoice() }
        
        assistant = AssistantController(this, lifecycleScope, { voice }) { 
            tvAssistant.text = it
            tvAssistant.visibility = View.VISIBLE 
        }

        vm.vehicle.observe(this) { v -> 
            last = v
            safe("bind") { bind(v) } 
        }
        
        // راه‌اندازی صدا با تاخیر
        h.postDelayed({ if (voice == null) voice = VoiceManager(this, this) }, 4000)
        
        if (!HardwareProfiler.isDone(this)) Thread {
            runCatching { HardwareProfiler.run(applicationContext) }
        }.apply { priority = Thread.MIN_PRIORITY }.start()
    }

    private fun bind(v: VehicleData) {
        val mph = prefs.speedUnit == "mph"
        val speedVal = v.speedKmh?.let { if (mph) it * 0.621f else it }
        
        // آپدیت گیج‌های BMW
        gSpeed.setValue(speedVal)
        gRpm.setValue(v.rpm)
        gFuel.setValue(v.fuelPercent)
        gTemp.setValue(v.engineTempC)
        
        // آپدیت وسط صفحه
        tvSpeedBig.text = speedVal?.let { nf.format(it) } ?: getString(R.string.dash)
        tvGear.text = v.gear ?: getString(R.string.dash)
        
        // علامت محدودیت سرعت
        limitSign.limit = v.speedLimit
        
        // حالت رانندگی
        if (v.driveMode != null) {
            tvDriveMode.text = v.driveMode
            tvDriveMode.visibility = View.VISIBLE
        } else {
            tvDriveMode.visibility = View.GONE
        }
    }

    private fun updateClock() {
        val now = Date()
        val tf = SimpleDateFormat(if (prefs.clock24) "HH:mm" else "hh:mm a", fa)
        findViewById<TextView>(R.id.tvTime).text = tf.format(now)
    }

    private fun startVoice() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            return 
        }
        if (voice == null) voice = VoiceManager(this, this)
        safe("listen") { voice?.listen() }
    }

    override fun onRequestPermissionsResult(r: Int, p: Array<out String>, g: IntArray) {
        super.onRequestPermissionsResult(r, p, g)
        if (g.firstOrNull() == PackageManager.PERMISSION_GRANTED) startVoice()
    }

    override fun onKeyDown(code: Int, e: KeyEvent): Boolean {
        if (code == prefs.swcVoiceKey || code == KeyEvent.KEYCODE_VOICE_ASSIST || code == KeyEvent.KEYCODE_SEARCH) {
            startVoice()
            return true 
        }
        return super.onKeyDown(code, e)
    }

    override fun onListening() { 
        tvAssistant.setText(R.string.listening) 
    }
    
    override fun onResult(text: String) { 
        tvAssistant.text = text
        safe("assistant") { assistant.handle(text) } 
    }
    
    override fun onError(code: Int) { 
        tvAssistant.setText(if (code == -1) R.string.r_no_stt else R.string.r_not_understood) 
    }
    
    override fun onTtsReady(persian: Boolean) { 
        runOnUiThread { 
            ttsOk = persian
            HardwareProfiler.setTts(this, persian) 
        } 
    }

    override fun onResume() {
        super.onResume()
        immersive()
        registerReceiver(timeRx, IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_DATE_CHANGED) 
        })
        updateClock()
        
        val br = prefs.brightness
        window.attributes = window.attributes.apply { screenBrightness = if (br >= 0) br / 100f else -1f }
    }

    override fun onPause() {
        runCatching { unregisterReceiver(timeRx) }
        super.onPause()
    }

    override fun onDestroy() {
        h.removeCallbacksAndMessages(null)
        voice?.release()
        voice = null
        super.onDestroy()
    }

    override fun onWindowFocusChanged(f: Boolean) {
        super.onWindowFocusChanged(f)
        if (f) immersive()
    }

    @Suppress("DEPRECATION") 
    private fun immersive() {
        if (prefs.keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        if (!prefs.fullscreen) { 
            window.decorView.systemUiVisibility = 0
            return 
        }
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or 
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or 
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    private inline fun safe(where: String, block: () -> Unit) {
        try { 
            block() 
        } catch (e: Exception) { 
            LogManager.e(this, "UI $where", e) 
        }
    }
}
