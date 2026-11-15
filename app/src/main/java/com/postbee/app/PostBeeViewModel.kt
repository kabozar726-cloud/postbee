package com.postbee.app

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.postbee.app.data.HistoryStore
import com.postbee.app.data.TokenStore
import com.postbee.app.data.UploadEngine
import com.postbee.app.data.VideoChecker
import com.postbee.app.models.AccountInfo
import com.postbee.app.models.Platform
import com.postbee.app.models.PlatformResult
import com.postbee.app.models.UploadRecord
import com.postbee.app.models.UploadStatus
import com.postbee.app.models.VideoInfo
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * PostBeeViewModel — saara haal yahan hai.
 * Bhaari kaam (file copy, network, video probe) hamesha Dispatchers.IO par.
 */
class PostBeeViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication()
    private val tokenStore = TokenStore(context)
    private val historyStore = HistoryStore(context)

    private val _accounts = MutableStateFlow<List<AccountInfo>>(emptyList())
    val accounts: StateFlow<List<AccountInfo>> = _accounts.asStateFlow()

    private val _history = MutableStateFlow<List<UploadRecord>>(emptyList())
    val history: StateFlow<List<UploadRecord>> = _history.asStateFlow()

    private val _uploadProgress = MutableStateFlow<Map<Platform, PlatformResult>>(emptyMap())
    val uploadProgress: StateFlow<Map<Platform, PlatformResult>> = _uploadProgress.asStateFlow()

    private val _videoInfo = MutableStateFlow<VideoInfo?>(null)
    val videoInfo: StateFlow<VideoInfo?> = _videoInfo.asStateFlow()

    private val _warnings = MutableStateFlow<List<String>>(emptyList())
    val warnings: StateFlow<List<String>> = _warnings.asStateFlow()

    init {
        loadAccounts()
        _history.value = historyStore.listRecords()
    }

    fun loadAccounts() {
        val list = mutableListOf<AccountInfo>()
        val tiktokToken = tokenStore.getToken(Platform.TIKTOK)
        list.add(
            AccountInfo(
                platform = Platform.TIKTOK,
                displayName = tokenStore.getString("name_tiktok") ?: "TikTok",
                connected = !tiktokToken.isNullOrBlank()
            )
        )
        val pageToken = tokenStore.getString("meta_page_token")
        list.add(
            AccountInfo(
                platform = Platform.FACEBOOK,
                displayName = tokenStore.getString("meta_page_name") ?: "Facebook Page",
                connected = !pageToken.isNullOrBlank()
            )
        )
        val igId = tokenStore.getString("ig_user_id")
        list.add(
            AccountInfo(
                platform = Platform.INSTAGRAM,
                displayName = tokenStore.getString("name_instagram") ?: "Instagram",
                connected = !igId.isNullOrBlank()
            )
        )
        _accounts.value = list
    }

    /** Video chunein — probe + warnings IO thread par. */
    fun setVideo(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val info = VideoChecker.probe(context, uri)
            _videoInfo.value = info
            _warnings.value = info?.let { VideoChecker.check(it) }
                ?: listOf("Video parhi nahi ja saki — dobara chunein.")
        }
    }

    /** TikTok login URL — null matlab key abhi set nahi. */
    fun buildTikTokAuthUrl(): String? {
        if (ApiConfig.TIKTOK_CLIENT_KEY.isBlank()) return null
        val (url, verifier, state) = UploadEngine.buildTikTokAuthUrl()
        tokenStore.saveString("pkce_verifier", verifier)
        tokenStore.saveString("oauth_state", state)
        return url
    }

    /** Meta login URL (Facebook + Instagram dono) — null matlab App ID abhi set nahi. */
    fun buildMetaAuthUrl(): String? {
        if (ApiConfig.META_APP_ID.isBlank()) return null
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        val state = (1..16).map { chars.random() }.joinToString("")
        tokenStore.saveString("oauth_state", state)
        return UploadEngine.buildMetaAuthUrl(state)
    }

    /**
     * OAuth callback — MainActivity onCreate/onNewIntent se aata hai.
     * Redirect URIs: postbee://oauth/tiktok aur postbee://oauth/meta.
     */
    fun handleOAuthCallback(uri: Uri) {
        val code = uri.getQueryParameter("code") ?: return
        val state = uri.getQueryParameter("state")
        if (state != null && state != tokenStore.getString("oauth_state")) return
        when (uri.path) {
            "/tiktok" -> viewModelScope.launch(Dispatchers.IO) {
                val verifier = tokenStore.getString("pkce_verifier") ?: return@launch
                UploadEngine.exchangeTikTokCode(code, verifier).onSuccess { token ->
                    tokenStore.saveToken(Platform.TIKTOK, token, 86400L)
                    UploadEngine.fetchTikTokUser(token).onSuccess { name ->
                        tokenStore.saveString("name_tiktok", name)
                    }
                    loadAccounts()
                }
            }
            "/meta" -> viewModelScope.launch(Dispatchers.IO) {
                UploadEngine.exchangeMetaCode(code).onSuccess { shortToken ->
                    UploadEngine.getLongLivedMetaToken(shortToken).onSuccess { longToken ->
                        tokenStore.saveString("meta_user_token", longToken)
                        UploadEngine.fetchMetaPages(longToken).onSuccess { pages ->
                            val page = pages.firstOrNull()
                            if (page != null) {
                                tokenStore.saveString("meta_page_id", page.id)
                                tokenStore.saveString("meta_page_token", page.accessToken)
                                tokenStore.saveString("meta_page_name", page.name)
                                page.instagramBusinessId?.let { igId ->
                                    tokenStore.saveString("ig_user_id", igId)
                                    tokenStore.saveString("name_instagram", page.name)
                                }
                            }
                            loadAccounts()
                        }
                    }
                }
            }
        }
    }

    /**
     * Aik video — teeno platforms par.
     * Har platform alag try/catch mein hai: aik fail ho to baaki rukte nahi.
     */
    fun startUpload(
        title: String,
        description: String,
        tags: String,
        thumbnailUri: Uri?,
        selected: Set<Platform>
    ) {
        val video = _videoInfo.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _uploadProgress.value = selected.associateWith {
                PlatformResult(it, UploadStatus.CHECKING, 0f, "Tayyari...")
            }
            val videoFile = UploadEngine.copyUriToCache(
                context,
                Uri.parse(video.uriString),
                "upload_${System.currentTimeMillis()}.mp4"
            )
            val caption = buildString {
                append(title)
                if (description.isNotBlank()) append("\n").append(description)
                if (tags.isNotBlank()) append("\n").append(tags)
            }

            // Thumbnail (agar di ho aur server set ho) — Instagram cover ke liye.
            var coverUrl: String? = null
            if (thumbnailUri != null &&
                ApiConfig.SERVER_BASE_URL.isNotBlank() &&
                selected.contains(Platform.INSTAGRAM)
            ) {
                try {
                    val thumbFile = UploadEngine.copyUriToCache(
                        context,
                        thumbnailUri,
                        "thumb_${System.currentTimeMillis()}.jpg"
                    )
                    coverUrl = UploadEngine.uploadToServer(
                        thumbFile,
                        ApiConfig.SERVER_BASE_URL,
                        "image",
                        "image/jpeg"
                    ).getOrNull()
                } catch (e: Exception) {
                    coverUrl = null
                }
            }

            val results = mutableListOf<PlatformResult>()
            for (platform in selected) {
                val result = try {
                    updateProgress(platform, UploadStatus.UPLOADING, 0.3f, "Upload ho raha hai...")
                    when (platform) {
                        Platform.TIKTOK -> {
                            val token = tokenStore.getToken(Platform.TIKTOK)
                                ?: throw IllegalStateException(
                                    "TikTok connect nahi — pehle Accounts mein login karein."
                                )
                            UploadEngine.publishTikTokVideo(token, videoFile, caption).fold(
                                onSuccess = {
                                    PlatformResult(
                                        platform, UploadStatus.DONE, 1f,
                                        "TikTok par publish ho gaya."
                                    )
                                },
                                onFailure = { e ->
                                    PlatformResult(
                                        platform, UploadStatus.FAILED, 0f,
                                        e.message ?: "TikTok upload fail."
                                    )
                                }
                            )
                        }
                        Platform.INSTAGRAM -> {
                            val pageToken = tokenStore.getString("meta_page_token")
                                ?: throw IllegalStateException(
                                    "Instagram connect nahi — pehle Meta login karein."
                                )
                            val igId = tokenStore.getString("ig_user_id")
                                ?: throw IllegalStateException(
                                    "Instagram Business account nahi mila — account ko " +
                                        "Business/Creator karein aur Facebook Page se link karein."
                                )
                            if (ApiConfig.SERVER_BASE_URL.isBlank()) {
                                throw IllegalStateException(
                                    "Server URL set nahi — Instagram ko video ka public " +
                                        "link chahiye. ApiConfig mein SERVER_BASE_URL dalein."
                                )
                            }
                            updateProgress(
                                platform, UploadStatus.UPLOADING, 0.5f,
                                "Server par video ja rahi hai..."
                            )
                            val videoUrl = UploadEngine.uploadToServer(
                                videoFile, ApiConfig.SERVER_BASE_URL
                            ).getOrThrow()
                            updateProgress(
                                platform, UploadStatus.PROCESSING, 0.7f,
                                "Instagram tayyar kar raha hai..."
                            )
                            UploadEngine.publishInstagramReel(
                                pageToken, igId, videoUrl, caption, coverUrl
                            ).fold(
                                onSuccess = {
                                    PlatformResult(
                                        platform, UploadStatus.DONE, 1f,
                                        "Instagram par Reel publish ho gayi."
                                    )
                                },
                                onFailure = { e ->
                                    PlatformResult(
                                        platform, UploadStatus.FAILED, 0f,
                                        e.message ?: "Instagram upload fail."
                                    )
                                }
                            )
                        }
                        Platform.FACEBOOK -> {
                            val pageToken = tokenStore.getString("meta_page_token")
                                ?: throw IllegalStateException(
                                    "Facebook Page connect nahi — pehle Meta login karein."
                                )
                            val pageId = tokenStore.getString("meta_page_id")
                                ?: throw IllegalStateException(
                                    "Facebook Page nahi mila — dobara login karein."
                                )
                            val asReel = video.durationMs in 3000L..90_000L
                            UploadEngine.uploadFacebookVideo(
                                pageToken, pageId, videoFile, title, description, asReel
                            ).fold(
                                onSuccess = { url ->
                                    PlatformResult(
                                        platform, UploadStatus.DONE, 1f,
                                        "Facebook par post ho gayi.", url
                                    )
                                },
                                onFailure = { e ->
                                    PlatformResult(
                                        platform, UploadStatus.FAILED, 0f,
                                        e.message ?: "Facebook upload fail."
                                    )
                                }
                            )
                        }
                    }
                } catch (e: Exception) {
                    PlatformResult(platform, UploadStatus.FAILED, 0f, e.message ?: "Upload fail.")
                }
                results.add(result)
                updateProgress(platform, result.status, result.progress, result.message)
            }

            val record = UploadRecord(
                UUID.randomUUID().toString(),
                title,
                System.currentTimeMillis(),
                results
            )
            historyStore.saveRecord(record)
            _history.value = historyStore.listRecords()
        }
    }

    private fun updateProgress(
        platform: Platform,
        status: UploadStatus,
        progress: Float,
        message: String
    ) {
        val current = _uploadProgress.value.toMutableMap()
        val old = current[platform]
        current[platform] = PlatformResult(platform, status, progress, message, old?.liveUrl)
        _uploadProgress.value = current
    }

    fun resetUploadProgress() {
        _uploadProgress.value = emptyMap()
    }

    fun clearHistory() {
        historyStore.clear()
        _history.value = emptyList()
    }
}
