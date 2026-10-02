package dev.qtremors.arcile.feature.importing

import android.content.Intent
import android.os.Bundle
import dev.qtremors.arcile.core.ui.showArcileToast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import dagger.hilt.android.AndroidEntryPoint
import dev.qtremors.arcile.core.operation.BulkFileOperationCoordinator
import dev.qtremors.arcile.core.operation.SaveToArcileImportItem
import dev.qtremors.arcile.core.operation.android.SaveToArcileUriGrantManager
import dev.qtremors.arcile.core.storage.domain.SaveDestinationPreferencesStore
import dev.qtremors.arcile.core.storage.domain.SaveDestinationBrowser
import dev.qtremors.arcile.core.storage.domain.VolumeRepository
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.theme.ArcileTheme
import dev.qtremors.arcile.core.ui.theme.UiPreferences
import javax.inject.Inject
import kotlinx.coroutines.flow.first

@AndroidEntryPoint
class SaveToArcileActivity : ComponentActivity() {
    @Inject
    lateinit var volumeRepository: VolumeRepository

    @Inject
    lateinit var bulkFileOperationCoordinator: BulkFileOperationCoordinator

    @Inject
    lateinit var saveDestinationPreferencesStore: SaveDestinationPreferencesStore

    @Inject
    lateinit var saveDestinationBrowser: SaveDestinationBrowser

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val preflight = IncomingShareReader.preflightFromIntent(this, intent)
        if (preflight.accepted.isEmpty()) {
            showArcileToast(
                preflight.messageOrDefault(getString(R.string.save_to_arcile_no_files)),
                longDuration = true
            )
            finish()
            return
        }

        setContent {
            ArcileTheme(uiPreferences = UiPreferences()) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    SaveToArcileRoute(
                        incoming = preflight.accepted,
                        loadVolumes = {
                            volumeRepository.getStorageVolumes().getOrElse { emptyList() }
                        },
                        loadDefaultPath = {
                            saveDestinationPreferencesStore.saveDestinationPreferencesFlow
                                .first()
                                .defaultPath
                        },
                        destinationBrowser = saveDestinationBrowser,
                        saveDefaultPath = {
                            saveDestinationPreferencesStore.updateDefaultSaveToArcilePath(it)
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
        destinationPath: String,
        incoming: List<IncomingSharedFile>
    ): Result<SaveIncomingResult> {
        val grantManager = SaveToArcileUriGrantManager(contentResolver)
        val ownedGrantUris = persistReadableUriPermissions(incoming, grantManager)
        val importItems = incoming.map { item ->
            SaveToArcileImportItem(
                uri = item.uri.toString(),
                displayName = item.displayName,
                sizeBytes = item.sizeBytes,
                requiresCountedStream = item.requiresCountedStream,
                ownsPersistedReadGrant = item.uri.toString() in ownedGrantUris
            )
        }
        val started = bulkFileOperationCoordinator.startImportOperation(
            destinationPath = destinationPath,
            importItems = importItems
        )
        return if (started) {
            Result.success(SaveIncomingResult(savedCount = 0, failures = emptyList(), queued = true))
        } else {
            grantManager.releaseOwnedPersistableReadGrants(importItems)
            Result.failure(IllegalStateException(getString(R.string.file_operation_already_running)))
        }
    }

    private fun persistReadableUriPermissions(
        incoming: List<IncomingSharedFile>,
        grantManager: SaveToArcileUriGrantManager
    ): Set<String> {
        val requiredFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        if (intent.flags and requiredFlags != requiredFlags) return emptySet()
        return grantManager.acquirePersistableReadGrants(incoming.map { it.uri })
    }

    private fun showDefaultSaved() {
        showArcileToast(getString(R.string.save_to_arcile_default_saved))
    }

    private fun handleImportStarted(result: SaveIncomingResult) {
        showArcileToast(result.userMessage(this), longDuration = true)
        if (result.queued || result.savedCount > 0 || result.failures.isEmpty()) finish()
    }

    private fun showImportFailure(error: Throwable) {
        showArcileToast(
            getString(R.string.save_to_arcile_failed, error.message ?: ""),
            longDuration = true
        )
    }
}
