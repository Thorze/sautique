package com.sautique.game

import android.graphics.RectF

/** Types partagés par les différentes parties du jeu. */

internal enum class State { READY, CHARGING, JUMPING, FALLING, BONUS, GAME_OVER, LEADERBOARD, CUSTOMIZE, SETTINGS }
internal enum class FallAnim { SPIN, PANIC, PARACHUTE, CARTOON }
internal enum class JumpTrick { NONE, FRONTFLIP, BACKFLIP, PIROUETTE, TUCK }
internal enum class BonusType(val title: String, val hint: String) {
    TIMING("Curseur", "Touche quand le curseur est dans le vert"),
    MASH("Tape-tape", "Tape le plus vite possible !"),
    STARS("Attrape les étoiles", "Touche les étoiles avant qu'elles filent"),
    WHEEL("Roue de la fortune", "Touche pour arrêter la roue")
}
internal enum class WorldStatus { IDLE, LOADING, OK, ERROR, NOT_CONFIGURED }

/** Bouton dessiné sur le canvas. */
internal class Button(val rect: RectF, val action: () -> Unit)
internal class Popup(val text: String, var x: Float, var y: Float, var life: Float)
