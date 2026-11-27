package pl.getdatrip.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Toll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import kotlinx.coroutines.launch
import pl.getdatrip.ACCENT_PRICE
import pl.getdatrip.AppState
import pl.getdatrip.GlobeIcon
import pl.getdatrip.ICON_PACK_PRICE
import pl.getdatrip.ICON_PRICE
import pl.getdatrip.SCRATCH_PRICE
import pl.getdatrip.ScratchColor
import pl.getdatrip.premiumAppAccents
import pl.getdatrip.ui.theme.GetDaTripDesign
import pl.getdatrip.ui.theme.tripSurface

private val productShape = RoundedCornerShape(20.dp)

@Composable
fun ShopScreen(
    state: AppState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    navigationClearance: Dp = 0.dp,
) {
    val language = LocalAppLanguage.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val purchaseError = tr(
        "Nie udało się kupić. Spróbuj ponownie.",
        "Couldn’t complete the purchase. Try again.",
        "Der Kauf konnte nicht abgeschlossen werden. Versuche es erneut.",
        "No se pudo completar la compra. Inténtalo de nuevo.",
    )
    fun buy(purchase: () -> Boolean) {
        if (!purchase()) scope.launch { snackbar.showSnackbar(purchaseError) }
    }

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, top = 12.dp, end = 16.dp, bottom = 24.dp + navigationClearance,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { ShopTitle(onBack) }
            item { PointsWallet(state.pointBalance, state.earnedPoints, state.discoveredCount) }
            item { ShopSectionTitle(tr("Akcenty aplikacji", "App accents", "App-Akzente", "Colores de acento")) }
            item {
                ShopGrid(premiumAppAccents) { accent, cardModifier ->
                    val owned = state.isAccentOwned(accent)
                    ShopProduct(
                        name = accent.label(language),
                        owned = owned,
                        selected = state.accent == accent,
                        price = ACCENT_PRICE,
                        affordable = state.pointBalance >= ACCENT_PRICE,
                        onAction = {
                            if (owned) state.setAccent(accent) else buy { state.purchaseAccent(accent) }
                        },
                        modifier = cardModifier,
                        preview = { ColorSwatch(accent.swatch) },
                    )
                }
            }
            item { ShopSectionTitle(tr("Kolory zdrapki", "Scratch map colors", "Farben für die Rubbelkarte", "Colores del mapa para rascar")) }
            item {
                ShopGrid(ScratchColor.entries) { color, cardModifier ->
                    val owned = state.isScratchOwned(color)
                    ShopProduct(
                        name = color.label(language),
                        owned = owned,
                        selected = state.scratchColor == color,
                        price = SCRATCH_PRICE,
                        affordable = state.pointBalance >= SCRATCH_PRICE,
                        onAction = {
                            if (owned) state.setScratchColor(color) else buy { state.purchaseScratch(color) }
                        },
                        modifier = cardModifier,
                        preview = { ColorSwatch(color.color) },
                    )
                }
            }
            item { ShopSectionTitle(tr("Ikony aplikacji", "Launcher icons", "App-Symbole", "Iconos de la aplicación")) }
            item {
                IconPackSummary(
                    owned = state.ownsIconPack,
                    affordable = state.pointBalance >= ICON_PACK_PRICE,
                    onBuy = { buy(state::purchaseIconPack) },
                )
            }
            item {
                ShopGrid(GlobeIcon.entries) { icon, cardModifier ->
                    val owned = state.isIconOwned(icon)
                    ShopProduct(
                        name = icon.label(language),
                        owned = owned,
                        selected = state.appIcon == icon,
                        price = ICON_PRICE,
                        affordable = state.pointBalance >= ICON_PRICE,
                        onAction = {
                            if (owned) state.setAppIcon(icon) else buy { state.purchaseIcon(icon) }
                        },
                        modifier = cardModifier,
                        preview = {
                            Image(
                                painterResource(icon.drawable),
                                contentDescription = null,
                                modifier = Modifier.size(39.dp),
                            )
                        },
                    )
                }
            }
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = navigationClearance))
    }
}

@Composable
private fun ShopTitle(onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr("Wstecz", "Back", "Zurück", "Atrás"))
        }
        Text(
            tr("Sklep", "Shop", "Shop", "Tienda"),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )
    }
}

@Composable
private fun PointsWallet(balance: Int, earned: Int, discovered: Int) {
    val colors = GetDaTripDesign.colors
    val language = LocalAppLanguage.current
    val numbers = remember(language) { NumberFormat.getIntegerInstance(language.locale) }

    Column(
        Modifier
            .fillMaxWidth()
            .tripSurface(RoundedCornerShape(28.dp), elevated = true)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(46.dp).background(colors.accent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Toll, contentDescription = null, tint = colors.accentInk)
            }
            Column {
                Text(
                    tr("Dostępne punkty", "Available points", "Verfügbare Punkte", "Puntos disponibles"),
                    color = colors.mutedText,
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(numbers.format(balance), style = MaterialTheme.typography.displaySmall)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            WalletStat(tr("Zdobyte punkty", "Points earned", "Verdiente Punkte", "Puntos obtenidos"), numbers.format(earned), Modifier.weight(1f))
            WalletStat(tr("Miejscowości od 1.1", "Places since 1.1", "Orte seit 1.1", "Localidades desde 1.1"), numbers.format(discovered), Modifier.weight(1f))
        }
        Text(
            tr(
                "1 punkt za każde 3 miejscowości potwierdzone lokalizacją telefonu.",
                "Earn 1 point for every 3 places confirmed by your phone’s location.",
                "Du erhältst 1 Punkt für je 3 Orte, die dein Standort bestätigt.",
                "Gana 1 punto por cada 3 localidades confirmadas con la ubicación de tu teléfono.",
            ),
            style = MaterialTheme.typography.bodySmall,
            color = colors.mutedText,
        )
    }
}

@Composable
private fun WalletStat(label: String, value: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.bodySmall, color = GetDaTripDesign.colors.mutedText)
    }
}

@Composable
private fun ShopSectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(start = 2.dp, top = 5.dp).semantics { heading() },
    )
}

@Composable
private fun <T> ShopGrid(products: List<T>, content: @Composable (T, Modifier) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = 2,
    ) {
        for (product in products) content(product, Modifier.weight(1f))
        if (products.size % 2 != 0) Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun ShopProduct(
    name: String,
    owned: Boolean,
    selected: Boolean,
    price: Int,
    affordable: Boolean,
    onAction: () -> Unit,
    modifier: Modifier,
    preview: @Composable () -> Unit,
) {
    val colors = GetDaTripDesign.colors
    val frame = if (selected) modifier.border(1.dp, colors.accent, productShape) else modifier
    Column(
        frame.tripSurface(productShape).padding(horizontal = 11.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            preview()
            Text(name, style = MaterialTheme.typography.titleSmall)
        }
        when {
            selected -> ProductStatus(tr("Wybrano", "Selected", "Ausgewählt", "Seleccionado"))
            owned -> ProductButton(
                tr("Wybierz", "Select", "Auswählen", "Seleccionar"),
                enabled = true,
                onClick = onAction,
                description = tr("Wybierz wariant $name", "Select $name", "$name auswählen", "Seleccionar $name"),
            )
            else -> ProductButton(
                tr("Kup · $price pkt", "Buy · $price pts", "Kaufen · $price Pkt.", "Comprar · $price pts"),
                affordable,
                onAction,
                description = tr(
                    "Kup wariant $name za $price pkt",
                    "Buy $name for $price points",
                    "$name für $price Punkte kaufen",
                    "Comprar $name por $price puntos",
                ),
            )
        }
    }
}

@Composable
private fun ProductButton(label: String, enabled: Boolean, onClick: () -> Unit, description: String = label) {
    val colors = GetDaTripDesign.colors
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = description },
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 7.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            disabledContainerColor = colors.canvas.copy(alpha = 0.37f),
            disabledContentColor = colors.mutedText,
        ),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun ProductStatus(label: String) {
    val tint = GetDaTripDesign.colors.accent
    Row(
        Modifier.padding(horizontal = 2.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.Check,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(15.dp),
        )
        Text(label, color = tint, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun ColorSwatch(color: Color) {
    Box(
        Modifier.size(33.dp)
            .background(color, CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.42f), CircleShape),
    )
}

@Composable
private fun IconPackSummary(owned: Boolean, affordable: Boolean, onBuy: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().tripSurface(productShape).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            if (owned) tr("Pakiet odblokowany", "Icon pack unlocked", "Symbolpaket freigeschaltet", "Paquete de iconos desbloqueado") else tr(
                "Pakiet pięciu ikon",
                "Five-icon pack",
                "Paket mit fünf Symbolen",
                "Paquete de cinco iconos",
            ),
            style = MaterialTheme.typography.titleMedium,
        )
        if (!owned) {
            ProductButton(tr(
                "Odblokuj · $ICON_PACK_PRICE pkt",
                "Unlock · $ICON_PACK_PRICE pts",
                "Freischalten · $ICON_PACK_PRICE Pkt.",
                "Desbloquear · $ICON_PACK_PRICE pts",
            ), affordable, onBuy)
        }
    }
}
