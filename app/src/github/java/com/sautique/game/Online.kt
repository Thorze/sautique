package com.sautique.game

import android.content.Context

/** Version GitHub : classement sur ton serveur (dossier server/). */
internal fun createOnline(context: Context): OnlineScores = ScoreServer(BuildConfig.SCORES_URL, BuildConfig.SCORES_KEY)
