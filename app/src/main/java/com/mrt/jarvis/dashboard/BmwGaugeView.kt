package com.mrt.jarvis.dashboard

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * BMW Live Cockpit style arc gauge.
 * Draws a dim track, an accent-colored progress arc, ticks, and numbers.
 */
class BmwGaugeView @JvmOverloads constructor(
    c: Context, a: AttributeSet? = null
) : View(c, a) {

    enum class Direction { CCW, CW }

    var maxValue = 260f
    var minValue = 0f
    var majorStep = 20f
    var minorPerMajor = 2
    var direction = Direction.CW
    var startAngleDeg = 150f
    var sweepAngleDeg = 240f
    var accentColor = 0xFFF5C542.toInt() // BMW Yellow
    var numberFormat: (Float) -> String = { it.toInt().toString() }

    private var value: Float? = null
    private var shown = 0f
    private val anim = ValueAnimator().apply {
        duration = 220
        interpolator = DecelerateInterpolator()
        addUpdateListener { shown = it.animatedValue as Float; invalidate() }
    }
    private val d = resources.displayMetrics.density

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = 0x22FFFFFF
    }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = accentColor
    }
    private val tickMinor = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x66FFFFFF; strokeCap = Paint.Cap.ROUND; strokeWidth = 1.4f * d
    }
    private val tickMajor = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xEEFFFFFF.toInt(); strokeCap = Paint.Cap.ROUND; strokeWidth = 2.6f * d
    }
    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFEFF3F8.toInt(); textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    }
    private val redlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = 0xAAE5484D.toInt()
    }
    var redlineFrom: Float = Float.MAX_VALUE

    private val oval = RectF()
    private var cx = 0f; private var cy = 0f; private var radius = 0f

    fun setValue(v: Float?) {
        value = v
        if (v == null) { anim.cancel(); shown = 0f; invalidate(); return }
        anim.cancel()
        anim.setFloatValues(shown, v.coerceIn(minValue, maxValue))
        anim.start()
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        cx = w / 2f; cy = h / 2f
        radius = min(w, h) / 2f - 20 * d
        oval.set(cx - radius, cy - radius, cx + radius, cy + radius)
        val sw = radius * 0.055f
        ringPaint.strokeWidth = sw
        progressPaint.strokeWidth = sw
        redlinePaint.strokeWidth = sw
        numberPaint.textSize = radius * 0.135f
    }

    private fun angleFor(fraction: Float): Float =
        if (direction == Direction.CW) startAngleDeg + sweepAngleDeg * fraction
        else startAngleDeg - sweepAngleDeg * fraction

    override fun onDraw(c: Canvas) {
        if (radius <= 0) return
        val a0 = angleFor(0f)
        val aSweepSigned = if (direction == Direction.CW) sweepAngleDeg else -sweepAngleDeg

        c.drawArc(oval, a0, aSweepSigned, false, ringPaint)

        if (redlineFrom < maxValue) {
            val f = (redlineFrom - minValue) / (maxValue - minValue)
            c.drawArc(oval, angleFor(f), aSweepSigned * (1f - f), false, redlinePaint)
        }

        if (value != null && shown > minValue) {
            val frac = (shown - minValue) / (maxValue - minValue)
            c.drawArc(oval, a0, aSweepSigned * frac, false, progressPaint)
        }

        val totalSteps = ((maxValue - minValue) / majorStep).toInt()
        val microSteps = totalSteps * minorPerMajor
        for (i in 0..microSteps) {
            val isMajor = i % minorPerMajor == 0
            val frac = i.toFloat() / microSteps
            val ang = Math.toRadians(angleFor(frac).toDouble())
            val outer = radius * 0.99f
            val inner = if (isMajor) radius * 0.84f else radius * 0.92f
            c.drawLine(
                cx + (outer * cos(ang)).toFloat(), cy + (outer * sin(ang)).toFloat(),
                cx + (inner * cos(ang)).toFloat(), cy + (inner * sin(ang)).toFloat(),
                if (isMajor) tickMajor else tickMinor
            )
            if (isMajor) {
                val labelR = radius * 0.70f
                val v = minValue + (maxValue - minValue) * frac
                c.drawText(
                    numberFormat(v),
                    cx + (labelR * cos(ang)).toFloat(),
                    cy + (labelR * sin(ang)).toFloat() + numberPaint.textSize * 0.35f,
                    numberPaint
                )
            }
        }
    }

    override fun onDetachedFromWindow() { anim.cancel(); super.onDetachedFromWindow() }
}
