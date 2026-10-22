package com.mrt.jarvis.music

import android.content.Context
import android.util.Log
import org.json.JSONArray
import java.io.File

object MusicCache {
    private const val FILE_NAME = "music_library.json"

    fun saveTracks(context: Context, tracks: List<MusicTrack>) {
        try {
            val jsonArray = JSONArray()
            tracks.forEach { jsonArray.put(it.toJson()) }
            val file = File(context.filesDir, FILE_NAME)
            file.writeText(jsonArray.toString())
        } catch (e: Exception) {
            Log.e("MusicCache", "Error saving tracks", e)
        }
    }

    fun loadTracks(context: Context): List<MusicTrack> {
        val tracks = mutableListOf<MusicTrack>()
        try {
            val file = File(context.filesDir, FILE_NAME)
            if (!file.exists()) return tracks
            val jsonArray = JSONArray(file.readText())
            for (i in 0 until jsonArray.length()) {
                tracks.add(MusicTrack.fromJson(jsonArray.getJSONObject(i)))
            }
        } catch (e: Exception) {
            Log.e("MusicCache", "Error loading tracks", e)
        }
        return tracks
    }
}
