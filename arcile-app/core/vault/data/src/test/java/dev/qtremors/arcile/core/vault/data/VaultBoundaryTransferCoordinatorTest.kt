package dev.qtremors.arcile.core.vault.data

import dev.qtremors.arcile.core.vault.domain.DirectoryId
import dev.qtremors.arcile.core.vault.domain.NodeId
import dev.qtremors.arcile.core.vault.domain.VaultBiometricChallenge
import dev.qtremors.arcile.core.vault.domain.VaultCancellationSignal
import dev.qtremors.arcile.core.vault.domain.VaultConflictDecision
import dev.qtremors.arcile.core.vault.domain.VaultConflictResolver
import dev.qtremors.arcile.core.vault.domain.VaultFileSystem
import dev.qtremors.arcile.core.vault.domain.VaultId
import dev.qtremors.arcile.core.vault.domain.VaultItemOutcome
import dev.qtremors.arcile.core.vault.domain.VaultKeyLease
import dev.qtremors.arcile.core.vault.domain.VaultLeasePurpose
import dev.qtremors.arcile.core.vault.domain.VaultListOptions
import dev.qtremors.arcile.core.vault.domain.VaultNodeCapabilities
import dev.qtremors.arcile.core.vault.domain.VaultNodeKind
import dev.qtremors.arcile.core.vault.domain.VaultNodeMetadata
import dev.qtremors.arcile.core.vault.domain.VaultNodeRef
import dev.qtremors.arcile.core.vault.domain.VaultPage
import dev.qtremors.arcile.core.vault.domain.VaultSearchHit
import dev.qtremors.arcile.core.vault.domain.VaultSearchQuery
import dev.qtremors.arcile.core.vault.domain.VaultSeekableReader
import dev.qtremors.arcile.core.vault.domain.VaultSessionManager
import dev.qtremors.arcile.core.vault.domain.VaultUnlockOptions
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultBoundaryTransferCoordinatorTest {
    @Test
    fun `nested directory export is iterative and a move deletes only after publication`() = runTest {
        val destination = Files.createTempDirectory("arcile-deep-export").toFile()
        val fileSystem = DeepDirectoryFileSystem(depth = 20)
        val sessions = RecordingSessionManager()
        val coordinator = DefaultVaultBoundaryTransferCoordinator(fileSystem, sessions)

        val result = coordinator.exportToDestination(
            reservation = coordinator.prepareExport(listOf(fileSystem.root.ref)).getOrThrow(),
            destinationPath = destination.absolutePath,
            move = true,
            conflicts = VaultConflictResolver { VaultConflictDecision.REPLACE },
            cancellation = VaultCancellationSignal { false }
        )

        assertEquals(result.items.single().failure?.cause?.stackTraceToString(), VaultItemOutcome.COMPLETED, result.items.single().outcome)
        assertEquals(listOf(fileSystem.root.ref), fileSystem.deleted)
        assertEquals(1, sessions.acquired)
        assertEquals(1, sessions.closed)
        assertFalse(destination.listFiles().orEmpty().any { it.name.startsWith(".arcile-") })
        var deepest = destination.resolve("Folder")
        repeat(20) { deepest = deepest.resolve("Level-${it + 1}") }
        assertTrue(deepest.isDirectory)
        destination.deleteRecursively()
    }

    @Test
    fun `cancelled export removes staging and preserves source`() = runTest {
        val destination = Files.createTempDirectory("arcile-cancelled-export").toFile()
        val fileSystem = DeepDirectoryFileSystem(depth = 1_000)
        val coordinator = DefaultVaultBoundaryTransferCoordinator(fileSystem, RecordingSessionManager())
        var checks = 0

        val result = coordinator.exportToDestination(
            reservation = coordinator.prepareExport(listOf(fileSystem.root.ref)).getOrThrow(),
            destinationPath = destination.absolutePath,
            move = true,
            conflicts = VaultConflictResolver { VaultConflictDecision.REPLACE },
            cancellation = VaultCancellationSignal { ++checks > 100 }
        )

        assertEquals(result.items.single().failure?.toString(), VaultItemOutcome.ROLLED_BACK, result.items.single().outcome)
        assertTrue(fileSystem.deleted.isEmpty())
        assertTrue(destination.listFiles().orEmpty().isEmpty())
        destination.deleteRecursively()
    }

    @Test
    fun `skip and keep both conflicts preserve existing destination`() = runTest {
        val destination = Files.createTempDirectory("arcile-conflict-export").toFile()
        destination.resolve("Folder").mkdir()
        val fileSystem = DeepDirectoryFileSystem(depth = 0)
        val coordinator = DefaultVaultBoundaryTransferCoordinator(fileSystem, RecordingSessionManager())

        val skipped = coordinator.exportToDestination(
            coordinator.prepareExport(listOf(fileSystem.root.ref)).getOrThrow(),
            destination.absolutePath,
            move = true,
            conflicts = VaultConflictResolver { VaultConflictDecision.SKIP },
            cancellation = VaultCancellationSignal { false }
        )
        val kept = coordinator.exportToDestination(
            coordinator.prepareExport(listOf(fileSystem.root.ref)).getOrThrow(),
            destination.absolutePath,
            move = false,
            conflicts = VaultConflictResolver { VaultConflictDecision.KEEP_BOTH },
            cancellation = VaultCancellationSignal { false }
        )

        assertEquals(skipped.items.single().failure?.toString(), VaultItemOutcome.SKIPPED, skipped.items.single().outcome)
        assertTrue(fileSystem.deleted.isEmpty())
        assertEquals(VaultItemOutcome.COMPLETED, kept.items.single().outcome)
        assertEquals(listOf("Folder", "Folder (1)"), destination.listFiles().orEmpty().map(File::getName).sorted())
        destination.deleteRecursively()
    }

    private class DeepDirectoryFileSystem(depth: Int) : VaultFileSystem {
        private val vaultId = VaultId.of("deep-vault")
        private val directories = (0..depth).map(::directory)
        val root: VaultNodeMetadata = directories.first()
        val deleted = mutableListOf<VaultNodeRef>()

        override suspend fun listDirectory(
            vaultId: VaultId,
            directoryId: DirectoryId,
            options: VaultListOptions
        ): Result<VaultPage<VaultNodeMetadata>> {
            val index = directoryId.value.removePrefix("directory-").toInt()
            return Result.success(VaultPage(listOfNotNull(directories.getOrNull(index + 1)), null, 1L))
        }

        override suspend fun metadata(ref: VaultNodeRef): Result<VaultNodeMetadata> =
            Result.success(directories.single { it.ref.nodeId == ref.nodeId })

        override suspend fun deletePermanently(ref: VaultNodeRef): Result<Unit> =
            Result.success(Unit).also { deleted += ref }

        override suspend fun search(
            vaultId: VaultId,
            directoryId: DirectoryId,
            query: VaultSearchQuery
        ): Result<VaultPage<VaultSearchHit>> = unsupported()

        override suspend fun createDirectory(
            vaultId: VaultId,
            parentId: DirectoryId,
            name: String
        ): Result<VaultNodeMetadata> = unsupported()

        override suspend fun createEmptyFile(
            vaultId: VaultId,
            parentId: DirectoryId,
            name: String,
            mimeType: String?
        ): Result<VaultNodeMetadata> = unsupported()

        override suspend fun rename(ref: VaultNodeRef, newName: String): Result<VaultNodeMetadata> = unsupported()
        override fun openReader(ref: VaultNodeRef): Result<VaultSeekableReader> = unsupported()

        private fun directory(index: Int): VaultNodeMetadata {
            val directoryId = DirectoryId.of("directory-$index")
            return VaultNodeMetadata(
                ref = VaultNodeRef(
                    vaultId = vaultId,
                    nodeId = NodeId.of("node-$index"),
                    parentId = if (index == 0) DirectoryId.Root else DirectoryId.of("directory-${index - 1}"),
                    capabilities = VaultNodeCapabilities(),
                    directoryId = directoryId
                ),
                name = if (index == 0) "Folder" else "Level-$index",
                kind = VaultNodeKind.DIRECTORY,
                sizeBytes = 0L,
                modifiedAtMillis = 1L,
                revision = 1L
            )
        }

        private fun <T> unsupported(): Result<T> = Result.failure(UnsupportedOperationException())
    }

    private class RecordingSessionManager : VaultSessionManager {
        var acquired = 0
        var closed = 0

        override fun acquireLease(vaultId: VaultId, purpose: VaultLeasePurpose): Result<VaultKeyLease> {
            acquired++
            return Result.success(object : VaultKeyLease {
                override val vaultId = vaultId
                override val purpose = purpose
                override var isClosed = false
                override fun close() {
                    if (!isClosed) {
                        isClosed = true
                        closed++
                    }
                }
            })
        }

        override suspend fun unlock(vaultId: VaultId, options: VaultUnlockOptions) = unsupported<Unit>()
        override suspend fun lockInteractive(vaultId: VaultId) = Unit
        override suspend fun lockAllInteractive() = Unit
        override suspend fun changePassword(
            vaultId: VaultId,
            currentPassword: CharArray,
            newPassword: CharArray,
            weakPasswordConfirmed: Boolean
        ) = unsupported<Unit>()
        override suspend fun prepareBiometricEnrollment(vaultId: VaultId, password: CharArray) =
            unsupported<VaultBiometricChallenge>()
        override suspend fun prepareBiometricUnlock(vaultId: VaultId) = unsupported<VaultBiometricChallenge>()
        override suspend fun removeBiometric(vaultId: VaultId) = unsupported<Unit>()

        private fun <T> unsupported(): Result<T> = Result.failure(UnsupportedOperationException())
    }
}
