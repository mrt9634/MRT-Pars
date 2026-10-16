package com.mrt.jarvis

import android.app.Application
import com.mrt.jarvis.system.CrashHandler
import com.mrt.jarvis.system.LogManager

/** Lightweight: only installs the crash handler and trims MRT1's own stale cache. No network at startup. */
class MrtApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashHandler.install(this)
        Thread { LogManager.cleanOwnCache(this) }.apply { priority = Thread.MIN_PRIORITY }.start()
    }
}
