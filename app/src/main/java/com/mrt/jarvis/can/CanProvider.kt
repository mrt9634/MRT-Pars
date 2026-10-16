package com.mrt.jarvis.can

import com.mrt.jarvis.vehicle.VehicleData

interface CanListener {
    fun onFrame(msg: CanMessage)
    fun onVehicle(update: (VehicleData) -> VehicleData)
    fun onState(state: CanState, reason: String? = null)
}

interface CanProvider {
    val name: String
    val protocol: String
    fun start(listener: CanListener)
    fun stop()
}
