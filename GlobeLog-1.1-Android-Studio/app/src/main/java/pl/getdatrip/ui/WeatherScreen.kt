package pl.getdatrip.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import pl.getdatrip.AppState
import pl.getdatrip.R
import pl.getdatrip.data.DEVICE_PLACE_ID
import pl.getdatrip.data.DailyWeather
import pl.getdatrip.data.Forecast
import pl.getdatrip.data.HourlyWeather
import pl.getdatrip.data.Place
import pl.getdatrip.data.weatherDescription
import pl.getdatrip.ui.theme.GetDaTripDesign
import pl.getdatrip.ui.theme.WeatherArtwork
import pl.getdatrip.ui.theme.tripSurface
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun WeatherScreen(
    state: AppState,
    onLocation: () -> Unit,
    onPlaces: () -> Unit,
    onBack: () -> Unit,
    navigationClearance: Dp = 0.dp,
) {
    val place = state.selectedPlace
    val forecast = state.forecast
    val language = LocalAppLanguage.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 22.dp, top = 12.dp, end = 22.dp, bottom = 10.dp + navigationClearance),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        WeatherHeader(
            place = place,
            saved = place?.let(state::isSaved) == true,
            onSave = { place?.let(state::toggleSaved) },
            onLocation = onLocation,
            locationLoading = state.locationLoading,
            onBack = onBack,
        )
        if (state.locationError != null) {
            NoticeCard(
                message = state.locationError.orEmpty(),
                action = tr("Wybierz miejsce", "Choose a place", "Ort auswählen", "Elegir una localidad"),
                onAction = onPlaces,
            )
        }
        when {
            place == null -> WelcomeCard(state.locationLoading, onLocation, onPlaces)
            forecast != null -> {
                CurrentCard(
                    forecast,
                    showCountryFlags = state.showCountryFlags,
                    distance = distanceLabel(state.distanceTo(forecast.place), language),
                    refreshing = state.weatherLoading || state.locationLoading,
                    onRefresh = if (place.id == DEVICE_PLACE_ID) onLocation else state::refreshWeather,
                )
                if (state.weatherError != null) {
                    NoticeCard(state.weatherError.orEmpty(), tr("Ponów", "Retry", "Erneut versuchen", "Reintentar"), state::refreshWeather)
                }
                if (forecast.hourly.isNotEmpty()) HourlyCard(forecast.hourly)
                if (forecast.daily.isNotEmpty()) DailyCard(forecast.daily)
            }
            state.weatherError != null -> NoticeCard(
                state.weatherError.orEmpty(), tr("Ponów", "Retry", "Erneut versuchen", "Reintentar"), state::refreshWeather,
            )
            else -> WeatherLoadingCard()
        }
    }
}

@Composable
private fun WeatherHeader(
    place: Place?,
    saved: Boolean,
    onSave: () -> Unit,
    onLocation: () -> Unit,
    locationLoading: Boolean,
    onBack: () -> Unit,
) {
    val colors = GetDaTripDesign.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Wróć do listy miejsc", "Back to places", "Zurück zur Ortsliste", "Volver a la lista"), tint = colors.text)
        }
        Box(
            modifier = Modifier
                .size(35.dp)
                .background(colors.accent, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.NearMe, null, Modifier.size(20.dp), tint = colors.accentInk)
        }
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 9.dp).weight(1f),
            color = colors.text,
        )
        if (place != null) {
            IconButton(
                onClick = onSave,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = if (saved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = if (saved) {
                        tr("Usuń z zapisanych", "Remove from saved places", "Aus den gespeicherten Orten entfernen", "Eliminar de las localidades guardadas")
                    } else {
                        tr("Zapisz miejsce", "Save place", "Ort speichern", "Guardar localidad")
                    },
                    tint = colors.accent,
                )
            }
        }
        IconButton(
            onClick = onLocation,
            enabled = !locationLoading,
            modifier = Modifier.size(48.dp).tripSurface(CircleShape, elevated = true),
        ) {
            Icon(Icons.Filled.GpsFixed, tr("Moja lokalizacja", "My location", "Mein Standort", "Mi ubicación"), Modifier.size(21.dp), tint = colors.accent)
        }
    }
}

@Composable
private fun WelcomeCard(loading: Boolean, onLocation: () -> Unit, onPlaces: () -> Unit) {
    val colors = GetDaTripDesign.colors
    val shape = RoundedCornerShape(topStart = 31.dp, topEnd = 31.dp, bottomStart = 23.dp, bottomEnd = 41.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .tripSurface(shape)
            .padding(20.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(166.dp)
                .clip(RoundedCornerShape(23.dp))
                .background(Brush.linearGradient(listOf(colors.skyTop, colors.skyBottom))),
            contentAlignment = Alignment.Center,
        ) {
            WeatherArtwork(code = 2, isDay = true, modifier = Modifier.size(152.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(
            tr("Pogoda w zasięgu ręki", "Weather at a glance", "Wetter auf einen Blick", "El tiempo de un vistazo"),
            style = MaterialTheme.typography.headlineLarge,
            color = colors.text,
        )
        Spacer(Modifier.height(7.dp))
        Text(
            tr(
                "Sprawdź aktualną pogodę i prognozę dla swojej okolicy lub miejsca, do którego się wybierasz.",
                "Check current conditions and the forecast for your area or anywhere you're headed.",
                "Sieh dir das aktuelle Wetter und die Vorhersage für deinen Standort oder dein Reiseziel an.",
                "Consulta el tiempo actual y la previsión para tu zona o tu destino.",
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.mutedText,
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onLocation,
            enabled = !loading,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Filled.GpsFixed, null, Modifier.size(19.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(
                if (loading) tr("Ustalam lokalizację…", "Finding your location…", "Standort wird ermittelt …", "Buscando tu ubicación…")
                else tr("Użyj mojej lokalizacji", "Use my location", "Meinen Standort verwenden", "Usar mi ubicación"),
            )
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onPlaces,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            border = BorderStroke(1.dp, colors.line),
        ) {
            Icon(Icons.Outlined.Search, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(tr("Znajdź miejscowość", "Find a place", "Ort suchen", "Buscar una localidad"))
        }
    }
}

@Composable
private fun CurrentCard(
    forecast: Forecast,
    showCountryFlags: Boolean,
    distance: String?,
    refreshing: Boolean,
    onRefresh: () -> Unit,
) {
    val colors = GetDaTripDesign.colors
    val language = LocalAppLanguage.current
    val glass = GetDaTripDesign.liquidGlass
    val current = forecast.current
    val place = forecast.place
    val shape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp, bottomEnd = 34.dp, bottomStart = 21.dp)
    val gradient = Brush.linearGradient(
        listOf(
            if (glass) lerp(colors.skyTop, colors.accent, 0.22f) else colors.skyTop,
            colors.skyBottom,
            if (glass) lerp(colors.card, colors.skyTop, 0.45f) else colors.card,
        ),
    )
    val transparency = GetDaTripDesign.glassTransparency
    val surface = if (glass) {
        Modifier.tripSurface(shape, elevated = true).drawBehind {
            drawRect(gradient, alpha = 0.23f - 0.14f * transparency)
        }
    } else {
        Modifier.clip(shape).background(gradient).border(1.dp, Color.White.copy(alpha = 0.12f), shape)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(surface)
            .padding(horizontal = 20.dp, vertical = 15.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Place, null, Modifier.size(16.dp), tint = colors.accent)
            Spacer(Modifier.width(6.dp))
            Text(
                if (place.id == DEVICE_PLACE_ID) tr("Moja lokalizacja", "My location", "Mein Standort", "Mi ubicación") else tr(
                    "Wybrane miejsce",
                    "Selected place",
                    "Ausgewählter Ort",
                    "Localidad seleccionada",
                ),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = colors.accent,
            )
            IconButton(
                onClick = onRefresh,
                enabled = !refreshing,
                modifier = Modifier.size(48.dp),
            ) {
                if (refreshing) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Refresh, tr("Odśwież pogodę", "Refresh weather", "Wetter aktualisieren", "Actualizar el tiempo"), Modifier.size(20.dp), tint = colors.accent)
                }
            }
        }
        Text(place.displayName(language), style = MaterialTheme.typography.displaySmall, color = colors.text)
        val area = listOf(
            place.region.removeSuffix(", ${place.countryName}").takeUnless { it == place.countryName }.orEmpty(),
            place.displayCountry(language),
        ).filterNot(String::isBlank).distinct().joinToString(", ")
        val flag = if (showCountryFlags) countryFlag(place.countryCode) else null
        if (area.isNotBlank() || flag != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (flag != null) {
                    Text(flag, Modifier.clearAndSetSemantics {}, style = MaterialTheme.typography.bodySmall)
                    if (area.isNotBlank()) Spacer(Modifier.width(6.dp))
                }
                Text(area, style = MaterialTheme.typography.bodySmall, color = colors.mutedText)
            }
        }
        if (distance != null) {
            Text(tr("$distance od Ciebie", "$distance away", "$distance entfernt", "A $distance de distancia"), style = MaterialTheme.typography.bodySmall, color = colors.accent)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val temperature = degrees(current.temperature)
                Text(
                    text = temperature,
                    modifier = Modifier.clearAndSetSemantics { contentDescription = "${temperature}C" },
                    style = MaterialTheme.typography.displayLarge,
                    color = colors.text,
                )
                Text(
                    text = weatherDescription(current.code, language),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.text,
                )
                forecast.daily.firstOrNull()?.let { today ->
                    Text(
                        tr(
                            "Maks. ${degrees(today.high)}   Min. ${degrees(today.low)}",
                            "High ${degrees(today.high)}   Low ${degrees(today.low)}",
                            "Höchstwert ${degrees(today.high)}   Tiefstwert ${degrees(today.low)}",
                            "Máx. ${degrees(today.high)}   Mín. ${degrees(today.low)}",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                        color = colors.mutedText,
                    )
                }
            }
            WeatherArtwork(current.code, current.isDay, Modifier.weight(0.82f).height(139.dp))
        }
        HorizontalDivider(
            Modifier.padding(top = 10.dp, bottom = 12.dp),
            color = Color.White.copy(alpha = 0.14f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WeatherMetric(
                tr("Odczuwalna", "Feels like", "Gefühlt", "Sensación térmica"), "${degrees(current.feelsLike)}C",
                Icons.Filled.Thermostat, Modifier.weight(1f),
            )
            WeatherMetric(
                tr("Wiatr", "Wind", "Wind", "Viento"), "${current.windSpeed.roundToInt()} km/h", Icons.Filled.Air, Modifier.weight(1f),
            )
            WeatherMetric(
                tr("Wilgotność", "Humidity", "Luftfeuchtigkeit", "Humedad"), "${current.humidity}%", Icons.Filled.WaterDrop, Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun WeatherMetric(label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    val colors = GetDaTripDesign.colors
    Column(modifier) {
        Icon(icon, null, Modifier.size(17.dp), tint = colors.accent)
        Text(
            label,
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = colors.mutedText,
        )
        Text(value, style = MaterialTheme.typography.titleSmall, color = colors.text)
    }
}

@Composable
private fun HourlyCard(hours: List<HourlyWeather>) {
    val colors = GetDaTripDesign.colors
    val language = LocalAppLanguage.current
    Column(
        modifier = Modifier.fillMaxWidth().tripSurface(RoundedCornerShape(25.dp)).padding(vertical = 16.dp),
    ) {
        Text(
            tr("Najbliższe godziny", "Hourly forecast", "Stündliche Vorhersage", "Previsión por horas"),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 17.dp),
            color = colors.text,
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(start = 13.dp, end = 13.dp, top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            hours.take(24).forEachIndexed { index, hour ->
                val label = if (index == 0) tr("Teraz", "Now", "Jetzt", "Ahora") else remember(hour.time, language) {
                    formatHour(hour.time, language.locale)
                }
                val description = weatherDescription(hour.code, language)
                val precipitation = "${hour.precipitationProbability}%"
                val precipitationDescription = tr(
                    "Prawdopodobieństwo opadów $precipitation",
                    "$precipitation chance of precipitation",
                    "Niederschlagswahrscheinlichkeit: $precipitation",
                    "$precipitation de probabilidad de precipitación",
                )
                Column(
                    modifier = Modifier
                        .width(59.dp)
                        .tripSurface(RoundedCornerShape(18.dp), elevated = index == 0)
                        .padding(vertical = 8.dp)
                        .semantics(mergeDescendants = true) { contentDescription = description },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(label, color = if (index == 0) colors.accent else colors.mutedText, style = MaterialTheme.typography.labelMedium)
                    WeatherArtwork(hour.code, hour.isDay, Modifier.size(31.dp))
                    Text(degrees(hour.temperature), style = MaterialTheme.typography.titleMedium, color = colors.text)
                    Text(
                        precipitation,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.mint,
                        modifier = Modifier.semantics {
                            contentDescription = precipitationDescription
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DailyCard(days: List<DailyWeather>) {
    val colors = GetDaTripDesign.colors
    val language = LocalAppLanguage.current
    Column(
        modifier = Modifier.fillMaxWidth().tripSurface(RoundedCornerShape(25.dp)).padding(16.dp),
    ) {
        val polishTitle = if (days.size == 1) "Prognoza na 1 dzień" else "Prognoza na ${days.size} dni"
        Text(
            tr(polishTitle, "${days.size}-day forecast", "${days.size}-Tage-Vorhersage", "Previsión de ${days.size} días"),
            style = MaterialTheme.typography.titleLarge,
            color = colors.text,
        )
        Spacer(Modifier.height(6.dp))
        days.forEachIndexed { index, day ->
            if (index > 0) HorizontalDivider(color = colors.line)
            val dayName = remember(day.date, language) { formatDay(day.date, language.locale) }
            val description = weatherDescription(day.code, language)
            val precipitation = "${day.precipitationProbability}%"
            val precipitationDescription = tr(
                "Prawdopodobieństwo opadów $precipitation",
                "$precipitation chance of precipitation",
                "Niederschlagswahrscheinlichkeit: $precipitation",
                "$precipitation de probabilidad de precipitación",
            )
            val low = degrees(day.low)
            val high = degrees(day.high)
            val lowDescription = tr("Minimum ${low}C", "Low ${low}C", "Tiefstwert ${low}C", "Mín. ${low}C")
            val highDescription = tr("Maksimum ${high}C", "High ${high}C", "Höchstwert ${high}C", "Máx. ${high}C")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 46.dp)
                    .semantics(mergeDescendants = true) { contentDescription = description },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (index == 0) tr("Dzisiaj", "Today", "Heute", "Hoy") else dayName,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                WeatherArtwork(day.code, true, Modifier.size(29.dp))
                Text(
                    precipitation,
                    modifier = Modifier.padding(start = 5.dp, end = 11.dp).semantics {
                        contentDescription = precipitationDescription
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.mint,
                )
                Text(
                    low,
                    modifier = Modifier.padding(end = 8.dp).semantics { contentDescription = lowDescription },
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.mutedText,
                )
                Text(
                    high,
                    modifier = Modifier.semantics { contentDescription = highDescription },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.text,
                )
            }
        }
    }
}

@Composable
private fun WeatherLoadingCard() {
    val colors = GetDaTripDesign.colors
    Row(
        modifier = Modifier.fillMaxWidth().tripSurface(RoundedCornerShape(25.dp)).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        Text(tr("Pobieram prognozę…", "Loading the forecast…", "Vorhersage wird geladen …", "Cargando la previsión…"), color = colors.text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun NoticeCard(message: String, action: String, onAction: () -> Unit) {
    val colors = GetDaTripDesign.colors
    Column(
        modifier = Modifier.fillMaxWidth().tripSurface(RoundedCornerShape(25.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = colors.text)
        OutlinedButton(onAction, border = BorderStroke(1.dp, colors.line)) { Text(action) }
    }
}

private fun degrees(value: Double) = "${value.roundToInt()}°"

private fun formatHour(dateTime: String, locale: Locale): String {
    val hour = dateTime.substringAfter('T').take(5)
    val parsed = runCatching { SimpleDateFormat("HH:mm", Locale.ROOT).parse(hour) }.getOrNull()
    return parsed?.let { DateFormat.getTimeInstance(DateFormat.SHORT, locale).format(it) } ?: hour
}

private fun formatDay(date: String, locale: Locale): String {
    val parsed = runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).parse(date) }.getOrNull()
    return parsed?.let { SimpleDateFormat("EEE", locale).format(it).replaceFirstChar { c -> c.titlecase(locale) } }
        ?: date
}
