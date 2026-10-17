package com.mrt.jarvis.dashboard

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.cos
import kotlin.math.sin

class CornerGaugeView @JvmOverloads constructor(
    c: Context, a: AttributeSet? = null
) : View(c, a) {

    enum class Side { LEFT, RIGHT }
    var side: Side = Side.LEFT
    var startLabel = "E"
    var endLabel = "F"
    var accentColor = 0xFFF5C542.toInt()

    private var value: Float? = null
    private var shown = 0f
    private val anim = ValueAnimator().apply {
        duration = 300; interpolator = DecelerateInterpolator()
        addUpdateListener { shown = it.animatedValue as Float; invalidate() }
    }
    private val d = resources.displayMetrics.density
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = 0x22FFFFFF
    }
    private val prog = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = accentColor
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFEFF3F8.toInt(); textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
    }
    private val oval = RectF()

    fun setValue(v: Float?) {
        value = v
        if (v == null) { anim.cancel(); shown = 0f; invalidate(); return }
        anim.cancel(); anim.setFloatValues(shown, v.coerceIn(0f, 100f)); anim.start()
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val r = minOf(w, h) * 0.9f
        val cx = if (side == Side.LEFT) w * 0.05f else w * 0.95f
        val cy = h * 0.15f
        oval.set(cx - r, cy - r, cx + r, cy + r)
        val sw = h * 0.16f
        track.strokeWidth = sw; prog.strokeWidth = sw
        labelPaint.textSize = h * 0.18f
    }

    override fun onDraw(c: Canvas) {
        val aStart = 90f
        val aSweep = if (side == Side.LEFT) -80f else 80f
        c.drawArc(oval, aStart, aSweep, false, track)
        if (value != null) {
            val frac = shown / 100f
            c.drawArc(oval, aStart, aSweep * frac, false, prog)
        }
        val rOuter = oval.width() / 2f + labelPaint.textSize
        val ang0 = Math.toRadians(aStart.toDouble())
        val ang1 = Math.toRadians((aStart + aSweep).toDouble())
        val cx = oval.centerX(); val cy = oval.centerY()
        val pad = labelPaint.textSize * 0.7f
        c.drawText(startLabel, cx + (rOuter * cos(ang0)).toFloat() - pad, cy + (rOuter * sin(ang0)).toFloat() + pad, labelPaint)
        c.drawText(endLabel, cx + (rOuter * cos(ang1)).toFloat() - pad, cy + (rOuter * sin(ang1)).toFloat() - pad, labelPaint)
    }
    override fun onDetachedFromWindow() { anim.cancel(); super.onDetachedFromWindow() }
}
