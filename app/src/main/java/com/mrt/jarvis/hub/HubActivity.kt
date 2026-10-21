package com.mrt.jarvis.hub

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.mrt.jarvis.MainActivity
import com.mrt.jarvis.R
import com.mrt.jarvis.apps.AppsGridActivity
import com.mrt.jarvis.gps.GpsActivity
import com.mrt.jarvis.music.MusicActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HubActivity : AppCompatActivity() {

    private lateinit var tvTime: TextView
    private val h = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hub)

        tvTime = findViewById(R.id.tvTime)

        // اتصال کارت‌های جدید BMW
        findViewById<android.view.View>(R.id.cardMedia).setOnClickListener { 
            startActivity(Intent(this, MusicActivity::class.java)) 
        }
        findViewById<android.view.View>(R.id.cardNav).setOnClickListener { 
            startActivity(Intent(this, GpsActivity::class.java)) 
        }
        findViewById<android.view.View>(R.id.cardVehicle).setOnClickListener { 
            startActivity(Intent(this, MainActivity::class.java)) 
        }
        findViewById<android.view.View>(R.id.cardComm).setOnClickListener { 
            try { startActivity(Intent(Intent.ACTION_DIAL)) } catch (e: Exception) {} 
        }
        findViewById<android.view.View>(R.id.cardNotif).setOnClickListener { 
            startActivity(Intent(this, AppsGridActivity::class.java)) 
        }

        updateClock()
        h.postDelayed(object : Runnable {
            override fun run() { updateClock(); h.postDelayed(this, 10000) }
        }, 10000)
    }

    private fun updateClock() {
        tvTime.text = SimpleDateFormat("HH:mm", Locale.US).format(Date())
    }
}
