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

    private enum class State { READY, CHARGING, JUMPING, FALLING, BONUS, GAME_OVER, LEADERBOARD, CUSTOMIZE, SETTINGS }
    private enum class FallAnim { SPIN, PANIC, PARACHUTE, CARTOON }
    private enum class JumpTrick { NONE, FRONTFLIP, BACKFLIP, PIROUETTE, TUCK }
    private enum class BonusType(val title: String, val hint: String) {
        TIMING("Curseur", "Touche quand le curseur est dans le vert"),
        MASH("Tape-tape", "Tape le plus vite possible !"),
        STARS("Attrape les étoiles", "Touche les étoiles avant qu'elles filent"),
        WHEEL("Roue de la fortune", "Touche pour arrêter la roue")
    }
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
    private val bonusIntro = 1.2f      // temps avant le début (évite un appui involontaire)
    private val mashDuration = 3f
    private val starsDuration = 6f
    private val wheelValues = intArrayOf(2, 5, 0, 3, 10, 1, 4, 0)

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
    val sounds = Sounds(context)
    private var lastWheelIdx = -1
    private var settingsReturnState = State.READY
    private val sfxSlider = RectF()
    private val musicSlider = RectF()
    private var dragSlider = 0          // 0 aucun, 1 effets, 2 musique
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
    private var fallT = 0f
    private var fallAnim = FallAnim.SPIN

    // Apparence du personnage
    private var lookShape = "cube"
    private var lookGlasses = Skins.NONE
    private var lookHat = Skins.NONE
    private var lookShoes = Skins.NONE
    private var unlockedLabels: List<String> = emptyList()

    // Ecran Personnage
    private var custSlot = Skins.Slot.SHAPE
    private var custReturnState = State.READY
    private var custMsg = ""
    private var custMsgT = 0f
    private var landSquash = 0f
    private var gameOverTime = 0f
    private var gameOverReason: String? = null
    private var time = 0f
    private var jumps = 0               // sauts réussis (pour déclencher les bonus)
    private var scareT = 0f
    private var crumbleHintShown = false

    // Série de parfaits : la barre « Perfect » apparaît à partir du 3e d'affilée
    private var perfectStreak = 0
    private var perfectBarAlpha = 0f
    private var perfectPulse = 0f
    private var lastPerfectPoints = 2
    private var shownStreak = 3          // valeur affichée pendant que la barre disparaît
    private val perfectBarPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val perfectBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(110, 30, 30, 50) }

    // Bonus : curseur entre -1 et 1, zone verte entre -bonusGreen et +bonusGreen
    private var bonusCount = 0
    private var bonusT = 0f
    private var bonusPos = -1f
    private var bonusDir = 1f
    private var bonusSpeed = 1.4f
    private var bonusGreen = 0.28f
    private var bonusResult = 0         // 0 en cours, 1 terminé
    private var bonusResultT = 0f
    private var bonusType = BonusType.TIMING
    private var bonusGain = 0
    private var bonusTaps = 0           // tapes (Tape-tape) ou étoiles attrapées
    private var bonusPulse = 0f
    private val stars = ArrayList<FloatArray>()     // x, y, vie restante, vie totale
    private val sparkles = ArrayList<FloatArray>()  // x, y, vie restante
    private var starSpawn = 0f
    private var wheelAngle = 0f
    private var wheelSpeed = 0f
    private var wheelStopping = false
    private var jumpTrick = JumpTrick.NONE
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFD54F") }
    private val wheelText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

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
    // Zones occupées par les barres système (encoche en haut, boutons de navigation en bas)
    private var insetTop = 0f
    private var insetBottom = 0f
    /** Bas de la zone utilisable, au-dessus des boutons de navigation. */
    private val ub get() = h - insetBottom
    private var lastFrameNs = 0L

    // --- Peintures ---
    private val bgPaint = Paint()
    private val playerTop = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFA270") }
    private val playerLeft = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF7A45") }
    private val playerRight = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#D95B2B") }
    private val accPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val accStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val lockedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#C9CED8") }
    private val itemPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bubbleTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2E3A59")
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val bubbleStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#2E3A59")
    }
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
        loadLook()
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
        perfectStreak = 0
        perfectBarAlpha = 0f
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

    override fun onApplyWindowInsets(insets: android.view.WindowInsets): android.view.WindowInsets {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val bars = insets.getInsets(
                android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout()
            )
            insetTop = bars.top.toFloat()
            insetBottom = bars.bottom.toFloat()
        } else {
            @Suppress("DEPRECATION")
            insetTop = insets.systemWindowInsetTop.toFloat()
            @Suppress("DEPRECATION")
            insetBottom = insets.systemWindowInsetBottom.toFloat()
        }
        return super.onApplyWindowInsets(insets)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        requestApplyInsets()
        onAttachedToWindowGame()
    }

    private fun onAttachedToWindowGame() {
        if (profile.pseudo.isNullOrBlank()) {
            post { askPseudo(first = true) }
        } else {
            syncOnline()
            if (!profile.customized) post { openCustomize() }
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
                if (!profile.customized) openCustomize()
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
            MotionEvent.ACTION_MOVE -> if (state == State.SETTINGS) dragTo(event.x)
            MotionEvent.ACTION_DOWN -> {
                if (state == State.SETTINGS) {
                    val pad = w * 0.06f
                    dragSlider = when {
                        event.y in sfxSlider.top - pad..sfxSlider.bottom + pad -> 1
                        event.y in musicSlider.top - pad..musicSlider.bottom + pad -> 2
                        else -> 0
                    }
                    if (dragSlider != 0) {
                        dragTo(event.x)
                        return true
                    }
                }
                // Les boutons affichés passent avant le saut
                val hit = buttons.firstOrNull { it.rect.contains(event.x, event.y) }
                if (hit != null) {
                    sounds.play(Sounds.Sfx.CLICK, 0.6f)
                    hit.action()
                    return true
                }
                when (state) {
                    State.READY -> {
                        state = State.CHARGING
                        charge = 0f
                    }
                    State.BONUS -> bonusTap(event.x, event.y)
                    else -> {}
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragSlider == 1) sounds.play(Sounds.Sfx.GREAT)
                dragSlider = 0
                if (state == State.CHARGING) startJump()
            }
        }
        return true
    }

    /** Charge l'apparence choisie (et repasse sur un élément gratuit s'il n'est plus débloqué). */
    private fun loadLook() {
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

    private fun openCustomize() {
        if (state == State.CUSTOMIZE) return
        custReturnState = state
        custMsg = ""
        state = State.CUSTOMIZE
    }

    private fun closeCustomize() {
        profile.customized = true
        state = custReturnState
    }

    private fun openSettings() {
        if (state == State.SETTINGS) return
        settingsReturnState = state
        state = State.SETTINGS
    }

    private fun dragTo(x: Float) {
        val r = if (dragSlider == 1) sfxSlider else if (dragSlider == 2) musicSlider else return
        val v = ((x - r.left) / r.width()).coerceIn(0f, 1f)
        val snapped = if (v < 0.03f) 0f else v
        if (dragSlider == 1) sounds.soundVolume = snapped else sounds.musicVolume = snapped
    }

    /** Le personnage rate : chute avec une animation choisie au hasard. */
    private fun startFall() {
        fallVy = 0f
        fallT = 0f
        fallAnim = FallAnim.values()[Random.nextInt(FallAnim.values().size)]
        perfectStreak = 0
        sounds.play(Sounds.Sfx.FALL)
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        state = State.FALLING
    }

    private fun startBonus() {
        bonusCount++
        // Un type au hasard, différent du précédent
        val types = BonusType.values().filter { bonusCount == 1 || it != bonusType }
        bonusType = types[Random.nextInt(types.size)]
        bonusGreen = maxOf(0.08f, 0.28f - 0.03f * (bonusCount - 1))
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

    private fun bonusTap(x: Float, y: Float) {
        if (bonusResult != 0 || bonusT < bonusIntro) return
        when (bonusType) {
            BonusType.TIMING -> finishBonus(if (abs(bonusPos) <= bonusGreen) bonusReward else 0)
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
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                }
            }
            BonusType.WHEEL -> wheelStopping = true
        }
    }

    /** Fin du bonus : ajoute les points gagnés (0 = raté, la partie continue). */
    private fun finishBonus(gain: Int) {
        bonusGain = gain
        score += gain
        bonusResult = 1
        bonusResultT = 0f
        sounds.play(if (gain > 0) Sounds.Sfx.BONUS_WIN else Sounds.Sfx.BONUS_LOSE)
        performHapticFeedback(if (gain > 0) HapticFeedbackConstants.VIRTUAL_KEY else HapticFeedbackConstants.LONG_PRESS)
    }

    /** Case de la roue sous le repère (en haut). */
    private fun wheelIndex(): Int {
        val seg = 360f / wheelValues.size
        val a = ((270f - wheelAngle) % 360f + 360f) % 360f
        return (a / seg).toInt().coerceIn(0, wheelValues.size - 1)
    }

    private fun endGame(reason: String?) {
        state = State.GAME_OVER
        gameOverTime = 0f
        gameOverReason = reason
        val before = profile.totalPoints
        newBest = profile.addRun(score)
        unlockedLabels = Skins.unlockedBetween(before, profile.totalPoints).map { it.label }
        val unlocked = unlockedLabels.isNotEmpty()
        // Musique de défaite, puis « congratulations » si un élément a été débloqué
        sounds.playGameOver { if (unlocked) sounds.play(Sounds.Sfx.UNLOCK) }
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
        sounds.play(Sounds.Sfx.SCARE)
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
        sounds.play(Sounds.Sfx.JUMP, 1f, 0.9f + 0.3f * ratio)
        // Petite figure en l'air sur les sauts assez longs
        val tricks = JumpTrick.values().filter { it != JumpTrick.NONE }
        jumpTrick = if (ratio < 0.3f) JumpTrick.NONE else tricks[Random.nextInt(tricks.size)]
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
        if (perfectPulse > 0f) perfectPulse = (perfectPulse - dt * 3f).coerceAtLeast(0f)
        val barTarget = if (perfectStreak >= 3) 1f else 0f
        perfectBarAlpha += (barTarget - perfectBarAlpha) * min(1f, dt * 8f)

        // Plateformes fragiles (en pause pendant le bonus et après la partie)
        if (state != State.BONUS && state != State.GAME_OVER && state != State.LEADERBOARD && state != State.CUSTOMIZE && state != State.SETTINGS) {
            for (p in platforms) {
                if (p.collapsed) {
                    p.dropV += 30f * dt
                    p.dropY += p.dropV * dt
                } else if (p.crumbleT >= 0f) {
                    p.crumbleT += dt
                    if (p.crumbleT >= p.crumbleDur) {
                        p.dropY = 0.001f
                        p.dropV = 0f
                        sounds.play(Sounds.Sfx.CRUMBLE, 0.9f)
                        val standing = (state == State.READY || state == State.CHARGING) && p === current
                        if (standing) {
                            gameOverReason = "La plateforme s'est effondrée !"
                            startFall()
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
                fallT += dt
                when (fallAnim) {
                    // Reste suspendu en l'air un instant, regarde la caméra… puis tombe
                    FallAnim.CARTOON -> if (fallT > 0.8f) fallVy += 45f * dt
                    // Le parachute s'ouvre, freine… puis se déchire
                    FallAnim.PARACHUTE -> if (fallT > 0.25f && fallT < 1.9f) {
                        fallVy += (1.4f - fallVy) * min(1f, dt * 6f)
                    } else {
                        fallVy += 30f * dt
                    }
                    FallAnim.PANIC -> fallVy += 22f * dt
                    FallAnim.SPIN -> fallVy += 30f * dt
                }
                py -= fallVy * dt
                if (sy(px, py + 1.2f, pz) > h) endGame(gameOverReason)
            }

            State.BONUS -> {
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
                        state = State.READY
                        sounds.playPlaylist()
                    }
                }
            }

            State.GAME_OVER -> gameOverTime += dt
            State.CUSTOMIZE -> if (custMsgT > 0f) custMsgT -= dt
            State.READY, State.LEADERBOARD, State.SETTINGS -> {}
        }

        val it = popups.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.life -= dt
            p.y -= w * 0.15f * dt
            if (p.life <= 0f) it.remove()
        }
    }

    /** Points d'un saut : 1, puis +2, +3, +4 et +5 max à partir du 3e parfait d'affilée. */
    private fun perfectPoints(streak: Int) = if (streak >= 3) min(streak - 1, 5) else 1

    private fun land() {
        py = 0f
        when {
            next.contains(px, pz) -> {
                jumps++
                landSquash = 1f
                val scared = checkScare(next)
                val perfect = !scared && abs(px - next.cx) < 0.13f && abs(pz - next.cz) < 0.13f
                perfectStreak = if (perfect) perfectStreak + 1 else 0
                val points = perfectPoints(perfectStreak)
                if (perfect && perfectStreak >= 3) {
                    perfectPulse = 1f
                    lastPerfectPoints = points
                    shownStreak = perfectStreak
                }
                score += points
                popups.add(
                    Popup(
                        if (perfect) "Parfait ! +$points" else "+1",
                        sx(px, pz) + camX, sy(px, playerHeight * 2.2f, pz) + camY, 0.8f
                    )
                )
                if (!scared) performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                when {
                    perfect -> sounds.play(Sounds.Sfx.PERFECT)
                    // « Great » de temps en temps (environ 1 saut sur 5)
                    !scared && Random.nextInt(5) == 0 && sounds.has(Sounds.Sfx.GREAT) -> sounds.play(Sounds.Sfx.GREAT)
                    else -> sounds.play(Sounds.Sfx.LAND)
                }
                current = next
                spawnNext()
                updateCameraTarget()
                startCrumble(current)
                if (jumps % bonusEvery == 0) startBonus() else state = State.READY
            }
            current.contains(px, pz) -> {
                // Saut trop court mais on reste sur sa plateforme : pas de point, la série s'arrête.
                landSquash = 1f
                perfectStreak = 0
                sounds.play(Sounds.Sfx.LAND, 0.8f)
                checkScare(current)
                startCrumble(current)
                state = State.READY
            }
            else -> startFall()
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
        canvas.drawText(score.toString(), w / 2f, insetTop + h * 0.17f, scorePaint)
        textPaint.textSize = w * 0.045f
        canvas.drawText("Record : $best", w / 2f, insetTop + h * 0.17f + w * 0.08f, textPaint)

        buttons.clear()

        if (state == State.READY && score == 0) {
            textPaint.textSize = w * 0.05f
            canvas.drawText("Maintiens appuyé pour sauter", w / 2f, ub - w * 0.27f, textPaint)
            textPaint.textSize = w * 0.04f
            canvas.drawText("plus longtemps = plus loin", w / 2f, ub - w * 0.2f, textPaint)

            // Raccourcis en haut : pseudo à gauche, classement à droite
            val ch = w * 0.085f
            val top = insetTop + w * 0.04f
            drawButton(canvas, RectF(w * 0.03f, top, w * 0.33f, top + ch), "✎ " + (profile.pseudo ?: "Pseudo"), chipPaint, w * 0.036f) {
                askPseudo(first = false)
            }
            drawButton(canvas, RectF(w * 0.67f, top, w * 0.97f, top + ch), "🏆 Classement", chipPaint, w * 0.036f) {
                openBoard(world = false)
            }
            val ty = top + ch + w * 0.025f
            drawButton(canvas, RectF(w * 0.03f, ty, w * 0.33f, ty + ch),
                (if (sounds.soundVolume > 0f || sounds.musicVolume > 0f) "🔊" else "🔇") + " Son", chipPaint, w * 0.036f) {
                openSettings()
            }
            val vy0 = ub - w * 0.11f
            drawButton(canvas, RectF(w * 0.6f, vy0, w * 0.97f, vy0 + w * 0.075f), "🎨 Personnage", chipPaint, w * 0.034f) {
                openCustomize()
            }
            // Version (toucher = vérifier les mises à jour)
            val vy = ub - w * 0.11f
            drawButton(canvas, RectF(w * 0.03f, vy, w * 0.3f, vy + w * 0.075f), "v" + BuildConfig.VERSION_NAME, chipPaint, w * 0.032f) {
                (context as? android.app.Activity)?.let { Updater.check(it, manual = true) }
            }
        }

        if (perfectBarAlpha > 0.02f && state != State.GAME_OVER) drawPerfectBar(canvas)
        if (state == State.BONUS) drawBonus(canvas)
        if (state == State.GAME_OVER) drawGameOver(canvas)
        if (state == State.LEADERBOARD) drawLeaderboard(canvas)
        if (state == State.CUSTOMIZE) drawCustomize(canvas)
        if (state == State.SETTINGS) drawSettings(canvas)

        postInvalidateOnAnimation()
    }

    /** Dessine un bouton arrondi et l'enregistre pour la détection des appuis. */
    private fun drawButton(canvas: Canvas, r: RectF, label: String, bg: Paint, textSize: Float, action: () -> Unit) {
        val radius = r.height() / 2f
        canvas.drawRoundRect(r, radius, radius, bg)
        btnTextPaint.textSize = textSize
        btnTextPaint.color = if (bg === tabOffPaint || bg === lockedPaint || (bg === itemPaint && isLight(bg.color)))
            Color.parseColor("#2E3A59") else Color.WHITE
        val label2 = ellipsize(label, btnTextPaint, r.width() - radius)
        canvas.drawText(label2, r.centerX(), r.centerY() - (btnTextPaint.descent() + btnTextPaint.ascent()) / 2f, btnTextPaint)
        buttons.add(Button(RectF(r), action))
    }

    private fun isLight(c: Int) = Color.red(c) * 0.3f + Color.green(c) * 0.59f + Color.blue(c) * 0.11f > 170f

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
    private fun drawPerfectBar(canvas: Canvas) {
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

    private fun drawStar(canvas: Canvas, x: Float, y: Float, r: Float, paint: Paint) {
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
    private fun drawTimeBar(canvas: Canvas, y: Float, remaining: Float) {
        val left = w * 0.15f
        val right = w * 0.85f
        val bh = w * 0.025f
        rect.set(left, y, right, y + bh)
        canvas.drawRoundRect(rect, bh / 2, bh / 2, barBgPaint)
        rect.set(left, y, left + (right - left) * remaining.coerceIn(0f, 1f), y + bh)
        canvas.drawRoundRect(rect, bh / 2, bh / 2, greenZonePaint)
    }

    private fun drawBonus(canvas: Canvas) {
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
                val cx = mid + half * bonusPos
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
                canvas.drawText("TAPE !", w / 2f, cy - (btnTextPaint.descent() + btnTextPaint.ascent()) / 2f, btnTextPaint)
                drawTimeBar(canvas, cy + w * 0.27f, 1f - t / mashDuration)
                textPaint.textSize = w * 0.045f
                canvas.drawText("+${min(bonusTaps / 4, 10)} points", w / 2f, cy + w * 0.38f, textPaint)
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
            bonusResult != 0 && bonusGain > 0 -> "Bravo ! +$bonusGain"
            bonusResult != 0 -> "Raté… on continue !"
            bonusT < bonusIntro -> "Prêt ?"
            else -> ""
        }
        if (msg.isNotEmpty()) {
            textPaint.textSize = w * 0.085f
            val my = if (bonusType == BonusType.STARS) h * 0.55f else ub - w * 0.08f
            canvas.drawText(msg, w / 2f, my, textPaint)
        }
    }

    /** Hauteur du corps selon la forme choisie. */
    private fun shapeHeight(scaleH: Float) = scaleH * when (lookShape) {
        "tour" -> 0.82f
        "boule" -> 0.58f
        else -> playerHeight
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
    private fun drawParachute(canvas: Canvas, cx: Float, topY: Float) {
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
    private fun drawBubble(canvas: Canvas, x: Float, bottomY: Float, text: String) {
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

    /**
     * Dessine le personnage (forme, couleur, yeux et accessoires) avec ses pieds en (ox, oy),
     * à la hauteur y0. [dirX] = regarde vers +x (sinon +z), [front] = regarde la caméra.
     */
    private fun drawCharacter(
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

    private fun currentLook(slot: Skins.Slot): String = when (slot) {
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

    private fun drawSlider(canvas: Canvas, r: RectF, label: String, value: Float) {
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

    private fun drawSettings(canvas: Canvas) {
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

    private fun drawCustomize(canvas: Canvas) {
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
}
