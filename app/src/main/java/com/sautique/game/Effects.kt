package com.sautique.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// ---------- Particules ----------

/** Petit nuage de poussière sous les pieds à l'atterrissage. */
internal fun GameView.spawnDust(x: Float, z: Float) {
    val bx = sx(x, z) + camX
    val by = sy(x, 0f, z) + camY
    repeat(8) {
        val a = Random.nextFloat() * 6.283f
        val sp = k * (0.6f + Random.nextFloat() * 0.8f)
        particles += Particle(
            bx, by, cos(a) * sp, sin(a) * sp * 0.4f - k * 0.3f,
            0.45f, 0.45f, k * (0.05f + Random.nextFloat() * 0.05f), Color.argb(170, 235, 230, 225), 0f
        )
    }
}

/** Gerbe d'étincelles (parfait, pièce). */
internal fun GameView.spawnSparkles(x: Float, y: Float, color: Int, count: Int = 12) {
    repeat(count) {
        val a = Random.nextFloat() * 6.283f
        val sp = k * (1.5f + Random.nextFloat() * 2f)
        particles += Particle(
            x, y, cos(a) * sp, sin(a) * sp - k * 1.2f,
            0.7f, 0.7f, k * (0.06f + Random.nextFloat() * 0.05f), color, k * 4f, Particle.STAR
        )
    }
}

/** Morceaux d'une plateforme qui s'effondre. */
internal fun GameView.spawnDebris(p: Platform) {
    val bx = sx(p.cx, p.cz) + camX
    val by = sy(p.cx, 0f, p.cz) + camY
    repeat(14) {
        particles += Particle(
            bx + (Random.nextFloat() - 0.5f) * k * p.half * 2.4f,
            by + Random.nextFloat() * k * 0.8f,
            (Random.nextFloat() - 0.5f) * k * 2f, -Random.nextFloat() * k * 1.5f,
            1.1f, 1.1f, k * (0.08f + Random.nextFloat() * 0.1f), shade(p.baseColor, 0.7f + Random.nextFloat() * 0.3f),
            k * 9f
        )
    }
}

/** Secousse de la caméra (intensité en unités du monde). */
internal fun GameView.shake(amount: Float) {
    shakeT = 0.35f
    shakeAmp = maxOf(shakeAmp * (shakeT / 0.35f), amount * k)
}

internal fun GameView.updateEffects(dt: Float) {
    val it = particles.iterator()
    while (it.hasNext()) {
        val p = it.next()
        p.life -= dt
        if (p.life <= 0f) { it.remove(); continue }
        p.vy += p.gravity * dt
        p.x += p.vx * dt
        p.y += p.vy * dt
        p.vx *= 1f - 1.5f * dt
    }
    if (shakeT > 0f) {
        shakeT = (shakeT - dt).coerceAtLeast(0f)
        val f = shakeT / 0.35f
        shakeX = (Random.nextFloat() * 2f - 1f) * shakeAmp * f
        shakeY = (Random.nextFloat() * 2f - 1f) * shakeAmp * f
    } else {
        shakeX = 0f
        shakeY = 0f
        shakeAmp = 0f
    }
    for (r in ribbon) r[2] -= dt
    ribbon.removeAll { it[2] <= 0f }
    val bi = banners.iterator()
    while (bi.hasNext()) {
        val b = bi.next()
        b.life -= dt
        if (b.life <= 0f) bi.remove()
    }
}

internal fun GameView.drawParticles(canvas: Canvas) {
    for (p in particles) {
        val a = (p.life / p.maxLife).coerceIn(0f, 1f)
        accPaint.color = p.color
        accPaint.alpha = (Color.alpha(p.color) * a).toInt()
        val x = p.x - camX + shakeX
        val y = p.y - camY + shakeY
        when (p.kind) {
            Particle.STAR -> drawStar(canvas, x, y, p.size * (0.6f + 0.4f * a), accPaint)
            Particle.HEART -> drawHeart(canvas, x, y, p.size, accPaint)
            Particle.NOTE -> {
                bangPaint.color = p.color
                bangPaint.alpha = accPaint.alpha
                bangPaint.textSize = p.size * 3f
                canvas.drawText("♪", x, y, bangPaint)
                bangPaint.color = Color.parseColor("#E53950")
                bangPaint.alpha = 255
            }
            Particle.BUBBLE -> {
                accStroke.color = p.color
                accStroke.alpha = accPaint.alpha
                accStroke.strokeWidth = k * 0.03f
                val r = p.size * (1.6f - 0.6f * a)
                accPaint.color = Color.argb((90 * a).toInt(), 200, 230, 255)
                canvas.drawCircle(x, y, r, accPaint)
                canvas.drawCircle(x, y, r, accStroke)
                accStroke.alpha = 255
            }
            else -> canvas.drawCircle(x, y, p.size * (1.5f - 0.5f * a), accPaint)
        }
    }
    accPaint.alpha = 255
}

/** Messages en haut de l'écran (missions réussies, nouveau décor…). */
internal fun GameView.drawBanners(canvas: Canvas) {
    var y = insetTop + h * 0.17f + w * 0.3f
    for (b in banners) {
        val a = (b.life / 0.4f).coerceIn(0f, 1f)
        bubbleTextPaint.textSize = w * 0.042f
        val tw = bubbleTextPaint.measureText(b.text)
        val bh = w * 0.085f
        rect.set(w / 2f - tw / 2f - w * 0.04f, y, w / 2f + tw / 2f + w * 0.04f, y + bh)
        bubblePaint.alpha = (235 * a).toInt()
        canvas.drawRoundRect(rect, bh / 2f, bh / 2f, bubblePaint)
        bubblePaint.alpha = 255
        bubbleTextPaint.alpha = (255 * a).toInt()
        canvas.drawText(b.text, w / 2f, rect.centerY() - (bubbleTextPaint.descent() + bubbleTextPaint.ascent()) / 2f, bubbleTextPaint)
        bubbleTextPaint.alpha = 255
        y += bh + w * 0.02f
    }
}

// ---------- Décor ----------

/** Ciel qui change avec le score (matin, journée, coucher de soleil, nuit, aurore, espace). */
internal fun GameView.drawSky(canvas: Canvas) {
    val sky = Theme.sky(score)
    if (sky.top != skyTop || sky.bottom != skyBottom) {
        skyTop = sky.top
        skyBottom = sky.bottom
        bgPaint.shader = LinearGradient(0f, 0f, 0f, h, sky.top, sky.bottom, Shader.TileMode.CLAMP)
    }
    if (sky.name != skyName) {
        if (skyName.isNotEmpty() && state != State.GAME_OVER && score > 0) {
            banners += Banner(
                tr("sky." + sky.name), 2.2f
            )
        }
        skyName = sky.name
    }
    canvas.drawRect(0f, 0f, w, h, bgPaint)

    // Soleil, puis lune la nuit
    val cx = w * 0.82f
    val cy = h * 0.33f
    val r = w * 0.075f
    if (sky.night < 0.9f) {
        accPaint.color = Color.argb((200 * (1f - sky.night)).toInt(), 255, 236, 170)
        canvas.drawCircle(cx, cy, r * 1.35f, accPaint)
        accPaint.color = Color.argb((255 * (1f - sky.night)).toInt(), 255, 214, 102)
        canvas.drawCircle(cx, cy, r, accPaint)
    }
    if (sky.night > 0.1f) {
        accPaint.color = Color.argb((235 * sky.night).toInt(), 245, 243, 225)
        canvas.drawCircle(cx, cy, r * 0.85f, accPaint)
        accPaint.color = Theme.lerp(sky.top, sky.bottom, cy / h)
        accPaint.alpha = (255 * sky.night).toInt()
        canvas.drawCircle(cx + r * 0.38f, cy - r * 0.2f, r * 0.72f, accPaint)
    }
    // Etoiles qui scintillent la nuit
    if (sky.night > 0.05f) {
        for (s in skyStars) {
            val tw = 0.5f + 0.5f * sin(time * 2f + s[2])
            accPaint.color = Color.argb((220 * sky.night * tw).toInt(), 255, 255, 255)
            canvas.drawCircle(s[0] * w, s[1] * h, w * 0.004f * (1f + s[2] % 1f), accPaint)
        }
    }
    accPaint.alpha = 255
}

/** Petit cœur centré en (x, y). */
internal fun GameView.drawHeart(canvas: Canvas, x: Float, y: Float, sz: Float, paint: android.graphics.Paint) {
    tmpPath.reset()
    tmpPath.moveTo(x, y + sz * 0.9f)
    tmpPath.cubicTo(x - sz * 1.6f, y - sz * 0.2f, x - sz * 0.6f, y - sz * 1.3f, x, y - sz * 0.45f)
    tmpPath.cubicTo(x + sz * 0.6f, y - sz * 1.3f, x + sz * 1.6f, y - sz * 0.2f, x, y + sz * 0.9f)
    tmpPath.close()
    canvas.drawPath(tmpPath, paint)
}

/** Traînée choisie dans l'écran Personnage, émise pendant les sauts. */
internal fun GameView.emitTrail(dt: Float) {
    if (lookTrail == Skins.NONE) return
    if (lookTrail == "trait") {
        // Un point à chaque image : le ruban suit exactement le saut
        ribbon += floatArrayOf(sx(px, pz) + camX, sy(px, py + 0.28f, pz) + camY, RIBBON_LIFE)
        return
    }
    trailTimer -= dt
    if (trailTimer > 0f) return
    trailTimer = if (lookTrail == "arcenciel") 0.02f else 0.05f
    val x = sx(px, pz) + camX + (Random.nextFloat() - 0.5f) * k * 0.3f
    val y = sy(px, py + 0.25f, pz) + camY + (Random.nextFloat() - 0.5f) * k * 0.3f
    val drift = (Random.nextFloat() - 0.5f) * k * 0.4f
    when (lookTrail) {
        "etoiles" -> particles += Particle(x, y, drift, k * 0.4f, 0.8f, 0.8f, k * 0.16f, Color.parseColor("#FFB300"), 0f, Particle.STAR)
        "bulles" -> particles += Particle(x, y, drift, -k * 0.6f, 0.9f, 0.9f, k * 0.11f, Color.parseColor("#3F8EFC"), 0f, Particle.BUBBLE)
        "notes" -> particles += Particle(x, y, drift, -k * 0.7f, 0.9f, 0.9f, k * 0.13f,
            listOf("#3F8EFC", "#8E5CD9", "#2E3A59", "#E53950")[Random.nextInt(4)].let { Color.parseColor(it) }, 0f, Particle.NOTE)
        "coeurs" -> particles += Particle(x, y, drift, -k * 0.5f, 0.9f, 0.9f, k * 0.12f, Color.parseColor("#FF4D8D"), 0f, Particle.HEART)
        "arcenciel" -> {
            val hue = (time * 400f) % 360f
            particles += Particle(sx(px, pz) + camX, sy(px, py + 0.25f, pz) + camY, 0f, 0f, 0.7f, 0.7f, k * 0.17f,
                Color.HSVToColor(floatArrayOf(hue, 0.8f, 1f)), 0f, Particle.CIRCLE)
        }
    }
}

private const val RIBBON_LIFE = 0.35f

/** Traînée « Trait » : ruban large et transparent qui s'affine et s'efface. */
internal fun GameView.drawRibbon(canvas: Canvas) {
    if (ribbon.size < 2) return
    val base = if (lookColor == Skins.RAINBOW || lookColor == Skins.GALAXY) Color.parseColor("#FFD54F")
    else playerLeft.color
    ribbonPaint.color = base
    ribbonPaint.strokeJoin = android.graphics.Paint.Join.ROUND
    // Tronçons d'un seul tenant (pas de surépaisseur aux jointures), de plus en plus fins et
    // transparents vers la queue
    val chunk = 5
    var start = 0
    while (start < ribbon.size - 1) {
        val end = minOf(start + chunk, ribbon.size - 1)
        tmpPath.reset()
        tmpPath.moveTo(ribbon[start][0] - camX + shakeX, ribbon[start][1] - camY + shakeY)
        for (i in start + 1..end) tmpPath.lineTo(ribbon[i][0] - camX + shakeX, ribbon[i][1] - camY + shakeY)
        val f = (ribbon[end][2] / RIBBON_LIFE).coerceIn(0f, 1f)
        ribbonPaint.alpha = (115 * f).toInt()
        ribbonPaint.strokeWidth = k * 0.22f * (0.35f + 0.65f * f)
        canvas.drawPath(tmpPath, ribbonPaint)
        start = end
    }
}
