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

internal fun GameView.startBonus() {
    bonusCount++
    // Un type au hasard, différent du précédent
    val types = BonusType.values().filter { bonusCount == 1 || it != bonusType }
    bonusType = types[Random.nextInt(types.size)]
    bonusSpeed = min(1.4f + 0.3f * (bonusCount - 1), 3.6f)
    bonusPos = -1f
    bonusDir = 1f
    bonusT = 0f
    bonusResult = 0
    bonusResultT = 0f
    bonusGain = 0
    bonusTaps = 0
    bonusPulse = 0f
    stars.clear()
    sparkles.clear()
    starSpawn = 0f
    wheelAngle = Random.nextFloat() * 360f
    wheelSpeed = 620f
    wheelStopping = false
    lastWheelIdx = -1
    sounds.playBonusMusic()
    state = State.BONUS
}

internal fun GameView.bonusTap(x: Float, y: Float) {
    if (bonusResult != 0 || bonusT < bonusIntro) return
    when (bonusType) {
        BonusType.TIMING -> finishBonus(Scoring.cursorPoints(bonusPos))
        BonusType.MASH -> {
            bonusTaps++
            bonusPulse = 1f
            sounds.play(Sounds.Sfx.TAP, 0.7f, 0.8f + min(bonusTaps, 40) * 0.02f)
        }
        BonusType.STARS -> {
            val r = w * 0.1f
            val hit = stars.firstOrNull { (it[0] - x) * (it[0] - x) + (it[1] - y) * (it[1] - y) < r * r }
            if (hit != null) {
                stars.remove(hit)
                bonusTaps++
                sparkles.add(floatArrayOf(hit[0], hit[1], 0.5f))
                sounds.play(Sounds.Sfx.STAR, 1f, 0.9f + min(bonusTaps, 12) * 0.05f)
                vibrate(HapticFeedbackConstants.VIRTUAL_KEY)
            }
        }
        BonusType.WHEEL -> wheelStopping = true
    }
}

/** Fin du bonus : ajoute les points gagnés (0 = raté, la partie continue). */
internal fun GameView.finishBonus(gain: Int) {
    bonusGain = gain
    score += gain
    bonusResult = 1
    bonusResultT = 0f
    sounds.play(if (gain > 0) Sounds.Sfx.BONUS_WIN else Sounds.Sfx.BONUS_LOSE)
    if (gain > 0) missionEvent(MissionType.BONUS_WIN)
    vibrate(if (gain > 0) HapticFeedbackConstants.VIRTUAL_KEY else HapticFeedbackConstants.LONG_PRESS)
}

/** Case de la roue sous le repère (en haut). */
internal fun GameView.wheelIndex(): Int {
    val seg = 360f / wheelValues.size
    val a = ((270f - wheelAngle) % 360f + 360f) % 360f
    return (a / seg).toInt().coerceIn(0, wheelValues.size - 1)
}

internal fun GameView.drawStar(canvas: Canvas, x: Float, y: Float, r: Float, paint: Paint) {
    tmpPath.reset()
    for (i in 0 until 10) {
        val rr = if (i % 2 == 0) r else r * 0.45f
        val a = -Math.PI / 2 + i * Math.PI / 5
        val px0 = x + (rr * kotlin.math.cos(a)).toFloat()
        val py0 = y + (rr * kotlin.math.sin(a)).toFloat()
        if (i == 0) tmpPath.moveTo(px0, py0) else tmpPath.lineTo(px0, py0)
    }
    tmpPath.close()
    canvas.drawPath(tmpPath, paint)
}

/** Barre de temps restant (1 = plein). */
internal fun GameView.drawTimeBar(canvas: Canvas, y: Float, remaining: Float) {
    val left = w * 0.15f
    val right = w * 0.85f
    val bh = w * 0.025f
    rect.set(left, y, right, y + bh)
    canvas.drawRoundRect(rect, bh / 2, bh / 2, barBgPaint)
    rect.set(left, y, left + (right - left) * remaining.coerceIn(0f, 1f), y + bh)
    canvas.drawRoundRect(rect, bh / 2, bh / 2, greenZonePaint)
}

internal fun GameView.drawBonus(canvas: Canvas) {
    canvas.drawRect(0f, 0f, w, h, overlayPaint)
    val titleY = h * 0.2f
    textPaint.textSize = w * 0.12f
    canvas.drawText("BONUS", w / 2f, titleY, textPaint)
    textPaint.textSize = w * 0.06f
    canvas.drawText(bonusType.title, w / 2f, titleY + w * 0.1f, textPaint)
    textPaint.textSize = w * 0.04f
    canvas.drawText(bonusType.hint, w / 2f, titleY + w * 0.17f, textPaint)

    val t = (bonusT - bonusIntro).coerceAtLeast(0f)
    val cy = h * 0.55f
    when (bonusType) {
        BonusType.TIMING -> {
            // Barre en dégradé : rouge aux extrémités, jaune, vert au centre
            val left = w * 0.1f
            val right = w * 0.9f
            val bh = w * 0.1f
            val half = (right - left) / 2f
            val mid = (left + right) / 2f
            rect.set(left, cy - bh / 2, right, cy + bh / 2)
            itemPaint.shader = LinearGradient(
                left, 0f, right, 0f,
                intArrayOf(
                    Color.parseColor("#E53950"), Color.parseColor("#FFC93C"), Color.parseColor("#3DBE6E"),
                    Color.parseColor("#FFC93C"), Color.parseColor("#E53950")
                ),
                floatArrayOf(0f, 0.3f, 0.5f, 0.7f, 1f), Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(rect, bh / 2, bh / 2, itemPaint)
            itemPaint.shader = null
            val cx = mid + half * bonusPos
            // Gain en direct au-dessus du curseur
            if (bonusResult == 0 && bonusT >= bonusIntro) {
                textPaint.textSize = w * 0.06f
                canvas.drawText("+${Scoring.cursorPoints(bonusPos)}", cx, cy - bh / 2 - w * 0.07f, textPaint)
            }
            val cw = w * 0.025f
            val ch = bh + w * 0.07f
            rect.set(cx - cw / 2, cy - ch / 2, cx + cw / 2, cy + ch / 2)
            cursorStroke.strokeWidth = w * 0.008f
            canvas.drawRoundRect(rect, cw / 2, cw / 2, cursorPaint)
            canvas.drawRoundRect(rect, cw / 2, cw / 2, cursorStroke)
        }
        BonusType.MASH -> {
            textPaint.textSize = w * 0.1f
            canvas.drawText("$bonusTaps", w / 2f, cy - w * 0.27f, textPaint)
            val r = w * 0.19f * (1f + 0.1f * bonusPulse)
            canvas.drawCircle(w / 2f, cy, r, primaryBtnPaint)
            btnTextPaint.color = Color.WHITE
            btnTextPaint.textSize = w * 0.08f
            canvas.drawText(tr("bonus.tap_button"), w / 2f, cy - (btnTextPaint.descent() + btnTextPaint.ascent()) / 2f, btnTextPaint)
            drawTimeBar(canvas, cy + w * 0.27f, 1f - t / mashDuration)
            textPaint.textSize = w * 0.045f
            canvas.drawText(tr("bonus.points", "n" to min(bonusTaps / 4, 10)), w / 2f, cy + w * 0.38f, textPaint)
        }
        BonusType.STARS -> {
            for (st in stars) {
                val f = st[2] / st[3]
                val pop = min(1f, (1f - f) * 6f)
                starPaint.alpha = (255 * min(1f, f * 3f)).toInt()
                drawStar(canvas, st[0], st[1], w * 0.075f * pop, starPaint)
            }
            starPaint.alpha = 255
            for (sp in sparkles) {
                val f = sp[2] / 0.5f
                starPaint.alpha = (255 * f).toInt()
                for (k2 in 0 until 6) {
                    val a = k2 * Math.PI / 3
                    val d = w * 0.12f * (1f - f)
                    drawStar(canvas, sp[0] + (d * kotlin.math.cos(a)).toFloat(), sp[1] + (d * kotlin.math.sin(a)).toFloat(), w * 0.02f, starPaint)
                }
            }
            starPaint.alpha = 255
            textPaint.textSize = w * 0.06f
            canvas.drawText("★ $bonusTaps", w / 2f, ub - w * 0.2f, textPaint)
            drawTimeBar(canvas, ub - w * 0.16f, 1f - t / starsDuration)
        }
        BonusType.WHEEL -> {
            val r = w * 0.34f
            val n = wheelValues.size
            val seg = 360f / n
            rect.set(w / 2f - r, cy - r, w / 2f + r, cy + r)
            for (i in 0 until n) {
                itemPaint.color = palette[i % palette.size]
                if (wheelValues[i] == 0) itemPaint.color = Color.parseColor("#5C6378")
                if (wheelValues[i] == 10) itemPaint.color = Color.parseColor("#E0B03C")
                canvas.drawArc(rect, wheelAngle + i * seg, seg, true, itemPaint)
            }
            wheelText.textSize = w * 0.065f
            for (i in 0 until n) {
                val a = Math.toRadians((wheelAngle + i * seg + seg / 2f).toDouble())
                val tx = w / 2f + (r * 0.68f * kotlin.math.cos(a)).toFloat()
                val ty = cy + (r * 0.68f * kotlin.math.sin(a)).toFloat()
                val label = if (wheelValues[i] == 0) "0" else "+${wheelValues[i]}"
                canvas.drawText(label, tx, ty - (wheelText.descent() + wheelText.ascent()) / 2f, wheelText)
            }
            canvas.drawCircle(w / 2f, cy, r * 0.12f, cursorPaint)
            // Repère en haut
            tmpPath.reset()
            tmpPath.moveTo(w / 2f - w * 0.04f, cy - r - w * 0.05f)
            tmpPath.lineTo(w / 2f + w * 0.04f, cy - r - w * 0.05f)
            tmpPath.lineTo(w / 2f, cy - r + w * 0.03f)
            tmpPath.close()
            canvas.drawPath(tmpPath, cursorPaint)
            cursorStroke.strokeWidth = w * 0.006f
            canvas.drawPath(tmpPath, cursorStroke)
        }
    }

    val msg = when {
        bonusResult != 0 && bonusGain > 0 -> tr("bonus.win", "n" to bonusGain)
        bonusResult != 0 -> tr("bonus.miss")
        bonusT < bonusIntro -> tr("bonus.ready")
        else -> ""
    }
    if (msg.isNotEmpty()) {
        textPaint.textSize = w * 0.085f
        val my = if (bonusType == BonusType.STARS) h * 0.55f else ub - w * 0.08f
        canvas.drawText(msg, w / 2f, my, textPaint)
    }
}

/** Mise à jour d'un niveau bonus en cours. */
internal fun GameView.updateBonus(dt: Float) {
    bonusT += dt
    if (bonusPulse > 0f) bonusPulse = (bonusPulse - dt * 6f).coerceAtLeast(0f)
    sparkles.forEach { it[2] -= dt }
    sparkles.removeAll { it[2] <= 0f }
    val t = bonusT - bonusIntro
    if (bonusResult == 0 && t >= 0f) {
        when (bonusType) {
            BonusType.TIMING -> {
                bonusPos += bonusDir * bonusSpeed * dt
                if (bonusPos > 1f) { bonusPos = 2f - bonusPos; bonusDir = -1f }
                if (bonusPos < -1f) { bonusPos = -2f - bonusPos; bonusDir = 1f }
            }
            BonusType.MASH -> if (t >= mashDuration) finishBonus(min(bonusTaps / 4, 10))
            BonusType.STARS -> {
                starSpawn -= dt
                if (t < starsDuration && starSpawn <= 0f && stars.size < 3) {
                    val life = maxOf(0.75f, 1.4f - 0.08f * bonusCount)
                    stars.add(floatArrayOf(
                        w * (0.15f + Random.nextFloat() * 0.7f),
                        h * (0.36f + Random.nextFloat() * 0.38f),
                        life, life
                    ))
                    starSpawn = 0.45f
                }
                stars.forEach { it[2] -= dt }
                stars.removeAll { it[2] <= 0f }
                if (t >= starsDuration && stars.isEmpty()) finishBonus(bonusTaps)
            }
            BonusType.WHEEL -> {
                wheelAngle = (wheelAngle + wheelSpeed * dt) % 360f
                val wi = wheelIndex()
                if (wi != lastWheelIdx) {
                    lastWheelIdx = wi
                    sounds.play(Sounds.Sfx.WHEEL_TICK, 0.5f)
                }
                if (t > 6f) wheelStopping = true
                if (wheelStopping) {
                    wheelSpeed *= kotlin.math.exp(-1.6f * dt)
                    if (wheelSpeed < 12f) {
                        wheelSpeed = 0f
                        finishBonus(wheelValues[wheelIndex()])
                    }
                }
            }
        }
    } else if (bonusResult != 0) {
        bonusResultT += dt
        // Raté ou réussi, la partie continue
        if (bonusResultT > 1.3f) {
            // La plateforme fragile sous le personnage continue de trembler mais ne s'effondre plus
            if (current.crumbly) current.crumbleSafe = true
            state = State.READY
            sounds.playPlaylist()
        }
    }
}
