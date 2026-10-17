package com.mrt.jarvis.music

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.mrt.jarvis.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MusicActivity : AppCompatActivity() {

    private lateinit var player: MusicPlayer
    private var playlist = listOf<MusicTrack>()
    
    private lateinit var tvEmpty: TextView
    private lateinit var tvTrackTitle: TextView
    private lateinit var tvArtist: TextView
    private lateinit var tvAlbum: TextView
    
    private lateinit var tvCurrentTime: TextView
    private lateinit var tvTotalTime: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var btnPlayPause: ImageButton
    
    private var isUserSeeking = false
    private val h = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_music)
        
        player = MusicPlayer()
        initViews()
        setupPlayerCallbacks()
        checkPermissionAndScan()
    }

    private fun initViews() {
        tvEmpty = findViewById(R.id.tvEmptyState)
        tvTrackTitle = findViewById(R.id.tvTrackTitle)
        tvArtist = findViewById(R.id.tvArtist)
        tvAlbum = findViewById(R.id.tvAlbum)
        
        tvCurrentTime = findViewById(R.id.tvCurrentTime)
        tvTotalTime = findViewById(R.id.tvTotalTime)
        seekBar = findViewById(R.id.seekBar)
        btnPlayPause = findViewById(R.id.btnPlayPause)

        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        val btnPrev = findViewById<ImageButton>(R.id.btnPrev)
        val btnNext = findViewById<ImageButton>(R.id.btnNext)
        val tvTopTime = findViewById<TextView>(R.id.tvTopTime)

        btnBack.setOnClickListener { finish() }
        btnPlayPause.setOnClickListener { player.togglePlayPause() }
        btnNext.setOnClickListener { player.next() }
        btnPrev.setOnClickListener { player.prev() }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    tvCurrentTime.text = formatTime(progress.toLong())
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) { isUserSeeking = true }
            override fun onStopTrackingTouch(sb: SeekBar?) {
                isUserSeeking = false
                player.seekTo(sb?.progress ?: 0)
            }
        })
        
        // ساعت بالا
        val tf = SimpleDateFormat("HH:mm", Locale.US)
        tvTopTime.text = tf.format(Date())
    }

    private fun setupPlayerCallbacks() {
        player.onTrackChanged = { track ->
            tvEmpty.visibility = View.GONE
            tvTrackTitle.text = track.title
            tvArtist.text = track.artist
            tvAlbum.text = track.album
        }

        player.onPlayStateChanged = { isPlaying ->
            btnPlayPause.setImageResource(
                if (isPlaying) android.R.drawable.ic_media_pause 
                else android.R.drawable.ic_media_play
            )
        }

        player.onProgress = { current, total ->
            if (!isUserSeeking) {
                seekBar.max = total
                seekBar.progress = current
                tvCurrentTime.text = formatTime(current.toLong())
                tvTotalTime.text = formatTime(total.toLong())
            }
        }
    }

    private fun checkPermissionAndScan() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 100)
        } else {
            scanMusic()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            scanMusic()
        } else {
            Toast.makeText(this, R.string.music_permission_needed, Toast.LENGTH_LONG).show()
            tvEmpty.text = getString(R.string.music_permission_needed)
        }
    }

    private fun scanMusic() {
        tvEmpty.visibility = View.VISIBLE
        tvEmpty.text = getString(R.string.music_loading)
        
        CoroutineScope(Dispatchers.Main).launch {
            val tracks = withContext(Dispatchers.IO) {
                MusicScanner.scanUsbDrives(this@MusicActivity)
            }
            
            playlist = tracks
            if (tracks.isEmpty()) {
                tvEmpty.text = getString(R.string.music_no_tracks)
            } else {
                player.setPlaylist(tracks)
                player.play(0)
            }
        }
    }

    private fun formatTime(ms: Long): String {
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return String.format(Locale.US, "%02d:%02d", min, sec)
    }

    override fun onDestroy() {
        player.release()
        h.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
