package com.sautique.game

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random

/**
 * Le jeu en vue isométrique : on maintient le doigt appuyé pour charger le saut,
 * on relâche pour sauter. Plus l'appui est long, plus le saut est long.
 * Chaque atterrissage réussi sur la nouvelle plateforme rapporte 1 point.
 *
 * Monde 3D : x et z au sol, y vers le haut. Les plateformes ont leur dessus à y = 0.
 * Projection isométrique :
 *   écranX = (x - z) * cos30 * k
 *   écranY = -(x + z) * sin30 * k - y * k
 * Avancer en +x monte vers la droite de l'écran, avancer en +z monte vers la gauche.
 */
class GameView(context: Context) : View(context) {

    private enum class State { READY, CHARGING, JUMPING, FALLING, GAME_OVER }

    private val cos30 = 0.8660254f
    private val sin30 = 0.5f

    // --- Réglages du gameplay (en unités du monde) ---
    private val maxChargeTime = 1.4f   // secondes d'appui pour la portée max
    private val maxReach = 5.6f        // portée max d'un saut
    private val platformDepth = 1.3f   // épaisseur des blocs
    private val playerHalf = 0.24f     // demi-largeur du cube personnage
    private val playerHeight = 0.55f

    private val palette = intArrayOf(
        Color.parseColor("#FF8A65"), Color.parseColor("#4DB6AC"), Color.parseColor("#FFD54F"),
        Color.parseColor("#9575CD"), Color.parseColor("#81C784"), Color.parseColor("#F06292"),
        Color.parseColor("#64B5F6"), Color.parseColor("#ECEFF1")
    )

    /** Une plateforme : un bloc carré ou un cylindre, centré en (cx, cz), dessus à y = 0. */
    private inner class Platform(
        val cx: Float, val cz: Float, val half: Float, val round: Boolean, color: Int
    ) {
        /** Profondeur du point le plus éloigné (plus grand x + z) : sert au tri d'affichage. */
        val farDepth = if (round) cx + cz + half * 1.4142135f else cx + cz + 2f * half

        private val topPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = shade(color, 1.08f) }
        private val leftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = shade(color, 0.82f) }
        private val rightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = shade(color, 0.62f) }
        private val markPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = shade(color, 0.9f) }

        // Formes pré-calculées en coordonnées locales (origine = centre du dessus projeté)
        private val topPath = Path()
        private val leftPath = Path()
        private val rightPath = Path()
        private val topOval = RectF()
        private val sideRect = RectF()
        private val bottomOval = RectF()
        private val markOval = RectF()

        init {
            val d = platformDepth
            if (round) {
                val rx = 1.2247449f * k * half
                val ry = 0.70710677f * k * half
                val dp = d * k
                topOval.set(-rx, -ry, rx, ry)
                sideRect.set(-rx, 0f, rx, dp)
                bottomOval.set(-rx, dp - ry, rx, dp + ry)
                leftPaint.shader = LinearGradient(
                    -rx, 0f, rx, 0f,
                    shade(color, 0.88f), shade(color, 0.55f), Shader.TileMode.CLAMP
                )
            } else {
                val a = half
                // A = coin proche (bas de l'écran), B = coin +x (droite), C = loin, D = coin +z (gauche)
                quad(topPath, -a, 0f, -a, a, 0f, -a, a, 0f, a, -a, 0f, a)
                // face visible côté z min (en bas à droite)
                quad(rightPath, -a, 0f, -a, a, 0f, -a, a, -d, -a, -a, -d, -a)
                // face visible côté x min (en bas à gauche)
                quad(leftPath, -a, 0f, a, -a, 0f, -a, -a, -d, -a, -a, -d, a)
            }
            val m = 0.13f
            markOval.set(-1.2247449f * k * m, -0.70710677f * k * m, 1.2247449f * k * m, 0.70710677f * k * m)
        }

        fun contains(x: Float, z: Float): Boolean {
            val dx = x - cx
            val dz = z - cz
            return if (round) dx * dx + dz * dz <= half * half else abs(dx) <= half && abs(dz) <= half
        }

        fun draw(canvas: Canvas) {
            canvas.save()
            canvas.translate(sx(cx, cz), sy(cx, 0f, cz))
            if (round) {
                canvas.drawOval(bottomOval, leftPaint)
                canvas.drawRect(sideRect, leftPaint)
                canvas.drawOval(topOval, topPaint)
            } else {
                canvas.drawPath(leftPath, leftPaint)
                canvas.drawPath(rightPath, rightPaint)
                canvas.drawPath(topPath, topPaint)
            }
            canvas.drawOval(markOval, markPaint)
            canvas.restore()
        }
    }

    private class Popup(val text: String, var x: Float, var y: Float, var life: Float)

    // --- Etat ---
    private var state = State.READY
    private var score = 0
    private val prefs = context.getSharedPreferences("sautique", Context.MODE_PRIVATE)
    private var best = prefs.getInt("best", 0)
    private var newBest = false

    private val platforms = ArrayList<Platform>()
    private val drawOrder = ArrayList<Platform>()
    private lateinit var current: Platform
    private lateinit var next: Platform

    // Personnage
    private var px = 0f
    private var pz = 0f
    private var py = 0f
    private var alongX = true          // direction du prochain saut : +x ou +z
    private var charge = 0f
    private var jumpT = 0f
    private var jumpDuration = 0.5f
    private var jumpFrom = 0f
    private var jumpDist = 0f
    private var jumpHeight = 0f
    private var fallVy = 0f
    private var landSquash = 0f
    private var gameOverTime = 0f
    private var time = 0f

    // Caméra (en coordonnées isométriques projetées, en pixels)
    private var camX = 0f
    private var camY = 0f
    private var camTargetX = 0f
    private var camTargetY = 0f

    private val popups = ArrayList<Popup>()

    // --- Dimensions ---
    private var w = 0f
    private var h = 0f
    private var k = 1f   // pixels par unité du monde
    private var lastFrameNs = 0L

    // --- Peintures ---
    private val bgPaint = Paint()
    private val playerTop = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFA270") }
    private val playerLeft = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF7A45") }
    private val playerRight = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#D95B2B") }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(60, 0, 0, 0) }
    private val eyeWhitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val pupilPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1B1B1B") }
    private val barBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(90, 0, 0, 0) }
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val overlayPaint = Paint().apply { color = Color.argb(150, 10, 15, 30) }
    private val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        setShadowLayer(8f, 0f, 4f, Color.argb(90, 0, 0, 0))
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        setShadowLayer(6f, 0f, 3f, Color.argb(90, 0, 0, 0))
    }
    private val rect = RectF()
    private val tmpPath = Path()

    // --- Projection ---
    private fun isoX(x: Float, z: Float) = (x - z) * cos30 * k
    private fun isoY(x: Float, y: Float, z: Float) = -(x + z) * sin30 * k - y * k
    private fun sx(x: Float, z: Float) = w * 0.5f - camX + isoX(x, z)
    private fun sy(x: Float, y: Float, z: Float) = h * 0.6f - camY + isoY(x, y, z)

    /** Remplit un chemin quadrilatère à partir de 4 points 3D (en coordonnées locales projetées). */
    private fun quad(
        path: Path,
        x1: Float, y1: Float, z1: Float, x2: Float, y2: Float, z2: Float,
        x3: Float, y3: Float, z3: Float, x4: Float, y4: Float, z4: Float
    ) {
        path.reset()
        path.moveTo(isoX(x1, z1), isoY(x1, y1, z1))
        path.lineTo(isoX(x2, z2), isoY(x2, y2, z2))
        path.lineTo(isoX(x3, z3), isoY(x3, y3, z3))
        path.lineTo(isoX(x4, z4), isoY(x4, y4, z4))
        path.close()
    }

    private fun shade(color: Int, f: Float): Int = Color.rgb(
        (Color.red(color) * f).toInt().coerceIn(0, 255),
        (Color.green(color) * f).toInt().coerceIn(0, 255),
        (Color.blue(color) * f).toInt().coerceIn(0, 255)
    )

    override fun onSizeChanged(width: Int, height: Int, oldw: Int, oldh: Int) {
        w = width.toFloat()
        h = height.toFloat()
        k = w / 9.5f
        bgPaint.shader = LinearGradient(
            0f, 0f, 0f, h,
            Color.parseColor("#FDE2C8"), Color.parseColor("#C9E4F5"),
            Shader.TileMode.CLAMP
        )
        scorePaint.textSize = w * 0.24f
        resetGame()
    }

    private fun resetGame() {
        platforms.clear()
        current = Platform(0f, 0f, 0.85f, false, Color.parseColor("#B0BEC5"))
        platforms.add(current)
        px = 0f
        pz = 0f
        py = 0f
        score = 0
        newBest = false
        charge = 0f
        landSquash = 0f
        popups.clear()
        spawnNext()
        updateCameraTarget()
        camX = camTargetX
        camY = camTargetY
        state = State.READY
    }

    /** Fait apparaître une nouvelle plateforme dans la direction +x ou +z. */
    private fun spawnNext() {
        val difficulty = min(score / 40f, 1f)
        val half = (0.85f - 0.4f * difficulty) * (0.8f + Random.nextFloat() * 0.35f)
        val round = Random.nextFloat() < 0.4f
        val minDist = current.half + half + 0.45f
        val dist = minDist + Random.nextFloat() * 2.3f
        alongX = Random.nextBoolean()
        val color = palette[Random.nextInt(palette.size)]
        // Le centre est aligné avec la position du personnage sur l'autre axe
        next = if (alongX) Platform(current.cx + dist, pz, half, round, color)
        else Platform(px, current.cz + dist, half, round, color)
        platforms.add(next)
        while (platforms.size > 6) platforms.removeAt(0)
    }

    private fun updateCameraTarget() {
        val mx = (current.cx + next.cx) / 2f
        val mz = (current.cz + next.cz) / 2f
        camTargetX = isoX(mx, mz)
        camTargetY = isoY(mx, 0f, mz)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                when (state) {
                    State.READY -> {
                        state = State.CHARGING
                        charge = 0f
                    }
                    State.GAME_OVER -> if (gameOverTime > 0.6f) resetGame()
                    else -> {}
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (state == State.CHARGING) startJump()
            }
        }
        return true
    }

    private fun startJump() {
        val ratio = charge / maxChargeTime
        jumpDist = ratio * maxReach
        jumpFrom = if (alongX) px else pz
        jumpDuration = 0.35f + 0.3f * ratio
        jumpHeight = 0.6f + jumpDist * 0.3f
        jumpT = 0f
        state = State.JUMPING
    }

    private fun update(dt: Float) {
        time += dt
        if (landSquash > 0f) landSquash = (landSquash - dt * 4f).coerceAtLeast(0f)

        // Caméra qui glisse doucement vers sa cible
        val c = min(1f, dt * 6f)
        camX += (camTargetX - camX) * c
        camY += (camTargetY - camY) * c

        when (state) {
            State.CHARGING -> charge = min(charge + dt, maxChargeTime)

            State.JUMPING -> {
                jumpT += dt
                val f = min(jumpT / jumpDuration, 1f)
                val pos = jumpFrom + jumpDist * f
                if (alongX) px = pos else pz = pos
                py = 4f * jumpHeight * f * (1f - f)
                if (f >= 1f) land()
            }

            State.FALLING -> {
                fallVy += 30f * dt
                py -= fallVy * dt
                if (sy(px, py + playerHeight, pz) > h) {
                    state = State.GAME_OVER
                    gameOverTime = 0f
                    if (score > best) {
                        best = score
                        newBest = true
                        prefs.edit().putInt("best", best).apply()
                    }
                }
            }

            State.GAME_OVER -> gameOverTime += dt
            State.READY -> {}
        }

        val it = popups.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.life -= dt
            p.y -= w * 0.15f * dt
            if (p.life <= 0f) it.remove()
        }
    }

    private fun land() {
        py = 0f
        when {
            next.contains(px, pz) -> {
                score++
                landSquash = 1f
                val perfect = abs(px - next.cx) < 0.13f && abs(pz - next.cz) < 0.13f
                popups.add(
                    Popup(
                        if (perfect) "Parfait ! +1" else "+1",
                        sx(px, pz) + camX, sy(px, playerHeight * 2.2f, pz) + camY, 0.8f
                    )
                )
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                current = next
                spawnNext()
                updateCameraTarget()
                state = State.READY
            }
            current.contains(px, pz) -> {
                // Saut trop court mais on reste sur sa plateforme : pas de point.
                landSquash = 1f
                state = State.READY
            }
            else -> {
                fallVy = 0f
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                state = State.FALLING
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        val now = System.nanoTime()
        val dt = if (lastFrameNs == 0L) 0f else ((now - lastFrameNs) / 1_000_000_000f).coerceIn(0f, 0.05f)
        lastFrameNs = now
        update(dt)

        canvas.drawRect(0f, 0f, w, h, bgPaint)

        // Affichage du plus lointain au plus proche, le personnage inséré au bon endroit
        drawOrder.clear()
        drawOrder.addAll(platforms)
        drawOrder.sortByDescending { it.farDepth }
        val playerDepth = px + pz
        var playerDrawn = false
        for (p in drawOrder) {
            if (!playerDrawn && playerDepth > p.farDepth) {
                drawPlayer(canvas)
                playerDrawn = true
            }
            p.draw(canvas)
        }
        if (!playerDrawn) drawPlayer(canvas)

        // Jauge de charge
        if (state == State.CHARGING) {
            val ratio = charge / maxChargeTime
            val bw = w * 0.16f
            val bh = w * 0.022f
            val cx = sx(px, pz)
            val top = sy(px, playerHeight, pz) - w * 0.09f
            rect.set(cx - bw / 2, top, cx + bw / 2, top + bh)
            canvas.drawRoundRect(rect, bh / 2, bh / 2, barBgPaint)
            barPaint.color = Color.rgb((80 + 175 * ratio).toInt(), (220 - 150 * ratio).toInt(), 90)
            rect.set(cx - bw / 2, top, cx - bw / 2 + bw * ratio, top + bh)
            canvas.drawRoundRect(rect, bh / 2, bh / 2, barPaint)
        }

        // Popups "+1" (stockés en coordonnées sans caméra)
        textPaint.textSize = w * 0.06f
        for (p in popups) {
            textPaint.alpha = (255 * (p.life / 0.8f)).toInt().coerceIn(0, 255)
            canvas.drawText(p.text, p.x - camX, p.y - camY, textPaint)
        }
        textPaint.alpha = 255

        // Score en haut et en gros
        canvas.drawText(score.toString(), w / 2f, h * 0.17f, scorePaint)
        textPaint.textSize = w * 0.045f
        canvas.drawText("Record : $best", w / 2f, h * 0.17f + w * 0.08f, textPaint)

        if (state == State.READY && score == 0) {
            textPaint.textSize = w * 0.05f
            canvas.drawText("Maintiens appuyé pour sauter", w / 2f, h * 0.88f, textPaint)
            textPaint.textSize = w * 0.04f
            canvas.drawText("plus longtemps = plus loin", w / 2f, h * 0.88f + w * 0.07f, textPaint)
        }

        if (state == State.GAME_OVER) drawGameOver(canvas)

        postInvalidateOnAnimation()
    }

    private fun drawPlayer(canvas: Canvas) {
        // Ombre sur la plateforme quand le personnage est au-dessus
        if (state != State.FALLING && (current.contains(px, pz) || next.contains(px, pz))) {
            val s = (1f - py / 4f).coerceIn(0.4f, 1f)
            val r = playerHalf * 1.5f * s
            val cx = sx(px, pz)
            val cy = sy(px, 0f, pz)
            rect.set(cx - 1.2247449f * k * r, cy - 0.70710677f * k * r, cx + 1.2247449f * k * r, cy + 0.70710677f * k * r)
            canvas.drawOval(rect, shadowPaint)
        }

        // Ecrasement pendant la charge, rebond à l'atterrissage
        var scaleW = 1f
        var scaleH = 1f
        if (state == State.CHARGING) {
            val r = charge / maxChargeTime
            scaleH = 1f - 0.4f * r
            scaleW = 1f + 0.18f * r
        } else if (landSquash > 0f) {
            scaleH = 1f - 0.22f * landSquash
            scaleW = 1f + 0.1f * landSquash
        } else if (state == State.JUMPING) {
            scaleH = 1.1f
            scaleW = 0.95f
        }
        val a = playerHalf * scaleW
        val hgt = playerHeight * scaleH
        val y0 = py
        val y1 = py + hgt

        canvas.save()
        canvas.translate(sx(px, pz), sy(px, 0f, pz))
        // face gauche (x min), face droite (z min), dessus
        quad(tmpPath, -a, y1, a, -a, y1, -a, -a, y0, -a, -a, y0, a)
        canvas.drawPath(tmpPath, playerLeft)
        quad(tmpPath, -a, y1, -a, a, y1, -a, a, y0, -a, -a, y0, -a)
        canvas.drawPath(tmpPath, playerRight)
        quad(tmpPath, -a, y1, -a, a, y1, -a, a, y1, a, -a, y1, a)
        canvas.drawPath(tmpPath, playerTop)

        // Yeux sur la face visible, du côté où il va sauter
        val eyeY = y0 + hgt * 0.68f
        val eyeR = a * 0.24f * k
        val closed = (time % 3.2f) > 3.05f || state == State.GAME_OVER
        for (t in floatArrayOf(0.25f, 0.68f)) {
            val ex: Float
            val ey: Float
            if (alongX) {
                // face z min, vers +x
                val x = -a + 2f * a * t
                ex = isoX(x, -a); ey = isoY(x, eyeY, -a)
            } else {
                // face x min, vers +z
                val z = -a + 2f * a * t
                ex = isoX(-a, z); ey = isoY(-a, eyeY, z)
            }
            if (closed) {
                canvas.drawRect(ex - eyeR, ey - eyeR * 0.15f, ex + eyeR, ey + eyeR * 0.15f, pupilPaint)
            } else {
                canvas.drawCircle(ex, ey, eyeR, eyeWhitePaint)
                val dir = if (alongX) 1f else -1f
                canvas.drawCircle(ex + dir * eyeR * 0.35f, ey - eyeR * 0.15f, eyeR * 0.5f, pupilPaint)
            }
        }
        canvas.restore()
    }

    private fun drawGameOver(canvas: Canvas) {
        canvas.drawRect(0f, 0f, w, h, overlayPaint)
        textPaint.textSize = w * 0.12f
        canvas.drawText("Perdu !", w / 2f, h * 0.38f, textPaint)
        textPaint.textSize = w * 0.07f
        canvas.drawText("Score : $score", w / 2f, h * 0.38f + w * 0.14f, textPaint)
        canvas.drawText(
            if (newBest) "Nouveau record !" else "Record : $best",
            w / 2f, h * 0.38f + w * 0.24f, textPaint
        )
        if (gameOverTime > 0.6f) {
            textPaint.textSize = w * 0.05f
            canvas.drawText("Touchez pour rejouer", w / 2f, h * 0.38f + w * 0.40f, textPaint)
        }
    }
}
