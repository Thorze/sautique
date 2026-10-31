package com.sautique.game

import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Classement mondial via l'API REST de Supabase (voir supabase/schema.sql).
 * Les appels réseau se font en arrière-plan, les réponses reviennent sur le thread principal.
 */
class OnlineLeaderboard(baseUrl: String, private val key: String) {

    class Entry(val id: String, val pseudo: String, val best: Int)

    private val base = baseUrl.trimEnd('/')
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    val configured: Boolean get() = base.isNotBlank() && key.isNotBlank()

    fun submit(id: String, pseudo: String, score: Int, onDone: (Boolean) -> Unit) {
        val body = JSONObject()
            .put("p_id", id)
            .put("p_pseudo", pseudo)
            .put("p_score", score)
            .toString()
        run(onDone, false) {
            val r = request("POST", "/rest/v1/rpc/submit_score", body)
            r.code in 200..299
        }
    }

    fun fetchTop(limit: Int, onResult: (List<Entry>?) -> Unit) {
        run(onResult, null) {
            val r = request("GET", "/rest/v1/players?select=id,pseudo,best&best=gt.0&order=best.desc,updated_at.asc&limit=$limit", null)
            if (r.code !in 200..299) return@run null
            val arr = JSONArray(r.body)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                Entry(o.getString("id"), o.getString("pseudo"), o.getInt("best"))
            }
        }
    }

    /** Place mondiale pour un score donné (1 = premier). */
    fun fetchRank(score: Int, onResult: (Int?) -> Unit) {
        run(onResult, null) {
            val r = request("GET", "/rest/v1/players?select=id&best=gt.$score&limit=1", null, mapOf("Prefer" to "count=exact"))
            if (r.code !in 200..299) return@run null
            // En-tête du type "0-0/42" ou "*/0"
            val total = r.contentRange?.substringAfter('/')?.toIntOrNull() ?: return@run null
            total + 1
        }
    }

    private class Response(val code: Int, val body: String, val contentRange: String?)

    private fun <T> run(callback: (T) -> Unit, fallback: T, block: () -> T) {
        if (!configured) {
            callback(fallback)
            return
        }
        executor.execute {
            val result = try { block() } catch (e: Exception) { fallback }
            main.post { callback(result) }
        }
    }

    private fun request(method: String, path: String, body: String?, extra: Map<String, String> = emptyMap()): Response {
        val conn = URL(base + path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("apikey", key)
            // Les anciennes clés "anon" sont des JWT et doivent aussi être passées en Bearer.
            if (!key.startsWith("sb_")) conn.setRequestProperty("Authorization", "Bearer $key")
            conn.setRequestProperty("Accept", "application/json")
            for ((k, v) in extra) conn.setRequestProperty(k, v)
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            return Response(code, text, conn.getHeaderField("Content-Range"))
        } finally {
            conn.disconnect()
        }
    }
}
