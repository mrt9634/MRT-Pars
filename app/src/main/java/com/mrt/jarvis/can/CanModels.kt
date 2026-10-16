package com.mrt.jarvis.can

data class CanMessage(val id: Int, val data: ByteArray, val timestamp: Long = System.currentTimeMillis()) {
    fun hex(): String = data.joinToString(" ") { "%02X".format(it) }
    fun idText(): String = if (id < 0) "RAW" else "0x%03X".format(id)
}

/** Describes how to extract one physical value from a frame (DBC-like). */
data class CanSignal(
    val name: String, val frameId: Int, val startByte: Int, val length: Int,
    val scale: Float = 1f, val offset: Float = 0f, val bigEndian: Boolean = true,
)

enum class CanState { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

data class CanStats(
    val state: CanState = CanState.DISCONNECTED,
    val provider: String = "none",
    val protocol: String = "unknown",
    val reason: String? = null,
    val rx: Long = 0, val tx: Long = 0, val errors: Long = 0,
    val lastFrame: CanMessage? = null,
    val lastError: String? = null,
    val mcu: String? = null,
    val decoded: Map<String, Float> = emptyMap(),
    /** Every candidate that was found, with its result, for Diagnostics. */
    val tried: List<String> = emptyList(),
)
