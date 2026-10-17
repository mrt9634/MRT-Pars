package com.mrt.jarvis.music

import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper

class MusicPlayer {

    private var mediaPlayer: MediaPlayer? = null
    private var playlist: List<MusicTrack> = emptyList()
    private var currentIndex = -1
    private var isPrepared = false

    private val handler = Handler(Looper.getMainLooper())
    private var progressRunnable: Runnable? = null

    var onTrackChanged: ((MusicTrack) -> Unit)? = null
    var onPlayStateChanged: ((Boolean) -> Unit)? = null
    var onProgress: ((Int, Int) -> Unit)? = null

    fun setPlaylist(tracks: List<MusicTrack>) {
        playlist = tracks
        currentIndex = -1
    }

    fun play(index: Int) {
        if (index !in playlist.indices) return
        currentIndex = index
        playTrack(playlist[index])
    }

    fun togglePlayPause() {
        if (mediaPlayer == null) return
        try {
            if (mediaPlayer!!.isPlaying) {
                mediaPlayer!!.pause()
                onPlayStateChanged?.invoke(false)
            } else {
                mediaPlayer!!.start()
                onPlayStateChanged?.invoke(true)
            }
        } catch (_: Exception) {}
    }

    fun next() {
        if (playlist.isEmpty()) return
        val nextIndex = if (currentIndex + 1 >= playlist.size) 0 else currentIndex + 1
        play(nextIndex)
    }

    fun prev() {
        if (playlist.isEmpty()) return
        val prevIndex = if (currentIndex - 1 < 0) playlist.size - 1 else currentIndex - 1
        play(prevIndex)
    }

    fun seekTo(ms: Int) {
        mediaPlayer?.seekTo(ms)
    }

    private fun playTrack(track: MusicTrack) {
        isPrepared = false
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(track.file.absolutePath)
                setOnPreparedListener {
                    isPrepared = true
                    it.start()
                    onPlayStateChanged?.invoke(true)
                    onTrackChanged?.invoke(track)
                    startProgressUpdates()
                }
                setOnCompletionListener { next() }
                prepareAsync()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startProgressUpdates() {
        stopProgressUpdates()
        progressRunnable = object : Runnable {
            override fun run() {
                if (isPrepared && mediaPlayer != null) {
                    try {
                        onProgress?.invoke(mediaPlayer!!.currentPosition, mediaPlayer!!.duration)
                        handler.postDelayed(this, 500)
                    } catch (_: Exception) {}
                }
            }
        }
        handler.post(progressRunnable!!)
    }

    private fun stopProgressUpdates() {
        progressRunnable?.let { handler.removeCallbacks(it) }
        progressRunnable = null
    }

    fun release() {
        stopProgressUpdates()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
