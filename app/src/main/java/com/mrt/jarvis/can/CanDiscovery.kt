package com.mrt.jarvis.can

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbManager
import java.io.File

/**
 * STRICTLY READ-ONLY inspection of the real head unit. Never binds to vendor services,
 * never opens a port for writing, never transmits a frame. Reports only what exists.
 */
object CanDiscovery {
    val VENDOR_PACKAGES = listOf(
        "com.xygala.canbus", "com.xy.canbus", "com.xyauto.canbus", "com.syu.canbus", "com.syu.ms",
        "com.microntek.canbus", "com.ts.can", "com.fyt.car", "com.hzbhd.canbus", "com.zhonghong.canbus")
    val VENDOR_ACTIONS = listOf(
        "com.xygala.canbus.CANBUS_DATA", "com.xy.canbus.data", "com.xyauto.canbus.receive", "xy.android.canbus.send",
        "com.syu.canbus.data", "android.intent.action.CANBUS_DATA", "com.microntek.canbus.DATA",
        "com.hzbhd.canbus.data", "com.mtc.canbus.data", "com.ts.can.data", "com.txz.canbus.data")
    val VENDOR_SERVICES = listOf("canbus", "can", "xy_canbus", "xyservice", "mcu", "mcu_service",
        "syu_canbus", "car_service", "carservice", "vendor.can")
    private val MCU_PROPS = listOf("persist.sys.mcu.version", "ro.mcu.version", "persist.xy.mcu", "ro.xy.mcu.version",
        "persist.sys.canbus", "ro.canbus.version", "persist.sys.car.type", "ro.product.mcu")
    private val SERIAL_PREFIX = listOf("ttyS", "ttyMT", "ttyUSB", "ttyACM", "ttyHS", "ttyHSL")

    enum class Kind { BROADCAST, SERVICE, APP, SERIAL, USB, SOCKETCAN }
    data class Candidate(val kind: Kind, val id: String, val usable: Boolean, val note: String = "")
    data class Report(
        val vendorApps: List<String>, val services: List<String>, val socketCan: List<String>,
        val serial: List<String>, val readableSerial: List<String>, val usb: List<String>,
        val mcuProps: Map<String, String>, val candidates: List<Candidate>,
    ) {
        val mcuText: String? get() = mcuProps.entries.firstOrNull()?.let { "${it.value} (${it.key})" }
    }

    fun run(ctx: Context): Report {
        val pm = ctx.packageManager
        val apps = VENDOR_PACKAGES.filter {
            try { pm.getPackageInfo(it, 0); true } catch (e: PackageManager.NameNotFoundException) { false } }
        val services = VENDOR_SERVICES.filter { serviceExists(it) }
        val net = File("/sys/class/net").list()?.filter { it.startsWith("can") || it.startsWith("vcan") }?.sorted() ?: emptyList()
        val serial = File("/dev").list()?.filter { n -> SERIAL_PREFIX.any { n.startsWith(it) } }?.sorted() ?: emptyList()
        val readable = serial.filter { runCatching { File("/dev/$it").canRead() }.getOrDefault(false) }
        val usb = usbAdapters(ctx)
        val props = MCU_PROPS.mapNotNull { k -> sysProp(k)?.takeIf { it.isNotBlank() }?.let { k to it } }.toMap()

        val c = ArrayList<Candidate>()
        // Broadcast listening is passive and safe on every ROM (data arrives only if the vendor sends it).
        c += Candidate(Kind.BROADCAST, "vendor-broadcast", true, if (apps.isEmpty()) "no vendor CAN app found" else apps.joinToString())
        services.forEach { c += Candidate(Kind.SERVICE, it, false, "service present, no public read interface") }
        readable.forEach { c += Candidate(Kind.SERIAL, "/dev/$it", true) }
        (serial - readable.toSet()).forEach { c += Candidate(Kind.SERIAL, "/dev/$it", false, "no read permission (system only)") }
        usb.forEach { c += Candidate(Kind.USB, it, true) }
        net.forEach { c += Candidate(Kind.SOCKETCAN, it, false, "AF_CAN not reachable from a normal app") }
        return Report(apps, services, net, serial, readable, usb, props, c)
    }

    private fun serviceExists(name: String): Boolean = runCatching {
        val sm = Class.forName("android.os.ServiceManager")
        sm.getMethod("checkService", String::class.java).invoke(null, name) != null
    }.getOrDefault(false)

    fun sysProp(key: String): String? = runCatching {
        Class.forName("android.os.SystemProperties").getMethod("get", String::class.java).invoke(null, key) as? String
    }.getOrNull()

    private fun usbAdapters(ctx: Context): List<String> = runCatching {
        val um = ctx.getSystemService(UsbManager::class.java) ?: return emptyList()
        um.deviceList.values.filter { d ->
            (0 until d.interfaceCount).any { i -> d.getInterface(i).interfaceClass in setOf(
                UsbConstants.USB_CLASS_CDC_DATA, UsbConstants.USB_CLASS_COMM, UsbConstants.USB_CLASS_VENDOR_SPEC) }
        }.map { it.deviceName }
    }.getOrDefault(emptyList())
}
