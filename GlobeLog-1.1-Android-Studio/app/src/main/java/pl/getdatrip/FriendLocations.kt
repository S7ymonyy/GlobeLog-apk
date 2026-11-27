package pl.getdatrip

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import pl.getdatrip.data.WeatherRepository
import kotlin.math.roundToInt

data class FriendLocationMarker(
    val uid: String,
    val displayName: String,
    val latitude: Double,
    val longitude: Double,
    val updatedAtMillis: Long,
)

@Stable
class FriendLocations(context: Context, scope: CoroutineScope, private val language: () -> AppLanguage) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]))
    private val repository = WeatherRepository(appContext, language)
    private val firebase = runCatching { FirebaseApp.getInstance() }.getOrNull()
    private val auth = firebase?.let { runCatching { FirebaseAuth.getInstance(it) }.getOrNull() }
    private val database = firebase?.let { runCatching { FirebaseFirestore.getInstance(it) }.getOrNull() }
    private val writes = Mutex()

    var sharing by mutableStateOf(false)
        private set
    var markers by mutableStateOf(emptyList<FriendLocationMarker>())
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private var accountUid: String? = null
    private var friends = emptyMap<String, SocialFriend>()
    private var foreground = false
    private var mapVisible = false
    private var publishJob: Job? = null
    private var expiryJob: Job? = null
    private var lastAttemptAt: Long? = null
    private val readers = mutableMapOf<String, Job>()
    private val points = mutableMapOf<String, SharedPoint>()

    fun updateFriends(profile: SocialProfile?, friends: List<SocialFriend>) {
        val uid = profile?.uid?.takeIf { it == auth?.currentUser?.uid }
        if (accountUid != uid) {
            accountUid = uid
            sharing = false
            error = null
            lastAttemptAt = null
            stopPublisher()
            readers.values.forEach { it.cancel() }
            readers.clear()
            points.clear()
        }
        this.friends = if (uid == null) emptyMap() else friends.associateBy { it.uid } - uid
        syncReaders()
        syncPublisher()
    }

    fun setForeground(visible: Boolean) {
        foreground = visible
        syncReaders()
        syncPublisher()
    }

    fun setMapVisible(visible: Boolean) {
        mapVisible = visible
        syncReaders()
    }

    fun setSharing(enabled: Boolean): Boolean {
        if (!enabled) {
            val uid = accountUid
            sharing = false
            error = null
            stopPublisher()
            if (uid != null) scope.launch { removePoint(uid) }
            return true
        }
        if (accountUid == null || database == null || auth?.currentUser?.uid != accountUid) {
            error = language().text(
                "Zaloguj się, aby udostępnić lokalizację.", "Sign in to share your location.",
                "Melde dich an, um deinen Standort zu teilen.", "Inicia sesión para compartir tu ubicación.",
            )
            return false
        }
        if (!hasLocationPermission()) {
            error = language().text(
                "Zezwól aplikacji na dostęp do lokalizacji.", "Allow the app to access your location.",
                "Erlaube der App den Zugriff auf deinen Standort.", "Permite que la aplicación acceda a tu ubicación.",
            )
            return false
        }
        error = null
        sharing = true
        syncPublisher()
        return true
    }

    suspend fun stopSharingAndRemove(): Boolean {
        val uid = accountUid ?: return true
        sharing = false
        stopPublisher()
        return removePoint(uid)
    }

    fun close() {
        sharing = false
        scope.cancel()
        markers = emptyList()
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun canPublish(uid: String): Boolean = sharing && foreground && friends.isNotEmpty() &&
        uid == accountUid && uid == auth?.currentUser?.uid && hasLocationPermission()

    private fun syncPublisher() {
        val uid = accountUid
        val cloud = database
        if (uid == null || cloud == null || !canPublish(uid)) {
            stopPublisher()
            return
        }
        if (publishJob?.isActive == true) return

        publishJob = scope.launch {
            val document = cloud.collection(COLLECTION).document(uid)
            while (isActive && canPublish(uid)) {
                val elapsed = lastAttemptAt?.let { SystemClock.elapsedRealtime() - it } ?: PUBLISH_INTERVAL_MILLIS
                delay((PUBLISH_INTERVAL_MILLIS - elapsed).coerceAtLeast(0))
                if (!canPublish(uid)) break
                lastAttemptAt = SystemClock.elapsedRealtime()

                try {
                    val fix = repository.currentLocation()
                    writes.withLock {
                        if (canPublish(uid)) {
                            val fields = mapOf(
                                "latitudeE3" to (fix.latitude * 1000).roundToInt(),
                                "longitudeE3" to (fix.longitude * 1000).roundToInt(),
                                "updatedAt" to FieldValue.serverTimestamp(),
                            )
                            // Wait for an in-flight write before a subsequent opt-out deletes it.
                            withContext(NonCancellable) {
                                cloud.runTransaction { it.set(document, fields) }.await()
                            }
                            if (canPublish(uid)) error = null
                        }
                    }
                } catch (failure: CancellationException) {
                    throw failure
                } catch (_: Exception) {
                    if (canPublish(uid)) error = language().text(
                        "Nie udało się udostępnić lokalizacji. Sprawdź usługę lokalizacji i internet.",
                        "Could not share your location. Check location services and your internet connection.",
                        "Der Standort konnte nicht geteilt werden. Prüfe Standortdienste und Internetverbindung.",
                        "No se pudo compartir tu ubicación. Comprueba los servicios de ubicación y la conexión a Internet.",
                    )
                }
            }
        }
    }

    private fun stopPublisher() {
        publishJob?.cancel()
        publishJob = null
    }

    private suspend fun removePoint(uid: String): Boolean {
        val cloud = database ?: return false
        return try {
            writes.withLock {
                if (uid != auth?.currentUser?.uid) return@withLock false
                val document = cloud.collection(COLLECTION).document(uid)
                cloud.runTransaction { it.delete(document) }.await()
                if (accountUid == uid && !sharing) lastAttemptAt = null
                true
            }
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Exception) {
            if (accountUid == uid && !sharing) error = language().text(
                "Nie udało się usunąć ostatniej pozycji od razu. Wygaśnie po 15 minutach.",
                "Your last position could not be removed immediately. It expires after 15 minutes.",
                "Die letzte Position konnte nicht sofort gelöscht werden. Sie läuft nach 15 Minuten ab.",
                "No se pudo quitar tu última posición de inmediato. Caduca después de 15 minutos.",
            )
            false
        }
    }

    private fun syncReaders() {
        val cloud = database
        val ownUid = accountUid
        val targets = if (foreground && mapVisible && ownUid != null && ownUid == auth?.currentUser?.uid) {
            friends.keys
        } else {
            emptySet()
        }

        for (uid in readers.keys - targets) {
            readers.remove(uid)?.cancel()
            points.remove(uid)
        }
        if (cloud != null) {
            for (uid in targets - readers.keys) {
                val document = cloud.collection(COLLECTION).document(uid)
                readers[uid] = scope.launch { watchFriend(uid, document) }
            }
        }
        refreshMarkers()
    }

    private suspend fun watchFriend(uid: String, document: DocumentReference) {
        var retry = 30_000L
        while (true) {
            try {
                document.snapshots().collect { snapshot ->
                    if (!snapshot.metadata.isFromCache) {
                        retry = 30_000L
                        val point = snapshot.toSharedPoint()
                        if (point == null) points.remove(uid) else points[uid] = point
                        refreshMarkers()
                    }
                }
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                points.remove(uid)
                refreshMarkers()
            }
            delay(retry)
            retry = (retry * 2).coerceAtMost(PUBLISH_INTERVAL_MILLIS)
        }
    }

    private fun DocumentReference.snapshots() = callbackFlow {
        val registration = addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, failure ->
            if (failure != null) close(failure) else if (snapshot != null) trySend(snapshot)
        }
        awaitClose { registration.remove() }
    }

    private fun DocumentSnapshot.toSharedPoint(): SharedPoint? {
        val latitude = getLong("latitudeE3")?.takeIf { it in -90_000..90_000 } ?: return null
        val longitude = getLong("longitudeE3")?.takeIf { it in -180_000..180_000 } ?: return null
        val updatedAt = getTimestamp("updatedAt")?.toDate()?.time ?: return null
        val age = (System.currentTimeMillis() - updatedAt).coerceAtLeast(0)
        if (age >= EXPIRY_MILLIS) return null
        val expiresAtElapsed = SystemClock.elapsedRealtime() + EXPIRY_MILLIS - age
        return SharedPoint(latitude / 1000.0, longitude / 1000.0, updatedAt, expiresAtElapsed)
    }

    private fun refreshMarkers() {
        expiryJob?.cancel()
        expiryJob = null
        val now = SystemClock.elapsedRealtime()
        points.values.removeAll { now >= it.expiresAtElapsed }
        markers = points.mapNotNull { (uid, point) ->
            val friend = friends[uid] ?: return@mapNotNull null
            FriendLocationMarker(uid, friend.displayName, point.latitude, point.longitude, point.updatedAtMillis)
        }
        val earliest = points.values.minOfOrNull { it.expiresAtElapsed } ?: return
        expiryJob = scope.launch {
            delay((earliest - now).coerceAtLeast(1L))
            expiryJob = null
            refreshMarkers()
        }
    }

    private data class SharedPoint(
        val latitude: Double,
        val longitude: Double,
        val updatedAtMillis: Long,
        val expiresAtElapsed: Long,
    )

    private companion object {
        const val COLLECTION = "friendLocations"
        const val PUBLISH_INTERVAL_MILLIS = 120_000L
        const val EXPIRY_MILLIS = 900_000L
    }
}
