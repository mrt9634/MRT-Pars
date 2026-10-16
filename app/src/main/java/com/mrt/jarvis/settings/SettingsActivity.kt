package com.mrt.jarvis.settings

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.mrt.jarvis.BuildConfig
import com.mrt.jarvis.R
import com.mrt.jarvis.ai.Net
import com.mrt.jarvis.can.CanDiscovery
import com.mrt.jarvis.can.CanFrameLog
import com.mrt.jarvis.can.CanState
import com.mrt.jarvis.diagnostics.DiagnosticsActivity
import com.mrt.jarvis.system.CrashHandler
import com.mrt.jarvis.system.HardwareProfiler
import com.mrt.jarvis.system.LogManager
import com.mrt.jarvis.vehicle.VehicleDataRepository
import java.text.SimpleDateFormat
import java.util.*

/** 13-category glass settings. Values are saved immediately; CAN changes restart the provider on exit. */
class SettingsActivity : AppCompatActivity() {

    private lateinit var root: LinearLayout
    private lateinit var cats: LinearLayout
    private val prefs by lazy { AppPrefs(this) }
    private var canDirty = false
    private var uiDirty = false       // launcher/splash/dash style changed → suggest restart
    private var learningKey = false
    private var current = 0
    private val onLeave = ArrayList<() -> Unit>()

    private val categories = listOf(
        R.string.c_vehicle,      // 0
        R.string.c_can,          // 1
        R.string.c_dashboard,    // 2
        R.string.c_launcher,     // 3  ← NEW
        R.string.c_clock,        // 4
        R.string.c_display,      // 5
        R.string.c_voice,        // 6
        R.string.c_ai,           // 7
        R.string.c_media,        // 8
        R.string.c_swc,          // 9
        R.string.c_diag,         // 10
        R.string.c_system,       // 11
        R.string.c_about         // 12
    )

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContentView(R.layout.activity_settings)
        root = findViewById(R.id.root)
        cats = findViewById(R.id.cats)

        categories.forEachIndexed { i, id ->
            cats.addView(TextView(this).apply {
                setText(id)
                textSize = 16f
                setPadding(dp(14), dp(12), dp(14), dp(12))
                setTextColor(getColor(R.color.text))
                setOnClickListener { show(i) }
            })
        }
        cats.addView(Button(this).apply {
            setText(R.string.back)
            setOnClickListener { finish() }
        })
        show(b?.getInt("cat") ?: 0)
    }

    override fun onSaveInstanceState(o: Bundle) {
        super.onSaveInstanceState(o)
        o.putInt("cat", current)
    }

    private fun show(i: Int) {
        flush()
        current = i
        learningKey = false
        root.removeAllViews()

        for (k in 0 until categories.size) {
            (cats.getChildAt(k) as TextView).apply {
                setBackgroundResource(if (k == i) R.drawable.glass else 0)
                setTextColor(getColor(if (k == i) R.color.accent else R.color.text))
            }
        }
        header(categories[i])
        when (i) {
            0 -> vehicle()
            1 -> can()
            2 -> dashboard()
            3 -> launcher()
            4 -> clock()
            5 -> display()
            6 -> voice()
            7 -> ai()
            8 -> media()
            9 -> swc()
            10 -> diag()
            11 -> system()
            12 -> about()
        }
    }

    // ============ 0. Vehicle ============
    private fun vehicle() {
        val m = edit(R.string.s_vehicle_model, prefs.vehicleModel)
        onLeave += { prefs.vehicleModel = m.text.toString() }
        radio(R.string.s_speed_unit,
            listOf("kmh" to R.string.u_kmh, "mph" to R.string.u_mph),
            prefs.speedUnit) { prefs.speedUnit = it }
        radio(R.string.s_temp_unit,
            listOf("c" to R.string.u_c, "f" to R.string.u_f),
            prefs.tempUnit) { prefs.tempUnit = it }
        note(getString(R.string.s_vehicle_note))
    }

    // ============ 1. CAN ============
    private fun can() {
        val s = VehicleDataRepository.canStats()
        note(getString(R.string.s_can_readonly))
        label(getString(R.string.can_status) + ": " + when (s.state) {
            CanState.CONNECTED -> "CONNECTED"
            CanState.CONNECTING -> "CONNECTING"
            else -> "DISCONNECTED"
        })
        mono(
            "Provider: ${s.provider}\nProtocol: ${s.protocol}\nMCU: ${s.mcu ?: "-"}\n" +
            "RX: ${s.rx}   TX: ${s.tx} (read-only)   Errors: ${s.errors}\n" +
            "Last frame: ${s.lastFrame?.let { it.idText() + "  " + it.hex() } ?: "-"}\n" +
            "Last error: ${s.lastError ?: "-"}"
        )

        val ids = listOf("auto") + CanDiscovery.run(this).candidates
            .filter { it.usable }.map { it.id }
        label(getString(R.string.s_can_provider))
        root.addView(Spinner(this).apply {
            adapter = ArrayAdapter(
                this@SettingsActivity,
                android.R.layout.simple_spinner_dropdown_item,
                ids.map { if (it == "auto") getString(R.string.s_can_auto) else it }
            )
            setSelection(ids.indexOf(prefs.canProviderMode).coerceAtLeast(0))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    if (prefs.canProviderMode != ids[pos]) {
                        prefs.canProviderMode = ids[pos]
                        canDirty = true
                    }
                }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
        })

        val a = edit(R.string.s_can_action, prefs.canAction, ltr = true)
        onLeave += {
            if (a.text.toString() != prefs.canAction) {
                prefs.canAction = a.text.toString().trim()
                canDirty = true
            }
        }
        switch(R.string.s_demo, prefs.demoMode) {
            prefs.demoMode = it
            canDirty = true
        }
        button(R.string.s_can_rescan) {
            canDirty = false
            flush()
            VehicleDataRepository.restart(this)
            toast(R.string.saved)
        }
        button(R.string.s_can_monitor) {
            startActivity(Intent(this, DiagnosticsActivity::class.java))
        }
        button(R.string.s_can_clear_frames) {
            CanFrameLog.clear()
            toast(R.string.saved)
        }
    }

    // ============ 2. Dashboard ============
    private fun dashboard() {
        radio(R.string.s_dash_style,
            listOf("bmw" to R.string.s_dash_bmw, "classic" to R.string.s_dash_classic),
            prefs.dashStyle) {
            prefs.dashStyle = it
            uiDirty = true
        }
        radio(R.string.s_layout,
            listOf(
                "standard" to R.string.l_standard,
                "left" to R.string.l_left,
                "right" to R.string.l_right,
                "custom" to R.string.l_custom
            ), prefs.layout) { prefs.layout = it }
        radio(R.string.s_custom_side,
            listOf("left" to R.string.side_left, "right" to R.string.side_right),
            prefs.customSide) { prefs.customSide = it }
        switch(R.string.s_info_clock, prefs.showInfoClock) { prefs.showInfoClock = it }
        switch(R.string.s_info_date, prefs.showInfoDate) { prefs.showInfoDate = it }
        switch(R.string.s_info_vehicle, prefs.showInfoVehicle) { prefs.showInfoVehicle = it }

        val r = edit(R.string.s_redline, prefs.redline.toString(), number = true)
        val sm = edit(R.string.s_speed_max, prefs.speedMax.toString(), number = true)
        onLeave += {
            r.text.toString().toIntOrNull()?.takeIf { it in 2000..9000 }?.let { prefs.redline = it }
            sm.text.toString().toIntOrNull()?.takeIf { it in 120..320 }?.let { prefs.speedMax = it }
        }
    }

    // ============ 3. Launcher (NEW) ============
    private fun launcher() {
        radio(R.string.s_launcher_style,
            listOf(
                "cards" to R.string.s_launcher_cards,
                "e90" to R.string.s_launcher_e90,
                "idrive" to R.string.s_launcher_idrive
            ), prefs.launcherStyle) {
            prefs.launcherStyle = it
            uiDirty = true
        }

        switch(R.string.s_splash_enabled, prefs.splashEnabled) { prefs.splashEnabled = it }
        seek(R.string.s_splash_duration,
            prefs.splashMs / 100, 5, 60) { prefs.splashMs = it * 100 }

        note("تغییرات سبک منو و صفحه آغازین، در اجرای بعدی اعمال می‌شود.")
    }

    // ============ 4. Clock ============
    private fun clock() {
        switch(R.string.s_clock24, prefs.clock24) { prefs.clock24 = it }
        switch(R.string.s_analog, prefs.analogClock) { prefs.analogClock = it }
        switch(R.string.s_show_date, prefs.showDate) { prefs.showDate = it }
        switch(R.string.s_show_day, prefs.showDay) { prefs.showDay = it }
        mono(SimpleDateFormat("HH:mm  dd/MM/yyyy  EEEE", Locale.US).format(Date()) +
             "\n" + TimeZone.getDefault().id)
        note(getString(R.string.s_clock_note))
        button(R.string.s_open_date_settings) { open(Intent(Settings.ACTION_DATE_SETTINGS)) }
    }

    // ============ 5. Display ============
    private fun display() {
        switch(R.string.s_fullscreen, prefs.fullscreen) { prefs.fullscreen = it }
        switch(R.string.s_keep_on, prefs.keepScreenOn) { prefs.keepScreenOn = it }
        seek(R.string.s_brightness, if (prefs.brightness < 0) 80 else prefs.brightness, 5, 100) {
            prefs.brightness = it
        }
        val dm = resources.displayMetrics
        mono("${dm.widthPixels}x${dm.heightPixels}  ${dm.densityDpi}dpi  " +
             "(${resources.configuration.screenWidthDp}x${resources.configuration.screenHeightDp}dp)")
    }

    // ============ 6. Voice ============
    private fun voice() {
        switch(R.string.s_tts, prefs.ttsEnabled) { prefs.ttsEnabled = it }
        switch(R.string.s_wake, prefs.wakeWord) { prefs.wakeWord = it }
        switch(R.string.s_offline_stt, prefs.offlineSttEnabled) { prefs.offlineSttEnabled = it }
        note(getString(R.string.s_offline_stt_desc))

        val mic = packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_MICROPHONE) &&
                  android.speech.SpeechRecognizer.isRecognitionAvailable(this)
        label(getString(if (mic) R.string.mic_ok else R.string.mic_unavailable))
        HardwareProfiler.load(this)?.optBoolean("ttsPersian", false)?.let {
            label(getString(if (it) R.string.voice_out_ok else R.string.voice_out_unavailable))
        }
        button(R.string.s_open_tts) { open(Intent("com.android.settings.TTS_SETTINGS")) }
    }

    // ============ 7. AI / OpenAI ============
    private fun ai() {
        switch(R.string.s_ai_enable, prefs.aiEnabled) { prefs.aiEnabled = it }

        val k = edit(R.string.s_api_key, if (prefs.apiKey.isEmpty()) "" else MASK, secret = true, ltr = true)
        if (!prefs.secureStorageAvailable) {
            note("هشدار: ذخیره‌سازی امن (Keystore) در دسترس نیست. کلید در حالت معمولی ذخیره می‌شود.")
        }

        val m = edit(R.string.s_model, prefs.aiModel, ltr = true)
        val u = edit(R.string.s_base_url, prefs.aiBaseUrl, ltr = true)
        note(getString(R.string.s_base_url_hint))

        onLeave += {
            val t = k.text.toString().trim()
            if (t != MASK) prefs.apiKey = t
            prefs.aiModel = m.text.toString().trim().ifBlank { "gpt-4o-mini" }
            prefs.aiBaseUrl = u.text.toString().trim().ifBlank { "https://api.openai.com/v1" }
                .trimEnd('/')
        }
        label(getString(R.string.internet) + ": " +
              getString(if (Net.online(this)) R.string.online else R.string.offline))
    }

    // ============ 8. Media ============
    private fun media() {
        seek(R.string.s_vol_step, prefs.volumeStep, 1, 5) { prefs.volumeStep = it }
        button(R.string.s_open_sound) { open(Intent(Settings.ACTION_SOUND_SETTINGS)) }
    }

    // ============ 9. Steering wheel ============
    private lateinit var swcLabel: TextView
    private fun swc() {
        swcLabel = label(getString(R.string.s_swc_voice) + ": " + KeyEvent.keyCodeToString(prefs.swcVoiceKey))
        button(R.string.s_swc_learn) {
            learningKey = true
            swcLabel.setText(R.string.s_swc_wait)
        }
        note(getString(R.string.s_swc_note))
    }

    override fun onKeyDown(code: Int, e: KeyEvent): Boolean {
        if (learningKey && code != KeyEvent.KEYCODE_BACK) {
            prefs.swcVoiceKey = code
            learningKey = false
            swcLabel.text = getString(R.string.s_swc_voice) + ": " + KeyEvent.keyCodeToString(code)
            return true
        }
        return super.onKeyDown(code, e)
    }

    // ============ 10. Diagnostics ============
    private fun diag() {
        button(R.string.s_open_diag) {
            startActivity(Intent(this, DiagnosticsActivity::class.java))
        }
        label(getString(R.string.s_program_log))
        val log = mono(LogManager.read(this, 6000).ifBlank { "-" })
        button(R.string.s_clear_log) {
            LogManager.clear(this)
            log.text = "-"
            toast(R.string.s_log_cleared)
        }
    }

    // ============ 11. System ============
    private fun system() {
        switch(R.string.s_start_boot, prefs.startOnBoot) { prefs.startOnBoot = it }
        button(R.string.s_home_settings) { open(Intent(Settings.ACTION_HOME_SETTINGS)) }
        if (CrashHandler.safeMode(this)) {
            note(getString(R.string.safe_banner))
            button(R.string.s_exit_safe) {
                CrashHandler.clearSafeMode(this)
                canDirty = true
                show(current)
            }
        }
        label(getString(R.string.s_profile))
        val prof = mono(HardwareProfiler.load(this)?.toString(2) ?: "-")
        button(R.string.s_rerun_profile) {
            Thread {
                val j = HardwareProfiler.run(applicationContext)
                runOnUiThread { prof.text = j.toString(2) }
            }.start()
        }
    }

    // ============ 12. About ============
    private fun about() {
        label(getString(R.string.version) + ": " + BuildConfig.VERSION_NAME)
        note(getString(R.string.about_text))
    }

    // ============ helpers ============
    private fun flush() {
        onLeave.forEach { runCatching { it() } }
        onLeave.clear()
    }

    override fun onPause() {
        flush()
        if (canDirty) {
            canDirty = false
            VehicleDataRepository.restart(this)
        }
        if (uiDirty) {
            uiDirty = false
            Toast.makeText(this, "برای اعمال تغییرات، برنامه را دوباره اجرا کنید.",
                Toast.LENGTH_LONG).show()
        }
        super.onPause()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(id: Int) = Toast.makeText(this, id, Toast.LENGTH_SHORT).show()
    private fun open(i: Intent) { runCatching { startActivity(i) } }

    private fun header(id: Int) = root.addView(TextView(this).apply {
        setText(id)
        textSize = 20f
        setTextColor(getColor(R.color.accent))
        setPadding(0, 0, 0, dp(12))
    })

    private fun label(s: String) = TextView(this).apply {
        text = s
        textSize = 14f
        setTextColor(getColor(R.color.text_dim))
        setPadding(0, dp(10), 0, dp(4))
    }.also { root.addView(it) }

    private fun note(s: String) = root.addView(TextView(this).apply {
        text = s
        textSize = 13f
        setTextColor(getColor(R.color.amber))
        setPadding(0, dp(8), 0, dp(8))
    })

    private fun mono(s: String) = TextView(this).apply {
        text = s
        textSize = 12f
        typeface = android.graphics.Typeface.MONOSPACE
        textDirection = View.TEXT_DIRECTION_LTR
        gravity = Gravity.START
        setTextIsSelectable(true)
        setTextColor(getColor(R.color.text))
        setPadding(0, dp(6), 0, dp(6))
    }.also { root.addView(it) }

    private fun switch(id: Int, v: Boolean, on: (Boolean) -> Unit) = root.addView(Switch(this).apply {
        setText(id)
        isChecked = v
        textSize = 15f
        setTextColor(getColor(R.color.text))
        setPadding(0, dp(8), 0, dp(8))
        setOnCheckedChangeListener { _, c -> on(c) }
    })

    private fun button(id: Int, on: () -> Unit) = root.addView(Button(this).apply {
        setText(id)
        setOnClickListener { on() }
    })

    private fun seek(id: Int, v: Int, min: Int, max: Int, on: (Int) -> Unit) {
        val l = label(getString(id) + ": " + v)
        root.addView(SeekBar(this).apply {
            this.max = max - min
            progress = v - min
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) {
                    if (u) {
                        on(p + min)
                        l.text = getString(id) + ": " + (p + min)
                    }
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        })
    }

    private fun radio(id: Int, opts: List<Pair<String, Int>>, cur: String, on: (String) -> Unit) {
        label(getString(id))
        root.addView(RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            opts.forEachIndexed { i, (key, label) ->
                addView(RadioButton(this@SettingsActivity).apply {
                    this.id = 1000 + i
                    setText(label)
                    setTextColor(getColor(R.color.text))
                    isChecked = key == cur
                    setPadding(0, 0, dp(16), 0)
                })
            }
            setOnCheckedChangeListener { _, checked ->
                opts.getOrNull(checked - 1000)?.let { on(it.first) }
            }
        })
    }

    private fun edit(
        id: Int, v: String,
        secret: Boolean = false, number: Boolean = false, ltr: Boolean = false
    ): EditText {
        label(getString(id))
        return EditText(this).apply {
            setText(v)
            setTextColor(getColor(R.color.text))
            isSingleLine = true
            if (ltr) textDirection = View.TEXT_DIRECTION_LTR
            inputType = when {
                secret -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                number -> InputType.TYPE_CLASS_NUMBER
                else -> InputType.TYPE_CLASS_TEXT
            }
        }.also { root.addView(it) }
    }

    companion object {
        private const val MASK = "••••••••"
    }
}
