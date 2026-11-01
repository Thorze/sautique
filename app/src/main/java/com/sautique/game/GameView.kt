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



    internal val cos30 = 0.8660254f
    internal val sin30 = 0.5f

    // --- Réglages du gameplay (en unités du monde) ---
    internal val maxChargeTime = 1.4f   // secondes d'appui pour la portée max
    internal val maxReach = 5.6f        // portée max d'un saut
    internal val platformDepth = 1.3f   // épaisseur des blocs
    internal val playerHalf = 0.24f     // demi-largeur du cube personnage
    internal val playerHeight = 0.55f

    // Niveau bonus
    internal val bonusEvery = 10        // un bonus tous les 10 sauts réussis
    internal val bonusReward = 5        // points gagnés si on réussit le bonus
    internal val bonusIntro = 1.2f      // temps avant le début (évite un appui involontaire)
    internal val mashDuration = 3f
    internal val starsDuration = 6f
    internal val wheelValues = intArrayOf(2, 5, 0, 3, 10, 1, 4, 0)

    // Plateformes fragiles
    internal val crumbleFromScore = 25

    // Plateformes spéciales
    internal val trampolineFromScore = 8
    internal val miniFromScore = 15
    internal val movingFromScore = 20

    // Frayeur quand on atterrit tout au bord
    internal val scareMargin = 0.12f    // distance au bord (unités) en dessous de laquelle il a peur
    internal val scareDuration = 1.2f

    internal val palette = intArrayOf(
        Color.parseColor("#FF8A65"), Color.parseColor("#4DB6AC"), Color.parseColor("#FFD54F"),
        Color.parseColor("#9575CD"), Color.parseColor("#81C784"), Color.parseColor("#F06292"),
        Color.parseColor("#64B5F6"), Color.parseColor("#ECEFF1")
    )

    // --- Etat ---
    internal var state = State.READY
    internal var score = 0
    internal val profile = LocalScores(context)
    internal val missions = DailyMissions(profile) { java.time.LocalDate.now().toString() }

    // Partie en cours
    internal var runTime = 0f              // durée de la partie (s, hors pause) pour le serveur
    internal var coinsThisRun = 0
    internal var pendingBounce = false     // rebond automatique après un trampoline
    internal var bounceDelay = 0f
    internal var autoJump = false
    internal var pausedFrom = State.READY
    internal var missionsReturnState = State.READY
    internal var boardTab = 0              // 0 perso, 1 semaine, 2 mondial
    internal var pseudoConflict = false
    internal var pendingBuy: Skins.Item? = null
    internal var pendingTint: Skins.Tint? = null
    internal val shopPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F5C542") }

    // Effets
    internal val particles = ArrayList<Particle>()
    internal val banners = ArrayList<Banner>()
    internal var shakeT = 0f
    internal var shakeAmp = 0f
    internal var shakeX = 0f
    internal var shakeY = 0f
    internal var skyTop = 0
    internal var skyBottom = 0
    internal var skyName = ""
    internal val skyStars = List(70) { floatArrayOf(Random.nextFloat(), Random.nextFloat() * 0.75f, Random.nextFloat() * 6.28f) }
    val sounds = Sounds(context)
    internal var lastWheelIdx = -1
    internal var settingsReturnState = State.READY
    internal val sfxSlider = RectF()
    internal val musicSlider = RectF()
    internal var dragSlider = 0          // 0 aucun, 1 effets, 2 musique
    internal val online = ScoreServer(BuildConfig.SCORES_URL, BuildConfig.SCORES_KEY)
    internal var best = profile.best
    internal var newBest = false

    // Classements
    internal var boardReturnState = State.READY
    internal var worldStatus = WorldStatus.IDLE
    internal var worldEntries: List<ScoreServer.Entry> = emptyList()
    internal var worldRank: Int? = null
    internal var personalRuns: List<LocalScores.Run> = emptyList()
    internal var syncing = false
    internal var pseudoDialogShown = false
    internal val buttons = ArrayList<Button>()
    internal val dateFormat = java.text.SimpleDateFormat("dd/MM/yy", java.util.Locale.FRANCE)

    internal val platforms = ArrayList<Platform>()
    internal val drawOrder = ArrayList<Platform>()
    internal lateinit var current: Platform
    internal lateinit var next: Platform

    // Personnage
    internal var px = 0f
    internal var pz = 0f
    internal var py = 0f
    internal var alongX = true          // direction du prochain saut : +x ou +z
    internal var charge = 0f
    internal var jumpT = 0f
    internal var jumpDuration = 0.5f
    internal var jumpFrom = 0f
    internal var jumpDist = 0f
    internal var jumpHeight = 0f
    internal var fallVy = 0f
    internal var fallT = 0f
    internal var fallAnim = FallAnim.SPIN

    // Apparence du personnage
    internal var lookShape = "cube"
    internal var lookGlasses = Skins.NONE
    internal var lookHat = Skins.NONE
    internal var lookShoes = Skins.NONE
    internal var lookColor = "orange"
    internal var lookBack = Skins.NONE
    internal var lookTrail = Skins.NONE
    internal val tintColors = HashMap<Skins.Slot, Int>()
    internal var trailTimer = 0f
    /** Points du ruban de la traînée « Trait » : x, y (écran + caméra), vie restante. */
    internal val ribbon = ArrayList<FloatArray>()
    internal val ribbonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    // Vies bonus : un cœur à ramasser tous les [lifeEvery] sauts réussis
    internal val lifeEvery = 30
    internal val maxLives = 3
    internal var lives = 0
    internal var reviveT = 0f
    internal val reviveDuration = 1.5f
    internal var reviveFromX = 0f
    internal var reviveFromY = 0f
    internal var reviveFromZ = 0f
    internal var reviveTarget: Platform? = null
    internal var unlockedLabels: List<String> = emptyList()

    // Ecran Personnage
    internal var custSlot = Skins.Slot.SHAPE
    internal var custReturnState = State.READY
    internal var custMsg = ""
    internal var custMsgT = 0f
    internal var landSquash = 0f
    internal var gameOverTime = 0f
    internal var gameOverReason: String? = null
    internal var time = 0f
    internal var jumps = 0               // sauts réussis (pour déclencher les bonus)
    internal var scareT = 0f
    internal var crumbleHintShown = false

    // Série de parfaits : la barre « Perfect » apparaît à partir du 3e d'affilée
    internal var perfectStreak = 0
    internal var perfectBarAlpha = 0f
    internal var perfectPulse = 0f
    internal var lastPerfectPoints = 2
    internal var shownStreak = 3          // valeur affichée pendant que la barre disparaît
    internal val perfectBarPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    internal val perfectBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(110, 30, 30, 50) }

    // Bonus : curseur entre -1 et 1 (0 = centre vert)
    internal var bonusCount = 0
    internal var bonusT = 0f
    internal var bonusPos = -1f
    internal var bonusDir = 1f
    internal var bonusSpeed = 1.4f
    internal var bonusResult = 0         // 0 en cours, 1 terminé
    internal var bonusResultT = 0f
    internal var bonusType = BonusType.TIMING
    internal var bonusGain = 0
    internal var bonusTaps = 0           // tapes (Tape-tape) ou étoiles attrapées
    internal var bonusPulse = 0f
    internal val stars = ArrayList<FloatArray>()     // x, y, vie restante, vie totale
    internal val sparkles = ArrayList<FloatArray>()  // x, y, vie restante
    internal var starSpawn = 0f
    internal var wheelAngle = 0f
    internal var wheelSpeed = 0f
    internal var wheelStopping = false
    internal var jumpTrick = JumpTrick.NONE
    internal val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFD54F") }
    internal val wheelText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    // Caméra (en coordonnées isométriques projetées, en pixels)
    internal var camX = 0f
    internal var camY = 0f
    internal var camTargetX = 0f
    internal var camTargetY = 0f

    internal val popups = ArrayList<Popup>()

    // --- Dimensions ---
    internal var w = 0f
    internal var h = 0f
    internal var k = 1f   // pixels par unité du monde
    // Zones occupées par les barres système (encoche en haut, boutons de navigation en bas)
    internal var insetTop = 0f
    internal var insetBottom = 0f
    /** Bas de la zone utilisable, au-dessus des boutons de navigation. */
    internal val ub get() = h - insetBottom
    internal var lastFrameNs = 0L

    // --- Peintures ---
    internal val bgPaint = Paint()
    internal val playerTop = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFA270") }
    internal val playerLeft = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF7A45") }
    internal val playerRight = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#D95B2B") }
    internal val accPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    internal val accStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    internal val lockedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#C9CED8") }
    internal val itemPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    internal val bubbleTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2E3A59")
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    internal val bubbleStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#2E3A59")
    }
    internal val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(60, 0, 0, 0) }
    internal val eyeWhitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    internal val pupilPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1B1B1B") }
    internal val barBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(90, 0, 0, 0) }
    internal val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    internal val overlayPaint = Paint().apply { color = Color.argb(150, 10, 15, 30) }
    internal val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        setShadowLayer(8f, 0f, 4f, Color.argb(90, 0, 0, 0))
    }
    internal val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        setShadowLayer(6f, 0f, 3f, Color.argb(90, 0, 0, 0))
    }
    internal val primaryBtnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF7A45") }
    internal val secondaryBtnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(60, 255, 255, 255) }
    internal val chipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(70, 0, 0, 0) }
    internal val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FAFAFA") }
    internal val tabOnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2E3A59") }
    internal val tabOffPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E3E7EE") }
    internal val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFE0D1") }
    internal val btnTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    internal val darkTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2E3A59")
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    internal val greyTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#7A869A")
        textAlign = Paint.Align.CENTER
    }
    internal val redZonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E53950") }
    internal val greenZonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#3DBE6E") }
    internal val cursorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    internal val cursorStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#2E3A59")
    }
    internal val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    internal val bangPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E53950")
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    internal val rect = RectF()
    internal val tmpPath = Path()

    // --- Projection ---
    internal fun isoX(x: Float, z: Float) = (x - z) * cos30 * k
    internal fun isoY(x: Float, y: Float, z: Float) = -(x + z) * sin30 * k - y * k
    internal fun sx(x: Float, z: Float) = w * 0.5f - camX + shakeX + isoX(x, z)
    internal fun sy(x: Float, y: Float, z: Float) = h * 0.6f - camY + shakeY + isoY(x, y, z)

    /** Remplit un chemin quadrilatère à partir de 4 points 3D (en coordonnées locales projetées). */
    internal fun quad(
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

    internal fun shade(color: Int, f: Float): Int = Color.rgb(
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

    internal fun resetGame() {
        platforms.clear()
        current = Platform(this, 0f, 0f, 0.85f, false, Color.parseColor("#B0BEC5"))
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
        runTime = 0f
        coinsThisRun = 0
        lives = 0
        pendingBounce = false
        autoJump = false
        particles.clear()
        ribbon.clear()
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
    internal fun spawnNext() {
        val difficulty = min(score / 40f, 1f)
        alongX = Random.nextBoolean()
        // Type de plateforme : de plus en plus de plateformes spéciales avec le score
        val r = Random.nextFloat()
        val trampChance = if (score >= trampolineFromScore) 0.10f else 0f
        val miniChance = if (score >= miniFromScore) 0.09f else 0f
        val movingChance = if (score >= movingFromScore) 0.12f else 0f
        val crumbleChance = if (score < crumbleFromScore) 0f else min(0.45f + (score - crumbleFromScore) / 60f, 0.8f)
        val kind = when {
            // Après un trampoline, on rebondit automatiquement : la suivante reste simple
            current.kind == PlatformKind.TRAMPOLINE -> PlatformKind.NORMAL
            r < trampChance -> PlatformKind.TRAMPOLINE
            r < trampChance + miniChance -> PlatformKind.MINI
            r < trampChance + miniChance + movingChance -> PlatformKind.MOVING
            Random.nextFloat() < crumbleChance -> PlatformKind.CRUMBLY
            else -> PlatformKind.NORMAL
        }
        val half = if (kind == PlatformKind.MINI) 0.3f
        else (0.85f - 0.4f * difficulty) * (0.8f + Random.nextFloat() * 0.35f)
        val round = Random.nextFloat() < 0.4f
        val moving = kind == PlatformKind.MOVING
        val minDist = current.half + half + 0.45f + (if (moving) 0.8f else 0f)
        val dist = minDist + Random.nextFloat() * (if (moving) 1.2f else 2.3f)
        val color = if (kind == PlatformKind.MINI) Color.parseColor("#F5C542") else palette[Random.nextInt(palette.size)]
        // Le centre est aligné avec la position du personnage sur l'autre axe
        next = Platform(
            this,
            if (alongX) current.cx + dist else px,
            if (alongX) pz else current.cz + dist,
            half, round, color, kind, alongX
        )
        // Pièces : une de temps en temps, rarement une grosse
        val c = Random.nextFloat()
        next.coin = when {
            c < 0.02f -> 5
            c < 0.18f -> 1
            else -> 0
        }
        // Un cœur (vie bonus) sur la plateforme du 30e, 60e, 90e… saut réussi
        if ((jumps + 1) % lifeEvery == 0 && lives < maxLives) {
            next.heart = true
            next.coin = 0
        }
        platforms.add(next)
        while (platforms.size > 6) platforms.removeAt(0)
    }

    internal fun updateCameraTarget() {
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

    internal fun onAttachedToWindowGame() {
        if (profile.pseudo.isNullOrBlank()) {
            post { askPseudo(first = true) }
        } else {
            syncOnline()
            if (!profile.customized) post { openCustomize() }
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
                    State.READY -> if (!pendingBounce) {
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

    /** Vibration, si elle n'est pas désactivée dans les réglages. */
    internal fun vibrate(type: Int) {
        if (profile.vibrationOn) performHapticFeedback(type)
    }

    /** Fait avancer les missions du jour et crédite les pièces gagnées. */
    internal fun missionEvent(type: MissionType, amount: Int = 1) {
        val r = missions.record(type, amount)
        if (r.coins <= 0) return
        profile.coins += r.coins
        for (m in r.completed) {
            banners += Banner((if (m.challenge) "🏅 Défi du jour réussi ! +" else "✅ Mission réussie ! +") + "${m.reward} 🪙", 3f)
        }
        if (r.allDoneBonus) banners += Banner("🎉 Missions du jour terminées : +${DailyMissions.ALL_DONE_REWARD} 🪙", 3.2f)
        sounds.play(Sounds.Sfx.UNLOCK)
    }

    /** Ramasse la pièce posée sur la plateforme. */
    internal fun collectCoin(p: Platform) {
        val n = p.coin
        p.coin = 0
        coinsThisRun += n
        profile.coins += n
        val x = sx(p.cx, p.cz) + camX
        val y = sy(p.cx, 0.55f, p.cz) + camY
        spawnSparkles(x, y, Color.parseColor("#FFC93C"), if (n > 1) 22 else 12)
        popups.add(Popup("+$n 🪙", x, y - k * 0.4f, 0.9f))
        sounds.play(Sounds.Sfx.COIN)
        missionEvent(MissionType.COINS, n)
    }

    /** Ramasse le cœur : une vie de plus. */
    internal fun collectHeart(p: Platform) {
        p.heart = false
        lives = (lives + 1).coerceAtMost(maxLives)
        val x = sx(p.cx, p.cz) + camX
        val y = sy(p.cx, 0.6f, p.cz) + camY
        spawnSparkles(x, y, Color.parseColor("#FF4D8D"), 18)
        popups.add(Popup("+1 vie ❤️", x, y - k * 0.4f, 1.1f))
        banners += Banner("❤️ Vie bonus ! Si tu tombes, tu reviens", 2.6f)
        sounds.play(Sounds.Sfx.UNLOCK)
    }

    /** Le personnage tombait mais avait une vie : il remonte avec des ailes d'ange. */
    internal fun startRevive() {
        lives--
        reviveFromX = px
        reviveFromY = py
        reviveFromZ = pz
        // Retour sur la plateforme de départ, ou sur la suivante si elle s'est effondrée
        reviveTarget = if (!current.collapsed) current else next
        reviveT = 0f
        perfectStreak = 0
        gameOverReason = null
        banners += Banner(if (lives > 0) "😇 Sauvé ! Il te reste $lives ❤️" else "😇 Sauvé ! Plus de vie en réserve", 2.6f)
        sounds.play(Sounds.Sfx.REVIVE)
        state = State.REVIVE
    }

    internal fun updateRevive(dt: Float) {
        val target = reviveTarget ?: return
        reviveT += dt
        val f = (reviveT / reviveDuration).coerceIn(0f, 1f)
        val e = f * f * (3f - 2f * f)
        px = reviveFromX + (target.cx - reviveFromX) * e
        pz = reviveFromZ + (target.cz - reviveFromZ) * e
        py = reviveFromY * (1f - e) + sin(f * Math.PI.toFloat()) * 1.6f
        if (f >= 1f) {
            py = 0f
            px = target.cx
            pz = target.cz
            landSquash = 1f
            spawnDust(px, pz)
            if (target.crumbly && target.crumbleT >= 0f) target.crumbleT = 0f
            if (target === next) {
                // On repart de la plateforme suivante
                next.frozen = true
                if (next.coin > 0) collectCoin(next)
                if (next.heart) collectHeart(next)
                current = next
                spawnNext()
                updateCameraTarget()
            }
            reviveTarget = null
            state = State.READY
        }
    }

    /** Parties en cours où l'on peut mettre en pause. */
    internal fun canPause() = when (state) {
        State.READY -> jumps > 0 || score > 0
        State.JUMPING, State.CHARGING, State.BONUS -> true
        else -> false
    }

    internal fun pauseGame() {
        if (!canPause()) return
        if (state == State.CHARGING) {
            state = State.READY
            charge = 0f
        }
        pausedFrom = state
        state = State.PAUSED
    }

    internal fun resumeGame() {
        state = pausedFrom
        lastFrameNs = 0L
    }

    /** Appelé quand l'appli passe en arrière-plan. */
    fun onHostPause() = pauseGame()

    /** Rebond automatique d'un trampoline vers le centre de la plateforme suivante. */
    internal fun startAutoJump() {
        jumpFrom = if (alongX) px else pz
        jumpDist = (if (alongX) next.cx else next.cz) - jumpFrom
        jumpDuration = 0.85f
        jumpHeight = 2.6f
        jumpT = 0f
        jumpTrick = JumpTrick.DOUBLE_FLIP
        autoJump = true
        current.bounce = 1f
        sounds.play(Sounds.Sfx.JUMP, 1f, 1.4f)
        state = State.JUMPING
    }

    /** Le personnage rate : chute avec une animation choisie au hasard. */
    internal fun startFall() {
        fallVy = 0f
        fallT = 0f
        fallAnim = FallAnim.values()[Random.nextInt(FallAnim.values().size)]
        perfectStreak = 0
        pendingBounce = false
        autoJump = false
        sounds.play(Sounds.Sfx.FALL)
        vibrate(HapticFeedbackConstants.LONG_PRESS)
        shake(0.06f)
        state = State.FALLING
    }

    internal fun endGame(reason: String?) {
        state = State.GAME_OVER
        gameOverTime = 0f
        gameOverReason = reason
        val before = profile.totalPoints
        profile.queueRun(score, runTime, jumps)
        missionEvent(MissionType.GAMES)
        missionEvent(MissionType.SCORE, score)
        newBest = profile.addRun(score)
        unlockedLabels = Skins.unlockedBetween(before, profile.totalPoints).map { it.label }
        val unlocked = unlockedLabels.isNotEmpty()
        // Musique de défaite, puis « congratulations » si un élément a été débloqué
        sounds.playGameOver { if (unlocked) sounds.play(Sounds.Sfx.UNLOCK) }
        best = profile.best
        syncOnline()
    }

    /** Lance l'effritement d'une plateforme fragile quand on atterrit dessus. */
    internal fun startCrumble(p: Platform) {
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
    internal fun checkScare(p: Platform): Boolean {
        if (p.edgeMargin(px, pz) >= scareMargin) return false
        scareT = scareDuration
        sounds.play(Sounds.Sfx.SCARE)
        vibrate(HapticFeedbackConstants.LONG_PRESS)
        return true
    }

    internal fun startJump() {
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

    internal fun update(dt: Float) {
        // En pause (ou réglages ouverts depuis la pause) : tout est figé
        if (state == State.PAUSED || (state == State.SETTINGS && settingsReturnState == State.PAUSED)) return
        time += dt
        updateEffects(dt)
        for (p in platforms) p.update(dt)
        if (state == State.CHARGING || state == State.JUMPING || state == State.FALLING || state == State.BONUS ||
            state == State.REVIVE ||
            (state == State.READY && jumps > 0)) runTime += dt
        if (state == State.READY && pendingBounce) {
            bounceDelay -= dt
            if (bounceDelay <= 0f) {
                pendingBounce = false
                startAutoJump()
            }
        }
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
        if (state != State.BONUS && state != State.GAME_OVER && state != State.LEADERBOARD && state != State.CUSTOMIZE && state != State.SETTINGS &&
            state != State.MISSIONS) {
            for (p in platforms) {
                if (p.collapsed) {
                    p.dropV += 30f * dt
                    p.dropY += p.dropV * dt
                } else if (p.crumbleT >= 0f && p.crumbleSafe) {
                    // Après un bonus : elle tremble toujours, sans jamais s'effondrer
                    p.crumbleT = min(p.crumbleT + dt, p.crumbleDur * 0.85f)
                } else if (p.crumbleT >= 0f) {
                    p.crumbleT += dt
                    if (p.crumbleT >= p.crumbleDur) {
                        p.dropY = 0.001f
                        p.dropV = 0f
                        sounds.play(Sounds.Sfx.CRUMBLE, 0.9f)
                        spawnDebris(p)
                        shake(0.08f)
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
                emitTrail(dt)
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
                if (lives > 0 && fallT > 0.6f) startRevive()
                else if (sy(px, py + 1.2f, pz) > h) endGame(gameOverReason)
            }

            State.BONUS -> updateBonus(dt)
            State.REVIVE -> updateRevive(dt)

            State.GAME_OVER -> gameOverTime += dt
            State.CUSTOMIZE -> if (custMsgT > 0f) custMsgT -= dt
            State.READY, State.LEADERBOARD, State.SETTINGS, State.PAUSED, State.MISSIONS -> {}
        }

        val it = popups.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.life -= dt
            p.y -= w * 0.15f * dt
            if (p.life <= 0f) it.remove()
        }
    }

    internal fun land() {
        py = 0f
        when {
            next.contains(px, pz) -> {
                val wasAuto = autoJump
                autoJump = false
                jumps++
                landSquash = 1f
                next.frozen = true
                val scared = !wasAuto && checkScare(next)
                val perfect = !wasAuto && !scared && abs(px - next.cx) < 0.13f && abs(pz - next.cz) < 0.13f
                if (!wasAuto) perfectStreak = if (perfect) perfectStreak + 1 else 0
                var points = if (perfect) Scoring.perfectPoints(perfectStreak) else 1
                if (perfect && perfectStreak >= 3) {
                    perfectPulse = 1f
                    lastPerfectPoints = points
                    shownStreak = perfectStreak
                }
                if (next.kind == PlatformKind.MINI) points += Scoring.MINI_BONUS
                score += points
                popups.add(
                    Popup(
                        if (perfect) "Parfait ! +$points" else "+$points",
                        sx(px, pz) + camX, sy(px, playerHeight * 2.2f, pz) + camY, 0.8f
                    )
                )
                spawnDust(px, pz)
                if (perfect) spawnSparkles(sx(px, pz) + camX, sy(px, 0.3f, pz) + camY, Color.parseColor("#FFD54F"), 16)
                if (!scared) vibrate(HapticFeedbackConstants.VIRTUAL_KEY)
                when {
                    perfect -> sounds.play(Sounds.Sfx.PERFECT)
                    // « Great » de temps en temps (environ 1 saut sur 5)
                    !scared && Random.nextInt(5) == 0 && sounds.has(Sounds.Sfx.GREAT) -> sounds.play(Sounds.Sfx.GREAT)
                    else -> sounds.play(Sounds.Sfx.LAND)
                }
                if (next.coin > 0) collectCoin(next)
                if (next.heart) collectHeart(next)
                missionEvent(MissionType.JUMPS)
                if (perfect) {
                    missionEvent(MissionType.PERFECTS)
                    missionEvent(MissionType.STREAK, perfectStreak)
                }
                if (scared) missionEvent(MissionType.SCARES)
                missionEvent(MissionType.SCORE, score)
                current = next
                spawnNext()
                updateCameraTarget()
                startCrumble(current)
                if (current.kind == PlatformKind.TRAMPOLINE) {
                    // Boing ! Rebond automatique vers la plateforme suivante
                    pendingBounce = true
                    bounceDelay = 0.3f
                    current.bounce = 1f
                    popups.add(Popup("Boing !", sx(px, pz) + camX, sy(px, playerHeight * 3.4f, pz) + camY, 0.9f))
                    missionEvent(MissionType.TRAMPOLINES)
                }
                if (jumps % bonusEvery == 0) startBonus() else state = State.READY
            }
            current.contains(px, pz) -> {
                // Saut trop court mais on reste sur sa plateforme : pas de point, la série s'arrête.
                landSquash = 1f
                perfectStreak = 0
                autoJump = false
                sounds.play(Sounds.Sfx.LAND, 0.8f)
                spawnDust(px, pz)
                checkScare(current)
                startCrumble(current)
                if (current.kind == PlatformKind.TRAMPOLINE) {
                    pendingBounce = true
                    bounceDelay = 0.3f
                    current.bounce = 1f
                }
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

        drawSky(canvas)

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
        drawParticles(canvas)

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
        val livesText = if (lives > 0) "   " + "❤️".repeat(lives) else ""
        canvas.drawText("Record : $best   🪙 ${profile.coins}$livesText", w / 2f, insetTop + h * 0.17f + w * 0.08f, textPaint)
        drawBanners(canvas)

        buttons.clear()

        // Bouton pause pendant la partie
        if (canPause() && state != State.BONUS) {
            val ps = w * 0.11f
            val top = insetTop + w * 0.04f
            drawButton(canvas, RectF(w * 0.97f - ps, top, w * 0.97f, top + ps), "⏸", chipPaint, w * 0.05f) { pauseGame() }
        }

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
                openBoard(0)
            }
            val ty = top + ch + w * 0.025f
            val doneCount = missions.list().indices.count { missions.isDone(it) }
            drawButton(canvas, RectF(w * 0.67f, ty, w * 0.97f, ty + ch), "📋 Missions $doneCount/4", chipPaint, w * 0.034f) {
                openMissions()
            }
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
        if (state == State.PAUSED) drawPause(canvas)
        if (state == State.MISSIONS) drawMissions(canvas)
        if (state == State.SETTINGS) drawSettings(canvas)

        postInvalidateOnAnimation()
    }

    /**
     * Dessine le personnage (forme, couleur, yeux et accessoires) avec ses pieds en (ox, oy),
     * à la hauteur y0. [dirX] = regarde vers +x (sinon +z), [front] = regarde la caméra.
     */

}
