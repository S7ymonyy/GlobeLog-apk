package pl.getdatrip.ui

import android.app.ActivityManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.SatelliteAlt
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import java.io.IOException
import kotlin.math.log2
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONException
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.TileSystem
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import pl.getdatrip.AppLanguage
import pl.getdatrip.BuildConfig
import pl.getdatrip.ScratchColor
import pl.getdatrip.data.DEVICE_FALLBACK_NAME
import pl.getdatrip.data.Place
import pl.getdatrip.ui.theme.GetDaTripDesign

data class MapTrackedPerson(
    val id: String,
    val displayName: String,
    val latitude: Double,
    val longitude: Double,
    val isSelf: Boolean = false,
)

@Composable
fun UnlockedMapScreen(
    places: List<Place>,
    onPlace: (Place) -> Unit,
    modifier: Modifier = Modifier,
    navigationClearance: Dp = 0.dp,
    scratchColor: ScratchColor = ScratchColor.entries.first(),
    darkMap: Boolean = false,
    satellite: Boolean = false,
    onSatelliteChanged: (Boolean) -> Unit = {},
    trackedPeople: List<MapTrackedPerson> = emptyList(),
    onRequestLocation: (() -> Unit)? = null,
) {
    val colors = GetDaTripDesign.colors
    val language = LocalAppLanguage.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val uriHandler = LocalUriHandler.current
    val camera = rememberSaveable(saver = mapCameraSaver) { MapCamera() }
    var mapView by remember { mutableStateOf<UnlockedMapView?>(null) }
    var approximateBoundaries by remember { mutableStateOf(places.isNotEmpty()) }
    val markerColor = MaterialTheme.colorScheme.primaryContainer.toArgb()
    val pinColor = colors.accent.toArgb()
    val buttonColors = IconButtonDefaults.filledTonalIconButtonColors(
        containerColor = colors.canvas,
        contentColor = colors.accent,
    )

    Box(modifier.fillMaxSize()) {
        AndroidView(
            factory = { context -> UnlockedMapView(context, camera, satellite).also { mapView = it } },
            modifier = Modifier.fillMaxSize(),
            onReset = null,
            onRelease = { it.release() },
            update = { view ->
                view.bindLifecycle(lifecycle)
                view.onPlace = onPlace
                view.onApproximation = { approximateBoundaries = it }
                view.contentDescription = language.text(
                    "Mapa zdrapka odblokowanych miejsc",
                    "Scratch map of unlocked places",
                    "Rubbelkarte der freigeschalteten Orte",
                    "Mapa para rascar de localidades desbloqueadas",
                )
                view.showMapStyle(darkMap, satellite)
                view.showScratchColor(scratchColor.color.toArgb())
                view.showPlaces(places, markerColor, pinColor, language)
                view.showTrackedPeople(trackedPeople)
            },
        )

        val mapStatus = when {
            places.isEmpty() -> tr(
                "Brak odblokowanych miejsc",
                "No unlocked places",
                "Keine freigeschalteten Orte",
                "No hay localidades desbloqueadas",
            )
            approximateBoundaries -> tr(
                "Niektóre granice są przybliżone",
                "Some boundaries are approximate",
                "Einige Grenzen sind angenähert",
                "Algunos límites son aproximados",
            )
            else -> null
        }
        Row(
            modifier = Modifier.align(Alignment.TopStart).fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.weight(1f)) {
                if (mapStatus != null) {
                    Surface(shape = RoundedCornerShape(12.dp), color = colors.canvas, contentColor = colors.text) {
                        Text(
                            text = mapStatus,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
            FilledTonalButton(
                onClick = { onSatelliteChanged(!satellite) },
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = colors.canvas,
                    contentColor = colors.accent,
                ),
            ) {
                Icon(
                    if (satellite) Icons.Rounded.Map else Icons.Rounded.SatelliteAlt,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (satellite) tr("Mapa", "Map", "Karte", "Mapa")
                    else tr("Satelita", "Satellite", "Satellit", "Satélite"),
                )
            }
        }

        Column(
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 16.dp + navigationClearance),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (onRequestLocation != null || trackedPeople.any { it.isSelf }) {
                FilledTonalIconButton(
                    onClick = { if (mapView?.centerOnSelf(whenAvailable = true) != true) onRequestLocation?.invoke() },
                    colors = buttonColors,
                ) {
                    Icon(
                        Icons.Rounded.MyLocation,
                        contentDescription = tr("Moja lokalizacja", "My location", "Mein Standort", "Mi ubicación"),
                    )
                }
            }
            FilledTonalIconButton(onClick = { mapView?.controller?.zoomIn() }, colors = buttonColors) {
                Icon(Icons.Rounded.Add, contentDescription = tr("Przybliż mapę", "Zoom in", "Vergrößern", "Acercar"))
            }
            FilledTonalIconButton(onClick = { mapView?.controller?.zoomOut() }, colors = buttonColors) {
                Icon(Icons.Rounded.Remove, contentDescription = tr("Oddal mapę", "Zoom out", "Verkleinern", "Alejar"))
            }
        }

        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 8.dp + navigationClearance),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            if (satellite) {
                MapCredit(SATELLITE_ATTRIBUTION) {
                    runCatching { uriHandler.openUri(SATELLITE_CREDIT_URL) }
                }
            }
            MapCredit("© OpenStreetMap contributors") {
                runCatching { uriHandler.openUri("https://www.openstreetmap.org/copyright") }
            }
        }
    }
}

@Composable
private fun MapCredit(text: String, onClick: () -> Unit) {
    val colors = GetDaTripDesign.colors
    Surface(onClick = onClick, shape = RoundedCornerShape(12.dp), color = colors.canvas, contentColor = colors.text) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall)
    }
}

private class MapCamera(
    var latitude: Double = 0.0,
    var longitude: Double = 0.0,
    var zoom: Double = -1.0,
)

private val mapCameraSaver = listSaver<MapCamera, Double>(
    save = { listOf(it.latitude, it.longitude, it.zoom) },
    restore = { MapCamera(it[0], it[1], it[2]) },
)

private const val LIGHT_WATER = 0xFFAAD3DF.toInt()
private const val DARK_WATER = 0xFF183441.toInt()
private const val SATELLITE_BACKGROUND = 0xFF142431.toInt()
private const val LOCALITY_ZOOM = 11.0
private const val MIN_BOUNDARY_LOOKUP_ZOOM = 9.0

// Invert lightness while preserving color differences between land, water, and road tiles.
private val darkTileFilter = ColorMatrixColorFilter(
    floatArrayOf(
        0.38425f, -0.894f, -0.09025f, 0f, 165f,
        -0.26575f, -0.244f, -0.09025f, 0f, 168f,
        -0.26575f, -0.894f, 0.55975f, 0f, 174f,
        0f, 0f, 0f, 1f, 0f,
    ),
)

private class UnlockedMapView(
    context: Context,
    private val camera: MapCamera,
    initialSatellite: Boolean,
) : MapView(
    configureMap(context),
    createTileProvider(context, initialSatellite),
) {
    var onPlace: (Place) -> Unit = {}
    var onApproximation: (Boolean) -> Unit = {}

    private var mapPlaces = emptyList<Place>()
    private val scratchOverlay = ScratchOverlay()
    private val boundaryStore = MapBoundaryStore(context.applicationContext)
    private val boundaryScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val exactBoundaries = mutableMapOf<String, LocalityBoundary>()
    private val attemptedBoundaries = mutableSetOf<String>()
    private var boundaryCacheRead: Job? = null
    private var boundaryDelay: Job? = null
    private var boundaryLookup: Job? = null
    private var lookupRequested = false
    private val placeMarkers = mutableListOf<Marker>()
    private val trackedMarkers = mutableMapOf<String, Pair<MapTrackedPerson, Marker>>()
    private var markerColors: Pair<Int, Int>? = null
    private var markerLanguage: AppLanguage? = null
    private var boundLifecycle: Lifecycle? = null
    private var resumed = false
    private var darkMap: Boolean? = null
    private var satellite = initialSatellite
    private var roadZoomBeforeSatellite: Double? = null
    private var centerOnNextSelf = false
    private val observer = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_RESUME -> {
                onResume()
                resumed = true
                scheduleVisibleBoundaryLookup()
            }
            Lifecycle.Event.ON_PAUSE -> pauseMap()
            else -> Unit
        }
    }

    init {
        setDestroyMode(false)
        setMultiTouchControls(true)
        setTilesScaledToDpi(true)
        setTilesScaleFactor(0.93f)
        setVerticalMapRepetitionEnabled(false)
        val tileSystem = MapView.getTileSystem()
        setScrollableAreaLimitLatitude(tileSystem.maxLatitude, tileSystem.minLatitude, 0)
        zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        mapOverlay.setLoadingLineColor(android.graphics.Color.TRANSPARENT)
        overlays.add(scratchOverlay)

        addMapListener(object : MapListener {
            override fun onScroll(event: ScrollEvent): Boolean {
                saveCamera()
                scheduleVisibleBoundaryLookup()
                return false
            }

            override fun onZoom(event: ZoomEvent): Boolean {
                saveCamera()
                camera.zoom = event.zoomLevel
                scheduleVisibleBoundaryLookup()
                return false
            }
        })
        addOnFirstLayoutListener { _, _, _, _, _ ->
            frameCamera()
            if (centerOnNextSelf) centerOnSelf(whenAvailable = true)
            scheduleVisibleBoundaryLookup()
        }
    }

    fun bindLifecycle(lifecycle: Lifecycle) {
        if (boundLifecycle === lifecycle) return
        boundLifecycle?.removeObserver(observer)
        pauseMap()
        boundLifecycle = lifecycle
        lifecycle.addObserver(observer)
    }

    fun showMapStyle(dark: Boolean, satellite: Boolean) {
        val sourceChanged = this.satellite != satellite
        if (darkMap == dark && !sourceChanged) return
        darkMap = dark
        if (sourceChanged) {
            val zoom = zoomLevelDouble
            val center = if (isLayoutOccurred) mapCenter else null
            this.satellite = satellite
            if (satellite) {
                roadZoomBeforeSatellite = zoom.takeIf { it > SATELLITE_MAX_ZOOM }
                setTileProvider(createTileProvider(context, satellite = true))
                controller.setZoom(zoom.coerceAtMost(SATELLITE_MAX_ZOOM.toDouble()))
            } else {
                setTileProvider(createTileProvider(context, satellite = false))
                val restoredZoom = if (zoom >= SATELLITE_MAX_ZOOM) roadZoomBeforeSatellite ?: zoom else zoom
                controller.setZoom(restoredZoom)
                roadZoomBeforeSatellite = null
            }
            if (center != null) controller.setCenter(center)
        }
        mapOverlay.setColorFilter(if (dark && !satellite) darkTileFilter else null)
        mapOverlay.setLoadingBackgroundColor(if (satellite) SATELLITE_BACKGROUND else LIGHT_WATER)
        mapOverlay.setLoadingLineColor(android.graphics.Color.TRANSPARENT)
        setBackgroundColor(when {
            satellite -> SATELLITE_BACKGROUND
            dark -> DARK_WATER
            else -> LIGHT_WATER
        })
        invalidate()
    }

    fun showScratchColor(color: Int) {
        if (scratchOverlay.setScratchColor(color)) invalidate()
    }

    fun showPlaces(places: List<Place>, markerColor: Int, pinColor: Int, language: AppLanguage) {
        val palette = markerColor to pinColor
        val localities = places.distinctBy(Place::id).filter {
            it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0
        }
        val placesChanged = localities != mapPlaces
        if (!placesChanged && palette == markerColors && language == markerLanguage) return
        mapPlaces = localities
        markerColors = palette
        markerLanguage = language

        for (marker in placeMarkers) {
            overlays.remove(marker)
            marker.onDetach(this)
        }
        placeMarkers.clear()

        val tileSystem = MapView.getTileSystem()
        val density = resources.displayMetrics.density
        for (place in localities) {
            val marker = Marker(this).apply {
                position = GeoPoint(tileSystem.cleanLatitude(place.latitude), place.longitude)
                title = place.displayName(language)
                icon = MapPin(density, markerColor, pinColor)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                setInfoWindow(null)
                setOnMarkerClickListener { _, _ ->
                    onPlace(place)
                    true
                }
            }
            overlays.add(1 + placeMarkers.size, marker)
            placeMarkers.add(marker)
        }

        if (placesChanged) {
            val ids = localities.mapTo(mutableSetOf(), Place::id)
            exactBoundaries.keys.retainAll(ids)
            attemptedBoundaries.retainAll(ids)
            refreshScratchAreas()
            loadCachedBoundaries(localities)
        }
        invalidate()
    }

    fun showTrackedPeople(people: List<MapTrackedPerson>) {
        val visible = people.filter {
            it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0
        }.associateBy(MapTrackedPerson::id)
        var changed = false
        for (id in trackedMarkers.keys.toList()) {
            if (id in visible) continue
            trackedMarkers.remove(id)?.second?.let { marker ->
                overlays.remove(marker)
                marker.onDetach(this)
            }
            changed = true
        }

        for ((id, person) in visible) {
            val current = trackedMarkers[id]
            if (current?.first == person) continue
            val marker = current?.second ?: Marker(this).also {
                it.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                it.setInfoWindowAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_TOP)
                overlays.add(it)
            }
            if (current == null || current.first.displayName != person.displayName || current.first.isSelf != person.isSelf) {
                marker.title = person.displayName
                marker.icon = LivePersonPin(resources.displayMetrics.density, person)
            }
            val latitude = MapView.getTileSystem().cleanLatitude(person.latitude)
            marker.position = GeoPoint(latitude, person.longitude)
            trackedMarkers[id] = person to marker
            changed = true
        }
        if (changed) invalidate()
        if (centerOnNextSelf && isLayoutOccurred) centerOnSelf(whenAvailable = true)
    }

    fun centerOnSelf(whenAvailable: Boolean): Boolean {
        val position = trackedMarkers.values.firstOrNull { it.first.isSelf }?.second?.position
        if (position == null || !isLayoutOccurred) {
            centerOnNextSelf = whenAvailable
            return false
        }
        centerOnNextSelf = false
        controller.animateTo(position, maxOf(LOCALITY_ZOOM, zoomLevelDouble), 375L)
        return true
    }

    fun release() {
        saveCamera()
        boundLifecycle?.removeObserver(observer)
        boundLifecycle = null
        pauseMap()
        boundaryScope.cancel()
        onDetach()
        placeMarkers.clear()
        trackedMarkers.clear()
        onPlace = {}
        onApproximation = {}
    }

    private fun refreshScratchAreas() {
        val tileSystem = MapView.getTileSystem()
        scratchOverlay.showRevealedAreas(mapPlaces.map { place ->
            val center = GeoPoint(tileSystem.cleanLatitude(place.latitude), place.longitude)
            ScratchLocality(center, exactBoundaries[place.id])
        })
        onApproximation(mapPlaces.any { it.id !in exactBoundaries })
        invalidate()
    }

    private fun loadCachedBoundaries(places: List<Place>) {
        boundaryCacheRead?.cancel()
        boundaryCacheRead = boundaryScope.launch {
            var changed = false
            for (place in places) {
                if (place.id in exactBoundaries) continue
                val outline = boundaryStore.cached(place) ?: continue
                exactBoundaries[place.id] = outline
                changed = true
            }
            if (changed) refreshScratchAreas()
            scheduleVisibleBoundaryLookup()
        }
    }

    private fun scheduleVisibleBoundaryLookup() {
        boundaryDelay?.cancel()
        if (!resumed || !isLayoutOccurred || zoomLevelDouble < MIN_BOUNDARY_LOOKUP_ZOOM) return
        boundaryDelay = boundaryScope.launch {
            delay(500)
            if (boundaryLookup?.isActive == true) {
                lookupRequested = true
                return@launch
            }
            boundaryLookup = boundaryScope.launch { resolveVisibleBoundaries() }
        }
    }

    private suspend fun resolveVisibleBoundaries() {
        val currentLanguage = markerLanguage ?: return
        val activeProjection = projection
        val viewport = activeProjection.intrinsicScreenRect
        val center = mapCenter
        val point = Point()
        val closestPlaces = mapPlaces.asSequence().filter { place ->
            if (place.id in exactBoundaries || place.id in attemptedBoundaries || place.name == DEVICE_FALLBACK_NAME) {
                false
            } else {
                activeProjection.toPixels(GeoPoint(place.latitude, place.longitude), point)
                viewport.contains(point.x, point.y)
            }
        }.sortedBy { place ->
            GeoPoint(place.latitude, place.longitude).distanceToAsDouble(center)
        }.take(2).toList()

        for (place in closestPlaces) {
            val outline = try {
                boundaryStore.load(place, currentLanguage)
            } catch (_: IOException) {
                null
            } catch (_: JSONException) {
                null
            }
            if (mapPlaces.none { it.id == place.id }) continue
            attemptedBoundaries.add(place.id)
            if (outline != null) {
                exactBoundaries[place.id] = outline
                refreshScratchAreas()
            }
        }
        boundaryLookup = null
        if (lookupRequested) {
            lookupRequested = false
            scheduleVisibleBoundaryLookup()
        }
    }

    private fun frameCamera() {
        if (camera.zoom >= 0.0) {
            val center = GeoPoint(camera.latitude, camera.longitude)
            controller.setZoom(camera.zoom)
            controller.setCenter(center)
        } else {
            val points = placeMarkers.map(Marker::getPosition).distinct()
            when (points.size) {
                0 -> {
                    val worldZoom = log2(width.toDouble() / TileSystem.getTileSize()).coerceAtLeast(0.0)
                    controller.setZoom(worldZoom)
                    controller.setCenter(GeoPoint(0.0, 0.0))
                }
                1 -> {
                    controller.setZoom(LOCALITY_ZOOM)
                    controller.setCenter(points[0])
                }
                else -> {
                    val margin = minOf((64 * resources.displayMetrics.density).roundToInt(), minOf(width, height) / 6)
                    zoomToBoundingBox(BoundingBox.fromGeoPoints(points), false, margin, LOCALITY_ZOOM, null)
                }
            }
        }
        saveCamera()
    }

    private fun pauseMap() {
        if (!resumed) return
        saveCamera()
        onPause()
        resumed = false
        boundaryDelay?.cancel()
        boundaryLookup?.cancel()
        lookupRequested = false
    }

    private fun saveCamera() {
        if (!isLayoutOccurred) return
        val center = mapCenter
        camera.latitude = center.latitude
        camera.longitude = center.longitude
        camera.zoom = zoomLevelDouble
    }
}

private fun createTileProvider(context: Context, satellite: Boolean): MapTileProviderBasic =
    MapTileProviderBasic(context, if (satellite) SatelliteTileSource else TileSourceFactory.MAPNIK).apply {
        // Keep disk hits first and start fresh visible downloads before trying approximations.
        setOfflineFirst(false)
    }

private fun configureMap(context: Context): Context {
    val base = File(context.cacheDir, "osmdroid")
    val tiles = File(base, "tiles").apply { mkdirs() }
    val memoryClass = context.getSystemService(ActivityManager::class.java)?.memoryClass ?: 128
    Configuration.getInstance().apply {
        osmdroidBasePath = base
        osmdroidTileCache = tiles
        userAgentValue = "GlobeLog/${BuildConfig.VERSION_NAME} (${context.packageName})"
        cacheMapTileCount = (if (memoryClass >= 192) 48 else 28).toShort()
    }
    return context
}

private class MapPin(density: Float, private val fillColor: Int, private val rimColor: Int) : Drawable() {
    private val pinWidth = (34 * density).roundToInt()
    private val pinHeight = (42 * density).roundToInt()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var pinAlpha = 255
    private val silhouette = Path().apply {
        moveTo(17f, 3f)
        cubicTo(25f, 3f, 31f, 9f, 31f, 17f)
        cubicTo(31f, 24f, 23f, 31f, 17f, 40f)
        cubicTo(11f, 31f, 3f, 24f, 3f, 17f)
        cubicTo(3f, 9f, 9f, 3f, 17f, 3f)
        close()
    }

    override fun draw(canvas: Canvas) {
        val transform = canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        canvas.scale(bounds.width() / 34f, bounds.height() / 42f)
        paint.color = fillColor
        paint.alpha = pinAlpha
        paint.style = Paint.Style.FILL
        canvas.drawPath(silhouette, paint)
        paint.color = rimColor
        paint.alpha = pinAlpha
        paint.style = Paint.Style.STROKE
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeWidth = 2f
        canvas.drawPath(silhouette, paint)
        paint.style = Paint.Style.FILL
        canvas.drawCircle(17f, 17f, 5f, paint)
        canvas.restoreToCount(transform)
    }

    override fun getIntrinsicWidth() = pinWidth
    override fun getIntrinsicHeight() = pinHeight

    override fun setAlpha(alpha: Int) {
        pinAlpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Required by Drawable")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

private class LivePersonPin(density: Float, person: MapTrackedPerson) : Drawable() {
    private val diameter = (34 * density).roundToInt()
    private val fill = if (person.isSelf) 0xFF215DAD.toInt() else 0xFF205B48.toInt()
    private val ring = if (person.isSelf) 0xFFBDE1FF.toInt() else 0xFFB9F2D4.toInt()
    private val initials = person.displayName.trim().let { name ->
        if (name.isEmpty()) "?" else String(Character.toChars(name.codePointAt(0))).uppercase()
    }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textSize = 14f
    }
    private var pinAlpha = 255

    override fun draw(canvas: Canvas) {
        val transform = canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        canvas.scale(bounds.width() / 34f, bounds.height() / 34f)
        paint.color = ring
        paint.alpha = pinAlpha / 3
        canvas.drawCircle(17f, 17f, 16f, paint)
        paint.alpha = pinAlpha
        canvas.drawCircle(17f, 17f, 12.5f, paint)
        paint.color = fill
        paint.alpha = pinAlpha
        canvas.drawCircle(17f, 17f, 10.5f, paint)
        paint.color = android.graphics.Color.WHITE
        paint.alpha = pinAlpha
        canvas.drawText(initials, 17f, 17f - (paint.ascent() + paint.descent()) / 2, paint)
        canvas.restoreToCount(transform)
    }

    override fun getIntrinsicWidth() = diameter
    override fun getIntrinsicHeight() = diameter

    override fun setAlpha(alpha: Int) {
        pinAlpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Required by Drawable")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
