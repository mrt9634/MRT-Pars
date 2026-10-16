package com.mrt.jarvis.can

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.mrt.jarvis.vehicle.VehicleData
import java.io.File
import java.io.FileInputStream

/** Nothing connected. Honest "not available". */
class NoCanProvider(private val reason: String) : CanProvider {
    override val name = "none"
    override val protocol = "-"
    override fun start(listener: CanListener) = listener.onState(CanState.DISCONNECTED, reason)
    override fun stop() {}
}

/**
 * Maps vendor broadcast extras to VehicleData. Only keys actually present are used;
 * missing values stay unchanged (null => "--"). Returns true if any value was usable.
 */
@Suppress("DEPRECATION")
fun applyExtras(ex: Bundle, listener: CanListener): Boolean {
    fun f(vararg k: String): Float? = k.firstNotNullOfOrNull { key ->
        if (!ex.containsKey(key)) null else when (val o = ex.get(key)) {
            is Number -> o.toFloat(); is String -> o.toFloatOrNull(); else -> null } }
    fun b(vararg k: String): Boolean? = k.firstNotNullOfOrNull { key ->
        if (!ex.containsKey(key)) null else when (val o = ex.get(key)) {
            is Boolean -> o; is Number -> o.toInt() != 0; is String -> o == "1" || o.equals("true", true); else -> null } }
    val gear: String? = when (val g = ex.get("gear")) {
        is String -> g.takeIf { it.isNotBlank() }
        is Number -> when (g.toInt()) { 0 -> "P"; 1 -> "R"; 2 -> "N"; 3 -> "D"; else -> null }
        else -> null
    }
    val u = VehicleData(
        speedKmh = f("speed", "vehicle_speed", "carSpeed"), rpm = f("rpm", "engine_rpm", "engineSpeed"),
        engineTempC = f("water_temp", "engine_temp", "coolant"), fuelPercent = f("fuel", "fuel_level", "fuelPercent"),
        batteryVolt = f("battery", "voltage", "batt_volt"), outsideTempC = f("outside_temp", "out_temp", "ambient"),
        tripKm = f("trip"), odometerKm = f("odometer", "mileage", "total_km"), avgSpeed = f("avg_speed"),
        instantConsumption = f("instant_consumption", "inst_fuel"), avgConsumption = f("avg_consumption", "avg_fuel"),
        gear = gear, reverse = b("reverse", "rear", "back_car"), parkingBrake = b("handbrake", "parking_brake", "epb"),
        brake = b("brake", "foot_brake"), doorOpen = b("door", "door_open", "anyDoor"), trunkOpen = b("trunk", "boot"),
        hoodOpen = b("hood", "bonnet"), seatBelt = b("seatbelt", "belt"), lights = b("lights", "low_beam", "sidelight"),
        highBeam = b("high_beam", "highbeam"), turnLeft = b("turn_left", "left_turn"), turnRight = b("turn_right", "right_turn"),
        engineRunning = b("engine", "acc", "engine_running"))
    if (u == VehicleData()) return false
    listener.onVehicle { v -> v.copy(
        speedKmh = u.speedKmh ?: v.speedKmh, rpm = u.rpm ?: v.rpm, engineTempC = u.engineTempC ?: v.engineTempC,
        fuelPercent = u.fuelPercent ?: v.fuelPercent, batteryVolt = u.batteryVolt ?: v.batteryVolt,
        outsideTempC = u.outsideTempC ?: v.outsideTempC, tripKm = u.tripKm ?: v.tripKm,
        odometerKm = u.odometerKm ?: v.odometerKm, avgSpeed = u.avgSpeed ?: v.avgSpeed,
        instantConsumption = u.instantConsumption ?: v.instantConsumption, avgConsumption = u.avgConsumption ?: v.avgConsumption,
        gear = u.gear ?: v.gear, reverse = u.reverse ?: v.reverse, parkingBrake = u.parkingBrake ?: v.parkingBrake,
        brake = u.brake ?: v.brake, doorOpen = u.doorOpen ?: v.doorOpen, trunkOpen = u.trunkOpen ?: v.trunkOpen,
        hoodOpen = u.hoodOpen ?: v.hoodOpen, seatBelt = u.seatBelt ?: v.seatBelt, lights = u.lights ?: v.lights,
        highBeam = u.highBeam ?: v.highBeam, turnLeft = u.turnLeft ?: v.turnLeft, turnRight = u.turnRight ?: v.turnRight,
        engineRunning = u.engineRunning ?: v.engineRunning, isDemo = false) }
    return true
}

/** Passively listens to every known vendor CAN broadcast plus an optional custom action. Read-only. */
class VendorBroadcastCanProvider(private val ctx: Context, private val custom: String) : CanProvider {
    override val name = "Vendor broadcast"
    @Volatile private var proto = "Intent"
    override val protocol: String get() = proto
    private var rx: BroadcastReceiver? = null

    override fun start(listener: CanListener) {
        listener.onState(CanState.CONNECTING)
        rx = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                val ex = i.extras ?: return
                var ok = false
                ex.getByteArray("frame")?.let { listener.onFrame(CanMessage(ex.getInt("id", -1), it)); ok = true }
                ex.getByteArray("data")?.let { listener.onFrame(CanMessage(ex.getInt("id", -1), it)); ok = true }
                if (applyExtras(ex, listener)) ok = true
                if (ok) { proto = "Intent: ${i.action}"; listener.onState(CanState.CONNECTED) }
            }
        }
        val f = IntentFilter()
        (CanDiscovery.VENDOR_ACTIONS + listOfNotNull(custom.trim().ifBlank { null })).distinct().forEach { f.addAction(it) }
        ctx.registerReceiver(rx, f)
    }
    override fun stop() { rx?.let { runCatching { ctx.unregisterReceiver(it) } }; rx = null }
}

/**
 * Read-only serial stream. Port settings are inherited from the vendor (no native termios),
 * so bytes are logged RAW and undecoded. CONNECTED only once real bytes arrive.
 */
class SerialCanProvider(private val path: String) : CanProvider {
    override val name = "Serial $path"
    override val protocol = "Serial (raw, undecoded)"
    @Volatile private var run = false
    private var th: Thread? = null

    override fun start(listener: CanListener) {
        listener.onState(CanState.CONNECTING); run = true
        th = Thread({
            try {
                FileInputStream(File(path)).use { inp ->
                    val b = ByteArray(64)
                    while (run) {
                        val n = inp.read(b)
                        if (n <= 0) { Thread.sleep(50); continue }
                        listener.onFrame(CanMessage(-1, b.copyOf(n))); listener.onState(CanState.CONNECTED)
                    }
                }
            } catch (e: Exception) { if (run) listener.onState(CanState.ERROR, e.javaClass.simpleName + ": " + e.message) }
        }, "mrt-serial").apply { isDaemon = true; start() }
    }
    override fun stop() { run = false; th?.interrupt(); th = null }
}

/** USB CAN/serial adapter: bulk-IN only (read-only). Uses the standard Android USB permission dialog. */
class UsbCanProvider(private val ctx: Context, private val deviceName: String) : CanProvider {
    override val name = "USB $deviceName"
    override val protocol = "USB bulk (raw, undecoded)"
    private val action = "com.mrt.jarvis.USB_PERMISSION"
    @Volatile private var run = false
    private var conn: UsbDeviceConnection? = null
    private var permRx: BroadcastReceiver? = null

    override fun start(listener: CanListener) {
        val um = ctx.getSystemService(UsbManager::class.java)
        val dev = um?.deviceList?.get(deviceName)
        if (um == null || dev == null) { listener.onState(CanState.DISCONNECTED, "device gone"); return }
        listener.onState(CanState.CONNECTING)
        if (um.hasPermission(dev)) open(um, dev, listener) else {
            permRx = object : BroadcastReceiver() { override fun onReceive(c: Context, i: Intent) {
                if (i.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) open(um, dev, listener)
                else listener.onState(CanState.DISCONNECTED, "USB permission denied") } }
            ctx.registerReceiver(permRx, IntentFilter(action))
            um.requestPermission(dev, PendingIntent.getBroadcast(ctx, 0, Intent(action).setPackage(ctx.packageName), 0))
        }
    }

    private fun open(um: UsbManager, dev: UsbDevice, l: CanListener) {
        for (ii in 0 until dev.interfaceCount) {
            val intf = dev.getInterface(ii)
            for (ei in 0 until intf.endpointCount) {
                val ep = intf.getEndpoint(ei)
                if (ep.type != UsbConstants.USB_ENDPOINT_XFER_BULK || ep.direction != UsbConstants.USB_DIR_IN) continue
                val c = um.openDevice(dev)
                if (c == null) { l.onState(CanState.ERROR, "openDevice failed"); return }
                if (!c.claimInterface(intf, true)) { c.close(); l.onState(CanState.ERROR, "claimInterface failed"); return }
                conn = c; run = true
                Thread({
                    val b = ByteArray(ep.maxPacketSize.coerceAtLeast(64))
                    while (run) {
                        val n = c.bulkTransfer(ep, b, b.size, 500)
                        if (n > 0) { l.onFrame(CanMessage(-1, b.copyOf(n))); l.onState(CanState.CONNECTED) }
                    }
                }, "mrt-usb").apply { isDaemon = true; start() }
                return
            }
        }
        l.onState(CanState.ERROR, "no bulk-IN endpoint")
    }

    override fun stop() {
        run = false; conn?.close(); conn = null
        permRx?.let { runCatching { ctx.unregisterReceiver(it) } }; permRx = null
    }
}

/** UI TEST ONLY. Off by default and clearly flagged on screen. */
class DemoProvider : CanProvider {
    override val name = "DEMO"
    override val protocol = "demo"
    private val h = Handler(Looper.getMainLooper())
    private var t = 0f
    private var l: CanListener? = null
    private val tick = object : Runnable { override fun run() {
        t += 0.1f
        val sp = 60f + 40f * kotlin.math.sin(t / 4)
        l?.onVehicle { VehicleData(speedKmh = sp, rpm = 1200f + sp * 25, engineTempC = 90f, fuelPercent = 67f,
            batteryVolt = 13.8f, outsideTempC = 24f, tripKm = 12.4f, gear = "D", lights = true,
            engineRunning = true, seatBelt = true, isDemo = true) }
        h.postDelayed(this, 200)
    } }
    override fun start(listener: CanListener) { l = listener; listener.onState(CanState.CONNECTED, "demo"); h.post(tick) }
    override fun stop() { h.removeCallbacks(tick); l = null }
}
