package com.sautique.game

/** Règles de points, sans dépendance Android (testées dans ScoringTest). */
object Scoring {
    /** Points d'un saut réussi : 1, puis +2, +3, +4 et +5 max à partir du 3e parfait d'affilée. */
    fun perfectPoints(streak: Int) = if (streak >= 3) minOf(streak - 1, 5) else 1

    /** Bonus Curseur : de 0 point sur les bords (position ±1) à 10 points pile au centre (0). */
    fun cursorPoints(pos: Float): Int {
        val f = (1f - kotlin.math.abs(pos)).coerceIn(0f, 1f)
        return Math.round(f * f * 10f)
    }

    /** Points supplémentaires pour l'atterrissage sur une mini-plateforme. */
    const val MINI_BONUS = 2

    /** v1.4.2 -> 10402. Doit rester identique au calcul du workflow de release. */
    fun versionCodeOf(tag: String): Int? {
        val m = Regex("""^v?(\d+)(?:\.(\d+))?(?:\.(\d+))?""").find(tag.trim()) ?: return null
        val (a, b, c) = m.destructured
        return a.toInt() * 10000 + (b.toIntOrNull() ?: 0) * 100 + (c.toIntOrNull() ?: 0)
    }
}
