package pl.getdatrip

import androidx.compose.ui.graphics.Color

const val DEFAULT_GLASS_TRANSPARENCY = 0.55f

enum class AppThemeMode { Light, Dark, System }

enum class AppAccent(
    val label: String,
    private val englishLabel: String,
    private val germanLabel: String,
    private val spanishLabel: String,
    val color: Color,
    val ink: Color,
    val container: Color,
    val swatch: Color = color,
) {
    Blue("Niebieski", "Blue", "Blau", "Azul", Color(0xFFB1C5FA), Color(0xFF142C56), Color(0xFF1E3F80)),
    Lavender("Lawenda", "Lavender", "Lavendel", "Lavanda", Color(0xFFCEBCFF), Color(0xFF301E54), Color(0xFF483674)),
    Mint("Mięta", "Mint", "Minze", "Menta", Color(0xFFA5E5B5), Color(0xFF10371D), Color(0xFF245439)),
    Amber("Bursztyn", "Amber", "Bernstein", "Ámbar", Color(0xFFFFC477), Color(0xFF453004), Color(0xFF694619)),
    Rose("Róż", "Pink", "Rosa", "Rosa", Color(0xFFFFB3CE), Color(0xFF50162F), Color(0xFF6A314A)),
    Teal("Turkus", "Teal", "Türkis", "Turquesa", Color(0xFF8ADDDC), Color(0xFF033736), Color(0xFF1A5356)),
    Green("Zielony", "Green", "Grün", "Verde", Color(0xFF86DB5B), Color(0xFF143907), Color(0xFF2B5317)),
    Purple("Fioletowy", "Purple", "Violett", "Violeta", Color(0xFFC18BF4), Color(0xFF2C1348), Color(0xFF502B75)),
    Black("Czarny", "Black", "Schwarz", "Negro", Color(0xFFD0CED8), Color(0xFF121216), Color(0xFF232329), Color(0xFF151519)),
    Yellow("Żółty", "Yellow", "Gelb", "Amarillo", Color(0xFFFFE477), Color(0xFF3E3204), Color(0xFF5B4C10)),
    Coral("Koral", "Coral", "Koralle", "Coral", Color(0xFFFF9C89), Color(0xFF462019), Color(0xFF73382E)),
    Ice("Lodowy błękit", "Ice blue", "Eisblau", "Azul hielo", Color(0xFFAAE5F5), Color(0xFF153941), Color(0xFF28535F)),
    Mulberry("Morwa", "Mulberry", "Maulbeere", "Mora", Color(0xFFF0A4DF), Color(0xFF421B37), Color(0xFF68315B)),
    Sand("Piaskowy", "Sand", "Sandfarben", "Arena", Color(0xFFE9CAAA), Color(0xFF40301F), Color(0xFF5D452C)),
    Slate("Łupek", "Slate", "Schiefer", "Pizarra", Color(0xFFB0BBD5), Color(0xFF252E44), Color(0xFF3F4965));

    fun label(language: AppLanguage): String = language.text(label, englishLabel, germanLabel, spanishLabel)
}
