package com.sautique.game

import android.content.Context
import java.util.Locale

/**
 * Traductions. Chaque langue est un fichier JSON du dossier `langues/` à la racine du dépôt
 * (`fr.json`, `en.json`…), copié dans l'appli à la compilation. Le français sert de secours
 * pour une clé absente d'une autre langue.
 *
 * Dans les textes, `{n}`, `{name}`… sont remplacés par les valeurs passées à [tr].
 */
object I18n {
    /** Langues proposées, dans l'ordre du réglage. */
    val languages = listOf("fr", "en", "es", "de", "pt", "it")
    const val AUTO = "auto"

    private var strings: Map<String, String> = emptyMap()
    private var fallback: Map<String, String> = emptyMap()
    private val names = HashMap<String, String>()

    var lang = "fr"
        private set
    private var loaded = false

    /** Charge la langue choisie dans les réglages (« auto » par défaut). */
    fun init(context: Context, force: Boolean = false) {
        if (loaded && !force) return
        val pref = context.getSharedPreferences("sautique", Context.MODE_PRIVATE).getString("language", AUTO) ?: AUTO
        val assets = context.applicationContext.assets
        load(resolve(pref)) { code ->
            runCatching { assets.open("$code.json").bufferedReader().use { it.readText() } }.getOrNull()
        }
        loaded = true
    }

    /** Langue réellement utilisée pour un réglage (« auto » = langue du téléphone, anglais sinon). */
    fun resolve(pref: String): String =
        if (pref in languages) pref else Locale.getDefault().language.takeIf { it in languages } ?: "en"

    /** Charge la langue [code] ; [read] renvoie le contenu du fichier d'une langue. */
    fun load(code: String, read: (String) -> String?) {
        fallback = read("fr")?.let { parse(it) } ?: emptyMap()
        strings = if (code == "fr") fallback else read(code)?.let { parse(it) } ?: fallback
        lang = code
        for (l in languages) if (l !in names) read(l)?.let { parse(it)["_nom"] }?.let { names[l] = it }
    }

    /** Nom d'une langue dans cette langue (« English », « Deutsch »…). */
    fun nameOf(code: String) = names[code] ?: code

    fun tr(key: String, vararg args: Pair<String, Any?>): String {
        var s = strings[key] ?: fallback[key] ?: key
        for ((k, v) in args) s = s.replace("{$k}", v.toString())
        return s
    }

    /** Lecture d'un objet JSON plat { "clé": "texte", … } (sans dépendance, pour les tests aussi). */
    fun parse(json: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        var i = 0
        fun skip() { while (i < json.length && (json[i].isWhitespace() || json[i] == ',' || json[i] == ':')) i++ }
        fun str(): String {
            val sb = StringBuilder()
            i++ // guillemet ouvrant
            while (i < json.length && json[i] != '"') {
                val c = json[i]
                if (c == '\\' && i + 1 < json.length) {
                    i++
                    when (val e = json[i]) {
                        'n' -> sb.append('\n')
                        't' -> sb.append('\t')
                        'u' -> { sb.append(json.substring(i + 1, i + 5).toInt(16).toChar()); i += 4 }
                        else -> sb.append(e)
                    }
                } else sb.append(c)
                i++
            }
            i++ // guillemet fermant
            return sb.toString()
        }
        i = json.indexOf('{') + 1
        while (true) {
            skip()
            if (i >= json.length || json[i] == '}') break
            val key = str()
            skip()
            out[key] = str()
        }
        return out
    }
}

/** « oui » / « non » traduits. */
internal fun yesNo(b: Boolean) = I18n.tr(if (b) "common.yes" else "common.no")

/** Raccourci : texte traduit de la clé [key]. */
internal fun tr(key: String, vararg args: Pair<String, Any?>) = I18n.tr(key, *args)
