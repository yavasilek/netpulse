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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

class UpdateManager(
    private val context: Context,
    private val releaseClient: GitHubReleaseClient,
) {
    private val downloadManager = context.getSystemService(DownloadManager::class.java)
    private val preferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow<UpdateState>(restoreState())

    val state: StateFlow<UpdateState> = _state.asStateFlow()

    suspend fun check(): UpdateState {
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
        _state.value = result
        return result
    }

    fun download(release: ReleaseInfo) {
        if (BuildConfig.DEBUG) {
            _state.value = UpdateState.Error(
                "Установка обновлений проверяется в подписанной release-сборке",
            )
            return
        }
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
        }
        _state.value = UpdateState.Downloading(release)
    }

    suspend fun handleDownload(downloadId: Long) = withContext(Dispatchers.IO) {
        if (preferences.getLong(KEY_DOWNLOAD_ID, -1) != downloadId) return@withContext
        val cursor = downloadManager.query(
            DownloadManager.Query().setFilterById(downloadId),
        )
        val successful = cursor.use {
            if (!it.moveToFirst()) false
            else {
                val statusIndex = it.getColumnIndex(DownloadManager.COLUMN_STATUS)
                statusIndex >= 0 &&
                    it.getInt(statusIndex) == DownloadManager.STATUS_SUCCESSFUL
            }
        }
        if (!successful) {
            _state.value = UpdateState.Error("Загрузка обновления не завершена")
            return@withContext
        }

        val path = preferences.getString(KEY_FILE_PATH, null)
        val version = preferences.getString(KEY_VERSION, null)
        if (path == null || version == null) {
            _state.value = UpdateState.Error("Не найдены данные загруженного APK")
            return@withContext
        }
        val file = File(path)
        val verificationError = verify(file)
        if (verificationError != null) {
            file.delete()
            clearPending()
            _state.value = UpdateState.Error(verificationError)
            notifyUpdateError(verificationError)
            return@withContext
        }

        preferences.edit { putBoolean(KEY_READY, true) }
        _state.value = UpdateState.ReadyToInstall(version, path)
        notifyReadyToInstall(version)
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
        val ready = preferences.getBoolean(KEY_READY, false)
        val version = preferences.getString(KEY_VERSION, null)
        val path = preferences.getString(KEY_FILE_PATH, null)
        return if (ready && version != null && path != null && File(path).isFile) {
            UpdateState.ReadyToInstall(version, path)
        } else {
            UpdateState.Idle
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

    private companion object {
        const val PREFERENCES_NAME = "netpulse_update"
        const val KEY_DOWNLOAD_ID = "download_id"
        const val KEY_FILE_PATH = "file_path"
        const val KEY_VERSION = "version"
        const val KEY_SHA256 = "sha256"
        const val KEY_READY = "ready"
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        const val UPDATE_CHANNEL_ID = "app_updates"
        const val UPDATE_NOTIFICATION_ID = 2001
    }
}
