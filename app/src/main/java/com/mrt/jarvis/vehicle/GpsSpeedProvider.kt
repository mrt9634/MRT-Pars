package com.mrt.jarvis.vehicle

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.core.content.ContextCompat

class GpsSpeedProvider(private val context: Context, private val onSpeedUpdate: (Float?) -> Unit) {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            // سرعت در اندروید به متر بر ثانیه است، ضرب در 3.6 می‌شود تا کیلومتر بر ساعت شود
            val speedKmh = if (location.hasSpeed()) location.speed * 3.6f else null
            onSpeedUpdate(speedKmh)
        }
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {
            onSpeedUpdate(null) // اگر GPS قطع شد، سرعت صفر یا -- شود
        }
    }

    fun start() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            // هر 1 ثانیه سرعت رو آپدیت کن
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000L,
                0f,
                locationListener
            )
        }
    }

    fun stop() {
        locationManager.removeUpdates(locationListener)
    }
}
