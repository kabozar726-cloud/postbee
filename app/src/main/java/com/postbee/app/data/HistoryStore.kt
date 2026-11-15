package com.postbee.app.data

import android.content.Context
import com.postbee.app.models.Platform
import com.postbee.app.models.PlatformResult
import com.postbee.app.models.UploadRecord
import com.postbee.app.models.UploadStatus
import org.json.JSONArray
import org.json.JSONObject

/**
 * Upload history — SharedPreferences + org.json (koi aur library nahi).
 * Naye se purana order, zyada se zyada 50 records.
 */
class HistoryStore(context: Context) {

    private val prefs = context.getSharedPreferences("postbee_history", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_RECORDS = "records"
        private const val MAX_RECORDS = 50
    }

    fun saveRecord(record: UploadRecord) {
        val list = listRecords().toMutableList()
        list.add(0, record)
        while (list.size > MAX_RECORDS) {
            list.removeAt(list.lastIndex)
        }
        val arr = JSONArray()
        for (r in list) {
            arr.put(recordToJson(r))
        }
        prefs.edit().putString(KEY_RECORDS, arr.toString()).apply()
    }

    fun listRecords(): List<UploadRecord> {
        val raw = prefs.getString(KEY_RECORDS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val out = mutableListOf<UploadRecord>()
            for (i in 0 until arr.length()) {
                jsonToRecord(arr.getJSONObject(i))?.let { out.add(it) }
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun clear() {
        prefs.edit().remove(KEY_RECORDS).apply()
    }

    private fun recordToJson(r: UploadRecord): JSONObject {
        val results = JSONArray()
        for (res in r.results) {
            val o = JSONObject()
            o.put("platform", res.platform.name)
            o.put("status", res.status.name)
            o.put("progress", res.progress.toDouble())
            o.put("message", res.message)
            if (res.liveUrl != null) o.put("liveUrl", res.liveUrl)
            results.put(o)
        }
        val o = JSONObject()
        o.put("id", r.id)
        o.put("title", r.title)
        o.put("createdAt", r.createdAt)
        o.put("results", results)
        return o
    }

    private fun jsonToRecord(o: JSONObject): UploadRecord? {
        return try {
            val results = mutableListOf<PlatformResult>()
            val arr = o.optJSONArray("results") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val ro = arr.getJSONObject(i)
                val platform = try {
                    Platform.valueOf(ro.optString("platform", "TIKTOK"))
                } catch (e: Exception) {
                    Platform.TIKTOK
                }
                val status = try {
                    UploadStatus.valueOf(ro.optString("status", "FAILED"))
                } catch (e: Exception) {
                    UploadStatus.FAILED
                }
                results.add(
                    PlatformResult(
                        platform = platform,
                        status = status,
                        progress = ro.optDouble("progress", 0.0).toFloat(),
                        message = ro.optString("message", ""),
                        liveUrl = if (ro.has("liveUrl")) ro.optString("liveUrl") else null
                    )
                )
            }
            UploadRecord(
                id = o.optString("id", ""),
                title = o.optString("title", ""),
                createdAt = o.optLong("createdAt", 0L),
                results = results
            )
        } catch (e: Exception) {
            null
        }
    }
}
