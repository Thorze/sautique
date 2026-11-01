package com.sautique.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Types de plateformes. */
internal enum class PlatformKind {
    NORMAL,
    /** Se fissure puis s'effondre peu après l'atterrissage. */
    CRUMBLY,
    /** Fait rebondir automatiquement jusqu'à la plateforme suivante. */
    TRAMPOLINE,
    /** Toute petite, mais rapporte des points en plus. */
    MINI,
    /** Avance et recule tant qu'on n'a pas atterri dessus. */
    MOVING
}

/**
 * Une plateforme : un bloc carré ou un cylindre, centré en (cx, cz), dessus à y = 0.
 * Une plateforme mobile se déplace le long de l'axe du saut ([moveAlongX]).
 */
internal class Platform(
    private val g: GameView,
    private val baseX: Float, private val baseZ: Float,
    val half: Float, val round: Boolean, color: Int,
    val kind: PlatformKind = PlatformKind.NORMAL,
    private val moveAlongX: Boolean = true
) {
    // --- Déplacement (plateforme mobile) ---
    private val moveAmp = 0.8f
    private var movePhase = Random.nextFloat() * 6.283f
    private var offset = 0f
    var frozen = false

    val cx get() = baseX + if (kind == PlatformKind.MOVING && moveAlongX) offset else 0f
    val cz get() = baseZ + if (kind == PlatformKind.MOVING && !moveAlongX) offset else 0f

    /** Pièce posée dessus : 0 = aucune, 1 = pièce, 5 = grosse pièce. */
    var coin = 0

    /** Cœur (vie bonus) posé dessus. */
    var heart = false

    // --- Effritement (plateforme fragile) ---
    val crumbly get() = kind == PlatformKind.CRUMBLY
    /** Temps écoulé depuis le début de l'effritement (négatif = pas commencé). */
    var crumbleT = -1f
    var crumbleDur = 2f
    /** Chute de la plateforme une fois effondrée (unités vers le bas). */
    var dropY = 0f
    var dropV = 0f
    val collapsed get() = dropY > 0f

    /** Ecrasement du trampoline quand on rebondit dessus (0..1). */
    var bounce = 0f

    /** Profondeur du point le plus éloigné (plus grand x + z) : sert au tri d'affichage. */
    val farDepth get() = if (round) cx + cz + half * 1.4142135f else cx + cz + 2f * half

    val baseColor = color
    private val k = g.k
    private val topPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = g.shade(color, 1.08f) }
    private val leftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = g.shade(color, 0.82f) }
    private val rightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = g.shade(color, 0.62f) }
    private val markPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = g.shade(color, 0.9f) }
    private val detailPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    // Formes pré-calculées en coordonnées locales (origine = centre du dessus projeté)
    private val topPath = Path()
    private val leftPath = Path()
    private val rightPath = Path()
    private val crackPath = Path()
    private val arrowPath = Path()
    private val topOval = RectF()
    private val sideRect = RectF()
    private val bottomOval = RectF()
    private val markOval = RectF()
    private val oval = RectF()

    init {
        val d = g.platformDepth
        if (round) {
            val rx = 1.2247449f * k * half
            val ry = 0.70710677f * k * half
            val dp = d * k
            topOval.set(-rx, -ry, rx, ry)
            sideRect.set(-rx, 0f, rx, dp)
            bottomOval.set(-rx, dp - ry, rx, dp + ry)
            leftPaint.shader = LinearGradient(
                -rx, 0f, rx, 0f,
                g.shade(color, 0.88f), g.shade(color, 0.55f), Shader.TileMode.CLAMP
            )
        } else {
            val a = half
            // A = coin proche (bas de l'écran), B = coin +x (droite), C = loin, D = coin +z (gauche)
            g.quad(topPath, -a, 0f, -a, a, 0f, -a, a, 0f, a, -a, 0f, a)
            g.quad(rightPath, -a, 0f, -a, a, 0f, -a, a, -d, -a, -a, -d, -a)
            g.quad(leftPath, -a, 0f, a, -a, 0f, -a, -a, -d, -a, -a, -d, a)
        }
        if (crumbly) {
            // Fissures en zigzag qui partent du centre
            repeat(4) { i ->
                val angle = (i + Random.nextFloat() * 0.6f) * (Math.PI.toFloat() / 2f)
                crackPath.moveTo(0f, 0f)
                var r = 0f
                var a2 = angle
                while (r < half * 0.8f) {
                    r += half * (0.18f + Random.nextFloat() * 0.12f)
                    a2 += (Random.nextFloat() - 0.5f) * 0.9f
                    val rr = min(r, half * 0.85f)
                    val dx = cos(a2) * rr
                    val dz = sin(a2) * rr
                    crackPath.lineTo(g.isoX(dx, dz), g.isoY(dx, 0f, dz))
                }
            }
        }
        if (kind == PlatformKind.MOVING) {
            // Double flèche dans le sens du déplacement
            val l = half * 0.6f
            fun p(t: Float, side: Float): Pair<Float, Float> {
                val wx = if (moveAlongX) t else side
                val wz = if (moveAlongX) side else t
                return g.isoX(wx, wz) to g.isoY(wx, 0f, wz)
            }
            val (x0, y0) = p(-l, 0f)
            val (x1, y1) = p(l, 0f)
            arrowPath.moveTo(x0, y0); arrowPath.lineTo(x1, y1)
            val h = half * 0.2f
            val (a1x, a1y) = p(l - h, h); val (a2x, a2y) = p(l - h, -h)
            arrowPath.moveTo(a1x, a1y); arrowPath.lineTo(x1, y1); arrowPath.lineTo(a2x, a2y)
            val (b1x, b1y) = p(-l + h, h); val (b2x, b2y) = p(-l + h, -h)
            arrowPath.moveTo(b1x, b1y); arrowPath.lineTo(x0, y0); arrowPath.lineTo(b2x, b2y)
        }
        val m = 0.13f
        markOval.set(-1.2247449f * k * m, -0.70710677f * k * m, 1.2247449f * k * m, 0.70710677f * k * m)
    }

    fun update(dt: Float) {
        if (kind == PlatformKind.MOVING && !frozen) {
            movePhase += dt * 1.6f
            offset = sin(movePhase) * moveAmp
        }
        if (bounce > 0f) bounce = (bounce - dt * 4f).coerceAtLeast(0f)
    }

    fun contains(x: Float, z: Float): Boolean {
        if (collapsed) return false
        val dx = x - cx
        val dz = z - cz
        return if (round) dx * dx + dz * dz <= half * half else abs(dx) <= half && abs(dz) <= half
    }

    /** Distance entre (x, z) et le bord de la plateforme. */
    fun edgeMargin(x: Float, z: Float): Float {
        val dx = x - cx
        val dz = z - cz
        return if (round) half - sqrt(dx * dx + dz * dz) else half - maxOf(abs(dx), abs(dz))
    }

    /** Ovale « posé à plat » sur le dessus, de rayon [r] (unités), décalé de [dy] pixels. */
    private fun flatOval(r: Float, dy: Float = 0f) {
        oval.set(-1.2247449f * k * r, -0.70710677f * k * r + dy, 1.2247449f * k * r, 0.70710677f * k * r + dy)
    }

    fun draw(canvas: Canvas) {
        canvas.save()
        // Tremblement de plus en plus fort pendant l'effritement
        var shake = 0f
        if (crumbleT >= 0f && !collapsed) {
            val f = (crumbleT / crumbleDur).coerceIn(0f, 1f)
            shake = sin(g.time * 55f) * k * (0.015f + 0.05f * f)
        }
        canvas.translate(g.sx(cx, cz) + shake, g.sy(cx, 0f, cz) + dropY * k)
        if (round) {
            canvas.drawOval(bottomOval, leftPaint)
            canvas.drawRect(sideRect, leftPaint)
            canvas.drawOval(topOval, topPaint)
        } else {
            canvas.drawPath(leftPath, leftPaint)
            canvas.drawPath(rightPath, rightPaint)
            canvas.drawPath(topPath, topPaint)
        }
        when (kind) {
            PlatformKind.TRAMPOLINE -> {
                // Toile du trampoline, qui s'enfonce quand on rebondit
                val sink = bounce * k * 0.12f
                flatOval(half * 0.72f)
                detailPaint.color = Color.parseColor("#E53950")
                canvas.drawOval(oval, detailPaint)
                flatOval(half * 0.6f, sink)
                detailPaint.color = Color.parseColor("#2E3A59")
                canvas.drawOval(oval, detailPaint)
                strokePaint.color = Color.argb(90, 255, 255, 255)
                strokePaint.strokeWidth = k * 0.02f
                flatOval(half * 0.4f, sink)
                canvas.drawOval(oval, strokePaint)
            }
            PlatformKind.MINI -> {
                g.textPaint.textSize = k * 0.32f
                canvas.drawText("+${Scoring.MINI_BONUS}", 0f, -(g.textPaint.descent() + g.textPaint.ascent()) / 2f, g.textPaint)
            }
            PlatformKind.MOVING -> {
                strokePaint.color = Color.argb(150, 255, 255, 255)
                strokePaint.strokeWidth = k * 0.04f
                canvas.drawPath(arrowPath, strokePaint)
                canvas.drawOval(markOval, markPaint)
            }
            else -> canvas.drawOval(markOval, markPaint)
        }
        if (crumbly) {
            val f = if (crumbleT < 0f) 0f else (crumbleT / crumbleDur).coerceIn(0f, 1f)
            strokePaint.color = Color.argb((110 + 145 * f).toInt(), 60, 45, 40)
            strokePaint.strokeWidth = k * 0.035f
            canvas.drawPath(crackPath, strokePaint)
        }
        if (heart) drawHeartPickup(canvas) else if (coin > 0) drawCoin(canvas)
        canvas.restore()
    }

    /** Cœur qui bat et flotte au-dessus du centre. */
    private fun drawHeartPickup(canvas: Canvas) {
        val beat = 1f + 0.12f * abs(sin(g.time * 5f))
        val cy = g.isoY(0f, 0.65f, 0f) + sin(g.time * 3f) * k * 0.05f
        flatOval(0.16f)
        detailPaint.color = Color.argb(50, 0, 0, 0)
        canvas.drawOval(oval, detailPaint)
        detailPaint.color = Color.argb(90, 255, 120, 160)
        canvas.drawCircle(0f, cy, k * 0.3f * beat, detailPaint)
        detailPaint.color = Color.parseColor("#FF4D8D")
        g.drawHeart(canvas, 0f, cy, k * 0.16f * beat, detailPaint)
    }

    /** Pièce qui tourne et flotte au-dessus du centre. */
    private fun drawCoin(canvas: Canvas) {
        val big = coin > 1
        val r = k * (if (big) 0.24f else 0.17f)
        val bob = sin(g.time * 3f) * k * 0.05f
        val cy = g.isoY(0f, 0.55f, 0f) + bob
        val spin = abs(cos(g.time * 3.2f)).coerceAtLeast(0.12f)
        // ombre sur la plateforme
        flatOval(0.14f)
        detailPaint.color = Color.argb(50, 0, 0, 0)
        canvas.drawOval(oval, detailPaint)
        oval.set(-r * spin, cy - r, r * spin, cy + r)
        detailPaint.color = Color.parseColor(if (big) "#FFB300" else "#FFC93C")
        canvas.drawOval(oval, detailPaint)
        strokePaint.color = Color.parseColor("#C98A00")
        strokePaint.strokeWidth = k * 0.025f
        canvas.drawOval(oval, strokePaint)
        if (big && spin > 0.5f) {
            g.textPaint.textSize = r * 1.1f
            canvas.drawText("5", 0f, cy - (g.textPaint.descent() + g.textPaint.ascent()) / 2f, g.textPaint)
        }
    }
}
