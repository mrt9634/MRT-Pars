package com.mrt.jarvis.settings

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class AppPrefs(ctx: Context) {
    private val p = ctx.getSharedPreferences("mrt_prefs", Context.MODE_PRIVATE)
    /** API key lives in Keystore-backed encrypted storage, never in source code. */
    private val secure = runCatching {
        EncryptedSharedPreferences.create(ctx, "mrt_secure",
            MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    }.getOrNull()

    // System
    var startOnBoot by bool("boot", true)
    var fullscreen by bool("fullscreen", true)
    var keepScreenOn by bool("keep_on", true)
    var brightness by int("bright", -1)

    // Vehicle
    var vehicleModel by str("vehicle", "Peugeot Pars 1392/1393")
    var speedUnit by str("u_speed", "kmh")        // kmh | mph
    var tempUnit by str("u_temp", "c")            // c | f

    // CAN (READ-ONLY by design; there is no transmit option at all)
    var canProviderMode by str("can_mode", "auto")
    var canAction by str("can_action", "")
    var canTimeoutMs by int("can_timeout", 8000)
    var demoMode by bool("demo", false)          // OFF by default

    // Dashboard
    var layout by str("layout", "standard")       // standard | left | right | custom
    var customSide by str("custom_side", "right") // left | right
    var showInfoClock by bool("info_clock", true)
    var showInfoDate by bool("info_date", true)
    var showInfoVehicle by bool("info_vehicle", true)
    var redline by int("redline", 6000)
    var speedMax by int("speed_max", 220)

    // Clock & date (always the Android system clock and timezone)
    var clock24 by bool("clock24", true)
    var analogClock by bool("analog", false)
    var showDate by bool("show_date", true)
    var showDay by bool("show_day", true)

    // Voice
    var ttsEnabled by bool("tts", true)
    var wakeWord by bool("wake", false)

    // AI
    var aiEnabled by bool("ai", false)
    var aiModel by str("model", "gpt-4o-mini")
    var apiKey: String
        get() = secure?.getString("openai", "") ?: ""
        set(v) { secure?.edit()?.putString("openai", v)?.apply() }

    // Media / steering wheel
    var volumeStep by int("vol_step", 1)
    var swcVoiceKey by int("swc_voice", 231)   // KeyEvent.KEYCODE_VOICE_ASSIST

    private fun bool(k: String, def: Boolean) = object : kotlin.properties.ReadWriteProperty<Any, Boolean> {
        override fun getValue(thisRef: Any, property: kotlin.reflect.KProperty<*>) = p.getBoolean(k, def)
        override fun setValue(thisRef: Any, property: kotlin.reflect.KProperty<*>, value: Boolean) { p.edit().putBoolean(k, value).apply() }
    }
    private fun int(k: String, def: Int) = object : kotlin.properties.ReadWriteProperty<Any, Int> {
        override fun getValue(thisRef: Any, property: kotlin.reflect.KProperty<*>) = p.getInt(k, def)
        override fun setValue(thisRef: Any, property: kotlin.reflect.KProperty<*>, value: Int) { p.edit().putInt(k, value).apply() }
    }
    private fun str(k: String, def: String) = object : kotlin.properties.ReadWriteProperty<Any, String> {
        override fun getValue(thisRef: Any, property: kotlin.reflect.KProperty<*>) = p.getString(k, def) ?: def
        override fun setValue(thisRef: Any, property: kotlin.reflect.KProperty<*>, value: String) { p.edit().putString(k, value).apply() }
    }
}
