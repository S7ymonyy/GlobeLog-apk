package pl.getdatrip

import android.net.Uri
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal data class AppUpdate(val version: String, val releaseUrl: String)

internal suspend fun checkForUpdate(repository: String): AppUpdate? = withContext(Dispatchers.IO) {
    require(repository.matches(Regex("[A-Za-z0-9][A-Za-z0-9-]*/[A-Za-z0-9_.-]+")))
    val connection = URL("https://api.github.com/repos/$repository/releases/latest").openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = 6000
        connection.readTimeout = 8000
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "GlobeLog/${BuildConfig.VERSION_NAME}")
        when (val status = connection.responseCode) {
            HttpURLConnection.HTTP_OK -> Unit
            else -> throw IOException("GitHub Releases returned $status")
        }
        val release = connection.inputStream.bufferedReader().use { reader ->
            val buffer = CharArray(4096)
            val result = StringBuilder()
            while (true) {
                val count = reader.read(buffer)
                if (count < 0) break
                if (result.length + count > 256_000) throw IOException("GitHub response too large")
                result.append(buffer, 0, count)
            }
            JSONObject(result.toString())
        }
        val tag = release.optString("tag_name")
        if (!isNewerRelease(tag, BuildConfig.VERSION_NAME)) return@withContext null
        AppUpdate(tag, "https://github.com/$repository/releases/tag/${Uri.encode(tag)}")
    } finally {
        connection.disconnect()
    }
}

internal fun isNewerRelease(tag: String, installed: String): Boolean {
    fun parts(version: String): List<Long>? {
        val normalized = version.trim().removePrefix("v").removePrefix("V").substringBefore('+')
        if (!normalized.matches(Regex("[0-9]+(?:\\.[0-9]+){0,3}"))) return null
        return normalized.split('.').map { it.toLongOrNull() ?: return null }
    }
    val latest = parts(tag) ?: return false
    val current = parts(installed) ?: return false
    for (index in 0 until maxOf(latest.size, current.size)) {
        val result = latest.getOrElse(index) { 0 }.compareTo(current.getOrElse(index) { 0 })
        if (result != 0) return result > 0
    }
    return false
}
