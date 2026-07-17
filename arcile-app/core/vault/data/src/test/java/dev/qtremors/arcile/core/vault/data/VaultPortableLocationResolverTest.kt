package dev.qtremors.arcile.core.vault.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.arcile.core.storage.domain.StorageVolume
import dev.qtremors.arcile.core.storage.domain.VolumeRepository
import dev.qtremors.arcile.core.vault.domain.VaultFailure
import dev.qtremors.arcile.core.vault.domain.VaultId
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VaultPortableLocationResolverTest {
    private lateinit var context: Context
    private lateinit var firstRoot: File
    private lateinit var secondRoot: File
    private lateinit var repository: MutableVolumeRepository
    private lateinit var resolver: VaultPortableLocationResolver

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("onlyfiles_vault_locations", Context.MODE_PRIVATE).edit().clear().commit()
        firstRoot = File(context.cacheDir, "volume-a").apply { deleteRecursively(); mkdirs() }
        secondRoot = File(context.cacheDir, "volume-b").apply { deleteRecursively(); mkdirs() }
        repository = MutableVolumeRepository(volume("portable-id", firstRoot))
        resolver = VaultPortableLocationResolver(repository)
    }

    @After
    fun tearDown() {
        firstRoot.deleteRecursively()
        secondRoot.deleteRecursively()
    }

    @Test
    fun `registration follows a stable volume id across remount paths`() = runTest {
        val firstFolder = File(firstRoot, "Documents/Safe").apply { mkdirs() }
        val identified = resolver.identify(firstFolder.path)
        assertEquals("portable-id", identified.location.volumeId)
        assertEquals("Documents/Safe", identified.location.relativePath)

        val pointer = ExternalVaultPointer(
            vaultId = VaultId.random().value,
            volumeId = identified.location.volumeId,
            relativePath = identified.location.relativePath,
            cachedName = "Safe",
            cachedCreatedAtMillis = 1L,
            headerFingerprint = "fingerprint"
        )
        val remountedFolder = File(secondRoot, "Documents/Safe").apply { mkdirs() }
        repository.current = volume("portable-id", secondRoot)
        assertEquals(remountedFolder.canonicalPath, resolver.resolve(pointer).access.directory.canonicalPath)
    }

    @Test
    fun `missing volume and escaping relative paths fail without fallback`() = runTest {
        val pointer = ExternalVaultPointer(
            vaultId = VaultId.random().value,
            volumeId = "gone",
            relativePath = "Safe",
            cachedName = "Gone",
            cachedCreatedAtMillis = 1L
        )
        assertTrue(resolver.resolveCatching(pointer) is VaultFailure.RemovableStorageMissing)

        val escaping = pointer.copy(volumeId = "portable-id", relativePath = "../outside")
        assertTrue(resolver.resolveCatching(escaping) is VaultFailure.InvalidPath)
    }

    @Test
    fun `new registry records never persist an absolute path`() {
        val registry = VaultLocationRegistry(context)
        val pointer = ExternalVaultPointer(
            vaultId = VaultId.random().value,
            volumeId = "portable-id",
            relativePath = "Documents/Safe",
            cachedName = "Safe",
            cachedCreatedAtMillis = 1L,
            headerFingerprint = "fingerprint"
        )
        registry.put(pointer)
        val encoded = context.getSharedPreferences("onlyfiles_vault_locations", Context.MODE_PRIVATE)
            .getStringSet("external_vault_pointers", emptySet()).orEmpty().single()
        assertFalse(encoded.contains(firstRoot.canonicalPath))
        assertTrue(encoded.contains("portable-id"))
        assertTrue(encoded.contains("Documents/Safe"))
        assertEquals(pointer, registry.find(VaultId.of(pointer.vaultId)))
    }

    private fun volume(id: String, root: File) = StorageVolume(
        id = id,
        storageKey = id,
        name = id,
        path = root.path,
        totalBytes = root.totalSpace,
        freeBytes = root.freeSpace,
        isPrimary = false,
        isRemovable = true
    )
}

private suspend fun VaultPortableLocationResolver.resolveCatching(pointer: ExternalVaultPointer): Throwable? =
    runCatching { resolve(pointer) }.exceptionOrNull()

private class MutableVolumeRepository(initial: StorageVolume) : VolumeRepository {
    var current: StorageVolume = initial
        set(value) {
            field = value
            state.value = listOf(value)
        }
    private val state = MutableStateFlow(listOf(initial))

    override fun observeStorageVolumes() = state
    override suspend fun getStorageVolumes() = Result.success(state.value)
    override suspend fun getVolumeForPath(path: String): Result<StorageVolume> {
        val target = File(path).canonicalPath
        val root = File(current.path).canonicalPath.trimEnd(File.separatorChar) + File.separator
        return if (target.startsWith(root, ignoreCase = true)) Result.success(current)
        else Result.failure(IllegalArgumentException("Path is outside the volume"))
    }
    override fun getStandardFolders(): Map<String, String?> = emptyMap()
}
