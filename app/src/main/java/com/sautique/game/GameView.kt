package com.sautique.game

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.min
import kotlin.random.Random

/**
 * Le jeu : on maintient le doigt appuyé pour charger le saut, on relâche
 * pour sauter. Plus l'appui est long, plus le saut est long.
 * Chaque atterrissage réussi sur la nouvelle plateforme rapporte 1 point.
 */
class GameView(context: Context) : View(context) {

    private enum class State { READY, CHARGING, JUMPING, FALLING, SCROLLING, GAME_OVER }

    /** Plateforme en coordonnées "monde" (x = bord gauche). */
    private class Platform(val x: Float, val width: Float) {
        val right get() = x + width
        val center get() = x + width / 2f
        fun contains(px: Float) = px >= x && px <= right
    }

    private class Popup(val text: String, var x: Float, var y: Float, var life: Float)

    // --- Réglages du gameplay ---
    private val maxChargeTime = 1.4f      // secondes d'appui pour atteindre la portée max
    private val maxReachRatio = 0.85f     // portée max en fraction de la largeur d'écran
    private val scrollDuration = 0.35f
    private val startPlatformScreenX = 0.08f

    // --- Etat ---
    private var state = State.READY
    private var score = 0
    private val prefs = context.getSharedPreferences("sautique", Context.MODE_PRIVATE)
    private var best = prefs.getInt("best", 0)
    private var newBest = false

    private val platforms = ArrayList<Platform>()
    private var cameraX = 0f
    private var cameraFrom = 0f
    private var cameraTo = 0f
    private var scrollT = 0f

    // Personnage : px = centre des pieds (monde), py = hauteur au-dessus des plateformes
    private var px = 0f
    private var py = 0f
    private var charge = 0f
    private var jumpT = 0f
    private var jumpDuration = 0.5f
    private var jumpFromX = 0f
    private var jumpDist = 0f
    private var jumpHeight = 0f
    private var fallVy = 0f
    private var landSquash = 0f
    private var gameOverTime = 0f
    private var time = 0f

    private val popups = ArrayList<Popup>()

    // --- Dimensions ---
    private var w = 0f
    private var h = 0f
    private var groundY = 0f
    private var playerW = 0f
    private var playerH = 0f

    private var lastFrameNs = 0L

    // --- Peintures ---
    private val bgPaint = Paint()
    private val platformPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2E3A59") }
    private val platformTopPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#46557D") }
    private val targetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF4D6D") }
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF7A45") }
    private val eyeWhitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val pupilPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1B1B1B") }
    private val cloudPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(170, 255, 255, 255) }
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

    // Nuages décoratifs (position en coordonnées écran, parallaxe)
    private val clouds = List(6) { floatArrayOf(Random.nextFloat(), 0.05f + Random.nextFloat() * 0.4f, 0.6f + Random.nextFloat() * 0.8f) }

    override fun onSizeChanged(width: Int, height: Int, oldw: Int, oldh: Int) {
        w = width.toFloat()
        h = height.toFloat()
        groundY = h * 0.62f
        playerW = w * 0.075f
        playerH = w * 0.095f
        bgPaint.shader = LinearGradient(
            0f, 0f, 0f, h,
            Color.parseColor("#4FB3E8"), Color.parseColor("#BDE6FA"),
            Shader.TileMode.CLAMP
        )
        scorePaint.textSize = w * 0.24f
        resetGame()
    }

    private fun resetGame() {
        platforms.clear()
        val startWidth = w * 0.22f
        platforms.add(Platform(w * startPlatformScreenX, startWidth))
        px = platforms[0].right - playerW * 0.6f
        py = 0f
        cameraX = 0f
        score = 0
        newBest = false
        charge = 0f
        landSquash = 0f
        popups.clear()
        spawnNext()
        state = State.READY
    }

    /** Fait apparaître une nouvelle plateforme à droite de la dernière. */
    private fun spawnNext() {
        val last = platforms.last()
        val difficulty = min(score / 40f, 1f)
        val baseWidth = w * (0.20f - 0.11f * difficulty)
        val width = baseWidth * (0.75f + Random.nextFloat() * 0.5f)
        val minGap = w * 0.06f
        val maxGap = w * 0.58f - width
        val gap = minGap + Random.nextFloat() * (maxGap - minGap)
        platforms.add(Platform(last.right + gap, width))
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
        jumpDist = ratio * w * maxReachRatio
        jumpFromX = px
        jumpDuration = 0.35f + 0.3f * ratio
        jumpHeight = w * 0.06f + jumpDist * 0.35f
        jumpT = 0f
        state = State.JUMPING
    }

    private fun update(dt: Float) {
        time += dt
        if (landSquash > 0f) landSquash = (landSquash - dt * 4f).coerceAtLeast(0f)

        when (state) {
            State.CHARGING -> charge = min(charge + dt, maxChargeTime)

            State.JUMPING -> {
                jumpT += dt
                val f = min(jumpT / jumpDuration, 1f)
                px = jumpFromX + jumpDist * f
                py = 4f * jumpHeight * f * (1f - f)
                if (f >= 1f) land()
            }

            State.FALLING -> {
                fallVy += w * 3.5f * dt
                py -= fallVy * dt
                if (groundY - py - playerH > h) {
                    state = State.GAME_OVER
                    gameOverTime = 0f
                    if (score > best) {
                        best = score
                        newBest = true
                        prefs.edit().putInt("best", best).apply()
                    }
                }
            }

            State.SCROLLING -> {
                scrollT += dt / scrollDuration
                val t = min(scrollT, 1f)
                val eased = 1f - (1f - t) * (1f - t) * (1f - t)
                cameraX = cameraFrom + (cameraTo - cameraFrom) * eased
                if (t >= 1f) {
                    platforms.removeAt(0)
                    state = State.READY
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
        val current = platforms[0]
        val next = platforms[1]
        when {
            next.contains(px) -> {
                score++
                landSquash = 1f
                val perfect = kotlin.math.abs(px - next.center) < w * 0.012f
                popups.add(Popup(if (perfect) "Parfait ! +1" else "+1", px, groundY - playerH * 1.6f, 0.8f))
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                spawnNext()
                cameraFrom = cameraX
                cameraTo = next.x - w * startPlatformScreenX
                scrollT = 0f
                state = State.SCROLLING
            }
            current.contains(px) -> {
                // Saut trop court mais on reste sur la plateforme : pas de point, on recommence.
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

        // Fond
        canvas.drawRect(0f, 0f, w, h, bgPaint)
        drawClouds(canvas)

        // Plateformes
        for (p in platforms) {
            val left = p.x - cameraX
            val right = p.right - cameraX
            if (right < 0f || left > w) continue
            rect.set(left, groundY, right, h)
            canvas.drawRect(rect, platformPaint)
            canvas.drawRect(left, groundY, right, groundY + w * 0.018f, platformTopPaint)
            // petite cible au centre
            val c = p.center - cameraX
            canvas.drawRect(c - w * 0.012f, groundY, c + w * 0.012f, groundY + w * 0.008f, targetPaint)
        }

        drawPlayer(canvas)

        // Jauge de charge
        if (state == State.CHARGING) {
            val ratio = charge / maxChargeTime
            val bw = w * 0.16f
            val bh = w * 0.022f
            val cx = px - cameraX
            val top = groundY - playerH - w * 0.07f
            rect.set(cx - bw / 2, top, cx + bw / 2, top + bh)
            canvas.drawRoundRect(rect, bh / 2, bh / 2, barBgPaint)
            barPaint.color = Color.rgb((80 + 175 * ratio).toInt(), (220 - 150 * ratio).toInt(), 90)
            rect.set(cx - bw / 2, top, cx - bw / 2 + bw * ratio, top + bh)
            canvas.drawRoundRect(rect, bh / 2, bh / 2, barPaint)
        }

        // Popups "+1"
        for (p in popups) {
            textPaint.textSize = w * 0.06f
            textPaint.alpha = (255 * (p.life / 0.8f)).toInt().coerceIn(0, 255)
            canvas.drawText(p.text, p.x - cameraX, p.y, textPaint)
        }
        textPaint.alpha = 255

        // Score en haut et en gros
        canvas.drawText(score.toString(), w / 2f, h * 0.17f, scorePaint)
        textPaint.textSize = w * 0.045f
        canvas.drawText("Record : $best", w / 2f, h * 0.17f + w * 0.08f, textPaint)

        if (state == State.READY && score == 0) {
            textPaint.textSize = w * 0.05f
            canvas.drawText("Maintiens appuyé pour sauter", w / 2f, h * 0.82f, textPaint)
            textPaint.textSize = w * 0.04f
            canvas.drawText("plus longtemps = plus loin", w / 2f, h * 0.82f + w * 0.07f, textPaint)
        }

        if (state == State.GAME_OVER) drawGameOver(canvas)

        postInvalidateOnAnimation()
    }

    private fun drawClouds(canvas: Canvas) {
        for (c in clouds) {
            val span = w * 1.4f
            var x = (c[0] * span - cameraX * 0.25f * c[2]) % span
            if (x < 0) x += span
            x -= w * 0.2f
            val y = h * c[1]
            val r = w * 0.05f * c[2]
            canvas.drawCircle(x, y, r, cloudPaint)
            canvas.drawCircle(x + r * 0.9f, y + r * 0.2f, r * 0.8f, cloudPaint)
            canvas.drawCircle(x - r * 0.9f, y + r * 0.25f, r * 0.7f, cloudPaint)
        }
    }

    private fun drawPlayer(canvas: Canvas) {
        // Ecrasement pendant la charge, rebond à l'atterrissage
        var sx = 1f
        var sy = 1f
        if (state == State.CHARGING) {
            val r = charge / maxChargeTime
            sy = 1f - 0.35f * r
            sx = 1f + 0.2f * r
        } else if (landSquash > 0f) {
            sy = 1f - 0.2f * landSquash
            sx = 1f + 0.12f * landSquash
        } else if (state == State.JUMPING) {
            sy = 1.08f
            sx = 0.94f
        }
        val bw = playerW * sx
        val bh = playerH * sy
        val cx = px - cameraX
        val bottom = groundY - py
        rect.set(cx - bw / 2, bottom - bh, cx + bw / 2, bottom)
        canvas.drawRoundRect(rect, bw * 0.28f, bw * 0.28f, bodyPaint)

        // Yeux (clignement de temps en temps)
        val blink = (time % 3.2f) > 3.05f
        val eyeY = bottom - bh * 0.66f
        val eyeR = bw * 0.16f
        for (ex in floatArrayOf(cx + bw * 0.05f, cx + bw * 0.32f)) {
            if (blink || state == State.GAME_OVER) {
                canvas.drawRect(ex - eyeR, eyeY - eyeR * 0.15f, ex + eyeR, eyeY + eyeR * 0.15f, pupilPaint)
            } else {
                canvas.drawCircle(ex, eyeY, eyeR, eyeWhitePaint)
                canvas.drawCircle(ex + eyeR * 0.35f, eyeY, eyeR * 0.5f, pupilPaint)
            }
        }
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
