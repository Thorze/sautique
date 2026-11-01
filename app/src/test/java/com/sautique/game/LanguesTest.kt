package com.sautique.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Vérifie que chaque fichier de langue a les mêmes textes (et les mêmes {variables}) que le français. */
class LanguesTest {
    private val dir = listOf(File("../langues"), File("langues")).first { it.isDirectory }
    private fun load(code: String) = I18n.parse(File(dir, "$code.json").readText())
    private fun vars(s: String) = Regex("\\{\\w+\\}").findAll(s).map { it.value }.toSet()

    @Test
    fun toutesLesLanguesSontCompletes() {
        val fr = load("fr")
        assertTrue(fr.size > 200)
        for (code in I18n.languages) {
            val t = load(code)
            assertEquals("Clés manquantes ou en trop dans $code.json", fr.keys, t.keys)
            for ((k, v) in fr) assertEquals("Variables différentes pour « $k » dans $code.json", vars(v), vars(t.getValue(k)))
        }
    }

    @Test
    fun remplaceLesVariables() {
        I18n.load("en") { File(dir, "$it.json").readText() }
        assertEquals("Best: 42", I18n.tr("common.record", "n" to 42))
        assertEquals("Reach 15 points in one game", MissionType.SCORE.describe(15))
        assertEquals("Snowman", Skins.find(Skins.Slot.CHARACTER, "bonhomme").label)
        assertEquals("absent.key", I18n.tr("absent.key"))
    }
}
