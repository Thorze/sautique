package com.sautique.game

/**
 * Formes, couleurs et accessoires du personnage.
 * [cost] = nombre de points cumulés (toutes parties confondues) pour débloquer l'élément.
 * [price] = prix en pièces : ces éléments ne s'obtiennent qu'en les achetant (boutique).
 */
object Skins {

    enum class Slot(val key: String, val title: String) {
        SHAPE("shape", "Forme"),
        COLOR("color", "Couleur"),
        GLASSES("glasses", "Lunettes"),
        HAT("hat", "Chapeau"),
        SHOES("shoes", "Chaussures"),
        BACK("back", "Dos"),
        TRAIL("trail", "Traînée")
    }

    class Item(
        val slot: Slot, val id: String, val label: String, val cost: Int,
        val color: Int = 0, val price: Int = 0
    ) {
        val isShop get() = price > 0
    }

    /** Couleurs spéciales dessinées avec un dégradé. */
    const val RAINBOW = "arcenciel"
    const val GALAXY = "galaxie"

    const val NONE = "none"

    val items: List<Item> = listOf(
        // Formes
        Item(Slot.SHAPE, "cube", "Cube", 0),
        Item(Slot.SHAPE, "boule", "Boule", 150),
        Item(Slot.SHAPE, "cylindre", "Cylindre", 500),
        Item(Slot.SHAPE, "tour", "Tour", 1200),

        // Couleurs
        Item(Slot.COLOR, "orange", "Orange", 0, 0xFFFF7A45.toInt()),
        Item(Slot.COLOR, "bleu", "Bleu", 0, 0xFF3F8EFC.toInt()),
        Item(Slot.COLOR, "vert", "Vert", 0, 0xFF3DBE6E.toInt()),
        Item(Slot.COLOR, "rose", "Rose", 0, 0xFFF06292.toInt()),
        Item(Slot.COLOR, "violet", "Violet", 60, 0xFF8E5CD9.toInt()),
        Item(Slot.COLOR, "jaune", "Jaune", 150, 0xFFFFC93C.toInt()),
        Item(Slot.COLOR, "turquoise", "Turquoise", 300, 0xFF26C6DA.toInt()),
        Item(Slot.COLOR, "rouge", "Rouge", 500, 0xFFE53950.toInt()),
        Item(Slot.COLOR, "noir", "Nuit", 800, 0xFF3A3F58.toInt()),
        Item(Slot.COLOR, "or", "Or", 1500, 0xFFE0B03C.toInt()),
        Item(Slot.COLOR, RAINBOW, "Arc-en-ciel", 0, 0xFFFF5DA2.toInt(), price = 600),
        Item(Slot.COLOR, GALAXY, "Galaxie", 0, 0xFF4B2C83.toInt(), price = 900),

        // Lunettes
        Item(Slot.GLASSES, NONE, "Aucune", 0),
        Item(Slot.GLASSES, "rondes", "Rondes", 100),
        Item(Slot.GLASSES, "soleil", "Soleil", 400),
        Item(Slot.GLASSES, "coeur", "Cœurs", 900),
        Item(Slot.GLASSES, "pixel", "Pixel", 0, price = 150),
        Item(Slot.GLASSES, "monocle", "Monocle", 0, price = 250),

        // Chapeaux
        Item(Slot.HAT, NONE, "Aucun", 0),
        Item(Slot.HAT, "casquette", "Casquette", 200),
        Item(Slot.HAT, "hautdeforme", "Haut-de-forme", 700),
        Item(Slot.HAT, "couronne", "Couronne", 2000),
        Item(Slot.HAT, "magicien", "Magicien", 0, price = 200),
        Item(Slot.HAT, "viking", "Viking", 0, price = 350),
        Item(Slot.HAT, "aureole", "Auréole", 0, price = 700),
        Item(Slot.HAT, "bonnet", "Bonnet", 450),
        Item(Slot.HAT, "cowboy", "Cowboy", 0, price = 300),

        // Chaussures
        Item(Slot.SHOES, NONE, "Aucune", 0),
        Item(Slot.SHOES, "baskets", "Baskets", 300),
        Item(Slot.SHOES, "bottes", "Bottes", 850),
        Item(Slot.SHOES, "fusees", "Fusées", 0, price = 450),

        // Dos
        Item(Slot.BACK, NONE, "Rien", 0),
        Item(Slot.BACK, "cape", "Cape", 250),
        Item(Slot.BACK, "sac", "Sac à dos", 600),
        Item(Slot.BACK, "papillon", "Ailes de papillon", 0, price = 400),
        Item(Slot.BACK, "jetpack", "Jetpack", 0, price = 650),

        // Traînée pendant les sauts
        Item(Slot.TRAIL, NONE, "Aucune", 0),
        Item(Slot.TRAIL, "trait", "Trait", 80),
        Item(Slot.TRAIL, "etoiles", "Étoiles", 200),
        Item(Slot.TRAIL, "bulles", "Bulles", 550),
        Item(Slot.TRAIL, "notes", "Notes", 0, price = 250),
        Item(Slot.TRAIL, "coeurs", "Cœurs", 0, price = 300),
        Item(Slot.TRAIL, "arcenciel", "Arc-en-ciel", 0, price = 500)
    )

    /**
     * Anciens seuils (avant le rééquilibrage de la v5.1) : les éléments déjà débloqués avec
     * ces seuils restent acquis.
     */
    val legacyCosts = mapOf(
        "shape:boule" to 30,
        "shape:cylindre" to 120,
        "shape:tour" to 300,
        "color:violet" to 15,
        "color:jaune" to 40,
        "color:turquoise" to 80,
        "color:rouge" to 150,
        "color:noir" to 250,
        "color:or" to 400,
        "glasses:rondes" to 20,
        "glasses:soleil" to 90,
        "glasses:coeur" to 250,
        "hat:casquette" to 40,
        "hat:hautdeforme" to 180,
        "hat:couronne" to 500,
        "hat:bonnet" to 110,
        "shoes:baskets" to 70,
        "shoes:bottes" to 220,
        "back:cape" to 60,
        "back:sac" to 160,
        "trail:etoiles" to 50,
        "trail:bulles" to 140,
    )

    /**
     * Peintures pour les objets (lunettes, chapeau, chaussures, dos) : achetées une fois avec les
     * pièces, puis utilisables sur n'importe quel objet. « Origine » = couleurs d'origine.
     */
    class Tint(val id: String, val label: String, val color: Int, val price: Int)

    const val ORIGINAL = "origine"

    val tints = listOf(
        Tint(ORIGINAL, "Origine", 0, 0),
        Tint("rouge", "Rouge", 0xFFE53950.toInt(), 60),
        Tint("bleu", "Bleu", 0xFF3F8EFC.toInt(), 60),
        Tint("vert", "Vert", 0xFF3DBE6E.toInt(), 60),
        Tint("jaune", "Jaune", 0xFFFFC93C.toInt(), 80),
        Tint("rose", "Rose", 0xFFFF6FB0.toInt(), 80),
        Tint("violet", "Violet", 0xFF8E5CD9.toInt(), 100),
        Tint("blanc", "Blanc", 0xFFF4F4F4.toInt(), 120),
        Tint("noir", "Noir", 0xFF26262E.toInt(), 120),
        Tint("or", "Or", 0xFFE0B03C.toInt(), 250)
    )

    /** Emplacements qui acceptent une peinture. */
    val tintable = setOf(Slot.GLASSES, Slot.HAT, Slot.SHOES, Slot.BACK)

    fun tint(id: String) = tints.firstOrNull { it.id == id } ?: tints.first()

    fun bySlot(slot: Slot) = items.filter { it.slot == slot }

    fun default(slot: Slot) = bySlot(slot).first().id

    fun find(slot: Slot, id: String) = bySlot(slot).firstOrNull { it.id == id } ?: bySlot(slot).first()

    /** Eléments débloqués en passant de [before] à [after] points cumulés. */
    fun unlockedBetween(before: Int, after: Int) = items.filter { !it.isShop && it.cost in (before + 1)..after }

    /** Prochain élément à débloquer. */
    fun nextUnlock(points: Int) = items.filter { !it.isShop && it.cost > points }.minByOrNull { it.cost }
}
