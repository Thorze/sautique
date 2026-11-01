package com.sautique.game

/**
 * Décor qui évolue avec le score : matin, journée, coucher de soleil, nuit, aurore, espace.
 * Les couleurs sont des entiers ARGB ; aucune dépendance Android (testé dans ThemeTest).
 */
object Theme {

    class Sky(val top: Int, val bottom: Int, val night: Float, val name: String)

    private class Stop(val score: Int, val top: Int, val bottom: Int, val night: Float, val name: String)

    private val stops = listOf(
        Stop(0, 0xFFFDE2C8.toInt(), 0xFFC9E4F5.toInt(), 0f, "morning"),
        Stop(20, 0xFF7CC6F2.toInt(), 0xFFE3F5FF.toInt(), 0f, "day"),
        Stop(40, 0xFFFF8E72.toInt(), 0xFFFFD6A5.toInt(), 0.1f, "sunset"),
        Stop(60, 0xFF0F1C3F.toInt(), 0xFF35507A.toInt(), 1f, "night"),
        Stop(80, 0xFF14203D.toInt(), 0xFF2E8B74.toInt(), 1f, "aurora"),
        Stop(100, 0xFF05010F.toInt(), 0xFF3A1460.toInt(), 1f, "space")
    )

    /** Nombre de points sur lesquels se fait la transition avant chaque palier. */
    private const val BLEND = 8f

    fun sky(score: Int): Sky {
        val s = score.toFloat()
        var i = stops.indexOfLast { it.score <= score }.coerceAtLeast(0)
        val cur = stops[i]
        val next = stops.getOrNull(i + 1) ?: return Sky(cur.top, cur.bottom, cur.night, cur.name)
        val start = next.score - BLEND
        if (s < start) return Sky(cur.top, cur.bottom, cur.night, cur.name)
        val t = ((s - start) / BLEND).coerceIn(0f, 1f)
        return Sky(lerp(cur.top, next.top, t), lerp(cur.bottom, next.bottom, t),
            cur.night + (next.night - cur.night) * t, if (t < 0.5f) cur.name else next.name)
    }

    /** Interpolation entre deux couleurs ARGB. */
    fun lerp(a: Int, b: Int, t: Float): Int {
        fun ch(shift: Int): Int {
            val ca = (a ushr shift) and 0xFF
            val cb = (b ushr shift) and 0xFF
            return (ca + (cb - ca) * t + 0.5f).toInt().coerceIn(0, 255) shl shift
        }
        return ch(24) or ch(16) or ch(8) or ch(0)
    }
}
