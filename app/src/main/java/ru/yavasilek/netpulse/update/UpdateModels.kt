package ru.yavasilek.netpulse.update

data class ReleaseAsset(
    val name: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val sha256: String?,
)

data class ReleaseInfo(
    val tagName: String,
    val versionName: String,
    val title: String,
    val notes: String,
    val pageUrl: String,
    val publishedAt: String?,
    val apk: ReleaseAsset,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val currentVersion: String) : UpdateState
    data class Available(val release: ReleaseInfo) : UpdateState
    data class Preparing(val versionName: String) : UpdateState
    data class Downloading(
        val versionName: String,
        val downloadedBytes: Long,
        val totalBytes: Long?,
        val isPaused: Boolean = false,
    ) : UpdateState {
        val progressFraction: Float?
            get() = totalBytes
                ?.takeIf { it > 0 }
                ?.let { total ->
                    (downloadedBytes.toDouble() / total)
                        .coerceIn(0.0, 1.0)
                        .toFloat()
                }

        val percent: Int?
            get() = progressFraction?.let { (it * 100).toInt() }
    }
    data class Verifying(val versionName: String) : UpdateState
    data class ReadyToInstall(val versionName: String, val filePath: String) : UpdateState
    data class Error(val message: String) : UpdateState
}
