package pl.getdatrip.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.getdatrip.AppAccent
import pl.getdatrip.AppLanguage
import pl.getdatrip.AppState
import pl.getdatrip.AppThemeMode
import pl.getdatrip.GlobeIcon
import pl.getdatrip.ScratchColor
import pl.getdatrip.data.Place
import pl.getdatrip.ui.theme.GetDaTripDesign
import pl.getdatrip.ui.theme.tripSurface
import kotlin.math.roundToInt

private fun screenContentPadding(navigationClearance: Dp) = PaddingValues(
    start = 22.dp,
    top = 12.dp,
    end = 22.dp,
    bottom = 24.dp + navigationClearance
)

private val firstSettingsRow = RoundedCornerShape(
    topStart = 24.dp,
    topEnd = 24.dp,
    bottomStart = 8.dp,
    bottomEnd = 8.dp
)

private val lastSettingsRow = RoundedCornerShape(
    topStart = 8.dp,
    topEnd = 8.dp,
    bottomStart = 24.dp,
    bottomEnd = 24.dp
)

private enum class AppearancePicker { Accent, Scratch, Icon }

@Composable
fun PlacesScreen(
    state: AppState,
    onLocation: () -> Unit,
    onSelected: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    navigationClearance: Dp = 0.dp
) {
    val colors = GetDaTripDesign.colors
    val keyboard = LocalSoftwareKeyboardController.current
    val query = state.query.trim()
    val places = state.searchResults.filterNot(state::isSaved)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = screenContentPadding(navigationClearance),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { SecondaryTitle(tr("Dodaj miejsce", "Add a place", "Ort hinzufügen", "Añadir localidad"), onBack) }
        item {
            OutlinedTextField(
                value = state.query,
                onValueChange = state::updateQuery,
                placeholder = { Text(tr("Szukaj miejscowości", "Search for a place", "Nach einem Ort suchen", "Buscar una localidad")) },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = if (state.query.isNotEmpty()) {
                    {
                        IconButton(onClick = { state.updateQuery("") }) {
                            Icon(Icons.Rounded.Close, tr("Wyczyść wyszukiwanie", "Clear search", "Suche löschen", "Borrar búsqueda"))
                        }
                    }
                } else {
                    null
                },
                singleLine = true,
                shape = RoundedCornerShape(22.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = colors.card,
                    unfocusedContainerColor = colors.card,
                    focusedBorderColor = colors.accent,
                    unfocusedBorderColor = colors.line,
                    focusedLeadingIconColor = colors.accent,
                    unfocusedLeadingIconColor = colors.mutedText,
                    focusedPlaceholderColor = colors.mutedText,
                    unfocusedPlaceholderColor = colors.mutedText
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (query.isEmpty()) {
            item {
                CurrentLocationCard(
                    loading = state.locationLoading,
                    onClick = {
                        keyboard?.hide()
                        onLocation()
                    }
                )
            }
            state.locationError?.let { error ->
                item { Text(error, color = MaterialTheme.colorScheme.error) }
            }
        } else {
            when {
                query.length < 2 -> item {
                    PlacesMessage(tr(
                        "Wpisz co najmniej 2 znaki.",
                        "Enter at least 2 characters.",
                        "Gib mindestens 2 Zeichen ein.",
                        "Introduce al menos 2 caracteres.",
                    ))
                }
                state.searchLoading -> item {
                    Row(
                        modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = colors.accent
                        )
                        Text(tr("Szukam miejscowości…", "Searching for places…", "Orte werden gesucht …", "Buscando localidades…"), color = colors.mutedText)
                    }
                }
                state.searchError != null -> item {
                    Text(state.searchError.orEmpty(), color = MaterialTheme.colorScheme.error)
                }
                state.searchResults.isEmpty() -> item {
                    PlacesMessage(tr(
                        "Brak wyników. Spróbuj wpisać inną nazwę.",
                        "No results. Try a different name.",
                        "Keine Ergebnisse. Versuche einen anderen Namen.",
                        "No hay resultados. Prueba con otro nombre.",
                    ))
                }
                places.isEmpty() -> item {
                    PlacesMessage(tr(
                        "Wszystkie znalezione miejsca są już na liście.",
                        "All matching places are already on your list.",
                        "Alle passenden Orte stehen bereits auf deiner Liste.",
                        "Todas las localidades coincidentes ya están en tu lista.",
                    ))
                }
                else -> item { SectionTitle(tr("Wyniki wyszukiwania", "Search results", "Suchergebnisse", "Resultados de búsqueda")) }
            }
        }

        items(places, key = { it.id }) { place ->
            PlaceRow(
                place = place,
                flag = if (state.showCountryFlags) countryFlag(place.countryCode) else null,
                onAdd = {
                    state.addSaved(place)
                    state.updateQuery("")
                    keyboard?.hide()
                    onSelected()
                }
            )
        }
    }
}

@Composable
fun SettingsScreen(
    state: AppState,
    onAutoAddPlacesChanged: (Boolean) -> Unit,
    onShowDistancesChanged: (Boolean) -> Unit,
    sharingLocation: Boolean,
    canShareLocation: Boolean,
    locationSharingError: String?,
    onShareLocationChanged: (Boolean) -> Unit,
    onShop: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    navigationClearance: Dp = 0.dp
) {
    val colors = GetDaTripDesign.colors
    val uriHandler = LocalUriHandler.current
    val language = LocalAppLanguage.current
    val accentTitle = tr("Kolor akcentu", "Accent color", "Akzentfarbe", "Color de acento")
    val scratchTitle = tr("Kolor zdrapki", "Scratch map color", "Farbe der Rubbelschicht", "Color de la capa de rascar")
    val iconTitle = tr("Ikona aplikacji", "App icon", "App-Symbol", "Icono de la aplicación")
    var picker by rememberSaveable { mutableStateOf<AppearancePicker?>(null) }
    val closePicker: () -> Unit = { picker = null }
    val openShop: () -> Unit = {
        closePicker()
        onShop()
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = screenContentPadding(navigationClearance),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { SecondaryTitle(tr("Ustawienia", "Settings", "Einstellungen", "Ajustes"), onBack) }
        item { SectionTitle(tr("Język", "Language", "Sprache", "Idioma")) }
        item {
            LanguageSettings(selected = state.language, onSelect = state::setLanguage)
        }
        item { SectionTitle(tr("Wygląd", "Appearance", "Darstellung", "Apariencia")) }
        item { ThemeSettings(state.theme, state::setTheme) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SettingsSwitch(
                    title = "Liquid Glass",
                    subtitle = tr(
                        "Półprzezroczyste karty i pasek nawigacji",
                        "Translucent cards and navigation bar",
                        "Halbtransparente Karten und Navigationsleiste",
                        "Tarjetas y barra de navegación translúcidas",
                    ),
                    icon = Icons.Rounded.AutoAwesome,
                    checked = state.liquidGlass,
                    onCheckedChange = state::setLiquidGlass,
                    shape = firstSettingsRow
                )
                GlassTransparencySlider(
                    transparency = state.glassTransparency,
                    onTransparencyChange = state::setGlassTransparency,
                    onFinished = state::saveGlassTransparency,
                    shape = RoundedCornerShape(8.dp)
                )
                SettingsSwitch(
                    title = tr("Pokazuj flagi kraju", "Show country flags", "Länderflaggen anzeigen", "Mostrar banderas de países"),
                    icon = Icons.Rounded.Flag,
                    checked = state.showCountryFlags,
                    onCheckedChange = state::setShowCountryFlags,
                    shape = RoundedCornerShape(8.dp)
                )
                AppearanceSetting(
                    title = accentTitle,
                    selection = state.accent.label(language),
                    onClick = { picker = AppearancePicker.Accent },
                    shape = RoundedCornerShape(8.dp)
                ) { AppearanceSwatch(state.accent.swatch) }
                AppearanceSetting(
                    title = scratchTitle,
                    selection = state.scratchColor.label(language),
                    onClick = { picker = AppearancePicker.Scratch },
                    shape = RoundedCornerShape(8.dp)
                ) { AppearanceSwatch(state.scratchColor.color) }
                AppearanceSetting(
                    title = iconTitle,
                    selection = state.appIcon.label(language),
                    onClick = { picker = AppearancePicker.Icon },
                    shape = lastSettingsRow
                ) { AppearanceIcon(state.appIcon) }
            }
        }
        item { SectionTitle(tr("Lokalizacja", "Location", "Standort", "Ubicación")) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SettingsSwitch(
                    title = tr(
                        "Automatycznie dodawaj miejscowości",
                        "Automatically add places",
                        "Orte automatisch hinzufügen",
                        "Añadir localidades automáticamente",
                    ),
                    subtitle = if (state.autoAddPlaces && !state.autoTrackingActive) {
                        tr(
                            "Śledzenie czeka na uruchomienie. Sprawdź lokalizację w ustawieniach telefonu.",
                            "Tracking is waiting to start. Check location in your phone’s settings.",
                            "Die Ortung wartet auf den Start. Prüfe die Standorteinstellungen deines Geräts.",
                            "El seguimiento está pendiente. Comprueba la ubicación en los ajustes del teléfono.",
                        )
                    } else {
                        tr(
                            "Także w tle po udzieleniu zgody.",
                            "Works in the background with permission.",
                            "Funktioniert mit deiner Erlaubnis auch im Hintergrund.",
                            "Funciona en segundo plano con tu permiso.",
                        )
                    },
                    icon = Icons.Rounded.MyLocation,
                    checked = state.autoAddPlaces,
                    onCheckedChange = onAutoAddPlacesChanged,
                    shape = firstSettingsRow
                )
                SettingsSwitch(
                    title = tr("Pokazuj odległość", "Show distance", "Entfernung anzeigen", "Mostrar distancia"),
                    subtitle = when {
                        state.distanceLoading -> tr("Ustalam Twoją lokalizację…", "Getting your location…", "Standort wird ermittelt …", "Obteniendo tu ubicación…")
                        state.distanceError != null -> state.distanceError
                        else -> tr(
                            "W linii prostej od Twojej lokalizacji",
                            "Straight-line distance from your location",
                            "Luftlinie von deinem Standort",
                            "Distancia en línea recta desde tu ubicación",
                        )
                    },
                    icon = Icons.Rounded.Straighten,
                    checked = state.showDistances,
                    onCheckedChange = onShowDistancesChanged,
                    shape = RoundedCornerShape(8.dp)
                )
                SettingsSwitch(
                    title = tr("Udostępniaj lokalizację znajomym", "Share location with friends", "Standort mit Freunden teilen", "Compartir ubicación con amigos"),
                    subtitle = if (canShareLocation) tr(
                        "Przybliżona pozycja dla zaakceptowanych znajomych, gdy aplikacja jest otwarta.",
                        "Approximate position for accepted friends while the app is open.",
                        "Ungefähre Position für bestätigte Freunde, solange die App geöffnet ist.",
                        "Posición aproximada para amigos confirmados mientras la app está abierta.",
                    ) else tr("Zaloguj się w Profilu.", "Sign in from Profile.", "Melde dich im Profil an.", "Inicia sesión desde Perfil."),
                    icon = Icons.Rounded.MyLocation,
                    checked = sharingLocation,
                    onCheckedChange = onShareLocationChanged,
                    enabled = canShareLocation,
                    shape = lastSettingsRow,
                )
                locationSharingError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
        item { UpdateSettings() }
        item { SectionTitle(tr("Twórca", "Creator", "Entwickler", "Creador")) }
        item {
            CreditRow(
                name = "Szymon Nawrocki",
                detail = tr(
                    "Inspiracja aplikacją GetDaTrip",
                    "Inspired by the GetDaTrip app",
                    "Inspiriert von der App GetDaTrip",
                    "Inspirado en la aplicación GetDaTrip",
                )
            )
        }
        item {
            TextButton(onClick = { uriHandler.openUri("https://open-meteo.com/") }) {
                Text(
                    tr("Dane pogodowe z Open‑Meteo", "Weather data from Open‑Meteo", "Wetterdaten von Open‑Meteo", "Datos meteorológicos de Open‑Meteo"),
                    color = colors.mutedText,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }

    when (picker) {
        AppearancePicker.Accent -> AppearanceDialog(accentTitle, closePicker) {
            AccentOptions(state.accent, state::isAccentOwned, state::setAccent, openShop)
        }
        AppearancePicker.Scratch -> AppearanceDialog(scratchTitle, closePicker, openShop) {
            OwnedAppearanceOptions(
                options = ScratchColor.entries.filter(state::isScratchOwned),
                selected = state.scratchColor,
                label = { it.label(language) },
                preview = { AppearanceSwatch(it.color) },
                onSelect = {
                    state.setScratchColor(it)
                    closePicker()
                }
            )
        }
        AppearancePicker.Icon -> AppearanceDialog(iconTitle, closePicker, openShop) {
            OwnedAppearanceOptions(
                options = GlobeIcon.entries.filter(state::isIconOwned),
                selected = state.appIcon,
                label = { it.label(language) },
                preview = { AppearanceIcon(it) },
                onSelect = {
                    state.setAppIcon(it)
                    closePicker()
                }
            )
        }
        null -> Unit
    }
}

@Composable
private fun SecondaryTitle(title: String, onBack: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = tr("Wstecz", "Back", "Zurück", "Atrás"))
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        modifier = Modifier.padding(start = 2.dp, top = 6.dp),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun ThemeSettings(selected: AppThemeMode, onSelect: (AppThemeMode) -> Unit) {
    Column(
        Modifier.fillMaxWidth().tripSurface(RoundedCornerShape(24.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(tr("Motyw aplikacji", "App theme", "App-Design", "Tema de la aplicación"),
            style = MaterialTheme.typography.titleMedium)
        FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppThemeMode.entries.forEach { mode ->
                val label = when (mode) {
                    AppThemeMode.Light -> tr("Jasny", "Light", "Hell", "Claro")
                    AppThemeMode.Dark -> tr("Ciemny", "Dark", "Dunkel", "Oscuro")
                    AppThemeMode.System -> tr("Systemowy", "System", "System", "Del sistema")
                }
                FilterChip(selected = mode == selected, onClick = { onSelect(mode) }, label = { Text(label) })
            }
        }
    }
}

@Composable
private fun LanguageSettings(selected: AppLanguage, onSelect: (AppLanguage) -> Unit) {
    val colors = GetDaTripDesign.colors
    val primaryLanguages = listOf(AppLanguage.Polish, AppLanguage.English)
    var expanded by rememberSaveable { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(240),
        label = "languageListChevron"
    )
    val expansionDescription = if (expanded) {
        tr("Rozwinięta", "Expanded", "Ausgeklappt", "Expandida")
    } else {
        tr("Zwinięta", "Collapsed", "Eingeklappt", "Contraída")
    }
    val selectLanguage: (AppLanguage) -> Unit = { language ->
        expanded = false
        onSelect(language)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .tripSurface(RoundedCornerShape(24.dp))
            .selectableGroup()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LanguageChoiceRow(primaryLanguages, selected, selectLanguage)
        HorizontalDivider(Modifier.padding(horizontal = 10.dp), color = colors.line)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(17.dp))
                .clickable(role = Role.Button) { expanded = !expanded }
                .semantics { stateDescription = expansionDescription }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(tr("Języki", "Languages", "Sprachen", "Idiomas"), style = MaterialTheme.typography.titleMedium)
                if (!expanded && selected !in primaryLanguages) {
                    Text(selected.label, style = MaterialTheme.typography.bodySmall, color = colors.mutedText)
                }
            }
            Icon(
                Icons.Rounded.ExpandMore,
                contentDescription = null,
                modifier = Modifier.rotate(chevronRotation),
                tint = colors.mutedText,
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(260), expandFrom = Alignment.Top) + fadeIn(tween(190)),
            exit = shrinkVertically(tween(220), shrinkTowards = Alignment.Top) + fadeOut(tween(150)),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val otherLanguages = AppLanguage.entries.filterNot(primaryLanguages::contains)
                for (languages in otherLanguages.chunked(2)) {
                    LanguageChoiceRow(languages, selected, selectLanguage)
                }
            }
        }
    }
}

@Composable
private fun LanguageChoiceRow(languages: List<AppLanguage>, selected: AppLanguage, onSelect: (AppLanguage) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (language in languages) {
            LanguageChoice(language.label, selected == language, Modifier.weight(1f)) {
                onSelect(language)
            }
        }
    }
}

@Composable
private fun LanguageChoice(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(17.dp))
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun CurrentLocationCard(loading: Boolean, onClick: () -> Unit) {
    val colors = GetDaTripDesign.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tripSurface(RoundedCornerShape(24.dp))
            .clickable(enabled = !loading, role = Role.Button, onClick = onClick)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .background(colors.accent.copy(alpha = 0.14f), CircleShape)
        ) {
            Icon(
                Icons.Rounded.MyLocation,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(21.dp)
            )
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = tr("Moja lokalizacja", "My location", "Mein Standort", "Mi ubicación"),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = if (loading) {
                    tr("Ustalam położenie…", "Getting your location…", "Standort wird ermittelt …", "Obteniendo tu ubicación…")
                } else {
                    tr("Sprawdź pogodę w pobliżu", "Check the weather nearby", "Wetter in der Nähe ansehen", "Consultar el tiempo cercano")
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.mutedText
            )
        }
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = colors.accent,
                strokeWidth = 2.dp
            )
        } else {
            Icon(Icons.Rounded.ChevronRight, null, tint = colors.mutedText)
        }
    }
}

@Composable
private fun PlacesMessage(message: String) {
    Text(
        text = message,
        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = GetDaTripDesign.colors.mutedText
    )
}

@Composable
private fun PlaceRow(place: Place, flag: String?, onAdd: () -> Unit) {
    val colors = GetDaTripDesign.colors
    val language = LocalAppLanguage.current
    val name = place.displayName(language)
    val country = place.displayCountry(language)
    val region = (place.region.split(", ")
        .filter { it.isNotBlank() && !it.equals(place.countryName, ignoreCase = true) } + country)
        .filter { it.isNotBlank() }
        .distinct()
        .joinToString(", ")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tripSurface(RoundedCornerShape(20.dp))
            .clickable(
                onClickLabel = tr("Dodaj miejsce $name", "Add $name", "$name hinzufügen", "Añadir $name"),
                role = Role.Button,
                onClick = onAdd
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(38.dp)
                .background(colors.accent.copy(alpha = 0.12f), CircleShape)
        ) {
            if (flag == null) {
                Icon(
                    Icons.Rounded.Place,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(19.dp)
                )
            } else {
                Text(flag, fontSize = 19.sp)
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    name,
                    modifier = Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Box(
                    modifier = Modifier.size(24.dp).background(colors.accent.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Add, null, Modifier.size(17.dp), tint = colors.accent)
                }
            }
            if (region.isNotBlank()) {
                Text(
                    region,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.mutedText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SettingsSwitch(
    title: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    shape: RoundedCornerShape,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    val colors = GetDaTripDesign.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tripSurface(shape)
            .toggleable(value = checked, role = Role.Switch, enabled = enabled, onValueChange = onCheckedChange)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.mutedText,
            modifier = Modifier.size(21.dp)
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.mutedText)
            }
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun GlassTransparencySlider(
    transparency: Float,
    onTransparencyChange: (Float) -> Unit,
    onFinished: () -> Unit,
    shape: RoundedCornerShape
) {
    val colors = GetDaTripDesign.colors
    val label = tr("Przezroczystość Liquid Glass", "Liquid Glass transparency", "Liquid-Glass-Transparenz", "Transparencia de Liquid Glass")
    val percent = "${(transparency * 100).roundToInt()}%"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .tripSurface(shape)
            .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 2.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Text(percent, color = colors.accent, style = MaterialTheme.typography.labelLarge)
        }
        Slider(
            value = transparency,
            onValueChange = onTransparencyChange,
            onValueChangeFinished = onFinished,
            modifier = Modifier.fillMaxWidth().semantics {
                contentDescription = label
                stateDescription = percent
            }
        )
    }
}

@Composable
private fun AppearanceSetting(
    title: String,
    selection: String,
    onClick: () -> Unit,
    shape: RoundedCornerShape,
    preview: @Composable () -> Unit
) {
    val colors = GetDaTripDesign.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tripSurface(shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        preview()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(selection, style = MaterialTheme.typography.bodySmall, color = colors.mutedText)
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = colors.mutedText)
    }
}

@Composable
private fun AppearanceDialog(
    title: String,
    onDismiss: () -> Unit,
    onShop: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val shopButton: (@Composable () -> Unit)? = if (onShop == null) {
        null
    } else {
        { TextButton(onClick = onShop) { Text(tr("Sklep", "Shop", "Shop", "Tienda")) } }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                content()
            }
        },
        dismissButton = shopButton,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(tr("Zamknij", "Close", "Schließen", "Cerrar")) }
        }
    )
}

@Composable
private fun <T> OwnedAppearanceOptions(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    preview: @Composable (T) -> Unit,
    onSelect: (T) -> Unit
) {
    Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .selectable(selected = option == selected, role = Role.RadioButton, onClick = { onSelect(option) })
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                preview(option)
                Text(label(option), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                RadioButton(selected = option == selected, onClick = null)
            }
        }
    }
}

@Composable
private fun AppearanceSwatch(color: Color) {
    Box(Modifier.size(26.dp).background(color, CircleShape).border(1.dp, Color.White.copy(alpha = 0.38f), CircleShape))
}

@Composable
private fun AppearanceIcon(icon: GlobeIcon) {
    Image(painterResource(icon.drawable), contentDescription = null, modifier = Modifier.size(26.dp))
}

@Composable
private fun AccentOptions(
    selected: AppAccent,
    isOwned: (AppAccent) -> Boolean,
    onSelect: (AppAccent) -> Unit,
    onShop: () -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        maxItemsInEachRow = 3
    ) {
        AppAccent.entries.forEach { accent ->
            val owned = isOwned(accent)
            AccentChoice(
                accent,
                selected = owned && selected == accent,
                locked = !owned,
                onClick = { if (owned) onSelect(accent) else onShop() }
            )
        }
    }
}

@Composable
private fun AccentChoice(accent: AppAccent, selected: Boolean, locked: Boolean, onClick: () -> Unit) {
    val colors = GetDaTripDesign.colors
    val shape = RoundedCornerShape(20.dp)
    val action = if (locked) {
        Modifier.clickable(role = Role.Button, onClickLabel = tr("Otwórz sklep", "Open shop", "Shop öffnen", "Abrir tienda"), onClick = onClick)
    } else {
        Modifier.selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
    }
    val background = when {
        locked -> Color(0xFF262626)
        selected -> accent.container
        else -> colors.cardRaised.copy(alpha = 0.85f)
    }
    val border = when {
        locked -> Color(0xFF4A4A4A)
        selected -> accent.color
        else -> colors.line
    }
    val swatch = if (locked) Color(0xFF727272) else accent.swatch
    Row(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(background)
            .border(1.dp, border, shape)
            .then(action)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(19.dp).background(swatch, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (locked) {
                Icon(
                    Icons.Rounded.Lock,
                    tr("Zablokowany", "Locked", "Gesperrt", "Bloqueado"),
                    modifier = Modifier.size(12.dp),
                    tint = Color.White
                )
            } else if (selected) {
                Icon(Icons.Rounded.Check, null, modifier = Modifier.size(13.dp), tint = accent.ink)
            }
        }
        Text(
            accent.label(LocalAppLanguage.current),
            style = MaterialTheme.typography.labelLarge,
            color = if (locked) Color(0xFFC5C5C5) else colors.text
        )
    }
}

@Composable
private fun CreditRow(name: String, detail: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .tripSurface(RoundedCornerShape(24.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(name, style = MaterialTheme.typography.titleMedium)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = GetDaTripDesign.colors.mutedText)
    }
}
