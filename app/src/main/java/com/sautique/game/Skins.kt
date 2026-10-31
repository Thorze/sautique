package com.sautique.game

import android.graphics.Color

/**
 * Formes, couleurs et accessoires du personnage.
 * [cost] = nombre de points cumulés (toutes parties confondues) pour débloquer l'élément.
 */
object Skins {

    enum class Slot(val key: String, val title: String) {
        SHAPE("shape", "Forme"),
        COLOR("color", "Couleur"),
        GLASSES("glasses", "Lunettes"),
        HAT("hat", "Chapeau"),
        SHOES("shoes", "Chaussures")
    }

    class Item(val slot: Slot, val id: String, val label: String, val cost: Int, val color: Int = 0)

    const val NONE = "none"

    val items: List<Item> = listOf(
        // Formes
        Item(Slot.SHAPE, "cube", "Cube", 0),
        Item(Slot.SHAPE, "boule", "Boule", 30),
        Item(Slot.SHAPE, "cylindre", "Cylindre", 120),
        Item(Slot.SHAPE, "tour", "Tour", 300),

        // Couleurs
        Item(Slot.COLOR, "orange", "Orange", 0, Color.parseColor("#FF7A45")),
        Item(Slot.COLOR, "bleu", "Bleu", 0, Color.parseColor("#3F8EFC")),
        Item(Slot.COLOR, "vert", "Vert", 0, Color.parseColor("#3DBE6E")),
        Item(Slot.COLOR, "rose", "Rose", 0, Color.parseColor("#F06292")),
        Item(Slot.COLOR, "violet", "Violet", 15, Color.parseColor("#8E5CD9")),
        Item(Slot.COLOR, "jaune", "Jaune", 40, Color.parseColor("#FFC93C")),
        Item(Slot.COLOR, "turquoise", "Turquoise", 80, Color.parseColor("#26C6DA")),
        Item(Slot.COLOR, "rouge", "Rouge", 150, Color.parseColor("#E53950")),
        Item(Slot.COLOR, "noir", "Nuit", 250, Color.parseColor("#3A3F58")),
        Item(Slot.COLOR, "or", "Or", 400, Color.parseColor("#E0B03C")),

        // Lunettes
        Item(Slot.GLASSES, NONE, "Aucune", 0),
        Item(Slot.GLASSES, "rondes", "Rondes", 20),
        Item(Slot.GLASSES, "soleil", "Soleil", 90),
        Item(Slot.GLASSES, "coeur", "Cœurs", 250),

        // Chapeaux
        Item(Slot.HAT, NONE, "Aucun", 0),
        Item(Slot.HAT, "casquette", "Casquette", 40),
        Item(Slot.HAT, "hautdeforme", "Haut-de-forme", 180),
        Item(Slot.HAT, "couronne", "Couronne", 500),

        // Chaussures
        Item(Slot.SHOES, NONE, "Aucune", 0),
        Item(Slot.SHOES, "baskets", "Baskets", 70),
        Item(Slot.SHOES, "bottes", "Bottes", 220)
    )

    fun bySlot(slot: Slot) = items.filter { it.slot == slot }

    fun default(slot: Slot) = bySlot(slot).first().id

    fun find(slot: Slot, id: String) = bySlot(slot).firstOrNull { it.id == id } ?: bySlot(slot).first()

    /** Eléments débloqués en passant de [before] à [after] points cumulés. */
    fun unlockedBetween(before: Int, after: Int) = items.filter { it.cost in (before + 1)..after }

    /** Prochain élément à débloquer. */
    fun nextUnlock(points: Int) = items.filter { it.cost > points }.minByOrNull { it.cost }
}
