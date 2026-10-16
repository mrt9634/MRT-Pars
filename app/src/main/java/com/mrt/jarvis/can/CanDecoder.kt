package com.mrt.jarvis.can

/**
 * Decodes raw frames to values using a signal table.
 * The table is EMPTY by default: Peugeot Pars CAN IDs must be verified on the real car
 * (capture frames in Diagnostics, then add signals here). No values are guessed.
 */
class CanDecoder(private val signals: List<CanSignal> = emptyList()) {
    fun decode(msg: CanMessage): Map<String, Float> {
        val out = HashMap<String, Float>()
        for (s in signals) {
            if (s.frameId != msg.id || s.startByte + s.length > msg.data.size) continue
            var raw = 0L
            for (i in 0 until s.length) {
                val b = msg.data[if (s.bigEndian) s.startByte + i else s.startByte + s.length - 1 - i].toLong() and 0xFF
                raw = (raw shl 8) or b
            }
            out[s.name] = raw * s.scale + s.offset
        }
        return out
    }
}
