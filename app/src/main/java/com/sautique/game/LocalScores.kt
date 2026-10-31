package com.sautique.game

import android.content.Context
import java.util.UUID

/** Profil du joueur et top perso, stockés sur le téléphone. */
class LocalScores(context: Context) : KeyValueStore {

    class Run(val score: Int, val time: Long)

    private val prefs = context.getSharedPreferences("sautique", Context.MODE_PRIVATE)

    /** Identifiant unique et anonyme de ce téléphone pour le classement mondial. */
    val playerId: String = prefs.getString("player_id", null)
        ?: UUID.randomUUID().toString().also { prefs.edit().putString("player_id", it).apply() }

    var pseudo: String?
        get() = prefs.getString("pseudo", null)
        set(value) = prefs.edit().putString("pseudo", value).apply()

    val best: Int get() = prefs.getInt("best", 0)

    /** Dernier meilleur score / pseudo envoyés avec succès au classement mondial. */
    var syncedBest: Int
        get() = prefs.getInt("synced_best", 0)
        set(value) = prefs.edit().putInt("synced_best", value).apply()
    var syncedPseudo: String?
        get() = prefs.getString("synced_pseudo", null)
        set(value) = prefs.edit().putString("synced_pseudo", value).apply()

    /** Points cumulés sur toutes les parties : servent à débloquer les skins. */
    var totalPoints: Int
        get() {
            if (!prefs.contains("total_points")) {
                // Première fois : on part des parties déjà jouées
                val seed = topRuns().sumOf { it.score }
                prefs.edit().putInt("total_points", seed).apply()
                return seed
            }
            return prefs.getInt("total_points", 0)
        }
        set(value) = prefs.edit().putInt("total_points", value).apply()

    /** true une fois que le joueur a vu l'écran de choix du personnage. */
    var customized: Boolean
        get() = prefs.getBoolean("customized", false)
        set(value) = prefs.edit().putBoolean("customized", value).apply()

    fun selected(slot: Skins.Slot): String =
        prefs.getString("skin_" + slot.key, null) ?: Skins.default(slot)

    fun select(slot: Skins.Slot, id: String) = prefs.edit().putString("skin_" + slot.key, id).apply()

    // ---------- Pièces et boutique ----------

    var coins: Int
        get() = prefs.getInt("coins", 0)
        set(value) = prefs.edit().putInt("coins", value.coerceAtLeast(0)).apply()

    fun owns(item: Skins.Item) = !item.isShop || prefs.getStringSet("owned", emptySet())!!.contains(key(item))

    /** Achète l'élément si on a assez de pièces. */
    fun buy(item: Skins.Item): Boolean {
        if (owns(item) || coins < item.price) return false
        coins -= item.price
        val owned = HashSet(prefs.getStringSet("owned", emptySet())!!)
        owned += key(item)
        prefs.edit().putStringSet("owned", owned).apply()
        return true
    }

    private fun key(item: Skins.Item) = item.slot.key + ":" + item.id

    /** Un élément est utilisable s'il est débloqué par les points ou acheté. */
    fun available(item: Skins.Item) = if (item.isShop) owns(item) else item.cost <= totalPoints

    // ---------- Réglages ----------

    var vibrationOn: Boolean
        get() = prefs.getBoolean("vibration_on", true)
        set(value) = prefs.edit().putBoolean("vibration_on", value).apply()

    // ---------- Envoi des scores au serveur ----------

    /** Meilleure partie de la semaine pas encore envoyée (score, durée en s, sauts). */
    fun queueRun(score: Int, durationS: Float, jumps: Int) {
        if (score <= 0) return
        if (prefs.getInt("pending_score", 0) >= score) return
        prefs.edit()
            .putInt("pending_score", score)
            .putFloat("pending_duration", durationS)
            .putInt("pending_jumps", jumps)
            .apply()
    }

    val pendingScore get() = prefs.getInt("pending_score", 0)
    val pendingDuration get() = prefs.getFloat("pending_duration", 0f)
    val pendingJumps get() = prefs.getInt("pending_jumps", 0)

    fun clearPending(sentScore: Int) {
        if (pendingScore <= sentScore) prefs.edit().remove("pending_score").remove("pending_duration")
            .remove("pending_jumps").apply()
    }

    fun needsSync() = pendingScore > 0 || (pseudo != null && pseudo != syncedPseudo)

    // ---------- KeyValueStore (missions) ----------

    override fun getInt(key: String, def: Int) = prefs.getInt(key, def)
    override fun putInt(key: String, value: Int) = prefs.edit().putInt(key, value).apply()
    override fun getString(key: String): String? = prefs.getString(key, null)
    override fun putString(key: String, value: String) = prefs.edit().putString(key, value).apply()

    fun topRuns(): List<Run> {
        val raw = prefs.getString("runs", "") ?: ""
        return raw.split(';').mapNotNull { item ->
            val parts = item.split(':')
            if (parts.size != 2) return@mapNotNull null
            val s = parts[0].toIntOrNull() ?: return@mapNotNull null
            val t = parts[1].toLongOrNull() ?: return@mapNotNull null
            Run(s, t)
        }
    }

    /** Enregistre une partie. Renvoie true si c'est un nouveau record. */
    fun addRun(score: Int): Boolean {
        if (score <= 0) return false
        totalPoints += score
        val runs = (topRuns() + Run(score, System.currentTimeMillis()))
            .sortedWith(compareByDescending<Run> { it.score }.thenBy { it.time })
            .take(MAX_RUNS)
        val editor = prefs.edit().putString("runs", runs.joinToString(";") { "${it.score}:${it.time}" })
        val isBest = score > best
        if (isBest) editor.putInt("best", score)
        editor.apply()
        return isBest
    }

    companion object {
        const val MAX_RUNS = 10
    }
}
