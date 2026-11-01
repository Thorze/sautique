package com.sautique.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.ceil
import kotlin.math.sin
import kotlin.random.Random

/**
 * Fausses publicités parodiques, affichées de temps en temps quand on relance une partie.
 * Tout est dessiné en code (aucune image, aucun son). Désactivables dans les réglages.
 */
internal class FakeAd(val id: String, val bg: Int, val text: Int, val accent: Int, val ctaColor: Int) {
    /** Textes traduits : clés « ad.<id>.brand », « .title », « .lines », « .legal », « .cta »… */
    fun t(part: String, vararg args: Pair<String, Any?>) = tr("ad.$id.$part", *args)
    val brand get() = t("brand")
    val title get() = t("title")
    val lines get() = t("lines")
    val legal get() = t("legal")
    val cta get() = t("cta")
}

internal object FakeAds {
    /** Une pub toutes les [EVERY] parties relancées. */
    const val EVERY = 3
    /** Secondes avant de pouvoir fermer la pub. */
    const val CLOSE_DELAY = 3f

    private fun c(s: String) = Color.parseColor(s)

    val all = listOf(
        FakeAd("assurance", c("#EAF2FB"), c("#1D3557"), c("#1D4E89"), c("#1D4E89")),
        FakeAd("cereales", c("#FFF3C4"), c("#5A2D0C"), c("#E53950"), c("#E53950")),
        FakeAd("immo", c("#F4F1EA"), c("#2B2B2B"), c("#2F6B4F"), c("#2F6B4F")),
        FakeAd("genie", c("#1E1E3A"), Color.WHITE, c("#FFD54F"), c("#3DBE6E")),
        FakeAd("crypto", c("#0F2027"), Color.WHITE, c("#3DDC97"), c("#F7931A")),
        FakeAd("love", c("#FFE4EC"), c("#5B1A33"), c("#E0457B"), c("#E0457B")),
        FakeAd("premium", c("#2E3A59"), Color.WHITE, c("#FFD54F"), c("#E0A800"))
    )
}

/** « Rejouer » : relance la partie, avec de temps en temps une fausse pub avant. */
internal fun GameView.replay() {
    if (profile.fakeAdsOn && ++adGames >= FakeAds.EVERY) {
        adGames = 0
        openAd()
    } else resetGame()
}

internal fun GameView.openAd() {
    // Tirage sans répétition : toutes les pubs passent avant de revoir la même
    if (adBag.isEmpty()) adBag.addAll(FakeAds.all.shuffled())
    currentAd = adBag.removeAt(0)
    adTime = 0f
    adMsg = null
    adClicks = 0
    adToastT = 0f
    adCloseLeft = false
    adDodge = Random.nextFloat() < 0.4f
    state = State.AD
}

private fun GameView.closeAd() {
    currentAd = null
    resetGame()
}

private fun GameView.adToast(text: String) {
    adToast = text
    adToastT = 1.6f
}

/** Réaction au gros bouton de la pub. */
private fun GameView.adClick(ad: FakeAd) {
    adClicks++
    adMsg = when (ad.id) {
        "assurance" -> if (adClicks < 3) {
            adToast(ad.t("click", "n" to adClicks))
            null
        } else ad.t("answer", "n" to sessionFalls)
        "immo" -> if (profile.coins < 3600) ad.t("answer", "n" to profile.coins) else ad.t("answer_rich")
        else -> ad.t("answer")
    }
}

/** Découpe un texte en lignes qui tiennent dans [maxW] (les \n forcent un retour à la ligne). */
internal fun GameView.wrapText(text: String, paint: Paint, maxW: Float): List<String> {
    val out = ArrayList<String>()
    for (para in text.split('\n')) {
        var line = ""
        for (word in para.split(' ')) {
            val t = if (line.isEmpty()) word else "$line $word"
            if (line.isEmpty() || paint.measureText(t) <= maxW) line = t
            else {
                out += line
                line = word
            }
        }
        out += line
    }
    return out
}

internal fun GameView.drawAd(canvas: Canvas) {
    val ad = currentAd ?: return
    val top = insetTop
    val bottom = ub
    adPaint.color = ad.bg
    canvas.drawRect(0f, 0f, w, h, adPaint)

    // Bandeau « Publicité »
    adText.textAlign = Paint.Align.LEFT
    adText.textSize = w * 0.03f
    adText.color = ad.text
    adText.alpha = 140
    canvas.drawText(tr("ad.label"), w * 0.05f, top + w * 0.08f, adText)
    adText.alpha = 255
    adText.textAlign = Paint.Align.CENTER

    // Marque
    adBold.color = ad.accent
    adBold.textSize = w * 0.05f
    canvas.drawText(ellipsize(ad.brand, adBold, w * 0.9f), w / 2f, top + w * 0.2f, adBold)

    // Illustration
    val artTop = top + w * 0.25f
    val artH = minOf(w * 0.68f, (bottom - top) * 0.33f)
    drawAdArt(canvas, ad, w / 2f, artTop + artH / 2f, artH)

    // Mentions légales minuscules au-dessus du gros bouton
    val pulse = 1f + 0.035f * sin(adTime * 7f)
    val cw = w * 0.4f * pulse
    val ch = w * 0.065f * pulse
    val cy = bottom - w * 0.14f
    adText.textSize = w * 0.024f
    val legal = wrapText(ad.legal, adText, w * 0.86f)
    val legalTop = cy - w * 0.065f - w * 0.04f - legal.size * w * 0.032f

    // Titre et arguments, centrés entre l'illustration et les mentions légales
    adBold.textSize = w * 0.058f
    adText.textSize = w * 0.04f
    val titleLines = wrapText(ad.title, adBold, w * 0.88f)
    val bodyLines = wrapText(ad.lines, adText, w * 0.88f)
    val blockH = titleLines.size * w * 0.07f + w * 0.02f + bodyLines.size * w * 0.056f
    val free = legalTop - w * 0.08f - (artTop + artH)
    var y = artTop + artH + w * 0.06f + ((free - blockH) / 2f).coerceAtLeast(0f)
    adBold.color = ad.text
    for (l in titleLines) {
        canvas.drawText(l, w / 2f, y, adBold)
        y += w * 0.07f
    }
    y += w * 0.02f
    adText.color = ad.text
    for (l in bodyLines) {
        canvas.drawText(l, w / 2f, y, adText)
        y += w * 0.056f
    }

    adText.textSize = w * 0.024f
    adText.alpha = 150
    var ly = cy - ch - w * 0.04f - (legal.size - 1) * w * 0.032f
    for (l in legal) {
        canvas.drawText(l, w / 2f, ly, adText)
        ly += w * 0.032f
    }
    adText.alpha = 255
    adCtaPaint.color = ad.ctaColor
    drawButton(canvas, RectF(w / 2f - cw, cy - ch, w / 2f + cw, cy + ch), ad.cta, adCtaPaint, w * 0.048f) {
        adClick(ad)
    }
    if (adToastT > 0f) {
        adBold.color = ad.text
        adBold.textSize = w * 0.04f
        adBold.alpha = (255 * adToastT.coerceAtMost(1f)).toInt()
        canvas.drawText(adToast, w / 2f, cy - ch - w * 0.04f - legal.size * w * 0.032f - w * 0.03f, adBold)
        adBold.alpha = 255
    }

    // Croix de fermeture : compte à rebours, puis parfois elle esquive le premier appui
    val cr = w * 0.045f
    val cx = if (adCloseLeft) w * 0.08f else w * 0.92f
    val ccy = top + w * 0.07f
    adPaint.color = Color.argb(60, 0, 0, 0)
    canvas.drawCircle(cx, ccy, cr, adPaint)
    val remain = FakeAds.CLOSE_DELAY - adTime
    adText.color = ad.text
    if (remain > 0f) {
        adText.textSize = w * 0.04f
        canvas.drawText(ceil(remain).toInt().toString(), cx, ccy + w * 0.014f, adText)
    } else {
        adStroke.color = ad.text
        adStroke.strokeWidth = w * 0.007f
        val d = cr * 0.4f
        canvas.drawLine(cx - d, ccy - d, cx + d, ccy + d, adStroke)
        canvas.drawLine(cx + d, ccy - d, cx - d, ccy + d, adStroke)
        if (adMsg == null) buttons.add(Button(RectF(cx - cr * 1.8f, ccy - cr * 1.8f, cx + cr * 1.8f, ccy + cr * 1.8f)) {
            if (adDodge) {
                adDodge = false
                adCloseLeft = !adCloseLeft
                adToast(tr("ad.dodge"))
            } else closeAd()
        })
    }

    // Fenêtre de réponse au bouton
    adMsg?.let { msg ->
        buttons.clear()
        canvas.drawRect(0f, 0f, w, h, overlayPaint)
        darkTextPaint.textAlign = Paint.Align.CENTER
        darkTextPaint.textSize = w * 0.045f
        val lines = wrapText(msg, darkTextPaint, w * 0.72f)
        val lh = w * 0.062f
        val ph = lines.size * lh + w * 0.3f
        val pt = h / 2f - ph / 2f
        rect.set(w * 0.08f, pt, w * 0.92f, pt + ph)
        canvas.drawRoundRect(rect, w * 0.05f, w * 0.05f, panelPaint)
        var ty = pt + w * 0.11f
        for (l in lines) {
            canvas.drawText(l, w / 2f, ty, darkTextPaint)
            ty += lh
        }
        drawButton(canvas, RectF(w * 0.32f, pt + ph - w * 0.15f, w * 0.68f, pt + ph - w * 0.05f), "OK",
            primaryBtnPaint, w * 0.045f) { adMsg = null }
    }
}

// ---------------- Illustrations ----------------

/** Pavé isométrique : (cx, by) = coin avant en bas, rx/ry = demi-losange, hh = hauteur. */
private fun GameView.isoBox(canvas: Canvas, cx: Float, by: Float, rx: Float, ry: Float, hh: Float, top: Int, left: Int, right: Int) {
    fun face(c: Int, vararg p: Float) {
        tmpPath.reset()
        tmpPath.moveTo(p[0], p[1])
        var i = 2
        while (i < p.size) { tmpPath.lineTo(p[i], p[i + 1]); i += 2 }
        tmpPath.close()
        adPaint.color = c
        canvas.drawPath(tmpPath, adPaint)
    }
    face(left, cx, by, cx - rx, by - ry, cx - rx, by - ry - hh, cx, by - hh)
    face(right, cx, by, cx + rx, by - ry, cx + rx, by - ry - hh, cx, by - hh)
    face(top, cx, by - hh, cx - rx, by - ry - hh, cx, by - 2 * ry - hh, cx + rx, by - ry - hh)
}

/** Petit cube avec des yeux. */
private fun GameView.adCube(canvas: Canvas, cx: Float, by: Float, s: Float, color: Int, eyes: Boolean = true) {
    isoBox(canvas, cx, by, s * 0.866f, s * 0.5f, s, shade(color, 1.18f), color, shade(color, 0.78f))
    if (!eyes) return
    for (dx in floatArrayOf(-0.45f, 0.45f)) {
        val ex = cx + dx * s * 0.866f
        val ey = by - s * 0.62f - (if (dx < 0) 0f else 0f)
        adPaint.color = Color.WHITE
        canvas.drawCircle(ex, ey, s * 0.13f, adPaint)
        adPaint.color = Color.parseColor("#1B1B1B")
        canvas.drawCircle(ex, ey + s * 0.02f, s * 0.07f, adPaint)
    }
}

/** Texte dans une illustration, réduit pour tenir dans [maxW]. */
private fun GameView.artText(canvas: Canvas, text: String, x: Float, y: Float, size: Float, maxW: Float) {
    adBold.textSize = size
    val tw = adBold.measureText(text)
    if (tw > maxW) adBold.textSize = size * maxW / tw
    canvas.drawText(text, x, y, adBold)
}

private fun GameView.adEmoji(canvas: Canvas, text: String, x: Float, y: Float, size: Float) {
    adText.textAlign = Paint.Align.CENTER
    adText.textSize = size
    adText.color = Color.BLACK
    canvas.drawText(text, x, y + size * 0.35f, adText)
}

private fun GameView.drawAdArt(canvas: Canvas, ad: FakeAd, cx: Float, cy: Float, s: Float) {
    val t = adTime
    val orange = Color.parseColor("#FF7A45")
    when (ad.id) {
        "assurance" -> {
            // Cube orange blessé : bandage, pansement et béquilles
            val by = cy + s * 0.3f
            val e = s * 0.32f
            adStroke.color = Color.parseColor("#8D6E63")
            adStroke.strokeWidth = s * 0.03f
            canvas.drawLine(cx - e * 1.2f, by + s * 0.04f, cx - e * 0.95f, by - e * 1.2f, adStroke)
            canvas.drawLine(cx + e * 1.2f, by + s * 0.04f, cx + e * 0.95f, by - e * 1.2f, adStroke)
            adCube(canvas, cx, by, e, orange)
            // Bandage autour de la tête
            adStroke.color = Color.WHITE
            adStroke.strokeWidth = s * 0.045f
            canvas.drawLine(cx - e * 0.866f, by - e * 0.5f - e * 0.85f, cx, by - e * 0.85f, adStroke)
            canvas.drawLine(cx, by - e * 0.85f, cx + e * 0.866f, by - e * 0.5f - e * 0.85f, adStroke)
            // Pansement en croix
            adPaint.color = Color.parseColor("#F5D0A9")
            canvas.save()
            canvas.rotate(-30f, cx + e * 0.45f, by - e * 0.3f)
            rect.set(cx + e * 0.25f, by - e * 0.36f, cx + e * 0.65f, by - e * 0.24f)
            canvas.drawRoundRect(rect, e * 0.05f, e * 0.05f, adPaint)
            canvas.restore()
            // Étoiles qui tournent (sonné)
            for (i in 0 until 3) {
                val a = t * 3f + i * 2.1f
                adPaint.color = Color.parseColor("#FFC93C")
                drawStar(canvas, cx + kotlin.math.cos(a) * e * 0.9f, by - e * 2.2f + sin(a) * e * 0.2f, s * 0.04f, adPaint)
            }
            // Sceau « Agréé »
            adPaint.color = ad.accent
            canvas.drawCircle(cx + s * 0.6f, cy - s * 0.28f, s * 0.13f, adPaint)
            adBold.color = Color.WHITE
            artText(canvas, ad.t("art1"), cx + s * 0.6f, cy - s * 0.26f, s * 0.06f, s * 0.24f)
        }
        "cereales" -> {
            // Barre céréalique dans son emballage, penchée, avec des miettes
            canvas.save()
            canvas.rotate(-14f, cx - s * 0.2f, cy)
            adPaint.color = Color.parseColor("#B71C2C")
            for (end in floatArrayOf(-0.66f, 0.21f)) {
                for (i in 0 until 4) {
                    val y0 = cy - s * 0.14f + i * s * 0.07f
                    rect.set(cx + end * s, y0, cx + (end + 0.06f) * s, y0 + s * 0.07f)
                    canvas.drawRect(rect, adPaint)
                }
            }
            rect.set(cx - s * 0.62f, cy - s * 0.15f, cx + s * 0.23f, cy + s * 0.15f)
            adPaint.color = Color.parseColor("#E53950")
            canvas.drawRoundRect(rect, s * 0.04f, s * 0.04f, adPaint)
            rect.set(cx - s * 0.52f, cy - s * 0.11f, cx + s * 0.08f, cy + s * 0.11f)
            adPaint.color = Color.parseColor("#FFC93C")
            canvas.drawRoundRect(rect, s * 0.04f, s * 0.04f, adPaint)
            adBold.color = Color.parseColor("#E53950")
            artText(canvas, ad.t("art1"), cx - s * 0.22f, cy - s * 0.01f, s * 0.085f, s * 0.48f)
            artText(canvas, ad.t("art2"), cx - s * 0.22f, cy + s * 0.075f, s * 0.062f, s * 0.48f)
            canvas.restore()
            // Miettes
            for (i in 0 until 6) {
                adPaint.color = if (i % 2 == 0) Color.parseColor("#C68642") else Color.parseColor("#8D5524")
                val bx = cx - s * 0.5f + i * s * 0.11f
                val byy = cy + s * 0.32f + (i % 3) * s * 0.03f
                rect.set(bx, byy, bx + s * 0.04f, byy + s * 0.04f)
                canvas.drawRect(rect, adPaint)
            }
            // Étoile « +400 % »
            canvas.save()
            canvas.rotate(12f + 4f * sin(t * 5f), cx - s * 0.5f, cy - s * 0.42f)
            adPaint.color = Color.parseColor("#FFEB3B")
            drawStar(canvas, cx - s * 0.5f, cy - s * 0.42f, s * 0.16f, adPaint)
            adBold.color = Color.parseColor("#E53950")
            adBold.textSize = s * 0.065f
            canvas.drawText("+400%", cx - s * 0.5f, cy - s * 0.4f, adBold)
            canvas.restore()
            // Mascotte qui sautille
            val hop = kotlin.math.abs(sin(t * 4f)) * s * 0.08f
            val e = s * 0.22f
            val mx = cx + s * 0.38f
            val mby = cy + s * 0.38f - hop
            adCube(canvas, mx, mby, e, orange, eyes = false)
            adPaint.color = Color.parseColor("#1B1B1B")
            for (dx in floatArrayOf(-0.45f, 0.45f)) {
                rect.set(mx + dx * e * 0.866f - e * 0.2f, mby - e * 0.72f, mx + dx * e * 0.866f + e * 0.2f, mby - e * 0.52f)
                canvas.drawRoundRect(rect, e * 0.08f, e * 0.08f, adPaint)
            }
            adStroke.color = Color.parseColor("#1B1B1B")
            adStroke.strokeWidth = s * 0.012f
            canvas.drawLine(mx - e * 0.3f, mby - e * 0.65f, mx + e * 0.3f, mby - e * 0.65f, adStroke)
        }
        "immo" -> {
            // Pilier étroit et fissuré qui flotte, panneau « À louer »
            val bob = sin(t * 2f) * s * 0.02f
            adPaint.color = Color.argb(200, 255, 255, 255)
            for ((x, y, r) in listOf(Triple(-0.5f, -0.25f, 0.08f), Triple(-0.42f, -0.25f, 0.1f), Triple(0.45f, 0.1f, 0.09f), Triple(0.54f, 0.1f, 0.07f))) {
                canvas.drawCircle(cx + x * s, cy + y * s, r * s, adPaint)
            }
            val by = cy + s * 0.38f + bob
            isoBox(canvas, cx, by, s * 0.12f, s * 0.07f, s * 0.4f,
                Color.parseColor("#C8B89A"), Color.parseColor("#A8977A"), Color.parseColor("#8A7A60"))
            adStroke.color = Color.parseColor("#4E4232")
            adStroke.strokeWidth = s * 0.008f
            canvas.drawLine(cx - s * 0.05f, by - s * 0.4f, cx - s * 0.08f, by - s * 0.32f, adStroke)
            canvas.drawLine(cx - s * 0.08f, by - s * 0.32f, cx - s * 0.04f, by - s * 0.24f, adStroke)
            canvas.drawLine(cx + s * 0.06f, by - s * 0.2f, cx + s * 0.09f, by - s * 0.1f, adStroke)
            // Panneau
            val top = by - s * 0.4f - s * 0.14f
            adStroke.color = Color.parseColor("#6D4C41")
            adStroke.strokeWidth = s * 0.015f
            canvas.drawLine(cx, top + s * 0.1f, cx, top - s * 0.18f, adStroke)
            rect.set(cx - s * 0.17f, top - s * 0.36f, cx + s * 0.17f, top - s * 0.18f)
            adPaint.color = ad.accent
            canvas.drawRoundRect(rect, s * 0.02f, s * 0.02f, adPaint)
            adBold.color = Color.WHITE
            artText(canvas, ad.t("art1"), cx, top - s * 0.245f, s * 0.07f, s * 0.3f)
            // Débris qui tombent
            adPaint.color = Color.parseColor("#A8977A")
            for (i in 0 until 3) {
                val f = ((t * 0.7f + i * 0.33f) % 1f)
                canvas.drawRect(cx - s * 0.05f + i * s * 0.05f, by + f * s * 0.2f, cx - s * 0.035f + i * s * 0.05f, by + f * s * 0.2f + s * 0.015f, adPaint)
            }
        }
        "genie" -> {
            // Fausse séquence de jeu : le cube rate un saut ridicule
            val ph = t % 3f
            val gap = s * 0.42f
            val by = cy + s * 0.3f
            val slabTop = Color.parseColor("#B0BEC5")
            isoBox(canvas, cx - gap / 2f, by, s * 0.17f, s * 0.1f, s * 0.08f, slabTop, Color.parseColor("#8FA0A8"), Color.parseColor("#78868D"))
            isoBox(canvas, cx + gap / 2f, by, s * 0.17f, s * 0.1f, s * 0.08f, slabTop, Color.parseColor("#8FA0A8"), Color.parseColor("#78868D"))
            val e = s * 0.11f
            val baseY = by - s * 0.18f
            val (x, y) = when {
                ph < 1f -> (cx - gap / 2f) to baseY
                ph < 1.5f -> {
                    val f = (ph - 1f) / 0.5f
                    (cx - gap / 2f + f * gap * 0.45f) to (baseY - sin(f * Math.PI.toFloat()) * s * 0.06f)
                }
                else -> (cx - gap / 2f + gap * 0.45f) to (baseY + (ph - 1.5f) * (ph - 1.5f) * s * 0.9f)
            }
            if (y < cy + s * 0.55f) adCube(canvas, x, y, e, Color.parseColor("#9E9E9E"))
            if (ph > 1.9f) {
                adStroke.color = Color.parseColor("#E53950")
                adStroke.strokeWidth = s * 0.04f
                val d = s * 0.14f
                canvas.drawLine(cx - d, cy - s * 0.2f - d, cx + d, cy - s * 0.2f + d, adStroke)
                canvas.drawLine(cx + d, cy - s * 0.2f - d, cx - d, cy - s * 0.2f + d, adStroke)
                adBold.color = Color.parseColor("#FFD54F")
                artText(canvas, ad.t("art1"), cx, cy - s * 0.4f, s * 0.11f, s * 1.2f)
            }
            // Flèche rouge qui pointe le saut « évident »
            adStroke.color = Color.parseColor("#E53950")
            adStroke.strokeWidth = s * 0.02f
            val ax = cx + gap / 2f
            val ay = by - s * 0.32f - kotlin.math.abs(sin(t * 5f)) * s * 0.04f
            canvas.drawLine(ax, ay - s * 0.14f, ax, ay, adStroke)
            canvas.drawLine(ax - s * 0.04f, ay - s * 0.04f, ax, ay, adStroke)
            canvas.drawLine(ax + s * 0.04f, ay - s * 0.04f, ax, ay, adStroke)
        }
        "crypto" -> {
            // Courbe qui monte en zigzag (de 3 à 4 pièces), fusée au bout
            val l = cx - s * 0.6f
            val r = cx + s * 0.6f
            val b = cy + s * 0.4f
            val tp = cy - s * 0.4f
            adStroke.color = Color.argb(80, 255, 255, 255)
            adStroke.strokeWidth = s * 0.008f
            for (i in 0..3) {
                val gy = b - i * (b - tp) / 3f
                canvas.drawLine(l, gy, r, gy, adStroke)
            }
            val pts = floatArrayOf(0f, 0.2f, 0.12f, 0.35f, 0.22f, 0.15f, 0.35f, 0.5f, 0.48f, 0.3f, 0.6f, 0.62f, 0.72f, 0.45f, 0.85f, 0.8f, 1f, 0.95f)
            val reveal = ((t % 4f) / 2.5f).coerceAtMost(1f)
            adStroke.color = ad.accent
            adStroke.strokeWidth = s * 0.025f
            var lastX = l
            var lastY = b
            var i = 0
            while (i + 3 < pts.size) {
                val x0 = l + pts[i] * (r - l)
                val y0 = b - pts[i + 1] * (b - tp)
                val x1 = l + pts[i + 2] * (r - l)
                val y1 = b - pts[i + 3] * (b - tp)
                if (pts[i] > reveal) break
                val f = ((reveal - pts[i]) / (pts[i + 2] - pts[i])).coerceIn(0f, 1f)
                lastX = x0 + (x1 - x0) * f
                lastY = y0 + (y1 - y0) * f
                canvas.drawLine(x0, y0, lastX, lastY, adStroke)
                i += 2
            }
            adEmoji(canvas, "🚀", lastX, lastY - s * 0.06f, s * 0.12f)
            // Pièces « avant / après »
            adBold.textSize = s * 0.07f
            adBold.color = Color.argb(180, 255, 255, 255)
            canvas.drawText("3 🪙", l + s * 0.1f, b + s * 0.1f, adBold)
            adBold.color = ad.accent
            canvas.drawText("4 🪙 !!!", r - s * 0.12f, tp - s * 0.04f, adBold)
        }
        "love" -> {
            // Deux plateformes amoureuses qui se font les yeux doux
            val by = cy + s * 0.3f
            val sway = sin(t * 2.5f) * s * 0.015f
            for ((dx, col) in listOf(-0.3f to "#FF8FB1", 0.3f to "#8EC5FF")) {
                val px = cx + dx * s + (if (dx < 0) sway else -sway)
                val c = Color.parseColor(col)
                isoBox(canvas, px, by, s * 0.2f, s * 0.12f, s * 0.12f, shade(c, 1.15f), c, shade(c, 0.8f))
                for (ex in floatArrayOf(-0.07f, 0.07f)) {
                    adPaint.color = Color.WHITE
                    canvas.drawCircle(px + ex * s, by - s * 0.08f, s * 0.035f, adPaint)
                    adPaint.color = Color.parseColor("#1B1B1B")
                    canvas.drawCircle(px + ex * s + (if (dx < 0) s * 0.012f else -s * 0.012f), by - s * 0.08f, s * 0.018f, adPaint)
                }
                adPaint.color = Color.argb(120, 255, 80, 120)
                canvas.drawCircle(px - s * 0.13f, by - s * 0.03f, s * 0.025f, adPaint)
                canvas.drawCircle(px + s * 0.13f, by - s * 0.03f, s * 0.025f, adPaint)
            }
            // Cœurs qui montent
            for (i in 0 until 5) {
                val f = (t * 0.5f + i * 0.2f) % 1f
                val hx = cx + sin(f * 6f + i) * s * 0.08f
                adEmoji(canvas, "❤️", hx, by - s * 0.25f - f * s * 0.5f, s * (0.06f + 0.05f * (1f - f)))
            }
        }
        else -> {
            // Couronne dorée qui brille
            val gold = Color.parseColor("#FFC93C")
            tmpPath.reset()
            val bw = s * 0.45f
            val b = cy + s * 0.2f
            tmpPath.moveTo(cx - bw, b)
            tmpPath.lineTo(cx - bw, b - s * 0.3f)
            tmpPath.lineTo(cx - bw * 0.5f, b - s * 0.12f)
            tmpPath.lineTo(cx, b - s * 0.42f)
            tmpPath.lineTo(cx + bw * 0.5f, b - s * 0.12f)
            tmpPath.lineTo(cx + bw, b - s * 0.3f)
            tmpPath.lineTo(cx + bw, b)
            tmpPath.close()
            adPaint.color = gold
            canvas.drawPath(tmpPath, adPaint)
            rect.set(cx - bw, b - s * 0.04f, cx + bw, b + s * 0.06f)
            adPaint.color = Color.parseColor("#E0A800")
            canvas.drawRect(rect, adPaint)
            for ((x, col) in listOf(-0.25f to "#E53950", 0f to "#3F8EFC", 0.25f to "#3DBE6E")) {
                adPaint.color = Color.parseColor(col)
                canvas.drawCircle(cx + x * s, b - s * 0.08f, s * 0.035f, adPaint)
            }
            for (i in 0 until 4) {
                val f = (t * 0.8f + i * 0.25f) % 1f
                adPaint.color = Color.argb((255 * (1f - f)).toInt(), 255, 236, 140)
                val a = i * 1.7f
                drawStar(canvas, cx + kotlin.math.cos(a) * s * 0.6f, cy - s * 0.15f + sin(a) * s * 0.3f, s * 0.05f * (0.5f + f), adPaint)
            }
            adBold.color = gold
            artText(canvas, ad.t("art1"), cx, b + s * 0.22f, s * 0.1f, s * 0.9f)
        }
    }
}
