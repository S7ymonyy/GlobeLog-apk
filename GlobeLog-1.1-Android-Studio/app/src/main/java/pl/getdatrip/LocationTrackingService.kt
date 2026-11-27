package pl.getdatrip

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.compose.runtime.snapshotFlow
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationRequestCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import pl.getdatrip.data.DEVICE_FALLBACK_NAME
import pl.getdatrip.data.LOCALITY_ACCURACY_METERS
import pl.getdatrip.data.WeatherRepository
import pl.getdatrip.data.cachedFix
import pl.getdatrip.data.isRecentFix
import pl.getdatrip.data.relevantProviders
import pl.getdatrip.data.subscribeForFixes
import pl.getdatrip.data.unsubscribeFromFixes

class LocationTrackingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val fixes = Channel<Location>(Channel.CONFLATED)
    private val state by lazy { AppState.get(applicationContext) }
    private val repository by lazy { WeatherRepository(applicationContext) { state.language } }
    private val manager by lazy { getSystemService(LocationManager::class.java) }
    private val notificationManager by lazy { getSystemService(NotificationManager::class.java) }
    private val listeners = mutableListOf<LocationListenerCompat>()
    private var latestLocalityFix: Location? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            state.setAutoAddPlaces(false)
            stopSelf(startId)
            return START_NOT_STICKY
        }

        if ((listeners.isEmpty() && !showTrackingNotification()) || !state.autoAddPlaces) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        if (listeners.isEmpty()) {
            subscribeToLocation()
            if (listeners.isEmpty()) {
                // A foreground resume can retry without losing the user's preference.
                stopSelf(startId)
                return START_NOT_STICKY
            }
            state.setAutoTrackingActive(true)
            scope.launch { processFixes() }
            scope.launch {
                snapshotFlow { state.language }.drop(1).collect { language ->
                    refreshTrackingNotification(language)
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        fixes.cancel()
        manager?.let { locationManager -> listeners.forEach(locationManager::unsubscribeFromFixes) }
        listeners.clear()
        state.setAutoTrackingActive(false)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun subscribeToLocation() {
        val locationManager = manager ?: return
        val precise = hasPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        if (!precise && !hasPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)) return

        val providers = locationManager.relevantProviders(precise, enabledOnly = false)
        for (provider in providers) {
            val listener = object : LocationListenerCompat {
                override fun onLocationChanged(location: Location) { submit(location) }
            }
            val request = LocationRequestCompat.Builder(25_000)
                .setMinUpdateIntervalMillis(10_000)
                .setQuality(
                    if (provider == LocationManager.GPS_PROVIDER) LocationRequestCompat.QUALITY_HIGH_ACCURACY
                    else LocationRequestCompat.QUALITY_BALANCED_POWER_ACCURACY,
                ).build()
            if (locationManager.subscribeForFixes(provider, request, listener)) {
                listeners.add(listener)
                locationManager.cachedFix(provider)?.takeIf { it.isRecentFix(5_000) }?.let(::submit)
            }
        }
    }

    private fun submit(location: Location) {
        if (!scope.isActive || !state.autoAddPlaces || !location.isRecentFix(20_000)) return
        state.onDistanceOrigin(location)
        if (location.accuracy > LOCALITY_ACCURACY_METERS) return

        val previous = latestLocalityFix
        if (previous != null) {
            val lagNanos = previous.elapsedRealtimeNanos - location.elapsedRealtimeNanos
            if (lagNanos > 500_000_000L || (lagNanos >= 0 && location.accuracy >= previous.accuracy)) return
        }
        val snapshot = Location(location)
        latestLocalityFix = snapshot
        fixes.trySend(snapshot)
    }

    private suspend fun processFixes() {
        var resolvedLocation: Location? = null
        var attemptedLocation: Location? = null
        var attemptedAt: Long? = null

        for (firstFix in fixes) {
            if (!state.autoAddPlaces) return
            val elapsed = attemptedAt?.let { SystemClock.elapsedRealtime() - it }
            if (elapsed != null && elapsed < MIN_GEOCODE_INTERVAL) {
                delay(MIN_GEOCODE_INTERVAL - elapsed)
            }

            val fix = fixes.tryReceive().getOrNull() ?: firstFix
            if (!fix.isRecentFix(20_000, LOCALITY_ACCURACY_METERS)) continue
            if (resolvedLocation?.let { !hasMoved(it, fix) } == true) continue
            if (attemptedLocation?.let { !hasMoved(it, fix) && fix.accuracy >= it.accuracy * 0.8f } == true &&
                attemptedAt?.let { SystemClock.elapsedRealtime() - it < RETRY_INTERVAL } == true
            ) continue
            if (!state.autoAddPlaces) return

            attemptedLocation = fix
            attemptedAt = SystemClock.elapsedRealtime()
            val place = try {
                repository.resolvePlace(fix)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                continue
            }
            currentCoroutineContext().ensureActive()
            if (!state.autoAddPlaces) return

            if (place.name != DEVICE_FALLBACK_NAME) {
                resolvedLocation = fix
                state.onTrackedPlace(place)
            } else if (state.devicePlace == null) {
                state.onTrackedPlace(place)
            }
        }
    }

    private fun hasMoved(previous: Location, next: Location): Boolean {
        val threshold = maxOf(GEOCODE_DISTANCE, (previous.accuracy + next.accuracy) * 0.6f)
        return previous.distanceTo(next) >= threshold
    }

    private fun showTrackingNotification(): Boolean = try {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            trackingNotification(state.language),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )
        true
    } catch (_: SecurityException) {
        false
    } catch (_: IllegalStateException) {
        false
    }

    @SuppressLint("MissingPermission")
    private fun refreshTrackingNotification(language: AppLanguage) {
        try {
            notificationManager?.notify(NOTIFICATION_ID, trackingNotification(language))
        } catch (_: SecurityException) {
            // Android may block notifications while allowing the foreground service to continue.
        }
    }

    private fun trackingNotification(language: AppLanguage): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = language.text(
                "Automatyczne dodawanie miejscowości",
                "Automatic town discovery",
                "Automatische Ortserkennung",
                "Detección automática de localidades",
            )
            val channel = notificationManager?.getNotificationChannel(CHANNEL_ID) ?: NotificationChannel(
                CHANNEL_ID, name, NotificationManager.IMPORTANCE_LOW,
            ).apply { setShowBadge(false) }
            channel.name = name
            notificationManager?.createNotificationChannel(channel)
        }

        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 0, Intent(this, LocationTrackingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(language.text(
                "GlobeLog śledzi lokalizację",
                "GlobeLog is tracking your location",
                "GlobeLog erfasst deinen Standort",
                "GlobeLog está usando tu ubicación",
            ))
            .setContentText(
                language.text(
                    "Automatycznie dodaje odwiedzane miejscowości.",
                    "Automatically adds towns you visit.",
                    "Fügt automatisch Orte hinzu, die du besuchst.",
                    "Añade automáticamente las localidades que visitas.",
                ),
            )
            .setContentIntent(open)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setLocalOnly(true)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .addAction(0, language.text("Zatrzymaj", "Stop", "Anhalten", "Detener"), stop)
            .build()
    }

    companion object {
        private const val ACTION_STOP = "pl.getdatrip.action.STOP_LOCATION_TRACKING"
        private const val CHANNEL_ID = "location_tracking"
        private const val NOTIFICATION_ID = 120
        private const val MIN_GEOCODE_INTERVAL = 12_000L
        private const val RETRY_INTERVAL = 45_000L
        private const val GEOCODE_DISTANCE = 75f

        fun start(context: Context): Boolean {
            if (!hasPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) &&
                !hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            ) return false

            return try {
                ContextCompat.startForegroundService(context, Intent(context, LocationTrackingService::class.java))
                true
            } catch (_: SecurityException) {
                false
            } catch (_: IllegalStateException) {
                false
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LocationTrackingService::class.java))
        }

        private fun hasPermission(context: Context, permission: String): Boolean =
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
}
