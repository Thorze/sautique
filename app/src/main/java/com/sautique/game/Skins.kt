package com.sautique.game

/**
 * Formes, couleurs et accessoires du personnage.
 * [cost] = nombre de points cumulés (toutes parties confondues) pour débloquer l'élément.
 * [price] = prix en pièces : ces éléments ne s'obtiennent qu'en les achetant (boutique).
 */
object Skins {

    enum class Slot(val key: String) {
        CHARACTER("character"),
        SHAPE("shape"),
        COLOR("color"),
        GLASSES("glasses"),
        HAT("hat"),
        SHOES("shoes"),
        BACK("back"),
        TRAIL("trail");

        /** Nom traduit de l'onglet. */
        val title get() = I18n.tr("slot.$key")
    }

    class Item(
        val slot: Slot, val id: String, val cost: Int,
        val color: Int = 0, val price: Int = 0
    ) {
        val isShop get() = price > 0
        /** Nom traduit (clé « item.<onglet>.<id> » des fichiers de langue). */
        val label get() = I18n.tr("item.${slot.key}.$id")
    }

    /** Couleurs spéciales dessinées avec un dégradé. */
    const val RAINBOW = "arcenciel"
    const val GALAXY = "galaxie"

    const val NONE = "none"

    val items: List<Item> = listOf(
        // Personnages complets (non personnalisables), achetés avec les pièces
        Item(Slot.CHARACTER, NONE, 0),
        Item(Slot.CHARACTER, "donut", 0, price = 300),
        Item(Slot.CHARACTER, "bonhomme", 0, price = 400),
        Item(Slot.CHARACTER, "sapin", 0, price = 500),
        Item(Slot.CHARACTER, "perenoel", 0, price = 600),
        Item(Slot.CHARACTER, "hippo", 0, price = 700),
        Item(Slot.CHARACTER, "capybara", 0, price = 800),
        Item(Slot.CHARACTER, "girafe", 0, price = 900),

        // Formes
        Item(Slot.SHAPE, "cube", 0),
        Item(Slot.SHAPE, "boule", 150),
        Item(Slot.SHAPE, "cylindre", 500),
        Item(Slot.SHAPE, "tour", 1200),

        // Couleurs
        Item(Slot.COLOR, "orange", 0, 0xFFFF7A45.toInt()),
        Item(Slot.COLOR, "bleu", 0, 0xFF3F8EFC.toInt()),
        Item(Slot.COLOR, "vert", 0, 0xFF3DBE6E.toInt()),
        Item(Slot.COLOR, "rose", 0, 0xFFF06292.toInt()),
        Item(Slot.COLOR, "violet", 60, 0xFF8E5CD9.toInt()),
        Item(Slot.COLOR, "jaune", 150, 0xFFFFC93C.toInt()),
        Item(Slot.COLOR, "turquoise", 300, 0xFF26C6DA.toInt()),
        Item(Slot.COLOR, "rouge", 500, 0xFFE53950.toInt()),
        Item(Slot.COLOR, "noir", 800, 0xFF3A3F58.toInt()),
        Item(Slot.COLOR, "or", 1500, 0xFFE0B03C.toInt()),
        Item(Slot.COLOR, RAINBOW, 0, 0xFFFF5DA2.toInt(), price = 600),
        Item(Slot.COLOR, GALAXY, 0, 0xFF4B2C83.toInt(), price = 900),

        // Lunettes
        Item(Slot.GLASSES, NONE, 0),
        Item(Slot.GLASSES, "rondes", 100),
        Item(Slot.GLASSES, "soleil", 400),
        Item(Slot.GLASSES, "coeur", 900),
        Item(Slot.GLASSES, "pixel", 0, price = 150),
        Item(Slot.GLASSES, "monocle", 0, price = 250),

        // Chapeaux
        Item(Slot.HAT, NONE, 0),
        Item(Slot.HAT, "casquette", 200),
        Item(Slot.HAT, "hautdeforme", 700),
        Item(Slot.HAT, "couronne", 2000),
        Item(Slot.HAT, "magicien", 0, price = 200),
        Item(Slot.HAT, "viking", 0, price = 350),
        Item(Slot.HAT, "aureole", 0, price = 700),
        Item(Slot.HAT, "bonnet", 450),
        Item(Slot.HAT, "cowboy", 0, price = 300),

        // Chaussures
        Item(Slot.SHOES, NONE, 0),
        Item(Slot.SHOES, "baskets", 300),
        Item(Slot.SHOES, "bottes", 850),
        Item(Slot.SHOES, "fusees", 0, price = 450),

        // Dos
        Item(Slot.BACK, NONE, 0),
        Item(Slot.BACK, "cape", 250),
        Item(Slot.BACK, "sac", 600),
        Item(Slot.BACK, "papillon", 0, price = 400),
        Item(Slot.BACK, "jetpack", 0, price = 650),

        // Traînée pendant les sauts
        Item(Slot.TRAIL, NONE, 0),
        Item(Slot.TRAIL, "trait", 80),
        Item(Slot.TRAIL, "etoiles", 200),
        Item(Slot.TRAIL, "bulles", 550),
        Item(Slot.TRAIL, "notes", 0, price = 250),
        Item(Slot.TRAIL, "coeurs", 0, price = 300),
        Item(Slot.TRAIL, "arcenciel", 0, price = 500)
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
    class Tint(val id: String, val color: Int, val price: Int) {
        val label get() = I18n.tr("tint.$id")
    }

    const val ORIGINAL = "origine"

    val tints = listOf(
        Tint(ORIGINAL, 0, 0),
        Tint("rouge", 0xFFE53950.toInt(), 60),
        Tint("bleu", 0xFF3F8EFC.toInt(), 60),
        Tint("vert", 0xFF3DBE6E.toInt(), 60),
        Tint("jaune", 0xFFFFC93C.toInt(), 80),
        Tint("rose", 0xFFFF6FB0.toInt(), 80),
        Tint("violet", 0xFF8E5CD9.toInt(), 100),
        Tint("blanc", 0xFFF4F4F4.toInt(), 120),
        Tint("noir", 0xFF26262E.toInt(), 120),
        Tint("or", 0xFFE0B03C.toInt(), 250)
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
