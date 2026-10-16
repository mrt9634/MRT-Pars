package com.mrt.jarvis.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mrt.jarvis.hub.HubActivity
import com.mrt.jarvis.settings.AppPrefs

/**
 * Android 10 blocks background Activity starts for targetSdk>=29 unless the app is the
 * HOME app or holds "display over other apps". Best option on a head unit: set MRT1 as the
 * default Home/Launcher (manifest declares HOME), which also guarantees start after boot.
 *
 * We boot directly into HubActivity (not Splash) so the driver gets an immediate, usable screen.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        if (!AppPrefs(ctx).startOnBoot) return
        runCatching {
            ctx.startActivity(
                Intent(ctx, HubActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
