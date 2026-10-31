package com.sautique.game

import android.content.Context
import java.util.UUID

/** Profil du joueur et top perso, stockés sur le téléphone. */
class LocalScores(context: Context) {

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

    fun needsSync() = best > 0 && (best > syncedBest || pseudo != syncedPseudo)

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
