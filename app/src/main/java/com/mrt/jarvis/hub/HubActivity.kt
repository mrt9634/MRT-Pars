package com.mrt.jarvis.hub

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.mrt.jarvis.MainActivity
import com.mrt.jarvis.R
import com.mrt.jarvis.apps.AppsGridActivity
import com.mrt.jarvis.gps.GpsActivity
import com.mrt.jarvis.music.MusicActivity
import com.mrt.jarvis.settings.SettingsActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HubActivity : AppCompatActivity() {

    private lateinit var cardsContainer: LinearLayout
    private lateinit var tvClock: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvWarn: TextView

    private val h = Handler(Looper.getMainLooper())
    private var selectedIndex = 0

    private val cardViews = mutableListOf<View>()
    private val cards = mutableListOf<HubCard>()

    private val clockTick = object : Runnable {
        override fun run() {
            tvClock.text = SimpleDateFormat("HH:mm", Locale.US).format(Date())
            h.postDelayed(this, 10_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hub)

        cardsContainer = findViewById(R.id.hubCards)
        tvClock = findViewById(R.id.tvHubClock)
        tvStatus = findViewById(R.id.tvHubStatus)
        tvWarn = findViewById(R.id.tvHubWarn)

        findViewById<View>(R.id.btnHome).setOnClickListener {
            selectCard(0, open = false)
        }
        findViewById<View>(R.id.btnGrid).setOnClickListener {
            startActivity(Intent(this, SettingsHubActivity::class.java))
        }

        buildCards()
        renderCards()
        selectCard(0, open = false)
        h.post(clockTick)
    }

    private fun buildCards() {
        cards.clear()
        cards += HubCard("media", android.R.drawable.ic_media_play,
            R.string.hub_media, HubAction.OpenMusic)
        cards += HubCard("comm", android.R.drawable.ic_menu_call,
            R.string.hub_comm, HubAction.OpenCommunication)
        cards += HubCard("nav", android.R.drawable.ic_menu_mylocation,
            R.string.hub_nav, HubAction.OpenNavigation)
        cards += HubCard("connected", android.R.drawable.ic_menu_compass,
            R.string.hub_connected, HubAction.OpenConnected)
        cards += HubCard("vehicle", android.R.drawable.ic_menu_info_details,
            R.string.hub_vehicle, HubAction.OpenDashboard)
        // کارت آخر (اعلان‌ها) به برنامه‌ها تغییر یافت
        cards += HubCard("apps", android.R.drawable.ic_menu_sort_by_size,
            R.string.hub_apps, HubAction.OpenApps)
    }

    private fun renderCards() {
        cardsContainer.removeAllViews()
        cardViews.clear()

        val inflater = LayoutInflater.from(this)
        val marginPx = dp(6)

        cards.forEachIndexed { index, card ->
            val view = inflater.inflate(R.layout.item_hub_card, cardsContainer, false)

            view.findViewById<ImageView>(R.id.ivCardIcon).setImageResource(card.iconRes)
            view.findViewById<TextView>(R.id.tvCardLabel).setText(card.labelRes)

            val lp = LinearLayout.LayoutParams(0, dp(160), 1f).apply {
                setMargins(marginPx, 0, marginPx, 0)
            }
            view.layoutParams = lp

            view.setOnClickListener { selectCard(index, open = true) }
            view.isFocusable = true
            view.isFocusableInTouchMode = true

            cardsContainer.addView(view)
            cardViews += view
        }
    }

    private fun selectCard(index: Int, open: Boolean) {
        if (index !in cards.indices) return
        selectedIndex = index

        cardViews.forEachIndexed { i, v ->
            v.setBackgroundResource(
                if (i == selectedIndex) R.drawable.card_selected
                else R.drawable.card_normal
            )
            v.scaleX = if (i == selectedIndex) 1.04f else 1f
            v.scaleY = if (i == selectedIndex) 1.04f else 1f
        }

        if (open) openCard(cards[selectedIndex])
    }

    private fun openCard(card: HubCard) {
        when (card.action) {
            HubAction.OpenMusic -> {
                startActivity(Intent(this, MusicActivity::class.java))
            }
            HubAction.OpenNavigation -> {
                startActivity(Intent(this, GpsActivity::class.java))
            }
            HubAction.OpenDashboard ->
                startActivity(Intent(this, MainActivity::class.java))
            HubAction.OpenSettings ->
                startActivity(Intent(this, SettingsActivity::class.java))
            HubAction.OpenCommunication -> {
                try {
                    startActivity(Intent(Intent.ACTION_DIAL))
                } catch (e: Exception) {
                    Toast.makeText(this, "برنامه تماس یافت نشد", Toast.LENGTH_SHORT).show()
                }
            }
            HubAction.OpenApps -> {
                startActivity(Intent(this, AppsGridActivity::class.java))
            }
            HubAction.OpenConnected -> {
                startActivity(Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS))
            }
            HubAction.OpenNotifications -> placeholder(card) // <-- این خط مشکل when رو حل می‌کنه
            HubAction.Placeholder -> placeholder(card)
        }
    }

    private fun placeholder(card: HubCard) {
        Toast.makeText(
            this,
            getString(card.labelRes) + " – " + getString(R.string.hub_placeholder),
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                selectCard((selectedIndex - 1).coerceAtLeast(0), open = false)
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                selectCard((selectedIndex + 1).coerceAtMost(cards.size - 1), open = false)
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                selectCard(selectedIndex, open = true)
                true
            }
            KeyEvent.KEYCODE_BACK -> true
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onResume() {
        super.onResume()
        immersive()
        h.post(clockTick)
        updateStatus()
        selectCard(selectedIndex, open = false)
    }

    override fun onPause() {
        h.removeCallbacks(clockTick)
        super.onPause()
    }

    override fun onDestroy() {
        h.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) immersive()
    }

    @Suppress("DEPRECATION")
    private fun immersive() {
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun updateStatus() {
        tvStatus.text = ""
        tvWarn.visibility = View.GONE
    }

    private fun dp(v: Int): Int =
        (v * resources.displayMetrics.density).toInt()
}
