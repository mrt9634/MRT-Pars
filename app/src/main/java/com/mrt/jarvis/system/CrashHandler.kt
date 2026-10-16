package com.mrt.jarvis.system

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.mrt.jarvis.hub.HubActivity
import kotlin.system.exitProcess

/**
 * Captures uncaught exceptions, writes a small record to MRT1's own log, clears MRT1's own
 * cache, and restarts the dashboard. Crash-loop guard: 3 crashes within 2 minutes => SAFE MODE
 * (CAN providers not started) and no further automatic restart.
 *
 * Recovery target is HubActivity (not Splash) — we want a fast recovery, not another 2.5s splash.
 */
class CrashHandler private constructor(private val app: Context) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(t: Thread, e: Throwable) {
        try {
            ErrorLogger.crash(app, t, e)
            LogManager.cleanOwnCache(app, 0)
            val p = app.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()
            val recent = (p.getString("crashes", "") ?: "").split(',').mapNotNull { it.toLongOrNull() }
                .filter { now - it < WINDOW } + now
            val loop = recent.size >= 3
            p.edit().putString("crashes", recent.joinToString(",")).putBoolean("safe_mode", loop).commit()
            if (!loop) scheduleRestart()
        } catch (_: Throwable) { }
        android.os.Process.killProcess(android.os.Process.myPid())
        exitProcess(10)
    }

    private fun scheduleRestart() {
        val i = Intent(app, HubActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val pi = PendingIntent.getActivity(
            app, 7, i,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        app.getSystemService(AlarmManager::class.java)
            ?.set(AlarmManager.RTC, System.currentTimeMillis() + 1500, pi)
    }

    companion object {
        const val PREF = "mrt_crash"
        private const val WINDOW = 120_000L

        fun install(ctx: Context) {
            Thread.setDefaultUncaughtExceptionHandler(CrashHandler(ctx.applicationContext))
        }

        fun safeMode(ctx: Context) =
            ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean("safe_mode", false)

        fun clearSafeMode(ctx: Context) =
            ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                .putBoolean("safe_mode", false)
                .putString("crashes", "")
                .apply()
    }
}

object ErrorLogger {
    fun crash(ctx: Context, t: Thread, e: Throwable) = LogManager.e(ctx, "CRASH in thread ${t.name}", e)
    fun warn(ctx: Context, where: String, e: Throwable) = LogManager.e(ctx, "WARN $where", e)
}
