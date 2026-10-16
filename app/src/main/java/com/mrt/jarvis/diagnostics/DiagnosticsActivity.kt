package com.mrt.jarvis.diagnostics

import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.MotionEvent
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.mrt.jarvis.R
import com.mrt.jarvis.can.CanDiscovery
import com.mrt.jarvis.can.CanFrameLog
import com.mrt.jarvis.system.HardwareProfiler
import com.mrt.jarvis.system.LogManager
import com.mrt.jarvis.vehicle.VehicleDataRepository
import java.text.SimpleDateFormat
import java.util.*

/** Read-only diagnostics: CAN provider, real frames, measured rates, environment, system, program log. */
class DiagnosticsActivity : AppCompatActivity() {
    private lateinit var tv: TextView
    private var touch = "-"
    private var probe: CanDiscovery.Report? = null
    private val h = Handler(Looper.getMainLooper())
    private var pending = false
    private val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    override fun onCreate(b: Bundle?) {
        super.onCreate(b); setContentView(R.layout.activity_diagnostics)
        tv = findViewById(R.id.tvDiag)
        findViewById<TextView>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.btnClearFrames).setOnClickListener { CanFrameLog.clear(); render() }
        findViewById<TextView>(R.id.btnClearLog).setOnClickListener {
            LogManager.clear(this); Toast.makeText(this, R.string.s_log_cleared, Toast.LENGTH_SHORT).show(); render() }
        Thread { probe = CanDiscovery.run(applicationContext); runOnUiThread { render() } }.start()
        VehicleDataRepository.can.observe(this) { schedule() }
        render()
    }

    /** Throttle to max 2 renders/second so a busy bus never overloads the UI. */
    private fun schedule() { if (pending) return; pending = true; h.postDelayed({ pending = false; render() }, 500) }

    override fun dispatchTouchEvent(e: MotionEvent): Boolean {
        if (e.action == MotionEvent.ACTION_DOWN) { touch = "x:${e.rawX.toInt()} y:${e.rawY.toInt()}"; schedule() }
        return super.dispatchTouchEvent(e)
    }
    override fun onDestroy() { h.removeCallbacksAndMessages(null); super.onDestroy() }

    private fun render() {
        val s = VehicleDataRepository.canStats()
        val dm = DisplayMetrics().also { @Suppress("DEPRECATION") windowManager.defaultDisplay.getRealMetrics(it) }
        val rates = CanFrameLog.idRates()
        tv.text = buildString {
            appendLine("== CAN PROVIDER ==")
            appendLine("Status: ${s.state}")
            s.reason?.let { appendLine("Reason: $it") }
            appendLine("Provider: ${s.provider}"); appendLine("Protocol: ${s.protocol}")
            appendLine("MCU: ${s.mcu ?: "-"}")
            appendLine("RX: ${s.rx}   TX: ${s.tx} (read-only, never transmits)   Errors: ${s.errors}")
            appendLine("Last error: ${s.lastError ?: "-"}")
            appendLine("Last frame: " + (s.lastFrame?.let { "${it.idText()} @ ${fmt.format(Date(it.timestamp))}  ${it.hex()}" } ?: "-"))
            appendLine("Decoded signals: " + (if (s.decoded.isEmpty()) "none (no verified Pars signal table yet)"
                else s.decoded.entries.joinToString { "${it.key}=${it.value}" }))
            appendLine(); appendLine("== CANDIDATES ==")
            if (s.tried.isEmpty()) appendLine("-") else s.tried.forEach { appendLine(it) }
            appendLine(); appendLine("== UPDATE FREQUENCY (measured) ==")
            if (rates.isEmpty()) appendLine("-") else rates.entries.sortedBy { it.key }.forEach {
                appendLine((if (it.key < 0) "RAW" else "0x%03X".format(it.key)) + "  %.1f Hz".format(it.value)) }
            appendLine(); appendLine("== RAW FRAME LOG (newest first, ${CanFrameLog.total()} total) ==")
            val rec = CanFrameLog.recent(30)
            if (rec.isEmpty()) appendLine("no real frames received") else rec.forEach {
                appendLine("${fmt.format(Date(it.timestamp))}  ${it.idText()}  ${it.hex()}") }
            appendLine(); appendLine("== ENVIRONMENT ==")
            probe?.let { p ->
                appendLine("Vendor CAN apps: ${p.vendorApps.ifEmpty { listOf("none") }}")
                appendLine("Vendor services: ${p.services.ifEmpty { listOf("none") }}")
                appendLine("SocketCAN: ${p.socketCan.ifEmpty { listOf("none") }}")
                appendLine("Serial: ${p.serial.ifEmpty { listOf("none") }}  readable: ${p.readableSerial.ifEmpty { listOf("none") }}")
                appendLine("USB adapters: ${p.usb.ifEmpty { listOf("none") }}")
                appendLine("MCU props: ${if (p.mcuProps.isEmpty()) "none" else p.mcuProps}")
            } ?: appendLine("scanning…")
            appendLine(); appendLine("== SYSTEM ==")
            appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} / ${Build.HARDWARE}")
            appendLine("Screen: ${dm.widthPixels}x${dm.heightPixels} @ ${dm.densityDpi}dpi")
            appendLine("RAM class: ${(getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager).memoryClass} MB")
            appendLine("Profile: ${HardwareProfiler.load(this@DiagnosticsActivity)?.toString() ?: "-"}")
            appendLine(); appendLine("== TOUCH =="); appendLine(touch)
            appendLine(); appendLine("== MRT1 PROGRAM LOG ==")
            appendLine(LogManager.read(this@DiagnosticsActivity, 5000).ifBlank { "-" })
        }
    }
}
