package com.postbee.app.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.postbee.app.ApiConfig
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Meta Page — Facebook login ke baad milti hai. */
data class MetaPage(
    val id: String,
    val name: String,
    val accessToken: String,
    val instagramBusinessId: String?
)

/**
 * UploadEngine — teeno platforms ka ASAL upload code.
 *
 * Sirf java.net.HttpURLConnection + org.json use hota hai (koi aur network
 * library nahi). Har function Result<String> deta hai — kaamyabi par live
 * URL ya post ID, nakami par Roman Urdu message ke saath exception.
 * Per-platform try/catch ViewModel mein hai, yahan nahi.
 *
 * Saare network calls Dispatchers.IO par chalte hain.
 */
object UploadEngine {

    // ---------- Chhote helpers ----------

    private fun readResponse(conn: HttpURLConnection): String {
        val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
        return stream?.bufferedReader()?.use { it.readText() } ?: ""
    }

    private fun postForm(
        urlStr: String,
        params: Map<String, String>,
        bearer: String? = null,
        extraHeaders: Map<String, String> = emptyMap()
    ): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 30_000
            readTimeout = 60_000
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            if (bearer != null) setRequestProperty("Authorization", "Bearer $bearer")
            extraHeaders.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        val body = params.entries.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
        }
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        val text = readResponse(conn)
        conn.disconnect()
        if (code !in 200..299) throw IOException("HTTP $code: $text")
        return text
    }

    private fun postJson(urlStr: String, jsonBody: String, bearer: String): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 30_000
            readTimeout = 60_000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $bearer")
        }
        conn.outputStream.use { it.write(jsonBody.toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        val text = readResponse(conn)
        conn.disconnect()
        if (code !in 200..299) throw IOException("HTTP $code: $text")
        return text
    }

    private fun httpGet(urlStr: String, bearer: String? = null): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 30_000
            readTimeout = 60_000
            if (bearer != null) setRequestProperty("Authorization", "Bearer $bearer")
        }
        val code = conn.responseCode
        val text = readResponse(conn)
        conn.disconnect()
        if (code !in 200..299) throw IOException("HTTP $code: $text")
        return text
    }

    private fun putBytes(urlStr: String, bytes: ByteArray, headers: Map<String, String>): Int {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"
            doOutput = true
            connectTimeout = 30_000
            readTimeout = 120_000
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        conn.outputStream.use { it.write(bytes) }
        val code = conn.responseCode
        val text = readResponse(conn)
        conn.disconnect()
        if (code !in 200..299) throw IOException("Chunk upload fail HTTP $code: $text")
        return code
    }

    private fun postBytes(urlStr: String, bytes: ByteArray, headers: Map<String, String>): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 30_000
            readTimeout = 120_000
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        conn.outputStream.use { it.write(bytes) }
        val code = conn.responseCode
        val text = readResponse(conn)
        conn.disconnect()
        if (code !in 200..299) throw IOException("Upload fail HTTP $code: $text")
        return text
    }

    private fun randomString(length: Int): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
        val rnd = SecureRandom()
        return (1..length).map { chars[rnd.nextInt(chars.length)] }.joinToString("")
    }

    private fun codeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(
            digest, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        )
    }

    /** Content URI ko cache file mein copy karein (IO thread par call karein). */
    fun copyUriToCache(context: Context, uri: Uri, fileName: String): File {
        val file = File(context.cacheDir, fileName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(file).use { output -> input.copyTo(output) }
        }
        return file
    }

    // ---------- TIKTOK ----------

    /** Login Kit authorize URL banayein. Wapas: (url, codeVerifier, state). */
    fun buildTikTokAuthUrl(): Triple<String, String, String> {
        val verifier = randomString(64)
        val state = randomString(16)
        val challenge = codeChallenge(verifier)
        val url = "https://www.tiktok.com/v2/auth/authorize/" +
            "?client_key=${ApiConfig.TIKTOK_CLIENT_KEY}" +
            "&scope=user.info.basic,video.upload,video.publish" +
            "&response_type=code" +
            "&redirect_uri=${URLEncoder.encode(ApiConfig.TIKTOK_REDIRECT_URI, "UTF-8")}" +
            "&state=$state" +
            "&code_challenge=$challenge" +
            "&code_challenge_method=S256"
        return Triple(url, verifier, state)
    }

    /** Login ke baad mile code ko access token mein badlein. */
    suspend fun exchangeTikTokCode(code: String, verifier: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (ApiConfig.TIKTOK_CLIENT_KEY.isBlank()) {
                    throw IllegalStateException(
                        "TikTok developer key abhi set nahi — approval ke baad ApiConfig mein dalein."
                    )
                }
                val text = postForm(
                    "https://open.tiktokapis.com/v2/oauth/token/",
                    mapOf(
                        "client_key" to ApiConfig.TIKTOK_CLIENT_KEY,
                        "client_secret" to ApiConfig.TIKTOK_CLIENT_SECRET,
                        "code" to code,
                        "grant_type" to "authorization_code",
                        "redirect_uri" to ApiConfig.TIKTOK_REDIRECT_URI,
                        "code_verifier" to verifier
                    )
                )
                val token = JSONObject(text).optString("access_token", "")
                if (token.isBlank()) throw IOException("TikTok token nahi mila: $text")
                token
            }
        }

    /** TikTok account ka naam — Accounts screen par dikhane ke liye. */
    suspend fun fetchTikTokUser(token: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val text = httpGet(
                    "https://open.tiktokapis.com/v2/user/info/?fields=display_name",
                    token
                )
                val name = JSONObject(text)
                    .optJSONObject("data")
                    ?.optJSONObject("user")
                    ?.optString("display_name", "") ?: ""
                if (name.isBlank()) throw IOException("TikTok user info nahi mili: $text")
                name
            }
        }

    /**
     * TikTok Direct Post — video seedha profile par publish hoti hai.
     * Flow: init → 5MB chunks mein PUT → status poll.
     * NOTE: jab tak TikTok audit pass na ho, post sirf PRIVATE (SELF_ONLY)
     * hoti hai — ye TikTok ka rule hai, app ki ghalti nahi.
     */
    suspend fun publishTikTokVideo(
        token: String,
        videoFile: File,
        title: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            if (ApiConfig.TIKTOK_CLIENT_KEY.isBlank()) {
                throw IllegalStateException(
                    "TikTok developer key abhi set nahi — approval ke baad ApiConfig mein dalein."
                )
            }
            val size = videoFile.length()
            val chunkSize = ApiConfig.UPLOAD_CHUNK_SIZE_BYTES.toLong()
            val totalChunks = ((size + chunkSize - 1) / chunkSize).toInt()

            val initBody = JSONObject().apply {
                put("post_info", JSONObject().apply {
                    put("title", title)
                    put("privacy_level", "PUBLIC_TO_EVERYONE")
                    put("disable_duet", false)
                    put("disable_comment", false)
                    put("disable_stitch", false)
                    put("video_cover_timestamp_ms", 1000)
                })
                put("source_info", JSONObject().apply {
                    put("source", "FILE_UPLOAD")
                    put("video_size", size)
                    put("chunk_size", chunkSize)
                    put("total_chunk_count", totalChunks)
                })
            }
            val initText = postJson(
                "https://open.tiktokapis.com/v2/post/publish/video/init/",
                initBody.toString(),
                token
            )
            val initData = JSONObject(initText).optJSONObject("data")
                ?: throw IOException("TikTok init fail: $initText")
            val uploadUrl = initData.optString("upload_url", "")
            val publishId = initData.optString("publish_id", "")
            if (uploadUrl.isBlank() || publishId.isBlank()) {
                throw IOException("TikTok upload URL nahi mila: $initText")
            }

            FileInputStream(videoFile).use { input ->
                val buffer = ByteArray(ApiConfig.UPLOAD_CHUNK_SIZE_BYTES)
                var offset = 0L
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    val end = offset + read - 1
                    putBytes(
                        uploadUrl,
                        buffer.copyOf(read),
                        mapOf(
                            "Content-Type" to "video/mp4",
                            "Content-Range" to "bytes $offset-$end/$size"
                        )
                    )
                    offset += read
                }
            }

            var status = ""
            for (i in 0 until 12) {
                delay(5000)
                val stText = postJson(
                    "https://open.tiktokapis.com/v2/post/publish/status/fetch/",
                    JSONObject().put("publish_id", publishId).toString(),
                    token
                )
                status = JSONObject(stText).optJSONObject("data")
                    ?.optString("status", "") ?: ""
                if (status == "PUBLISHED" || status == "FAILED") break
            }
            if (status == "FAILED") {
                throw IOException("TikTok ne video publish nahi ki (status FAILED).")
            }
            // TikTok seedha video ka link nahi deta — publish_id hi saboot hai.
            "tiktok:$publishId"
        }
    }

    // ---------- META (Facebook + Instagram) ----------

    /** Meta login URL — Facebook + Instagram dono isi se connect hote hain. */
    fun buildMetaAuthUrl(state: String): String {
        val scope = "pages_manage_posts,pages_read_engagement,pages_show_list," +
            "instagram_basic,instagram_content_publish"
        return "https://www.facebook.com/v21.0/dialog/oauth" +
            "?client_id=${ApiConfig.META_APP_ID}" +
            "&redirect_uri=${URLEncoder.encode(ApiConfig.META_REDIRECT_URI, "UTF-8")}" +
            "&scope=$scope" +
            "&response_type=code" +
            "&state=$state"
    }

    /** Meta login code ko chhota token mein badlein. */
    suspend fun exchangeMetaCode(code: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (ApiConfig.META_APP_ID.isBlank()) {
                    throw IllegalStateException(
                        "Meta App ID abhi set nahi — Meta developer app banane ke baad ApiConfig mein dalein."
                    )
                }
                val url = "https://graph.facebook.com/v21.0/oauth/access_token" +
                    "?client_id=${ApiConfig.META_APP_ID}" +
                    "&redirect_uri=${URLEncoder.encode(ApiConfig.META_REDIRECT_URI, "UTF-8")}" +
                    "&client_secret=${ApiConfig.META_APP_SECRET}" +
                    "&code=$code"
                val text = httpGet(url)
                val token = JSONObject(text).optString("access_token", "")
                if (token.isBlank()) throw IOException("Meta token nahi mila: $text")
                token
            }
        }

    /** Chhote token ko lamba (60 din) token mein badlein. */
    suspend fun getLongLivedMetaToken(shortToken: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = "https://graph.facebook.com/v21.0/oauth/access_token" +
                    "?grant_type=fb_exchange_token" +
                    "&client_id=${ApiConfig.META_APP_ID}" +
                    "&client_secret=${ApiConfig.META_APP_SECRET}" +
                    "&fb_exchange_token=$shortToken"
                val text = httpGet(url)
                val token = JSONObject(text).optString("access_token", "")
                if (token.isBlank()) throw IOException("Lamba Meta token nahi mila: $text")
                token
            }
        }

    /** User ke Pages + har Page ka Instagram Business account nikaalein. */
    suspend fun fetchMetaPages(userToken: String): Result<List<MetaPage>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = "https://graph.facebook.com/v21.0/me/accounts" +
                    "?fields=id,name,access_token,instagram_business_account" +
                    "&access_token=${URLEncoder.encode(userToken, "UTF-8")}"
                val text = httpGet(url)
                val pages = mutableListOf<MetaPage>()
                val arr: JSONArray = JSONObject(text).optJSONArray("data") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val igId = o.optJSONObject("instagram_business_account")
                        ?.optString("id", null)
                    pages.add(
                        MetaPage(
                            id = o.optString("id", ""),
                            name = o.optString("name", ""),
                            accessToken = o.optString("access_token", ""),
                            instagramBusinessId = igId
                        )
                    )
                }
                pages
            }
        }

    // ---------- INSTAGRAM ----------

    /**
     * Instagram Reel publish — 2 steps: container banayein, phir publish karein.
     * videoUrl PUBLIC link hona chahiye (server se) — Meta video ko us link
     * se uthata hai. coverUrl bhi public link hona chahiye (ya null).
     */
    suspend fun publishInstagramReel(
        pageToken: String,
        igUserId: String,
        videoUrl: String,
        caption: String,
        coverUrl: String?
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val params = mutableMapOf(
                "media_type" to "REELS",
                "video_url" to videoUrl,
                "caption" to caption,
                "access_token" to pageToken
            )
            if (!coverUrl.isNullOrBlank()) params["cover_url"] = coverUrl

            val createText = postForm(
                "https://graph.facebook.com/v21.0/$igUserId/media",
                params
            )
            val containerId = JSONObject(createText).optString("id", "")
            if (containerId.isBlank()) {
                throw IOException("Instagram container nahi bana: $createText")
            }

            var status = ""
            for (i in 0 until 10) {
                delay(5000)
                val stText = httpGet(
                    "https://graph.facebook.com/v21.0/$containerId" +
                        "?fields=status_code" +
                        "&access_token=${URLEncoder.encode(pageToken, "UTF-8")}"
                )
                status = JSONObject(stText).optString("status_code", "")
                if (status == "FINISHED" || status == "ERROR") break
            }
            if (status != "FINISHED") {
                throw IOException(
                    "Instagram video tayyar nahi hui (status: $status) — dobara koshish karein."
                )
            }

            val pubText = postForm(
                "https://graph.facebook.com/v21.0/$igUserId/media_publish",
                mapOf("creation_id" to containerId, "access_token" to pageToken)
            )
            val mediaId = JSONObject(pubText).optString("id", "")
            if (mediaId.isBlank()) throw IOException("Instagram publish fail: $pubText")
            "instagram:$mediaId"
        }
    }

    // ---------- SERVER (Instagram ke liye public link) ----------

    /**
     * Video (ya thumbnail) ko apne server par chadhayein.
     * Server ka POST /upload endpoint {"url": "..."} wapas de.
     */
    suspend fun uploadToServer(
        file: File,
        serverBaseUrl: String,
        fieldName: String = "video",
        mimeType: String = "video/mp4"
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val base = serverBaseUrl.trimEnd('/')
            val boundary = "PostBee${System.currentTimeMillis()}"
            val conn = (URL("$base/upload").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 30_000
                readTimeout = 300_000
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            }
            conn.outputStream.use { out ->
                val head = "--$boundary\r\n" +
                    "Content-Disposition: form-data; name=\"$fieldName\"; " +
                    "filename=\"${file.name}\"\r\n" +
                    "Content-Type: $mimeType\r\n\r\n"
                out.write(head.toByteArray(Charsets.UTF_8))
                FileInputStream(file).use { it.copyTo(out) }
                out.write("\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8))
            }
            val code = conn.responseCode
            val text = readResponse(conn)
            conn.disconnect()
            if (code !in 200..299) throw IOException("Server upload fail HTTP $code: $text")
            val url = JSONObject(text).optString("url", "")
            if (url.isBlank()) throw IOException("Server ne video ka link nahi diya: $text")
            url
        }
    }

    // ---------- FACEBOOK ----------

    private fun uploadChunksToRupload(pageToken: String, videoId: String, file: File) {
        FileInputStream(file).use { input ->
            val buffer = ByteArray(ApiConfig.UPLOAD_CHUNK_SIZE_BYTES)
            var offset = 0L
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                postBytes(
                    "https://rupload.facebook.com/video-upload/$videoId",
                    buffer.copyOf(read),
                    mapOf(
                        "Authorization" to "OAuth $pageToken",
                        "file_offset" to offset.toString()
                    )
                )
                offset += read
            }
        }
    }

    /**
     * Facebook video upload.
     * - 90 second tak: Reel (video_reels, 3 steps)
     * - Is se lambi: Page feed video (resumable upload, 3 steps)
     * Dono mein sirf Page par post hoti hai — personal profile par nahi.
     */
    suspend fun uploadFacebookVideo(
        pageToken: String,
        pageId: String,
        videoFile: File,
        title: String,
        description: String,
        asReel: Boolean
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            if (asReel) {
                val startText = postForm(
                    "https://graph.facebook.com/v21.0/$pageId/video_reels",
                    mapOf("upload_phase" to "start", "access_token" to pageToken)
                )
                val videoId = JSONObject(startText).optString("video_id", "")
                if (videoId.isBlank()) {
                    throw IOException("Facebook Reel start fail: $startText")
                }
                uploadChunksToRupload(pageToken, videoId, videoFile)
                val finishText = postForm(
                    "https://graph.facebook.com/v21.0/$pageId/video_reels",
                    mapOf(
                        "upload_phase" to "finish",
                        "video_id" to videoId,
                        "video_state" to "PUBLISHED",
                        "description" to "$title\n$description",
                        "access_token" to pageToken
                    )
                )
                val finishJson = JSONObject(finishText)
                val postId = finishJson.optString("post_id", finishJson.optString("id", ""))
                if (postId.isBlank()) {
                    throw IOException("Facebook Reel finish fail: $finishText")
                }
                "https://www.facebook.com/$postId"
            } else {
                if (ApiConfig.META_APP_ID.isBlank()) {
                    throw IllegalStateException(
                        "Meta App ID abhi set nahi — Meta developer app banane ke baad ApiConfig mein dalein."
                    )
                }
                val startText = postForm(
                    "https://graph.facebook.com/v21.0/${ApiConfig.META_APP_ID}/uploads",
                    mapOf(
                        "file_name" to videoFile.name,
                        "file_length" to videoFile.length().toString(),
                        "file_type" to "video/mp4",
                        "access_token" to pageToken
                    )
                )
                val sessionId = JSONObject(startText).optString("id", "")
                if (sessionId.isBlank()) {
                    throw IOException("Facebook upload session nahi bani: $startText")
                }
                var handle = ""
                FileInputStream(videoFile).use { input ->
                    val buffer = ByteArray(ApiConfig.UPLOAD_CHUNK_SIZE_BYTES)
                    var offset = 0L
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        val resp = postBytes(
                            "https://graph.facebook.com/v21.0/upload:$sessionId",
                            buffer.copyOf(read),
                            mapOf(
                                "Authorization" to "OAuth $pageToken",
                                "file_offset" to offset.toString()
                            )
                        )
                        handle = JSONObject(resp).optString("h", handle)
                        offset += read
                    }
                }
                if (handle.isBlank()) {
                    throw IOException("Facebook video upload fail — handle nahi mila.")
                }
                val finishText = postForm(
                    "https://graph.facebook.com/v21.0/$pageId/videos",
                    mapOf(
                        "title" to title,
                        "description" to description,
                        "fbuploader_video_file_chunk" to handle,
                        "access_token" to pageToken
                    )
                )
                val vid = JSONObject(finishText).optString("id", "")
                if (vid.isBlank()) {
                    throw IOException("Facebook video publish fail: $finishText")
                }
                "https://www.facebook.com/watch/?v=$vid"
            }
        }
    }
}
