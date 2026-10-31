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
        SHOES("shoes", "Chaussures")
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
        Item(Slot.SHAPE, "boule", "Boule", 30),
        Item(Slot.SHAPE, "cylindre", "Cylindre", 120),
        Item(Slot.SHAPE, "tour", "Tour", 300),

        // Couleurs
        Item(Slot.COLOR, "orange", "Orange", 0, 0xFFFF7A45.toInt()),
        Item(Slot.COLOR, "bleu", "Bleu", 0, 0xFF3F8EFC.toInt()),
        Item(Slot.COLOR, "vert", "Vert", 0, 0xFF3DBE6E.toInt()),
        Item(Slot.COLOR, "rose", "Rose", 0, 0xFFF06292.toInt()),
        Item(Slot.COLOR, "violet", "Violet", 15, 0xFF8E5CD9.toInt()),
        Item(Slot.COLOR, "jaune", "Jaune", 40, 0xFFFFC93C.toInt()),
        Item(Slot.COLOR, "turquoise", "Turquoise", 80, 0xFF26C6DA.toInt()),
        Item(Slot.COLOR, "rouge", "Rouge", 150, 0xFFE53950.toInt()),
        Item(Slot.COLOR, "noir", "Nuit", 250, 0xFF3A3F58.toInt()),
        Item(Slot.COLOR, "or", "Or", 400, 0xFFE0B03C.toInt()),
        Item(Slot.COLOR, RAINBOW, "Arc-en-ciel", 0, 0xFFFF5DA2.toInt(), price = 200),
        Item(Slot.COLOR, GALAXY, "Galaxie", 0, 0xFF4B2C83.toInt(), price = 300),

        // Lunettes
        Item(Slot.GLASSES, NONE, "Aucune", 0),
        Item(Slot.GLASSES, "rondes", "Rondes", 20),
        Item(Slot.GLASSES, "soleil", "Soleil", 90),
        Item(Slot.GLASSES, "coeur", "Cœurs", 250),
        Item(Slot.GLASSES, "pixel", "Pixel", 0, price = 50),
        Item(Slot.GLASSES, "monocle", "Monocle", 0, price = 90),

        // Chapeaux
        Item(Slot.HAT, NONE, "Aucun", 0),
        Item(Slot.HAT, "casquette", "Casquette", 40),
        Item(Slot.HAT, "hautdeforme", "Haut-de-forme", 180),
        Item(Slot.HAT, "couronne", "Couronne", 500),
        Item(Slot.HAT, "magicien", "Magicien", 0, price = 60),
        Item(Slot.HAT, "viking", "Viking", 0, price = 120),
        Item(Slot.HAT, "aureole", "Auréole", 0, price = 250),

        // Chaussures
        Item(Slot.SHOES, NONE, "Aucune", 0),
        Item(Slot.SHOES, "baskets", "Baskets", 70),
        Item(Slot.SHOES, "bottes", "Bottes", 220),
        Item(Slot.SHOES, "fusees", "Fusées", 0, price = 150)
    )

    fun bySlot(slot: Slot) = items.filter { it.slot == slot }

    fun default(slot: Slot) = bySlot(slot).first().id

    fun find(slot: Slot, id: String) = bySlot(slot).firstOrNull { it.id == id } ?: bySlot(slot).first()

    /** Eléments débloqués en passant de [before] à [after] points cumulés. */
    fun unlockedBetween(before: Int, after: Int) = items.filter { !it.isShop && it.cost in (before + 1)..after }

    /** Prochain élément à débloquer. */
    fun nextUnlock(points: Int) = items.filter { !it.isShop && it.cost > points }.minByOrNull { it.cost }
}
