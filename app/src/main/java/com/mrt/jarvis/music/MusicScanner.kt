package com.mrt.jarvis.music

import android.content.Context
import android.media.MediaMetadataRetriever
import java.io.File

object MusicScanner {

    private val audioExtensions = listOf("mp3", "m4a", "wav", "flac", "ogg", "aac")

    fun scanUsbDrives(ctx: Context): List<MusicTrack> {
        val tracks = mutableListOf<MusicTrack>()
        val possibleRoots = mutableListOf<File>()

        // مسیرهای رایج فلش
        possibleRoots.add(File("/storage"))
        possibleRoots.add(File("/mnt/usb_storage"))
        possibleRoots.add(File("/mnt/media_rw"))
        
        // مسیر پوشه اپ (با چک کردن نال بودن)
        val extDir = ctx.getExternalFilesDir(null)
        if (extDir != null && extDir.parentFile != null) {
            possibleRoots.add(extDir.parentFile!!)
        }

        for (root in possibleRoots) {
            if (root.exists() && root.isDirectory) {
                root.walkTopDown().forEach { file ->
                    if (file.isFile && file.extension.lowercase() in audioExtensions) {
                        val track = extractMetadata(file)
                        if (track != null) {
                            tracks.add(track)
                        }
                    }
                }
            }
        }
        return tracks.distinctBy { it.file.absolutePath }.sortedBy { it.title }
    }

    private fun extractMetadata(file: File): MusicTrack? {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: file.nameWithoutExtension
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "Unknown Artist"
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: "Unknown Album"
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val duration = durationStr?.toLongOrNull() ?: 0L
            
            retriever.release()
            
            MusicTrack(file, title, artist, album, duration)
        } catch (e: Exception) {
            null
        }
    }
}
