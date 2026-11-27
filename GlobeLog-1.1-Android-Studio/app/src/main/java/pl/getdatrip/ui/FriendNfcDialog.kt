package pl.getdatrip.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.nfc.tech.IsoDep
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.delay
import pl.getdatrip.FriendNfcCard

private enum class NfcMode { Choose, Send, Receive }

@Composable
internal fun FriendNfcDialog(ownCode: String, onCode: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val activity = remember(context) { context.nfcActivity() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val adapter = remember(context) { NfcAdapter.getDefaultAdapter(context) }
    val canSend = context.packageManager.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION)
    val onReceived by rememberUpdatedState(onCode)
    var mode by remember { mutableStateOf(NfcMode.Choose) }
    val enabled = adapter?.isEnabled == true && activity != null
    DisposableEffect(mode, lifecycle, adapter, activity, ownCode, enabled) {
        val active = AtomicBoolean(false)
        if (enabled && adapter != null && activity != null && mode != NfcMode.Choose) {
            val reader = NfcAdapter.ReaderCallback { tag ->
                val code = runCatching {
                    IsoDep.get(tag)?.use { device ->
                        device.connect()
                        device.timeout = 3000
                        FriendNfcCard.read(device.transceive(FriendNfcCard.selectCommand))
                    }
                }.getOrNull()
                if (code != null && code != ownCode) activity.runOnUiThread {
                    if (active.compareAndSet(true, false)) onReceived(code)
                }
            }
            fun start() {
                active.set(true)
                if (mode == NfcMode.Send) FriendNfcCard.show(ownCode)
                else runCatching { adapter.enableReaderMode(activity, reader,
                    NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK, null) }
            }
            fun stop() {
                active.set(false)
                if (mode == NfcMode.Send) FriendNfcCard.hide()
                else runCatching { adapter.disableReaderMode(activity) }
            }
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> start()
                    Lifecycle.Event.ON_PAUSE -> stop()
                    else -> Unit
                }
            }
            lifecycle.addObserver(observer)
            onDispose {
                lifecycle.removeObserver(observer)
                stop()
            }
        } else onDispose { }
    }
    LaunchedEffect(mode) {
        if (mode != NfcMode.Choose) {
            delay(60_000)
            mode = NfcMode.Choose
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Dodaj znajomego przez NFC", "Add a friend via NFC", "Freund per NFC hinzufügen", "Añadir amigo por NFC")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(when {
                    !enabled -> tr("Włącz NFC na telefonie, który je obsługuje.", "Turn on NFC on a supported phone.",
                        "Aktiviere NFC auf einem unterstützten Gerät.", "Activa NFC en un teléfono compatible.")
                    mode == NfcMode.Send -> tr("Na drugim telefonie wybierz Odbierz kod i zbliż urządzenia.",
                        "Choose Receive code on the other phone, then hold the devices close together.",
                        "Wähle am anderen Gerät Code empfangen und halte beide Geräte aneinander.",
                        "Elige Recibir código en el otro teléfono y acerca los dispositivos.")
                    mode == NfcMode.Receive -> tr("Zbliż telefon znajomego, który udostępnia kod.",
                        "Bring the phone sharing its code close to this one.",
                        "Halte das Gerät deines Freundes an dieses Gerät.", "Acerca el teléfono del amigo que comparte su código.")
                    else -> tr("Na jednym telefonie udostępnij kod, a na drugim go odbierz.",
                        "Share the code on one phone and receive it on the other.",
                        "Teile den Code auf einem Gerät und empfange ihn auf dem anderen.",
                        "Comparte el código en un teléfono y recíbelo en el otro.")
                })
                if (enabled && mode == NfcMode.Choose) {
                    TextButton(onClick = { mode = NfcMode.Send }, enabled = canSend) {
                        Text(tr("Udostępnij kod", "Share code", "Code teilen", "Compartir código"))
                    }
                    TextButton(onClick = { mode = NfcMode.Receive }) {
                        Text(tr("Odbierz kod", "Receive code", "Code empfangen", "Recibir código"))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Zamknij", "Close", "Schließen", "Cerrar")) } },
    )
}

private tailrec fun Context.nfcActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.nfcActivity()
    else -> null
}
