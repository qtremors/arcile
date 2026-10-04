package dev.qtremors.arcile.core.operation.android.apk

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.SearchRepository
import dev.qtremors.arcile.core.storage.domain.StorageScope
import dev.qtremors.arcile.plugin.api.PluginCompatibility
import dev.qtremors.arcile.plugin.api.PluginContract
import dev.qtremors.arcile.plugin.api.PluginMetadata
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OnDeviceApkDiscoveryTest {

    private lateinit var context: Context
    private val testDispatcher = UnconfinedTestDispatcher()
    private val dispatchers = ArcileDispatchers(
        io = testDispatcher,
        default = testDispatcher,
        main = testDispatcher,
        storage = testDispatcher
    )
    private val searchRepository = mockk<SearchRepository>()
    private val metadataReader = mockk<ApkArchiveMetadataReader>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val pm = context.packageManager
        val shadowPm = shadowOf(pm)
        val packageInfo = PackageInfo().apply {
            packageName = context.packageName
            versionName = "2.0.0"
            longVersionCode = 200L
            @Suppress("DEPRECATION")
            signatures = arrayOf(Signature(byteArrayOf(1, 2, 3)))
        }
        shadowPm.installPackage(packageInfo)
    }

    @Test
    fun `discoverArcileUpdate returns highest valid candidate for matching application ID`() = runTest {
        val file1 = FileModel(name = "arcile-2.1.0.apk", reference = "/sdcard/Download/arcile-2.1.0.apk", size = 100, lastModified = 1000, isDirectory = false)
        val file2 = FileModel(name = "arcile-2.2.0.apk", reference = "/sdcard/Download/arcile-2.2.0.apk", size = 100, lastModified = 2000, isDirectory = false)
        val fileDowngrade = FileModel(name = "arcile-1.9.0.apk", reference = "/sdcard/Download/arcile-1.9.0.apk", size = 100, lastModified = 500, isDirectory = false)

        coEvery { searchRepository.getFilesByCategory(any(), any()) } returns Result.success(listOf(file1, file2, fileDowngrade))

        val signatureDigest = DefaultApkArchiveMetadataReader.sha256Digest(byteArrayOf(1, 2, 3))
        coEvery { metadataReader.readMetadata(file1.reference) } returns ApkArchiveMetadata(
            packageName = context.packageName,
            versionName = "2.1.0",
            versionCode = 210L,
            minSdkVersion = 30,
            targetSdkVersion = 34,
            signingDigests = listOf(signatureDigest),
            filePath = file1.reference,
            lastModified = 1000
        )
        coEvery { metadataReader.readMetadata(file2.reference) } returns ApkArchiveMetadata(
            packageName = context.packageName,
            versionName = "2.2.0",
            versionCode = 220L,
            minSdkVersion = 30,
            targetSdkVersion = 34,
            signingDigests = listOf(signatureDigest),
            filePath = file2.reference,
            lastModified = 2000
        )
        coEvery { metadataReader.readMetadata(fileDowngrade.reference) } returns ApkArchiveMetadata(
            packageName = context.packageName,
            versionName = "1.9.0",
            versionCode = 190L,
            minSdkVersion = 30,
            targetSdkVersion = 34,
            signingDigests = listOf(signatureDigest),
            filePath = fileDowngrade.reference,
            lastModified = 500
        )

        val discovery = DefaultOnDeviceApkDiscovery(context, searchRepository, metadataReader, dispatchers)
        val result = discovery.discoverArcileUpdate()

        assertNotNull(result)
        assertEquals(220L, result?.metadata?.versionCode)
        assertEquals("2.2.0", result?.metadata?.versionName)
        assertEquals(file2.reference, result?.metadata?.filePath)
        assertTrue(result?.isValid == true)
    }

    @Test
    fun `discoverArcileUpdate returns null if all candidates are downgrades or have mismatched signatures`() = runTest {
        val fileMismatch = FileModel(name = "arcile-3.0.0.apk", reference = "/sdcard/Download/arcile-3.0.0.apk", size = 100, lastModified = 1000, isDirectory = false)
        coEvery { searchRepository.getFilesByCategory(any(), any()) } returns Result.success(listOf(fileMismatch))
        coEvery { metadataReader.readMetadata(fileMismatch.reference) } returns ApkArchiveMetadata(
            packageName = context.packageName,
            versionName = "3.0.0",
            versionCode = 300L,
            minSdkVersion = 30,
            targetSdkVersion = 34,
            signingDigests = listOf("untrusted_signature_digest"),
            filePath = fileMismatch.reference
        )

        val discovery = DefaultOnDeviceApkDiscovery(context, searchRepository, metadataReader, dispatchers)
        val result = discovery.discoverArcileUpdate()

        assertNull(result)
    }

    @Test
    fun `discoverArcileUpdate rejects candidate when signer metadata is missing`() = runTest {
        val file = FileModel(
            name = "arcile-3.0.0.apk",
            reference = "/sdcard/Download/arcile-3.0.0.apk",
            size = 100,
            lastModified = 1000,
            isDirectory = false
        )
        coEvery { searchRepository.getFilesByCategory(any(), any()) } returns Result.success(listOf(file))
        coEvery { metadataReader.readMetadata(file.reference) } returns ApkArchiveMetadata(
            packageName = context.packageName,
            versionName = "3.0.0",
            versionCode = 300L,
            minSdkVersion = 30,
            targetSdkVersion = 34,
            signingDigests = emptyList(),
            filePath = file.reference
        )

        val discovery = DefaultOnDeviceApkDiscovery(context, searchRepository, metadataReader, dispatchers)

        assertNull(discovery.discoverArcileUpdate())
    }
}
