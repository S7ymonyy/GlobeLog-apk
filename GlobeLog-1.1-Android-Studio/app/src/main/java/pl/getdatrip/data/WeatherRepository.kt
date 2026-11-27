package pl.getdatrip.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.SimpleTimeZone
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONException
import org.json.JSONObject
import pl.getdatrip.AppLanguage

const val DEVICE_PLACE_ID = "device"
const val DEVICE_FALLBACK_NAME = "Moja lokalizacja"
internal const val LOCALITY_ACCURACY_METERS = 250f
private const val MAX_RAW_ACCURACY_METERS = 10_000f

internal fun Location.isRecentFix(maxAgeMillis: Long, maxAccuracy: Float = MAX_RAW_ACCURACY_METERS): Boolean =
    hasAccuracy() && accuracy.isFinite() && accuracy in 0f..maxAccuracy &&
        latitude in -90.0..90.0 && longitude in -180.0..180.0 &&
        SystemClock.elapsedRealtimeNanos() - elapsedRealtimeNanos in 0L..TimeUnit.MILLISECONDS.toNanos(maxAgeMillis)

internal fun LocationManager.relevantProviders(precise: Boolean, enabledOnly: Boolean): List<String> {
    val providers = try {
        getProviders(enabledOnly)
    } catch (_: RuntimeException) {
        emptyList()
    }
    return providers.filter { provider ->
        when (provider) {
            LocationManager.GPS_PROVIDER -> precise
            LocationManager.NETWORK_PROVIDER, LocationManager.FUSED_PROVIDER -> true
            else -> false
        }
    }
}

internal fun LocationManager.cachedFix(provider: String): Location? = try {
    getLastKnownLocation(provider)
} catch (_: IllegalArgumentException) {
    null
} catch (_: IllegalStateException) {
    null
} catch (_: SecurityException) {
    null
}

@SuppressLint("MissingPermission")
internal fun LocationManager.subscribeForFixes(
    provider: String,
    request: LocationRequestCompat,
    listener: LocationListenerCompat,
): Boolean = try {
    LocationManagerCompat.requestLocationUpdates(this, provider, request, listener, Looper.getMainLooper())
    true
} catch (_: IllegalArgumentException) {
    false
} catch (_: IllegalStateException) {
    false
} catch (_: SecurityException) {
    false
}

internal fun LocationManager.unsubscribeFromFixes(listener: LocationListenerCompat) {
    try {
        LocationManagerCompat.removeUpdates(this, listener)
    } catch (_: IllegalArgumentException) {
    } catch (_: IllegalStateException) {
    } catch (_: SecurityException) {
    }
}

data class Place(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val region: String = "",
    val countryName: String = "",
    val countryCode: String = "",
    val localityId: String = "",
    val subdivisionCode: String = "",
    val manuallyAdded: Boolean = false,
    val localityAliases: List<String> = emptyList(),
)

data class Forecast(
    val place: Place,
    val current: CurrentWeather,
    val hourly: List<HourlyWeather>,
    val daily: List<DailyWeather>,
)

data class CurrentWeather(
    val temperature: Double,
    val feelsLike: Double,
    val humidity: Int,
    val windSpeed: Double,
    val code: Int,
    val isDay: Boolean,
)

data class HourlyWeather(
    val time: String,
    val temperature: Double,
    val precipitationProbability: Int,
    val code: Int,
    val isDay: Boolean,
)

data class DailyWeather(
    val date: String,
    val high: Double,
    val low: Double,
    val precipitationProbability: Int,
    val code: Int,
)

class WeatherException(message: String, cause: Throwable? = null) : IOException(message, cause)

class LocationUnavailableException(
    message: String,
    val locationDisabled: Boolean = false,
) : IOException(message)

class WeatherRepository(
    context: Context,
    private val language: () -> AppLanguage = { AppLanguage.Polish },
) {
    private val appContext = context.applicationContext
    private val locationManager = appContext.getSystemService(LocationManager::class.java)

    suspend fun currentPlace(): Place = resolvePlace(currentLocation(forLocality = true))

    @SuppressLint("MissingPermission")
    suspend fun currentLocation(maxCachedAgeMillis: Long = 30_000, forLocality: Boolean = false): Location {
        val currentLanguage = language()
        val precise = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        if (!precise && !hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            throw SecurityException(currentLanguage.text(
                "GlobeLog potrzebuje dostępu do lokalizacji.",
                "GlobeLog needs access to your location.",
                "GlobeLog benötigt Zugriff auf deinen Standort.",
                "GlobeLog necesita acceso a tu ubicación.",
            ))
        }

        val manager = locationManager ?: throw LocationUnavailableException(
            currentLanguage.text(
                "To urządzenie nie udostępnia lokalizacji.",
                "This device cannot provide its location.",
                "Dieses Gerät kann keinen Standort bereitstellen.",
                "Este dispositivo no puede proporcionar su ubicación.",
            ),
        )
        if (!LocationManagerCompat.isLocationEnabled(manager)) {
            throw LocationUnavailableException(
                currentLanguage.text(
                    "Włącz lokalizację w ustawieniach telefonu.",
                    "Turn on location in your phone settings.",
                    "Aktiviere den Standort in den Geräteeinstellungen.",
                    "Activa la ubicación en los ajustes del teléfono.",
                ),
                locationDisabled = true,
            )
        }

        val providers = manager.relevantProviders(precise, enabledOnly = true)
        val preferredAccuracy = if (precise) 75f else 5_000f
        val fixAge = if (forLocality) minOf(maxCachedAgeMillis, 12_000L) else maxCachedAgeMillis
        val cacheAge = if (forLocality) minOf(fixAge, 5_000L) else fixAge
        val recent = (providers + LocationManager.PASSIVE_PROVIDER).mapNotNull(manager::cachedFix)
            .bestRecentFix(cacheAge)
        if (recent != null && recent.accuracy <= preferredAccuracy) return recent

        val waitMillis = if (forLocality && precise) 16_000L else 8_000L
        return fetchLocation(manager, providers, recent, preferredAccuracy, fixAge, waitMillis)
            ?: throw LocationUnavailableException(
                currentLanguage.text(
                    "Nie udało się ustalić lokalizacji. Spróbuj ponownie lub wyszukaj miejscowość.",
                    "Could not determine your location. Try again or search for a place.",
                    "Dein Standort konnte nicht ermittelt werden. Versuche es erneut oder suche nach einem Ort.",
                    "No se pudo determinar tu ubicación. Inténtalo de nuevo o busca una localidad.",
                ),
            )
    }

    suspend fun resolvePlace(location: Location): Place {
        val latitude = (location.latitude * 10_000).roundToInt() / 10_000.0
        val longitude = (location.longitude * 10_000).roundToInt() / 10_000.0
        val geography = if (location.isRecentFix(30_000, LOCALITY_ACCURACY_METERS)) {
            resolveGeography(latitude, longitude)
        } else {
            null
        }
        val name = geography?.city ?: DEVICE_FALLBACK_NAME
        return Place(
            id = DEVICE_PLACE_ID,
            name = name,
            latitude = latitude,
            longitude = longitude,
            region = region(name, geography?.subdivision, geography?.countryName),
            countryName = geography?.countryName.orEmpty(),
            countryCode = geography?.countryCode.orEmpty(),
            localityId = geography?.localityId.orEmpty(),
            subdivisionCode = geography?.subdivisionCode.orEmpty(),
            localityAliases = geography?.children.orEmpty(),
        )
    }

    suspend fun enrichPlaceCountry(place: Place): Place {
        if (place.countryName.isNotBlank() && place.countryCode.isNotBlank()) return place
        val geography = resolveGeography(place.latitude, place.longitude, checkSmallLocality = false) ?: return place
        return place.copy(
            countryName = place.countryName.ifBlank { geography.countryName.orEmpty() },
            countryCode = place.countryCode.ifBlank { geography.countryCode.orEmpty() },
            localityId = place.localityId.ifBlank {
                geography.localityId.orEmpty().takeIf { geography.city.equals(place.name, ignoreCase = true) }.orEmpty()
            },
            subdivisionCode = place.subdivisionCode.ifBlank { geography.subdivisionCode.orEmpty() },
        )
    }

    suspend fun searchPlaces(query: String): List<Place> {
        val name = query.trim()
        if (name.length < 2) return emptyList()

        val currentLanguage = language()
        val encoded = URLEncoder.encode(name, "UTF-8")
        val matches = findPlaces(encoded, currentLanguage, currentLanguage)
        if (currentLanguage == AppLanguage.Polish || matches.any { it.isSettlement }) {
            return matches.map { it.place }
        }

        // The service matches localized names. Some Polish names need a Polish query.
        val polishMatches = try {
            findPlaces(encoded, AppLanguage.Polish, currentLanguage)
        } catch (_: WeatherException) {
            emptyList()
        }
        return (polishMatches + matches).distinctBy { it.place.id }.take(12).map { it.place }
    }

    private suspend fun findPlaces(
        encodedName: String,
        searchLanguage: AppLanguage,
        currentLanguage: AppLanguage,
    ): List<PlaceMatch> {
        val searchUrl = "https://geocoding-api.open-meteo.com/v1/search" +
            "?name=$encodedName&count=12&language=${searchLanguage.code}&format=json"
        val searchError = currentLanguage.text(
            "Nie udało się wyszukać miejscowości.",
            "Could not search for places.",
            "Die Ortssuche ist fehlgeschlagen.",
            "No se pudieron buscar localidades.",
        )
        return weatherRequest(searchError, searchUrl, currentLanguage) { response ->
            val entries = response.optJSONArray("results") ?: return@weatherRequest emptyList()
            buildList {
                for (index in 0 until entries.length()) {
                    val entry = entries.optJSONObject(index) ?: continue
                    val id = entry.text("id") ?: continue
                    val label = entry.text("name") ?: continue
                    val latitude = entry.optDouble("latitude")
                    val longitude = entry.optDouble("longitude")
                    if (!latitude.isFinite() || !longitude.isFinite()) continue

                    val countryCode = countryCode(entry.text("country_code"))
                    val countryName = countryName(countryCode, currentLanguage) ?: entry.text("country")
                    val isSettlement = entry.text("feature_code")?.startsWith("PPL") == true
                    val place = Place(
                        id = id,
                        name = label,
                        latitude = latitude,
                        longitude = longitude,
                        region = region(label, entry.text("admin1"), countryName),
                        countryName = countryName.orEmpty(),
                        countryCode = countryCode.orEmpty(),
                        localityId = id.takeIf { isSettlement }.orEmpty(),
                    )
                    add(PlaceMatch(place, isSettlement))
                }
            }
        }
    }

    suspend fun forecast(place: Place): Forecast {
        val currentLanguage = language()
        val forecastUrl = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=${place.latitude}&longitude=${place.longitude}" +
            "&current=temperature_2m,apparent_temperature,relative_humidity_2m,wind_speed_10m,weather_code,is_day" +
            "&hourly=temperature_2m,precipitation_probability,weather_code,is_day" +
            "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max,weather_code" +
            "&timezone=auto&timeformat=unixtime&forecast_days=7"
        val forecastError = currentLanguage.text(
            "Nie udało się pobrać prognozy pogody.",
            "Could not load the weather forecast.",
            "Die Wettervorhersage konnte nicht geladen werden.",
            "No se pudo cargar la previsión meteorológica.",
        )
        return weatherRequest(forecastError, forecastUrl, currentLanguage) { response ->
            parseForecast(place, response)
        }
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED

    private suspend fun fetchLocation(
        manager: LocationManager,
        providers: List<String>,
        recent: Location?,
        preferredAccuracy: Float,
        maxFixAgeMillis: Long,
        waitMillis: Long,
    ): Location? {
        val observed = listOfNotNull(recent).toMutableList()
        val target = withTimeoutOrNull(waitMillis) {
            val updates = Channel<Location>(Channel.BUFFERED)
            val listeners = providers.mapNotNull { provider ->
                val listener = object : LocationListenerCompat {
                    override fun onLocationChanged(location: Location) { updates.trySend(Location(location)) }
                }
                val request = LocationRequestCompat.Builder(1_000)
                    .setMinUpdateIntervalMillis(500)
                    .setDurationMillis(waitMillis)
                    .setQuality(
                        if (provider == LocationManager.NETWORK_PROVIDER) LocationRequestCompat.QUALITY_BALANCED_POWER_ACCURACY
                        else LocationRequestCompat.QUALITY_HIGH_ACCURACY,
                    ).build()
                listener.takeIf { manager.subscribeForFixes(provider, request, it) }
            }
            try {
                if (listeners.isEmpty()) return@withTimeoutOrNull null
                for (fix in updates) {
                    if (!fix.isRecentFix(maxFixAgeMillis)) continue
                    observed.add(fix)
                    if (fix.accuracy <= preferredAccuracy) return@withTimeoutOrNull fix
                }
                null
            } finally {
                updates.cancel()
                listeners.forEach(manager::unsubscribeFromFixes)
            }
        }
        return target ?: observed.bestRecentFix(maxFixAgeMillis)
    }

    private fun Iterable<Location>.bestRecentFix(maxAgeMillis: Long): Location? = asSequence()
        .filter { it.isRecentFix(maxAgeMillis) }
        .minWithOrNull(compareBy<Location> { it.accuracy }.thenByDescending { it.elapsedRealtimeNanos })

    private suspend fun resolveGeography(
        latitude: Double,
        longitude: Double,
        checkSmallLocality: Boolean = true,
    ): Geography? = coroutineScope {
        val currentLanguage = language()
        val onlineRequest = async { onlineGeography(latitude, longitude, currentLanguage) }
        val platform = platformGeography(latitude, longitude, currentLanguage)
        val online = onlineRequest.await()
        val needsLocalDetail = online?.adminLevel?.let { it >= 7 } ?: (platform?.city == null)
        if (checkSmallLocality && needsLocalDetail) {
            val detail = detailedGeography(latitude, longitude, currentLanguage)
            val knownCountry = platform?.countryCode ?: online?.countryCode
            if (detail != null && (knownCountry == null || knownCountry == detail.countryCode) &&
                !detail.city.equals(online?.city, ignoreCase = true)
            ) {
                return@coroutineScope detail.copy(subdivisionCode = online?.subdivisionCode)
            }
        }
        if (online == null) return@coroutineScope platform
        if (platform == null) return@coroutineScope online
        if (platform.countryCode != null && online.countryCode != null && platform.countryCode != online.countryCode) {
            return@coroutineScope platform
        }
        val onlineCityMatches = platform.city == null || online.city.equals(platform.city, ignoreCase = true) ||
            online.children.any { it.equals(platform.city, ignoreCase = true) }
        platform.copy(
            city = if (onlineCityMatches) online.city ?: platform.city else platform.city,
            subdivision = platform.subdivision ?: online.subdivision,
            countryName = platform.countryName ?: online.countryName,
            countryCode = platform.countryCode ?: online.countryCode,
            localityId = online.localityId.takeIf { onlineCityMatches },
            subdivisionCode = online.subdivisionCode,
            children = if (onlineCityMatches) online.children else emptyList(),
        )
    }

    private suspend fun platformGeography(latitude: Double, longitude: Double, language: AppLanguage): Geography? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(appContext, language.locale)
        val address = try {
            withTimeoutOrNull(2_000) {
                if (Build.VERSION.SDK_INT >= 33) {
                    suspendCancellableCoroutine { continuation ->
                        val completed = AtomicBoolean(false)
                        geocoder.getFromLocation(latitude, longitude, 1, object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) {
                                if (completed.compareAndSet(false, true)) continuation.resume(addresses.firstOrNull())
                            }

                            override fun onError(errorMessage: String?) {
                                if (completed.compareAndSet(false, true)) continuation.resume(null)
                            }
                        })
                    }
                } else {
                    @Suppress("DEPRECATION")
                    runInterruptible(Dispatchers.IO) {
                        geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull()
                    }
                }
            }
        } catch (_: IOException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: IllegalStateException) {
            null
        } catch (_: SecurityException) {
            null
        } ?: return null

        val code = countryCode(address.countryCode)
        return Geography(
            city = address.locality.nonBlank(),
            subdivision = address.adminArea.nonBlank(),
            countryName = countryName(code, language) ?: address.countryName.nonBlank(),
            countryCode = code,
        )
    }

    private suspend fun onlineGeography(latitude: Double, longitude: Double, language: AppLanguage): Geography? =
        withContext(Dispatchers.IO) {
            try {
                val url = "https://api.bigdatacloud.net/data/reverse-geocode-client" +
                    "?latitude=$latitude&longitude=$longitude&localityLanguage=${language.code}"
                val response = requestJson(url, timeoutMillis = 1_500)
                val code = countryCode(response.text("countryCode"))
                val city = response.text("city") ?: response.text("locality")
                val administrative = response.optJSONObject("localityInfo")?.optJSONArray("administrative")
                val areas = (0 until (administrative?.length() ?: 0)).mapNotNull { administrative?.optJSONObject(it) }
                val cityArea = areas.filter { it.text("name").equals(city, ignoreCase = true) && it.optInt("adminLevel") >= 4 }
                    .maxByOrNull { it.optInt("adminLevel") }
                val children = if (cityArea == null) emptyList() else areas.dropWhile { it !== cityArea }
                    .drop(1).mapNotNull { it.text("name") }
                Geography(
                    city = city,
                    subdivision = response.text("principalSubdivision"),
                    countryName = response.text("countryName") ?: countryName(code, language),
                    countryCode = code,
                    localityId = cityArea?.optLong("geonameId")?.takeIf { it > 0L }?.toString(),
                    subdivisionCode = response.text("principalSubdivisionCode"),
                    children = children,
                    adminLevel = cityArea?.optInt("adminLevel"),
                )
            } catch (_: IOException) {
                null
            } catch (_: JSONException) {
                null
            }
        }

    private suspend fun detailedGeography(latitude: Double, longitude: Double, language: AppLanguage): Geography? =
        withContext(Dispatchers.IO) {
            try {
                val url = "https://photon.komoot.io/reverse?lat=$latitude&lon=$longitude&limit=1"
                val feature = requestJson(url, timeoutMillis = 1_200).optJSONArray("features")?.optJSONObject(0)
                    ?: return@withContext null
                val properties = feature.optJSONObject("properties") ?: return@withContext null
                val coordinates = feature.optJSONObject("geometry")?.optJSONArray("coordinates")
                    ?: return@withContext null
                val featureLatitude = coordinates.optDouble(1)
                val featureLongitude = coordinates.optDouble(0)
                if (featureLatitude !in -90.0..90.0 || featureLongitude !in -180.0..180.0) return@withContext null
                val distance = FloatArray(1)
                Location.distanceBetween(latitude, longitude, featureLatitude, featureLongitude, distance)
                if (distance[0] > 250f) return@withContext null

                val city = properties.text("city") ?: return@withContext null
                val code = countryCode(properties.text("countrycode"))
                Geography(
                    city = city,
                    subdivision = properties.text("state"),
                    countryName = countryName(code, language) ?: properties.text("country"),
                    countryCode = code,
                    children = listOfNotNull(properties.text("district"), properties.text("locality")),
                )
            } catch (_: IOException) {
                null
            } catch (_: JSONException) {
                null
            }
        }

    private suspend fun <T> weatherRequest(
        message: String,
        url: String,
        language: AppLanguage,
        parse: (JSONObject) -> T,
    ): T =
        withContext(Dispatchers.IO) {
            try {
                parse(requestJson(url, timeoutMillis = 8_000))
            } catch (error: IOException) {
                val details = language.text(
                    "Sprawdź połączenie z internetem i spróbuj ponownie.",
                    "Check your internet connection and try again.",
                    "Überprüfe die Internetverbindung und versuche es erneut.",
                    "Comprueba tu conexión a Internet e inténtalo de nuevo.",
                )
                throw WeatherException("$message $details", error)
            } catch (error: JSONException) {
                val details = language.text(
                    "Serwis zwrócił niepełne dane.",
                    "The service returned incomplete data.",
                    "Der Dienst hat unvollständige Daten geliefert.",
                    "El servicio devolvió datos incompletos.",
                )
                throw WeatherException("$message $details", error)
            }
        }

    private fun requestJson(url: String, timeoutMillis: Int): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = timeoutMillis
        connection.readTimeout = timeoutMillis
        connection.setRequestProperty("Accept", "application/json")
        return try {
            if (connection.responseCode !in 200..299) {
                throw IOException("HTTP ${connection.responseCode}")
            }
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()) }
        } finally {
            connection.disconnect()
        }
    }

    private fun parseForecast(place: Place, response: JSONObject): Forecast {
        val timeZone = TimeZone.getTimeZone(response.getString("timezone"))
        val hourFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.ROOT).apply {
            this.timeZone = timeZone
        }
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply {
            // Forecast days use the server's fixed local offset even across daylight saving changes.
            this.timeZone = SimpleTimeZone(response.getInt("utc_offset_seconds") * 1000, "Forecast")
        }
        val current = response.getJSONObject("current")
        val currentTime = current.getLong("time")
        val hourly = response.getJSONObject("hourly")
        val daily = response.getJSONObject("daily")

        return Forecast(
            place = place,
            current = CurrentWeather(
                temperature = current.getDouble("temperature_2m"),
                feelsLike = current.getDouble("apparent_temperature"),
                humidity = current.getInt("relative_humidity_2m"),
                windSpeed = current.getDouble("wind_speed_10m"),
                code = current.getInt("weather_code"),
                isDay = current.getInt("is_day") == 1,
            ),
            hourly = parseHourly(hourly, hourFormat, currentTime),
            daily = parseDaily(daily, dateFormat),
        )
    }

    private fun parseHourly(hourly: JSONObject, format: SimpleDateFormat, currentTime: Long): List<HourlyWeather> {
        val times = hourly.getJSONArray("time")
        val temperatures = hourly.getJSONArray("temperature_2m")
        val precipitation = hourly.getJSONArray("precipitation_probability")
        val codes = hourly.getJSONArray("weather_code")
        val daylight = hourly.getJSONArray("is_day")

        return (0 until times.length()).asSequence()
            .filter { times.getLong(it) > currentTime - 3_600 }
            .take(24)
            .filterNot { temperatures.isNull(it) || precipitation.isNull(it) || codes.isNull(it) || daylight.isNull(it) }
            .map { index ->
                HourlyWeather(
                    time = format.format(Date(times.getLong(index) * 1000)),
                    temperature = temperatures.getDouble(index),
                    precipitationProbability = precipitation.getInt(index),
                    code = codes.getInt(index),
                    isDay = daylight.getInt(index) == 1,
                )
            }.toList()
    }

    private fun parseDaily(daily: JSONObject, format: SimpleDateFormat): List<DailyWeather> {
        val dates = daily.getJSONArray("time")
        val highs = daily.getJSONArray("temperature_2m_max")
        val lows = daily.getJSONArray("temperature_2m_min")
        val precipitation = daily.getJSONArray("precipitation_probability_max")
        val codes = daily.getJSONArray("weather_code")

        return (0 until dates.length()).mapNotNull { index ->
            if (highs.isNull(index) || lows.isNull(index) || precipitation.isNull(index) || codes.isNull(index)) {
                return@mapNotNull null
            }
            DailyWeather(
                date = format.format(Date(dates.getLong(index) * 1000)),
                high = highs.getDouble(index),
                low = lows.getDouble(index),
                precipitationProbability = precipitation.getInt(index),
                code = codes.getInt(index),
            )
        }
    }

    private fun JSONObject.text(key: String): String? =
        if (isNull(key)) null else optString(key).nonBlank()

    private fun String?.nonBlank(): String? = this?.trim()?.takeIf(String::isNotEmpty)

    private fun countryCode(value: String?): String? = value.nonBlank()?.uppercase(Locale.ROOT)
        ?.takeIf { code -> code.length == 2 && code.all { it in 'A'..'Z' } }

    private fun countryName(code: String?, language: AppLanguage): String? = code?.let {
        Locale.Builder().setRegion(it).build().getDisplayCountry(language.locale)
            .takeIf { name -> name.isNotBlank() && name != it }
    }

    private fun region(name: String, vararg parts: String?): String = parts.asSequence()
        .filterNotNull()
        .filter { it.isNotBlank() && !it.equals(name, ignoreCase = true) }
        .distinct()
        .joinToString(", ")

    private data class PlaceMatch(val place: Place, val isSettlement: Boolean)

    private data class Geography(
        val city: String?,
        val subdivision: String?,
        val countryName: String?,
        val countryCode: String?,
        val localityId: String? = null,
        val subdivisionCode: String? = null,
        val children: List<String> = emptyList(),
        val adminLevel: Int? = null,
    )
}

fun weatherDescription(code: Int, language: AppLanguage = AppLanguage.Polish): String = when (code) {
    0 -> language.text("Bezchmurnie", "Clear skies", "Klarer Himmel", "Cielo despejado")
    1 -> language.text("Przeważnie pogodnie", "Mostly clear", "Überwiegend klar", "Mayormente despejado")
    2 -> language.text("Częściowe zachmurzenie", "Partly cloudy", "Teilweise bewölkt", "Parcialmente nublado")
    3 -> language.text("Pochmurno", "Overcast", "Bedeckt", "Cubierto")
    45 -> language.text("Mgła", "Fog", "Nebel", "Niebla")
    48 -> language.text("Mgła osadzająca szadź", "Freezing fog", "Gefrierender Nebel", "Niebla helada")
    51 -> language.text("Lekka mżawka", "Light drizzle", "Leichter Nieselregen", "Llovizna ligera")
    53 -> language.text("Mżawka", "Drizzle", "Nieselregen", "Llovizna")
    55 -> language.text("Intensywna mżawka", "Heavy drizzle", "Starker Nieselregen", "Llovizna intensa")
    56, 57 -> language.text("Marznąca mżawka", "Freezing drizzle", "Gefrierender Nieselregen", "Llovizna helada")
    61 -> language.text("Lekki deszcz", "Light rain", "Leichter Regen", "Lluvia ligera")
    63 -> language.text("Deszcz", "Rain", "Regen", "Lluvia")
    65 -> language.text("Ulewny deszcz", "Heavy rain", "Starker Regen", "Lluvia intensa")
    66, 67 -> language.text("Marznący deszcz", "Freezing rain", "Gefrierender Regen", "Lluvia helada")
    71 -> language.text("Lekkie opady śniegu", "Light snow", "Leichter Schneefall", "Nevada ligera")
    73 -> language.text("Opady śniegu", "Snow", "Schnee", "Nieve")
    75 -> language.text("Intensywne opady śniegu", "Heavy snow", "Starker Schneefall", "Nevada intensa")
    77 -> language.text("Śnieg ziarnisty", "Snow grains", "Schneegriesel", "Nieve granulada")
    80, 81 -> language.text("Przelotny deszcz", "Rain showers", "Regenschauer", "Chubascos")
    82 -> language.text("Przelotna ulewa", "Heavy rain showers", "Starke Regenschauer", "Chubascos intensos")
    85, 86 -> language.text("Przelotne opady śniegu", "Snow showers", "Schneeschauer", "Chubascos de nieve")
    95 -> language.text("Burza", "Thunderstorm", "Gewitter", "Tormenta")
    96, 99 -> language.text("Burza z gradem", "Thunderstorm with hail", "Gewitter mit Hagel", "Tormenta con granizo")
    else -> language.text("Nieznane warunki", "Weather unavailable", "Keine Wetterdaten verfügbar", "Tiempo no disponible")
}
