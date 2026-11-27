package pl.getdatrip.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import java.util.Locale
import pl.getdatrip.AppLanguage
import pl.getdatrip.data.DEVICE_FALLBACK_NAME
import pl.getdatrip.data.Place

internal val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.Polish }

@Composable
@ReadOnlyComposable
internal fun tr(polish: String, english: String, german: String, spanish: String): String =
    LocalAppLanguage.current.text(polish, english, german, spanish)

internal fun Place.displayName(language: AppLanguage): String =
    if (name == DEVICE_FALLBACK_NAME) language.text(name, "My location", "Mein Standort", "Mi ubicación") else name

internal fun Place.displayCountry(language: AppLanguage): String {
    val regionCode = countryCode.uppercase(Locale.ROOT)
    if (regionCode.length == 2 && regionCode.all { it in 'A'..'Z' }) {
        val localized = Locale.Builder().setRegion(regionCode).build().getDisplayCountry(language.locale)
        if (localized.isNotBlank() && localized != regionCode) return localized
    }
    return countryName.ifBlank { region }
}
