package ru.yavasilek.netpulse.update

import android.os.Build
import org.json.JSONObject
import ru.yavasilek.netpulse.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI

class GitHubReleaseClient {
    suspend fun latestRelease(
        repository: String = BuildConfig.GITHUB_REPOSITORY,
    ): ReleaseInfo = withContext(Dispatchers.IO) {
        require(repository.count { it == '/' } == 1) {
            "Некорректный адрес GitHub-репозитория"
        }
        val json = JSONObject(
            request("https://api.github.com/repos/$repository/releases/latest"),
        )
        val assets = json.getJSONArray("assets")
        var apk: ReleaseAsset? = null
        for (index in 0 until assets.length()) {
            val asset = assets.getJSONObject(index)
            val name = asset.getString("name")
            if (!name.endsWith(".apk", ignoreCase = true)) continue
            val digest = asset.optString("digest")
                .takeIf { it.startsWith("sha256:", ignoreCase = true) }
                ?.substringAfter(':')
            apk = ReleaseAsset(
                name = name,
                downloadUrl = asset.getString("browser_download_url"),
                sizeBytes = asset.optLong("size"),
                sha256 = digest,
            )
            if (name.contains("release", ignoreCase = true)) break
        }
        val selectedApk = requireNotNull(apk) {
            "В последнем GitHub Release нет APK"
        }
        val tagName = json.getString("tag_name")
        ReleaseInfo(
            tagName = tagName,
            versionName = tagName.removePrefix("v"),
            title = json.optString("name").ifBlank { tagName },
            notes = json.optString("body"),
            pageUrl = json.getString("html_url"),
            publishedAt = json.optString("published_at").takeIf(String::isNotBlank),
            apk = selectedApk,
        )
    }

    private fun request(url: String): String {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("X-GitHub-Api-Version", "2026-03-10")
            connection.setRequestProperty(
                "User-Agent",
                "NetPulse/${BuildConfig.VERSION_NAME} Android/${Build.VERSION.SDK_INT}",
            )
            val status = connection.responseCode
            if (status == HttpURLConnection.HTTP_NOT_FOUND) {
                error("GitHub Release пока не опубликован")
            }
            if (status !in 200..299) {
                error("GitHub вернул HTTP $status")
            }
            return connection.inputStream.bufferedReader().use { reader ->
                reader.readText().take(MAX_RESPONSE_LENGTH)
            }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 8_000
        const val MAX_RESPONSE_LENGTH = 1_000_000
    }
}
