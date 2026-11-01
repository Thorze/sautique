package com.sautique.game

/**
 * Classement mondial. Deux versions selon l'endroit d'où vient l'appli :
 * - GitHub (APK) : ton serveur ([ScoreServer]), avec un pseudo choisi dans le jeu ;
 * - Play Store : les classements Google Play Jeux (nom du profil Play Jeux, aucun serveur à toi).
 * Chaque version fournit [createOnline] dans son dossier (src/github, src/play).
 */
interface OnlineScores {
    class Entry(val pseudo: String, val best: Int, val me: Boolean)
    class Board(val top: List<Entry>, val myRank: Int?)
    enum class SubmitResult { OK, PSEUDO_TAKEN, REJECTED, ERROR }

    val configured: Boolean
    /** true = le joueur choisit son pseudo dans le jeu ; false = nom du compte (Play Jeux). */
    val usesPseudo: Boolean get() = true
    /** Nom affiché quand il vient du compte (Play Jeux) ; null si pas connecté. */
    val playerName: String? get() = null
    val signedIn: Boolean get() = true
    /** Connexion au compte (Play Jeux) ; [onDone] est appelé dans tous les cas. */
    fun signIn(onDone: () -> Unit) = onDone()

    /** Envoie une partie (score, durée en secondes, nombre de sauts). Score 0 = mise à jour du pseudo. */
    fun submit(id: String, pseudo: String, score: Int, durationS: Float, jumps: Int, onDone: (SubmitResult) -> Unit)

    /** Classement : [period] = "all" (de tous les temps) ou "week" (semaine en cours). */
    fun fetchBoard(limit: Int, myId: String, period: String, onResult: (Board?) -> Unit)

    /** Le pseudo est-il libre ? null = impossible de vérifier (hors ligne). */
    fun checkPseudo(name: String, myId: String, onResult: (Boolean?) -> Unit) = onResult(true)
}
