package com.sautique.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path

/**
 * Personnages complets achetés avec les pièces (bonhomme de neige, Père Noël, donut, sapin,
 * hippopotame, girafe, capybara). Ils ne se personnalisent pas.
 *
 * Ils sont dessinés de profil, tournés vers la droite (miroir quand le personnage va vers la
 * gauche), en unités du monde : x vers la droite, y vers le haut, pieds en (0, 0).
 */

/** Hauteur (unités) du personnage spécial, pour placer les bulles et la rotation des figures. */
internal fun specialHeight(id: String) = when (id) {
    "bonhomme" -> 1.0f
    "perenoel" -> 0.95f
    "donut" -> 0.6f
    "sapin" -> 0.98f
    "hippo" -> 0.62f
    "girafe" -> 1.25f
    "capybara" -> 0.56f
    else -> 0.55f
}

/** Couleurs des petites décorations (paillettes, boules), fixes pour éviter le scintillement. */
private val decoColors = intArrayOf(
    0xFFE53950.toInt(), 0xFFFFC93C.toInt(), 0xFF3F8EFC.toInt(), 0xFF3DBE6E.toInt(), 0xFFFFFFFF.toInt(), 0xFF8E5CD9.toInt()
)

/**
 * Dessine le personnage spécial [lookSpecial]. Le canvas est déjà centré sur les pieds du personnage.
 */
internal fun GameView.drawSpecialCharacter(
    canvas: Canvas, y0: Float, scaleW: Float, scaleH: Float, dirX: Boolean, scared: Boolean, front: Boolean, angel: Boolean
) {
    val base = isoY(0f, y0, 0f)
    val sw = k * scaleW
    val sh = k * scaleH
    fun X(x: Float) = x * sw
    fun Y(y: Float) = base - y * sh
    val p = accPaint

    fun circle(x: Float, y: Float, r: Float, c: Int) {
        p.color = c
        canvas.drawCircle(X(x), Y(y), r * (sw + sh) / 2f, p)
    }
    fun oval(cx: Float, cy: Float, rx: Float, ry: Float, c: Int) {
        p.color = c
        rect.set(X(cx - rx), Y(cy + ry), X(cx + rx), Y(cy - ry))
        canvas.drawOval(rect, p)
    }
    fun box(l: Float, b: Float, r: Float, t: Float, c: Int, radius: Float = 0f) {
        p.color = c
        rect.set(X(l), Y(t), X(r), Y(b))
        if (radius > 0f) canvas.drawRoundRect(rect, radius * sw, radius * sh, p) else canvas.drawRect(rect, p)
    }
    fun poly(c: Int, vararg pts: Float) {
        tmpPath.reset()
        tmpPath.moveTo(X(pts[0]), Y(pts[1]))
        var i = 2
        while (i < pts.size) { tmpPath.lineTo(X(pts[i]), Y(pts[i + 1])); i += 2 }
        tmpPath.close()
        p.color = c
        canvas.drawPath(tmpPath, p)
    }
    fun line(x0: Float, yA: Float, x1: Float, yB: Float, c: Int, width: Float) {
        accStroke.color = c
        accStroke.strokeWidth = width * k
        canvas.drawLine(X(x0), Y(yA), X(x1), Y(yB), accStroke)
    }
    val closed = !scared && (time % 3.2f) > 3.05f
    /** Œil « dessin animé » : blanc + pupille qui regarde devant. */
    fun eye(x: Float, y: Float, r: Float) {
        val rr = r * (if (scared) 1.25f else 1f)
        if (closed) { box(x - rr, y - rr * 0.15f, x + rr, y + rr * 0.15f, 0xFF1B1B1B.toInt()); return }
        circle(x, y, rr, Color.WHITE)
        circle(x + (if (front) 0f else rr * 0.35f), y + rr * 0.1f, rr * (if (scared) 0.32f else 0.5f), 0xFF1B1B1B.toInt())
    }
    /** Œil en « bouton » (charbon du bonhomme de neige, petit œil du capybara). */
    fun dotEye(x: Float, y: Float, r: Float) {
        val rr = r * (if (scared) 1.4f else 1f)
        if (closed) { box(x - rr, y - rr * 0.2f, x + rr, y + rr * 0.2f, 0xFF1B1B1B.toInt()); return }
        circle(x, y, rr, 0xFF1B1B1B.toInt())
        circle(x - rr * 0.3f, y + rr * 0.3f, rr * 0.3f, 0x99FFFFFF.toInt())
    }

    canvas.save()
    // Profil tourné vers la droite ; miroir quand on va vers la gauche
    if (!front && !dirX) canvas.scale(-1f, 1f)

    // Ailes d'ange (vie bonus) derrière le personnage
    if (angel) {
        val hgt = specialHeight(lookSpecial)
        val flap = kotlin.math.sin(time * 14f) * 0.06f
        oval(-0.32f, hgt * 0.6f + flap, 0.22f, 0.1f, 0xF0FFFFFF.toInt())
        oval(0.32f, hgt * 0.6f + flap, 0.22f, 0.1f, 0xF0FFFFFF.toInt())
    }

    when (lookSpecial) {
        "bonhomme" -> {
            // Trois boules de neige, ombrées sur la droite
            for ((cy, r) in listOf(0.24f to 0.24f, 0.6f to 0.18f, 0.87f to 0.14f)) {
                circle(0f, cy, r, 0xFFC9D6E8.toInt())
                circle(-r * 0.12f, cy + r * 0.08f, r * 0.88f, 0xFFF7FAFF.toInt())
            }
            // Bras en bois
            line(0.12f, 0.62f, 0.32f, 0.76f, 0xFF7B4A2A.toInt(), 0.025f)
            line(-0.12f, 0.62f, -0.3f, 0.74f, 0xFF7B4A2A.toInt(), 0.025f)
            // Écharpe
            box(-0.15f, 0.72f, 0.15f, 0.78f, 0xFFE53950.toInt(), 0.03f)
            box(-0.12f, 0.58f, -0.06f, 0.75f, 0xFFC62839.toInt(), 0.02f)
            // Boutons
            dotEye(0.04f, 0.62f, 0.022f)
            dotEye(0.05f, 0.52f, 0.022f)
            // Visage : yeux en charbon, nez carotte
            dotEye(0.02f, 0.92f, 0.022f)
            dotEye(0.09f, 0.91f, 0.022f)
            poly(0xFFFF8C1A.toInt(), 0.1f, 0.87f, 0.1f, 0.83f, 0.27f, 0.85f)
            // Petit chapeau
            box(-0.14f, 0.98f, 0.14f, 1.01f, 0xFF22222C.toInt())
            box(-0.09f, 1.0f, 0.09f, 1.13f, 0xFF22222C.toInt())
        }
        "perenoel" -> {
            // Bottes et corps
            oval(-0.09f, 0.04f, 0.08f, 0.05f, 0xFF22222C.toInt())
            oval(0.1f, 0.04f, 0.08f, 0.05f, 0xFF22222C.toInt())
            oval(0f, 0.3f, 0.26f, 0.27f, 0xFFC62839.toInt())
            oval(-0.03f, 0.33f, 0.21f, 0.23f, 0xFFE53950.toInt())
            box(-0.25f, 0.06f, 0.25f, 0.11f, Color.WHITE, 0.03f)
            // Ceinture
            box(-0.26f, 0.25f, 0.26f, 0.31f, 0xFF22222C.toInt())
            box(0.05f, 0.24f, 0.13f, 0.32f, 0xFFF5C542.toInt(), 0.01f)
            box(0.07f, 0.26f, 0.11f, 0.30f, 0xFF22222C.toInt())
            // Tête, barbe, nez
            circle(0.03f, 0.64f, 0.15f, 0xFFF9C9A8.toInt())
            oval(0.03f, 0.55f, 0.17f, 0.12f, Color.WHITE)
            oval(0.1f, 0.6f, 0.08f, 0.03f, Color.WHITE)
            eye(0.05f, 0.68f, 0.035f)
            eye(0.13f, 0.67f, 0.035f)
            circle(0.17f, 0.62f, 0.035f, 0xFFF08A8A.toInt())
            // Bonnet qui tombe sur le côté, pompon
            poly(0xFFE53950.toInt(), -0.13f, 0.74f, 0.19f, 0.74f, -0.08f, 0.98f, -0.2f, 0.9f)
            box(-0.15f, 0.71f, 0.21f, 0.78f, Color.WHITE, 0.035f)
            circle(-0.2f, 0.9f, 0.055f, Color.WHITE)
        }
        "donut" -> {
            // Beignet debout comme une roue, glaçage rose et paillettes
            val cy = 0.3f
            fun ring(rOut: Float, rIn: Float, c: Int, dx: Float = 0f) {
                tmpPath.reset()
                tmpPath.fillType = Path.FillType.EVEN_ODD
                tmpPath.addCircle(X(dx), Y(cy), rOut * (sw + sh) / 2f, Path.Direction.CW)
                tmpPath.addCircle(X(dx), Y(cy), rIn * (sw + sh) / 2f, Path.Direction.CW)
                p.color = c
                canvas.drawPath(tmpPath, p)
                tmpPath.fillType = Path.FillType.WINDING
            }
            ring(0.3f, 0.09f, 0xFFB97A3D.toInt())
            ring(0.28f, 0.1f, 0xFFD9A066.toInt(), -0.01f)
            ring(0.25f, 0.12f, 0xFFFF7BAC.toInt(), -0.01f)
            // Paillettes
            for (i in 0 until 12) {
                val a = i * 0.52f + 0.3f
                val r = if (i % 2 == 0) 0.2f else 0.16f
                val x = r * kotlin.math.cos(a)
                val y = cy + r * kotlin.math.sin(a)
                if (y > cy + 0.06f && kotlin.math.abs(x) < 0.08f) continue   // pas sur les yeux
                box(x - 0.018f, y - 0.008f, x + 0.018f, y + 0.008f, decoColors[i % decoColors.size], 0.008f)
            }
            eye(-0.07f, cy + 0.17f, 0.045f)
            eye(0.07f, cy + 0.17f, 0.045f)
            // Sourire sous le trou
            accStroke.color = 0xFF7A3B1E.toInt()
            accStroke.strokeWidth = k * 0.02f
            rect.set(X(-0.07f), Y(cy - 0.12f), X(0.07f), Y(cy - 0.2f))
            canvas.drawArc(rect, 0f, 180f, false, accStroke)
        }
        "sapin" -> {
            box(-0.05f, 0f, 0.05f, 0.13f, 0xFF7B4A2A.toInt())
            // Trois étages, côté gauche plus clair
            for ((b, t, half) in listOf(Triple(0.1f, 0.5f, 0.3f), Triple(0.35f, 0.72f, 0.24f), Triple(0.58f, 0.9f, 0.17f))) {
                poly(0xFF1F7A4A.toInt(), -half, b, half, b, 0f, t)
                poly(0xFF2E9E5E.toInt(), -half, b, 0f, b, 0f, t)
            }
            // Boules et étoile
            val balls = listOf(-0.16f to 0.18f, 0.12f to 0.22f, -0.02f to 0.3f, -0.12f to 0.42f, 0.14f to 0.44f, 0.05f to 0.64f, -0.07f to 0.7f)
            balls.forEachIndexed { i, (x, y) -> circle(x, y, 0.03f, decoColors[i % 4]) }
            p.color = 0xFFFFD54F.toInt()
            drawStar(canvas, X(0f), Y(0.94f), 0.08f * (sw + sh) / 2f, p)
            eye(0.0f, 0.52f, 0.04f)
            eye(0.1f, 0.51f, 0.04f)
        }
        "hippo" -> {
            val c = 0xFF8E8AA8.toInt()
            val dark = 0xFF6F6A8C.toInt()
            for (x in floatArrayOf(-0.22f, -0.08f, 0.07f, 0.19f)) box(x, 0f, x + 0.08f, 0.15f, dark, 0.02f)
            oval(-0.02f, 0.3f, 0.3f, 0.2f, dark)
            oval(-0.04f, 0.32f, 0.27f, 0.17f, c)
            oval(-0.04f, 0.24f, 0.2f, 0.07f, 0xFFB4AFC9.toInt())
            box(-0.36f, 0.3f, -0.3f, 0.34f, dark, 0.02f)            // queue
            // Tête et gros museau
            oval(0.27f, 0.4f, 0.16f, 0.14f, c)
            oval(0.39f, 0.32f, 0.13f, 0.11f, 0xFFB4AFC9.toInt())
            circle(0.45f, 0.37f, 0.018f, 0xFF4A4660.toInt())
            circle(0.4f, 0.38f, 0.018f, 0xFF4A4660.toInt())
            oval(0.2f, 0.53f, 0.035f, 0.03f, dark)
            oval(0.29f, 0.54f, 0.035f, 0.03f, dark)
            eye(0.24f, 0.47f, 0.04f)
            eye(0.33f, 0.47f, 0.04f)
        }
        "girafe" -> {
            val c = 0xFFF2C14E.toInt()
            val spot = 0xFFB5651D.toInt()
            for (x in floatArrayOf(-0.19f, -0.11f, 0.06f, 0.14f)) {
                box(x, 0.02f, x + 0.05f, 0.38f, c)
                box(x, 0f, x + 0.05f, 0.04f, 0xFF4E2E1A.toInt())
            }
            line(-0.23f, 0.47f, -0.3f, 0.32f, 0xFF7B4A2A.toInt(), 0.015f)   // queue
            oval(0f, 0.46f, 0.25f, 0.15f, c)
            // Long cou
            poly(c, 0.1f, 0.5f, 0.21f, 0.55f, 0.33f, 1.06f, 0.24f, 1.08f)
            for ((x, y) in listOf(-0.1f to 0.48f, 0.04f to 0.42f, 0.12f to 0.52f, -0.02f to 0.54f, 0.2f to 0.68f, 0.25f to 0.86f)) {
                oval(x, y, 0.04f, 0.03f, spot)
            }
            // Tête, museau, cornes
            oval(0.31f, 1.1f, 0.1f, 0.07f, c)
            oval(0.39f, 1.07f, 0.06f, 0.05f, 0xFFE8C9A0.toInt())
            line(0.27f, 1.15f, 0.26f, 1.24f, 0xFF7B4A2A.toInt(), 0.02f)
            line(0.32f, 1.15f, 0.32f, 1.24f, 0xFF7B4A2A.toInt(), 0.02f)
            circle(0.26f, 1.24f, 0.022f, 0xFF4E2E1A.toInt())
            circle(0.32f, 1.24f, 0.022f, 0xFF4E2E1A.toInt())
            oval(0.22f, 1.14f, 0.04f, 0.02f, c)
            eye(0.31f, 1.13f, 0.03f)
            eye(0.36f, 1.12f, 0.025f)
        }
        "capybara" -> {
            val c = 0xFFA0703F.toInt()
            val dark = 0xFF7E5530.toInt()
            for (x in floatArrayOf(-0.22f, -0.12f, 0.04f, 0.12f)) box(x, 0f, x + 0.07f, 0.12f, dark, 0.02f)
            box(-0.3f, 0.08f, 0.2f, 0.42f, dark, 0.16f)
            box(-0.28f, 0.1f, 0.18f, 0.42f, c, 0.15f)
            // Tête carrée au museau arrondi
            box(0.08f, 0.17f, 0.4f, 0.46f, dark, 0.1f)
            box(0.08f, 0.19f, 0.38f, 0.46f, 0xFF8E6236.toInt(), 0.1f)
            oval(0.37f, 0.33f, 0.035f, 0.03f, 0xFF3A2614.toInt())
            oval(0.14f, 0.47f, 0.04f, 0.03f, dark)
            dotEye(0.25f, 0.4f, 0.025f)
            // Une petite orange posée sur la tête, comme il se doit
            circle(0.22f, 0.5f, 0.055f, 0xFFFF9F1C.toInt())
            oval(0.25f, 0.56f, 0.03f, 0.012f, 0xFF3DBE6E.toInt())
        }
    }
    canvas.restore()

    // Auréole de la vie bonus
    if (angel) {
        accStroke.color = 0xFFFFD54F.toInt()
        accStroke.strokeWidth = k * 0.035f
        val ay = Y(specialHeight(lookSpecial) + 0.15f)
        rect.set(-k * 0.2f, ay - k * 0.06f, k * 0.2f, ay + k * 0.06f)
        canvas.drawOval(rect, accStroke)
    }
}
