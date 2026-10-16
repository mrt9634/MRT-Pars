package com.mrt.jarvis.can

/** In-memory ring buffer of REAL received frames only. Never persisted, never synthesized. */
object CanFrameLog {
    private const val CAP = 300
    private val buf = ArrayDeque<CanMessage>(CAP)
    private val counts = HashMap<Int, Long>()
    private var firstTs = 0L
    private var total = 0L

    @Synchronized fun add(m: CanMessage) {
        if (buf.size >= CAP) buf.removeFirst()
        buf.addLast(m); total++
        counts[m.id] = (counts[m.id] ?: 0L) + 1L
        if (firstTs == 0L) firstTs = m.timestamp
    }
    @Synchronized fun recent(n: Int = 40): List<CanMessage> = buf.toList().takeLast(n).reversed()
    /** Measured update frequency per frame ID (Hz) since logging started. */
    @Synchronized fun idRates(): Map<Int, Float> {
        if (firstTs == 0L) return emptyMap()
        val secs = ((System.currentTimeMillis() - firstTs) / 1000f).coerceAtLeast(1f)
        return counts.mapValues { it.value / secs }
    }
    @Synchronized fun total() = total
    @Synchronized fun clear() { buf.clear(); counts.clear(); firstTs = 0L; total = 0L }
}
