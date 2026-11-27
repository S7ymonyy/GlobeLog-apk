package pl.getdatrip.ui

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.util.AtomicFile
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale
import kotlin.math.round
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import pl.getdatrip.AppLanguage
import pl.getdatrip.BuildConfig
import pl.getdatrip.data.Place

internal data class LocalityRing(val points: List<GeoPoint>, val hole: Boolean) {
    fun contains(point: GeoPoint): Boolean {
        var inside = false
        val anchor = points.first().longitude
        val pointX = longitudeDelta(point.longitude, anchor)
        var previous = points.last()
        for (current in points) {
            if ((current.latitude > point.latitude) != (previous.latitude > point.latitude)) {
                val x = longitudeDelta(current.longitude, anchor)
                val previousX = longitudeDelta(previous.longitude, anchor)
                val crossing = (previousX - x) * (point.latitude - current.latitude) /
                    (previous.latitude - current.latitude) + x
                if (crossing > pointX) inside = !inside
            }
            previous = current
        }
        return inside
    }
}

internal data class LocalityBoundary(val rings: List<LocalityRing>) {
    fun contains(point: GeoPoint): Boolean = rings.sumOf { ring ->
        when {
            !ring.contains(point) -> 0L
            ring.hole -> -1L
            else -> 1L
        }
    } > 0
}

internal fun longitudeDelta(value: Double, reference: Double): Double =
    (value - reference + 540.0) % 360.0 - 180.0

internal class MapBoundaryStore(context: Context) {
    private val directory = File(context.cacheDir, "locality-boundaries-v1")
    private val userAgent = "GlobeLog/${BuildConfig.VERSION_NAME} (${context.packageName})"

    suspend fun cached(place: Place): LocalityBoundary? = withContext(Dispatchers.IO) {
        readCached(cacheFile(place))
    }

    suspend fun load(place: Place, language: AppLanguage): LocalityBoundary? = withContext(Dispatchers.IO) {
        val file = cacheFile(place)
        readCached(file)?.let { return@withContext it }
        if (file.isFile && file.length() == 0L && System.currentTimeMillis() - file.lastModified() < missingCacheMillis) {
            return@withContext null
        }

        val candidates = findCandidates(place, language)
        val point = GeoPoint(place.latitude, place.longitude)
        for (id in candidates.take(2)) {
            val endpoint = "https://polygons.openstreetmap.fr/get_geojson.py?id=$id&params=0"
            val body = get(endpoint) ?: continue
            val outline = decodeLocalityBoundary(body) ?: continue
            if (!outline.contains(point)) continue
            writeCache(file, body)
            return@withContext outline
        }
        writeCache(file, "")
        null
    }

    private suspend fun findCandidates(place: Place, language: AppLanguage): List<Long> {
        val endpoint = Uri.parse("https://photon.komoot.io/api").buildUpon()
            .appendQueryParameter("q", place.name)
            .appendQueryParameter("limit", "9")
            // Use a coarse search bias. The saved coordinates are checked locally.
            .appendQueryParameter("lat", (round(place.latitude * 4) / 4).toString())
            .appendQueryParameter("lon", (round(place.longitude * 4) / 4).toString())
            .apply { if (language.code == "en" || language.code == "de") appendQueryParameter("lang", language.code) }
            .build().toString()
        val response = get(endpoint) ?: throw IOException("The locality lookup is unavailable")
        val features = JSONObject(response).getJSONArray("features")
        val expected = normalizeName(place.name)
        val country = place.countryCode.uppercase(Locale.ROOT)
        val center = GeoPoint(place.latitude, place.longitude)
        return buildList {
            for (index in 0 until features.length()) {
                val feature = features.optJSONObject(index) ?: continue
                val props = feature.optJSONObject("properties") ?: continue
                if (props.optString("osm_type") != "R") continue
                val value = props.optString("osm_value")
                val mappedLocality = props.optString("osm_key") == "place" && value in cityTypes
                val administrative = props.optString("osm_key") == "boundary" && value == "administrative"
                if (!mappedLocality && !administrative) continue
                val id = props.optLong("osm_id")
                if (id <= 0 || (country.isNotBlank() && props.optString("countrycode") != country)) continue
                val level = props.optJSONObject("extra")?.optInt("admin_level") ?: 0
                val type = props.optString("type")
                if (!mappedLocality && level !in 6..12 && !(level in 4..5 && type in cityTypes)) continue

                val extent = props.optJSONArray("extent") ?: continue
                val north = extent.optDouble(1)
                val south = extent.optDouble(3)
                val west = extent.optDouble(0)
                val east = extent.optDouble(2)
                val insideLongitude = if (west <= east) center.longitude in west..east else {
                    center.longitude >= west || center.longitude <= east
                }
                if (center.latitude !in south..north || !insideLongitude) continue

                val coordinates = feature.optJSONObject("geometry")?.optJSONArray("coordinates") ?: continue
                val latitude = coordinates.optDouble(1)
                val longitude = coordinates.optDouble(0)
                if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) continue
                val distance = center.distanceToAsDouble(GeoPoint(latitude, longitude))
                val exactName = normalizeName(props.optString("name")) == expected
                if (!exactName && !(type in cityTypes && distance <= 2_000)) continue
                add(BoundaryCandidate(id, exactName, mappedLocality, level, distance))
            }
        }.sortedWith(
            compareByDescending<BoundaryCandidate> { it.exactName }
                .thenByDescending { it.mappedLocality }.thenByDescending { it.level }.thenBy { it.distance },
        ).map { it.id }.distinct()
    }

    private fun cacheFile(place: Place): File {
        val key = "${place.id}\u001f${place.name}\u001f${place.latitude}\u001f${place.longitude}"
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
        return File(directory, digest.joinToString("") { "%02x".format(Locale.ROOT, it) })
    }

    private fun readCached(file: File): LocalityBoundary? {
        if (!file.isFile || file.length() !in 1L..maxResponseBytes.toLong()) return null
        return runCatching { decodeLocalityBoundary(AtomicFile(file).readFully().toString(Charsets.UTF_8)) }.getOrNull()
    }

    private fun writeCache(file: File, value: String) {
        directory.mkdirs()
        val atomic = AtomicFile(file)
        val stream = try { atomic.startWrite() } catch (_: IOException) { return }
        try {
            stream.write(value.toByteArray(Charsets.UTF_8))
            atomic.finishWrite(stream)
        } catch (_: IOException) {
            atomic.failWrite(stream)
        }
    }

    private suspend fun get(endpoint: String): String? = requestGate.withLock {
        delay((requestSpacingMillis - (SystemClock.elapsedRealtime() - lastRequestAt)).coerceAtLeast(0L))
        lastRequestAt = SystemClock.elapsedRealtime()
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 5_000
            connection.readTimeout = 8_000
            connection.setRequestProperty("User-Agent", userAgent)
            connection.setRequestProperty("Accept", "application/json")
            when (val status = connection.responseCode) {
                404 -> null
                in 200..299 -> connection.inputStream.use { input ->
                    val data = ByteArrayOutputStream()
                    val buffer = ByteArray(8_192)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (data.size() + read > maxResponseBytes) throw IOException("Boundary data is too large")
                        data.write(buffer, 0, read)
                    }
                    data.toString(Charsets.UTF_8.name())
                }
                else -> throw IOException("Boundary service returned HTTP $status")
            }
        } finally {
            connection.disconnect()
        }
    }

    private data class BoundaryCandidate(
        val id: Long,
        val exactName: Boolean,
        val mappedLocality: Boolean,
        val level: Int,
        val distance: Double,
    )

    private companion object {
        const val requestSpacingMillis = 1_400L
        const val missingCacheMillis = 7L * 24 * 60 * 60 * 1000
        const val maxResponseBytes = 2_000_000
        val cityTypes = setOf("city", "town", "village", "hamlet", "municipality", "locality")
        val requestGate = Mutex()
        var lastRequestAt = 0L
    }
}

private fun normalizeName(name: String): String = Normalizer.normalize(name.trim(), Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "")
    .lowercase(Locale.ROOT)
    .replace('ł', 'l')

internal fun decodeLocalityBoundary(json: String): LocalityBoundary? {
    val geoJson = runCatching { JSONObject(json) }.getOrNull() ?: return null
    val coordinates = geoJson.optJSONArray("coordinates") ?: return null
    val polygons = when (geoJson.optString("type")) {
        "Polygon" -> listOf(coordinates)
        "MultiPolygon" -> (0 until coordinates.length()).mapNotNull(coordinates::optJSONArray)
        else -> return null
    }
    val rings = buildList {
        for (polygon in polygons) {
            for (index in 0 until polygon.length()) {
                val line = polygon.optJSONArray(index) ?: continue
                val points = buildList {
                    for (vertex in 0 until line.length()) {
                        val pair = line.optJSONArray(vertex) ?: continue
                        val latitude = pair.optDouble(1)
                        val longitude = pair.optDouble(0)
                        if (latitude in -90.0..90.0 && longitude in -180.0..180.0) add(GeoPoint(latitude, longitude))
                    }
                }
                if (points.size < 4) continue
                val anchor = points.first().longitude
                var prior = points.last()
                var signedArea = 0.0
                for (point in points) {
                    signedArea += longitudeDelta(prior.longitude, anchor) * point.latitude -
                        longitudeDelta(point.longitude, anchor) * prior.latitude
                    prior = point
                }
                val hole = index > 0
                val clockwise = signedArea < 0
                add(LocalityRing(if (clockwise == hole) points.asReversed() else points, hole))
            }
        }
    }
    return if (rings.any { !it.hole }) LocalityBoundary(rings) else null
}
