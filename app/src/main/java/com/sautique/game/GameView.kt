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

    private enum class State { READY, CHARGING, JUMPING, FALLING, BONUS, GAME_OVER, LEADERBOARD }
    private enum class WorldStatus { IDLE, LOADING, OK, ERROR, NOT_CONFIGURED }

    /** Bouton dessiné sur le canvas. */
    private class Button(val rect: RectF, val action: () -> Unit)

    private val cos30 = 0.8660254f
    private val sin30 = 0.5f

    // --- Réglages du gameplay (en unités du monde) ---
    private val maxChargeTime = 1.4f   // secondes d'appui pour la portée max
    private val maxReach = 5.6f        // portée max d'un saut
    private val platformDepth = 1.3f   // épaisseur des blocs
    private val playerHalf = 0.24f     // demi-largeur du cube personnage
    private val playerHeight = 0.55f

    // Niveau bonus
    private val bonusEvery = 10        // un bonus tous les 10 sauts réussis
    private val bonusReward = 5        // points gagnés si on réussit le bonus
    private val bonusIntro = 1.0f      // temps avant que le curseur parte (évite un appui involontaire)

    // Plateformes fragiles
    private val crumbleFromScore = 25

    // Frayeur quand on atterrit tout au bord
    private val scareMargin = 0.12f    // distance au bord (unités) en dessous de laquelle il a peur
    private val scareDuration = 1.2f

    private val palette = intArrayOf(
        Color.parseColor("#FF8A65"), Color.parseColor("#4DB6AC"), Color.parseColor("#FFD54F"),
        Color.parseColor("#9575CD"), Color.parseColor("#81C784"), Color.parseColor("#F06292"),
        Color.parseColor("#64B5F6"), Color.parseColor("#ECEFF1")
    )

    /** Une plateforme : un bloc carré ou un cylindre, centré en (cx, cz), dessus à y = 0. */
    private inner class Platform(
        val cx: Float, val cz: Float, val half: Float, val round: Boolean, color: Int,
        val crumbly: Boolean = false
    ) {
        /** Temps écoulé depuis le début de l'effritement (négatif = pas commencé). */
        var crumbleT = -1f
        var crumbleDur = 2f
        /** Chute de la plateforme une fois effondrée (unités vers le bas). */
        var dropY = 0f
        var dropV = 0f
        val collapsed get() = dropY > 0f
        private val crackPath = Path()
        private val crackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = k * 0.035f
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            this.color = Color.rgb(60, 45, 40)
        }

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
                        val dx = kotlin.math.cos(a2) * rr
                        val dz = sin(a2) * rr
                        crackPath.lineTo(isoX(dx, dz), isoY(dx, 0f, dz))
                    }
                }
            }
            val m = 0.13f
            markOval.set(-1.2247449f * k * m, -0.70710677f * k * m, 1.2247449f * k * m, 0.70710677f * k * m)
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

        fun draw(canvas: Canvas) {
            canvas.save()
            // Tremblement de plus en plus fort pendant l'effritement
            var shake = 0f
            if (crumbleT >= 0f && !collapsed) {
                val f = (crumbleT / crumbleDur).coerceIn(0f, 1f)
                shake = sin(time * 55f) * k * (0.015f + 0.05f * f)
            }
            canvas.translate(sx(cx, cz) + shake, sy(cx, 0f, cz) + dropY * k)
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
            if (crumbly) {
                val f = if (crumbleT < 0f) 0f else (crumbleT / crumbleDur).coerceIn(0f, 1f)
                crackPaint.alpha = (110 + 145 * f).toInt()
                canvas.drawPath(crackPath, crackPaint)
            }
            canvas.restore()
        }
    }

    private class Popup(val text: String, var x: Float, var y: Float, var life: Float)

    // --- Etat ---
    private var state = State.READY
    private var score = 0
    private val profile = LocalScores(context)
    private val online = ScoreServer(BuildConfig.SCORES_URL, BuildConfig.SCORES_KEY)
    private var best = profile.best
    private var newBest = false

    // Classements
    private var boardWorld = false            // onglet : false = perso, true = mondial
    private var boardReturnState = State.READY
    private var worldStatus = WorldStatus.IDLE
    private var worldEntries: List<ScoreServer.Entry> = emptyList()
    private var worldRank: Int? = null
    private var personalRuns: List<LocalScores.Run> = emptyList()
    private var syncing = false
    private var pseudoDialogShown = false
    private val buttons = ArrayList<Button>()
    private val dateFormat = java.text.SimpleDateFormat("dd/MM/yy", java.util.Locale.FRANCE)

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
    private var gameOverReason: String? = null
    private var time = 0f
    private var jumps = 0               // sauts réussis (pour déclencher les bonus)
    private var scareT = 0f
    private var crumbleHintShown = false

    // Bonus : curseur entre -1 et 1, zone verte entre -bonusGreen et +bonusGreen
    private var bonusCount = 0
    private var bonusT = 0f
    private var bonusPos = -1f
    private var bonusDir = 1f
    private var bonusSpeed = 1.4f
    private var bonusGreen = 0.28f
    private var bonusResult = 0         // 0 en cours, 1 réussi, -1 raté
    private var bonusResultT = 0f

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
    private val primaryBtnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF7A45") }
    private val secondaryBtnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(60, 255, 255, 255) }
    private val chipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(70, 0, 0, 0) }
    private val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FAFAFA") }
    private val tabOnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2E3A59") }
    private val tabOffPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E3E7EE") }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFE0D1") }
    private val btnTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val darkTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2E3A59")
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val greyTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#7A869A")
        textAlign = Paint.Align.CENTER
    }
    private val redZonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E53950") }
    private val greenZonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#3DBE6E") }
    private val cursorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val cursorStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#2E3A59")
    }
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val bangPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E53950")
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
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
        jumps = 0
        bonusCount = 0
        scareT = 0f
        crumbleHintShown = false
        gameOverReason = null
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
        // A partir de 25 points, de plus en plus de plateformes fragiles
        val crumbleChance = if (score < crumbleFromScore) 0f else min(0.5f + (score - crumbleFromScore) / 50f, 0.9f)
        val crumbly = Random.nextFloat() < crumbleChance
        // Le centre est aligné avec la position du personnage sur l'autre axe
        next = if (alongX) Platform(current.cx + dist, pz, half, round, color, crumbly)
        else Platform(px, current.cz + dist, half, round, color, crumbly)
        platforms.add(next)
        while (platforms.size > 6) platforms.removeAt(0)
    }

    private fun updateCameraTarget() {
        val mx = (current.cx + next.cx) / 2f
        val mz = (current.cz + next.cz) / 2f
        camTargetX = isoX(mx, mz)
        camTargetY = isoY(mx, 0f, mz)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (profile.pseudo.isNullOrBlank()) {
            post { askPseudo(first = true) }
        } else {
            syncOnline()
        }
    }

    /** Fenêtre de saisie du pseudo. */
    private fun askPseudo(first: Boolean) {
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
            }
            .apply { if (!first) setNegativeButton("Annuler", null) }
            .setOnDismissListener { pseudoDialogShown = false }
            .show()
    }

    /** Envoie le record et le pseudo au classement mondial si besoin. */
    private fun syncOnline() {
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

    private fun openBoard(world: Boolean) {
        if (state != State.LEADERBOARD) boardReturnState = state
        state = State.LEADERBOARD
        boardWorld = world
        personalRuns = profile.topRuns()
        if (world) loadWorld()
    }

    private fun loadWorld() {
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

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Les boutons affichés passent avant le saut
                val hit = buttons.firstOrNull { it.rect.contains(event.x, event.y) }
                if (hit != null) {
                    hit.action()
                    return true
                }
                when (state) {
                    State.READY -> {
                        state = State.CHARGING
                        charge = 0f
                    }
                    State.BONUS -> bonusTap()
                    else -> {}
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (state == State.CHARGING) startJump()
            }
        }
        return true
    }

    private fun startBonus() {
        bonusCount++
        bonusGreen = maxOf(0.08f, 0.28f - 0.04f * (bonusCount - 1))
        bonusSpeed = min(1.4f + 0.35f * (bonusCount - 1), 3.6f)
        bonusPos = -1f
        bonusDir = 1f
        bonusT = 0f
        bonusResult = 0
        bonusResultT = 0f
        state = State.BONUS
    }

    private fun bonusTap() {
        if (bonusResult != 0 || bonusT < bonusIntro) return
        bonusResultT = 0f
        if (abs(bonusPos) <= bonusGreen) {
            bonusResult = 1
            score += bonusReward
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        } else {
            bonusResult = -1
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    private fun endGame(reason: String?) {
        state = State.GAME_OVER
        gameOverTime = 0f
        gameOverReason = reason
        newBest = profile.addRun(score)
        best = profile.best
        syncOnline()
    }

    /** Lance l'effritement d'une plateforme fragile quand on atterrit dessus. */
    private fun startCrumble(p: Platform) {
        if (!p.crumbly || p.crumbleT >= 0f) return
        val f = ((score - crumbleFromScore) / 35f).coerceIn(0f, 1f)
        p.crumbleDur = 2.2f - 1.0f * f
        p.crumbleT = 0f
        if (!crumbleHintShown) {
            crumbleHintShown = true
            popups.add(Popup("Elle s'effrite, vite !", sx(px, pz) + camX, sy(px, playerHeight * 3.4f, pz) + camY, 1.4f))
        }
    }

    /** Vérifie si le personnage a atterri tout au bord : il tremble et a peur. */
    private fun checkScare(p: Platform): Boolean {
        if (p.edgeMargin(px, pz) >= scareMargin) return false
        scareT = scareDuration
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
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
        if (scareT > 0f) scareT = (scareT - dt).coerceAtLeast(0f)

        // Plateformes fragiles (en pause pendant le bonus et après la partie)
        if (state != State.BONUS && state != State.GAME_OVER && state != State.LEADERBOARD) {
            for (p in platforms) {
                if (p.collapsed) {
                    p.dropV += 30f * dt
                    p.dropY += p.dropV * dt
                } else if (p.crumbleT >= 0f) {
                    p.crumbleT += dt
                    if (p.crumbleT >= p.crumbleDur) {
                        p.dropY = 0.001f
                        p.dropV = 0f
                        val standing = (state == State.READY || state == State.CHARGING) && p === current
                        if (standing) {
                            fallVy = 0f
                            gameOverReason = "La plateforme s'est effondrée !"
                            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            state = State.FALLING
                        }
                    }
                }
            }
        }

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
                if (sy(px, py + playerHeight, pz) > h) endGame(gameOverReason)
            }

            State.BONUS -> {
                bonusT += dt
                if (bonusResult == 0) {
                    if (bonusT >= bonusIntro) {
                        bonusPos += bonusDir * bonusSpeed * dt
                        if (bonusPos > 1f) { bonusPos = 2f - bonusPos; bonusDir = -1f }
                        if (bonusPos < -1f) { bonusPos = -2f - bonusPos; bonusDir = 1f }
                    }
                } else {
                    bonusResultT += dt
                    if (bonusResultT > 1.0f) {
                        if (bonusResult > 0) state = State.READY else endGame("Bonus raté !")
                    }
                }
            }

            State.GAME_OVER -> gameOverTime += dt
            State.READY, State.LEADERBOARD -> {}
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
                jumps++
                landSquash = 1f
                val scared = checkScare(next)
                val perfect = !scared && abs(px - next.cx) < 0.13f && abs(pz - next.cz) < 0.13f
                popups.add(
                    Popup(
                        if (perfect) "Parfait ! +1" else "+1",
                        sx(px, pz) + camX, sy(px, playerHeight * 2.2f, pz) + camY, 0.8f
                    )
                )
                if (!scared) performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                current = next
                spawnNext()
                updateCameraTarget()
                startCrumble(current)
                if (jumps % bonusEvery == 0) startBonus() else state = State.READY
            }
            current.contains(px, pz) -> {
                // Saut trop court mais on reste sur sa plateforme : pas de point.
                landSquash = 1f
                checkScare(current)
                startCrumble(current)
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

        buttons.clear()

        if (state == State.READY && score == 0) {
            textPaint.textSize = w * 0.05f
            canvas.drawText("Maintiens appuyé pour sauter", w / 2f, h * 0.88f, textPaint)
            textPaint.textSize = w * 0.04f
            canvas.drawText("plus longtemps = plus loin", w / 2f, h * 0.88f + w * 0.07f, textPaint)

            // Raccourcis en haut : pseudo à gauche, classement à droite
            val ch = w * 0.085f
            val top = w * 0.04f
            drawButton(canvas, RectF(w * 0.03f, top, w * 0.33f, top + ch), "✎ " + (profile.pseudo ?: "Pseudo"), chipPaint, w * 0.036f) {
                askPseudo(first = false)
            }
            drawButton(canvas, RectF(w * 0.67f, top, w * 0.97f, top + ch), "🏆 Classement", chipPaint, w * 0.036f) {
                openBoard(world = false)
            }
        }

        if (state == State.BONUS) drawBonus(canvas)
        if (state == State.GAME_OVER) drawGameOver(canvas)
        if (state == State.LEADERBOARD) drawLeaderboard(canvas)

        postInvalidateOnAnimation()
    }

    /** Dessine un bouton arrondi et l'enregistre pour la détection des appuis. */
    private fun drawButton(canvas: Canvas, r: RectF, label: String, bg: Paint, textSize: Float, action: () -> Unit) {
        val radius = r.height() / 2f
        canvas.drawRoundRect(r, radius, radius, bg)
        btnTextPaint.textSize = textSize
        btnTextPaint.color = if (bg === tabOffPaint) Color.parseColor("#2E3A59") else Color.WHITE
        val label2 = ellipsize(label, btnTextPaint, r.width() - radius)
        canvas.drawText(label2, r.centerX(), r.centerY() - (btnTextPaint.descent() + btnTextPaint.ascent()) / 2f, btnTextPaint)
        buttons.add(Button(RectF(r), action))
    }

    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var t = text
        while (t.length > 1 && paint.measureText("$t…") > maxWidth) t = t.dropLast(1)
        return "$t…"
    }

    private fun drawLeaderboard(canvas: Canvas) {
        canvas.drawRect(0f, 0f, w, h, overlayPaint)
        val left = w * 0.06f
        val right = w * 0.94f
        val top = h * 0.08f
        val bottom = h * 0.92f
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

    private fun drawBonus(canvas: Canvas) {
        canvas.drawRect(0f, 0f, w, h, overlayPaint)
        val cy = h * 0.5f
        textPaint.textSize = w * 0.13f
        canvas.drawText("BONUS", w / 2f, cy - w * 0.28f, textPaint)
        textPaint.textSize = w * 0.045f
        canvas.drawText("Touche quand le curseur est dans le vert", w / 2f, cy - w * 0.17f, textPaint)

        // Barre : rouge aux extrémités, vert au milieu
        val left = w * 0.1f
        val right = w * 0.9f
        val bh = w * 0.1f
        val half = (right - left) / 2f
        val mid = (left + right) / 2f
        rect.set(left, cy - bh / 2, right, cy + bh / 2)
        canvas.drawRoundRect(rect, bh / 2, bh / 2, redZonePaint)
        rect.set(mid - half * bonusGreen, cy - bh / 2, mid + half * bonusGreen, cy + bh / 2)
        canvas.drawRect(rect, greenZonePaint)

        // Curseur
        val cx = mid + half * bonusPos
        val cw = w * 0.025f
        val ch = bh + w * 0.07f
        rect.set(cx - cw / 2, cy - ch / 2, cx + cw / 2, cy + ch / 2)
        cursorStroke.strokeWidth = w * 0.008f
        canvas.drawRoundRect(rect, cw / 2, cw / 2, cursorPaint)
        canvas.drawRoundRect(rect, cw / 2, cw / 2, cursorStroke)

        val msg = when {
            bonusResult > 0 -> "Bravo ! +$bonusReward"
            bonusResult < 0 -> "Raté !"
            bonusT < bonusIntro -> "Prêt ?"
            else -> ""
        }
        if (msg.isNotEmpty()) {
            textPaint.textSize = w * 0.09f
            canvas.drawText(msg, w / 2f, cy + w * 0.25f, textPaint)
        }
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

        // Tremblement de peur
        val scared = scareT > 0f
        val trembleX = if (scared) sin(time * 75f) * k * 0.035f * (scareT / scareDuration).coerceAtLeast(0.3f) else 0f

        canvas.save()
        canvas.translate(sx(px, pz) + trembleX, sy(px, 0f, pz))
        // face gauche (x min), face droite (z min), dessus
        quad(tmpPath, -a, y1, a, -a, y1, -a, -a, y0, -a, -a, y0, a)
        canvas.drawPath(tmpPath, playerLeft)
        quad(tmpPath, -a, y1, -a, a, y1, -a, a, y0, -a, -a, y0, -a)
        canvas.drawPath(tmpPath, playerRight)
        quad(tmpPath, -a, y1, -a, a, y1, -a, a, y1, a, -a, y1, a)
        canvas.drawPath(tmpPath, playerTop)

        // Yeux sur la face visible, du côté où il va sauter
        val eyeY = y0 + hgt * 0.68f
        val eyeR = a * (if (scared) 0.3f else 0.24f) * k
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
                val pupil = if (scared) 0.32f else 0.5f
                canvas.drawCircle(ex + dir * eyeR * 0.35f, ey - eyeR * 0.15f, eyeR * pupil, pupilPaint)
            }
        }

        // "!" au-dessus de la tête quand il a eu peur
        if (scared) {
            val elapsed = scareDuration - scareT
            val pop = min(1f, elapsed / 0.15f)
            val bx = 0f
            val by = isoY(0f, y1 + 0.5f, 0f)
            val r = k * 0.2f * pop
            canvas.drawCircle(bx, by, r, bubblePaint)
            bangPaint.textSize = r * 1.5f
            canvas.drawText("!", bx, by - (bangPaint.descent() + bangPaint.ascent()) / 2f, bangPaint)
        }
        canvas.restore()
    }

    private fun drawGameOver(canvas: Canvas) {
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
            drawButton(canvas, RectF(w * 0.2f, y, w * 0.8f, y + sh), "✎ Pseudo : " + (profile.pseudo ?: "?"),
                chipPaint, w * 0.04f) { askPseudo(first = false) }
        }
    }
}
