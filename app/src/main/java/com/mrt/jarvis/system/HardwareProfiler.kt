package com.mrt.jarvis.system

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.speech.SpeechRecognizer
import android.util.DisplayMetrics
import android.view.WindowManager
import com.mrt.jarvis.can.CanDiscovery
import org.json.JSONObject

/**
 * First-launch hardware profile. Detects what really exists on this head unit and stores it
 * locally (private prefs). Read-only: changes nothing in Android, MCU or CAN configuration.
 */
object HardwareProfiler {
    private const val PREF = "mrt_profile"

    fun isDone(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean("done", false)
    fun load(ctx: Context): JSONObject? = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        .getString("json", null)?.let { runCatching { JSONObject(it) }.getOrNull() }

    /** Call off the main thread. Persian TTS availability is added later by the voice layer. */
    fun run(ctx: Context): JSONObject {
        val pm = ctx.packageManager
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION") ctx.getSystemService(WindowManager::class.java)?.defaultDisplay?.getRealMetrics(dm)
        val can = CanDiscovery.run(ctx)
        val st = runCatching { StatFs(Environment.getDataDirectory().path) }.getOrNull()
        val navMode = runCatching { android.provider.Settings.Secure.getInt(ctx.contentResolver, "navigation_mode") }.getOrDefault(-1)
        val res = ctx.resources
        fun dimen(name: String): Int {
            val id = res.getIdentifier(name, "dimen", "android")
            return if (id > 0) res.getDimensionPixelSize(id) else 0
        }
        val j = JSONObject()
            .put("android", Build.VERSION.RELEASE).put("sdk", Build.VERSION.SDK_INT)
            .put("device", "${Build.MANUFACTURER} ${Build.MODEL}").put("hardware", Build.HARDWARE)
            .put("width", dm.widthPixels).put("height", dm.heightPixels).put("dpi", dm.densityDpi).put("density", dm.density.toDouble())
            .put("orientation", if (dm.widthPixels >= dm.heightPixels) "landscape" else "portrait")
            .put("navMode", navMode).put("statusBarPx", dimen("status_bar_height")).put("navBarPx", dimen("navigation_bar_height"))
            .put("mic", pm.hasSystemFeature(PackageManager.FEATURE_MICROPHONE))
            .put("stt", SpeechRecognizer.isRecognitionAvailable(ctx))
            .put("bluetooth", pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH))
            .put("gps", pm.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS))
            .put("usbHost", pm.hasSystemFeature(PackageManager.FEATURE_USB_HOST))
            .put("storageFreeMb", st?.let { it.availableBytes / 1_048_576 } ?: -1L)
            .put("canVendorApps", can.vendorApps.joinToString())
            .put("canServices", can.services.joinToString())
            .put("canCandidates", can.candidates.filter { it.usable }.joinToString { it.id })
            .put("mcu", can.mcuText ?: "")
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString("json", j.toString()).putBoolean("done", true).apply()
        LogManager.i(ctx, "First-launch profile: $j")
        return j
    }

    fun setTts(ctx: Context, persian: Boolean) {
        val j = load(ctx) ?: return
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString("json", j.put("ttsPersian", persian).toString()).apply()
    }

    fun reset(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().clear().apply()
}

/**
 * One-time initialization reboot. Only a privileged (system-signed) build holds REBOOT;
 * a normal install never does, so we simply ask the user to reboot once. Never crashes.
 */
object InitReboot {
    private const val PREF = "mrt_profile"
    fun pending(ctx: Context) = !ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean("reboot_handled", false)
    fun markHandled(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putBoolean("reboot_handled", true).commit()
    fun canReboot(ctx: Context) = ctx.checkSelfPermission("android.permission.REBOOT") == PackageManager.PERMISSION_GRANTED

    /** Returns true if a controlled reboot was actually started. Marked first, so it can never loop. */
    fun tryReboot(ctx: Context): Boolean {
        markHandled(ctx)
        if (!canReboot(ctx)) return false
        return runCatching { ctx.getSystemService(android.os.PowerManager::class.java)?.reboot("mrt1-init"); true }.getOrDefault(false)
    }
}
