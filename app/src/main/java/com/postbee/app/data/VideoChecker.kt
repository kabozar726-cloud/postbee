package com.postbee.app.data

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.postbee.app.models.VideoInfo

/**
 * Video ko upload se PEHLE check karta hai — taake ghalat video par
 * waqt zaya na ho aur user ko pehle se pata chal jaye ke kya hoga.
 * Saara kaam IO thread par hota hai (ViewModel se call hota hai).
 */
object VideoChecker {

    fun probe(context: Context, uri: Uri): VideoInfo? {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val duration = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: 0L
            val width = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
            )?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
            )?.toIntOrNull() ?: 0
            retriever.release()

            var size = 0L
            context.contentResolver.openFileDescriptor(uri, "r")?.use {
                size = it.statSize
            }
            val mime = context.contentResolver.getType(uri) ?: "video/mp4"
            VideoInfo(uri.toString(), duration, width, height, size, mime)
        } catch (e: Exception) {
            null
        }
    }

    /** Roman Urdu warnings — khaali list matlab video teeno ke liye theek hai. */
    fun check(video: VideoInfo): List<String> {
        val list = mutableListOf<String>()
        if (video.durationMs > 90_000L) {
            list.add(
                "Instagram Reel sirf 90 second tak ki hoti hai — ye video " +
                    "${video.durationMs / 1000} second ki hai, is liye Instagram par " +
                    "feed video banegi. Chhoti karein to Reel banegi."
            )
        }
        if (video.durationMs > 600_000L) {
            list.add("TikTok par 10 minute se lambi video upload nahi hoti.")
        }
        if (video.durationMs in 1L..2_999L) {
            list.add("Video 3 second se chhoti hai — Reels ke liye kam se kam 3 second chahiye.")
        }
        if (video.sizeBytes > 300L * 1024L * 1024L) {
            list.add("Video 300MB se bari hai — Instagram isay accept nahi karega.")
        }
        if (video.width > 0 && video.height > 0) {
            val ratio = video.width.toFloat() / video.height.toFloat()
            if (ratio < 0.5f || ratio > 0.6f) {
                list.add(
                    "Video 9:16 vertical nahi hai — TikTok aur Instagram par " +
                        "oopar-neeche kaat kar dikhayi jayegi."
                )
            }
        }
        return list
    }

    /** Video ka pehla frame — Upload screen par preview ke liye. */
    fun thumbnailFrame(context: Context, uri: Uri): Bitmap? {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val frame = retriever.getFrameAtTime(
                1_000_000L,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            )
            retriever.release()
            frame
        } catch (e: Exception) {
            null
        }
    }
}
