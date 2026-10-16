package com.mrt.jarvis.vehicle

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.mrt.jarvis.can.CanManager
import com.mrt.jarvis.can.CanStats

/** Single source of truth. UI never talks to CAN directly. */
object VehicleDataRepository {
    private val _data = MutableLiveData(VehicleData())
    private val _can = MutableLiveData(CanStats())
    val data: LiveData<VehicleData> = _data
    val can: LiveData<CanStats> = _can
    private var manager: CanManager? = null
    private var refs = 0

    fun current(): VehicleData = _data.value ?: VehicleData()
    fun canStats(): CanStats = _can.value ?: CanStats()

    fun start(ctx: Context) {
        if (refs++ > 0) return
        manager = CanManager(ctx.applicationContext,
            { upd -> _data.postValue(current().upd()) }, { _can.postValue(it) }).also { it.start() }
    }
    fun stop() { if (--refs > 0) return; manager?.stop(); manager = null; _data.postValue(VehicleData()) }
    fun restart(ctx: Context) { refs = 1; stop(); start(ctx) }
}
