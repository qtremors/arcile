package dev.qtremors.arcile.feature.onlyfiles

import dev.qtremors.arcile.core.storage.domain.FileBrowserRepository
import dev.qtremors.arcile.core.storage.domain.SaveDestinationBrowser
import dev.qtremors.arcile.core.storage.domain.VolumeRepository
import dev.qtremors.arcile.core.vault.domain.DirectoryId
import dev.qtremors.arcile.core.vault.domain.NodeId
import dev.qtremors.arcile.core.vault.domain.VaultBoundaryTransferCoordinator
import dev.qtremors.arcile.core.vault.domain.VaultCatalog
import dev.qtremors.arcile.core.vault.domain.VaultExternalAccessManager
import dev.qtremors.arcile.core.vault.domain.VaultFileSystem
import dev.qtremors.arcile.core.vault.domain.VaultHealthService
import dev.qtremors.arcile.core.vault.domain.VaultImportCoordinator
import dev.qtremors.arcile.core.vault.domain.VaultId
import dev.qtremors.arcile.core.vault.domain.VaultLocationKind
import dev.qtremors.arcile.core.vault.domain.VaultNodeCapabilities
import dev.qtremors.arcile.core.vault.domain.VaultNodeKind
import dev.qtremors.arcile.core.vault.domain.VaultNodeMetadata
import dev.qtremors.arcile.core.vault.domain.VaultNodeRef
import dev.qtremors.arcile.core.vault.domain.VaultPage
import dev.qtremors.arcile.core.vault.domain.VaultRepository
import dev.qtremors.arcile.core.vault.domain.VaultSecurityPreferences
import dev.qtremors.arcile.core.vault.domain.VaultSecuritySettings
import dev.qtremors.arcile.core.vault.domain.VaultSessionManager
import dev.qtremors.arcile.core.vault.domain.VaultSummary
import dev.qtremors.arcile.core.vault.domain.VaultThumbnailCache
import dev.qtremors.arcile.core.vault.domain.VaultTransferCoordinator
import dev.qtremors.arcile.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnlyFilesViewModelLockTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `exit waits for one vault lock before navigating`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = repository()
            val events = mutableListOf<String>()
            coEvery { repository.lockAll() } coAnswers {
                delay(100)
                events += "locked"
            }
            val viewModel = viewModel(repository)

            viewModel.lockAllAndExit { events += "exited" }
            viewModel.lockAllAndExit { events += "duplicate exit" }
            advanceUntilIdle()

            assertEquals(listOf("locked", "exited"), events)
            coVerify(exactly = 1) { repository.lockAll() }
        }

    @Test
    fun `repository lock clears vault metadata and previews`() =
        runTest(mainDispatcherRule.dispatcher) {
            val vaultId = VaultId.of("vault")
            val summary = VaultSummary(vaultId, "Vault", VaultLocationKind.APP_PRIVATE, 0L, true)
            val unlockedIds = MutableStateFlow(setOf(vaultId))
            val repository = repository().also {
                every { it.vaults } returns MutableStateFlow(listOf(summary))
                every { it.unlockedVaultIds } returns unlockedIds
            }
            val node = VaultNodeMetadata(
                VaultNodeRef(vaultId, NodeId.of("photo"), DirectoryId.Root, VaultNodeCapabilities()),
                "photo.jpg", VaultNodeKind.FILE, 1L, 0L, 1L
            )
            val fileSystem = mockk<VaultFileSystem> {
                coEvery { listDirectory(vaultId, DirectoryId.Root, any()) } returns
                    Result.success(VaultPage(listOf(node), null, 1L))
            }
            val viewModel = viewModel(repository, fileSystem)
            assertTrue(viewModel.openVault(summary))
            advanceUntilIdle()
            viewModel.showProperties(listOf(node))
            viewModel.open(node)
            assertEquals(node, viewModel.state.value.viewer)

            unlockedIds.value = emptySet()
            advanceUntilIdle()

            val state = viewModel.state.value
            assertNull(state.selectedVaultId)
            assertNull(state.viewer)
            assertTrue(state.properties.isEmpty())
            assertTrue(state.viewerQueue.isEmpty())
            assertTrue(state.nodes.isEmpty())
        }

    private fun repository() = mockk<VaultRepository>(relaxed = true) {
        every { vaults } returns MutableStateFlow(emptyList())
        every { unlockedVaultIds } returns MutableStateFlow(emptySet())
    }

    private fun viewModel(
        repository: VaultRepository,
        fileSystem: VaultFileSystem = mockk(relaxed = true)
    ): OnlyFilesViewModel {
        val transferCoordinator = mockk<VaultTransferCoordinator>(relaxed = true) {
            every { progress } returns emptyFlow()
        }
        val boundaryCoordinator = mockk<VaultBoundaryTransferCoordinator>(relaxed = true) {
            every { progress } returns emptyFlow()
        }
        val importCoordinator = mockk<VaultImportCoordinator>(relaxed = true) {
            every { activeImports } returns MutableStateFlow(emptyMap())
            every { events } returns emptyFlow()
        }
        val securityPreferences = mockk<VaultSecurityPreferences>(relaxed = true) {
            every { settings } returns MutableStateFlow(VaultSecuritySettings())
        }
        return OnlyFilesViewModel(
            repository = repository,
            fileSystem = fileSystem,
            transferCoordinator = transferCoordinator,
            importCoordinator = importCoordinator,
            externalAccessManager = mockk<VaultExternalAccessManager>(relaxed = true),
            healthService = mockk<VaultHealthService>(relaxed = true),
            destinationBrowser = mockk<SaveDestinationBrowser>(relaxed = true),
            localFileBrowser = mockk<FileBrowserRepository>(relaxed = true),
            volumeRepository = mockk<VolumeRepository>(relaxed = true),
            securityPreferences = securityPreferences,
            catalog = mockk<VaultCatalog>(relaxed = true),
            sessionManager = mockk<VaultSessionManager>(relaxed = true),
            boundaryTransferCoordinator = boundaryCoordinator,
            vaultThumbnailCache = mockk<VaultThumbnailCache>(relaxed = true)
        )
    }
}
