package com.postbee.app

/**
 * PostBee v1 — API settings.
 *
 * YE SAB KHALI HAIN — approval ke baad bharna hai:
 * - TikTok key/secret: developers.tiktok.com par app banane ke baad milte hain
 *   (Content Posting API product + video.publish / video.upload scope).
 * - Meta App ID/secret: developers.facebook.com par Business-type app banane
 *   ke baad milte hain.
 * - Server URL: Hetzner server set hone ke baad. Instagram ko video ka
 *   PUBLIC link chahiye, is liye server zaroori hai.
 *
 * Keys kabhi bhi chat mein ya git history mein share nahi karni.
 */
object ApiConfig {

    // TODO: TikTok developer approval ke baad bharein
    const val TIKTOK_CLIENT_KEY = ""
    const val TIKTOK_CLIENT_SECRET = ""

    // OAuth callback — AndroidManifest mein postbee://oauth intent-filter se milta hai
    const val TIKTOK_REDIRECT_URI = "postbee://oauth/tiktok"

    // TODO: Meta developer app banane ke baad bharein
    const val META_APP_ID = ""
    const val META_APP_SECRET = ""

    // OAuth callback — Meta login ke baad wapas app mein
    const val META_REDIRECT_URI = "postbee://oauth/meta"

    // TODO: Hetzner server tayyar hone ke baad bharein, masalan "https://api.postbee.app"
    // Server par POST /upload endpoint hona chahiye jo {"url": "..."} wapas de.
    const val SERVER_BASE_URL = ""

    // Upload chunk size — TikTok aur Facebook dono 5MB chunks se upload karte hain
    const val UPLOAD_CHUNK_SIZE_BYTES = 5 * 1024 * 1024
}
