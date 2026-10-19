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
import androidx.appcompat.app.AppCompatActivity
import com.mrt.jarvis.MainActivity
import com.mrt.jarvis.R
import com.mrt.jarvis.diagnostics.DiagnosticsActivity
import com.mrt.jarvis.gps.GpsActivity
import com.mrt.jarvis.music.MusicActivity
import com.mrt.jarvis.settings.SettingsActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsHubActivity : AppCompatActivity() {

    private lateinit var cardsContainer: LinearLayout
    private lateinit var tvClock: TextView
    private val h = Handler(Looper.getMainLooper())
    private var selectedIndex = 0

    private val cardViews = mutableListOf<View>()
    private val cards = mutableListOf<HubCard>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings_hub)

        cardsContainer = findViewById(R.id.settingsCards)
        tvClock = findViewById(R.id.tvSettingsClock)

        findViewById<View>(R.id.btnHomeSettings).setOnClickListener { finish() }

        buildCards()
        renderCards()
        selectCard(0, open = false)

        tvClock.text = SimpleDateFormat("HH:mm", Locale.US).format(Date())
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
        cards += HubCard("notif", android.R.drawable.ic_dialog_info,
            R.string.hub_notifications, HubAction.OpenNotifications)
    }

    private fun renderCards() {
        cardsContainer.removeAllViews()
        cardViews.clear()

        val inflater = LayoutInflater.from(this)
        val marginPx = (6 * resources.displayMetrics.density).toInt()

        cards.forEachIndexed { index, card ->
            val view = inflater.inflate(R.layout.item_hub_card, cardsContainer, false)
            view.findViewById<ImageView>(R.id.ivCardIcon).setImageResource(card.iconRes)
            view.findViewById<TextView>(R.id.tvCardLabel).setText(card.labelRes)

            val lp = LinearLayout.LayoutParams(0, (140 * resources.displayMetrics.density).toInt(), 1f).apply {
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
            v.scaleX = if (i == selectedIndex) 1.05f else 1f
            v.scaleY = if (i == selectedIndex) 1.05f else 1f
        }

        if (open) openCard(cards[selectedIndex])
    }

    private fun openCard(card: HubCard) {
        when (card.action) {
            HubAction.OpenMusic -> startActivity(Intent(this, MusicActivity::class.java))
            HubAction.OpenNavigation -> startActivity(Intent(this, GpsActivity::class.java))
            HubAction.OpenDashboard -> startActivity(Intent(this, MainActivity::class.java))
            HubAction.OpenSettings -> startActivity(Intent(this, SettingsActivity::class.java))
            HubAction.OpenCommunication -> {
                startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS))
            }
            HubAction.OpenConnected -> {
                startActivity(Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS))
            }
            HubAction.OpenNotifications -> {
                startActivity(Intent(this, DiagnosticsActivity::class.java))
            }
            else -> {}
        }
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
            KeyEvent.KEYCODE_BACK -> {
                finish()
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }
}
