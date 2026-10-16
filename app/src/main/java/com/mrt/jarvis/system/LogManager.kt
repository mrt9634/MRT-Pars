package com.mrt.jarvis.system

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * MRT1-owned log only: <app files>/logs/mrt1.log (rotated at 64 KB, one backup).
 * Never touches Android system logs, other apps, or files outside MRT1's private storage.
 */
object LogManager {
    private const val MAX = 64 * 1024
    private const val TAG = "MRT1"
    private fun dir(ctx: Context) = File(ctx.filesDir, "logs").apply { mkdirs() }
    fun file(ctx: Context) = File(dir(ctx), "mrt1.log")

    fun i(ctx: Context, msg: String) { Log.i(TAG, msg); write(ctx, "I", msg) }
    fun e(ctx: Context, msg: String, t: Throwable? = null) {
        Log.e(TAG, msg, t); write(ctx, "E", msg + (t?.let { "\n" + Log.getStackTraceString(it).take(4000) } ?: ""))
    }

    @Synchronized private fun write(ctx: Context, lvl: String, msg: String) {
        runCatching {
            val f = file(ctx)
            if (f.length() > MAX) { val old = File(dir(ctx), "mrt1.1.log"); old.delete(); f.renameTo(old) }
            file(ctx).appendText("${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())} $lvl $msg\n")
        }
    }

    fun read(ctx: Context, maxChars: Int = 12000): String = runCatching {
        val t = file(ctx).takeIf { it.exists() }?.readText() ?: ""
        if (t.length > maxChars) t.takeLast(maxChars) else t
    }.getOrDefault("")

    /** "Clear MRT1 Log": deletes only MRT1's own log files. */
    @Synchronized fun clear(ctx: Context) { dir(ctx).listFiles()?.forEach { it.delete() } }

    /** Removes only MRT1's own stale temp/cache files (older than [days]). */
    fun cleanOwnCache(ctx: Context, days: Int = 3) {
        runCatching {
            val limit = System.currentTimeMillis() - days * 86_400_000L
            ctx.cacheDir.walkBottomUp().filter { it != ctx.cacheDir && it.lastModified() <= limit }.forEach { it.delete() }
        }
    }
}
