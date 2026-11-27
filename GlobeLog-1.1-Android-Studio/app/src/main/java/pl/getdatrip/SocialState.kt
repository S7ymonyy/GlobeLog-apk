package pl.getdatrip

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import java.security.SecureRandom
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

internal const val MAX_SOCIAL_MESSAGE_LENGTH = 1_200

data class SocialProfile(
    val uid: String,
    val displayName: String,
    val friendCode: String,
    val score: Int,
    val photo: Blob? = null,
)

data class SocialFriend(
    val uid: String,
    val displayName: String,
    val score: Int,
    val friendshipId: String,
    val photo: Blob?,
)

data class SocialInvitation(val id: String, val senderName: String)

data class SocialMessage(
    val id: String,
    val senderUid: String,
    val text: String,
    val sentAtMillis: Long?,
    val editedAtMillis: Long? = null,
)

@Stable
class SocialState(context: Context, scope: CoroutineScope, private val language: () -> AppLanguage) {
    private val appContext = context.applicationContext
    private val chatPreferences = appContext.getSharedPreferences("globelog_chat_preferences", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]))
    private val firebase = runCatching { FirebaseApp.getInstance() }.getOrNull()
        ?: runCatching { FirebaseApp.initializeApp(appContext) }.getOrNull()
    private val webClientId = readWebClientId()
    private val auth = firebase?.takeIf { webClientId.isNotBlank() }
        ?.let { runCatching { FirebaseAuth.getInstance(it) }.getOrNull() }
    private val database = firebase?.takeIf { auth != null }
        ?.let { runCatching { FirebaseFirestore.getInstance(it) }.getOrNull() }

    val configured: Boolean get() = auth != null && database != null
    var profile by mutableStateOf<SocialProfile?>(null)
        private set
    var friends by mutableStateOf(emptyList<SocialFriend>())
        private set
    var invitations by mutableStateOf(emptyList<SocialInvitation>())
        private set
    var activeFriend by mutableStateOf<SocialFriend?>(null)
        private set
    var messages by mutableStateOf(emptyList<SocialMessage>())
        private set
    var pinnedMessage by mutableStateOf<SocialMessage?>(null)
        private set
    var pinnedMessageId by mutableStateOf<String?>(null)
        private set
    var ownDeletionBlock by mutableStateOf(false)
        private set
    var otherDeletionBlock by mutableStateOf(false)
        private set
    val chatDeletionBlocked: Boolean get() = ownDeletionBlock || otherDeletionBlock
    private var pinnedChats by mutableStateOf(emptySet<String>())
    var error by mutableStateOf<String?>(null)
        private set
    private var pendingOperations by mutableIntStateOf(0)
    val loading: Boolean get() = pendingOperations > 0

    private var accountUid: String? = null
    private var requestedScore = 0
    private var connecting: Job? = null
    private var publishingScore: Job? = null
    private var profileRegistration: ListenerRegistration? = null
    private var relationshipsRegistration: ListenerRegistration? = null
    private var messagesRegistration: ListenerRegistration? = null
    private var pinnedRegistration: ListenerRegistration? = null
    private val friendRegistrations = mutableMapOf<String, ListenerRegistration>()
    private val friendProfiles = mutableMapOf<String, SocialFriend>()
    private var relationships = emptyMap<String, Friendship>()
    private val authListener = FirebaseAuth.AuthStateListener { observeAccount(it.currentUser) }

    init {
        database?.let {
            // Private messages can remain available in this session's cache without persisting to disk.
            runCatching {
                it.firestoreSettings = FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                    .build()
            }
        }
        if (configured) auth?.addAuthStateListener(authListener)
    }

    fun signIn(activity: Context) {
        val account = auth ?: return
        if (!configured || loading) return
        account.currentUser?.let {
            observeAccount(it)
            return
        }

        runAction({ text(
            "Nie udało się zalogować kontem Google.",
            "Google sign-in failed.",
            "Die Google-Anmeldung ist fehlgeschlagen.",
            "No se pudo iniciar sesión con Google.",
        ) }) {
            val option = GetGoogleIdOption.Builder()
                .setServerClientId(webClientId)
                .setFilterByAuthorizedAccounts(false)
                .setAutoSelectEnabled(false)
                .build()
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            val credential = CredentialManager.create(activity).getCredential(activity, request).credential
            if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                throw SocialFailure(text("Wybierz konto Google.", "Choose a Google account.", "Wähle ein Google-Konto.", "Elige una cuenta de Google."))
            }
            val token = GoogleIdTokenCredential.createFrom(credential.data).idToken
            val result = account.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
            observeAccount(result.user)
        }
    }

    fun signOut() {
        val account = auth ?: return
        account.signOut()
        observeAccount(null)
        clearError()
        scope.launch {
            try {
                CredentialManager.create(appContext).clearCredentialState(ClearCredentialStateRequest())
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                // Firebase has already signed out even if the credential provider cannot reset state.
            }
        }
    }

    fun syncScore(score: Int) {
        requestedScore = score.coerceIn(0, MAX_SCORE)
        publishScore()
    }

    fun changeDisplayName(input: String, onSaved: () -> Unit) {
        val name = input.filterNot(Char::isISOControl).trim().take(MAX_PROFILE_NAME_LENGTH)
        if (name.isBlank()) return
        updateProfile("displayName", onSaved) { name }
    }

    fun changeProfilePhoto(uri: Uri?) {
        updateProfile("photo") {
            if (uri == null) {
                FieldValue.delete()
            } else {
                withContext(Dispatchers.IO) { loadProfilePhoto(appContext.contentResolver, uri) }
                    ?: throw SocialFailure(text(
                        "Nie udało się przygotować zdjęcia.", "Could not prepare the photo.",
                        "Das Foto konnte nicht verarbeitet werden.", "No se pudo preparar la foto.",
                    ))
            }
        }
    }

    private fun updateProfile(field: String, onSaved: () -> Unit = {}, value: suspend () -> Any) {
        val uid = profile?.uid ?: return
        val cloud = database ?: return
        if (loading) return
        runAction({ text(
            "Nie udało się zapisać profilu.", "Could not save the profile.",
            "Das Profil konnte nicht gespeichert werden.", "No se pudo guardar el perfil.",
        ) }) {
            val updated = value()
            if (accountUid != uid) return@runAction
            cloud.collection(PROFILES).document(uid).update(field, updated).await()
            if (accountUid == uid) onSaved()
        }
    }

    fun invite(code: String, onSent: () -> Unit = {}) {
        val sender = profile ?: return
        val cloud = database ?: return
        val normalized = code.filterNot { it == '-' || it.isWhitespace() }.uppercase(Locale.ROOT)
        if (!CODE_PATTERN.matches(normalized)) {
            error = text(
                "Wpisz 10-znakowy kod znajomego.",
                "Enter a 10-character friend code.",
                "Gib einen Freundescode mit 10 Zeichen ein.",
                "Escribe un código de amigo de 10 caracteres.",
            )
            return
        }
        runAction({ text(
            "Nie udało się wysłać zaproszenia.",
            "Could not send the invitation.",
            "Die Einladung konnte nicht gesendet werden.",
            "No se pudo enviar la invitación.",
        ) }) {
            val recipient = cloud.collection(CODES).document(normalized).get(Source.SERVER).await().getString("uid")
                ?: throw SocialFailure(text(
                    "Nie znaleziono tego kodu.",
                    "That code was not found.",
                    "Dieser Code wurde nicht gefunden.",
                    "No se encontró ese código.",
                ))
            if (recipient == sender.uid) {
                throw SocialFailure(text("To Twój własny kod.", "That is your own code.", "Das ist dein eigener Code.", "Ese es tu propio código."))
            }
            if (accountUid != sender.uid) return@runAction

            val members = listOf(sender.uid, recipient).sorted()
            val id = members.joinToString("_")
            when (relationships[id]?.status) {
                ACCEPTED -> throw SocialFailure(text(
                    "Ta osoba jest już znajomym.",
                    "This person is already a friend.",
                    "Diese Person ist bereits dein Freund.",
                    "Esa persona ya es tu amiga.",
                ))
                PENDING -> throw SocialFailure(text(
                    "Zaproszenie już oczekuje.",
                    "An invitation is already pending.",
                    "Eine Einladung ist bereits offen.",
                    "Ya hay una invitación pendiente.",
                ))
            }
            val invitation = cloud.collection(FRIENDSHIPS).document(id)
            val fields = mapOf(
                "members" to members,
                "senderUid" to sender.uid,
                "recipientUid" to recipient,
                "recipientCode" to normalized,
                "status" to PENDING,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            val senderDocument = cloud.collection(PROFILES).document(sender.uid)
            cloud.runTransaction { transaction ->
                val currentName = transaction.get(senderDocument).getString("displayName")
                    ?: throw IllegalStateException("Missing social profile")
                transaction.set(invitation, fields + ("senderName" to currentName))
            }.await()
            if (accountUid == sender.uid) onSent()
        }
    }

    fun accept(invitation: SocialInvitation) = respond(invitation, ACCEPTED)

    fun decline(invitation: SocialInvitation) = respond(invitation, DECLINED)

    fun openChat(friend: SocialFriend) {
        if (friend.friendshipId !in friendProfiles || accountUid == null) return
        val cloud = database ?: return
        closeChat()
        activeFriend = friendProfiles[friend.friendshipId]
        val uid = accountUid ?: return
        refreshChatPolicy(cloud, uid)
        messagesRegistration = cloud.collection(FRIENDSHIPS).document(friend.friendshipId).collection(MESSAGES)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(MESSAGE_LIMIT)
            .addSnapshotListener { snapshot, failure ->
                if (accountUid != uid || activeFriend?.friendshipId != friend.friendshipId) return@addSnapshotListener
                if (failure != null) {
                    error = text(
                        "Nie udało się pobrać wiadomości.",
                        "Could not load messages.",
                        "Die Nachrichten konnten nicht geladen werden.",
                        "No se pudieron cargar los mensajes.",
                    )
                } else {
                    messages = snapshot?.documents.orEmpty().mapNotNull { it.toMessage() }.asReversed()
                }
            }
    }

    fun closeChat() {
        messagesRegistration?.remove()
        messagesRegistration = null
        pinnedRegistration?.remove()
        pinnedRegistration = null
        activeFriend = null
        messages = emptyList()
        pinnedMessage = null
        pinnedMessageId = null
        ownDeletionBlock = false
        otherDeletionBlock = false
    }

    fun isChatPinned(friend: SocialFriend): Boolean = friend.friendshipId in pinnedChats

    fun toggleChatPin(friend: SocialFriend) {
        val uid = accountUid ?: return
        val id = friend.friendshipId
        if (friendProfiles[id]?.uid != friend.uid) return

        pinnedChats = if (id in pinnedChats) pinnedChats - id else pinnedChats + id
        chatPreferences.edit().putStringSet("pinned_chats_" + uid, pinnedChats).apply()
    }

    fun setChatDeletionBlock(blocked: Boolean) {
        val uid = accountUid ?: return
        val friend = activeFriend ?: return
        val cloud = database ?: return
        val relationship = relationships[friend.friendshipId] ?: return
        if (relationship.status != ACCEPTED || blocked == ownDeletionBlock) return

        val field = if (relationship.senderUid == uid) SENDER_BLOCK else RECIPIENT_BLOCK
        val document = cloud.collection(FRIENDSHIPS).document(friend.friendshipId)
        runAction(::chatSettingsFailure) {
            cloud.runTransaction { transaction ->
                transaction.update(document, field, blocked, "updatedAt", FieldValue.serverTimestamp())
            }.await()
        }
    }

    fun pinMessage(message: SocialMessage) {
        if (message.id == pinnedMessageId || messages.none { it.id == message.id }) return
        writeMessagePin(message.id)
    }

    fun unpinMessage() {
        val pin = pinnedMessageId ?: return
        writeMessagePin(FieldValue.delete(), expectedPin = pin)
    }

    private fun writeMessagePin(value: Any, expectedPin: String? = null) {
        val friend = activeFriend ?: return
        val cloud = database ?: return
        val document = cloud.collection(FRIENDSHIPS).document(friend.friendshipId)
        runAction(::chatSettingsFailure) {
            cloud.runTransaction { transaction ->
                if (expectedPin != null && transaction.get(document).getString(PINNED_MESSAGE) != expectedPin) {
                    return@runTransaction
                }
                transaction.update(document, PINNED_MESSAGE, value, "updatedAt", FieldValue.serverTimestamp())
            }.await()
        }
    }

    fun editMessage(message: SocialMessage, text: String, onSaved: () -> Unit = {}) {
        val uid = accountUid ?: return
        val friend = activeFriend ?: return
        val cloud = database ?: return
        val updated = text.trim()
        if (message.senderUid != uid || updated.isEmpty() || updated.length > MAX_SOCIAL_MESSAGE_LENGTH ||
            updated == message.text
        ) return

        val document = cloud.collection(FRIENDSHIPS).document(friend.friendshipId)
            .collection(MESSAGES).document(message.id)
        runAction({ text(
            "Nie udało się edytować wiadomości.",
            "Could not edit the message.",
            "Die Nachricht konnte nicht bearbeitet werden.",
            "No se pudo editar el mensaje.",
        ) }) {
            cloud.runTransaction { transaction ->
                transaction.update(document, "text", updated, "editedAt", FieldValue.serverTimestamp())
            }.await()
            if (accountUid == uid && activeFriend?.friendshipId == friend.friendshipId) onSaved()
        }
    }

    fun deleteMessage(message: SocialMessage, onDeleted: () -> Unit = {}) {
        val uid = accountUid ?: return
        val friend = activeFriend ?: return
        val cloud = database ?: return
        if (message.senderUid != uid) return

        val relationship = cloud.collection(FRIENDSHIPS).document(friend.friendshipId)
        val document = relationship.collection(MESSAGES).document(message.id)
        runAction({ text(
            "Nie udało się usunąć wiadomości.",
            "Could not delete the message.",
            "Die Nachricht konnte nicht gelöscht werden.",
            "No se pudo eliminar el mensaje.",
        ) }) {
            val blockedError = text(
                "Usuwanie wiadomości w tej rozmowie jest zablokowane.",
                "Message deletion is blocked in this chat.",
                "Das Löschen von Nachrichten ist in diesem Chat gesperrt.",
                "La eliminación de mensajes está bloqueada en este chat.",
            )
            cloud.runTransaction { transaction ->
                val current = transaction.get(relationship)
                if (current.getBoolean(SENDER_BLOCK) == true || current.getBoolean(RECIPIENT_BLOCK) == true) {
                    throw SocialFailure(blockedError)
                }
                if (current.getString(PINNED_MESSAGE) == message.id) {
                    transaction.update(relationship, PINNED_MESSAGE, FieldValue.delete(), "updatedAt", FieldValue.serverTimestamp())
                }
                transaction.delete(document)
            }.await()
            if (accountUid == uid && activeFriend?.friendshipId == friend.friendshipId) onDeleted()
        }
    }

    private fun chatSettingsFailure() = text(
        "Nie udało się zapisać ustawień rozmowy.",
        "Could not save chat settings.",
        "Die Chat-Einstellungen konnten nicht gespeichert werden.",
        "No se pudo guardar la configuración del chat.",
    )

    private fun refreshChatPolicy(cloud: FirebaseFirestore, uid: String) {
        val chatId = activeFriend?.friendshipId ?: return
        val relationship = relationships[chatId] ?: return
        val requesterIsSender = relationship.senderUid == uid
        ownDeletionBlock = if (requesterIsSender) relationship.senderBlocksDeletion else relationship.recipientBlocksDeletion
        otherDeletionBlock = if (requesterIsSender) relationship.recipientBlocksDeletion else relationship.senderBlocksDeletion
        val pin = relationship.pinnedMessageId
        if (pin == pinnedMessageId) return

        pinnedRegistration?.remove()
        pinnedRegistration = null
        pinnedMessage = null
        pinnedMessageId = pin
        if (pin == null) return

        pinnedRegistration = cloud.collection(FRIENDSHIPS).document(chatId).collection(MESSAGES).document(pin)
            .addSnapshotListener { snapshot, failure ->
                if (accountUid != uid || activeFriend?.friendshipId != chatId || pinnedMessageId != pin) {
                    return@addSnapshotListener
                }
                if (failure == null) pinnedMessage = snapshot?.toMessage()
            }
    }

    fun sendMessage(text: String, onSent: () -> Unit = {}) {
        val sender = profile ?: return
        val friend = activeFriend ?: return
        val cloud = database ?: return
        val message = text.trim()
        if (message.isEmpty()) return
        if (message.length > MAX_SOCIAL_MESSAGE_LENGTH) {
            error = text(
                "Wiadomość może mieć do $MAX_SOCIAL_MESSAGE_LENGTH znaków.",
                "Messages can have up to $MAX_SOCIAL_MESSAGE_LENGTH characters.",
                "Nachrichten dürfen höchstens $MAX_SOCIAL_MESSAGE_LENGTH Zeichen lang sein.",
                "Los mensajes pueden tener hasta $MAX_SOCIAL_MESSAGE_LENGTH caracteres.",
            )
            return
        }
        runAction({ text(
            "Nie udało się wysłać wiadomości.",
            "Could not send the message.",
            "Die Nachricht konnte nicht gesendet werden.",
            "No se pudo enviar el mensaje.",
        ) }) {
            val document = cloud.collection(FRIENDSHIPS).document(friend.friendshipId).collection(MESSAGES).document()
            val fields = mapOf(
                "senderUid" to sender.uid,
                "text" to message,
                "createdAt" to FieldValue.serverTimestamp(),
            )
            cloud.runTransaction { transaction -> transaction.set(document, fields) }.await()
            if (accountUid == sender.uid) onSent()
        }
    }

    fun clearError() {
        error = null
    }

    fun close() {
        auth?.removeAuthStateListener(authListener)
        observeAccount(null)
        scope.cancel()
    }

    private fun respond(invitation: SocialInvitation, status: String) {
        val cloud = database ?: return
        if (invitations.none { it.id == invitation.id }) return
        runAction({ text(
            "Nie udało się zapisać odpowiedzi na zaproszenie.",
            "Could not respond to the invitation.",
            "Die Antwort auf die Einladung konnte nicht gespeichert werden.",
            "No se pudo responder a la invitación.",
        ) }) {
            val document = cloud.collection(FRIENDSHIPS).document(invitation.id)
            cloud.runTransaction { transaction ->
                transaction.update(document, "status", status, "updatedAt", FieldValue.serverTimestamp())
            }.await()
        }
    }

    private fun observeAccount(user: FirebaseUser?) {
        val cloud = database ?: return
        val uid = user?.uid
        if (accountUid != uid) {
            accountUid = uid
            pinnedChats = uid?.let { chatPreferences.getStringSet("pinned_chats_" + it, emptySet())?.toSet() }.orEmpty()
            connecting?.cancel()
            publishingScore?.cancel()
            profileRegistration?.remove()
            relationshipsRegistration?.remove()
            friendRegistrations.values.forEach { it.remove() }
            friendRegistrations.clear()
            friendProfiles.clear()
            relationships = emptyMap()
            closeChat()
            profile = null
            friends = emptyList()
            invitations = emptyList()
        }
        if (user == null || profile != null || connecting?.isActive == true) return
        connecting = runAction({ text(
            "Nie udało się wczytać profilu społecznościowego.",
            "Could not load your social profile.",
            "Dein Profil konnte nicht geladen werden.",
            "No se pudo cargar tu perfil.",
        ) }) {
            val loaded = ensureProfile(cloud, user)
            if (accountUid != uid) return@runAction
            profile = loaded
            watchProfile(cloud, loaded.uid)
            watchRelationships(cloud, loaded.uid)
            publishScore()
        }
    }

    private suspend fun ensureProfile(cloud: FirebaseFirestore, user: FirebaseUser): SocialProfile {
        val uid = user.uid
        val document = cloud.collection(PROFILES).document(uid)
        val displayName = user.displayName?.trim()?.take(MAX_PROFILE_NAME_LENGTH)?.takeIf(String::isNotEmpty)
            ?: text("Podróżnik", "Traveler", "Reisender", "Viajero")
        repeat(5) {
            val code = buildString(CODE_LENGTH) {
                repeat(CODE_LENGTH) { append(CODE_CHARACTERS[RANDOM.nextInt(CODE_CHARACTERS.length)]) }
            }
            val claim = cloud.collection(CODES).document(code)
            val loaded = cloud.runTransaction { transaction ->
                val current = transaction.get(document)
                if (current.exists()) return@runTransaction current.toProfile()
                    ?: throw IllegalStateException("Invalid social profile")
                if (transaction.get(claim).exists()) return@runTransaction null
                // Older clients still require the legacy score on new profiles.
                transaction.set(document, mapOf("displayName" to displayName, "friendCode" to code, "score" to 0))
                transaction.set(claim, mapOf("uid" to uid))
                SocialProfile(uid, displayName, code, score = 0)
            }.await()
            if (loaded != null) return loaded
        }
        throw IllegalStateException("Could not generate a friend code")
    }

    private fun watchProfile(cloud: FirebaseFirestore, uid: String) {
        profileRegistration = cloud.collection(PROFILES).document(uid).addSnapshotListener { snapshot, failure ->
            if (accountUid != uid) return@addSnapshotListener
            if (failure != null) {
                error = text(
                    "Nie udało się odświeżyć profilu.",
                    "Could not refresh your profile.",
                    "Dein Profil konnte nicht aktualisiert werden.",
                    "No se pudo actualizar tu perfil.",
                )
            } else {
                snapshot?.toProfile()?.let { incoming ->
                    // A cached snapshot can arrive after a newer score has already been confirmed.
                    profile = incoming.copy(score = maxOf(incoming.score, profile?.score ?: 0))
                }
            }
        }
    }

    private fun watchRelationships(cloud: FirebaseFirestore, uid: String) {
        relationshipsRegistration = cloud.collection(FRIENDSHIPS).whereArrayContains("members", uid).limit(FRIEND_LIMIT)
            .addSnapshotListener { snapshot, failure ->
                if (accountUid != uid) return@addSnapshotListener
                if (failure != null) {
                    error = text(
                        "Nie udało się pobrać listy znajomych.",
                        "Could not load friends.",
                        "Die Freundesliste konnte nicht geladen werden.",
                        "No se pudo cargar la lista de amigos.",
                    )
                    return@addSnapshotListener
                }
                val current = snapshot?.documents.orEmpty().mapNotNull { it.toFriendship() }
                relationships = current.associateBy { it.id }
                invitations = current.filter { it.status == PENDING && it.recipientUid == uid }
                    .map { SocialInvitation(it.id, it.senderName) }

                val accepted = current.filter { it.status == ACCEPTED }.associateBy { it.id }
                for (id in friendRegistrations.keys - accepted.keys) {
                    friendRegistrations.remove(id)?.remove()
                    friendProfiles.remove(id)
                }
                for ((id, relationship) in accepted) {
                    if (id !in friendRegistrations) watchFriend(cloud, uid, relationship)
                }
                publishFriends()
                refreshChatPolicy(cloud, uid)
            }
    }

    private fun watchFriend(cloud: FirebaseFirestore, uid: String, friendship: Friendship) {
        val friendUid = if (friendship.senderUid == uid) friendship.recipientUid else friendship.senderUid
        friendRegistrations[friendship.id] = cloud.collection(PROFILES).document(friendUid)
            .addSnapshotListener { snapshot, failure ->
                if (accountUid != uid || relationships[friendship.id]?.status != ACCEPTED) return@addSnapshotListener
                if (failure != null) {
                    error = text(
                        "Nie udało się wczytać wyniku znajomego.",
                        "Could not load your friend's score.",
                        "Die Punkte deines Freundes konnten nicht geladen werden.",
                        "No se pudo cargar la puntuación de tu amigo.",
                    )
                } else {
                    val displayName = snapshot?.getString("displayName") ?: return@addSnapshotListener
                    val score = snapshot.leaderboardScore()
                    friendProfiles[friendship.id] = SocialFriend(friendUid, displayName, score, friendship.id, snapshot.getBlob("photo"))
                    publishFriends()
                }
            }
    }

    private fun publishFriends() {
        friends = friendProfiles.values.sortedWith(compareByDescending<SocialFriend> { it.score }.thenBy { it.displayName })
        val currentChat = activeFriend ?: return
        val updated = friendProfiles[currentChat.friendshipId]
        if (updated == null) closeChat() else activeFriend = updated
    }

    private fun publishScore() {
        val cloud = database ?: return
        val current = profile ?: return
        val uid = current.uid
        if (requestedScore <= current.score || publishingScore?.isActive == true) return

        publishingScore = scope.launch {
            try {
                val document = cloud.collection(PROFILES).document(uid)
                while (accountUid == uid && requestedScore > (profile?.score ?: 0)) {
                    val target = requestedScore
                    val published = cloud.runTransaction { transaction ->
                        val score = transaction.get(document).leaderboardScore()
                        if (target > score) transaction.update(document, LEADERBOARD_SCORE_FIELD, target)
                        maxOf(score, target)
                    }.await()
                    if (accountUid == uid) profile = profile?.let { it.copy(score = maxOf(it.score, published)) }
                }
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                if (accountUid == uid) error = text(
                    "Nie udało się zsynchronizować punktów.",
                    "Could not sync your score.",
                    "Deine Punkte konnten nicht synchronisiert werden.",
                    "No se pudo sincronizar tu puntuación.",
                )
            }
        }
    }

    private fun runAction(failureMessage: () -> String, block: suspend () -> Unit): Job = scope.launch {
        val uid = accountUid
        error = null
        pendingOperations++
        try {
            block()
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: GetCredentialCancellationException) {
            // Dismissing the chooser is a normal sign-in exit.
        } catch (failure: SocialFailure) {
            if (accountUid == uid) error = failure.message
        } catch (_: Exception) {
            if (accountUid == uid) error = failureMessage()
        } finally {
            pendingOperations--
        }
    }

    private fun DocumentSnapshot.toProfile(): SocialProfile? {
        val name = getString("displayName") ?: return null
        val code = getString("friendCode") ?: return null
        return SocialProfile(id, name, code, leaderboardScore(), getBlob("photo"))
    }

    private fun DocumentSnapshot.leaderboardScore(): Int =
        (getLong(LEADERBOARD_SCORE_FIELD) ?: 0L).coerceIn(0L, MAX_SCORE.toLong()).toInt()

    private fun DocumentSnapshot.toFriendship(): Friendship? {
        val sender = getString("senderUid") ?: return null
        val recipient = getString("recipientUid") ?: return null
        val name = getString("senderName") ?: return null
        val status = getString("status") ?: return null
        return Friendship(
            id = id,
            senderUid = sender,
            recipientUid = recipient,
            senderName = name,
            status = status,
            senderBlocksDeletion = getBoolean(SENDER_BLOCK) == true,
            recipientBlocksDeletion = getBoolean(RECIPIENT_BLOCK) == true,
            pinnedMessageId = getString(PINNED_MESSAGE),
        )
    }

    private fun DocumentSnapshot.toMessage(): SocialMessage? {
        val sender = getString("senderUid") ?: return null
        val text = getString("text") ?: return null
        val timestamp = getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        val edited = getTimestamp("editedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        return SocialMessage(id, sender, text, timestamp?.toDate()?.time, edited?.toDate()?.time)
    }

    @SuppressLint("DiscouragedApi")
    private fun readWebClientId(): String {
        val id = appContext.resources.getIdentifier("default_web_client_id", "string", appContext.packageName)
        return if (id == 0) "" else appContext.getString(id).trim()
    }

    private fun text(polish: String, english: String, german: String, spanish: String) =
        language().text(polish, english, german, spanish)

    private class SocialFailure(message: String) : Exception(message)

    private data class Friendship(
        val id: String,
        val senderUid: String,
        val recipientUid: String,
        val senderName: String,
        val status: String,
        val senderBlocksDeletion: Boolean,
        val recipientBlocksDeletion: Boolean,
        val pinnedMessageId: String?,
    )

    private companion object {
        const val PROFILES = "profiles"
        const val LEADERBOARD_SCORE_FIELD = "scoreV11"
        const val CODES = "codes"
        const val FRIENDSHIPS = "friendships"
        const val MESSAGES = "messages"
        const val SENDER_BLOCK = "senderBlocksDeletion"
        const val RECIPIENT_BLOCK = "recipientBlocksDeletion"
        const val PINNED_MESSAGE = "pinnedMessageId"
        const val PENDING = "pending"
        const val ACCEPTED = "accepted"
        const val DECLINED = "declined"
        const val CODE_LENGTH = 10
        const val CODE_CHARACTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        const val MESSAGE_LIMIT = 100L
        const val FRIEND_LIMIT = 100L
        const val MAX_SCORE = 1_000_000_000
        val RANDOM = SecureRandom()
        val CODE_PATTERN = Regex("[$CODE_CHARACTERS]{$CODE_LENGTH}")
    }
}
