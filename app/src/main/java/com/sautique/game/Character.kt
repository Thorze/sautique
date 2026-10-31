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
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Hauteur du corps selon la forme choisie. */
internal fun GameView.shapeHeight(scaleH: Float) = scaleH * when (lookShape) {
    "tour" -> 0.82f
    "boule" -> 0.58f
    else -> playerHeight
}

internal fun GameView.drawPlayer(canvas: Canvas) {
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
        if (jumpTrick == JumpTrick.TUCK) {
            // Se met en boule au sommet du saut
            val f = (jumpT / jumpDuration).coerceIn(0f, 1f)
            val s = 1f - 0.4f * sin(f * Math.PI.toFloat())
            scaleH = s
            scaleW = 1f + 0.15f * (1f - s)
        } else {
            scaleH = 1.1f
            scaleW = 0.95f
        }
    }

    val scared = scareT > 0f
    var ox = sx(px, pz)
    val oy = sy(px, 0f, pz)
    if (scared) ox += sin(time * 75f) * k * 0.035f * (scareT / scareDuration).coerceAtLeast(0.3f)

    // Animation de chute
    val falling = state == State.FALLING
    var rotation = 0f
    var panic = false
    var front = false
    val chute = falling && fallAnim == FallAnim.PARACHUTE && fallT > 0.25f && fallT < 1.9f
    if (falling) when (fallAnim) {
        FallAnim.SPIN -> rotation = fallT * 620f * (if (alongX) 1f else -1f)
        FallAnim.PANIC -> {
            panic = true
            ox += sin(time * 70f) * k * 0.05f
        }
        FallAnim.PARACHUTE -> if (chute) rotation = sin(fallT * 5f) * 10f
        FallAnim.CARTOON -> front = fallT < 1.1f
    }
    val bodyH = shapeHeight(scaleH)

    // Figures pendant le saut
    var flipScaleX = 1f
    if (state == State.JUMPING && jumpTrick != JumpTrick.NONE) {
        val f = (jumpT / jumpDuration).coerceIn(0f, 1f)
        val e = f * f * (3f - 2f * f)
        val dirSign = if (alongX) 1f else -1f
        when (jumpTrick) {
            JumpTrick.FRONTFLIP -> rotation = 360f * e * dirSign
            JumpTrick.BACKFLIP -> rotation = -360f * e * dirSign
            JumpTrick.PIROUETTE -> flipScaleX = kotlin.math.cos(e * 2f * Math.PI.toFloat())
            else -> {}
        }
    }

    canvas.save()
    val pivotY = oy + isoY(0f, py + bodyH / 2f, 0f)
    if (rotation != 0f) canvas.rotate(rotation, ox, pivotY)
    if (flipScaleX != 1f) canvas.scale(if (abs(flipScaleX) < 0.05f) 0.05f else flipScaleX, 1f, ox, pivotY)
    if (chute) drawParachute(canvas, ox, oy + isoY(0f, py + bodyH, 0f))
    drawCharacter(canvas, ox, oy, py, scaleW, scaleH, alongX, scared || panic, front)
    canvas.restore()

    // Bulles au-dessus de la tête
    val headY = oy + isoY(0f, py + bodyH + 0.5f, 0f)
    if (scared) {
        val elapsed = scareDuration - scareT
        val r = k * 0.2f * min(1f, elapsed / 0.15f)
        canvas.drawCircle(ox, headY, r, bubblePaint)
        bangPaint.textSize = r * 1.5f
        canvas.drawText("!", ox, headY - (bangPaint.descent() + bangPaint.ascent()) / 2f, bangPaint)
    }
    if (falling) when (fallAnim) {
        FallAnim.PANIC -> drawBubble(canvas, ox, headY, "Aaaah !")
        FallAnim.SPIN -> if (fallT < 0.8f) drawBubble(canvas, ox, headY - k * 0.3f, "Wouaah !")
        FallAnim.CARTOON -> when {
            fallT in 0.2f..0.8f -> drawBubble(canvas, ox, headY, "…")
            fallT in 0.8f..1.5f -> drawBubble(canvas, ox, headY, "Oups !")
        }
        FallAnim.PARACHUTE -> if (fallT in 1.9f..2.6f) drawBubble(canvas, ox, headY, "Crac !")
    }
}

/** Petit parachute rayé au-dessus du personnage. */
internal fun GameView.drawParachute(canvas: Canvas, cx: Float, topY: Float) {
    val half = k * 0.6f
    val canopyBase = topY - k * 0.55f
    accStroke.color = Color.parseColor("#2E3A59")
    accStroke.strokeWidth = k * 0.015f
    canvas.drawLine(cx - half, canopyBase, cx - k * 0.08f, topY, accStroke)
    canvas.drawLine(cx, canopyBase, cx, topY, accStroke)
    canvas.drawLine(cx + half, canopyBase, cx + k * 0.08f, topY, accStroke)
    rect.set(cx - half, canopyBase - half * 0.8f, cx + half, canopyBase + half * 0.8f)
    accPaint.color = Color.parseColor("#E53950")
    canvas.drawArc(rect, 180f, 180f, true, accPaint)
    accPaint.color = Color.WHITE
    canvas.drawArc(rect, 216f, 36f, true, accPaint)
    canvas.drawArc(rect, 288f, 36f, true, accPaint)
}

/** Bulle de BD avec un petit texte. */
internal fun GameView.drawBubble(canvas: Canvas, x: Float, bottomY: Float, text: String) {
    bubbleTextPaint.textSize = w * 0.04f
    val tw = bubbleTextPaint.measureText(text)
    val pad = w * 0.025f
    val bh = w * 0.07f
    val y = bottomY - w * 0.02f
    rect.set(x - tw / 2f - pad, y - bh, x + tw / 2f + pad, y)
    tmpPath.reset()
    tmpPath.moveTo(x - w * 0.015f, y - 1f)
    tmpPath.lineTo(x + w * 0.015f, y - 1f)
    tmpPath.lineTo(x, y + w * 0.025f)
    tmpPath.close()
    bubbleStroke.strokeWidth = w * 0.005f
    canvas.drawRoundRect(rect, bh / 2f, bh / 2f, bubblePaint)
    canvas.drawRoundRect(rect, bh / 2f, bh / 2f, bubbleStroke)
    canvas.drawPath(tmpPath, bubblePaint)
    canvas.drawText(text, x, rect.centerY() - (bubbleTextPaint.descent() + bubbleTextPaint.ascent()) / 2f, bubbleTextPaint)
}

internal fun GameView.drawCharacter(
    canvas: Canvas, ox: Float, oy: Float, y0: Float, scaleW: Float, scaleH: Float,
    dirX: Boolean, scared: Boolean, front: Boolean
) {
    canvas.save()
    canvas.translate(ox, oy)
    val round = lookShape == "cylindre" || lookShape == "boule"
    val a = scaleW * when (lookShape) {
        "tour" -> 0.18f
        "cylindre" -> 0.25f
        "boule" -> 0.29f
        else -> playerHalf
    }
    val hgt = shapeHeight(scaleH)
    val y1 = y0 + hgt

    // --- Corps ---
    when (lookShape) {
        "cylindre" -> {
            val rx = 1.2247449f * k * a
            val ry = 0.70710677f * k * a
            val yb = isoY(0f, y0, 0f)
            val yt = isoY(0f, y1, 0f)
            rect.set(-rx, yb - ry, rx, yb + ry)
            canvas.drawOval(rect, playerLeft)
            canvas.drawRect(-rx, yt, rx, yb, playerLeft)
            canvas.save()
            canvas.clipRect(0f, yt - ry * 2f, rx, yb + ry)
            canvas.drawOval(rect, playerRight)
            canvas.drawRect(-rx, yt, rx, yb, playerRight)
            canvas.restore()
            rect.set(-rx, yt - ry, rx, yt + ry)
            canvas.drawOval(rect, playerTop)
        }
        "boule" -> {
            val cy = isoY(0f, y0 + hgt / 2f, 0f)
            val rx = a * k * 1.15f
            val ryv = hgt / 2f * k
            rect.set(-rx, cy - ryv, rx, cy + ryv)
            canvas.drawOval(rect, playerRight)
            rect.set(-rx * 0.98f, cy - ryv, rx * 0.84f, cy + ryv * 0.86f)
            canvas.drawOval(rect, playerLeft)
            canvas.drawCircle(-rx * 0.38f, cy - ryv * 0.45f, rx * 0.2f, playerTop)
        }
        else -> {
            // face gauche (x min), face droite (z min), dessus
            quad(tmpPath, -a, y1, a, -a, y1, -a, -a, y0, -a, -a, y0, a)
            canvas.drawPath(tmpPath, playerLeft)
            quad(tmpPath, -a, y1, -a, a, y1, -a, a, y0, -a, -a, y0, -a)
            canvas.drawPath(tmpPath, playerRight)
            quad(tmpPath, -a, y1, -a, a, y1, -a, a, y1, a, -a, y1, a)
            canvas.drawPath(tmpPath, playerTop)
        }
    }

    // Direction du regard à l'écran (+x : en haut à droite, +z : en haut à gauche, caméra : vers le bas)
    val fdx = if (front) 0f else if (dirX) 0.866f else -0.866f
    val fdy = if (front) 0.5f else -0.5f

    // --- Chaussures ---
    if (lookShoes != Skins.NONE) {
        val fa = if (lookShape == "boule") a * 0.6f else a
        for (i in 0..1) {
            val wx = if (i == 0) -fa * 0.85f else fa * 0.15f
            val wz = if (i == 0) fa * 0.15f else -fa * 0.85f
            val sx0 = isoX(wx, wz) + fdx * k * 0.05f
            val sy0 = isoY(wx, y0, wz)
            val sw = k * 0.12f
            val sh = k * 0.06f
            if (lookShoes == "bottes") {
                accPaint.color = Color.parseColor("#7B4A2A")
                canvas.drawRect(sx0 - sw * 0.6f, sy0 - k * 0.15f, sx0 + sw * 0.6f, sy0, accPaint)
                rect.set(sx0 - sw, sy0 - sh, sx0 + sw, sy0 + sh)
                canvas.drawOval(rect, accPaint)
            } else {
                accPaint.color = Color.WHITE
                rect.set(sx0 - sw, sy0 - sh, sx0 + sw, sy0 + sh)
                canvas.drawOval(rect, accPaint)
                accPaint.color = Color.parseColor("#E53950")
                canvas.drawRect(sx0 - sw * 0.5f, sy0 - sh * 0.35f, sx0 + sw * 0.5f, sy0 + sh * 0.1f, accPaint)
            }
        }
    }

    // --- Yeux ---
    val eyeY = y0 + hgt * (if (lookShape == "boule") 0.6f else 0.68f)
    val eyeR = k * 0.058f * (if (scared) 1.25f else 1f)
    val ex = FloatArray(2)
    val ey = FloatArray(2)
    for (i in 0..1) {
        val wx: Float
        val wz: Float
        if (!round) {
            if (front) {
                wx = if (i == 0) -a else -a * 0.45f
                wz = if (i == 0) -a * 0.45f else -a
            } else {
                val t = if (i == 0) 0.25f else 0.68f
                wx = if (dirX) -a + 2f * a * t else -a
                wz = if (dirX) -a else -a + 2f * a * t
            }
        } else {
            val rr = if (lookShape == "boule") a * 0.9f else a
            val th = when {
                front -> if (i == 0) -2.0f else -2.75f
                dirX -> if (i == 0) -1.9f else -1.3f
                else -> if (i == 0) 3.47f else 2.87f
            }
            wx = rr * kotlin.math.cos(th)
            wz = rr * sin(th)
        }
        ex[i] = isoX(wx, wz)
        ey[i] = isoY(wx, eyeY, wz)
    }
    val closed = !scared && (time % 3.2f) > 3.05f
    val pupilDir = if (front) 0f else if (dirX) 1f else -1f
    for (i in 0..1) {
        if (closed) {
            canvas.drawRect(ex[i] - eyeR, ey[i] - eyeR * 0.15f, ex[i] + eyeR, ey[i] + eyeR * 0.15f, pupilPaint)
        } else {
            canvas.drawCircle(ex[i], ey[i], eyeR, eyeWhitePaint)
            val pupil = if (scared) 0.32f else 0.5f
            canvas.drawCircle(ex[i] + pupilDir * eyeR * 0.35f, ey[i] - eyeR * 0.15f, eyeR * pupil, pupilPaint)
        }
    }

    // --- Lunettes ---
    fun bridge(r: Float) {
        val dx = ex[1] - ex[0]
        val dy = ey[1] - ey[0]
        val len = sqrt(dx * dx + dy * dy)
        if (len > 2f * r) {
            canvas.drawLine(ex[0] + dx / len * r, ey[0] + dy / len * r, ex[1] - dx / len * r, ey[1] - dy / len * r, accStroke)
        }
    }
    when (lookGlasses) {
        "rondes" -> {
            accStroke.color = Color.parseColor("#2E2A28")
            accStroke.strokeWidth = k * 0.022f
            val r = eyeR * 1.55f
            for (i in 0..1) canvas.drawCircle(ex[i], ey[i], r, accStroke)
            bridge(r)
        }
        "soleil" -> {
            accPaint.color = Color.rgb(25, 25, 35)
            accStroke.color = Color.rgb(25, 25, 35)
            accStroke.strokeWidth = k * 0.025f
            for (i in 0..1) {
                rect.set(ex[i] - eyeR * 1.6f, ey[i] - eyeR * 1.15f, ex[i] + eyeR * 1.6f, ey[i] + eyeR * 1.15f)
                canvas.drawRoundRect(rect, eyeR * 0.6f, eyeR * 0.6f, accPaint)
            }
            bridge(eyeR * 1.4f)
            accPaint.color = Color.argb(150, 255, 255, 255)
            for (i in 0..1) canvas.drawCircle(ex[i] - eyeR * 0.6f, ey[i] - eyeR * 0.4f, eyeR * 0.3f, accPaint)
        }
        "coeur" -> {
            accPaint.color = Color.parseColor("#FF4D8D")
            val sz = eyeR * 1.2f
            for (i in 0..1) {
                val x = ex[i]
                val y = ey[i]
                tmpPath.reset()
                tmpPath.moveTo(x, y + sz * 0.9f)
                tmpPath.cubicTo(x - sz * 1.6f, y - sz * 0.2f, x - sz * 0.6f, y - sz * 1.3f, x, y - sz * 0.45f)
                tmpPath.cubicTo(x + sz * 0.6f, y - sz * 1.3f, x + sz * 1.6f, y - sz * 0.2f, x, y + sz * 0.9f)
                tmpPath.close()
                canvas.drawPath(tmpPath, accPaint)
            }
            accStroke.color = Color.parseColor("#FF4D8D")
            accStroke.strokeWidth = k * 0.02f
            bridge(sz * 1.1f)
        }
    }

    // --- Chapeau ---
    val hy = isoY(0f, y1, 0f)
    val hs = a * k * 1.1f
    when (lookHat) {
        "casquette" -> {
            accPaint.color = Color.parseColor("#E53950")
            rect.set(-hs * 0.85f, hy - hs * 0.75f, hs * 0.85f, hy + hs * 0.35f)
            canvas.drawArc(rect, 180f, 180f, true, accPaint)
            accPaint.color = Color.parseColor("#B32638")
            val vx = fdx * hs * 0.9f
            val vy = hy - hs * 0.2f + fdy * hs * 0.25f
            rect.set(vx - hs * 0.55f, vy - hs * 0.2f, vx + hs * 0.55f, vy + hs * 0.2f)
            canvas.drawOval(rect, accPaint)
        }
        "hautdeforme" -> {
            accPaint.color = Color.parseColor("#22222C")
            rect.set(-hs * 1.2f, hy - hs * 0.37f, hs * 1.2f, hy + hs * 0.27f)
            canvas.drawOval(rect, accPaint)
            canvas.drawRect(-hs * 0.72f, hy - hs * 1.45f, hs * 0.72f, hy - hs * 0.05f, accPaint)
            accPaint.color = Color.parseColor("#E53950")
            canvas.drawRect(-hs * 0.72f, hy - hs * 0.45f, hs * 0.72f, hy - hs * 0.2f, accPaint)
            accPaint.color = Color.parseColor("#3A3A48")
            rect.set(-hs * 0.72f, hy - hs * 1.65f, hs * 0.72f, hy - hs * 1.25f)
            canvas.drawOval(rect, accPaint)
        }
        "couronne" -> {
            accPaint.color = Color.parseColor("#F5C542")
            tmpPath.reset()
            tmpPath.moveTo(-hs * 0.8f, hy + hs * 0.05f)
            tmpPath.lineTo(-hs * 0.8f, hy - hs * 0.75f)
            tmpPath.lineTo(-hs * 0.4f, hy - hs * 0.35f)
            tmpPath.lineTo(0f, hy - hs * 0.95f)
            tmpPath.lineTo(hs * 0.4f, hy - hs * 0.35f)
            tmpPath.lineTo(hs * 0.8f, hy - hs * 0.75f)
            tmpPath.lineTo(hs * 0.8f, hy + hs * 0.05f)
            tmpPath.close()
            canvas.drawPath(tmpPath, accPaint)
            accPaint.color = Color.parseColor("#E53950")
            canvas.drawCircle(0f, hy - hs * 0.2f, hs * 0.13f, accPaint)
        }
    }
    canvas.restore()
}

/** Charge l'apparence choisie (et repasse sur un élément gratuit s'il n'est plus débloqué). */
internal fun GameView.loadLook() {
    val pts = profile.totalPoints
    fun pick(slot: Skins.Slot): String {
        val item = Skins.find(slot, profile.selected(slot))
        return if (item.cost <= pts) item.id else Skins.default(slot)
    }
    lookShape = pick(Skins.Slot.SHAPE)
    lookGlasses = pick(Skins.Slot.GLASSES)
    lookHat = pick(Skins.Slot.HAT)
    lookShoes = pick(Skins.Slot.SHOES)
    val c = Skins.find(Skins.Slot.COLOR, pick(Skins.Slot.COLOR)).color
    playerTop.color = shade(c, 1.18f)
    playerLeft.color = c
    playerRight.color = shade(c, 0.78f)
}

internal fun GameView.currentLook(slot: Skins.Slot): String = when (slot) {
    Skins.Slot.SHAPE -> lookShape
    Skins.Slot.GLASSES -> lookGlasses
    Skins.Slot.HAT -> lookHat
    Skins.Slot.SHOES -> lookShoes
    Skins.Slot.COLOR -> {
        val pts = profile.totalPoints
        val item = Skins.find(slot, profile.selected(slot))
        if (item.cost <= pts) item.id else Skins.default(slot)
    }
}
