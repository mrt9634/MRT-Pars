package com.mrt.jarvis.dashboard

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.min

/** Lightweight arc gauge: paths cached in onSizeChanged, only redraws on value change. */
class GaugeView @JvmOverloads constructor(c: Context, a: AttributeSet? = null) : View(c, a) {
    var maxValue = 220f
    var redlineFrom = Float.MAX_VALUE
    var label = ""
    var unit = ""
    var formatter: (Float) -> String = { it.toInt().toString() }
    private var value: Float? = null
    private var shown = 0f
    private val anim = ValueAnimator().apply { duration = 180; interpolator = DecelerateInterpolator()
        addUpdateListener { shown = it.animatedValue as Float; invalidate() } }
    private val d = resources.displayMetrics.density
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = 0x22FFFFFF }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val red = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0xAAE5484D.toInt() }
    private val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x55FFFFFF; strokeWidth = 1.5f * d }
    private val big = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL) }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF8A96A8.toInt(); textAlign = Paint.Align.CENTER }
    private val oval = RectF()
    private val ticks = Path()

    fun setValue(v: Float?) {
        value = v
        if (v == null) { anim.cancel(); shown = 0f; invalidate(); return }
        anim.cancel(); anim.setFloatValues(shown, v.coerceIn(0f, maxValue)); anim.start()
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val r = min(w, h) / 2f - 14 * d
        oval.set(w / 2f - r, h / 2f - r, w / 2f + r, h / 2f + r)
        val sw = r * 0.07f
        track.strokeWidth = sw; arc.strokeWidth = sw; red.strokeWidth = sw
        arc.shader = SweepGradient(w / 2f, h / 2f, intArrayOf(0xFF1C5A8A.toInt(), 0xFF3FA9F5.toInt(), 0xFFBFE6FF.toInt()), null)
            .also { m -> Matrix().apply { setRotate(120f, w / 2f, h / 2f); m.setLocalMatrix(this) } }
        big.textSize = r * 0.5f; small.textSize = r * 0.13f
        ticks.reset()
        for (i in 0..10) {
            val ang = Math.toRadians((135 + 27.0 * i)); val cx = w / 2f; val cy = h / 2f
            ticks.moveTo(cx + (r - sw * 1.4f) * Math.cos(ang).toFloat(), cy + (r - sw * 1.4f) * Math.sin(ang).toFloat())
            ticks.lineTo(cx + (r - sw * 2.4f) * Math.cos(ang).toFloat(), cy + (r - sw * 2.4f) * Math.sin(ang).toFloat())
        }
    }

    override fun onDraw(c: Canvas) {
        c.drawArc(oval, 135f, 270f, false, track)
        if (redlineFrom < maxValue) c.drawArc(oval, 135f + 270f * redlineFrom / maxValue, 270f * (1 - redlineFrom / maxValue), false, red)
        c.drawPath(ticks, tick)
        if (value != null && shown > 0f) c.drawArc(oval, 135f, 270f * shown / maxValue, false, arc)
        val cx = width / 2f; val cy = height / 2f
        c.drawText(if (value == null) "--" else formatter(shown), cx, cy + big.textSize * 0.3f, big)
        c.drawText(unit, cx, cy + big.textSize * 0.3f + small.textSize * 1.6f, small)
        c.drawText(label, cx, oval.bottom - small.textSize * 0.2f, small)
    }
    override fun onDetachedFromWindow() { anim.cancel(); super.onDetachedFromWindow() }
}
