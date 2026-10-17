package com.mrt.jarvis.dashboard

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class SpeedLimitView @JvmOverloads constructor(
    c: Context, a: AttributeSet? = null
) : View(c, a) {

    var limit: Int? = null
        set(v) { if (field != v) { field = v; visibility = if (v == null) GONE else VISIBLE; invalidate() } }

    private val d = resources.displayMetrics.density
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = 0xFFE5484D.toInt(); strokeWidth = 5 * d
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL; color = 0xFFFFFFFF.toInt()
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF000000.toInt(); textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        text.textSize = h * 0.55f
    }
    
    override fun onDraw(c: Canvas) {
        val r = minOf(width, height) / 2f - 4 * d
        val cx = width / 2f; val cy = height / 2f
        c.drawCircle(cx, cy, r, fill)
        c.drawCircle(cx, cy, r, ring)
        limit?.let { c.drawText(it.toString(), cx, cy + text.textSize * 0.35f, text) }
    }
}
