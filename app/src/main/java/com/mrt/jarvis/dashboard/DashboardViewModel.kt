package com.mrt.jarvis.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.mrt.jarvis.vehicle.VehicleDataRepository

class DashboardViewModel(app: Application) : AndroidViewModel(app) {
    val vehicle = VehicleDataRepository.data
    val can = VehicleDataRepository.can
    init { VehicleDataRepository.start(app) }
    override fun onCleared() { VehicleDataRepository.stop() }
}
