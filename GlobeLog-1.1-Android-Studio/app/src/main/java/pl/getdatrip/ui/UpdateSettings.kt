package pl.getdatrip.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import pl.getdatrip.BuildConfig
import pl.getdatrip.checkForUpdate
import pl.getdatrip.ui.theme.tripSurface

@Composable
internal fun UpdateSettings() {
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var status by rememberSaveable { mutableStateOf("") }
    val checkLabel = tr("Sprawdź dostępne aktualizacje", "Check for updates", "Nach Updates suchen", "Buscar actualizaciones")
    val missing = tr("Adres GitHuba nie jest jeszcze ustawiony.", "The GitHub repository is not configured yet.",
        "Das GitHub-Repository ist noch nicht eingerichtet.", "El repositorio de GitHub aún no está configurado.")
    val latest = tr("Masz aktualną wersję.", "You have the latest version.", "Du hast die aktuelle Version.", "Ya tienes la última versión.")
    val failure = tr("Nie udało się sprawdzić aktualizacji.", "Could not check for updates.",
        "Die Updates konnten nicht geprüft werden.", "No se pudieron comprobar las actualizaciones.")
    Column(
        Modifier.fillMaxWidth().tripSurface(RoundedCornerShape(24.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Button(onClick = {
            if (BuildConfig.GITHUB_REPOSITORY.isBlank()) {
                status = missing
            } else {
                busy = true
                scope.launch {
                    try {
                        val update = checkForUpdate(BuildConfig.GITHUB_REPOSITORY)
                        if (update == null) status = latest else uriHandler.openUri(update.releaseUrl)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        status = failure
                    } finally {
                        busy = false
                    }
                }
            }
        }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(checkLabel) }
        if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.bodySmall)
    }
}
