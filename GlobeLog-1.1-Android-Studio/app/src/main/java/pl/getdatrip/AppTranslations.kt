package pl.getdatrip

import android.content.res.AssetManager
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONObject

internal object AppTranslations {
    @Volatile private var assets: AssetManager? = null
    private val catalogs = ConcurrentHashMap<String, Catalog>()
    private val placeholder = Regex("""\{([1-9])\}""")

    fun initialize(assets: AssetManager) {
        this.assets = assets
    }

    fun translate(language: String, english: String): String {
        val assetManager = assets ?: return english
        val catalog = catalogs[language] ?: load(assetManager, language).let {
            catalogs.putIfAbsent(language, it) ?: it
        }
        catalog.exact[english]?.let { return it }
        for ((pattern, translated) in catalog.templates) {
            val match = pattern.matchEntire(english) ?: continue
            return placeholder.replace(translated) { index ->
                match.groupValues.getOrElse(index.groupValues[1].toInt()) { "" }
            }
        }
        return english
    }

    private fun load(assets: AssetManager, language: String): Catalog = runCatching {
        val file = assets.open("language/$language.json").bufferedReader().use { JSONObject(it.readText()) }
        val exact = mutableMapOf<String, String>()
        val templates = mutableListOf<Pair<Regex, String>>()
        for (key in file.keys()) {
            val value = file.getString(key)
            if (!placeholder.containsMatchIn(key)) {
                exact[key] = value
                continue
            }
            val expression = buildString {
                var start = 0
                for (argument in placeholder.findAll(key)) {
                    append(Regex.escape(key.substring(start, argument.range.first)))
                    append("(.+?)")
                    start = argument.range.last + 1
                }
                append(Regex.escape(key.substring(start)))
            }
            templates.add(Regex(expression) to value)
        }
        Catalog(exact, templates)
    }.getOrElse { Catalog(emptyMap(), emptyList()) }

    private data class Catalog(val exact: Map<String, String>, val templates: List<Pair<Regex, String>>)
}
