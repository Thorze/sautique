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
    // La traînée « Trait » passe derrière le personnage
    drawRibbon(canvas)
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
            JumpTrick.DOUBLE_FLIP -> rotation = 720f * e * dirSign
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
    drawCharacter(canvas, ox, oy, py, scaleW, scaleH, alongX, scared || panic, front, angel = state == State.REVIVE)
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
    dirX: Boolean, scared: Boolean, front: Boolean, angel: Boolean = false
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

    // Direction du regard à l'écran (+x : en haut à droite, +z : en haut à gauche, caméra : vers le bas)
    val fdx = if (front) 0f else if (dirX) 0.866f else -0.866f
    val fdy = if (front) 0.5f else -0.5f
    val inAir = y0 > 0.05f || state == State.FALLING

    // --- Dos (dessiné avant le corps, il dépasse derrière) ---
    val bx = -fdx * a * k * 0.9f                 // côté opposé au regard
    val midY = isoY(0f, y0 + hgt * 0.55f, 0f)
    val topY = isoY(0f, y1, 0f)
    val botY = isoY(0f, y0, 0f)
    fun wing(side: Int, color: Int, len: Float, flap: Float) {
        canvas.save()
        canvas.rotate(side * (25f + flap), side * a * k * 0.6f, midY)
        accPaint.color = color
        rect.set(side * a * k * 0.4f, midY - len * 0.35f, side * a * k * 0.4f + side * len, midY + len * 0.35f)
        rect.sort()
        canvas.drawOval(rect, accPaint)
        canvas.restore()
    }
    when (lookBack) {
        "cape" -> {
            val flutter = if (inAir) k * 0.25f else k * 0.06f * sin(time * 3f)
            tmpPath.reset()
            tmpPath.moveTo(-a * k * 0.9f, topY + k * 0.08f)
            tmpPath.lineTo(a * k * 0.9f, topY + k * 0.08f)
            tmpPath.lineTo(a * k * 1.15f + bx * 1.4f - fdx * flutter, botY + (if (inAir) -k * 0.05f else k * 0.04f))
            tmpPath.lineTo(-a * k * 1.15f + bx * 1.4f - fdx * flutter, botY + (if (inAir) -k * 0.05f else k * 0.04f))
            tmpPath.close()
            accPaint.color = tinted(Skins.Slot.BACK, Color.parseColor("#C62839"))
            canvas.drawPath(tmpPath, accPaint)
        }
        "sac" -> {
            accPaint.color = tinted(Skins.Slot.BACK, Color.parseColor("#7A5230"))
            rect.set(bx - a * k * 0.55f, midY - k * 0.2f, bx + a * k * 0.55f, midY + k * 0.17f)
            canvas.drawRoundRect(rect, k * 0.08f, k * 0.08f, accPaint)
            accPaint.color = tinted(Skins.Slot.BACK, Color.parseColor("#5C3C22"), 0.75f)
            rect.set(bx - a * k * 0.4f, midY + k * 0.02f, bx + a * k * 0.4f, midY + k * 0.13f)
            canvas.drawRoundRect(rect, k * 0.04f, k * 0.04f, accPaint)
        }
        "papillon" -> {
            val flap = sin(time * (if (inAir) 18f else 4f)) * 15f
            for (side in intArrayOf(-1, 1)) {
                wing(side, tinted(Skins.Slot.BACK, Color.parseColor("#FF8FC7")), k * 0.42f, flap)
                wing(side, tinted(Skins.Slot.BACK, Color.parseColor("#9C6BFF"), 0.7f), k * 0.26f, flap - 30f)
            }
        }
        "jetpack" -> {
            for (dx in floatArrayOf(-1f, 1f)) {
                val cx = bx + dx * a * k * 0.38f
                accPaint.color = tinted(Skins.Slot.BACK, Color.parseColor("#9AA3B5"))
                rect.set(cx - k * 0.08f, midY - k * 0.22f, cx + k * 0.08f, midY + k * 0.16f)
                canvas.drawRoundRect(rect, k * 0.06f, k * 0.06f, accPaint)
                accPaint.color = Color.parseColor("#E53950")
                canvas.drawRect(cx - k * 0.08f, midY - k * 0.12f, cx + k * 0.08f, midY - k * 0.08f, accPaint)
                if (inAir) {
                    val fl = k * (0.22f + 0.08f * sin(time * 40f + dx))
                    tmpPath.reset()
                    tmpPath.moveTo(cx - k * 0.06f, midY + k * 0.16f)
                    tmpPath.lineTo(cx + k * 0.06f, midY + k * 0.16f)
                    tmpPath.lineTo(cx, midY + k * 0.16f + fl)
                    tmpPath.close()
                    accPaint.color = Color.parseColor("#FF9F1C")
                    canvas.drawPath(tmpPath, accPaint)
                }
            }
        }
    }
    // Ailes d'ange quand il revient grâce à une vie
    if (angel) {
        val flap = sin(time * 14f) * 22f
        for (side in intArrayOf(-1, 1)) {
            wing(side, Color.argb(240, 255, 255, 255), k * 0.55f, flap)
            wing(side, Color.argb(255, 235, 240, 250), k * 0.36f, flap - 25f)
        }
    }

    // --- Chaussures (dessinées avant le corps : il en cache le haut, on voit dépasser les pieds) ---
    if (lookShoes != Skins.NONE) {
        val fa = if (lookShape == "boule") a * 0.6f else a
        for (i in 0..1) {
            val wx = if (i == 0) -fa * 0.85f else fa * 0.15f
            val wz = if (i == 0) fa * 0.15f else -fa * 0.85f
            val sx0 = isoX(wx, wz) + fdx * k * 0.07f
            val sy0 = isoY(wx, y0, wz) + k * 0.05f
            val sw = k * 0.12f
            val sh = k * 0.065f
            if (lookShoes == "fusees") {
                accPaint.color = tinted(Skins.Slot.SHOES, Color.parseColor("#9AA3B5"))
                rect.set(sx0 - sw * 0.8f, sy0 - k * 0.12f, sx0 + sw * 0.8f, sy0 + sh * 0.5f)
                canvas.drawRoundRect(rect, sw * 0.4f, sw * 0.4f, accPaint)
                accPaint.color = Color.parseColor("#E53950")
                canvas.drawRect(sx0 - sw * 0.8f, sy0 - k * 0.07f, sx0 + sw * 0.8f, sy0 - k * 0.04f, accPaint)
                // Flammes en l'air
                if (y0 > 0.05f || state == State.FALLING) {
                    val fl = k * (0.14f + 0.06f * kotlin.math.sin(time * 40f + i))
                    tmpPath.reset()
                    tmpPath.moveTo(sx0 - sw * 0.55f, sy0 + sh * 0.5f)
                    tmpPath.lineTo(sx0 + sw * 0.55f, sy0 + sh * 0.5f)
                    tmpPath.lineTo(sx0, sy0 + sh * 0.5f + fl)
                    tmpPath.close()
                    accPaint.color = Color.parseColor("#FF9F1C")
                    canvas.drawPath(tmpPath, accPaint)
                    accPaint.color = Color.parseColor("#FFE066")
                    canvas.drawCircle(sx0, sy0 + sh * 0.5f + fl * 0.3f, sw * 0.25f, accPaint)
                }
            } else if (lookShoes == "bottes") {
                accPaint.color = tinted(Skins.Slot.SHOES, Color.parseColor("#4E2E1A"), 0.55f)
                rect.set(sx0 - sw * 1.1f, sy0 - sh * 0.9f, sx0 + sw * 1.1f, sy0 + sh * 1.15f)
                canvas.drawOval(rect, accPaint)
                accPaint.color = tinted(Skins.Slot.SHOES, Color.parseColor("#7B4A2A"))
                rect.set(sx0 - sw * 1.05f, sy0 - sh * 1.1f, sx0 + sw * 1.05f, sy0 + sh * 0.8f)
                canvas.drawOval(rect, accPaint)
            } else {
                accPaint.color = tinted(Skins.Slot.SHOES, Color.WHITE)
                rect.set(sx0 - sw, sy0 - sh, sx0 + sw, sy0 + sh)
                canvas.drawOval(rect, accPaint)
                accPaint.color = Color.parseColor("#E53950")
                canvas.drawRect(sx0 - sw * 0.5f, sy0 - sh * 0.35f, sx0 + sw * 0.5f, sy0 + sh * 0.1f, accPaint)
            }
        }
    }

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
            // Sur la boule, les yeux restent bien à l'intérieur de la silhouette
            val rr = if (lookShape == "boule") a * 0.68f else a
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
            accStroke.color = tinted(Skins.Slot.GLASSES, Color.parseColor("#2E2A28"))
            accStroke.strokeWidth = k * 0.022f
            val r = eyeR * 1.55f
            for (i in 0..1) canvas.drawCircle(ex[i], ey[i], r, accStroke)
            bridge(r)
        }
        "soleil" -> {
            accPaint.color = tinted(Skins.Slot.GLASSES, Color.rgb(25, 25, 35))
            accStroke.color = tinted(Skins.Slot.GLASSES, Color.rgb(25, 25, 35))
            accStroke.strokeWidth = k * 0.025f
            for (i in 0..1) {
                rect.set(ex[i] - eyeR * 1.6f, ey[i] - eyeR * 1.15f, ex[i] + eyeR * 1.6f, ey[i] + eyeR * 1.15f)
                canvas.drawRoundRect(rect, eyeR * 0.6f, eyeR * 0.6f, accPaint)
            }
            bridge(eyeR * 1.4f)
            accPaint.color = Color.argb(150, 255, 255, 255)
            for (i in 0..1) canvas.drawCircle(ex[i] - eyeR * 0.6f, ey[i] - eyeR * 0.4f, eyeR * 0.3f, accPaint)
        }
        "pixel" -> {
            // Lunettes « 8 bits » : une barre noire et deux verres en escalier
            accPaint.color = tinted(Skins.Slot.GLASSES, Color.rgb(20, 20, 25))
            val gw = eyeR * 1.7f
            val gy = (ey[0] + ey[1]) / 2f
            val gx0 = minOf(ex[0], ex[1]) - gw
            val gx1 = maxOf(ex[0], ex[1]) + gw
            canvas.drawRect(gx0, gy - eyeR * 1.0f, gx1, gy - eyeR * 0.45f, accPaint)
            for (i in 0..1) {
                canvas.drawRect(ex[i] - gw, gy - eyeR * 0.5f, ex[i] + gw * 0.9f, gy + eyeR * 0.3f, accPaint)
                canvas.drawRect(ex[i] - gw * 0.6f, gy + eyeR * 0.25f, ex[i] + gw * 0.5f, gy + eyeR * 0.85f, accPaint)
            }
            accPaint.color = Color.WHITE
            for (i in 0..1) canvas.drawRect(ex[i] - gw * 0.7f, gy - eyeR * 0.35f, ex[i] - gw * 0.35f, gy, accPaint)
        }
        "monocle" -> {
            val i = 1
            accStroke.color = tinted(Skins.Slot.GLASSES, Color.parseColor("#D4A017"))
            accStroke.strokeWidth = k * 0.022f
            canvas.drawCircle(ex[i], ey[i], eyeR * 1.6f, accStroke)
            accStroke.strokeWidth = k * 0.01f
            canvas.drawLine(ex[i] + eyeR * 1.2f, ey[i] + eyeR * 1.1f, ex[i] + eyeR * 1.6f, ey[i] + eyeR * 5f, accStroke)
        }
        "coeur" -> {
            accPaint.color = tinted(Skins.Slot.GLASSES, Color.parseColor("#FF4D8D"))
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
            accStroke.color = tinted(Skins.Slot.GLASSES, Color.parseColor("#FF4D8D"))
            accStroke.strokeWidth = k * 0.02f
            bridge(sz * 1.1f)
        }
    }

    // --- Chapeau ---
    // Sur la boule, le haut est arrondi : le chapeau est un peu plus petit et s'enfonce sur la tête
    val ball = lookShape == "boule"
    val hs = a * k * (if (ball) 0.85f else 1.1f)
    val hy = isoY(0f, y1, 0f) + (if (ball) hs * 0.5f else 0f)
    when (lookHat) {
        "casquette" -> {
            accPaint.color = tinted(Skins.Slot.HAT, Color.parseColor("#E53950"))
            rect.set(-hs * 0.85f, hy - hs * 0.75f, hs * 0.85f, hy + hs * 0.35f)
            canvas.drawArc(rect, 180f, 180f, true, accPaint)
            accPaint.color = tinted(Skins.Slot.HAT, Color.parseColor("#B32638"), 0.75f)
            val vx = fdx * hs * 0.9f
            val vy = hy - hs * 0.2f + fdy * hs * 0.25f
            rect.set(vx - hs * 0.55f, vy - hs * 0.2f, vx + hs * 0.55f, vy + hs * 0.2f)
            canvas.drawOval(rect, accPaint)
        }
        "hautdeforme" -> {
            accPaint.color = tinted(Skins.Slot.HAT, Color.parseColor("#22222C"))
            rect.set(-hs * 1.2f, hy - hs * 0.37f, hs * 1.2f, hy + hs * 0.27f)
            canvas.drawOval(rect, accPaint)
            canvas.drawRect(-hs * 0.72f, hy - hs * 1.45f, hs * 0.72f, hy - hs * 0.05f, accPaint)
            accPaint.color = Color.parseColor("#E53950")
            canvas.drawRect(-hs * 0.72f, hy - hs * 0.45f, hs * 0.72f, hy - hs * 0.2f, accPaint)
            accPaint.color = tinted(Skins.Slot.HAT, Color.parseColor("#3A3A48"), 1.15f)
            rect.set(-hs * 0.72f, hy - hs * 1.65f, hs * 0.72f, hy - hs * 1.25f)
            canvas.drawOval(rect, accPaint)
        }
        "magicien" -> {
            accPaint.color = tinted(Skins.Slot.HAT, Color.parseColor("#2F3FA8"))
            rect.set(-hs * 1.15f, hy - hs * 0.3f, hs * 1.15f, hy + hs * 0.25f)
            canvas.drawOval(rect, accPaint)
            tmpPath.reset()
            tmpPath.moveTo(-hs * 0.75f, hy - hs * 0.05f)
            tmpPath.lineTo(hs * 0.75f, hy - hs * 0.05f)
            tmpPath.lineTo(fdx * hs * 0.4f, hy - hs * 2.1f)
            tmpPath.close()
            canvas.drawPath(tmpPath, accPaint)
            accPaint.color = Color.parseColor("#FFD54F")
            drawStar(canvas, -hs * 0.15f, hy - hs * 0.6f, hs * 0.2f, accPaint)
            drawStar(canvas, hs * 0.25f, hy - hs * 1.15f, hs * 0.14f, accPaint)
            canvas.drawCircle(fdx * hs * 0.4f, hy - hs * 2.1f, hs * 0.1f, accPaint)
        }
        "viking" -> {
            // Cornes
            accPaint.color = Color.parseColor("#F3EEDF")
            for (side in intArrayOf(-1, 1)) {
                tmpPath.reset()
                tmpPath.moveTo(side * hs * 0.55f, hy - hs * 0.35f)
                tmpPath.quadTo(side * hs * 1.35f, hy - hs * 0.6f, side * hs * 1.15f, hy - hs * 1.35f)
                tmpPath.quadTo(side * hs * 1.0f, hy - hs * 0.75f, side * hs * 0.45f, hy - hs * 0.6f)
                tmpPath.close()
                canvas.drawPath(tmpPath, accPaint)
            }
            // Casque
            accPaint.color = tinted(Skins.Slot.HAT, Color.parseColor("#8D96A8"))
            rect.set(-hs * 0.85f, hy - hs * 0.85f, hs * 0.85f, hy + hs * 0.35f)
            canvas.drawArc(rect, 180f, 180f, true, accPaint)
            accPaint.color = Color.parseColor("#C98A00")
            canvas.drawRect(-hs * 0.85f, hy - hs * 0.3f, hs * 0.85f, hy - hs * 0.15f, accPaint)
        }
        "bonnet" -> {
            // Bonnet de laine avec revers et pompon
            accPaint.color = tinted(Skins.Slot.HAT, Color.parseColor("#3F8EFC"))
            rect.set(-hs * 0.9f, hy - hs * 1.1f, hs * 0.9f, hy + hs * 0.5f)
            canvas.drawArc(rect, 180f, 180f, true, accPaint)
            accPaint.color = tinted(Skins.Slot.HAT, Color.parseColor("#2C6ED5"), 0.8f)
            rect.set(-hs * 0.95f, hy - hs * 0.42f, hs * 0.95f, hy - hs * 0.08f)
            canvas.drawRoundRect(rect, hs * 0.15f, hs * 0.15f, accPaint)
            accPaint.color = Color.WHITE
            canvas.drawCircle(0f, hy - hs * 1.15f, hs * 0.22f, accPaint)
        }
        "cowboy" -> {
            accPaint.color = tinted(Skins.Slot.HAT, Color.parseColor("#A0673A"))
            // Large bord relevé
            rect.set(-hs * 1.5f, hy - hs * 0.4f, hs * 1.5f, hy + hs * 0.25f)
            canvas.drawOval(rect, accPaint)
            // Calotte
            tmpPath.reset()
            tmpPath.moveTo(-hs * 0.7f, hy - hs * 0.15f)
            tmpPath.lineTo(-hs * 0.6f, hy - hs * 1.05f)
            tmpPath.quadTo(0f, hy - hs * 0.75f, hs * 0.6f, hy - hs * 1.05f)
            tmpPath.lineTo(hs * 0.7f, hy - hs * 0.15f)
            tmpPath.close()
            accPaint.color = tinted(Skins.Slot.HAT, Color.parseColor("#B57A45"), 1.1f)
            canvas.drawPath(tmpPath, accPaint)
            accPaint.color = Color.parseColor("#4E2E1A")
            canvas.drawRect(-hs * 0.68f, hy - hs * 0.42f, hs * 0.68f, hy - hs * 0.25f, accPaint)
        }
        "aureole" -> {
            accStroke.color = tinted(Skins.Slot.HAT, Color.parseColor("#FFD54F"))
            accStroke.strokeWidth = k * 0.035f
            val ay = hy - hs * 0.7f + kotlin.math.sin(time * 3f) * hs * 0.08f
            rect.set(-hs * 0.85f, ay - hs * 0.25f, hs * 0.85f, ay + hs * 0.25f)
            canvas.drawOval(rect, accStroke)
        }
        "couronne" -> {
            accPaint.color = tinted(Skins.Slot.HAT, Color.parseColor("#F5C542"))
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
    if (angel) {
        accStroke.color = Color.parseColor("#FFD54F")
        accStroke.strokeWidth = k * 0.035f
        val ay = isoY(0f, y1, 0f) - k * 0.3f
        rect.set(-a * k * 0.9f, ay - a * k * 0.28f, a * k * 0.9f, ay + a * k * 0.28f)
        canvas.drawOval(rect, accStroke)
    }
    canvas.restore()
}

/** Couleur d'un objet : la peinture choisie pour cet emplacement, sinon sa couleur d'origine. */
internal fun GameView.tinted(slot: Skins.Slot, original: Int, f: Float = 1f): Int {
    val t = tintColors[slot] ?: 0
    return if (t == 0) original else shade(t, f)
}

/** Charge l'apparence choisie (et repasse sur un élément gratuit s'il n'est plus débloqué). */
internal fun GameView.loadLook() {
    fun pick(slot: Skins.Slot): String {
        val item = Skins.find(slot, profile.selected(slot))
        return if (profile.available(item)) item.id else Skins.default(slot)
    }
    lookShape = pick(Skins.Slot.SHAPE)
    lookGlasses = pick(Skins.Slot.GLASSES)
    lookHat = pick(Skins.Slot.HAT)
    lookShoes = pick(Skins.Slot.SHOES)
    lookBack = pick(Skins.Slot.BACK)
    lookTrail = pick(Skins.Slot.TRAIL)
    lookColor = pick(Skins.Slot.COLOR)
    for (slot in Skins.tintable) {
        val t = Skins.tint(profile.selectedTint(slot))
        tintColors[slot] = if (profile.ownsTint(t)) t.color else 0
    }
    val c = Skins.find(Skins.Slot.COLOR, lookColor).color
    playerTop.color = shade(c, 1.18f)
    playerLeft.color = c
    playerRight.color = shade(c, 0.78f)
    // Couleurs spéciales : dégradés (coordonnées locales du personnage)
    val r = k * 0.35f
    val shader: (Float) -> Shader? = { f ->
        when (lookColor) {
            Skins.RAINBOW -> LinearGradient(
                -r, -r * 2f, r, 0f,
                intArrayOf(
                    shade(Color.parseColor("#FF4D4D"), f), shade(Color.parseColor("#FFC93C"), f),
                    shade(Color.parseColor("#3DBE6E"), f), shade(Color.parseColor("#3F8EFC"), f),
                    shade(Color.parseColor("#8E5CD9"), f)
                ), null, Shader.TileMode.MIRROR
            )
            Skins.GALAXY -> LinearGradient(
                -r, -r * 2f, r, 0f,
                intArrayOf(shade(Color.parseColor("#1B1446"), f), shade(Color.parseColor("#6A3FB5"), f), shade(Color.parseColor("#E0609E"), f)),
                null, Shader.TileMode.MIRROR
            )
            else -> null
        }
    }
    playerTop.shader = shader(1.15f)
    playerLeft.shader = shader(1f)
    playerRight.shader = shader(0.75f)
}

internal fun GameView.currentLook(slot: Skins.Slot): String = when (slot) {
    Skins.Slot.SHAPE -> lookShape
    Skins.Slot.GLASSES -> lookGlasses
    Skins.Slot.HAT -> lookHat
    Skins.Slot.SHOES -> lookShoes
    Skins.Slot.BACK -> lookBack
    Skins.Slot.TRAIL -> lookTrail
    Skins.Slot.COLOR -> lookColor
}
