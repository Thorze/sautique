package com.sautique.game

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

/**
 * Classement mondial hébergé sur ton propre serveur (voir le dossier server/).
 * Les appels réseau se font en arrière-plan, les réponses reviennent sur le thread principal.
 */
class ScoreServer(baseUrl: String, private val key: String) {

    class Entry(val pseudo: String, val best: Int, val me: Boolean)
    class Board(val top: List<Entry>, val myRank: Int?)

    private val base = baseUrl.trim().trimEnd('/')
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    val configured: Boolean get() = base.isNotBlank()

    enum class SubmitResult { OK, PSEUDO_TAKEN, REJECTED, ERROR }

    /** Envoie une partie (score, durée en secondes, nombre de sauts). Score 0 = mise à jour du pseudo. */
    fun submit(id: String, pseudo: String, score: Int, durationS: Float, jumps: Int, onDone: (SubmitResult) -> Unit) {
        val body = JSONObject()
            .put("id", id)
            .put("pseudo", pseudo)
            .put("score", score)
            .put("duration", durationS.toDouble())
            .put("jumps", jumps)
            .toString()
        call(onDone, SubmitResult.ERROR) {
            when (request("POST", "/api/scores", body).first) {
                in 200..299 -> SubmitResult.OK
                409 -> SubmitResult.PSEUDO_TAKEN
                in 400..499 -> SubmitResult.REJECTED
                else -> SubmitResult.ERROR
            }
        }
    }

    /** Classement : [period] = "all" (de tous les temps) ou "week" (semaine en cours). */
    fun fetchBoard(limit: Int, myId: String, period: String, onResult: (Board?) -> Unit) {
        call(onResult, null) {
            val (code, text) = request(
                "GET", "/api/top?limit=$limit&period=$period&me=" + URLEncoder.encode(myId, "UTF-8"), null
            )
            if (code !in 200..299) return@call null
            val json = JSONObject(text)
            val arr = json.getJSONArray("top")
            val top = List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                Entry(o.getString("pseudo"), o.getInt("best"), o.optBoolean("me"))
            }
            val me = json.optJSONObject("me")
            Board(top, me?.optInt("rank"))
        }
    }

    /** Le pseudo est-il libre ? null = impossible de vérifier (hors ligne). */
    fun checkPseudo(name: String, myId: String, onResult: (Boolean?) -> Unit) {
        call(onResult, null) {
            val (code, text) = request(
                "GET", "/api/pseudo?name=" + URLEncoder.encode(name, "UTF-8") + "&me=" + URLEncoder.encode(myId, "UTF-8"), null
            )
            if (code !in 200..299) return@call null
            JSONObject(text).optBoolean("available", true)
        }
    }

    private fun <T> call(callback: (T) -> Unit, fallback: T, block: () -> T) {
        if (!configured) {
            callback(fallback)
            return
        }
        executor.execute {
            val result = try { block() } catch (e: Exception) { fallback }
            main.post { callback(result) }
        }
    }

    private fun request(method: String, path: String, body: String?): Pair<Int, String> {
        val conn = URL(base + path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("Accept", "application/json")
            if (key.isNotBlank()) conn.setRequestProperty("X-Api-Key", key)
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            return code to text
        } finally {
            conn.disconnect()
        }
    }
}
