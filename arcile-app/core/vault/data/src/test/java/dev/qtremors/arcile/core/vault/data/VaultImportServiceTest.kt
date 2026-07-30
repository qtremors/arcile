package dev.qtremors.arcile.core.vault.data

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.arcile.core.vault.domain.VaultImportProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.android.controller.ServiceController
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VaultImportServiceTest {
    private lateinit var context: Context
    private lateinit var controller: ServiceController<VaultImportService>
    private lateinit var service: VaultImportService
    private lateinit var notificationManager: NotificationManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        controller = Robolectric.buildService(VaultImportService::class.java)
        service = controller.get()
        notificationManager = context.getSystemService(NotificationManager::class.java)
    }

    @Test
    fun `completed import removes its progress notification`() {
        val started = CountDownLatch(1)
        val finish = CountDownLatch(1)
        service.executeImport = { _, _, _, _, onProgress ->
            onProgress(progress(completedItems = 1, totalItems = 2))
            started.countDown()
            finish.await(2, TimeUnit.SECONDS)
        }

        controller.withIntent(startIntent("vault-one")).startCommand(0, 1)

        assertTrue(started.await(2, TimeUnit.SECONDS))
        awaitNotificationCount(1)
        finish.countDown()
        awaitNotificationCount(0)
    }

    @Test
    fun `finishing current import preserves another import notification`() {
        val firstStarted = CountDownLatch(1)
        val secondStarted = CountDownLatch(1)
        val finishFirst = CountDownLatch(1)
        val finishSecond = CountDownLatch(1)
        service.executeImport = { vaultId, _, _, _, onProgress ->
            onProgress(progress(completedItems = 1, totalItems = 2))
            if (vaultId.value == "vault-one") {
                firstStarted.countDown()
                finishFirst.await(2, TimeUnit.SECONDS)
            } else {
                secondStarted.countDown()
                finishSecond.await(2, TimeUnit.SECONDS)
            }
        }

        controller.withIntent(startIntent("vault-one")).startCommand(0, 1)
        assertTrue(firstStarted.await(2, TimeUnit.SECONDS))
        controller.withIntent(startIntent("vault-two")).startCommand(0, 2)
        assertTrue(secondStarted.await(2, TimeUnit.SECONDS))
        awaitNotificationCount(2)

        finishSecond.countDown()
        awaitNotificationCount(1)
        finishFirst.countDown()
        awaitNotificationCount(0)
    }

    @Test
    fun `cancelled import rejects late progress notification`() {
        val started = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val progressCallback = AtomicReference<(VaultImportProgress) -> Unit>()
        service.executeImport = { _, _, _, _, onProgress ->
            progressCallback.set(onProgress)
            onProgress(progress(completedItems = 1, totalItems = 2))
            started.countDown()
            finish.await(2, TimeUnit.SECONDS)
        }
        controller.withIntent(startIntent("vault-one")).startCommand(0, 1)
        assertTrue(started.await(2, TimeUnit.SECONDS))
        awaitNotificationCount(1)

        controller.withIntent(
            Intent(context, VaultImportService::class.java).apply {
                action = VaultImportService.ACTION_CANCEL
                putExtra(VaultImportService.EXTRA_VAULT_ID, "vault-one")
            }
        ).startCommand(0, 2)

        awaitNotificationCount(0)
        progressCallback.get().invoke(progress(completedItems = 2, totalItems = 2))
        assertEquals(0, shadowOf(notificationManager).allNotifications.size)
        finish.countDown()
    }

    private fun startIntent(vaultId: String): Intent =
        Intent(context, VaultImportService::class.java).apply {
            action = VaultImportService.ACTION_START
            putExtra(VaultImportService.EXTRA_VAULT_ID, vaultId)
            putExtra(VaultImportService.EXTRA_DESTINATION, "")
            putExtra(VaultImportService.EXTRA_RESERVATION_TOKEN, "token-$vaultId")
            putStringArrayListExtra(
                VaultImportService.EXTRA_SOURCE_URIS,
                arrayListOf("content://test/$vaultId")
            )
        }

    private fun progress(completedItems: Int, totalItems: Int) = VaultImportProgress(
        completedItems = completedItems,
        totalItems = totalItems,
        bytesCopied = completedItems.toLong(),
        totalBytes = totalItems.toLong(),
        currentName = "file-$completedItems"
    )

    private fun awaitNotificationCount(expected: Int) {
        val deadline = System.currentTimeMillis() + 2_000
        while (
            shadowOf(notificationManager).allNotifications.size != expected &&
            System.currentTimeMillis() < deadline
        ) {
            Thread.sleep(10)
        }
        assertEquals(expected, shadowOf(notificationManager).allNotifications.size)
    }
}
