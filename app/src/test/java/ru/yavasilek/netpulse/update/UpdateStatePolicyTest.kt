package ru.yavasilek.netpulse.update

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateStatePolicyTest {
    @Test
    fun lateCheckDoesNotOverwriteDownloading() {
        val downloading = UpdateState.Downloading(
            versionName = "0.4.0",
            downloadedBytes = 512,
            totalBytes = 1_024,
        )

        val published = publishLateCheckWhile(downloading)

        assertEquals(downloading, published)
    }

    @Test
    fun lateCheckDoesNotOverwriteReadyToInstall() {
        val ready = UpdateState.ReadyToInstall(
            versionName = "0.4.0",
            filePath = "C:\\downloads\\NetPulse-0.4.0.apk",
        )

        val published = publishLateCheckWhile(ready)

        assertEquals(ready, published)
    }

    @Test
    fun missingReadyApkClearsMetadataInsteadOfRestoringDownload() {
        val plan = UpdateStatePolicy.restore(
            metadata = PendingUpdateMetadata(
                ready = true,
                versionName = "0.4.0",
                filePath = "C:\\downloads\\missing.apk",
                downloadId = 42L,
                totalBytes = 1_024L,
            ),
            currentVersion = "0.3.2",
            fileExists = false,
        )

        assertTrue(plan is UpdateRestorePlan.Clear)
        assertFalse(plan.state is UpdateState.Downloading)
        assertTrue(plan.state is UpdateState.Error)
        plan as UpdateRestorePlan.Clear
        assertEquals(42L, plan.downloadId)
        assertEquals("C:\\downloads\\missing.apk", plan.filePath)
    }

    private fun release() = ReleaseInfo(
        tagName = "v0.4.0",
        versionName = "0.4.0",
        title = "NetPulse 0.4.0",
        notes = "",
        pageUrl = "https://github.com/yavasilek/netpulse/releases/tag/v0.4.0",
        publishedAt = null,
        apk = ReleaseAsset(
            name = "NetPulse-0.4.0.apk",
            downloadUrl = "https://example.test/NetPulse-0.4.0.apk",
            sizeBytes = 1_024L,
            sha256 = "abc",
        ),
    )

    private fun publishLateCheckWhile(current: UpdateState): UpdateState = runBlocking {
        val state = MutableStateFlow<UpdateState>(UpdateState.Checking)
        val checkResponse = CompletableDeferred<UpdateState>()
        val checkJob = launch(start = CoroutineStart.UNDISPATCHED) {
            val result = checkResponse.await()
            state.update { latest -> UpdateStatePolicy.afterCheck(latest, result) }
        }

        state.value = current
        checkResponse.complete(UpdateState.Available(release()))
        checkJob.join()

        state.value
    }
}
