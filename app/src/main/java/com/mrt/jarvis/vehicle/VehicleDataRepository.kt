package com.mrt.jarvis.vehicle

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.mrt.jarvis.can.CanManager
import com.mrt.jarvis.can.CanStats

/** Single source of truth. UI never talks to CAN or GPS directly. */
object VehicleDataRepository {
    private val _data = MutableLiveData(VehicleData())
    private val _can = MutableLiveData(CanStats())
    val data: LiveData<VehicleData> = _data
    val can: LiveData<CanStats> = _can
    
    private var manager: CanManager? = null
    private var gpsProvider: GpsSpeedProvider? = null
    private var refs = 0
    private var isCanConnected = false

    fun current(): VehicleData = _data.value ?: VehicleData()
    fun canStats(): CanStats = _can.value ?: CanStats()

    fun start(ctx: Context) {
        if (refs++ > 0) return
        
        // 1. راه‌اندازی CAN (وقتی کانکت شد، اولویت می‌گیره)
        manager = CanManager(ctx.applicationContext,
            { upd -> _data.postValue(current().upd()) },
            { stats -> 
                _can.postValue(stats) 
                isCanConnected = (stats.state == com.mrt.jarvis.can.CanState.CONNECTED)
            }).also { it.start() }

        // 2. راه‌اندازی GPS (تا وقتی CAN وصل نشده، سرعت رو از اینجا می‌گیریم)
        gpsProvider = GpsSpeedProvider(ctx) { speed ->
            if (!isCanConnected) {
                // فقط وقتی CAN قطع است سرعت GPS را اعمال کن
                _data.postValue(current().copy(speedKmh = speed))
            }
        }.also { it.start() }
    }

    fun stop() { 
        if (--refs > 0) return
        manager?.stop()
        manager = null
        gpsProvider?.stop()
        gpsProvider = null
        isCanConnected = false
        _data.postValue(VehicleData()) 
    }
    
    fun restart(ctx: Context) { refs = 1; stop(); start(ctx) }
}
