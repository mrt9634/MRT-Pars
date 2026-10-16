package com.mrt.jarvis.dashboard

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.mrt.jarvis.vehicle.VehicleData

/** Indicators light only when a real signal is TRUE. Unknown = very dim outline. */
class StatusIndicatorsView @JvmOverloads constructor(c: Context, a: AttributeSet? = null) : View(c, a) {
    private val items = listOf("DOOR", "TRUNK", "HOOD", "BELT", "LIGHT", "HIGH", "◀", "▶", "P", "BRAKE", "R", "ENG")
    private var states = arrayOfNulls<Boolean>(items.size)
    private val d = resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; textSize = 10 * d; isFakeBoldText = true }
    private val warn = setOf(0, 1, 2, 8, 9)

    fun bind(v: VehicleData) {
        val n = arrayOf(v.doorOpen, v.trunkOpen, v.hoodOpen, v.seatBelt?.not(), v.lights, v.highBeam,
            v.turnLeft, v.turnRight, v.parkingBrake, v.brake, v.reverse, v.engineRunning)
        if (!n.contentEquals(states)) { states = n; invalidate() }
    }
    override fun onDraw(c: Canvas) {
        val cols = 4; val rows = 3; val cw = width / cols.toFloat(); val rh = height / rows.toFloat()
        items.forEachIndexed { i, s ->
            p.color = when (states[i]) {
                true -> if (i in warn) 0xFFF5A524.toInt() else if (i == 5) 0xFF3FA9F5.toInt() else 0xFF3DD68C.toInt()
                false -> 0x55FFFFFF
                null -> 0x1FFFFFFF
            }
            c.drawText(s, cw * (i % cols) + cw / 2, rh * (i / cols) + rh / 2 + p.textSize / 3, p)
        }
    }
}
