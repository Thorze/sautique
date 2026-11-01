package com.sautique.game

import kotlin.random.Random

/** Stockage clé/valeur (SharedPreferences dans l'appli, une Map dans les tests). */
interface KeyValueStore {
    fun getInt(key: String, def: Int): Int
    fun putInt(key: String, value: Int)
    fun getString(key: String): String?
    fun putString(key: String, value: String)
}

/** Ce qui fait avancer les missions. [max] = on garde la meilleure valeur au lieu d'additionner. */
enum class MissionType(val max: Boolean) {
    SCORE(true),
    PERFECTS(false),
    STREAK(true),
    COINS(false),
    BONUS_WIN(false),
    TRAMPOLINES(false),
    GAMES(false),
    JUMPS(false),
    SCARES(false);

    /** Texte traduit (clé « mission.score »…, {n} = objectif). */
    fun describe(target: Int) = I18n.tr("mission." + name.lowercase(), "n" to target)
}

class Mission(val type: MissionType, val target: Int, val reward: Int, val challenge: Boolean) {
    val label get() = type.describe(target)
}

/**
 * 3 missions du jour + 1 défi du jour plus difficile, identiques pour toute la journée
 * (tirées au hasard à partir de la date). Les récompenses sont des pièces.
 */
class DailyMissions(private val store: KeyValueStore, private val today: () -> String) {

    companion object {
        const val ALL_DONE_REWARD = 25

        private val easy = mapOf(
            MissionType.SCORE to listOf(15, 20, 25),
            MissionType.PERFECTS to listOf(4, 6, 8),
            MissionType.STREAK to listOf(2, 3),
            MissionType.COINS to listOf(5, 8, 10),
            MissionType.BONUS_WIN to listOf(1, 2),
            MissionType.TRAMPOLINES to listOf(2, 3, 4),
            MissionType.GAMES to listOf(3, 5),
            MissionType.JUMPS to listOf(40, 60, 80),
            MissionType.SCARES to listOf(2, 3)
        )
        private val hard = mapOf(
            MissionType.SCORE to listOf(50, 60, 75),
            MissionType.STREAK to listOf(4, 5, 6),
            MissionType.PERFECTS to listOf(15, 20),
            MissionType.COINS to listOf(25, 30)
        )

        /** Missions d'une date donnée (toujours les mêmes pour une même date). */
        fun generate(date: String): List<Mission> {
            val rnd = Random(date.hashCode())
            val types = easy.keys.shuffled(rnd).take(3)
            val list = types.map { t ->
                val targets = easy.getValue(t)
                val i = rnd.nextInt(targets.size)
                Mission(t, targets[i], 5 + 5 * i, challenge = false)
            }
            val ht = hard.keys.toList()[rnd.nextInt(hard.size)]
            val htargets = hard.getValue(ht)
            return list + Mission(ht, htargets[rnd.nextInt(htargets.size)], 50, challenge = true)
        }
    }

    private var date = ""
    var missions: List<Mission> = emptyList()
        private set

    private fun refresh() {
        val d = today()
        if (d == date) return
        date = d
        missions = generate(d)
        if (store.getString("missions_date") != d) {
            store.putString("missions_date", d)
            for (i in missions.indices) {
                store.putInt("mission_p$i", 0)
                store.putInt("mission_done$i", 0)
            }
            store.putInt("missions_all_done", 0)
        }
    }

    fun list(): List<Mission> { refresh(); return missions }

    fun progress(i: Int): Int { refresh(); return store.getInt("mission_p$i", 0) }

    fun isDone(i: Int): Boolean { refresh(); return store.getInt("mission_done$i", 0) == 1 }

    /** Les 3 missions (hors défi) sont-elles toutes terminées ? */
    fun allMissionsDone(): Boolean { refresh(); return (0 until 3).all { isDone(it) } }

    /**
     * Enregistre un événement. Renvoie les pièces gagnées et les missions terminées à l'instant
     * (avec, le cas échéant, le bonus pour avoir terminé les 3 missions du jour).
     */
    fun record(type: MissionType, amount: Int = 1): Result {
        refresh()
        var coins = 0
        val done = ArrayList<Mission>()
        missions.forEachIndexed { i, m ->
            if (m.type != type || isDone(i)) return@forEachIndexed
            val old = store.getInt("mission_p$i", 0)
            val p = if (type.max) maxOf(old, amount) else old + amount
            store.putInt("mission_p$i", minOf(p, m.target))
            if (p >= m.target) {
                store.putInt("mission_done$i", 1)
                coins += m.reward
                done += m
            }
        }
        var allBonus = false
        if (done.isNotEmpty() && allMissionsDone() && store.getInt("missions_all_done", 0) == 0) {
            store.putInt("missions_all_done", 1)
            coins += ALL_DONE_REWARD
            allBonus = true
        }
        return Result(coins, done, allBonus)
    }

    class Result(val coins: Int, val completed: List<Mission>, val allDoneBonus: Boolean)
}
