package com.mrt.jarvis.gps

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.mrt.jarvis.R
import com.mrt.jarvis.dashboard.SpeedLimitView
import com.mrt.jarvis.settings.AppPrefs
import com.mrt.jarvis.vehicle.VehicleDataRepository
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GpsActivity : AppCompatActivity() {

    private lateinit var tvSpeed: TextView
    private lateinit var tvTime: TextView
    private lateinit var tvRange: TextView
    private lateinit var tvTemp: TextView
    private lateinit var tvGear: TextView
    private lateinit var limitSign: SpeedLimitView
    
    private val nf = NumberFormat.getInstance(Locale.US).apply { maximumFractionDigits = 0 }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gps)

        tvSpeed = findViewById(R.id.tvGpsSpeed)
        tvTime = findViewById(R.id.tvTimeGps)
        tvRange = findViewById(R.id.tvGpsRange)
        tvTemp = findViewById(R.id.tvGpsTemp)
        tvGear = findViewById(R.id.tvGpsGear)
        limitSign = findViewById(R.id.gpsLimitSign)

        findViewById<android.view.View>(R.id.btnBackGps).setOnClickListener { finish() }
        
        // کلیک روی نقشه = باز کردن اپ ناوبری
        findViewById<android.view.View>(R.id.mapContainer).setOnClickListener {
            openMap()
        }

        // به‌روزرسانی ساعت
        tvTime.text = SimpleDateFormat("HH:mm", Locale.US).format(Date())

        // اتصال به دیتای خودرو
        VehicleDataRepository.data.observe(this) { v ->
            val mph = AppPrefs(this).speedUnit == "mph"
            val speed = v.speedKmh?.let { if (mph) it * 0.621f else it }
            
            tvSpeed.text = speed?.let { nf.format(it) } ?: "0"
            tvGear.text = v.gear ?: "P"
            
            v.rangeKm?.let { tvRange.text = "${nf.format(it)} km" }
            v.engineTempC?.let { tvTemp.text = "${nf.format(it)} °C" }
            
            limitSign.limit = v.speedLimit
        }
    }

    private fun openMap() {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q="))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, R.string.gps_no_app, Toast.LENGTH_SHORT).show()
        }
    }
}
