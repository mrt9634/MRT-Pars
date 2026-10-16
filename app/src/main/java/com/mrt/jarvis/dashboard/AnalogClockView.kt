package com.mrt.jarvis.dashboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Lightweight analog clock. Reads the Android system clock/timezone; redrawn once per minute by the host. */
class AnalogClockView @JvmOverloads constructor(c: Context, a: AttributeSet? = null) : View(c, a) {
    private val d = resources.displayMetrics.density
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f * d; color = 0x55FFFFFF }
    private val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 2f * d; color = 0x99FFFFFF.toInt(); strokeCap = Paint.Cap.ROUND }
    private val hourP = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 4f * d; color = 0xFFEFF3F8.toInt(); strokeCap = Paint.Cap.ROUND }
    private val minP = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 2.5f * d; color = 0xFF3FA9F5.toInt(); strokeCap = Paint.Cap.ROUND }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f; val cy = height / 2f; val r = min(cx, cy) - 4 * d
        if (r <= 0) return
        c.drawCircle(cx, cy, r, ring)
        for (i in 0 until 12) {
            val a = Math.toRadians(i * 30.0)
            c.drawLine(cx + (r * 0.86f * sin(a)).toFloat(), cy - (r * 0.86f * cos(a)).toFloat(),
                cx + (r * 0.96f * sin(a)).toFloat(), cy - (r * 0.96f * cos(a)).toFloat(), tick)
        }
        val now = Calendar.getInstance()
        val m = now.get(Calendar.MINUTE); val h = now.get(Calendar.HOUR) + m / 60f
        hand(c, cx, cy, r * 0.5f, h * 30.0, hourP)
        hand(c, cx, cy, r * 0.78f, m * 6.0, minP)
    }
    private fun hand(c: Canvas, cx: Float, cy: Float, len: Float, deg: Double, p: Paint) {
        val a = Math.toRadians(deg)
        c.drawLine(cx, cy, cx + (len * sin(a)).toFloat(), cy - (len * cos(a)).toFloat(), p)
    }
}
