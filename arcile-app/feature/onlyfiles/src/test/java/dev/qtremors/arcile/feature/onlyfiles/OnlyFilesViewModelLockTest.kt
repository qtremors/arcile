package dev.qtremors.arcile.feature.onlyfiles

import dev.qtremors.arcile.core.storage.domain.FileBrowserRepository
import dev.qtremors.arcile.core.storage.domain.SaveDestinationBrowser
import dev.qtremors.arcile.core.storage.domain.VolumeRepository
import dev.qtremors.arcile.core.vault.domain.VaultBoundaryTransferCoordinator
import dev.qtremors.arcile.core.vault.domain.VaultCatalog
import dev.qtremors.arcile.core.vault.domain.VaultExternalAccessManager
import dev.qtremors.arcile.core.vault.domain.VaultFileSystem
import dev.qtremors.arcile.core.vault.domain.VaultHealthService
import dev.qtremors.arcile.core.vault.domain.VaultImportCoordinator
import dev.qtremors.arcile.core.vault.domain.VaultRepository
import dev.qtremors.arcile.core.vault.domain.VaultSecurityPreferences
import dev.qtremors.arcile.core.vault.domain.VaultSecuritySettings
import dev.qtremors.arcile.core.vault.domain.VaultSessionManager
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

    private fun repository() = mockk<VaultRepository>(relaxed = true) {
        every { vaults } returns MutableStateFlow(emptyList())
        every { unlockedVaultIds } returns MutableStateFlow(emptySet())
    }

    private fun viewModel(repository: VaultRepository): OnlyFilesViewModel {
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
            fileSystem = mockk<VaultFileSystem>(relaxed = true),
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
