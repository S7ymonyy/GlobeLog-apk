package pl.getdatrip.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Switch
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.google.firebase.firestore.Blob
import java.text.DateFormat
import java.util.Date
import pl.getdatrip.MAX_PROFILE_NAME_LENGTH
import pl.getdatrip.MAX_SOCIAL_MESSAGE_LENGTH
import pl.getdatrip.SocialFriend
import pl.getdatrip.SocialMessage
import pl.getdatrip.SocialProfile
import pl.getdatrip.SocialState
import pl.getdatrip.decodeProfilePhoto
import pl.getdatrip.ui.theme.GetDaTripDesign
import pl.getdatrip.ui.theme.tripSurface

@Composable
fun ProfileScreen(
    social: SocialState,
    onShop: () -> Unit,
    onLeaderboard: () -> Unit,
    onConversation: (SocialFriend) -> Unit,
    navigationClearance: Dp = 0.dp,
) {
    val profile = social.profile
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    var friendCode by rememberSaveable(profile?.uid) { mutableStateOf("") }
    var invitationSent by rememberSaveable(profile?.uid) { mutableStateOf(false) }
    var editing by rememberSaveable(profile?.uid) { mutableStateOf(false) }
    var nfcDialogVisible by rememberSaveable(profile?.uid) { mutableStateOf(false) }
    var photoOwner by rememberSaveable { mutableStateOf<String?>(null) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val owner = photoOwner
        photoOwner = null
        if (owner != null && owner == social.profile?.uid && uri != null) social.changeProfilePhoto(uri)
    }
    val sendInvitation: () -> Unit = {
        val submittedCode = friendCode.trim()
        if (submittedCode.isNotEmpty() && !social.loading) {
            invitationSent = false
            keyboard?.hide()
            social.invite(submittedCode) {
                if (friendCode.trim() == submittedCode) friendCode = ""
                invitationSent = true
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = socialPadding(navigationClearance),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SocialTitle(tr("Profil", "Profile", "Profil", "Perfil")) }
        if (profile == null) {
            item {
                SocialCard {
                    if (!social.configured) {
                        Text(
                            tr(
                                "Logowanie Google, znajomi i wiadomości wymagają połączenia GlobeLog z projektem Firebase.",
                                "Google sign-in, friends and messages require GlobeLog to be connected to a Firebase project.",
                                "Für die Google-Anmeldung, Freunde und Nachrichten muss GlobeLog mit einem Firebase-Projekt verbunden sein.",
                                "El acceso con Google, los amigos y los mensajes requieren que GlobeLog esté conectado a un proyecto de Firebase.",
                            ),
                            color = GetDaTripDesign.colors.mutedText,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Button(
                        onClick = { social.signIn(context) },
                        enabled = social.configured && !social.loading,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (social.loading) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text(tr("Zaloguj się przez Google", "Sign in with Google", "Mit Google anmelden", "Iniciar sesión con Google"))
                        }
                    }
                }
            }
        } else {
            item {
                SocialCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SocialAvatar(profile.photo, 48.dp)
                        Text(
                            profile.displayName,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleLarge,
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 2,
                        )
                        TextButton(onClick = social::signOut, enabled = !social.loading) {
                            Text(tr("Wyloguj", "Sign out", "Abmelden", "Cerrar sesión"))
                        }
                    }
                    SocialLink(
                        tr("Edytuj profil", "Edit profile", "Profil bearbeiten", "Editar perfil"),
                        Icons.Rounded.Edit,
                        {
                            social.clearError()
                            editing = true
                        },
                    )
                }
            }
        }

        item {
            SocialLink(tr("Sklep", "Shop", "Shop", "Tienda"), Icons.Rounded.Storefront, onShop)
        }
        item {
            SocialLink(tr("Topka znajomych", "Friends leaderboard", "Freundesrangliste", "Clasificación de amigos"), Icons.Rounded.EmojiEvents, onLeaderboard)
        }
        social.error?.let { message ->
            item { SocialError(message, social::clearError) }
        }

        if (profile != null) {
            item {
                Text(tr("Zaproś znajomego", "Invite a friend", "Freund einladen", "Invitar a un amigo"), style = MaterialTheme.typography.titleLarge)
            }
            item {
                FriendCodeCard(profile)
            }
            item {
                TextButton(onClick = { nfcDialogVisible = true }) {
                    Text(tr("Dodaj przez NFC", "Add via NFC", "Per NFC hinzufügen", "Añadir por NFC"))
                }
            }
            item {
                SocialCard {
                    OutlinedTextField(
                        value = friendCode,
                        onValueChange = {
                            friendCode = it.take(80)
                            invitationSent = false
                        },
                        label = { Text(tr("Kod znajomego", "Friend’s code", "Freundescode", "Código de amigo")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            imeAction = ImeAction.Send,
                        ),
                        keyboardActions = KeyboardActions(onSend = { sendInvitation() }),
                    )
                    Button(
                        onClick = sendInvitation,
                        enabled = friendCode.isNotBlank() && !social.loading,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(tr("Wyślij zaproszenie", "Send invitation", "Einladung senden", "Enviar invitación"))
                    }
                    if (invitationSent) {
                        Text(tr("Zaproszenie wysłane.", "Invitation sent.", "Einladung gesendet.", "Invitación enviada."), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if (social.invitations.isNotEmpty()) {
                item { Text(tr("Zaproszenia", "Invitations", "Einladungen", "Invitaciones"), style = MaterialTheme.typography.titleLarge) }
                items(social.invitations, key = { "invitation:${it.id}" }) { invitation ->
                    SocialCard {
                        Text(invitation.senderName, style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { social.accept(invitation) }, enabled = !social.loading) {
                                Text(tr("Zaakceptuj", "Accept", "Annehmen", "Aceptar"))
                            }
                            TextButton(onClick = { social.decline(invitation) }, enabled = !social.loading) {
                                Text(tr("Odrzuć", "Decline", "Ablehnen", "Rechazar"))
                            }
                        }
                    }
                }
            }
            item { Text(tr("Znajomi", "Friends", "Freunde", "Amigos"), style = MaterialTheme.typography.titleLarge) }
            if (social.friends.isEmpty()) {
                item { SocialNotice(tr(
                    "Zaakceptowani znajomi pojawią się tutaj.",
                    "Accepted friends will appear here.",
                    "Bestätigte Freunde werden hier angezeigt.",
                    "Aquí aparecerán tus amigos confirmados.",
                )) }
            }
            items(social.friends, key = { "friend:${it.uid}" }) { friend ->
                SocialLink(friend.displayName, Icons.AutoMirrored.Rounded.Send, { onConversation(friend) }, friend.photo)
            }
        }
    }

    if (nfcDialogVisible && profile != null) {
        FriendNfcDialog(
            ownCode = profile.friendCode,
            onCode = { received ->
                friendCode = received
                invitationSent = false
                nfcDialogVisible = false
            },
            onDismiss = { nfcDialogVisible = false },
        )
    }
    if (editing && profile != null) {
        EditProfileDialog(social, profile, onDismiss = {
            editing = false
            social.clearError()
        }) {
            photoOwner = profile.uid
            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }
}

@Composable
private fun EditProfileDialog(
    social: SocialState,
    profile: SocialProfile,
    onDismiss: () -> Unit,
    onPickPhoto: () -> Unit,
) {
    var name by rememberSaveable(profile.uid) { mutableStateOf(profile.displayName) }
    val normalized = name.filterNot(Char::isISOControl).trim()
    val canSave = normalized.isNotBlank() && normalized != profile.displayName && !social.loading
    SocialActionDialog(
        onDismiss = { if (!social.loading) onDismiss() },
        title = tr("Edytuj profil", "Edit profile", "Profil bearbeiten", "Editar perfil"),
        body = {
            SocialAvatar(profile.photo, 60.dp)
            TextButton(onClick = onPickPhoto, enabled = !social.loading) {
                Text(tr("Zmień zdjęcie", "Change photo", "Foto ändern", "Cambiar foto"))
            }
            if (profile.photo != null) {
                TextButton(onClick = { social.changeProfilePhoto(null) }, enabled = !social.loading) {
                    Text(tr("Usuń zdjęcie", "Remove photo", "Foto entfernen", "Eliminar foto"))
                }
            }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.filterNot(Char::isISOControl).take(MAX_PROFILE_NAME_LENGTH) },
                label = { Text(tr("Nazwa profilu", "Display name", "Anzeigename", "Nombre de perfil")) },
                singleLine = true,
                enabled = !social.loading,
                modifier = Modifier.fillMaxWidth(),
            )
            social.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        },
        actions = {
            TextButton(onClick = onDismiss, enabled = !social.loading) {
                Text(tr("Zamknij", "Close", "Schließen", "Cerrar"))
            }
            TextButton(onClick = { social.changeDisplayName(normalized, onDismiss) }, enabled = canSave) {
                Text(tr("Zapisz nazwę", "Save name", "Namen speichern", "Guardar nombre"))
            }
        },
    )
}

@Composable
fun MessagesScreen(social: SocialState, navigationClearance: Dp = 0.dp) {
    val friend = social.activeFriend
    val profile = social.profile
    if (friend != null && profile != null) {
        BackHandler { social.closeChat() }
        ConversationScreen(social, profile, friend, navigationClearance)
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = socialPadding(navigationClearance),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SocialTitle(tr("Wiadomości", "Messages", "Nachrichten", "Mensajes")) }
        social.error?.let { message ->
            item { SocialError(message, social::clearError) }
        }
        when {
            !social.configured -> item {
                SocialNotice(tr(
                    "Wiadomości będą dostępne po skonfigurowaniu Firebase.",
                    "Messages will be available after Firebase is configured.",
                    "Nachrichten sind nach der Einrichtung von Firebase verfügbar.",
                    "Los mensajes estarán disponibles cuando se configure Firebase.",
                ))
            }
            profile == null -> item {
                SocialNotice(tr(
                    "Zaloguj się w Profilu, aby pisać do znajomych.",
                    "Sign in from Profile to message friends.",
                    "Melde dich im Profil an, um deinen Freunden Nachrichten zu schreiben.",
                    "Inicia sesión desde Perfil para escribir a tus amigos.",
                ))
            }
            social.friends.isEmpty() -> item {
                SocialNotice(tr(
                    "Zaproś znajomego w Profilu, aby rozpocząć rozmowę.",
                    "Invite a friend from Profile to start a chat.",
                    "Lade im Profil einen Freund ein, um einen Chat zu beginnen.",
                    "Invita a un amigo desde Perfil para iniciar un chat.",
                ))
            }
            else -> items(social.friends.sortedByDescending(social::isChatPinned), key = { it.uid }) { entry ->
                val icon = if (social.isChatPinned(entry)) Icons.Rounded.PushPin else Icons.AutoMirrored.Rounded.Send
                SocialLink(entry.displayName, icon, { social.openChat(entry) }, entry.photo)
            }
        }
    }
}

@Composable
fun LeaderboardScreen(social: SocialState, onBack: () -> Unit, navigationClearance: Dp = 0.dp) {
    val profile = social.profile
    val self = tr("Ty", "You", "Du", "Tú")
    val rankings = if (profile == null) emptyList() else {
        (social.friends.map { RankedPerson(it.uid, it.displayName, it.score) } +
            RankedPerson(profile.uid, self, profile.score))
            .sortedWith(compareByDescending<RankedPerson> { it.score }.thenBy { it.name })
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = socialPadding(navigationClearance),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SocialTitle(tr("Topka znajomych", "Friends leaderboard", "Freundesrangliste", "Clasificación de amigos"), onBack) }
        if (profile == null) {
            item {
                SocialNotice(
                    if (social.configured) {
                        tr(
                            "Zaloguj się w Profilu, aby porównać wyniki ze znajomymi.",
                            "Sign in from Profile to compare scores with friends.",
                            "Melde dich im Profil an, um deine Punkte mit Freunden zu vergleichen.",
                            "Inicia sesión desde Perfil para comparar tu puntuación con tus amigos.",
                        )
                    } else {
                        tr(
                            "Ranking znajomych wymaga skonfigurowania Firebase.",
                            "The friends leaderboard requires Firebase configuration.",
                            "Die Freundesrangliste erfordert eine Firebase-Konfiguration.",
                            "La clasificación de amigos requiere configurar Firebase.",
                        )
                    },
                )
            }
        } else {
            item {
                Text(
                    tr(
                        "Punkty zdobyte za odkrywanie miejscowości",
                        "Points earned for discovering places",
                        "Punkte für entdeckte Orte",
                        "Puntos por descubrir localidades",
                    ),
                    color = GetDaTripDesign.colors.mutedText,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            items(rankings.size, key = { rankings[it].uid }) { index ->
                val person = rankings[index]
                val rank = rankings.indexOfFirst { it.score == person.score } + 1
                SocialCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                rank.toString(),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                        Text(person.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        Text(
                            tr("${person.score} pkt", "${person.score} pts", "${person.score} Pkt.", "${person.score} pts"),
                            color = GetDaTripDesign.colors.accent,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FriendCodeCard(profile: SocialProfile) {
    val context = LocalContext.current
    val title = tr("Twój kod zaproszenia", "Your invitation code", "Dein Einladungscode", "Tu código de invitación")
    val copied = tr("Kod skopiowany", "Code copied", "Code kopiert", "Código copiado")
    val shareTitle = tr("Udostępnij kod", "Share code", "Code teilen", "Compartir código")
    val shareText = tr(
        "Dodaj mnie do znajomych w GlobeLog. Mój kod to ${profile.friendCode}.",
        "Add me as a friend on GlobeLog. My code is ${profile.friendCode}.",
        "Füge mich in GlobeLog als Freund hinzu. Mein Code lautet ${profile.friendCode}.",
        "Añádeme como amigo en GlobeLog. Mi código es ${profile.friendCode}.",
    )
    SocialCard {
        Text(title, color = GetDaTripDesign.colors.mutedText, style = MaterialTheme.typography.labelLarge)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                profile.friendCode,
                modifier = Modifier.weight(1f),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                color = GetDaTripDesign.colors.accent,
                style = MaterialTheme.typography.titleMedium,
            )
            IconButton(onClick = {
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
                    ?.setPrimaryClip(ClipData.newPlainText(title, profile.friendCode))
                if (Build.VERSION.SDK_INT < 33) Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
            }) {
                Icon(Icons.Rounded.ContentCopy, tr("Skopiuj kod", "Copy code", "Code kopieren", "Copiar código"))
            }
            IconButton(onClick = {
                val intent = Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, shareText)
                context.startActivity(Intent.createChooser(intent, shareTitle))
            }) {
                Icon(Icons.Rounded.Share, shareTitle)
            }
        }
    }
}

@Composable
private fun ConversationScreen(
    social: SocialState,
    profile: SocialProfile,
    friend: SocialFriend,
    navigationClearance: Dp,
) {
    var draft by rememberSaveable(friend.friendshipId) { mutableStateOf("") }
    var showChatSettings by rememberSaveable(friend.friendshipId) { mutableStateOf(false) }
    var messageToEdit by remember(friend.friendshipId) { mutableStateOf<SocialMessage?>(null) }
    var messageToDelete by remember(friend.friendshipId) { mutableStateOf<SocialMessage?>(null) }
    val listState = rememberLazyListState()
    val newestMessage = social.messages.lastOrNull()
    val messages = social.messages.asReversed()

    LaunchedEffect(friend.friendshipId, newestMessage?.id) {
        if (newestMessage != null && (listState.firstVisibleItemIndex <= 1 || newestMessage.senderUid == profile.uid)) {
            listState.scrollToItem(0)
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 16.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) { SocialTitle(friend.displayName, social::closeChat) }
            IconButton(onClick = {
                social.clearError()
                showChatSettings = true
            }) {
                Icon(Icons.Rounded.MoreVert, tr("Ustawienia rozmowy", "Chat settings", "Chat-Einstellungen", "Ajustes del chat"))
            }
        }

        social.pinnedMessage?.let { pinned ->
            PinnedMessageBanner(pinned, canUnpin = !social.loading, onUnpin = social::unpinMessage)
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            reverseLayout = true,
            contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (messages.isEmpty()) {
                item { SocialNotice(tr("Napisz pierwszą wiadomość.", "Write the first message.", "Schreib die erste Nachricht.", "Escribe el primer mensaje.")) }
            }
            items(messages, key = { it.id }) { message ->
                val own = message.senderUid == profile.uid
                val pinned = message.id == social.pinnedMessageId
                MessageBubble(
                    message = message,
                    own = own,
                    pinned = pinned,
                    canDelete = own && !social.chatDeletionBlocked && !social.loading,
                    busy = social.loading,
                    onPin = { if (pinned) social.unpinMessage() else social.pinMessage(message) },
                    onEdit = {
                        social.clearError()
                        messageToEdit = message
                    },
                    onDelete = {
                        social.clearError()
                        messageToDelete = message
                    },
                )
            }
        }

        social.error?.let { message ->
            Box(Modifier.padding(horizontal = 22.dp)) { SocialError(message, social::clearError) }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 22.dp, end = 16.dp, top = 8.dp, bottom = 14.dp + navigationClearance),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it.take(MAX_SOCIAL_MESSAGE_LENGTH) },
                modifier = Modifier.weight(1f),
                label = { Text(tr("Wiadomość", "Message", "Nachricht", "Mensaje")) },
                maxLines = 4,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            FilledTonalIconButton(
                onClick = {
                    val submittedDraft = draft
                    social.sendMessage(submittedDraft.trim()) {
                        if (draft == submittedDraft) draft = ""
                    }
                },
                enabled = draft.isNotBlank() && !social.loading,
            ) {
                Icon(Icons.AutoMirrored.Rounded.Send, tr("Wyślij wiadomość", "Send message", "Nachricht senden", "Enviar mensaje"))
            }
        }
    }

    if (showChatSettings) {
        ChatSettingsDialog(social, friend, onDismiss = {
            social.clearError()
            showChatSettings = false
        })
    }

    messageToEdit?.let { selected ->
        EditMessageDialog(social, selected, onDismiss = {
            social.clearError()
            messageToEdit = null
        })
    }
    messageToDelete?.let { selected ->
        DeleteMessageDialog(social, selected, onDismiss = {
            social.clearError()
            messageToDelete = null
        })
    }
}

@Composable
private fun MessageBubble(
    message: SocialMessage,
    own: Boolean,
    pinned: Boolean,
    canDelete: Boolean,
    busy: Boolean,
    onPin: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val language = LocalAppLanguage.current
    var actionsOpen by remember { mutableStateOf(false) }
    val time = remember(message.sentAtMillis, language) {
        message.sentAtMillis?.let {
            DateFormat.getTimeInstance(DateFormat.SHORT, language.locale).format(Date(it))
        }
    }
    val shape = if (own) {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 5.dp)
    } else {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 5.dp, bottomEnd = 18.dp)
    }
    val content = if (own) MaterialTheme.colorScheme.onPrimaryContainer else GetDaTripDesign.colors.text
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .align(if (own) Alignment.CenterEnd else Alignment.CenterStart)
                .widthIn(max = maxWidth * 0.84f),
        ) {
            Column(
                modifier = Modifier
                    .clip(shape)
                    .then(
                        if (own) Modifier.background(MaterialTheme.colorScheme.primaryContainer)
                        else Modifier.tripSurface(shape),
                    )
                    .clickable(
                        onClickLabel = tr("Opcje wiadomości", "Message options", "Nachrichtenoptionen", "Opciones del mensaje"),
                        onClick = { actionsOpen = true },
                    )
                    .padding(horizontal = 13.dp, vertical = 9.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(message.text, color = content, style = MaterialTheme.typography.bodyMedium)
                if (time != null || message.editedAtMillis != null || pinned) {
                    Row(
                        modifier = Modifier.align(Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        if (pinned) {
                            Icon(
                                Icons.Rounded.PushPin,
                                tr("Przypięta wiadomość", "Pinned message", "Angeheftete Nachricht", "Mensaje fijado"),
                                modifier = Modifier.size(12.dp),
                                tint = content,
                            )
                        }
                        if (message.editedAtMillis != null) {
                            Text(
                                tr("Edytowano", "Edited", "Bearbeitet", "Editado"),
                                color = content.copy(alpha = 0.75f),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        if (time != null) {
                            Text(time, color = content.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            DropdownMenu(expanded = actionsOpen, onDismissRequest = { actionsOpen = false }) {
                val pinLabel = if (pinned) {
                    tr("Odepnij wiadomość", "Unpin message", "Nachricht lösen", "Desfijar mensaje")
                } else {
                    tr("Przypnij wiadomość", "Pin message", "Nachricht anheften", "Fijar mensaje")
                }
                DropdownMenuItem(
                    text = { Text(pinLabel) },
                    enabled = !busy,
                    onClick = {
                        actionsOpen = false
                        onPin()
                    },
                )
                if (own) {
                    DropdownMenuItem(
                        text = { Text(tr("Edytuj wiadomość", "Edit message", "Nachricht bearbeiten", "Editar mensaje")) },
                        enabled = !busy,
                        onClick = {
                            actionsOpen = false
                            onEdit()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(tr("Usuń wiadomość", "Delete message", "Nachricht löschen", "Eliminar mensaje")) },
                        enabled = canDelete,
                        onClick = {
                            actionsOpen = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PinnedMessageBanner(message: SocialMessage, canUnpin: Boolean, onUnpin: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp)
            .tripSurface(RoundedCornerShape(16.dp))
            .padding(start = 12.dp, end = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.PushPin, contentDescription = null, tint = GetDaTripDesign.colors.accent)
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(
                tr("Przypięta wiadomość", "Pinned message", "Angeheftete Nachricht", "Mensaje fijado"),
                style = MaterialTheme.typography.labelMedium,
            )
            Text(message.text, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onUnpin, enabled = canUnpin) {
            Icon(Icons.Rounded.Close, tr("Odepnij wiadomość", "Unpin message", "Nachricht lösen", "Desfijar mensaje"))
        }
    }
}

@Composable
private fun ChatSettingsDialog(social: SocialState, friend: SocialFriend, onDismiss: () -> Unit) {
    SocialActionDialog(
        onDismiss = onDismiss,
        title = tr("Ustawienia rozmowy", "Chat settings", "Chat-Einstellungen", "Ajustes del chat"),
        body = {
            ChatSettingSwitch(
                title = tr("Przypnij rozmowę", "Pin conversation", "Unterhaltung anheften", "Fijar conversación"),
                description = tr(
                    "Na górze listy na tym urządzeniu.",
                    "Moves to the top of the list on this device.",
                    "Auf diesem Gerät oben in der Liste.",
                    "La sitúa al principio de la lista en este dispositivo.",
                ),
                checked = social.isChatPinned(friend),
                onCheckedChange = { social.toggleChatPin(friend) },
            )
            ChatSettingSwitch(
                title = tr("Blokuj usuwanie wiadomości", "Block message deletion", "Nachrichtenlöschen sperren", "Bloquear la eliminación de mensajes"),
                description = if (social.otherDeletionBlock) {
                    tr(
                        "Znajomy też włączył blokadę usuwania.",
                        "Your friend has also blocked deletion.",
                        "Dein Freund hat das Löschen ebenfalls gesperrt.",
                        "Tu amigo también ha bloqueado la eliminación.",
                    )
                } else {
                    tr(
                        "Blokada działa dla obu uczestników.",
                        "The block applies to both participants.",
                        "Die Sperre gilt für beide Teilnehmer.",
                        "El bloqueo se aplica a los dos participantes.",
                    )
                },
                checked = social.ownDeletionBlock,
                enabled = !social.loading,
                onCheckedChange = social::setChatDeletionBlock,
            )
            social.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        },
        actions = { TextButton(onClick = onDismiss) { Text(tr("Zamknij", "Close", "Schließen", "Cerrar")) } },
    )
}

@Composable
private fun ChatSettingSwitch(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = GetDaTripDesign.colors.mutedText)
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun EditMessageDialog(social: SocialState, message: SocialMessage, onDismiss: () -> Unit) {
    var value by rememberSaveable(message.id) { mutableStateOf(message.text) }
    SocialActionDialog(
        onDismiss = { if (!social.loading) onDismiss() },
        title = tr("Edytuj wiadomość", "Edit message", "Nachricht bearbeiten", "Editar mensaje"),
        body = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it.take(MAX_SOCIAL_MESSAGE_LENGTH) },
                label = { Text(tr("Wiadomość", "Message", "Nachricht", "Mensaje")) },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 5,
                enabled = !social.loading,
            )
            social.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        },
        actions = {
            TextButton(onClick = onDismiss, enabled = !social.loading) {
                Text(tr("Anuluj", "Cancel", "Abbrechen", "Cancelar"))
            }
            TextButton(
                onClick = { social.editMessage(message, value, onDismiss) },
                enabled = value.isNotBlank() && value.trim() != message.text && !social.loading,
            ) {
                Text(tr("Zapisz", "Save", "Speichern", "Guardar"))
            }
        },
    )
}

@Composable
private fun DeleteMessageDialog(social: SocialState, message: SocialMessage, onDismiss: () -> Unit) {
    SocialActionDialog(
        onDismiss = { if (!social.loading) onDismiss() },
        title = tr("Usuń wiadomość", "Delete message", "Nachricht löschen", "Eliminar mensaje"),
        body = {
            Text(
                if (social.chatDeletionBlocked) {
                    tr(
                        "Usuwanie wiadomości w tej rozmowie jest zablokowane.",
                        "Message deletion is blocked in this chat.",
                        "Das Löschen von Nachrichten ist in diesem Chat gesperrt.",
                        "La eliminación de mensajes está bloqueada en este chat.",
                    )
                } else {
                    tr(
                        "Wiadomość zniknie u obu uczestników.",
                        "The message will disappear for both participants.",
                        "Die Nachricht verschwindet für beide Teilnehmer.",
                        "El mensaje desaparecerá para los dos participantes.",
                    )
                },
            )
            social.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        },
        actions = {
            TextButton(onClick = onDismiss, enabled = !social.loading) {
                Text(tr("Anuluj", "Cancel", "Abbrechen", "Cancelar"))
            }
            TextButton(
                onClick = { social.deleteMessage(message, onDismiss) },
                enabled = !social.chatDeletionBlocked && !social.loading,
            ) {
                Text(tr("Usuń", "Delete", "Löschen", "Eliminar"))
            }
        },
    )
}

@Composable
private fun SocialActionDialog(
    title: String,
    onDismiss: () -> Unit,
    body: @Composable () -> Unit,
    actions: @Composable () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .tripSurface(RoundedCornerShape(26.dp), elevated = true)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            body()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { actions() }
        }
    }
}

private data class RankedPerson(val uid: String, val name: String, val score: Int)

private fun socialPadding(navigationClearance: Dp) = PaddingValues(
    start = 22.dp,
    top = 12.dp,
    end = 22.dp,
    bottom = 22.dp + navigationClearance,
)

@Composable
private fun SocialTitle(title: String, onBack: (() -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr("Wstecz", "Back", "Zurück", "Atrás"))
            }
        }
        Text(
            title,
            modifier = Modifier.padding(vertical = 5.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.headlineMedium,
        )
    }
}

@Composable
private fun SocialCard(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .tripSurface(RoundedCornerShape(22.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        content()
    }
}

@Composable
private fun SocialAvatar(photo: Blob?, size: Dp) {
    val avatar = remember(photo) { decodeProfilePhoto(photo)?.asImageBitmap() }
    if (avatar == null) {
        Icon(Icons.Rounded.AccountCircle, contentDescription = null,
            modifier = Modifier.size(size), tint = GetDaTripDesign.colors.accent)
    } else {
        Image(avatar, contentDescription = null,
            modifier = Modifier.size(size).clip(CircleShape), contentScale = ContentScale.Crop)
    }
}

@Composable
private fun SocialLink(title: String, icon: ImageVector, onClick: () -> Unit, photo: Blob? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tripSurface(RoundedCornerShape(19.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val colors = GetDaTripDesign.colors
        if (photo == null) Icon(icon, contentDescription = null, tint = colors.accent)
        else SocialAvatar(photo, 32.dp)
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = colors.mutedText)
    }
}

@Composable
private fun SocialNotice(message: String) {
    SocialCard {
        Text(message, color = GetDaTripDesign.colors.mutedText, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SocialError(message: String, onDismiss: () -> Unit) {
    SocialCard {
        Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onDismiss) { Text(tr("Zamknij", "Dismiss", "Schließen", "Cerrar")) }
    }
}
