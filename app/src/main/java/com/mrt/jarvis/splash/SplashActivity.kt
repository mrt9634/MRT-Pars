package com.mrt.jarvis.splash

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.mrt.jarvis.BuildConfig
import com.mrt.jarvis.R
import com.mrt.jarvis.hub.HubActivity
import com.mrt.jarvis.settings.AppPrefs

/**
 * Splash / brand screen (FULL OPTION).
 * Shown on cold-start only, then routes to Hub. Duration is configurable in AppPrefs.
 * Can be skipped by touch.
 */
class SplashActivity : AppCompatActivity() {

    private val h = Handler(Looper.getMainLooper())
    private var navigated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val prefs = AppPrefs(this)

        // نسخه
        findViewById<TextView>(R.id.tvSplashVersion).text = "v${BuildConfig.VERSION_NAME}"

        // انیمیشن ورود (fade + scale)
        val content = findViewById<View>(R.id.splashContent)
        val fade = ObjectAnimator.ofFloat(content, View.ALPHA, 0f, 1f).apply {
            duration = 700
            interpolator = DecelerateInterpolator()
        }
        val scale = ObjectAnimator.ofFloat(content, View.SCALE_X, 0.85f, 1f).apply {
            duration = 700
            interpolator = DecelerateInterpolator()
        }
        val scaleY = ObjectAnimator.ofFloat(content, View.SCALE_Y, 0.85f, 1f).apply {
            duration = 700
            interpolator = DecelerateInterpolator()
        }
        AnimatorSet().apply {
            playTogether(fade, scale, scaleY)
            start()
        }

        // اگه Splash غیرفعاله، فوراً برو
        if (!prefs.splashEnabled) {
            go()
            return
        }

        // زمان‌بندی خروج
        h.postDelayed({ go() }, prefs.splashMs.toLong())

        // لمس = رد کردن Splash
        content.setOnClickListener { go() }
    }

    private fun go() {
        if (navigated) return
        navigated = true
        startActivity(Intent(this, HubActivity::class.java))
        finish()
    }

    override fun onDestroy() {
        h.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
