package com.mrt.jarvis.music

import java.io.File

data class MusicTrack(
    val file: File,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long
)
