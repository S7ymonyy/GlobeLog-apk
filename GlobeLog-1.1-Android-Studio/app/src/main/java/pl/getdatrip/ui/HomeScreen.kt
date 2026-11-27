package pl.getdatrip.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.Toll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.getdatrip.AppState
import pl.getdatrip.data.Place
import pl.getdatrip.ui.theme.GetDaTripDesign
import pl.getdatrip.ui.theme.tripSurface
import java.text.NumberFormat
import java.util.Locale

@Composable
fun HomeScreen(
    state: AppState,
    onAdd: () -> Unit,
    onRefresh: () -> Unit,
    onLocation: () -> Unit,
    onPlace: (Place) -> Unit,
    onShop: () -> Unit,
    onLeaderboard: () -> Unit,
    navigationClearance: Dp = 0.dp,
) {
    val colors = GetDaTripDesign.colors
    val language = LocalAppLanguage.current
    val places = state.unlockedPlaces
    val refreshing = state.locationLoading || state.weatherLoading
    var removalPlaceId by rememberSaveable { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 66.dp)
                .padding(start = 16.dp, end = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "GlobeLog",
                modifier = Modifier.weight(1f).semantics { heading() },
                fontFamily = FontFamily.Default,
                fontSize = 25.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.Bold,
                color = colors.text,
            )
            TextButton(onClick = onLeaderboard, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text(tr("Topka znajomych", "Friends ranking", "Freundesrangliste", "Clasificación de amigos"), style = MaterialTheme.typography.labelMedium)
            }
            val balance = state.pointBalance
            val formattedBalance = remember(balance, language) {
                NumberFormat.getIntegerInstance(language.locale).format(balance)
            }
            PointsBalanceButton(formattedBalance, onShop)
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = 8.dp,
                    end = 16.dp,
                    bottom = 150.dp + navigationClearance,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                state.locationError?.let { error ->
                    item { Text(error, style = MaterialTheme.typography.bodyMedium, color = colors.mutedText) }
                }
                if (places.isEmpty() && !state.locationLoading) {
                    item {
                        Column(Modifier.padding(top = 12.dp)) {
                            Text(
                                tr(
                                    "Dodaj miejsce lub użyj lokalizacji telefonu",
                                    "Add a place or use your phone’s location",
                                    "Füge einen Ort hinzu oder verwende den Standort deines Geräts",
                                    "Añade una localidad o usa la ubicación de tu teléfono",
                                ),
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.mutedText,
                            )
                            TextButton(onClick = onLocation) {
                                Text(tr("Użyj mojej lokalizacji", "Use my location", "Meinen Standort verwenden", "Usar mi ubicación"))
                            }
                        }
                    }
                }
                items(places, key = { it.id }) { place ->
                    HomePlaceCard(
                        modifier = Modifier.animateItem(fadeInSpec = tween(220), fadeOutSpec = tween(150)),
                        place = place,
                        showFlag = state.showCountryFlags,
                        distance = distanceLabel(state.distanceTo(place), language),
                        onOpen = { onPlace(place) },
                        onRemoveRequest = { removalPlaceId = place.id },
                    )
                }
            }
            HomeActions(
                refreshing = refreshing,
                onAdd = onAdd,
                onRefresh = onRefresh,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 16.dp + navigationClearance),
            )
        }
    }

    val removalPlace = places.firstOrNull { it.id == removalPlaceId }
    if (removalPlace != null) {
        val placeName = removalPlace.displayName(language)
        AlertDialog(
            onDismissRequest = { removalPlaceId = null },
            title = { Text(tr("Usunąć $placeName?", "Remove $placeName?", "$placeName entfernen?", "¿Eliminar $placeName?")) },
            text = {
                Text(tr(
                    "Miejscowość zniknie z listy i mapy.",
                    "This place will be removed from the list and map.",
                    "Dieser Ort wird aus der Liste und von der Karte entfernt.",
                    "Esta localidad se eliminará de la lista y del mapa.",
                ))
            },
            confirmButton = {
                TextButton(onClick = {
                    removalPlaceId = null
                    state.removeUnlocked(removalPlace)
                }) {
                    Text(tr("Usuń", "Remove", "Entfernen", "Eliminar"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { removalPlaceId = null }) { Text(tr("Anuluj", "Cancel", "Abbrechen", "Cancelar")) }
            },
        )
    }
}

@Composable
private fun PointsBalanceButton(balance: String, onShop: () -> Unit) {
    val colors = GetDaTripDesign.colors
    val label = tr(
        "Otwórz sklep. Saldo: $balance pkt",
        "Open shop. Balance: $balance points",
        "Shop öffnen. Guthaben: $balance Punkte",
        "Abrir la tienda. Saldo: $balance puntos",
    )
    TextButton(
        onClick = onShop,
        modifier = Modifier.semantics { contentDescription = label },
        colors = ButtonDefaults.textButtonColors(
            containerColor = colors.accent.copy(alpha = 0.13f),
            contentColor = colors.accent,
        ),
        contentPadding = PaddingValues(horizontal = 9.dp),
    ) {
        Icon(Icons.Rounded.Toll, contentDescription = null, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(4.dp))
        Text(balance, Modifier.clearAndSetSemantics {}, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun HomeActions(
    refreshing: Boolean,
    onAdd: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier,
) {
    val colors = GetDaTripDesign.colors
    val glassEnabled = GetDaTripDesign.liquidGlass
    val transparency = GetDaTripDesign.glassTransparency
    var previousGlass by rememberSaveable { mutableStateOf(false) }
    val merge = remember { Animatable(if (previousGlass) 1f else 0f) }
    LaunchedEffect(glassEnabled) {
        previousGlass = glassEnabled
        merge.animateTo(if (glassEnabled) 1f else 0f, tween(360, easing = FastOutSlowInEasing))
    }

    val progress = merge.value
    val actionSize = (56f - 4f * progress).dp
    val actionShape = RoundedCornerShape((15f + 11f * progress).dp)
    val button = Modifier
        .size(actionSize)
        .shadow((6f * (1f - progress)).dp, actionShape, clip = false)
        .clip(actionShape)
    val scheme = MaterialTheme.colorScheme
    Box(modifier) {
        if (progress > 0f) {
            val shape = JoinedGlassShape(neck = 0.92f * progress * (1f - progress), vertical = true)
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = progress }
                    .glassHighlight(
                        shape = shape,
                        accent = colors.accent,
                        depth = lerp(colors.canvas, colors.card, 0.43f),
                        transparency = transparency,
                        backgroundOpacity = 0.97f,
                    ),
            )
            Box(
                Modifier
                    .align(Alignment.Center)
                    .width(26.dp)
                    .height(1.dp)
                    .background(colors.text.copy(alpha = 0.20f * progress)),
            )
        }

        Column(
            modifier = Modifier.padding((4f * progress).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy((12f * (1f - progress)).dp),
        ) {
            IconButton(
                onClick = onAdd,
                modifier = button.background(scheme.primaryContainer.copy(alpha = 1f - progress)),
            ) {
                Icon(
                    Icons.Filled.Add,
                    tr("Dodaj miejsce", "Add a place", "Ort hinzufügen", "Añadir localidad"),
                    Modifier.size(27.dp),
                    tint = lerp(scheme.onPrimaryContainer, colors.text, progress),
                )
            }
            IconButton(
                onClick = onRefresh,
                enabled = !refreshing,
                modifier = button.background(colors.accent.copy(alpha = 1f - progress)),
            ) {
                LocationActionContent(refreshing, lerp(colors.accentInk, colors.accent, progress))
            }
        }
    }
}

@Composable
private fun LocationActionContent(refreshing: Boolean, tint: Color) {
    val label = tr("Dodaj bieżącą miejscowość", "Unlock the current place", "Aktuellen Ort freischalten", "Desbloquear la localidad actual")
    Box(
        modifier = Modifier.semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        if (refreshing) {
            CircularProgressIndicator(Modifier.size(23.dp), color = tint, strokeWidth = 2.5.dp)
        } else {
            Icon(Icons.Filled.Refresh, contentDescription = null, Modifier.size(28.dp), tint = tint)
        }
    }
}

@Composable
private fun HomePlaceCard(
    modifier: Modifier,
    place: Place,
    showFlag: Boolean,
    distance: String?,
    onOpen: () -> Unit,
    onRemoveRequest: () -> Unit,
) {
    val colors = GetDaTripDesign.colors
    val language = LocalAppLanguage.current
    val placeName = place.displayName(language)
    val shape = RoundedCornerShape(20.dp)
    val card = if (GetDaTripDesign.liquidGlass) {
        Modifier.tripSurface(shape)
    } else {
        Modifier.clip(shape).background(colors.card)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 86.dp)
            .then(card)
            .combinedClickable(
                role = Role.Button,
                onClick = onOpen,
                onLongClickLabel = tr(
                    "Usuń $placeName z listy",
                    "Remove $placeName from the list",
                    "$placeName aus der Liste entfernen",
                    "Eliminar $placeName de la lista",
                ),
                onLongClick = onRemoveRequest,
            )
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val source = if (place.manuallyAdded) tr(
                    "Dodane ręcznie",
                    "Added manually",
                    "Manuell hinzugefügt",
                    "Añadida manualmente",
                ) else null
                Icon(
                    if (place.manuallyAdded) Icons.Filled.Add else Icons.Filled.Place,
                    source,
                    Modifier.size(19.dp),
                    tint = colors.accent,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    placeName,
                    fontFamily = FontFamily.Default,
                    fontSize = 25.sp,
                    lineHeight = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.text,
                )
            }
            val country = place.displayCountry(language)
            val flag = if (showFlag) countryFlag(place.countryCode) else null
            if (country.isNotBlank() || flag != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (flag != null) {
                        Text(flag, Modifier.clearAndSetSemantics {}, fontSize = 15.sp)
                        if (country.isNotBlank()) Spacer(Modifier.width(6.dp))
                    }
                    Text(country, fontFamily = FontFamily.Default, fontSize = 15.sp, color = colors.mutedText)
                }
            }
            if (distance != null) {
                Text(tr("$distance od Ciebie", "$distance away", "$distance entfernt", "A $distance de distancia"), style = MaterialTheme.typography.bodySmall, color = colors.accent)
            }
        }

    }
}

internal fun countryFlag(code: String): String? {
    val letters = code.trim().uppercase(Locale.ROOT)
    if (letters.length != 2 || letters.any { it !in 'A'..'Z' }) return null
    return buildString {
        letters.forEach { appendCodePoint(0x1F1E6 + (it - 'A')) }
    }
}
