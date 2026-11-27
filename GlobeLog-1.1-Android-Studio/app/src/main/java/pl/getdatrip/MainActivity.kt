package pl.getdatrip

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay
import pl.getdatrip.data.Place
import pl.getdatrip.ui.HomeScreen
import pl.getdatrip.ui.LeaderboardScreen
import pl.getdatrip.ui.LiquidGlassNavigation
import pl.getdatrip.ui.MapTrackedPerson
import pl.getdatrip.ui.LocalAppLanguage
import pl.getdatrip.ui.MessagesScreen
import pl.getdatrip.ui.PlacesScreen
import pl.getdatrip.ui.ProfileScreen
import pl.getdatrip.ui.SettingsScreen
import pl.getdatrip.ui.ShopScreen
import pl.getdatrip.ui.UnlockedMapScreen
import pl.getdatrip.ui.WeatherScreen
import pl.getdatrip.ui.theme.GetDaTripDesign
import pl.getdatrip.ui.theme.GetDaTripTheme
import pl.getdatrip.ui.tr

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppTranslations.initialize(applicationContext.assets)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        setContent { GlobeLogApp(this) }
    }
}

private enum class Destination { Home, Places, Weather, Settings, Map, Profile, Messages, Shop, Leaderboard }
private enum class LocationAction { AddPlace, AddPlaceWithoutPoints, EnableAutomaticAdding, ShowDistances, ShowMapLocation, ShareLocation }

private enum class MainTab(
    val destination: Destination,
    private val polish: String,
    private val english: String,
    private val german: String,
    private val spanish: String,
    val icon: ImageVector,
) {
    List(Destination.Home, "Lista", "List", "Liste", "Lista", Icons.AutoMirrored.Rounded.List),
    Map(Destination.Map, "Mapa", "Map", "Karte", "Mapa", Icons.Rounded.Map),
    Profile(Destination.Profile, "Profil", "Profile", "Profil", "Perfil", Icons.Rounded.Person),
    Messages(Destination.Messages, "Wiadomości", "Messages", "Nachrichten", "Mensajes", Icons.Rounded.Forum),
    Settings(Destination.Settings, "Ustawienia", "Settings", "Einstellungen", "Ajustes", Icons.Rounded.Settings);

    fun label(language: AppLanguage): String = language.text(polish, english, german, spanish)
}

private val mainDestinations = MainTab.entries.mapTo(mutableSetOf()) { it.destination }

@Composable
private fun GlobeLogApp(activity: ComponentActivity) {
    val state = remember(activity) { AppState.get(activity) }
    CompositionLocalProvider(LocalAppLanguage provides state.language) {
        GlobeLogContent(activity, state)
    }
}

@Composable
private fun GlobeLogContent(activity: ComponentActivity, state: AppState) {
    val socialScope = rememberCoroutineScope()
    val social = remember(activity) { SocialState(activity.applicationContext, socialScope) { state.language } }
    val friendLocations = remember(activity) { FriendLocations(activity.applicationContext, socialScope) { state.language } }
    DisposableEffect(social, friendLocations) {
        onDispose {
            friendLocations.close()
            social.close()
        }
    }
    LaunchedEffect(social, state.earnedPoints) { social.syncScore(state.earnedPoints) }
    LaunchedEffect(social.profile, social.friends) { friendLocations.updateFriends(social.profile, social.friends) }
    LaunchedEffect(state.appIcon) { applyLauncherIcon(activity, state.appIcon) }

    var destination by rememberSaveable { mutableStateOf(Destination.Home) }
    var weatherOrigin by rememberSaveable { mutableStateOf(Destination.Home) }
    var shopOrigin by rememberSaveable { mutableStateOf(Destination.Home) }
    var leaderboardOrigin by rememberSaveable { mutableStateOf(Destination.Home) }
    var showPermissionDialog by rememberSaveable { mutableStateOf(false) }
    var hasRequestedPermission by rememberSaveable { mutableStateOf(false) }
    var locationAction by rememberSaveable { mutableStateOf(LocationAction.AddPlace) }
    val pages = rememberSaveableStateHolder()

    val permissions = remember {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    }
    fun hasLocationPermission(): Boolean = permissions.any {
        ContextCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_GRANTED
    }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    fun startAutomaticAdding(askForNotification: Boolean) {
        state.setAutoAddPlaces(true)
        if (!LocationTrackingService.start(activity)) {
            if (askForNotification) {
                Toast.makeText(
                    activity,
                    state.language.text(
                        "Nie udało się uruchomić zapisywania w tle.",
                        "Could not start adding places in the background.",
                        "Das Hinzufügen von Orten im Hintergrund konnte nicht gestartet werden.",
                        "No se pudo iniciar el guardado de localidades en segundo plano.",
                    ),
                    Toast.LENGTH_LONG,
                ).show()
            }
        } else if (
            askForNotification && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun finishLocationAction() {
        when (locationAction) {
            LocationAction.AddPlace -> state.useLocation(select = destination == Destination.Weather, unlock = true)
            LocationAction.AddPlaceWithoutPoints -> state.useLocation(select = false, unlock = true, creditVisit = false)
            LocationAction.EnableAutomaticAdding -> startAutomaticAdding(askForNotification = true)
            LocationAction.ShowDistances -> state.setShowDistances(true)
            LocationAction.ShowMapLocation -> state.refreshMapLocation()
            LocationAction.ShareLocation -> friendLocations.setSharing(true)
        }
    }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        if (hasLocationPermission()) finishLocationAction() else showPermissionDialog = true
    }
    val applicationSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        if (hasLocationPermission()) finishLocationAction()
    }
    val requestLocation: (LocationAction) -> Unit = { action ->
        state.clearLocationError()
        locationAction = action
        when {
            hasLocationPermission() -> finishLocationAction()
            hasRequestedPermission && permissions.none(activity::shouldShowRequestPermissionRationale) -> {
                showPermissionDialog = true
            }
            else -> {
                hasRequestedPermission = true
                locationLauncher.launch(permissions)
            }
        }
    }
    val onLocation: () -> Unit = { requestLocation(LocationAction.AddPlace) }
    val onAutomaticAdding: (Boolean) -> Unit = { enabled ->
        if (enabled) {
            requestLocation(LocationAction.EnableAutomaticAdding)
        } else {
            state.setAutoAddPlaces(false)
            LocationTrackingService.stop(activity)
        }
    }
    val onShowDistances: (Boolean) -> Unit = { enabled ->
        if (enabled) requestLocation(LocationAction.ShowDistances) else state.setShowDistances(false)
    }
    val onShareLocation: (Boolean) -> Unit = { enabled ->
        if (enabled) requestLocation(LocationAction.ShareLocation) else friendLocations.setSharing(false)
    }
    val showHome: () -> Unit = { destination = Destination.Home }
    val showAdd: () -> Unit = { destination = Destination.Places }
    val showShop: () -> Unit = {
        shopOrigin = destination
        destination = Destination.Shop
    }
    val showLeaderboard: () -> Unit = {
        leaderboardOrigin = destination
        destination = Destination.Leaderboard
    }

    val goBack: () -> Unit = {
        destination = when (destination) {
            Destination.Weather -> weatherOrigin
            Destination.Shop -> shopOrigin
            Destination.Leaderboard -> leaderboardOrigin
            else -> Destination.Home
        }
    }
    val onPlace: (Place) -> Unit = { place ->
        weatherOrigin = if (destination == Destination.Map) Destination.Map else Destination.Home
        state.selectPlace(place)
        destination = Destination.Weather
    }

    LaunchedEffect(state) {
        state.enrichCountryMetadata()
        if (destination == Destination.Weather) state.refreshWeather()
    }
    LaunchedEffect(activity, friendLocations) {
        activity.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            friendLocations.setForeground(true)
            try {
                kotlinx.coroutines.awaitCancellation()
            } finally {
                friendLocations.setForeground(false)
            }
        }
    }
    LaunchedEffect(activity, destination) {
        friendLocations.setMapVisible(destination == Destination.Map)
        if (destination != Destination.Map) return@LaunchedEffect
        if (!hasRequestedPermission && !hasLocationPermission()) requestLocation(LocationAction.ShowMapLocation)
        activity.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                if (hasLocationPermission()) state.refreshMapLocation() else state.clearMapLocation()
                delay(15_000)
            }
        }
    }
    LaunchedEffect(activity, state.showDistances) {
        if (!state.showDistances) return@LaunchedEffect
        activity.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (state.showDistances) {
                if (!hasLocationPermission()) {
                    state.setShowDistances(false)
                    break
                }
                state.refreshDistanceOrigin()
                delay(60_000)
            }
        }
    }
    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && state.autoAddPlaces) {
                if (hasLocationPermission()) {
                    startAutomaticAdding(askForNotification = false)
                } else {
                    state.setAutoAddPlaces(false)
                    LocationTrackingService.stop(activity)
                }
            }
        }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }
    BackHandler(enabled = destination != Destination.Home, onBack = goBack)

    val darkTheme = when (state.theme) {
        AppThemeMode.Light -> false
        AppThemeMode.Dark -> true
        AppThemeMode.System -> isSystemInDarkTheme()
    }
    LaunchedEffect(activity, darkTheme) {
        val systemBars = if (darkTheme) SystemBarStyle.dark(AndroidColor.TRANSPARENT) else {
            SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.argb(140, 0, 0, 0))
        }
        activity.enableEdgeToEdge(statusBarStyle = systemBars, navigationBarStyle = systemBars)
    }
    val entrance = remember(destination) { Animatable(if (destination == Destination.Map) 1f else 0f) }
    LaunchedEffect(entrance) { if (entrance.value < 1f) entrance.animateTo(1f, tween(durationMillis = 210)) }

    GetDaTripTheme(
        darkTheme = darkTheme,
        liquidGlass = state.liquidGlass,
        accent = state.accent,
        glassTransparency = state.glassTransparency,
    ) {
        val colors = GetDaTripDesign.colors
        Surface(modifier = Modifier.fillMaxSize(), color = colors.canvas, contentColor = colors.text) {
            val selected = when (destination) {
                Destination.Places -> Destination.Home
                Destination.Weather -> weatherOrigin
                Destination.Shop -> shopOrigin
                Destination.Leaderboard -> leaderboardOrigin
                else -> destination
            }
            NavigationShell(
                selected = selected,
                showNavigation = destination in mainDestinations &&
                    (destination != Destination.Messages || social.activeFriend == null),
                showBackdropLighting = destination != Destination.Map,
                onSelect = { destination = it },
            ) { navigationClearance ->
                val entranceModifier = if (destination == Destination.Map) Modifier else Modifier.graphicsLayer {
                    alpha = 0.83f + 0.17f * entrance.value
                    translationY = 8.dp.toPx() * (1f - entrance.value)
                }
                Box(Modifier.fillMaxSize().then(entranceModifier)) {
                    pages.SaveableStateProvider(destination.name) {
                        when (destination) {
                            Destination.Home -> HomeScreen(
                                state = state,
                                onAdd = showAdd,
                                onRefresh = onLocation,
                                onLocation = onLocation,
                                onPlace = onPlace,
                                onShop = showShop,
                                onLeaderboard = showLeaderboard,
                                navigationClearance = navigationClearance,
                            )
                            Destination.Places -> PlacesScreen(
                                state = state,
                                onLocation = {
                                    showHome()
                                    requestLocation(LocationAction.AddPlaceWithoutPoints)
                                },
                                onSelected = showHome,
                                onBack = showHome,
                                navigationClearance = navigationClearance,
                            )
                            Destination.Weather -> WeatherScreen(
                                state = state,
                                onLocation = onLocation,
                                onPlaces = showAdd,
                                onBack = goBack,
                                navigationClearance = navigationClearance,
                            )
                            Destination.Settings -> SettingsScreen(
                                state = state,
                                onAutoAddPlacesChanged = onAutomaticAdding,
                                onShowDistancesChanged = onShowDistances,
                                sharingLocation = friendLocations.sharing,
                                canShareLocation = social.profile != null,
                                locationSharingError = friendLocations.error,
                                onShareLocationChanged = onShareLocation,
                                onShop = showShop,
                                navigationClearance = navigationClearance,
                            )
                            Destination.Map -> UnlockedMapScreen(
                                places = state.unlockedPlaces,
                                onPlace = onPlace,
                                scratchColor = state.scratchColor,
                                darkMap = darkTheme,
                                satellite = state.satelliteMap,
                                onSatelliteChanged = state::setSatelliteMap,
                                onRequestLocation = { requestLocation(LocationAction.ShowMapLocation) },
                                trackedPeople = buildList {
                                    state.mapLocation?.takeIf { hasLocationPermission() }?.let { location ->
                                        add(MapTrackedPerson("self", tr("Moja lokalizacja", "My location", "Mein Standort", "Mi ubicación"),
                                            location.latitude, location.longitude, isSelf = true))
                                    }
                                    friendLocations.markers.forEach { marker ->
                                        add(MapTrackedPerson(marker.uid, marker.displayName, marker.latitude, marker.longitude))
                                    }
                                },
                                navigationClearance = navigationClearance,
                            )
                            Destination.Profile -> ProfileScreen(
                                social = social,
                                onShop = showShop,
                                onLeaderboard = showLeaderboard,
                                onConversation = { friend ->
                                    social.openChat(friend)
                                    destination = Destination.Messages
                                },
                                navigationClearance = navigationClearance,
                            )
                            Destination.Messages -> MessagesScreen(
                                social = social,
                                navigationClearance = navigationClearance,
                            )
                            Destination.Shop -> ShopScreen(
                                state = state,
                                onBack = goBack,
                                navigationClearance = navigationClearance,
                            )
                            Destination.Leaderboard -> LeaderboardScreen(
                                social = social,
                                onBack = goBack,
                                navigationClearance = navigationClearance,
                            )
                        }
                    }
                }
            }
        }
        if (showPermissionDialog) {
            val addingPlace = locationAction == LocationAction.AddPlace || locationAction == LocationAction.AddPlaceWithoutPoints
            AlertDialog(
                onDismissRequest = { showPermissionDialog = false },
                title = { Text(tr("Dostęp do lokalizacji", "Location access", "Standortzugriff", "Acceso a la ubicación")) },
                text = {
                    Text(
                        when (locationAction) {
                            LocationAction.AddPlace, LocationAction.AddPlaceWithoutPoints -> tr(
                                "Zezwól na lokalizację, aby dodać bieżącą miejscowość. Możesz też wybrać miejscowość ręcznie.",
                                "Allow location access to add your current place. You can also choose a place manually.",
                                "Erlaube den Standortzugriff, um deinen aktuellen Ort hinzuzufügen. Du kannst einen Ort auch manuell auswählen.",
                                "Permite el acceso a la ubicación para añadir tu localidad actual. También puedes elegirla manualmente.",
                            )
                            LocationAction.EnableAutomaticAdding -> tr(
                                "Zezwól na lokalizację, aby aplikacja mogła zapisywać odwiedzane miejscowości również w tle.",
                                "Allow location access to save the places you visit while the app is in the background.",
                                "Erlaube den Standortzugriff, damit die App auch im Hintergrund besuchte Orte speichern kann.",
                                "Permite el acceso a la ubicación para guardar las localidades que visitas aunque la app esté en segundo plano.",
                            )
                            LocationAction.ShowDistances -> tr(
                                "Zezwól na lokalizację, aby obliczać odległość od miejscowości.",
                                "Allow location access to calculate how far you are from each place.",
                                "Erlaube den Standortzugriff, um die Entfernung zu den Orten zu berechnen.",
                                "Permite el acceso a la ubicación para calcular la distancia a cada localidad.",
                            )
                            LocationAction.ShowMapLocation -> tr(
                                "Zezwól na lokalizację, aby pokazać Twoją pozycję na mapie.",
                                "Allow location access to show your position on the map.",
                                "Erlaube den Standortzugriff, um deine Position auf der Karte zu zeigen.",
                                "Permite el acceso a la ubicación para mostrar tu posición en el mapa.",
                            )
                            LocationAction.ShareLocation -> tr(
                                "Zezwól na lokalizację, aby dzielić się przybliżoną pozycją ze znajomymi.",
                                "Allow location access to share your approximate position with friends.",
                                "Erlaube den Standortzugriff, um deine ungefähre Position mit Freunden zu teilen.",
                                "Permite el acceso a la ubicación para compartir tu posición aproximada con amigos.",
                            )
                        },
                    )
                },
                dismissButton = {
                    TextButton(onClick = {
                        showPermissionDialog = false
                        if (addingPlace) showAdd()
                    }) { Text(if (addingPlace) tr("Wybierz miejsce", "Choose a place", "Ort auswählen", "Elegir una localidad") else tr("Anuluj", "Cancel", "Abbrechen", "Cancelar")) }
                },
                confirmButton = {
                    val canAskAgain = permissions.any(activity::shouldShowRequestPermissionRationale)
                    TextButton(onClick = {
                        showPermissionDialog = false
                        if (canAskAgain) {
                            locationLauncher.launch(permissions)
                        } else {
                            val details = Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", activity.packageName, null),
                            )
                            applicationSettingsLauncher.launch(details)
                        }
                    }) { Text(if (canAskAgain) tr("Ponów", "Try again", "Erneut versuchen", "Intentar de nuevo") else tr("Otwórz ustawienia", "Open settings", "Einstellungen öffnen", "Abrir ajustes")) }
                },
            )
        }
    }
}

@Composable
private fun NavigationShell(
    selected: Destination,
    showNavigation: Boolean,
    showBackdropLighting: Boolean,
    onSelect: (Destination) -> Unit,
    content: @Composable (Dp) -> Unit,
) {
    if (!showNavigation) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) { content(0.dp) }
        return
    }
    val colors = GetDaTripDesign.colors
    val glass = GetDaTripDesign.liquidGlass
    val transparency = GetDaTripDesign.glassTransparency
    val language = LocalAppLanguage.current
    val hazeState = rememberHazeState()
    val useHaze = glass && showBackdropLighting && transparency in 0.04f..0.96f
    val density = LocalDensity.current
    var barHeight by remember(glass) { mutableStateOf(80.dp) }
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        val source = if (useHaze) Modifier.hazeSource(hazeState) else Modifier
        val lighting = if (useHaze) Modifier.drawBehind {
            drawRect(colors.canvas)
            val reflectionOrigin = Offset(size.width * 0.23f, size.height * 0.98f)
            drawRect(
                Brush.radialGradient(
                    listOf(colors.accent.copy(alpha = 0.19f + transparency * 0.10f), Color.Transparent),
                    center = reflectionOrigin,
                    radius = (size.width * 0.82f).coerceAtLeast(1f),
                ),
            )
        } else Modifier
        Box(
            Modifier.matchParentSize()
                .padding(bottom = if (glass) 0.dp else barHeight)
                .then(source)
                .then(lighting),
        ) {
            content(if (glass) barHeight else 0.dp)
        }
        val barPosition = Modifier.align(Alignment.BottomCenter).onSizeChanged { size ->
            barHeight = with(density) { size.height.toDp() }
        }
        if (glass) {
            LiquidGlassNavigation(
                labels = MainTab.entries.map { it.label(language) },
                icons = MainTab.entries.map { it.icon },
                selectedIndex = MainTab.entries.indexOfFirst { it.destination == selected },
                onSelect = { index -> onSelect(MainTab.entries[index].destination) },
                modifier = barPosition.padding(start = 14.dp, end = 14.dp, bottom = 8.dp),
                backdrop = if (useHaze) Modifier.hazeEffect(
                    state = hazeState,
                    style = HazeStyle(
                        backgroundColor = colors.canvas,
                        tints = emptyList(),
                        blurRadius = (28f * (1f - kotlin.math.abs(transparency * 2f - 1f))).dp,
                        noiseFactor = 0.014f,
                    ),
                ) else Modifier,
            )
        } else {
            MainTabBar(selected, onSelect, barPosition)
        }
    }
}

@Composable
private fun MainTabBar(selected: Destination, onSelect: (Destination) -> Unit, modifier: Modifier) {
    val colors = GetDaTripDesign.colors
    val language = LocalAppLanguage.current
    NavigationBar(
        modifier = modifier,
        containerColor = colors.cardRaised,
        contentColor = colors.text,
        windowInsets = WindowInsets(0, 0, 0, 0),
    ) {
        MainTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = selected == tab.destination,
                onClick = { onSelect(tab.destination) },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(tab.label(language)) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = colors.accentInk,
                    selectedTextColor = colors.text,
                    indicatorColor = colors.accent,
                    unselectedIconColor = colors.mutedText,
                    unselectedTextColor = colors.mutedText,
                ),
            )
        }
    }
}

private fun applyLauncherIcon(activity: ComponentActivity, selected: GlobeIcon) {
    val manager = activity.packageManager
    fun setEnabled(icon: GlobeIcon, enabled: Boolean) {
        val component = ComponentName(activity.packageName, "${activity.packageName}.${icon.launcherAlias}")
        val current = manager.getComponentEnabledSetting(component)
        val isEnabled = current == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
            (current == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && icon == GlobeIcon.Og)
        if (isEnabled == enabled) return
        manager.setComponentEnabledSetting(
            component,
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }

    setEnabled(selected, true)
    GlobeIcon.entries.filter { it != selected }.forEach { setEnabled(it, false) }
}
