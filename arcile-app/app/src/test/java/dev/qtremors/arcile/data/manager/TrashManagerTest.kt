package dev.qtremors.arcile.data.manager

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.arcile.data.FolderStatsStore
import dev.qtremors.arcile.data.provider.VolumeProvider
import dev.qtremors.arcile.data.source.MediaStoreClient
import dev.qtremors.arcile.domain.FolderStatUpdate
import dev.qtremors.arcile.domain.FolderStats
import dev.qtremors.arcile.domain.StorageKind
import dev.qtremors.arcile.testutil.createTempStorageRoot
import dev.qtremors.arcile.testutil.testVolume
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrashManagerTest {

    private lateinit var context: Context
    private lateinit var volumeProvider: VolumeProvider
    private lateinit var mediaStoreClient: MediaStoreClient
    private lateinit var folderStatsStore: FolderStatsStore
    private lateinit var trashManager: DefaultTrashManager
    private lateinit var root: File

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        root = createTempStorageRoot("trash-test")

        volumeProvider = mockk(relaxed = true)
        val vol = testVolume("primary", root.absolutePath, kind = StorageKind.INTERNAL)
        every { volumeProvider.activeStorageRoots } returns listOf(root.absolutePath)
        coEvery { volumeProvider.currentVolumes() } returns listOf(vol)
        every { volumeProvider.observeStorageVolumes() } returns flowOf(listOf(vol))

        mediaStoreClient = mockk(relaxed = true)
        folderStatsStore = object : FolderStatsStore {
            override suspend fun getCached(paths: Collection<String>): Map<String, FolderStats> = emptyMap()
            override fun observeUpdates() = emptyFlow<FolderStatUpdate>()
            override fun queue(paths: List<String>) = Unit
            override fun invalidate(paths: Collection<String>) = Unit
        }

        trashManager = DefaultTrashManager(context, volumeProvider, mediaStoreClient, folderStatsStore)
    }

    @After
    fun teardown() {
        root.deleteRecursively()
    }

    @Test
    fun `moveToTrash moves file to trash directory and creates metadata`() = runTest {
        val file = File(root, "test.txt").apply { 
            createNewFile()
            writeText("trash me")
        }

        val result = trashManager.moveToTrash(listOf(file.absolutePath))
        
        assertTrue(result.isSuccess)
        assertFalse(file.exists()) // original is gone

        val arcileDir = File(root, ".arcile")
        val trashDir = File(arcileDir, ".trash")
        val metadataDir = File(arcileDir, ".metadata")

        assertTrue(trashDir.exists())
        assertTrue(metadataDir.exists())

        // One file in trash, one metadata in metadata
        assertEquals(2, trashDir.listFiles()?.size ?: 0) // contains the trashed file + .nomedia
        assertEquals(1, metadataDir.listFiles()?.size ?: 0) // contains one .json file
    }

    @Test
    fun `getTrashFiles returns correct metadata`() = runTest {
        val file = File(root, "file1.txt").apply { createNewFile() }
        trashManager.moveToTrash(listOf(file.absolutePath))

        val result = trashManager.getTrashFiles()
        assertTrue(result.isSuccess)
        
        val trashItems = result.getOrThrow()
        assertEquals(1, trashItems.size)
        assertEquals(file.absolutePath, trashItems.first().originalPath)
        assertEquals("primary", trashItems.first().sourceVolumeId)
    }

    @Test
    fun `restoreFromTrash restores file to original location`() = runTest {
        val file = File(root, "important.txt").apply { 
            createNewFile()
            writeText("data")
        }
        val originalPath = file.absolutePath
        trashManager.moveToTrash(listOf(originalPath))
        assertFalse(file.exists())

        val trashItems = trashManager.getTrashFiles().getOrThrow()
        val trashId = trashItems.first().id

        val restoreResult = trashManager.restoreFromTrash(listOf(trashId), null)
        assertTrue(restoreResult.isSuccess)

        val restoredFile = File(originalPath)
        assertTrue(restoredFile.exists())
        assertEquals("data", restoredFile.readText())

        // Ensure trash is empty now
        val newTrashItems = trashManager.getTrashFiles().getOrThrow()
        assertTrue(newTrashItems.isEmpty())
    }

    @Test
    fun `getTrashFiles skips corrupted metadata gracefully`() = runTest {
        val metadataDir = File(File(root, ".arcile"), ".metadata")
        metadataDir.mkdirs()
        File(metadataDir, "corrupted.json").writeBytes(byteArrayOf(0x01, 0x02, 0x03)) // too short

        val result = trashManager.getTrashFiles()
        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrThrow().size)
    }

    @Test
    fun `TrashCryptoHelper fallback key round-trip and KeyStore coexistence`() = runTest {
        // We will use reflection to access the private TrashCryptoHelper
        val helperClass = Class.forName("dev.qtremors.arcile.data.manager.TrashCryptoHelper")
        val helperInstance = helperClass.getDeclaredField("INSTANCE").apply { isAccessible = true }.get(null)

        val encryptMethod = helperClass.getDeclaredMethod("encrypt", Context::class.java, String::class.java).apply { isAccessible = true }
        val decryptMethod = helperClass.getDeclaredMethod("decrypt", Context::class.java, ByteArray::class.java).apply { isAccessible = true }

        val plainText = """{"id":"test","originalPath":"/test","deletionTime":123}"""

        // 1. Normal encrypt (might use KeyStore or Fallback depending on Robolectric support)
        val encryptedWithKeyStore = encryptMethod.invoke(helperInstance, context, plainText) as ByteArray
        assertTrue(encryptedWithKeyStore[0] == 1.toByte() || encryptedWithKeyStore[0] == 2.toByte())

        // 2. Normal decrypt
        val decrypted1 = decryptMethod.invoke(helperInstance, context, encryptedWithKeyStore) as String
        assertEquals(plainText, decrypted1)

        // 3. Force encrypt with Fallback Key
        // We can do this by using reflection to call getFallbackKey and encryptOnce
        val getFallbackKeyMethod = helperClass.getDeclaredMethod("getFallbackKey", Context::class.java).apply { isAccessible = true }
        val fallbackKey = getFallbackKeyMethod.invoke(helperInstance, context) as javax.crypto.SecretKey

        val encryptOnceMethod = helperClass.getDeclaredMethod("encryptOnce", javax.crypto.SecretKey::class.java, String::class.java, Byte::class.java).apply { isAccessible = true }
        val encryptedWithFallback = encryptOnceMethod.invoke(helperInstance, fallbackKey, plainText, 2.toByte()) as ByteArray
        assertEquals(2.toByte(), encryptedWithFallback[0]) // FALLBACK_MARKER

        // 4. Decrypt fallback-encrypted data (simulates KeyStore being available but reading old fallback data)
        val decrypted2 = decryptMethod.invoke(helperInstance, context, encryptedWithFallback) as String
        assertEquals(plainText, decrypted2)

        // 5. Legacy mode (no marker)
        val legacyEncrypted = encryptOnceMethod.invoke(helperInstance, fallbackKey, plainText, 0.toByte()) as ByteArray
        val legacyBytes = legacyEncrypted.copyOfRange(1, legacyEncrypted.size) // remove marker to simulate legacy
        val decrypted3 = decryptMethod.invoke(helperInstance, context, legacyBytes) as String
        assertEquals(plainText, decrypted3)
    }
    }
