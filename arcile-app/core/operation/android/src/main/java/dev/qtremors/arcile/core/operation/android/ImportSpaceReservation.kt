package dev.qtremors.arcile.core.operation.android

import android.content.Context
import android.os.storage.StorageManager
import dev.qtremors.arcile.core.storage.domain.ArcileError
import dev.qtremors.arcile.core.ui.R
import java.io.File
import java.io.IOException

internal fun interface ImportSpaceReservation {
    fun verifyBeforeCommit()
}

internal fun interface ImportSpaceAllocator {
    fun reserve(
        destination: File,
        knownContentBytes: Long,
        hasUnknownSizes: Boolean
    ): ImportSpaceReservation
}

internal class AndroidImportSpaceAllocator(
    context: Context,
    private val storageManager: StorageManager = requireNotNull(
        context.applicationContext.getSystemService(StorageManager::class.java)
    )
) : ImportSpaceAllocator {
    private val appContext = context.applicationContext

    override fun reserve(
        destination: File,
        knownContentBytes: Long,
        hasUnknownSizes: Boolean
    ): ImportSpaceReservation {
        val plannedContentBytes = if (hasUnknownSizes) {
            (knownContentBytes + UNKNOWN_SIZE_INITIAL_RESERVATION_BYTES)
                .coerceAtMost(MAX_IMPORT_BYTES)
        } else {
            knownContentBytes
        }
        val requestedBytes = plannedContentBytes + FREE_SPACE_SAFETY_BUFFER_BYTES
        val storageUuid = try {
            storageManager.getUuidForPath(destination)
        } catch (error: IOException) {
            throw insufficientSpace(error)
        }

        ensureAllocatable(storageUuid, requestedBytes)
        try {
            storageManager.allocateBytes(storageUuid, requestedBytes)
        } catch (error: IOException) {
            throw insufficientSpace(error)
        }

        return ImportSpaceReservation {
            ensureAllocatable(storageUuid, FREE_SPACE_SAFETY_BUFFER_BYTES)
        }
    }

    private fun ensureAllocatable(storageUuid: java.util.UUID, requiredBytes: Long) {
        val availableBytes = try {
            storageManager.getAllocatableBytes(storageUuid)
        } catch (error: IOException) {
            throw insufficientSpace(error)
        }
        if (availableBytes < requiredBytes) throw insufficientSpace()
    }

    private fun insufficientSpace(cause: Throwable? = null): ArcileError.InsufficientSpace =
        ArcileError.InsufficientSpace(
            cause ?: IOException(appContext.getString(R.string.save_to_arcile_insufficient_space))
        )
}

private const val UNKNOWN_SIZE_INITIAL_RESERVATION_BYTES = 64L * 1024L * 1024L
