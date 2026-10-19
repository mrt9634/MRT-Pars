package com.mrt.jarvis.dashboard

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class AnalogClockView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val d = resources.displayMetrics.density
    
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF141A24.toInt()
        style = Paint.Style.FILL
        setShadowLayer(15f * d, 0f, 5f * d, 0xAA000000.toInt())
    }
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * d
    }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF8A96A8.toInt()
        strokeWidth = 2f * d
        strokeCap = Paint.Cap.ROUND
    }
    private val hourPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFEFF3F8.toInt()
        strokeWidth = 5f * d
        strokeCap = Paint.Cap.ROUND
        setShadowLayer(4f * d, 0f, 2f * d, 0xAA000000.toInt())
    }
    private val minPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF3FA9F5.toInt()
        strokeWidth = 3.5f * d
        strokeCap = Paint.Cap.ROUND
        setShadowLayer(4f * d, 0f, 2f * d, 0xAA000000.toInt())
    }
    private val secPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE5484D.toInt()
        strokeWidth = 1.5f * d
        strokeCap = Paint.Cap.ROUND
    }
    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFEFF3F8.toInt()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(cx, cy) - 10f * d

        canvas.drawCircle(cx, cy, radius, bgPaint)
        
        rimPaint.shader = SweepGradient(cx, cy, intArrayOf(
            0xFF8A96A8.toInt(), 0xFFEFF3F8.toInt(), 0xFF5A6672.toInt(), 
            0xFFEFF3F8.toInt(), 0xFF8A96A8.toInt()
        ), null)
        canvas.drawCircle(cx, cy, radius, rimPaint)

        for (i in 0 until 12) {
            val angle = Math.toRadians((i * 30).toDouble())
            val isMain = i % 3 == 0
            val startR = radius * (if (isMain) 0.82f else 0.88f)
            val endR = radius * 0.95f
            tickPaint.strokeWidth = (if (isMain) 3f else 1.5f) * d
            
            canvas.drawLine(
                cx + (startR * sin(angle)).toFloat(), cy - (startR * cos(angle)).toFloat(),
                cx + (endR * sin(angle)).toFloat(), cy - (endR * cos(angle)).toFloat(),
                tickPaint
            )
        }

        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR).toFloat()
        val minute = cal.get(Calendar.MINUTE).toFloat()
        val second = cal.get(Calendar.SECOND).toFloat()

        val hourAngle = Math.toRadians(((hour + minute / 60f) * 30).toDouble())
        val minAngle = Math.toRadians((minute * 6).toDouble())
        val secAngle = Math.toRadians((second * 6).toDouble())

        canvas.drawLine(cx, cy, cx + (radius * 0.5f * sin(hourAngle)).toFloat(), cy - (radius * 0.5f * cos(hourAngle)).toFloat(), hourPaint)
        canvas.drawLine(cx, cy, cx + (radius * 0.75f * sin(minAngle)).toFloat(), cy - (radius * 0.75f * cos(minAngle)).toFloat(), minPaint)
        canvas.drawLine(cx, cy, cx + (radius * 0.85f * sin(secAngle)).toFloat(), cy - (radius * 0.85f * cos(secAngle)).toFloat(), secPaint)

        canvas.drawCircle(cx, cy, 6f * d, centerPaint)
        centerPaint.color = 0xFFE5484D.toInt()
        canvas.drawCircle(cx, cy, 3f * d, centerPaint)

        postInvalidateDelayed(1000)
    }
}
