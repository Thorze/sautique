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

/** Fenêtre de saisie du pseudo (vérifie qu'il n'est pas déjà pris). */
internal fun GameView.askPseudo(first: Boolean, error: String? = null) {
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
    val mustChoose = first || pseudoConflict
    android.app.AlertDialog.Builder(context)
        .setTitle(if (first) "Choisis ton pseudo" else "Changer de pseudo")
        .setMessage(error ?: "Il apparaîtra dans le classement mondial.")
        .setView(container)
        .setCancelable(!mustChoose)
        .setPositiveButton("OK") { _, _ ->
            val p = input.text.toString().trim()
            if (p.isEmpty()) {
                if (profile.pseudo.isNullOrBlank()) profile.pseudo = "Joueur" + Random.nextInt(1000, 10000)
                afterPseudo()
                return@setPositiveButton
            }
            // Vérifie auprès du serveur que personne d'autre ne l'utilise
            online.checkPseudo(p, profile.playerId) { free ->
                if (free == false) {
                    post { askPseudo(first, "« $p » est déjà pris, choisis-en un autre.") }
                } else {
                    profile.pseudo = p
                    pseudoConflict = false
                    afterPseudo()
                }
            }
        }
        .apply { if (!mustChoose) setNegativeButton("Annuler", null) }
        .setOnDismissListener { pseudoDialogShown = false }
        .show()
}

private fun GameView.afterPseudo() {
    syncOnline()
    if (!profile.customized) openCustomize()
}

/** Envoie la meilleure partie en attente (et le pseudo) au serveur. */
internal fun GameView.syncOnline() {
    val pseudo = profile.pseudo
    if (syncing || !online.configured || pseudo.isNullOrBlank() || !profile.needsSync()) return
    syncing = true
    val score = profile.pendingScore
    online.submit(profile.playerId, pseudo, score, profile.pendingDuration, profile.pendingJumps) { result ->
        syncing = false
        when (result) {
            ScoreServer.SubmitResult.OK -> {
                profile.clearPending(score)
                profile.syncedPseudo = pseudo
                if (state == State.LEADERBOARD && boardTab > 0) loadWorld()
                // Le pseudo ou une nouvelle partie a pu arriver pendant l'envoi
                if (profile.needsSync() && (profile.pendingScore != score || profile.pseudo != pseudo)) syncOnline()
            }
            ScoreServer.SubmitResult.PSEUDO_TAKEN -> {
                pseudoConflict = true
                askPseudo(false, "Le pseudo « $pseudo » est déjà utilisé par un autre joueur. Choisis-en un autre.")
            }
            // Score refusé par le serveur : on ne réessaie pas
            ScoreServer.SubmitResult.REJECTED -> {
                profile.clearPending(score)
                profile.syncedPseudo = pseudo
            }
            ScoreServer.SubmitResult.ERROR -> {}
        }
    }
}

/** Classement : 0 = perso, 1 = semaine, 2 = de tous les temps. */
internal fun GameView.openBoard(tab: Int) {
    if (state != State.LEADERBOARD) boardReturnState = state
    state = State.LEADERBOARD
    boardTab = tab
    personalRuns = profile.topRuns()
    if (tab > 0) loadWorld()
}

internal fun GameView.loadWorld() {
    if (!online.configured) {
        worldStatus = WorldStatus.NOT_CONFIGURED
        return
    }
    worldStatus = WorldStatus.LOADING
    worldRank = null
    val tab = boardTab
    online.fetchBoard(10, profile.playerId, if (tab == 1) "week" else "all") { board ->
        if (boardTab != tab) return@fetchBoard
        if (board == null) {
            worldStatus = WorldStatus.ERROR
        } else {
            worldEntries = board.top
            worldRank = board.myRank
            worldStatus = WorldStatus.OK
        }
    }
}

internal fun GameView.openMissions() {
    if (state == State.MISSIONS) return
    missionsReturnState = state
    state = State.MISSIONS
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
    btnTextPaint.color = if (bg === tabOffPaint || bg === lockedPaint || bg === shopPaint || (bg === itemPaint && isLight(bg.color)))
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
    val tabs = listOf("Perso", "Semaine", "Mondial")
    val tw = (right - left - w * 0.08f) / tabs.size
    tabs.forEachIndexed { i, label ->
        val x0 = left + w * 0.04f + i * tw
        drawButton(canvas, RectF(x0 + w * 0.006f, tabTop, x0 + tw - w * 0.006f, tabTop + tabH), label,
            if (boardTab == i) tabOnPaint else tabOffPaint, w * 0.04f) { openBoard(i) }
    }

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

    if (boardTab == 0) {
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
            rank != null && boardTab == 1 -> "${profile.pseudo ?: ""} : place n°$rank cette semaine"
            rank != null -> "${profile.pseudo ?: ""} : place n°$rank  (record $best)"
            boardTab == 1 -> "Pas encore de score cette semaine"
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
    val points = if (perfectStreak >= 3) Scoring.perfectPoints(perfectStreak) else lastPerfectPoints
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
    val bottom = top + w * 1.12f
    rect.set(left, top, right, bottom)
    canvas.drawRoundRect(rect, w * 0.05f, w * 0.05f, panelPaint)
    darkTextPaint.textAlign = Paint.Align.CENTER
    darkTextPaint.textSize = w * 0.07f
    canvas.drawText("Son et vibrations", w / 2f, top + w * 0.11f, darkTextPaint)

    val sl = left + w * 0.08f
    val sr = right - w * 0.08f
    val bh = w * 0.035f
    sfxSlider.set(sl, top + w * 0.3f, sr, top + w * 0.3f + bh)
    musicSlider.set(sl, top + w * 0.55f, sr, top + w * 0.55f + bh)
    drawSlider(canvas, sfxSlider, "Effets sonores", sounds.soundVolume)
    drawSlider(canvas, musicSlider, "Musique", sounds.musicVolume)
    drawButton(canvas, RectF(sl, top + w * 0.68f, sr, top + w * 0.79f),
        "📳 Vibrations : " + (if (profile.vibrationOn) "oui" else "non"),
        if (profile.vibrationOn) tabOnPaint else tabOffPaint, w * 0.042f) {
        profile.vibrationOn = !profile.vibrationOn
        if (profile.vibrationOn) vibrate(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
    }

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

    // Mise en page adaptative : le bas (texte + OK) est fixe, la peinture juste au-dessus,
    // l'aperçu et les boutons rétrécissent si l'écran est court.
    val footY = bottom - w * 0.2f
    val tintable = custSlot in Skins.tintable
    val tintH = w * 0.17f
    val tintTop = footY - w * 0.05f - tintH
    val tabH = w * 0.085f
    val tabsH = 2 * tabH + w * 0.015f
    val idealGrid = 6 * w * 0.104f      // l'onglet Couleur a le plus d'éléments
    val avail = (footY - w * 0.05f - tintH) - (top + w * 0.14f) - tabsH - w * 0.05f - idealGrid
    val previewH = avail.coerceIn(w * 0.2f, w * 0.36f)
    val pScale = 2f * (previewH / (w * 0.36f)).coerceIn(0.6f, 1f)

    // Aperçu (regarde à droite puis à gauche)
    val pcx = w / 2f
    val pcy = top + w * 0.13f + previewH * 0.8f
    rect.set(pcx - k * 0.375f * pScale, pcy - k * 0.15f * pScale, pcx + k * 0.375f * pScale, pcy + k * 0.15f * pScale)
    canvas.drawOval(rect, shadowPaint)
    canvas.save()
    canvas.translate(pcx, pcy)
    canvas.scale(pScale, pScale)
    val dirX = (time / 2.5f).toInt() % 2 == 0
    drawCharacter(canvas, 0f, 0f, 0f, 1f, 1f, dirX, scared = false, front = false)
    canvas.restore()

    // Onglets
    val tabTop = top + w * 0.13f + previewH + w * 0.02f
    val slots = Skins.Slot.values()
    // Onglets sur deux lignes (4 + 3)
    slots.forEachIndexed { i, slot ->
        val row = if (i < 4) 0 else 1
        val inRow = if (row == 0) 4 else slots.size - 4
        val col = if (row == 0) i else i - 4
        val cw = (right - left - w * 0.04f) / inRow
        val x0 = left + w * 0.02f + col * cw
        val y0 = tabTop + row * (tabH + w * 0.015f)
        drawButton(
            canvas, RectF(x0 + w * 0.006f, y0, x0 + cw - w * 0.006f, y0 + tabH), slot.title,
            if (slot == custSlot) tabOnPaint else tabOffPaint, w * 0.034f
        ) { custSlot = slot }
    }

    // Eléments de l'onglet
    val pts = profile.totalPoints
    val selected = currentLook(custSlot)
    val gridTop = tabTop + tabsH + w * 0.035f
    val rows = (Skins.bySlot(custSlot).size + 1) / 2
    val gridBottom = (if (tintable) tintTop else footY - w * 0.05f) - w * 0.01f
    val step = min(w * 0.104f, (gridBottom - gridTop) / rows)
    val cellH = step * 0.85f
    val cellW = (right - left - w * 0.08f) / 2f
    Skins.bySlot(custSlot).forEachIndexed { i, item ->
        val col = i % 2
        val row = i / 2
        val x0 = left + w * 0.04f + col * cellW
        val y0 = gridTop + row * step
        val r = RectF(x0 + w * 0.01f, y0, x0 + cellW - w * 0.01f, y0 + cellH)
        val unlocked = profile.available(item)
        val isSel = item.id == selected
        val label = when {
            !unlocked && item.isShop -> "🪙 ${item.price} · ${item.label}"
            !unlocked -> "🔒 ${item.label} · ${item.cost}"
            isSel -> "✓ ${item.label}"
            else -> item.label
        }
        val bg = when {
            !unlocked && item.isShop -> shopPaint
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
        drawButton(canvas, r, label, bg, min(w * 0.036f, cellH * 0.42f)) {
            when {
                unlocked -> {
                    profile.select(custSlot, item.id)
                    loadLook()
                }
                item.isShop && profile.coins < item.price -> {
                    custMsg = "Il te manque ${item.price - profile.coins} 🪙 pour « ${item.label} »"
                    custMsgT = 2.5f
                }
                // Achat : un premier appui demande confirmation, le second achète
                item.isShop && pendingBuy === item && custMsgT > 0f -> {
                    if (profile.buy(item)) {
                        profile.select(custSlot, item.id)
                        loadLook()
                        custMsg = "🎉 « ${item.label} » acheté !"
                        custMsgT = 2.5f
                        sounds.play(Sounds.Sfx.UNLOCK)
                    }
                    pendingBuy = null
                }
                item.isShop -> {
                    pendingBuy = item
                    custMsg = "Touche encore pour acheter « ${item.label} » (${item.price} 🪙)"
                    custMsgT = 3f
                }
                else -> {
                    custMsg = "🔒 Se débloque à ${item.cost} points cumulés"
                    custMsgT = 2.5f
                }
            }
        }
    }

    // Peintures pour l'objet de cet onglet
    if (tintable) {
        // Juste sous les objets s'il y a la place, sinon calée au-dessus du texte du bas
        val ty = min(gridTop + rows * step + w * 0.02f, tintTop) + w * 0.035f
        darkTextPaint.textAlign = Paint.Align.LEFT
        darkTextPaint.textSize = w * 0.036f
        canvas.drawText("Peinture de l'objet", left + w * 0.05f, ty, darkTextPaint)
        val chosen = profile.selectedTint(custSlot)
        val count = Skins.tints.size
        val tstep = (right - left - w * 0.1f) / count
        val rad = min(tstep * 0.4f, w * 0.04f)
        Skins.tints.forEachIndexed { i, t ->
            val cx = left + w * 0.05f + tstep * (i + 0.5f)
            val cy = ty + w * 0.065f
            val owned = profile.ownsTint(t)
            if (t.id == Skins.ORIGINAL) {
                itemPaint.color = Color.parseColor("#E3E7EE")
                canvas.drawCircle(cx, cy, rad, itemPaint)
                bubbleStroke.strokeWidth = w * 0.006f
                canvas.drawLine(cx - rad * 0.6f, cy + rad * 0.6f, cx + rad * 0.6f, cy - rad * 0.6f, bubbleStroke)
            } else {
                itemPaint.color = t.color
                canvas.drawCircle(cx, cy, rad, itemPaint)
            }
            if (t.id == chosen) {
                bubbleStroke.strokeWidth = w * 0.008f
                canvas.drawCircle(cx, cy, rad + w * 0.01f, bubbleStroke)
            }
            if (!owned) {
                greyTextPaint.textSize = w * 0.026f
                canvas.drawText("${t.price}", cx, cy + rad + w * 0.035f, greyTextPaint)
            }
            val hit = RectF(cx - tstep / 2f, cy - rad - w * 0.02f, cx + tstep / 2f, cy + rad + w * 0.04f)
            buttons.add(Button(hit) {
                when {
                    owned -> {
                        profile.selectTint(custSlot, t.id)
                        loadLook()
                    }
                    profile.coins < t.price -> {
                        custMsg = "Il te manque ${t.price - profile.coins} 🪙 pour la peinture ${t.label.lowercase()}"
                        custMsgT = 2.5f
                    }
                    pendingTint === t && custMsgT > 0f -> {
                        if (profile.buyTint(t)) {
                            profile.selectTint(custSlot, t.id)
                            loadLook()
                            custMsg = "🎨 Peinture ${t.label.lowercase()} achetée ! Utilisable sur tous les objets"
                            custMsgT = 3f
                            sounds.play(Sounds.Sfx.UNLOCK)
                        }
                        pendingTint = null
                    }
                    else -> {
                        pendingTint = t
                        custMsg = "Touche encore pour acheter la peinture ${t.label.lowercase()} (${t.price} 🪙)"
                        custMsgT = 3f
                    }
                }
            })
        }
    }

    // Bas : points cumulés et validation
    greyTextPaint.textSize = w * 0.038f
    val nextItem = Skins.nextUnlock(pts)
    val info = when {
        custMsgT > 0f -> custMsg
        nextItem != null -> "🪙 ${profile.coins} · $pts pts · prochain : ${nextItem.label} à ${nextItem.cost}"
        else -> "🪙 ${profile.coins} · $pts pts · tout est débloqué !"
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
    if (coinsThisRun > 0) {
        textPaint.textSize = w * 0.045f
        canvas.drawText("+$coinsThisRun 🪙 ramassées (total ${profile.coins})", w / 2f, y0 + w * 0.31f, textPaint)
    }
    if (gameOverTime > 0.6f) {
        val bh = w * 0.13f
        var y = y0 + w * 0.36f
        drawButton(canvas, RectF(w * 0.2f, y, w * 0.8f, y + bh), "Rejouer", primaryBtnPaint, w * 0.06f) { resetGame() }
        y += bh + w * 0.04f
        val sh = w * 0.11f
        drawButton(canvas, RectF(w * 0.1f, y, w * 0.49f, y + sh), "🏆 Classement", secondaryBtnPaint, w * 0.042f) {
            openBoard(1)
        }
        drawButton(canvas, RectF(w * 0.51f, y, w * 0.9f, y + sh), "📋 Missions", secondaryBtnPaint, w * 0.042f) {
            openMissions()
        }
        y += sh + w * 0.04f
        drawButton(canvas, RectF(w * 0.1f, y, w * 0.49f, y + sh), "✎ " + (profile.pseudo ?: "Pseudo"),
            chipPaint, w * 0.04f) { askPseudo(first = false) }
        drawButton(canvas, RectF(w * 0.51f, y, w * 0.9f, y + sh), "🎨 Personnage",
            chipPaint, w * 0.04f) { openCustomize() }
        y += sh + w * 0.08f
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

/** Écran de pause. */
internal fun GameView.drawPause(canvas: Canvas) {
    canvas.drawRect(0f, 0f, w, h, overlayPaint)
    val y0 = h * 0.3f
    textPaint.textSize = w * 0.12f
    canvas.drawText("Pause", w / 2f, y0, textPaint)
    textPaint.textSize = w * 0.045f
    canvas.drawText("Score : $score", w / 2f, y0 + w * 0.09f, textPaint)
    val bh = w * 0.13f
    var y = y0 + w * 0.17f
    drawButton(canvas, RectF(w * 0.2f, y, w * 0.8f, y + bh), "▶ Reprendre", primaryBtnPaint, w * 0.06f) { resumeGame() }
    y += bh + w * 0.04f
    val sh = w * 0.11f
    drawButton(canvas, RectF(w * 0.2f, y, w * 0.8f, y + sh), "🔊 Son", secondaryBtnPaint, w * 0.045f) { openSettings() }
    y += sh + w * 0.03f
    drawButton(canvas, RectF(w * 0.2f, y, w * 0.8f, y + sh),
        "📳 Vibrations : " + (if (profile.vibrationOn) "oui" else "non"), secondaryBtnPaint, w * 0.045f) {
        profile.vibrationOn = !profile.vibrationOn
        if (profile.vibrationOn) vibrate(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
    }
    y += sh + w * 0.03f
    drawButton(canvas, RectF(w * 0.2f, y, w * 0.8f, y + sh), "Abandonner", chipPaint, w * 0.045f) {
        endGame("Partie abandonnée")
    }
}

/** Missions et défi du jour. */
internal fun GameView.drawMissions(canvas: Canvas) {
    canvas.drawRect(0f, 0f, w, h, overlayPaint)
    val left = w * 0.05f
    val right = w * 0.95f
    val top = insetTop + h * 0.06f
    val bottom = ub - h * 0.04f
    rect.set(left, top, right, bottom)
    canvas.drawRoundRect(rect, w * 0.05f, w * 0.05f, panelPaint)
    darkTextPaint.textAlign = Paint.Align.CENTER
    darkTextPaint.textSize = w * 0.07f
    canvas.drawText("Missions du jour", w / 2f, top + w * 0.11f, darkTextPaint)
    greyTextPaint.textSize = w * 0.04f
    canvas.drawText("🪙 ${profile.coins} pièces · nouvelles missions chaque jour", w / 2f, top + w * 0.18f, greyTextPaint)

    val list = missions.list()
    var y = top + w * 0.26f
    val cardH = w * 0.2f
    list.forEachIndexed { i, m ->
        if (m.challenge) {
            darkTextPaint.textAlign = Paint.Align.LEFT
            darkTextPaint.textSize = w * 0.045f
            canvas.drawText("🏅 Défi du jour", left + w * 0.05f, y + w * 0.06f, darkTextPaint)
            y += w * 0.09f
        }
        val done = missions.isDone(i)
        val p = missions.progress(i)
        rect.set(left + w * 0.04f, y, right - w * 0.04f, y + cardH)
        canvas.drawRoundRect(rect, w * 0.03f, w * 0.03f, if (done) highlightPaint else tabOffPaint)
        darkTextPaint.textAlign = Paint.Align.LEFT
        darkTextPaint.textSize = w * 0.042f
        val label = (if (done) "✅ " else "") + m.label
        canvas.drawText(ellipsize(label, darkTextPaint, right - left - w * 0.3f), left + w * 0.08f, y + w * 0.075f, darkTextPaint)
        darkTextPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("+${m.reward} 🪙", right - w * 0.08f, y + w * 0.075f, darkTextPaint)
        // Barre de progression
        val bl = left + w * 0.08f
        val br = right - w * 0.08f
        val by = y + w * 0.12f
        val bh = w * 0.035f
        rect.set(bl, by, br, by + bh)
        canvas.drawRoundRect(rect, bh / 2f, bh / 2f, lockedPaint)
        val f = (p.toFloat() / m.target).coerceIn(0f, 1f)
        if (f > 0f) {
            rect.set(bl, by, bl + (br - bl) * f, by + bh)
            canvas.drawRoundRect(rect, bh / 2f, bh / 2f, if (done) greenZonePaint else primaryBtnPaint)
        }
        greyTextPaint.textSize = w * 0.032f
        canvas.drawText("$p / ${m.target}", w / 2f, by + bh + w * 0.035f, greyTextPaint)
        y += cardH + w * 0.03f
    }
    greyTextPaint.textSize = w * 0.038f
    val allDone = missions.allMissionsDone()
    canvas.drawText(
        if (allDone) "🎉 Les 3 missions sont terminées !"
        else "Termine les 3 missions : +${DailyMissions.ALL_DONE_REWARD} 🪙 en plus",
        w / 2f, bottom - w * 0.2f, greyTextPaint
    )
    drawButton(canvas, RectF(w * 0.3f, bottom - w * 0.15f, w * 0.7f, bottom - w * 0.05f), "OK",
        primaryBtnPaint, w * 0.05f) { state = missionsReturnState }
}
