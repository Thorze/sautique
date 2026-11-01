package com.sautique.game

import android.app.Activity
import android.content.Context
import com.google.android.gms.games.PlayGames
import com.google.android.gms.games.PlayGamesSdk
import com.google.android.gms.games.leaderboard.LeaderboardVariant
import com.sautique.game.OnlineScores.Board
import com.sautique.game.OnlineScores.Entry
import com.sautique.game.OnlineScores.SubmitResult

/** Version Play Store : classements Google Play Jeux (aucun serveur à toi). */
internal fun createOnline(context: Context): OnlineScores = PlayScores(context as Activity)

/**
 * Un seul classement « Meilleur score » dans la Play Console ; Google calcule lui-même
 * les variantes semaine et « de tous les temps ». Le nom affiché est celui du profil Play Jeux.
 * Les identifiants sont dans res/values/play_games.xml (version play).
 */
internal class PlayScores(private val activity: Activity) : OnlineScores {
    private val appId = activity.getString(R.string.game_services_project_id)
    private val boardId = activity.getString(R.string.leaderboard_best)

    override val configured = appId.isNotEmpty() && appId.all { it.isDigit() } && appId != "0" && boardId.startsWith("Cgk")
    override val usesPseudo = false
    override var signedIn = false
        private set
    override var playerName: String? = null
        private set
    private var playerId: String? = null

    init {
        if (configured) {
            PlayGamesSdk.initialize(activity)
            // Connexion automatique si le joueur l'a déjà acceptée
            PlayGames.getGamesSignInClient(activity).isAuthenticated.addOnCompleteListener { t ->
                onAuth(t.isSuccessful && t.result.isAuthenticated) {}
            }
        }
    }

    private fun onAuth(ok: Boolean, then: () -> Unit) {
        signedIn = ok
        if (!ok) {
            then()
            return
        }
        PlayGames.getPlayersClient(activity).currentPlayer.addOnCompleteListener { t ->
            if (t.isSuccessful) {
                playerName = t.result.displayName
                playerId = t.result.playerId
            }
            then()
        }
    }

    override fun signIn(onDone: () -> Unit) {
        if (!configured) return onDone()
        PlayGames.getGamesSignInClient(activity).signIn().addOnCompleteListener { t ->
            onAuth(t.isSuccessful && t.result.isAuthenticated, onDone)
        }
    }

    override fun submit(id: String, pseudo: String, score: Int, durationS: Float, jumps: Int, onDone: (SubmitResult) -> Unit) {
        if (score <= 0) return onDone(SubmitResult.OK)
        if (!configured || !signedIn) return onDone(SubmitResult.ERROR)
        PlayGames.getLeaderboardsClient(activity).submitScoreImmediate(boardId, score.toLong())
            .addOnCompleteListener { t -> onDone(if (t.isSuccessful) SubmitResult.OK else SubmitResult.ERROR) }
    }

    override fun fetchBoard(limit: Int, myId: String, period: String, onResult: (Board?) -> Unit) {
        if (!configured || !signedIn) return onResult(null)
        val span = if (period == "week") LeaderboardVariant.TIME_SPAN_WEEKLY else LeaderboardVariant.TIME_SPAN_ALL_TIME
        val client = PlayGames.getLeaderboardsClient(activity)
        // Google renvoie au plus 25 joueurs par appel
        client.loadTopScores(boardId, span, LeaderboardVariant.COLLECTION_PUBLIC, limit.coerceIn(1, 25), true)
            .addOnCompleteListener { t ->
                if (!t.isSuccessful) return@addOnCompleteListener onResult(null)
                val data = t.result.get()
                val top = ArrayList<Entry>()
                if (data != null) {
                    val buffer = data.scores
                    for (i in 0 until buffer.count) {
                        val s = buffer.get(i)
                        top += Entry(s.scoreHolderDisplayName, s.rawScore.toInt(), s.scoreHolder?.playerId == playerId)
                    }
                    data.release()
                }
                client.loadCurrentPlayerLeaderboardScore(boardId, span, LeaderboardVariant.COLLECTION_PUBLIC)
                    .addOnCompleteListener { m ->
                        val rank = if (m.isSuccessful) m.result.get()?.rank?.toInt()?.takeIf { it > 0 } else null
                        onResult(Board(top, rank))
                    }
            }
    }
}
