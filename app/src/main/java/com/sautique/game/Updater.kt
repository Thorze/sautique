package com.sautique.game

import android.app.Activity
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Mise à jour automatique depuis les releases GitHub du dépôt (BuildConfig.UPDATE_REPO).
 * La release doit avoir un tag du type v1.4 ou v1.4.2 et contenir un fichier .apk.
 */
object Updater {

    class Release(val tag: String, val code: Int, val notes: String, val apkUrl: String)

    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var checking = false
    private var pending: Release? = null

    /** Vérifie s'il existe une version plus récente. [manual] affiche aussi « à jour » / les erreurs. */
    fun check(activity: Activity, manual: Boolean = false) {
        if (checking || BuildConfig.UPDATE_REPO.isBlank()) return
        checking = true
        executor.execute {
            val release = try { fetchLatest() } catch (e: Exception) { null }
            main.post {
                checking = false
                if (activity.isFinishing || activity.isDestroyed) return@post
                when {
                    release != null && release.code > BuildConfig.VERSION_CODE -> showUpdateDialog(activity, release)
                    manual && release == null -> toast(activity, tr("update.check_failed"))
                    manual -> toast(activity, tr("update.up_to_date", "v" to BuildConfig.VERSION_NAME))
                }
            }
        }
    }

    /** A appeler dans onResume : reprend l'installation après l'autorisation « sources inconnues ». */
    fun onResume(activity: Activity) {
        val r = pending ?: return
        if (canInstall(activity)) {
            pending = null
            download(activity, r)
        }
    }

    private fun fetchLatest(): Release? {
        val conn = URL("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest")
            .openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "Sautique-Android")
            if (conn.responseCode != 200) return null
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val tag = json.optString("tag_name")
            val code = Scoring.versionCodeOf(tag) ?: return null
            val assets = json.optJSONArray("assets") ?: return null
            var apk: String? = null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                    apk = a.optString("browser_download_url")
                    break
                }
            }
            return Release(tag, code, json.optString("body"), apk ?: return null)
        } finally {
            conn.disconnect()
        }
    }

    private fun showUpdateDialog(activity: Activity, r: Release) {
        val notes = r.notes.trim().let { if (it.length > 600) it.take(600) + "…" else it }
        val msg = tr("update.available_msg", "v" to r.tag.removePrefix("v"), "mine" to BuildConfig.VERSION_NAME) +
            if (notes.isNotEmpty()) "\n\n$notes" else ""
        AlertDialog.Builder(activity)
            .setTitle(tr("update.available"))
            .setMessage(msg)
            .setPositiveButton(tr("update.install")) { _, _ -> startInstall(activity, r) }
            .setNegativeButton(tr("update.later"), null)
            .show()
    }

    private fun canInstall(activity: Activity) =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || activity.packageManager.canRequestPackageInstalls()

    private fun startInstall(activity: Activity, r: Release) {
        if (canInstall(activity)) {
            download(activity, r)
            return
        }
        pending = r
        AlertDialog.Builder(activity)
            .setTitle(tr("update.permission"))
            .setMessage(tr("update.permission_msg"))
            .setPositiveButton(tr("update.open_settings")) { _, _ ->
                activity.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + activity.packageName))
                )
            }
            .setNegativeButton(tr("common.cancel")) { _, _ -> pending = null }
            .show()
    }

    private fun download(activity: Activity, r: Release) {
        val pad = (20 * activity.resources.displayMetrics.density).toInt()
        val bar = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
        val label = TextView(activity).apply { text = tr("update.downloading") }
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, 0)
            addView(label)
            addView(bar)
        }
        val dialog = AlertDialog.Builder(activity)
            .setTitle(tr("update.title", "v" to r.tag))
            .setView(box)
            .setCancelable(false)
            .show()

        val appContext = activity.applicationContext
        executor.execute {
            val installer = appContext.packageManager.packageInstaller
            var sessionId = -1
            try {
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                params.setAppPackageName(appContext.packageName)
                sessionId = installer.createSession(params)
                installer.openSession(sessionId).use { session ->
                    val conn = URL(r.apkUrl).openConnection() as HttpURLConnection
                    conn.connectTimeout = 10000
                    conn.readTimeout = 20000
                    conn.setRequestProperty("User-Agent", "Sautique-Android")
                    try {
                        if (conn.responseCode != 200) throw IllegalStateException("HTTP ${conn.responseCode}")
                        val total = conn.contentLengthLong
                        conn.inputStream.use { input ->
                            session.openWrite("sautique.apk", 0, if (total > 0) total else -1).use { out ->
                                val buf = ByteArray(64 * 1024)
                                var done = 0L
                                var lastPct = -1
                                while (true) {
                                    val n = input.read(buf)
                                    if (n < 0) break
                                    out.write(buf, 0, n)
                                    done += n
                                    if (total > 0) {
                                        val pct = (done * 100 / total).toInt()
                                        if (pct != lastPct) {
                                            lastPct = pct
                                            main.post { bar.progress = pct; label.text = tr("update.downloading") + " $pct %" }
                                        }
                                    }
                                }
                                session.fsync(out)
                            }
                        }
                    } finally {
                        conn.disconnect()
                    }
                    val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
                    val pi = PendingIntent.getBroadcast(
                        appContext, sessionId, Intent(appContext, UpdateReceiver::class.java), flags
                    )
                    session.commit(pi.intentSender)
                }
                main.post { dialog.dismiss() }
            } catch (e: Exception) {
                if (sessionId >= 0) try { installer.abandonSession(sessionId) } catch (_: Exception) {}
                main.post {
                    dialog.dismiss()
                    toast(appContext, tr("update.failed", "msg" to (e.message ?: tr("update.network_error"))))
                }
            }
        }
    }

    private fun toast(context: android.content.Context, text: String) =
        Toast.makeText(context, text, Toast.LENGTH_LONG).show()
}
