package ru.yavasilek.netpulse.update

internal data class PendingUpdateMetadata(
    val ready: Boolean,
    val versionName: String?,
    val filePath: String?,
    val downloadId: Long?,
    val totalBytes: Long?,
)

internal sealed interface UpdateRestorePlan {
    val state: UpdateState

    data class Keep(
        override val state: UpdateState,
    ) : UpdateRestorePlan

    data class Clear(
        override val state: UpdateState,
        val downloadId: Long?,
        val filePath: String?,
    ) : UpdateRestorePlan
}

internal object UpdateStatePolicy {
    fun beforeCheck(current: UpdateState): UpdateState =
        if (current.blocksCheckResult()) current else UpdateState.Checking

    fun afterCheck(
        current: UpdateState,
        result: UpdateState,
    ): UpdateState = if (current.blocksCheckResult()) current else result

    fun canStartDownload(current: UpdateState): Boolean =
        !current.blocksDownloadStart()

    fun restore(
        metadata: PendingUpdateMetadata,
        currentVersion: String,
        fileExists: Boolean,
    ): UpdateRestorePlan {
        val version = metadata.versionName
        if (version != null && !VersionComparator.isNewer(version, currentVersion)) {
            return UpdateRestorePlan.Clear(
                state = UpdateState.Idle,
                downloadId = metadata.downloadId,
                filePath = metadata.filePath,
            )
        }
        if (metadata.ready && (version == null || metadata.filePath == null || !fileExists)) {
            return UpdateRestorePlan.Clear(
                state = UpdateState.Error(
                    "Загруженный APK удалён. Проверьте обновление снова",
                ),
                downloadId = metadata.downloadId,
                filePath = metadata.filePath,
            )
        }
        return when {
            metadata.ready && version != null && metadata.filePath != null && fileExists ->
                UpdateRestorePlan.Keep(
                    UpdateState.ReadyToInstall(version, metadata.filePath),
                )
            metadata.downloadId != null && version != null ->
                UpdateRestorePlan.Keep(
                    UpdateState.Downloading(
                        versionName = version,
                        downloadedBytes = 0,
                        totalBytes = metadata.totalBytes,
                    ),
                )
            else -> UpdateRestorePlan.Keep(UpdateState.Idle)
        }
    }

    private fun UpdateState.blocksCheckResult(): Boolean =
        this is UpdateState.Preparing ||
            this is UpdateState.Downloading ||
            this is UpdateState.Verifying ||
            this is UpdateState.ReadyToInstall

    private fun UpdateState.blocksDownloadStart(): Boolean =
        this is UpdateState.Preparing ||
            this is UpdateState.Downloading ||
            this is UpdateState.Verifying
}
