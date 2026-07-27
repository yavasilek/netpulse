package ru.yavasilek.netpulse.update

import android.app.DownloadManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.edit
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.net.toUri
import ru.yavasilek.netpulse.BuildConfig
import ru.yavasilek.netpulse.MainActivity
import ru.yavasilek.netpulse.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

class UpdateManager(
    private val context: Context,
    private val applicationScope: CoroutineScope,
    private val releaseClient: GitHubReleaseClient,
) {
    private val downloadManager = context.getSystemService(DownloadManager::class.java)
    private val preferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val checkPreferences =
        context.getSharedPreferences(CHECK_PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow<UpdateState>(restoreState())
    private val downloadHandleMutex = Mutex()
    private var progressJob: Job? = null

    val state: StateFlow<UpdateState> = _state.asStateFlow()

    init {
        if (_state.value is UpdateState.Downloading) {
            val downloadId = preferences.getLong(KEY_DOWNLOAD_ID, -1)
            if (downloadId >= 0) startProgressTracking(downloadId)
        }
    }

    suspend fun check(): UpdateState {
        val current = _state.value
        if (
            current is UpdateState.Preparing ||
            current is UpdateState.Downloading ||
            current is UpdateState.Verifying ||
            current is UpdateState.ReadyToInstall
        ) {
            return current
        }
        _state.value = UpdateState.Checking
        val result = runCatching { releaseClient.latestRelease() }
            .fold(
                onSuccess = { release ->
                    if (
                        VersionComparator.isNewer(
                            candidate = release.versionName,
                            current = BuildConfig.VERSION_NAME,
                        )
                    ) {
                        UpdateState.Available(release)
                    } else {
                        UpdateState.UpToDate(BuildConfig.VERSION_NAME)
                    }
                },
                onFailure = { error ->
                    UpdateState.Error(error.message ?: "Не удалось проверить обновление")
                },
            )
        _state.update { current -> UpdateStatePolicy.afterCheck(current, result) }
        val published = _state.value
        if (result !is UpdateState.Error) {
            checkPreferences.edit {
                putLong(KEY_LAST_SUCCESSFUL_CHECK, System.currentTimeMillis())
            }
        }
        return published
    }

    suspend fun checkIfStale(
        nowMillis: Long = System.currentTimeMillis(),
        cooldownMillis: Long = CHECK_ON_LAUNCH_COOLDOWN,
    ): UpdateState {
        val lastChecked = checkPreferences.getLong(KEY_LAST_SUCCESSFUL_CHECK, 0L)
        return if (nowMillis - lastChecked >= cooldownMillis) {
            check()
        } else {
            _state.value
        }
    }

    fun download(release: ReleaseInfo) {
        if (
            _state.value is UpdateState.Preparing ||
            _state.value is UpdateState.Downloading ||
            _state.value is UpdateState.Verifying
        ) {
            return
        }
        if (BuildConfig.DEBUG) {
            _state.value = UpdateState.Error(
                "Установка обновлений проверяется в подписанной release-сборке",
            )
            return
        }
        _state.value = UpdateState.Preparing(release.versionName)
        try {
            val downloadDirectory =
                requireNotNull(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS))
            val destination = File(downloadDirectory, release.apk.name)
            if (destination.exists()) destination.delete()

            val request = DownloadManager.Request(release.apk.downloadUrl.toUri())
                .setTitle("NetPulse ${release.versionName}")
                .setDescription("Загрузка обновления")
                .setMimeType(APK_MIME_TYPE)
                .setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED,
                )
                .setDestinationInExternalFilesDir(
                    context,
                    Environment.DIRECTORY_DOWNLOADS,
                    release.apk.name,
                )
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(false)

            val downloadId = downloadManager.enqueue(request)
            preferences.edit {
                putLong(KEY_DOWNLOAD_ID, downloadId)
                putString(KEY_FILE_PATH, destination.absolutePath)
                putString(KEY_VERSION, release.versionName)
                putString(KEY_SHA256, release.apk.sha256)
                putLong(KEY_TOTAL_BYTES, release.apk.sizeBytes)
            }
            _state.value = UpdateState.Downloading(
                versionName = release.versionName,
                downloadedBytes = 0,
                totalBytes = release.apk.sizeBytes.takeIf { it > 0 },
            )
            startProgressTracking(downloadId)
        } catch (error: Exception) {
            clearPending()
            _state.value = UpdateState.Error(
                error.message ?: "Не удалось начать загрузку обновления",
            )
        }
    }

    suspend fun handleDownload(downloadId: Long) = downloadHandleMutex.withLock {
        handleDownloadLocked(downloadId)
    }

    private suspend fun handleDownloadLocked(downloadId: Long) = withContext(Dispatchers.IO) {
        if (preferences.getLong(KEY_DOWNLOAD_ID, -1) != downloadId) return@withContext
        if (preferences.getBoolean(KEY_READY, false)) return@withContext
        val successful =
            queryDownload(downloadId)?.status == DownloadManager.STATUS_SUCCESSFUL
        if (!successful) {
            clearPending()
            _state.value = UpdateState.Error("Загрузка обновления не завершена")
            return@withContext
        }

        val path = preferences.getString(KEY_FILE_PATH, null)
        val version = preferences.getString(KEY_VERSION, null)
        if (path == null || version == null) {
            clearPending()
            _state.value = UpdateState.Error("Не найдены данные загруженного APK")
            return@withContext
        }
        _state.value = UpdateState.Verifying(version)
        val file = File(path)
        val verificationError = verify(file)
        if (verificationError != null) {
            file.delete()
            clearPending()
            _state.value = UpdateState.Error(verificationError)
            notifyUpdateError(verificationError)
            return@withContext
        }

        preferences.edit(commit = true) { putBoolean(KEY_READY, true) }
        _state.value = UpdateState.ReadyToInstall(version, path)
        notifyReadyToInstall(version)
    }

    private fun startProgressTracking(downloadId: Long) {
        progressJob?.cancel()
        progressJob = applicationScope.launch(Dispatchers.IO) {
            while (true) {
                val snapshot = runCatching { queryDownload(downloadId) }.getOrNull()
                if (snapshot == null) {
                    clearPending()
                    _state.value = UpdateState.Error("Загрузка обновления не найдена")
                    return@launch
                }

                when (snapshot.status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        handleDownload(downloadId)
                        return@launch
                    }
                    DownloadManager.STATUS_FAILED -> {
                        clearPending()
                        _state.value = UpdateState.Error(
                            downloadFailureMessage(snapshot.reason),
                        )
                        return@launch
                    }
                    DownloadManager.STATUS_PENDING,
                    DownloadManager.STATUS_RUNNING,
                    DownloadManager.STATUS_PAUSED,
                    -> {
                        val version = preferences.getString(KEY_VERSION, null)
                        if (version == null) {
                            clearPending()
                            _state.value = UpdateState.Error(
                                "Не найдены данные загружаемого обновления",
                            )
                            return@launch
                        }
                        val reportedTotal = snapshot.totalBytes.takeIf { it > 0 }
                        val expectedTotal = preferences
                            .getLong(KEY_TOTAL_BYTES, -1)
                            .takeIf { it > 0 }
                        _state.value = UpdateState.Downloading(
                            versionName = version,
                            downloadedBytes = snapshot.downloadedBytes.coerceAtLeast(0),
                            totalBytes = reportedTotal ?: expectedTotal,
                            isPaused = snapshot.status == DownloadManager.STATUS_PAUSED,
                        )
                    }
                }
                delay(PROGRESS_POLL_INTERVAL)
            }
        }
    }

    private fun queryDownload(downloadId: Long): DownloadSnapshot? {
        val cursor = downloadManager.query(
            DownloadManager.Query().setFilterById(downloadId),
        )
        return cursor.use {
            if (!it.moveToFirst()) return@use null
            DownloadSnapshot(
                status = it.getInt(
                    it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS),
                ),
                reason = it.getInt(
                    it.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON),
                ),
                downloadedBytes = it.getLong(
                    it.getColumnIndexOrThrow(
                        DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR,
                    ),
                ),
                totalBytes = it.getLong(
                    it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES),
                ),
            )
        }
    }

    private fun downloadFailureMessage(reason: Int): String = when (reason) {
        DownloadManager.ERROR_INSUFFICIENT_SPACE ->
            "Недостаточно места для загрузки обновления"
        DownloadManager.ERROR_DEVICE_NOT_FOUND ->
            "Хранилище для обновления недоступно"
        DownloadManager.ERROR_CANNOT_RESUME ->
            "Не удалось продолжить загрузку обновления"
        DownloadManager.ERROR_HTTP_DATA_ERROR,
        DownloadManager.ERROR_TOO_MANY_REDIRECTS,
        DownloadManager.ERROR_UNHANDLED_HTTP_CODE,
        -> "GitHub прервал загрузку обновления"
        else -> "Не удалось загрузить обновление"
    }

    fun requestInstall(): InstallRequestResult {
        val path = preferences.getString(KEY_FILE_PATH, null)
            ?: return InstallRequestResult.NotReady
        if (!preferences.getBoolean(KEY_READY, false)) {
            return InstallRequestResult.NotReady
        }
        if (!context.packageManager.canRequestPackageInstalls()) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                "package:${context.packageName}".toUri(),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return InstallRequestResult.PermissionRequired
        }

        val file = File(path)
        if (!file.isFile) return InstallRequestResult.NotReady
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.files",
            file,
        )
        val installIntent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME_TYPE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            .putExtra(Intent.EXTRA_RETURN_RESULT, false)
        context.startActivity(installIntent)
        return InstallRequestResult.Started
    }

    private fun verify(file: File): String? {
        if (!file.isFile || file.length() <= 0) return "Загруженный APK повреждён"
        val expectedSha256 = preferences.getString(KEY_SHA256, null)
        if (expectedSha256.isNullOrBlank()) {
            return "В GitHub Release отсутствует SHA-256 APK"
        }
        val actualSha256 = sha256(file)
        if (!actualSha256.equals(expectedSha256, ignoreCase = true)) {
            return "SHA-256 загруженного APK не совпадает"
        }

        val archive = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageArchiveInfo(
                file.absolutePath,
                PackageManager.PackageInfoFlags.of(
                    PackageManager.GET_SIGNING_CERTIFICATES.toLong(),
                ),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageArchiveInfo(
                file.absolutePath,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    PackageManager.GET_SIGNING_CERTIFICATES
                } else {
                    PackageManager.GET_SIGNATURES
                },
            )
        } ?: return "Android не распознал APK"

        if (archive.packageName != context.packageName) {
            return "APK имеет другой package name"
        }
        val installed = installedPackageInfo()
        if (
            PackageInfoCompat.getLongVersionCode(archive) <=
            PackageInfoCompat.getLongVersionCode(installed)
        ) {
            return "Версия APK не новее установленной"
        }
        if (signerDigests(archive) != signerDigests(installed)) {
            return "APK подписан другим ключом"
        }
        return null
    }

    private fun installedPackageInfo(): PackageInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(
                    PackageManager.GET_SIGNING_CERTIFICATES.toLong(),
                ),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(
                context.packageName,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    PackageManager.GET_SIGNING_CERTIFICATES
                } else {
                    PackageManager.GET_SIGNATURES
                },
            )
        }

    private fun signerDigests(packageInfo: PackageInfo): Set<String> {
        @Suppress("DEPRECATION")
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = packageInfo.signingInfo ?: return emptySet()
            if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
        } else {
            packageInfo.signatures.orEmpty()
        }
        return signatures.mapTo(mutableSetOf()) { signature ->
            MessageDigest.getInstance("SHA-256")
                .digest(signature.toByteArray())
                .joinToString("") { byte -> "%02x".format(byte) }
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun restoreState(): UpdateState {
        val metadata = PendingUpdateMetadata(
            ready = preferences.getBoolean(KEY_READY, false),
            versionName = preferences.getString(KEY_VERSION, null),
            filePath = preferences.getString(KEY_FILE_PATH, null),
            downloadId = preferences.getLong(KEY_DOWNLOAD_ID, -1).takeIf { it >= 0 },
            totalBytes = preferences.getLong(KEY_TOTAL_BYTES, -1).takeIf { it > 0 },
        )
        val plan = UpdateStatePolicy.restore(
            metadata = metadata,
            currentVersion = BuildConfig.VERSION_NAME,
            fileExists = metadata.filePath?.let { File(it).isFile } == true,
        )
        return when (plan) {
            is UpdateRestorePlan.Keep -> plan.state
            is UpdateRestorePlan.Clear -> {
                plan.downloadId?.let { downloadManager.remove(it) }
                plan.filePath?.let { File(it).delete() }
                preferences.edit(commit = true) { clear() }
                plan.state
            }
        }
    }

    private fun clearPending() {
        preferences.edit { clear() }
    }

    private fun notifyReadyToInstall(version: String) {
        val manager = context.getSystemService(NotificationManager::class.java)
        ensureUpdateChannel(manager)
        manager.notify(
            UPDATE_NOTIFICATION_ID,
            Notification.Builder(context, UPDATE_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("NetPulse $version готов")
                .setContentText("Нажмите, чтобы подтвердить установку")
                .setContentIntent(updatePendingIntent())
                .setAutoCancel(true)
                .build(),
        )
    }

    private fun notifyUpdateError(message: String) {
        val manager = context.getSystemService(NotificationManager::class.java)
        ensureUpdateChannel(manager)
        manager.notify(
            UPDATE_NOTIFICATION_ID,
            Notification.Builder(context, UPDATE_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Обновление NetPulse отклонено")
                .setContentText(message)
                .setStyle(Notification.BigTextStyle().bigText(message))
                .setContentIntent(updatePendingIntent())
                .setAutoCancel(true)
                .build(),
        )
    }

    private fun ensureUpdateChannel(manager: NotificationManager) {
        manager.createNotificationChannel(
            NotificationChannel(
                UPDATE_CHANNEL_ID,
                "Обновления приложения",
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
    }

    private fun updatePendingIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_UPDATE, true)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context,
            41,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    enum class InstallRequestResult {
        Started,
        PermissionRequired,
        NotReady,
    }

    private data class DownloadSnapshot(
        val status: Int,
        val reason: Int,
        val downloadedBytes: Long,
        val totalBytes: Long,
    )

    private companion object {
        const val PREFERENCES_NAME = "netpulse_update"
        const val CHECK_PREFERENCES_NAME = "netpulse_update_checks"
        const val KEY_LAST_SUCCESSFUL_CHECK = "last_successful_check"
        const val KEY_DOWNLOAD_ID = "download_id"
        const val KEY_FILE_PATH = "file_path"
        const val KEY_VERSION = "version"
        const val KEY_SHA256 = "sha256"
        const val KEY_TOTAL_BYTES = "total_bytes"
        const val KEY_READY = "ready"
        const val PROGRESS_POLL_INTERVAL = 300L
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        const val UPDATE_CHANNEL_ID = "app_updates"
        const val UPDATE_NOTIFICATION_ID = 2001
        const val CHECK_ON_LAUNCH_COOLDOWN = 12 * 60 * 60 * 1_000L
    }
}
