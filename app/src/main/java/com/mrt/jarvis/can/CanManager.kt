package com.mrt.jarvis.can

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.mrt.jarvis.R
import com.mrt.jarvis.settings.AppPrefs
import com.mrt.jarvis.system.CrashHandler
import com.mrt.jarvis.system.LogManager
import com.mrt.jarvis.vehicle.VehicleData

/**
 * Automatic provider selection (READ-ONLY, nothing is ever transmitted):
 * 1. Discover real candidates on this head unit.
 * 2. Start every usable candidate in parallel (all passive readers).
 * 3. The first one that delivers REAL data wins; the others are stopped.
 * 4. If nothing delivers data within the timeout => "CAN DISCONNECTED", but the passive
 *    vendor-broadcast listener stays alive so late data still connects automatically.
 */
class CanManager(private val ctx: Context, private val sink: (VehicleData.() -> VehicleData) -> Unit,
                 private val statsSink: (CanStats) -> Unit) {
    private val h = Handler(Looper.getMainLooper())
    private val active = java.util.concurrent.CopyOnWriteArrayList<CanProvider>()
    @Volatile private var selected: CanProvider? = null
    private var stats = CanStats()
    private val decoder = CanDecoder()
    private val lock = Any()

    fun start() {
        val prefs = AppPrefs(ctx)
        if (prefs.demoMode) { launch(listOf(DemoProvider()), prefs); return }
        if (CrashHandler.safeMode(ctx)) {
            launch(listOf(NoCanProvider(ctx.getString(R.string.safe_mode_reason))), prefs); return }
        val rep = CanDiscovery.run(ctx)
        val mode = prefs.canProviderMode   // "auto" or a specific candidate id chosen in Settings
        val list = ArrayList<CanProvider>()
        for (c in rep.candidates) {
            if (!c.usable || (mode != "auto" && c.id != mode)) continue
            when (c.kind) {
                CanDiscovery.Kind.BROADCAST -> list += VendorBroadcastCanProvider(ctx, prefs.canAction)
                CanDiscovery.Kind.SERIAL -> list += SerialCanProvider(c.id)
                CanDiscovery.Kind.USB -> list += UsbCanProvider(ctx, c.id)
                else -> {}
            }
        }
        stats = CanStats(mcu = rep.mcuText, tried = rep.candidates.map {
            "${it.kind}  ${it.id}  ${if (it.usable) "usable" else "not usable"}  ${it.note}".trim() })
        if (list.isEmpty()) list += NoCanProvider(ctx.getString(R.string.can_reason_none))
        launch(list, prefs)
    }

    private fun launch(list: List<CanProvider>, prefs: AppPrefs) {
        update { copy(state = CanState.CONNECTING, provider = "AUTO", protocol = "auto-detect") }
        for (p in list) {
            active += p
            try { p.start(listenerFor(p)) } catch (e: Exception) {
                LogManager.e(ctx, "CAN start ${p.name}", e); update { copy(errors = errors + 1, lastError = e.message) } }
        }
        val only = list.singleOrNull()
        if (only is NoCanProvider) {
            update { copy(state = CanState.DISCONNECTED, provider = "none", protocol = "-") }
            return
        }
        h.postDelayed(timeout, prefs.canTimeoutMs.toLong())
    }

    private val timeout = Runnable {
        if (selected != null) return@Runnable
        active.filter { it !is VendorBroadcastCanProvider }.forEach { runCatching { it.stop() }; active.remove(it) }
        update { copy(state = CanState.DISCONNECTED, provider = "none", protocol = "-",
            reason = ctx.getString(R.string.can_reason_no_data)) }
    }

    private fun select(p: CanProvider): Boolean {
        synchronized(lock) {
            if (selected != null) return selected === p
            selected = p
        }
        h.post { active.filter { it !== p }.forEach { runCatching { it.stop() }; active.remove(it) } }
        LogManager.i(ctx, "CAN provider selected: ${p.name} / ${p.protocol}")
        return true
    }

    private fun listenerFor(p: CanProvider) = object : CanListener {
        override fun onFrame(msg: CanMessage) {
            if (!select(p)) return
            CanFrameLog.add(msg)
            val d = decoder.decode(msg)
            update { copy(rx = rx + 1, lastFrame = msg, decoded = if (d.isEmpty()) decoded else decoded + d) }
            if (d.isNotEmpty()) sink { copy(speedKmh = d["speed"] ?: speedKmh, rpm = d["rpm"] ?: rpm,
                engineTempC = d["engine_temp"] ?: engineTempC, fuelPercent = d["fuel"] ?: fuelPercent,
                batteryVolt = d["battery"] ?: batteryVolt) }
        }
        override fun onVehicle(update: (VehicleData) -> VehicleData) {
            if (select(p)) sink { update(this) }
        }
        override fun onState(state: CanState, reason: String?) {
            if (state == CanState.ERROR) update { copy(errors = errors + 1, lastError = reason) }
            if (p is DemoProvider && state == CanState.CONNECTED) select(p)
            if (selected === p) update { copy(state = state, reason = reason, provider = p.name, protocol = p.protocol) }
        }
    }

    private fun update(f: CanStats.() -> CanStats) {
        val s = synchronized(lock) { stats = stats.f(); stats }
        statsSink(s)
    }

    fun stop() {
        h.removeCallbacks(timeout)
        active.forEach { runCatching { it.stop() } }; active.clear(); selected = null
    }
}
