package pl.getdatrip

import java.util.Locale

enum class AppLanguage(val code: String, val label: String) {
    Polish("pl", "Polski"),
    English("en", "English"),
    German("de", "Deutsch"),
    Spanish("es", "Español"),
    French("fr", "Français"),
    Italian("it", "Italiano"),
    Portuguese("pt", "Português"),
    Ukrainian("uk", "Українська"),
    Turkish("tr", "Türkçe"),
    Russian("ru", "Русский"),
    Japanese("ja", "日本語"),
    Chinese("zh", "简体中文");

    val locale: Locale = Locale.forLanguageTag(code)

    fun text(polish: String, english: String, german: String, spanish: String): String = when (this) {
        Polish -> polish
        English -> english
        German -> german
        Spanish -> spanish
        else -> AppTranslations.translate(code, english)
    }
}
