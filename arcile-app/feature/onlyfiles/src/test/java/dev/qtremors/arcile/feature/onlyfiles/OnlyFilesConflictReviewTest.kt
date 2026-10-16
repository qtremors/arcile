package dev.qtremors.arcile.feature.onlyfiles

import dev.qtremors.arcile.core.vault.domain.VaultConflict
import dev.qtremors.arcile.core.vault.domain.VaultConflictDecision
import dev.qtremors.arcile.core.vault.domain.VaultId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnlyFilesConflictReviewTest {
    @Test
    fun `reviewed equal names are keyed by item and leave nested choices independent`() = runTest {
        val state = mounted()
        val review = OnlyFilesConflictReview(state)
        val resolver = review.resolver(1L, exporting = true, isCancelled = { false })
        val first = conflict("first")
        val second = conflict("second")
        val result = async { resolver.review(listOf(first, second)) }
        runCurrent()

        val prompt = requireNotNull(state.value.pendingConflict)
        assertEquals(listOf(first, second), prompt.conflicts)
        assertTrue(prompt.exporting)
        assertFalse(prompt.allowFuture)
        review.resolve(prompt.requestId, VaultConflictResponse(mapOf(
            0 to VaultConflictDecision.KEEP_BOTH,
            1 to VaultConflictDecision.REPLACE
        )))
        assertEquals(
            mapOf(first to VaultConflictDecision.KEEP_BOTH, second to VaultConflictDecision.REPLACE),
            result.await()
        )
        assertNull(state.value.pendingConflict)

        val nested = async { resolver.decide(conflict("nested")) }
        runCurrent()
        val nestedPrompt = requireNotNull(state.value.pendingConflict)
        assertTrue(nestedPrompt.allowFuture)
        review.resolve(prompt.requestId, VaultConflictResponse(mapOf(0 to VaultConflictDecision.REPLACE)))
        assertFalse(nested.isCompleted)
        review.resolve(nestedPrompt.requestId, VaultConflictResponse(mapOf(0 to VaultConflictDecision.SKIP)))
        assertEquals(VaultConflictDecision.SKIP, nested.await())
    }

    @Test
    fun `apply merge to later conflicts never treats a mixed conflict as a replacement`() = runTest {
        val state = mounted()
        val review = OnlyFilesConflictReview(state)
        val resolver = review.resolver(1L, exporting = false, isCancelled = { false })
        val folder = conflict("folder", folder = true)
        val first = async { resolver.decide(folder) }
        runCurrent()
        val request = requireNotNull(state.value.pendingConflict)
        review.resolve(request.requestId, VaultConflictResponse(
            mapOf(0 to VaultConflictDecision.MERGE_DIRECTORIES), VaultConflictDecision.MERGE_DIRECTORIES
        ))
        assertEquals(VaultConflictDecision.MERGE_DIRECTORIES, first.await())
        assertEquals(VaultConflictDecision.MERGE_DIRECTORIES, resolver.decide(conflict("other", folder = true)))
        assertNull(state.value.pendingConflict)

        val file = conflict("file")
        val next = async { resolver.decide(file) }
        runCurrent()
        val filePrompt = requireNotNull(state.value.pendingConflict)
        assertEquals(listOf(file), filePrompt.conflicts)
        review.resolve(filePrompt.requestId, VaultConflictResponse(mapOf(0 to VaultConflictDecision.MERGE_DIRECTORIES)))
        assertEquals(VaultConflictDecision.SKIP, next.await())
    }

    @Test
    fun `locking the mount closes the review and skips conflicts that arrive later`() = runTest {
        val state = mounted()
        val review = OnlyFilesConflictReview(state)
        val resolver = review.resolver(1L, exporting = false, isCancelled = { false })
        val conflict = conflict("private")
        val pending = async { resolver.review(listOf(conflict)) }
        runCurrent()
        assertEquals(listOf(conflict), state.value.pendingConflict?.conflicts)

        state.value = OnlyFilesUiState(mountGeneration = 2L)
        review.dismiss()

        assertEquals(mapOf(conflict to VaultConflictDecision.SKIP), pending.await())
        assertEquals(VaultConflictDecision.SKIP, resolver.decide(conflict("later")))
        assertNull(state.value.pendingConflict)
    }

    @Test
    fun `cancelled transfers never open a conflict prompt`() = runTest {
        val state = mounted()
        val resolver = OnlyFilesConflictReview(state).resolver(1L, exporting = true, isCancelled = { true })
        val conflict = conflict("cancelled")

        assertEquals(mapOf(conflict to VaultConflictDecision.SKIP), resolver.review(listOf(conflict)))
        assertEquals(VaultConflictDecision.SKIP, resolver.decide(conflict))
        assertNull(state.value.pendingConflict)
    }

    @Test
    fun `replace all only applies to the same source and destination kinds`() {
        val files = conflict("files")
        val folders = conflict("folders", folder = true)
        val fileOverFolder = files.copy(destinationIsDirectory = true)
        val folderOverFile = folders.copy(destinationIsDirectory = false)

        assertTrue(VaultConflictDecision.REPLACE.canApplyTo(files, conflict("other-file")))
        assertFalse(VaultConflictDecision.REPLACE.canApplyTo(files, folders))
        assertFalse(VaultConflictDecision.REPLACE.canApplyTo(files, fileOverFolder))
        assertFalse(VaultConflictDecision.REPLACE.canApplyTo(folders, folderOverFile))
        assertTrue(VaultConflictDecision.SKIP.canApplyTo(files, folders))
        assertTrue(VaultConflictDecision.KEEP_BOTH.canApplyTo(folders, fileOverFolder))
    }

    private fun mounted() = MutableStateFlow(OnlyFilesUiState(selectedVaultId = VaultId.of("vault"), mountGeneration = 1L))

    private fun conflict(id: String, folder: Boolean = false) =
        VaultConflict("same name", "same name", folder, folder, sourceIdentity = id)
}
