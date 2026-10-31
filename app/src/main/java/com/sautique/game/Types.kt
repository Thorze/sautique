package com.sautique.game

import android.graphics.RectF

/** Types partagés par les différentes parties du jeu. */

internal enum class State { READY, CHARGING, JUMPING, FALLING, BONUS, GAME_OVER, LEADERBOARD, CUSTOMIZE, SETTINGS, PAUSED, MISSIONS }
internal enum class FallAnim { SPIN, PANIC, PARACHUTE, CARTOON }
internal enum class JumpTrick { NONE, FRONTFLIP, BACKFLIP, PIROUETTE, TUCK, DOUBLE_FLIP }
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

/** Message affiché quelques secondes en haut de l'écran (mission réussie, nouveau décor…). */
internal class Banner(val text: String, var life: Float)

/** Particule d'effet visuel, en coordonnées écran « monde » (caméra ajoutée). */
internal class Particle(
    var x: Float, var y: Float, var vx: Float, var vy: Float,
    var life: Float, val maxLife: Float, val size: Float, val color: Int,
    val gravity: Float, val star: Boolean = false
)
