package dev.qtremors.arcile.feature.importing

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import dagger.hilt.android.AndroidEntryPoint
import dev.qtremors.arcile.core.operation.BulkFileOperationCoordinator
import dev.qtremors.arcile.core.operation.SaveToArcileImportItem
import dev.qtremors.arcile.core.storage.domain.SaveDestinationPreferencesStore
import dev.qtremors.arcile.core.storage.domain.VolumeRepository
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.theme.ArcileTheme
import dev.qtremors.arcile.core.ui.theme.ThemeState
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.first

@AndroidEntryPoint
class SaveToArcileActivity : ComponentActivity() {
    @Inject
    lateinit var volumeRepository: VolumeRepository

    @Inject
    lateinit var bulkFileOperationCoordinator: BulkFileOperationCoordinator

    @Inject
    lateinit var browserPreferencesStore: SaveDestinationPreferencesStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val preflight = IncomingShareReader.preflightFromIntent(this, intent)
        if (preflight.accepted.isEmpty()) {
            Toast.makeText(
                this,
                preflight.messageOrDefault(getString(R.string.save_to_arcile_no_files)),
                Toast.LENGTH_LONG
            ).show()
            finish()
            return
        }

        setContent {
            ArcileTheme(themeState = ThemeState()) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    SaveToArcileRoute(
                        incoming = preflight.accepted,
                        loadVolumes = {
                            volumeRepository.getStorageVolumes().getOrElse { emptyList() }
                        },
                        loadDefaultPath = {
                            browserPreferencesStore.saveDestinationPreferencesFlow
                                .first()
                                .defaultPath
                        },
                        saveDefaultPath = {
                            browserPreferencesStore.updateDefaultSaveToArcilePath(it)
                        },
                        copyTo = { destination ->
                            enqueueIncomingImport(destination, preflight.accepted)
                        },
                        onCancel = ::finish,
                        onDefaultSaved = ::showDefaultSaved,
                        onFinished = ::handleImportStarted,
                        onFailed = ::showImportFailure
                    )
                }
            }
        }
    }

    private fun enqueueIncomingImport(
        destination: File,
        incoming: List<IncomingSharedFile>
    ): Result<SaveIncomingResult> {
        persistReadableUriPermissions(incoming)
        val started = bulkFileOperationCoordinator.startImportOperation(
            destinationPath = destination.absolutePath,
            importItems = incoming.map { item ->
                SaveToArcileImportItem(
                    uri = item.uri.toString(),
                    displayName = item.displayName,
                    sizeBytes = item.sizeBytes,
                    requiresCountedStream = item.requiresCountedStream
                )
            }
        )
        return if (started) {
            Result.success(SaveIncomingResult(savedCount = 0, failures = emptyList(), queued = true))
        } else {
            Result.failure(IllegalStateException(getString(R.string.file_operation_already_running)))
        }
    }

    private fun persistReadableUriPermissions(incoming: List<IncomingSharedFile>) {
        val requiredFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        if (intent.flags and requiredFlags != requiredFlags) return
        incoming.forEach { item ->
            runCatching {
                contentResolver.takePersistableUriPermission(
                    item.uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }
    }

    private fun showDefaultSaved() {
        Toast.makeText(
            this,
            getString(R.string.save_to_arcile_default_saved),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun handleImportStarted(result: SaveIncomingResult) {
        Toast.makeText(this, result.userMessage(this), Toast.LENGTH_LONG).show()
        if (result.queued || result.savedCount > 0 || result.failures.isEmpty()) finish()
    }

    private fun showImportFailure(error: Throwable) {
        Toast.makeText(
            this,
            getString(R.string.save_to_arcile_failed, error.message ?: ""),
            Toast.LENGTH_LONG
        ).show()
    }
}
