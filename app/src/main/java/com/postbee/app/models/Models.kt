package com.postbee.app.models

/** Teen platforms — aik video, teen jagah. */
enum class Platform {
    TIKTOK, INSTAGRAM, FACEBOOK
}

fun Platform.label(): String = when (this) {
    Platform.TIKTOK -> "TikTok"
    Platform.INSTAGRAM -> "Instagram"
    Platform.FACEBOOK -> "Facebook"
}

/** Aik platform ka login haal. */
data class AccountInfo(
    val platform: Platform,
    val displayName: String,
    val connected: Boolean,
    val connectedAt: Long = 0L
)

/** Har platform par upload ki haalat. */
enum class UploadStatus {
    IDLE, CHECKING, UPLOADING, PROCESSING, DONE, FAILED
}

fun UploadStatus.label(): String = when (this) {
    UploadStatus.IDLE -> "Tayyar"
    UploadStatus.CHECKING -> "Check ho raha hai"
    UploadStatus.UPLOADING -> "Upload ho raha hai"
    UploadStatus.PROCESSING -> "Tayyar ho raha hai"
    UploadStatus.DONE -> "Ho gaya"
    UploadStatus.FAILED -> "Fail"
}

/** Aik platform ka upload nateeja. */
data class PlatformResult(
    val platform: Platform,
    val status: UploadStatus,
    val progress: Float = 0f,
    val message: String = "",
    val liveUrl: String? = null
)

/** Aik poori upload (saare platforms) ka record — History mein dikhta hai. */
data class UploadRecord(
    val id: String,
    val title: String,
    val createdAt: Long,
    val results: List<PlatformResult>
)

/** Chuni hui video ki maloomat — VideoChecker se milti hai. */
data class VideoInfo(
    val uriString: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val mimeType: String
)
