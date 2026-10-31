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

/** Fenêtre de saisie du pseudo. */
internal fun GameView.askPseudo(first: Boolean) {
    if (pseudoDialogShown) return
    pseudoDialogShown = true
    val input = android.widget.EditText(context).apply {
        setText(profile.pseudo ?: "")
        hint = "Ton pseudo"
        setSingleLine()
        filters = arrayOf(android.text.InputFilter.LengthFilter(16))
        setSelection(text.length)
    }
    val pad = (20 * resources.displayMetrics.density).toInt()
    val container = android.widget.FrameLayout(context).apply {
        setPadding(pad, pad / 2, pad, 0)
        addView(input)
    }
    android.app.AlertDialog.Builder(context)
        .setTitle(if (first) "Choisis ton pseudo" else "Changer de pseudo")
        .setMessage("Il apparaîtra dans le classement mondial.")
        .setView(container)
        .setCancelable(!first)
        .setPositiveButton("OK") { _, _ ->
            val p = input.text.toString().trim()
            when {
                p.isNotEmpty() -> profile.pseudo = p
                profile.pseudo.isNullOrBlank() -> profile.pseudo = "Joueur" + Random.nextInt(1000, 10000)
            }
            syncOnline()
            if (!profile.customized) openCustomize()
        }
        .apply { if (!first) setNegativeButton("Annuler", null) }
        .setOnDismissListener { pseudoDialogShown = false }
        .show()
}

/** Envoie le record et le pseudo au classement mondial si besoin. */
internal fun GameView.syncOnline() {
    val pseudo = profile.pseudo
    if (syncing || !online.configured || pseudo.isNullOrBlank() || !profile.needsSync()) return
    syncing = true
    val sentBest = profile.best
    online.submit(profile.playerId, pseudo, sentBest) { ok ->
        syncing = false
        if (ok) {
            profile.syncedBest = sentBest
            profile.syncedPseudo = pseudo
            if (state == State.LEADERBOARD && boardWorld) loadWorld()
            // Le pseudo ou le record a pu changer pendant l'envoi
            if (profile.needsSync()) syncOnline()
        }
    }
}

internal fun GameView.openBoard(world: Boolean) {
    if (state != State.LEADERBOARD) boardReturnState = state
    state = State.LEADERBOARD
    boardWorld = world
    personalRuns = profile.topRuns()
    if (world) loadWorld()
}

internal fun GameView.loadWorld() {
    if (!online.configured) {
        worldStatus = WorldStatus.NOT_CONFIGURED
        return
    }
    worldStatus = WorldStatus.LOADING
    worldRank = null
    online.fetchBoard(10, profile.playerId) { board ->
        if (board == null) {
            worldStatus = WorldStatus.ERROR
        } else {
            worldEntries = board.top
            worldRank = board.myRank
            worldStatus = WorldStatus.OK
        }
    }
}

internal fun GameView.openCustomize() {
    if (state == State.CUSTOMIZE) return
    custReturnState = state
    custMsg = ""
    state = State.CUSTOMIZE
}

internal fun GameView.closeCustomize() {
    profile.customized = true
    state = custReturnState
}

internal fun GameView.openSettings() {
    if (state == State.SETTINGS) return
    settingsReturnState = state
    state = State.SETTINGS
}

internal fun GameView.dragTo(x: Float) {
    val r = if (dragSlider == 1) sfxSlider else if (dragSlider == 2) musicSlider else return
    val v = ((x - r.left) / r.width()).coerceIn(0f, 1f)
    val snapped = if (v < 0.03f) 0f else v
    if (dragSlider == 1) sounds.soundVolume = snapped else sounds.musicVolume = snapped
}

/** Dessine un bouton arrondi et l'enregistre pour la détection des appuis. */
internal fun GameView.drawButton(canvas: Canvas, r: RectF, label: String, bg: Paint, textSize: Float, action: () -> Unit) {
    val radius = r.height() / 2f
    canvas.drawRoundRect(r, radius, radius, bg)
    btnTextPaint.textSize = textSize
    btnTextPaint.color = if (bg === tabOffPaint || bg === lockedPaint || (bg === itemPaint && isLight(bg.color)))
        Color.parseColor("#2E3A59") else Color.WHITE
    val label2 = ellipsize(label, btnTextPaint, r.width() - radius)
    canvas.drawText(label2, r.centerX(), r.centerY() - (btnTextPaint.descent() + btnTextPaint.ascent()) / 2f, btnTextPaint)
    buttons.add(Button(RectF(r), action))
}

internal fun GameView.isLight(c: Int) = Color.red(c) * 0.3f + Color.green(c) * 0.59f + Color.blue(c) * 0.11f > 170f

internal fun GameView.ellipsize(text: String, paint: Paint, maxWidth: Float): String {
    if (paint.measureText(text) <= maxWidth) return text
    var t = text
    while (t.length > 1 && paint.measureText("$t…") > maxWidth) t = t.dropLast(1)
    return "$t…"
}

internal fun GameView.drawLeaderboard(canvas: Canvas) {
    canvas.drawRect(0f, 0f, w, h, overlayPaint)
    val left = w * 0.06f
    val right = w * 0.94f
    val top = insetTop + h * 0.05f
    val bottom = ub - h * 0.03f
    rect.set(left, top, right, bottom)
    canvas.drawRoundRect(rect, w * 0.05f, w * 0.05f, panelPaint)

    // Titre
    darkTextPaint.textAlign = Paint.Align.CENTER
    darkTextPaint.textSize = w * 0.075f
    canvas.drawText("Classement", w / 2f, top + w * 0.12f, darkTextPaint)

    // Onglets
    val tabTop = top + w * 0.17f
    val tabH = w * 0.1f
    val mid = w / 2f
    drawButton(canvas, RectF(left + w * 0.05f, tabTop, mid - w * 0.01f, tabTop + tabH), "Top perso",
        if (!boardWorld) tabOnPaint else tabOffPaint, w * 0.042f) { openBoard(world = false) }
    drawButton(canvas, RectF(mid + w * 0.01f, tabTop, right - w * 0.05f, tabTop + tabH), "Top mondial",
        if (boardWorld) tabOnPaint else tabOffPaint, w * 0.042f) { openBoard(world = true) }

    // Lignes
    val listTop = tabTop + tabH + w * 0.06f
    val footer = bottom - w * 0.2f
    val rowH = (footer - w * 0.08f - listTop) / LocalScores.MAX_RUNS
    val rowText = min(w * 0.048f, rowH * 0.5f)
    darkTextPaint.textSize = rowText
    greyTextPaint.textSize = w * 0.045f
    val colRank = left + w * 0.06f
    val colName = left + w * 0.17f
    val colScore = right - w * 0.06f
    val centerY = (listTop + footer) / 2f

    fun row(i: Int, name: String, value: String, mine: Boolean) {
        val y = listTop + rowH * i
        if (mine) {
            rect.set(left + w * 0.03f, y, right - w * 0.03f, y + rowH * 0.92f)
            canvas.drawRoundRect(rect, rowH * 0.3f, rowH * 0.3f, highlightPaint)
        }
        val ty = y + rowH * 0.46f - (darkTextPaint.descent() + darkTextPaint.ascent()) / 2f
        darkTextPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("${i + 1}.", colRank, ty, darkTextPaint)
        canvas.drawText(ellipsize(name, darkTextPaint, colScore - colName - w * 0.15f), colName, ty, darkTextPaint)
        darkTextPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(value, colScore, ty, darkTextPaint)
    }

    if (!boardWorld) {
        if (personalRuns.isEmpty()) {
            canvas.drawText("Pas encore de partie.", w / 2f, centerY, greyTextPaint)
        } else {
            personalRuns.forEachIndexed { i, r -> row(i, dateFormat.format(java.util.Date(r.time)), r.score.toString(), false) }
        }
        greyTextPaint.textSize = w * 0.04f
        canvas.drawText("Record : $best", w / 2f, footer, greyTextPaint)
    } else {
        when (worldStatus) {
            WorldStatus.LOADING, WorldStatus.IDLE ->
                canvas.drawText("Chargement…", w / 2f, centerY, greyTextPaint)
            WorldStatus.ERROR -> {
                canvas.drawText("Impossible de charger le classement.", w / 2f, centerY, greyTextPaint)
                canvas.drawText("Vérifie ta connexion.", w / 2f, centerY + w * 0.07f, greyTextPaint)
            }
            WorldStatus.NOT_CONFIGURED ->
                canvas.drawText("Classement mondial non configuré.", w / 2f, centerY, greyTextPaint)
            WorldStatus.OK -> {
                if (worldEntries.isEmpty()) canvas.drawText("Aucun score pour l'instant.", w / 2f, centerY, greyTextPaint)
                worldEntries.forEachIndexed { i, e -> row(i, e.pseudo, e.best.toString(), e.me) }
            }
        }
        greyTextPaint.textSize = w * 0.04f
        val rank = worldRank
        val info = when {
            best <= 0 -> "Joue une partie pour entrer au classement !"
            rank != null -> "${profile.pseudo ?: ""} : place n°$rank  (record $best)"
            else -> "Ton record : $best"
        }
        canvas.drawText(ellipsize(info, greyTextPaint, right - left - w * 0.06f), w / 2f, footer, greyTextPaint)
    }

    // Retour
    drawButton(canvas, RectF(w * 0.3f, bottom - w * 0.15f, w * 0.7f, bottom - w * 0.05f), "Retour",
        primaryBtnPaint, w * 0.045f) { state = boardReturnState }
}

/** Barre « Perfect » sous le score, qui se remplit à chaque parfait enchaîné. */
internal fun GameView.drawPerfectBar(canvas: Canvas) {
    val a = perfectBarAlpha.coerceIn(0f, 1f)
    val scale = 1f + 0.12f * perfectPulse
    val cx = w / 2f
    val cy = insetTop + h * 0.17f + w * 0.17f
    val bw = w * 0.62f * scale
    val bh = w * 0.085f * scale
    canvas.save()
    canvas.translate(cx, cy)
    rect.set(-bw / 2f, -bh / 2f, bw / 2f, bh / 2f)
    perfectBgPaint.alpha = (110 * a).toInt()
    canvas.drawRoundRect(rect, bh / 2f, bh / 2f, perfectBgPaint)
    // Remplissage : 3e parfait = 1/4 … 6e et plus = barre pleine
    val level = ((shownStreak.coerceAtLeast(3) - 2) / 4f).coerceIn(0f, 1f)
    val fillW = (bw - bh * 0.3f) * level
    if (fillW > 0f) {
        rect.set(-bw / 2f + bh * 0.15f, -bh / 2f + bh * 0.15f, -bw / 2f + bh * 0.15f + fillW, bh / 2f - bh * 0.15f)
        perfectBarPaint.shader = LinearGradient(
            rect.left, 0f, rect.right.coerceAtLeast(rect.left + 1f), 0f,
            Color.parseColor("#FFD54F"), Color.parseColor("#FF7A45"), Shader.TileMode.CLAMP
        )
        perfectBarPaint.alpha = (255 * a).toInt()
        canvas.drawRoundRect(rect, bh * 0.35f, bh * 0.35f, perfectBarPaint)
    }
    val points = if (perfectStreak >= 3) perfectPoints(perfectStreak) else lastPerfectPoints
    textPaint.textSize = bh * 0.55f
    textPaint.alpha = (255 * a).toInt()
    canvas.drawText("PERFECT  ×$shownStreak   +$points", 0f, -(textPaint.descent() + textPaint.ascent()) / 2f, textPaint)
    textPaint.alpha = 255
    canvas.restore()
}

internal fun GameView.drawSlider(canvas: Canvas, r: RectF, label: String, value: Float) {
    darkTextPaint.textAlign = Paint.Align.LEFT
    darkTextPaint.textSize = w * 0.05f
    canvas.drawText(label, r.left, r.top - w * 0.05f, darkTextPaint)
    darkTextPaint.textAlign = Paint.Align.RIGHT
    canvas.drawText(if (value <= 0f) "Coupé" else "${(value * 100).toInt()} %", r.right, r.top - w * 0.05f, darkTextPaint)
    val bh = r.height()
    canvas.drawRoundRect(r, bh / 2, bh / 2, tabOffPaint)
    rect.set(r.left, r.top, r.left + r.width() * value, r.bottom)
    canvas.drawRoundRect(rect, bh / 2, bh / 2, primaryBtnPaint)
    val kx = r.left + r.width() * value
    canvas.drawCircle(kx, r.centerY(), bh * 1.1f, cursorPaint)
    cursorStroke.strokeWidth = w * 0.006f
    canvas.drawCircle(kx, r.centerY(), bh * 1.1f, cursorStroke)
}

internal fun GameView.drawSettings(canvas: Canvas) {
    canvas.drawRect(0f, 0f, w, h, overlayPaint)
    val left = w * 0.06f
    val right = w * 0.94f
    val top = h * 0.25f
    val bottom = top + w * 0.95f
    rect.set(left, top, right, bottom)
    canvas.drawRoundRect(rect, w * 0.05f, w * 0.05f, panelPaint)
    darkTextPaint.textAlign = Paint.Align.CENTER
    darkTextPaint.textSize = w * 0.07f
    canvas.drawText("Son", w / 2f, top + w * 0.11f, darkTextPaint)

    val sl = left + w * 0.08f
    val sr = right - w * 0.08f
    val bh = w * 0.035f
    sfxSlider.set(sl, top + w * 0.3f, sr, top + w * 0.3f + bh)
    musicSlider.set(sl, top + w * 0.55f, sr, top + w * 0.55f + bh)
    drawSlider(canvas, sfxSlider, "Effets sonores", sounds.soundVolume)
    drawSlider(canvas, musicSlider, "Musique", sounds.musicVolume)

    drawButton(canvas, RectF(w * 0.3f, bottom - w * 0.17f, w * 0.7f, bottom - w * 0.06f), "OK",
        primaryBtnPaint, w * 0.05f) { state = settingsReturnState }
}

internal fun GameView.drawCustomize(canvas: Canvas) {
    canvas.drawRect(0f, 0f, w, h, overlayPaint)
    val left = w * 0.04f
    val right = w * 0.96f
    val top = insetTop + h * 0.03f
    val bottom = ub - h * 0.02f
    rect.set(left, top, right, bottom)
    canvas.drawRoundRect(rect, w * 0.05f, w * 0.05f, panelPaint)

    darkTextPaint.textAlign = Paint.Align.CENTER
    darkTextPaint.textSize = w * 0.07f
    canvas.drawText("Ton personnage", w / 2f, top + w * 0.1f, darkTextPaint)

    // Aperçu (regarde à droite puis à gauche)
    val pcx = w / 2f
    val pcy = top + w * 0.42f
    rect.set(pcx - k * 0.75f, pcy - k * 0.3f, pcx + k * 0.75f, pcy + k * 0.3f)
    canvas.drawOval(rect, shadowPaint)
    canvas.save()
    canvas.translate(pcx, pcy)
    canvas.scale(2f, 2f)
    val dirX = (time / 2.5f).toInt() % 2 == 0
    drawCharacter(canvas, 0f, 0f, 0f, 1f, 1f, dirX, scared = false, front = false)
    canvas.restore()

    // Onglets
    val tabTop = pcy + w * 0.1f
    val tabH = w * 0.085f
    val slots = Skins.Slot.values()
    val tw = (right - left - w * 0.04f) / slots.size
    slots.forEachIndexed { i, slot ->
        val x0 = left + w * 0.02f + i * tw
        drawButton(
            canvas, RectF(x0 + w * 0.004f, tabTop, x0 + tw - w * 0.004f, tabTop + tabH), slot.title,
            if (slot == custSlot) tabOnPaint else tabOffPaint, w * 0.03f
        ) { custSlot = slot }
    }

    // Eléments de l'onglet
    val pts = profile.totalPoints
    val selected = currentLook(custSlot)
    val gridTop = tabTop + tabH + w * 0.04f
    val cellH = w * 0.095f
    val cellW = (right - left - w * 0.08f) / 2f
    Skins.bySlot(custSlot).forEachIndexed { i, item ->
        val col = i % 2
        val row = i / 2
        val x0 = left + w * 0.04f + col * cellW
        val y0 = gridTop + row * (cellH + w * 0.02f)
        val r = RectF(x0 + w * 0.01f, y0, x0 + cellW - w * 0.01f, y0 + cellH)
        val unlocked = item.cost <= pts
        val isSel = item.id == selected
        val label = when {
            !unlocked -> "🔒 ${item.label} · ${item.cost}"
            isSel -> "✓ ${item.label}"
            else -> item.label
        }
        val bg = when {
            !unlocked -> lockedPaint
            custSlot == Skins.Slot.COLOR -> itemPaint.also { it.color = item.color }
            isSel -> tabOnPaint
            else -> tabOffPaint
        }
        if (isSel && custSlot == Skins.Slot.COLOR) {
            bubbleStroke.strokeWidth = w * 0.008f
            val rr = RectF(r.left - w * 0.008f, r.top - w * 0.008f, r.right + w * 0.008f, r.bottom + w * 0.008f)
            canvas.drawRoundRect(rr, rr.height() / 2f, rr.height() / 2f, bubbleStroke)
        }
        drawButton(canvas, r, label, bg, w * 0.036f) {
            if (unlocked) {
                profile.select(custSlot, item.id)
                loadLook()
            } else {
                custMsg = "🔒 Se débloque à ${item.cost} points cumulés"
                custMsgT = 2.5f
            }
        }
    }

    // Bas : points cumulés et validation
    greyTextPaint.textSize = w * 0.038f
    val footY = bottom - w * 0.2f
    val nextItem = Skins.nextUnlock(pts)
    val info = when {
        custMsgT > 0f -> custMsg
        nextItem != null -> "Points cumulés : $pts · prochain : ${nextItem.label} à ${nextItem.cost}"
        else -> "Points cumulés : $pts · tout est débloqué !"
    }
    canvas.drawText(ellipsize(info, greyTextPaint, right - left - w * 0.06f), w / 2f, footY, greyTextPaint)
    drawButton(canvas, RectF(w * 0.3f, bottom - w * 0.15f, w * 0.7f, bottom - w * 0.05f), "OK",
        primaryBtnPaint, w * 0.05f) { closeCustomize() }
}

internal fun GameView.drawGameOver(canvas: Canvas) {
    canvas.drawRect(0f, 0f, w, h, overlayPaint)
    val y0 = h * 0.34f
    gameOverReason?.let {
        textPaint.textSize = w * 0.05f
        canvas.drawText(it, w / 2f, y0 - w * 0.14f, textPaint)
    }
    textPaint.textSize = w * 0.12f
    canvas.drawText("Perdu !", w / 2f, y0, textPaint)
    textPaint.textSize = w * 0.07f
    canvas.drawText("Score : $score", w / 2f, y0 + w * 0.14f, textPaint)
    canvas.drawText(
        if (newBest) "Nouveau record !" else "Record : $best",
        w / 2f, y0 + w * 0.24f, textPaint
    )
    if (gameOverTime > 0.6f) {
        val bh = w * 0.13f
        var y = y0 + w * 0.34f
        drawButton(canvas, RectF(w * 0.2f, y, w * 0.8f, y + bh), "Rejouer", primaryBtnPaint, w * 0.06f) { resetGame() }
        y += bh + w * 0.04f
        val sh = w * 0.11f
        drawButton(canvas, RectF(w * 0.1f, y, w * 0.49f, y + sh), "Top perso", secondaryBtnPaint, w * 0.045f) {
            openBoard(world = false)
        }
        drawButton(canvas, RectF(w * 0.51f, y, w * 0.9f, y + sh), "Top mondial", secondaryBtnPaint, w * 0.045f) {
            openBoard(world = true)
        }
        y += sh + w * 0.04f
        drawButton(canvas, RectF(w * 0.1f, y, w * 0.49f, y + sh), "✎ " + (profile.pseudo ?: "Pseudo"),
            chipPaint, w * 0.04f) { askPseudo(first = false) }
        drawButton(canvas, RectF(w * 0.51f, y, w * 0.9f, y + sh), "🎨 Personnage",
            chipPaint, w * 0.04f) { openCustomize() }
        y += sh + w * 0.09f
        textPaint.textSize = w * 0.042f
        if (unlockedLabels.isNotEmpty()) {
            canvas.drawText(ellipsize("🎁 Débloqué : " + unlockedLabels.joinToString(", "), textPaint, w * 0.92f),
                w / 2f, y, textPaint)
            y += w * 0.065f
        }
        val pts = profile.totalPoints
        Skins.nextUnlock(pts)?.let {
            textPaint.textSize = w * 0.036f
            canvas.drawText("Prochain : ${it.label} (encore ${it.cost - pts} pts)", w / 2f, y, textPaint)
        }
    }
}
