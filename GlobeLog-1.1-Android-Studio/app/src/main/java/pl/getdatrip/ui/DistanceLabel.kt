package pl.getdatrip.ui

import java.text.NumberFormat
import kotlin.math.roundToInt
import pl.getdatrip.AppLanguage

internal fun distanceLabel(meters: Float?, language: AppLanguage = AppLanguage.Polish): String? {
    if (meters == null || !meters.isFinite() || meters < 0f) return null
    if (meters.roundToInt() < 1_000) return "${meters.roundToInt()} m"

    val kilometers = meters / 1_000.0
    val formatter = NumberFormat.getNumberInstance(language.locale).apply {
        maximumFractionDigits = if (kilometers < 100.0) 1 else 0
    }
    return "${formatter.format(kilometers)} km"
}
