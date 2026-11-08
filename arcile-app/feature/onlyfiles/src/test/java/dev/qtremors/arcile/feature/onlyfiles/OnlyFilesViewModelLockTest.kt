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

    @Test
    fun `clipboard survives a vault switch and clears when its source locks`() =
        runTest(mainDispatcherRule.dispatcher) {
            val sourceId = VaultId.of("source")
            val destinationId = VaultId.of("destination")
            val source = VaultSummary(sourceId, "Source", VaultLocationKind.APP_PRIVATE, 0L, true)
            val destination = VaultSummary(destinationId, "Destination", VaultLocationKind.APP_PRIVATE, 0L, true)
            val unlockedIds = MutableStateFlow(setOf(sourceId, destinationId))
            val repository = repository().also {
                every { it.vaults } returns MutableStateFlow(listOf(source, destination))
                every { it.unlockedVaultIds } returns unlockedIds
            }
            val node = VaultNodeMetadata(
                VaultNodeRef(sourceId, NodeId.of("photo"), DirectoryId.Root, VaultNodeCapabilities()),
                "photo.jpg", VaultNodeKind.FILE, 1L, 0L, 1L
            )
            val fileSystem = mockk<VaultFileSystem> {
                coEvery { listDirectory(sourceId, DirectoryId.Root, any()) } returns
                    Result.success(VaultPage(listOf(node), null, 1L))
                coEvery { listDirectory(destinationId, DirectoryId.Root, any()) } returns
                    Result.success(VaultPage(emptyList(), null, 1L))
            }
            val viewModel = viewModel(repository, fileSystem)
            assertTrue(viewModel.openVault(source))
            advanceUntilIdle()
            viewModel.copy(listOf(node))
            assertTrue(viewModel.navigateUp())
            assertNull(viewModel.state.value.selectedVaultId)
            assertEquals(listOf(node.ref), viewModel.state.value.clipboard?.sources)
            assertTrue(viewModel.openVault(destination))
            advanceUntilIdle()
            assertEquals(listOf(node.ref), viewModel.state.value.clipboard?.sources)

            unlockedIds.value = setOf(destinationId)
            advanceUntilIdle()
            assertNull(viewModel.state.value.clipboard)
            assertEquals(destinationId, viewModel.state.value.selectedVaultId)
        }

    @Test
    fun `completion actions are single use and stale callbacks cannot affect newer feedback or locked mounts`() =
        runTest(mainDispatcherRule.dispatcher) {
            val vault = VaultId.of("feedback-vault")
            val summary = VaultSummary(vault, "Private", VaultLocationKind.APP_PRIVATE, 0L, true)
            val unlocked = MutableStateFlow(setOf(vault))
            val repository = repository().also {
                every { it.vaults } returns MutableStateFlow(listOf(summary))
                every { it.unlockedVaultIds } returns unlocked
            }
            val node = VaultNodeMetadata(
                VaultNodeRef(vault, NodeId.of("file"), DirectoryId.Root, VaultNodeCapabilities()),
                "private.txt", VaultNodeKind.FILE, 1, 0, 0
            )
            val fileSystem = mockk<VaultFileSystem> {
                coEvery { listDirectory(vault, DirectoryId.Root, any()) } returns Result.success(VaultPage(listOf(node), null, 1))
            }
            val boundary = mockk<VaultBoundaryTransferCoordinator> {
                every { progress } returns emptyFlow()
                every { prepareExport(listOf(node.ref)) } answers {
                    Result.success(mockk<dev.qtremors.arcile.core.vault.domain.VaultBoundaryTransferReservation>(relaxed = true) {
                        every { sources } returns listOf(node.ref)
                    })
                }
                coEvery { exportToDestination(any(), "/storage/Exports", false, any(), any()) } returns
                    dev.qtremors.arcile.core.vault.domain.VaultBatchResult(listOf(
                        dev.qtremors.arcile.core.vault.domain.VaultItemResult(
                            node.ref.backendIdentity, node.name, dev.qtremors.arcile.core.vault.domain.VaultItemOutcome.COMPLETED
                        )
                    ))
            }
            val viewModel = viewModel(repository, fileSystem, boundary)
            viewModel.openVault(summary)
            advanceUntilIdle()
            val opened = mutableListOf<String?>()
            fun export() {
                assertTrue(viewModel.beginExportSelection(listOf(node)))
                viewModel.finishExportSelection("/storage/Exports", move = false)
            }

            export()
            advanceUntilIdle()
            val first = requireNotNull(viewModel.state.value.operationFeedback)
            viewModel.revealOperationDestination(first, opened::add)
            viewModel.revealOperationDestination(first, opened::add)
            assertEquals(listOf("/storage/Exports"), opened)

            export()
            advanceUntilIdle()
            val replaced = requireNotNull(viewModel.state.value.operationFeedback)
            viewModel.copy(listOf(node))
            val latest = requireNotNull(viewModel.state.value.operationFeedback)
            viewModel.dismissOperationFeedback(replaced.id)
            viewModel.revealOperationDestination(replaced, opened::add)
            assertEquals(latest, viewModel.state.value.operationFeedback)
            assertEquals(1, opened.size)

            export()
            advanceUntilIdle()
            val beforeLock = requireNotNull(viewModel.state.value.operationFeedback)
            unlocked.value = emptySet()
            advanceUntilIdle()
            viewModel.revealOperationDestination(beforeLock, opened::add)
            assertNull(viewModel.state.value.operationFeedback)
            assertEquals(1, opened.size)
        }

    @Test
    fun `password keyboard and retention changes persist through the preference interface and update UI state`() =
        runTest(mainDispatcherRule.dispatcher) {
            val settings = MutableStateFlow(VaultSecuritySettings())
            val preferences = mockk<VaultSecurityPreferences>(relaxed = true) {
                every { this@mockk.settings } returns settings
                coEvery { setNumericPasswordKeyboard(any()) } coAnswers {
                    settings.value = settings.value.copy(numericPasswordKeyboard = firstArg())
                }
                coEvery { setLockOnScreenOff(any()) } coAnswers {
                    settings.value = settings.value.copy(lockOnScreenOff = firstArg())
                }
                coEvery { setBackgroundLockDelay(any()) } coAnswers {
                    settings.value = settings.value.copy(backgroundLockDelay = firstArg())
                }
            }
            val viewModel = viewModel(repository(), suppliedPreferences = preferences)
            advanceUntilIdle()
            assertEquals(false, viewModel.state.value.numericPasswordKeyboard)
            assertEquals(true, viewModel.state.value.lockOnScreenOff)
            assertEquals(dev.qtremors.arcile.core.vault.domain.VaultBackgroundLockDelay.IMMEDIATE, viewModel.state.value.backgroundLockDelay)

            viewModel.setNumericPasswordKeyboard(true)
            viewModel.setLockOnScreenOff(false)
            viewModel.setBackgroundLockDelay(dev.qtremors.arcile.core.vault.domain.VaultBackgroundLockDelay.ONE_MINUTE)
            advanceUntilIdle()

            coVerify(exactly = 1) { preferences.setNumericPasswordKeyboard(true) }
            coVerify(exactly = 1) { preferences.setLockOnScreenOff(false) }
            coVerify(exactly = 1) { preferences.setBackgroundLockDelay(dev.qtremors.arcile.core.vault.domain.VaultBackgroundLockDelay.ONE_MINUTE) }
            assertEquals(true, viewModel.state.value.numericPasswordKeyboard)
            assertEquals(false, viewModel.state.value.lockOnScreenOff)
            assertEquals(dev.qtremors.arcile.core.vault.domain.VaultBackgroundLockDelay.ONE_MINUTE, viewModel.state.value.backgroundLockDelay)

            val recreated = viewModel(repository(), suppliedPreferences = preferences)
            advanceUntilIdle()
            assertEquals(viewModel.state.value.numericPasswordKeyboard, recreated.state.value.numericPasswordKeyboard)
            assertEquals(viewModel.state.value.backgroundLockDelay, recreated.state.value.backgroundLockDelay)
            assertEquals(viewModel.state.value.lockOnScreenOff, recreated.state.value.lockOnScreenOff)
        }

    @Test
    fun `failed unlock presents localized authentication feedback instead of a backend exception`() =
        runTest(mainDispatcherRule.dispatcher) {
            val vault = VaultId.of("private-vault")
            val repository = repository()
            coEvery { repository.unlock(vault, any()) } returns Result.failure(
                dev.qtremors.arcile.core.vault.domain.VaultFailure.AuthenticationFailed()
            )
            val viewModel = viewModel(repository)
            viewModel.unlock(vault, "wrong-password")
            advanceUntilIdle()

            assertEquals(
                dev.qtremors.arcile.core.presentation.UiText.StringResource(R.string.onlyfiles_error_password),
                viewModel.state.value.operationFeedback?.message
            )
            assertNull(viewModel.state.value.selectedVaultId)
            assertNull(viewModel.state.value.message)
        }

    private fun repository() = mockk<VaultRepository>(relaxed = true) {
        every { vaults } returns MutableStateFlow(emptyList())
        every { unlockedVaultIds } returns MutableStateFlow(emptySet())
    }

    private fun viewModel(
        repository: VaultRepository,
        fileSystem: VaultFileSystem = mockk(relaxed = true),
        suppliedBoundary: VaultBoundaryTransferCoordinator? = null,
        suppliedPreferences: VaultSecurityPreferences? = null
    ): OnlyFilesViewModel {
        val transferCoordinator = mockk<VaultTransferCoordinator>(relaxed = true) {
            every { progress } returns emptyFlow()
        }
        val boundaryCoordinator = suppliedBoundary ?: mockk<VaultBoundaryTransferCoordinator>(relaxed = true) {
            every { progress } returns emptyFlow()
        }
        val importCoordinator = mockk<VaultImportCoordinator>(relaxed = true) {
            every { activeImports } returns MutableStateFlow(emptyMap())
            every { events } returns emptyFlow()
        }
        val securityPreferences = suppliedPreferences ?: mockk<VaultSecurityPreferences>(relaxed = true) {
            every { settings } returns MutableStateFlow(VaultSecuritySettings())
        }
        return OnlyFilesViewModel(
            repository = repository,
            fileSystem = fileSystem,
            transferCoordinator = transferCoordinator,
            importCoordinator = importCoordinator,
            externalAccessManager = mockk<VaultExternalAccessManager>(relaxed = true),
            healthService = mockk<VaultHealthService>(relaxed = true),
            backupService = mockk(relaxed = true),
            destinationBrowser = mockk<SaveDestinationBrowser>(relaxed = true),
            localFileBrowser = mockk<FileBrowserRepository>(relaxed = true),
            volumeRepository = mockk<VolumeRepository>(relaxed = true),
            securityPreferences = securityPreferences,
            catalog = mockk<VaultCatalog>(relaxed = true),
            sessionManager = mockk<VaultSessionManager>(relaxed = true),
            boundaryTransferCoordinator = boundaryCoordinator,
            vaultThumbnailCache = mockk<VaultThumbnailCache>(relaxed = true),
            operationRecovery = mockk {
                coEvery { list(any()) } returns Result.success(emptyList())
            }
        )
    }
}
