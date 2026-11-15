package com.postbee.app.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.postbee.app.models.Platform

/**
 * Login tokens mehfooz rakhne ke liye.
 * Pehle EncryptedSharedPreferences (AES256) try karta hai; agar phone par
 * woh na chale to saada SharedPreferences par gir jata hai aur log mein
 * likh deta hai — app crash nahi hoti.
 */
class TokenStore(context: Context) {

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "postbee_tokens",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Log.w("PostBee", "Encrypted prefs fail, saada prefs use ho raha hai: ${e.message}")
        context.getSharedPreferences("postbee_tokens_plain", Context.MODE_PRIVATE)
    }

    /** Token save karein. expiresInSeconds = 0 matlab kabhi expire nahi (Page token). */
    fun saveToken(platform: Platform, token: String, expiresInSeconds: Long = 0L) {
        val expiry = if (expiresInSeconds > 0) {
            System.currentTimeMillis() + expiresInSeconds * 1000L
        } else {
            0L
        }
        prefs.edit()
            .putString("token_${platform.name}", token)
            .putLong("exp_${platform.name}", expiry)
            .apply()
    }

    fun getToken(platform: Platform): String? =
        prefs.getString("token_${platform.name}", null)

    fun isExpired(platform: Platform): Boolean {
        val exp = prefs.getLong("exp_${platform.name}", 0L)
        return exp != 0L && System.currentTimeMillis() > exp
    }

    fun saveString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    fun getString(key: String, default: String? = null): String? =
        prefs.getString(key, default)

    fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    fun clear(platform: Platform) {
        prefs.edit()
            .remove("token_${platform.name}")
            .remove("exp_${platform.name}")
            .apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
