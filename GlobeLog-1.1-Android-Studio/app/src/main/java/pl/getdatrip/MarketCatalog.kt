package pl.getdatrip

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color

const val ACCENT_PRICE = 3
const val SCRATCH_PRICE = 2
const val ICON_PRICE = 5
const val ICON_PACK_PRICE = 25

enum class ScratchColor(
    private val polish: String,
    private val english: String,
    private val german: String,
    private val spanish: String,
    val color: Color,
) {
    Blue("Błękit", "Sky blue", "Himmelblau", "Azul celeste", Color(0xFF579BD0)),
    Lavender("Lawenda", "Lavender", "Lavendel", "Lavanda", Color(0xFFA289D1)),
    Black("Czarny", "Black", "Schwarz", "Negro", Color(0xFF171B22)),
    Mint("Mięta", "Mint", "Minze", "Menta", Color(0xFF6CBF9F)),
    Amber("Bursztyn", "Amber", "Bernstein", "Ámbar", Color(0xFFDAA051)),
    Rose("Róż", "Pink", "Rosa", "Rosa", Color(0xFFD77DA1)),
    Teal("Turkus", "Teal", "Türkis", "Turquesa", Color(0xFF3AB2B0)),
    Green("Zielony", "Green", "Grün", "Verde", Color(0xFF6CAC59)),
    Purple("Fioletowy", "Purple", "Violett", "Violeta", Color(0xFF8D63C5)),
    Coral("Koral", "Coral", "Koralle", "Coral", Color(0xFFD37564)),
    Ice("Lodowy błękit", "Ice blue", "Eisblau", "Azul hielo", Color(0xFF80BDD0)),
    Mulberry("Morwa", "Mulberry", "Maulbeere", "Mora", Color(0xFFB870A8)),
    Sand("Piaskowy", "Sand", "Sandfarben", "Arena", Color(0xFFBA9B7D)),
    Slate("Łupek", "Slate", "Schiefer", "Pizarra", Color(0xFF8997B8));

    fun label(language: AppLanguage): String = language.text(polish, english, german, spanish)
}

enum class GlobeIcon(
    val launcherAlias: String,
    @param:DrawableRes val drawable: Int,
    private val polish: String,
    private val english: String,
    private val german: String,
    private val spanish: String,
) {
    Og("LauncherOg", R.drawable.ic_getdatrip, "GetDaTrip OG", "GetDaTrip OG", "GetDaTrip OG", "GetDaTrip OG"),
    Default("LauncherDefault", R.drawable.ic_globe_default, "Globus", "Globe", "Globus", "Globo"),
    Ocean("LauncherOcean", R.drawable.ic_globe_ocean, "Ocean", "Ocean", "Ozean", "Océano"),
    Forest("LauncherForest", R.drawable.ic_globe_forest, "Las", "Forest", "Wald", "Bosque"),
    Sunset("LauncherSunset", R.drawable.ic_globe_sunset, "Zachód", "Sunset", "Sonnenuntergang", "Atardecer"),
    Night("LauncherNight", R.drawable.ic_globe_night, "Noc", "Night", "Nacht", "Noche");

    fun label(language: AppLanguage): String = language.text(polish, english, german, spanish)
}

internal val newPremiumAppAccents = listOf(AppAccent.Lavender, AppAccent.Amber, AppAccent.Teal, AppAccent.Green)
val premiumAppAccents = listOf(AppAccent.Rose, AppAccent.Blue, AppAccent.Black, AppAccent.Yellow, AppAccent.Mint) +
    newPremiumAppAccents + listOf(AppAccent.Coral, AppAccent.Ice, AppAccent.Mulberry, AppAccent.Sand, AppAccent.Slate)

val premiumGlobeIcons = GlobeIcon.entries.filter { it != GlobeIcon.Og }
