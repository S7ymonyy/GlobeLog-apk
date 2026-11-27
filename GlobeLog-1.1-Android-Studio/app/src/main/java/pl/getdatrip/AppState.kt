package pl.getdatrip

import android.content.Context
import android.location.Location
import android.os.SystemClock
import androidx.annotation.MainThread
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.TimeUnit
import pl.getdatrip.data.DEVICE_FALLBACK_NAME
import pl.getdatrip.data.DEVICE_PLACE_ID
import pl.getdatrip.data.Forecast
import pl.getdatrip.data.LOCALITY_ACCURACY_METERS
import pl.getdatrip.data.LocationUnavailableException
import pl.getdatrip.data.Place
import pl.getdatrip.data.WeatherException
import pl.getdatrip.data.WeatherRepository

@Stable
class AppState private constructor(context: Context, private val scope: CoroutineScope) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "getdatrip_preferences",
        Context.MODE_PRIVATE,
    )
    private var selectedLanguage by mutableStateOf(readLanguage())
    val language: AppLanguage get() = selectedLanguage
    private val repository = WeatherRepository(context.applicationContext) { language }

    private var selectedTheme by mutableStateOf(readTheme())
    val theme: AppThemeMode get() = selectedTheme

    private var glassEnabled by mutableStateOf(preferences.getBoolean("liquid_glass", false))
    val liquidGlass: Boolean get() = glassEnabled

    private val migrateAccentOwnership = !preferences.getBoolean("accent_catalog_099", false)
    private val migrateIconOwnership = !preferences.contains(STORE_ICONS_KEY)
    private var store by mutableStateOf(readStore())
    val ownsIconPack: Boolean get() = store.iconPackOwned || store.icons.containsAll(premiumGlobeIcons)

    private var selectedAccent by mutableStateOf(readAccent())
    val accent: AppAccent get() = selectedAccent

    private var selectedScratchColor by mutableStateOf(readScratchColor())
    val scratchColor: ScratchColor get() = selectedScratchColor

    private var selectedIcon by mutableStateOf(readIcon())
    val appIcon: GlobeIcon get() = selectedIcon

    private var satelliteEnabled by mutableStateOf(preferences.getBoolean("map_satellite", false))
    val satelliteMap: Boolean get() = satelliteEnabled

    private var glassTransparencyValue by mutableFloatStateOf(readGlassTransparency())
    val glassTransparency: Float get() = glassTransparencyValue

    private var countryFlagsEnabled by mutableStateOf(preferences.getBoolean("country_flags", false))
    val showCountryFlags: Boolean get() = countryFlagsEnabled

    private var automaticAddingEnabled by mutableStateOf(preferences.getBoolean("auto_add_places", false))
    val autoAddPlaces: Boolean get() = automaticAddingEnabled
    var autoTrackingActive by mutableStateOf(false)
        private set

    private var distancesEnabled by mutableStateOf(preferences.getBoolean("show_distances", false))
    val showDistances: Boolean get() = distancesEnabled

    var selectedPlace by mutableStateOf(readPlace("selected_place"))
        private set
    var devicePlace by mutableStateOf(
        readPlace("device_place") ?: selectedPlace?.takeIf { it.id == DEVICE_PLACE_ID },
    )
        private set
    var unlockedPlaces by mutableStateOf(readUnlockedPlaces())
        private set
    val savedPlaces: List<Place> get() = unlockedPlaces
    private var excludedPlaces = readPlaces("excluded_places").orEmpty()
    private var discoveryHistory by mutableStateOf(readPlaces(SCORE_DISCOVERIES_KEY).orEmpty())
    val discoveredCount: Int get() = discoveryHistory.size
    val earnedPoints: Int get() = discoveredCount / DISCOVERIES_PER_POINT
    val pointBalance: Int get() = (earnedPoints - store.spentPoints).coerceAtLeast(0)

    var forecast by mutableStateOf<Forecast?>(null)
        private set
    var weatherLoading by mutableStateOf(false)
        private set
    var weatherError by mutableStateOf<String?>(null)
        private set

    var locationLoading by mutableStateOf(false)
        private set
    var locationError by mutableStateOf<String?>(null)
        private set

    var mapLocation by mutableStateOf<Location?>(null)
        private set
    var mapLocationLoading by mutableStateOf(false)
        private set

    private var distanceOrigin by mutableStateOf<DistanceOrigin?>(null)
    var distanceLoading by mutableStateOf(false)
        private set
    var distanceError by mutableStateOf<String?>(null)
        private set

    var query by mutableStateOf("")
        private set
    var searchResults by mutableStateOf(emptyList<Place>())
        private set
    var searchLoading by mutableStateOf(false)
        private set
    var searchError by mutableStateOf<String?>(null)
        private set

    private var weatherJob: Job? = null
    private var selectAfterLocation = false
    private var unlockAfterLocation = false
    private var creditAfterLocation = false
    private var manualAfterLocation = false
    private var searchJob: Job? = null
    private var countryMetadataJob: Job? = null
    private var distanceJob: Job? = null
    private var distanceExpiryJob: Job? = null
    private var mapLocationJob: Job? = null

    init {
        val previousAccent = preferences.all["accent"] as? String
        val needsAccentMigration = previousAccent != null && previousAccent != selectedAccent.name
        if (needsAccentMigration || migrateAccentOwnership || migrateIconOwnership) {
            val editor = preferences.edit()
            if (needsAccentMigration) editor.putString("accent", selectedAccent.name)
            if (migrateAccentOwnership) {
                editor.putStringSet("store_accents", store.accents.mapTo(mutableSetOf()) { it.name })
                editor.putBoolean("accent_catalog_099", true)
            }
            if (migrateIconOwnership) {
                editor.putStringSet(STORE_ICONS_KEY, store.icons.mapTo(mutableSetOf()) { it.name })
                editor.putString("app_icon", selectedIcon.name)
            }
            editor.apply()
        }
    }

    fun setLanguage(language: AppLanguage) {
        if (selectedLanguage == language) return

        selectedLanguage = language
        preferences.edit().putString("language", language.code).apply()

        val retryForecast = weatherError != null && forecast == null && selectedPlace != null
        weatherError = null
        locationError = null
        distanceError = null
        searchForQuery()
        if (retryForecast) refreshWeather()
    }

    fun setTheme(theme: AppThemeMode) {
        if (selectedTheme == theme) return
        selectedTheme = theme
        preferences.edit().putString("app_theme", theme.name).apply()
    }

    fun setLiquidGlass(enabled: Boolean) {
        glassEnabled = enabled
        preferences.edit().putBoolean("liquid_glass", enabled).apply()
    }

    fun setAccent(accent: AppAccent) {
        if (selectedAccent == accent || !isAccentOwned(accent)) return

        selectedAccent = accent
        preferences.edit().putString("accent", accent.name).apply()
    }

    fun isAccentOwned(accent: AppAccent): Boolean = accent !in premiumAppAccents || accent in store.accents

    fun setScratchColor(color: ScratchColor) {
        if (selectedScratchColor == color || !isScratchOwned(color)) return

        selectedScratchColor = color
        preferences.edit().putString("scratch_color", color.name).apply()
    }

    fun isScratchOwned(color: ScratchColor): Boolean = color == ScratchColor.Blue || color in store.scratchColors

    fun setAppIcon(icon: GlobeIcon) {
        if (selectedIcon == icon || !isIconOwned(icon)) return

        selectedIcon = icon
        preferences.edit().putString("app_icon", icon.name).apply()
    }

    fun isIconOwned(icon: GlobeIcon): Boolean = icon == GlobeIcon.Og || store.iconPackOwned || icon in store.icons

    fun setSatelliteMap(enabled: Boolean) {
        if (satelliteEnabled == enabled) return

        satelliteEnabled = enabled
        preferences.edit().putBoolean("map_satellite", enabled).apply()
    }

    @MainThread
    @Synchronized
    fun purchaseAccent(accent: AppAccent): Boolean {
        if (isAccentOwned(accent)) return true
        return purchase(ACCENT_PRICE, store.copy(accents = store.accents + accent))
    }

    @MainThread
    @Synchronized
    fun purchaseScratch(color: ScratchColor): Boolean {
        if (isScratchOwned(color)) return true
        return purchase(SCRATCH_PRICE, store.copy(scratchColors = store.scratchColors + color))
    }

    @MainThread
    @Synchronized
    fun purchaseIcon(icon: GlobeIcon): Boolean {
        if (isIconOwned(icon)) return true
        return purchase(ICON_PRICE, store.copy(icons = store.icons + icon))
    }

    @MainThread
    @Synchronized
    fun purchaseIconPack(): Boolean {
        if (ownsIconPack) return true
        return purchase(ICON_PACK_PRICE, store.copy(iconPackOwned = true))
    }

    private fun purchase(price: Int, ownership: Store): Boolean {
        if (pointBalance < price) return false

        val previous = store
        val purchased = ownership.copy(spentPoints = previous.spentPoints + price)
        if (!editStore(purchased).commit()) {
            editStore(previous).apply()
            return false
        }

        store = purchased
        return true
    }

    private fun editStore(store: Store) = preferences.edit()
        .putInt(SCORE_SPENT_POINTS_KEY, store.spentPoints)
        .putStringSet("store_accents", store.accents.mapTo(mutableSetOf()) { it.name })
        .putStringSet("store_scratch_colors", store.scratchColors.mapTo(mutableSetOf()) { it.name })
        .putStringSet(STORE_ICONS_KEY, store.icons.mapTo(mutableSetOf()) { it.name })
        .putBoolean("store_icon_pack", store.iconPackOwned)

    fun setGlassTransparency(transparency: Float) {
        if (!transparency.isFinite()) return
        glassTransparencyValue = transparency.coerceIn(0f, 1f)
    }

    fun saveGlassTransparency() = preferences.edit().putFloat("glass_transparency", glassTransparencyValue).apply()

    fun setShowCountryFlags(enabled: Boolean) {
        countryFlagsEnabled = enabled
        preferences.edit().putBoolean("country_flags", enabled).apply()
    }

    fun setAutoAddPlaces(enabled: Boolean) {
        automaticAddingEnabled = enabled
        preferences.edit().putBoolean("auto_add_places", enabled).apply()
    }

    @MainThread
    internal fun setAutoTrackingActive(active: Boolean) {
        autoTrackingActive = active
    }

    fun setShowDistances(enabled: Boolean) {
        if (distancesEnabled == enabled) return

        distancesEnabled = enabled
        preferences.edit().putBoolean("show_distances", enabled).apply()
        if (!enabled) clearDistanceOrigin()
    }

    fun refreshMapLocation() {
        if (mapLocationLoading) return

        mapLocationLoading = true
        mapLocationJob = scope.launch {
            try {
                val fix = repository.currentLocation(maxCachedAgeMillis = 25_000)
                ensureActive()
                mapLocation = Location(fix)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mapLocation = null
            } finally {
                if (isActive) mapLocationLoading = false
            }
        }
    }

    fun clearMapLocation() {
        mapLocationJob?.cancel()
        mapLocationJob = null
        mapLocation = null
        mapLocationLoading = false
    }

    fun refreshDistanceOrigin() {
        if (!showDistances || distanceLoading) return

        distanceError = null
        distanceLoading = true
        distanceJob = scope.launch {
            try {
                val location = repository.currentLocation()
                ensureActive()
                onDistanceOrigin(location)
                if (distanceOrigin == null) distanceError = distanceFailure
            } catch (error: CancellationException) {
                throw error
            } catch (error: LocationUnavailableException) {
                ensureActive()
                distanceError = if (error.locationDisabled) {
                    language.text(
                        "Włącz lokalizację telefonu, aby wyświetlać odległości.",
                        "Turn on your phone’s location to see distances.",
                        "Aktiviere den Standort deines Geräts, um Entfernungen zu sehen.",
                        "Activa la ubicación del teléfono para ver las distancias.",
                    )
                } else {
                    distanceFailure
                }
            } catch (_: SecurityException) {
                ensureActive()
                distanceError = language.text(
                    "Zezwól na dostęp do lokalizacji, aby wyświetlać odległości.",
                    "Allow location access to see distances.",
                    "Erlaube den Standortzugriff, um Entfernungen zu sehen.",
                    "Permite el acceso a la ubicación para ver las distancias.",
                )
            } catch (_: Exception) {
                ensureActive()
                distanceError = distanceFailure
            } finally {
                if (isActive) distanceLoading = false
            }
        }
    }

    fun clearDistanceOrigin() {
        distanceJob?.cancel()
        distanceJob = null
        distanceExpiryJob?.cancel()
        distanceExpiryJob = null
        distanceOrigin = null
        distanceLoading = false
        distanceError = null
    }

    @MainThread
    fun onDistanceOrigin(location: Location) {
        if (!showDistances || location.latitude !in -90.0..90.0 || location.longitude !in -180.0..180.0) {
            return
        }

        val measuredAt = location.elapsedRealtimeNanos
        val age = SystemClock.elapsedRealtimeNanos() - measuredAt
        if (age !in 0L until DISTANCE_MAX_AGE_NANOS || measuredAt <= (distanceOrigin?.measuredAt ?: 0L)) {
            return
        }

        val origin = DistanceOrigin(location.latitude, location.longitude, measuredAt)
        distanceOrigin = origin
        distanceError = null
        distanceExpiryJob?.cancel()
        distanceExpiryJob = scope.launch {
            delay(TimeUnit.NANOSECONDS.toMillis(DISTANCE_MAX_AGE_NANOS - age) + 1)
            if (distanceOrigin == origin) distanceOrigin = null
        }
    }

    fun distanceTo(place: Place): Float? {
        if (!showDistances) return null
        val origin = distanceOrigin ?: return null
        val age = SystemClock.elapsedRealtimeNanos() - origin.measuredAt
        if (age !in 0L until DISTANCE_MAX_AGE_NANOS) return null

        val meters = FloatArray(1)
        Location.distanceBetween(origin.latitude, origin.longitude, place.latitude, place.longitude, meters)
        return meters[0].takeIf { it.isFinite() }
    }

    fun isSaved(place: Place): Boolean = unlockedPlaces.any { it.sameCity(place) }

    fun isCurrentDevicePlace(place: Place): Boolean = devicePlace?.sameCity(place) == true

    fun toggleSaved(place: Place) {
        if (isSaved(place)) removeUnlocked(place) else addUnlocked(place, manuallyAdded = true)
    }

    fun addSaved(place: Place) = addUnlocked(place, manuallyAdded = true)

    fun removeSaved(place: Place) = removeUnlocked(place)

    private fun addUnlocked(place: Place, creditVisit: Boolean = false, manuallyAdded: Boolean = false) {
        val candidate = place.copy(manuallyAdded = manuallyAdded || place.manuallyAdded)
        val matchingPlaces = unlockedPlaces.filter { it.sameCity(candidate) }
        val existing = matchingPlaces.firstOrNull()
        val discovered = if (existing == null) candidate.asSnapshot() else {
            matchingPlaces.drop(1).fold(existing.withDetailsFrom(candidate)) { merged, duplicate ->
                merged.withDetailsFrom(duplicate)
            }
        }
        val places = if (existing == null) {
            listOf(discovered) + unlockedPlaces
        } else {
            unlockedPlaces.mapNotNull {
                when {
                    it.id == existing.id -> discovered
                    it.sameCity(candidate) -> null
                    else -> it
                }
            }
        }
        val discoveries = if (creditVisit) historyWithVisit(candidate) else discoveryHistory
        writePlaceCollections(places, excludedPlaces.filterNot { it.sameCity(place) }, discoveries)
    }

    private fun historyWithVisit(place: Place): List<Place> {
        if (place.name == DEVICE_FALLBACK_NAME) return discoveryHistory
        val index = discoveryHistory.indexOfFirst { it.sameCity(place) }
        if (index == -1) return discoveryHistory + place.asSnapshot()

        val existing = discoveryHistory[index]
        val enriched = existing.withDetailsFrom(place)
        if (enriched == existing) return discoveryHistory
        return discoveryHistory.toMutableList().apply { this[index] = enriched }
    }

    fun removeUnlocked(place: Place) {
        val keptPlaces = unlockedPlaces.filterNot { it.sameCity(place) }
        if (keptPlaces.size == unlockedPlaces.size) return

        if (isCurrentDevicePlace(place)) unlockAfterLocation = false
        val exclusions = excludedPlaces.filterNot { it.sameCity(place) } + place.asSnapshot()
        writePlaceCollections(keptPlaces, exclusions)
        if (selectedPlace?.sameCity(place) == true) clearSelection()
    }

    fun selectPlace(place: Place) {
        selectAfterLocation = false
        clearLocationError()
        val liveDevicePlace = devicePlace?.takeIf { place.isDeviceSnapshot && it.sameCity(place) }
        applySelection(liveDevicePlace ?: place)
    }

    fun clearLocationError() {
        locationError = null
    }

    @MainThread
    fun onTrackedPlace(place: Place) {
        writeDevicePlace(place)
        if (autoAddPlaces && place.name != DEVICE_FALLBACK_NAME && !isExcluded(place)) {
            addUnlocked(place, creditVisit = true)
        }

        val selection = selectedPlace
        if (selection?.id == DEVICE_PLACE_ID && !selection.sameCity(place) && isSaved(place)) {
            applySelection(place)
        }
    }

    fun useLocation(select: Boolean = true, unlock: Boolean = false, creditVisit: Boolean = true) {
        selectAfterLocation = selectAfterLocation || select
        unlockAfterLocation = unlockAfterLocation || unlock
        creditAfterLocation = creditAfterLocation || creditVisit
        manualAfterLocation = manualAfterLocation || !creditVisit
        if (locationLoading) return

        clearLocationError()
        locationLoading = true
        scope.launch {
            val requestLanguage = language
            try {
                val location = repository.currentLocation(forLocality = true)
                ensureActive()
                onDistanceOrigin(location)
                val place = repository.resolvePlace(location)
                ensureActive()
                if (place.name == DEVICE_FALLBACK_NAME) {
                    if (devicePlace == null) writeDevicePlace(place)
                    locationError = if (!location.hasAccuracy() || location.accuracy > LOCALITY_ACCURACY_METERS) language.text(
                        "Nie udało się ustalić miejscowości. Włącz dokładną lokalizację i spróbuj ponownie.",
                        "Could not identify the place. Turn on precise location and try again.",
                        "Der Ort konnte nicht ermittelt werden. Aktiviere den genauen Standort und versuche es erneut.",
                        "No se pudo identificar la localidad. Activa la ubicación precisa e inténtalo de nuevo.",
                    ) else language.text(
                        "Nie udało się ustalić nazwy miejscowości. Sprawdź połączenie i spróbuj ponownie.",
                        "Could not find the name of this place. Check your connection and try again.",
                        "Der Ortsname konnte nicht ermittelt werden. Prüfe deine Verbindung und versuche es erneut.",
                        "No se pudo identificar el nombre de esta localidad. Comprueba tu conexión e inténtalo de nuevo.",
                    )
                    if (selectAfterLocation) applySelection(place)
                    return@launch
                }
                writeDevicePlace(place)
                if (unlockAfterLocation || !isExcluded(place)) {
                    addUnlocked(place, creditVisit = creditAfterLocation, manuallyAdded = manualAfterLocation)
                }
                if ((selectAfterLocation || selectedPlace?.id == DEVICE_PLACE_ID) && isSaved(place)) {
                    applySelection(place)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: LocationUnavailableException) {
                ensureActive()
                locationError = error.message.takeIf { language == requestLanguage } ?: locationFailure
            } catch (_: SecurityException) {
                ensureActive()
                locationError = language.text(
                    "Zezwól na dostęp do lokalizacji w ustawieniach telefonu.",
                    "Allow location access in your phone’s settings.",
                    "Erlaube den Standortzugriff in den Einstellungen deines Geräts.",
                    "Permite el acceso a la ubicación en los ajustes del teléfono.",
                )
            } catch (_: Exception) {
                ensureActive()
                locationError = locationFailure
            } finally {
                if (isActive) {
                    selectAfterLocation = false
                    unlockAfterLocation = false
                    creditAfterLocation = false
                    manualAfterLocation = false
                    locationLoading = false
                    if (locationError != null && forecast == null && !weatherLoading) {
                        refreshWeather()
                    }
                }
            }
        }
    }

    fun refreshWeather() {
        val place = selectedPlace ?: return
        weatherJob?.cancel()
        weatherLoading = true
        weatherError = null
        weatherJob = scope.launch {
            val requestLanguage = language
            try {
                val result = repository.forecast(place)
                ensureActive()
                forecast = result.copy(place = selectedPlace ?: place)
            } catch (error: CancellationException) {
                throw error
            } catch (error: WeatherException) {
                ensureActive()
                weatherError = error.message.takeIf { language == requestLanguage } ?: weatherFailure
            } catch (_: Exception) {
                ensureActive()
                weatherError = weatherFailure
            } finally {
                if (isActive) weatherLoading = false
            }
        }
    }

    fun updateQuery(value: String) {
        if (query == value) return

        query = value
        searchForQuery()
    }

    private fun searchForQuery() {
        searchJob?.cancel()
        searchResults = emptyList()
        searchError = null
        val term = query.trim()
        searchLoading = term.length >= 2
        if (!searchLoading) return

        searchJob = scope.launch {
            try {
                delay(350)
                val results = repository.searchPlaces(term)
                ensureActive()
                searchResults = results
            } catch (error: CancellationException) {
                throw error
            } catch (error: WeatherException) {
                ensureActive()
                searchError = error.message ?: searchFailure
            } catch (_: Exception) {
                ensureActive()
                searchError = searchFailure
            } finally {
                if (isActive) searchLoading = false
            }
        }
    }

    fun enrichCountryMetadata() {
        if (countryMetadataJob?.isActive == true) return

        val candidates = (listOfNotNull(devicePlace, selectedPlace) + unlockedPlaces)
            .distinct()
            .filter { it.countryCode.isBlank() || it.countryName.isBlank() }
        if (candidates.isEmpty()) return

        countryMetadataJob = scope.launch {
            for (place in candidates) {
                val enriched = try {
                    repository.enrichPlaceCountry(place)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    continue
                }
                ensureActive()
                if (enriched == place) continue

                if (devicePlace == place) writeDevicePlace(enriched)
                if (selectedPlace == place) {
                    writeSelectedPlace(enriched)
                    forecast = forecast?.copy(place = enriched)
                }
                writePlaceCollections(unlockedPlaces.map { if (it == place) enriched else it })
            }
        }
    }

    private fun applySelection(place: Place) {
        if (selectedPlace != place) forecast = null
        writeSelectedPlace(place)
        refreshWeather()
    }

    private fun clearSelection() {
        weatherJob?.cancel()
        selectedPlace = null
        forecast = null
        weatherLoading = false
        weatherError = null
        preferences.edit().remove("selected_place").apply()
    }

    private fun writeDevicePlace(place: Place) {
        if (devicePlace == place && preferences.contains("device_place")) return

        devicePlace = place
        val editor = preferences.edit().putString("device_place", place.toJson().toString())
        if (!preferences.contains("unlocked_places")) {
            editor.putString("unlocked_places", unlockedPlaces.toJsonArray())
        }
        editor.apply()
    }

    private fun writeSelectedPlace(place: Place) {
        selectedPlace = place
        val editor = preferences.edit().putString("selected_place", place.toJson().toString())
        if (!preferences.contains("device_place")) {
            devicePlace?.let { editor.putString("device_place", it.toJson().toString()) }
        }
        editor.apply()
    }

    private fun writePlaceCollections(
        places: List<Place>,
        exclusions: List<Place> = excludedPlaces,
        discoveries: List<Place> = discoveryHistory,
    ) {
        if (unlockedPlaces == places && excludedPlaces == exclusions && discoveryHistory == discoveries) return

        val editor = preferences.edit()
            .putString("unlocked_places", places.toJsonArray())
            .putString("excluded_places", exclusions.toJsonArray())
        if (discoveryHistory != discoveries) {
            editor.putString(SCORE_DISCOVERIES_KEY, discoveries.toJsonArray())
        }
        editor.apply()
        unlockedPlaces = places
        excludedPlaces = exclusions
        discoveryHistory = discoveries
    }

    private fun readPlace(key: String): Place? {
        val json = preferences.getString(key, null) ?: return null
        return try {
            JSONObject(json).toPlace()
        } catch (_: JSONException) {
            null
        }
    }

    private fun readAccent(): AppAccent {
        val storedName = preferences.all["accent"] as? String
        return AppAccent.entries.firstOrNull { it.name == storedName && isAccentOwned(it) }
            ?: AppAccent.Lavender.takeIf(::isAccentOwned) ?: AppAccent.Purple
    }

    private fun readScratchColor(): ScratchColor {
        val storedName = preferences.all["scratch_color"] as? String
        return ScratchColor.entries.firstOrNull { it.name == storedName && isScratchOwned(it) } ?: ScratchColor.Blue
    }

    private fun readIcon(): GlobeIcon {
        val storedName = preferences.all["app_icon"] as? String
        val previousDefault = migrateIconOwnership && GlobeIcon.Default in store.icons
        return GlobeIcon.entries.firstOrNull { it.name == storedName && isIconOwned(it) }
            ?: if (previousDefault) GlobeIcon.Default else GlobeIcon.Og
    }

    private fun readStore(): Store {
        val stored = preferences.all
        val accents = stored["store_accents"] as? Set<*> ?: emptySet<String>()
        val scratchColors = stored["store_scratch_colors"] as? Set<*> ?: emptySet<String>()
        val icons = stored[STORE_ICONS_KEY] as? Set<*> ?: emptySet<String>()
        val ownedIcons = premiumGlobeIcons.filterTo(mutableSetOf()) { it.name in icons }
        if (migrateIconOwnership && stored.isNotEmpty()) {
            ownedIcons.add(GlobeIcon.Default)
        }
        val ownedAccents = premiumAppAccents.filterTo(mutableSetOf()) { it.name in accents }
        if (migrateAccentOwnership && ("discovery_history" in stored || "accent" in stored)) {
            val previousAccent = AppAccent.entries.firstOrNull {
                it.name == stored["accent"] && (it !in premiumAppAccents || it in newPremiumAppAccents || it in ownedAccents)
            } ?: AppAccent.Lavender
            if (previousAccent in newPremiumAppAccents) ownedAccents.add(previousAccent)
        }
        return Store(
            spentPoints = (stored[SCORE_SPENT_POINTS_KEY] as? Int ?: 0).coerceAtLeast(0),
            accents = ownedAccents,
            scratchColors = ScratchColor.entries.filterTo(mutableSetOf()) { it.name in scratchColors },
            icons = ownedIcons,
            iconPackOwned = stored["store_icon_pack"] == true,
        )
    }

    private fun readLanguage(): AppLanguage {
        val storedCode = preferences.all["language"] as? String
        return AppLanguage.entries.firstOrNull { it.code == storedCode } ?: AppLanguage.Polish
    }

    private fun readTheme(): AppThemeMode {
        val stored = preferences.all
        return AppThemeMode.entries.firstOrNull { it.name == stored["app_theme"] }
            ?: if (stored["amoled"] == true) AppThemeMode.Dark else AppThemeMode.System
    }

    private fun readGlassTransparency(): Float {
        val storedValue = preferences.all["glass_transparency"] as? Float
        return storedValue?.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: DEFAULT_GLASS_TRANSPARENCY
    }

    private fun readUnlockedPlaces(): List<Place> {
        val stored = readPlaces("unlocked_places") ?: return readLegacyUnlockedPlaces()
        val merged = stored.mergeDuplicatePlaces()
        if (stored != merged) preferences.edit().putString("unlocked_places", merged.toJsonArray()).apply()
        return merged
    }

    private fun readLegacyUnlockedPlaces(): List<Place> {
        val saved = readPlaces("saved_places").orEmpty()
        val detected = devicePlace?.takeUnless { it.name == DEVICE_FALLBACK_NAME }?.asSnapshot()
        val current = detected?.let { device -> saved.firstOrNull { it.sameCity(device) } ?: device }
        return (listOfNotNull(current) + saved).mergeDuplicatePlaces()
    }

    private fun readPlaces(key: String): List<Place>? {
        val json = preferences.all[key] as? String ?: return null
        return try {
            val storedPlaces = JSONArray(json)
            (0 until storedPlaces.length()).mapNotNull { index ->
                storedPlaces.optJSONObject(index)?.toPlace()
            }
        } catch (_: JSONException) {
            null
        }
    }

    private fun List<Place>.mergeDuplicatePlaces(): List<Place> = fold(mutableListOf<Place>()) { merged, place ->
        val index = merged.indexOfFirst { it.sameCity(place) }
        if (index < 0) merged.add(place) else merged[index] = merged[index].withDetailsFrom(place)
        merged
    }

    private fun isExcluded(place: Place): Boolean = excludedPlaces.any { it.sameCity(place) }

    private fun JSONObject.toPlace(): Place? {
        val id = optString("id")
        val name = optString("name")
        val latitude = optDouble("latitude")
        val longitude = optDouble("longitude")
        if (id.isBlank() || name.isBlank() || latitude !in -90.0..90.0 || longitude !in -180.0..180.0) {
            return null
        }
        return Place(
            id = id,
            name = name,
            latitude = latitude,
            longitude = longitude,
            region = optString("region"),
            countryName = optString("countryName"),
            countryCode = optString("countryCode"),
            localityId = optString("localityId", id.takeIf { it.toLongOrNull() != null }.orEmpty()),
            subdivisionCode = optString("subdivisionCode"),
            manuallyAdded = optBoolean("manuallyAdded", id.toLongOrNull() != null),
            localityAliases = optJSONArray("localityAliases")?.let { aliases ->
                (0 until aliases.length()).mapNotNull { index -> aliases.optString(index).takeIf(String::isNotBlank) }
            }.orEmpty(),
        )
    }

    private fun Place.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("latitude", latitude)
        .put("longitude", longitude)
        .put("region", region)
        .put("countryName", countryName)
        .put("countryCode", countryCode)
        .put("localityId", localityId)
        .put("subdivisionCode", subdivisionCode)
        .put("manuallyAdded", manuallyAdded)
        .put("localityAliases", JSONArray(localityAliases))

    private fun List<Place>.toJsonArray(): String = JSONArray().also { entries ->
        forEach { entries.put(it.toJson()) }
    }.toString()

    private val Place.isDeviceSnapshot: Boolean get() = id.startsWith("$DEVICE_PLACE_ID:")

    private fun Place.asSnapshot(): Place = if (id == DEVICE_PLACE_ID) {
        copy(id = "$DEVICE_PLACE_ID:${localityId.ifBlank { "$latitude:$longitude" }}")
    } else {
        this
    }

    private fun Place.withDetailsFrom(place: Place): Place {
        val isCanonicalGpsUpdate = (isDeviceSnapshot || id == DEVICE_PLACE_ID) &&
            place.localityId.isNotBlank() && localityId.isBlank()
        val hasBetterName = (name == DEVICE_FALLBACK_NAME && place.name != DEVICE_FALLBACK_NAME) || isCanonicalGpsUpdate
        return copy(
            name = if (hasBetterName) place.name else name,
            region = if (hasBetterName) place.region.ifBlank { region } else region.ifBlank { place.region },
            countryName = countryName.ifBlank { place.countryName },
            countryCode = countryCode.ifBlank { place.countryCode },
            localityId = localityId.ifBlank { place.localityId },
            subdivisionCode = subdivisionCode.ifBlank { place.subdivisionCode },
            manuallyAdded = manuallyAdded || place.manuallyAdded,
            localityAliases = (localityAliases + place.localityAliases).distinct(),
        )
    }

    private fun Place.sameCity(place: Place): Boolean {
        if (id == place.id && id != DEVICE_PLACE_ID) return true
        if (countryCode.isNotBlank() && place.countryCode.isNotBlank() && countryCode != place.countryCode) {
            return false
        }
        val firstIdentity = localityId.ifBlank { id.takeIf { it.toLongOrNull() != null }.orEmpty() }
        val secondIdentity = place.localityId.ifBlank { place.id.takeIf { it.toLongOrNull() != null }.orEmpty() }
        if (firstIdentity.isNotBlank() && secondIdentity.isNotBlank()) return firstIdentity == secondIdentity
        if (subdivisionCode.isNotBlank() && place.subdivisionCode.isNotBlank() && subdivisionCode != place.subdivisionCode) {
            return false
        }
        val involvesDevice = id == DEVICE_PLACE_ID || place.id == DEVICE_PLACE_ID ||
            isDeviceSnapshot || place.isDeviceSnapshot
        if (involvesDevice && latitude == place.latitude && longitude == place.longitude) return true

        val missingCity = name == DEVICE_FALLBACK_NAME || place.name == DEVICE_FALLBACK_NAME
        val firstName = normalizeCity(name)
        val secondName = normalizeCity(place.name)
        val sameName = firstName == secondName
        val aliasMatch = involvesDevice && (localityAliases.any { normalizeCity(it) == secondName } ||
            place.localityAliases.any { normalizeCity(it) == firstName })
        if (!missingCity && !sameName && !aliasMatch) return false

        val distance = FloatArray(1)
        Location.distanceBetween(latitude, longitude, place.latitude, place.longitude, distance)
        val maxDistance = when {
            missingCity -> 200f
            aliasMatch && !sameName -> 1_000f
            else -> 4_000f
        }
        return distance[0] <= maxDistance
    }

    private fun normalizeCity(name: String): String = Normalizer.normalize(name.trim(), Normalizer.Form.NFD)
        .replace(DIACRITICS, "")
        .lowercase(Locale.ROOT)
        .replace('ł', 'l')

    private val distanceFailure: String get() = language.text(
        "Nie udało się ustalić położenia telefonu.",
        "Couldn’t determine your phone’s location.",
        "Der Standort deines Geräts konnte nicht ermittelt werden.",
        "No se pudo determinar la ubicación de tu teléfono.",
    )

    private val locationFailure: String get() = language.text(
        "Nie udało się ustalić lokalizacji. Spróbuj ponownie.",
        "Couldn’t determine your location. Try again.",
        "Dein Standort konnte nicht ermittelt werden. Versuche es erneut.",
        "No se pudo determinar tu ubicación. Inténtalo de nuevo.",
    )

    private val weatherFailure: String get() = language.text(
        "Nie udało się pobrać pogody. Sprawdź połączenie i spróbuj ponownie.",
        "Couldn’t load the weather. Check your connection and try again.",
        "Das Wetter konnte nicht geladen werden. Prüfe deine Verbindung und versuche es erneut.",
        "No se pudo cargar el tiempo. Comprueba tu conexión e inténtalo de nuevo.",
    )

    private val searchFailure: String get() = language.text(
        "Nie udało się wyszukać miejscowości. Spróbuj ponownie.",
        "Couldn’t search for towns. Try again.",
        "Die Ortssuche ist fehlgeschlagen. Versuche es erneut.",
        "No se pudieron buscar localidades. Inténtalo de nuevo.",
    )

    private data class DistanceOrigin(val latitude: Double, val longitude: Double, val measuredAt: Long)

    private data class Store(
        val spentPoints: Int,
        val accents: Set<AppAccent>,
        val scratchColors: Set<ScratchColor>,
        val icons: Set<GlobeIcon>,
        val iconPackOwned: Boolean,
    )

    companion object {
        private const val STORE_ICONS_KEY = "store_icons"
        // A new points season starts with its own history and spending. Cosmetic ownership is shared.
        private const val SCORE_DISCOVERIES_KEY = "discovery_history_v11"
        private const val SCORE_SPENT_POINTS_KEY = "store_spent_points_v11"
        private const val DISCOVERIES_PER_POINT = 3
        private val DIACRITICS = Regex("\\p{M}+")
        private val DISTANCE_MAX_AGE_NANOS = TimeUnit.MINUTES.toNanos(5)

        @Volatile
        private var instance: AppState? = null

        fun get(context: Context): AppState = instance ?: synchronized(this) {
            instance ?: AppState(
                context.applicationContext,
                CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
            ).also { instance = it }
        }
    }
}
