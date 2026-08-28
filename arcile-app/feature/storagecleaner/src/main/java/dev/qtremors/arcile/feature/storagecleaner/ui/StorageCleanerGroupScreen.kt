package dev.qtremors.arcile.feature.storagecleaner.ui

import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import dev.qtremors.arcile.core.presentation.UiText
import dev.qtremors.arcile.core.storage.domain.CleanerGroupType
import dev.qtremors.arcile.core.storage.domain.CleanerRiskLevel
import dev.qtremors.arcile.core.storage.domain.CleanerSectionRule
import dev.qtremors.arcile.core.ui.ArcileFeedbackEvent
import dev.qtremors.arcile.core.ui.ArcileFeedbackSeverity
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.dialogs.AlertDialog
import dev.qtremors.arcile.core.ui.rememberArcileHaptics
import dev.qtremors.arcile.core.ui.theme.ExpressiveShapes
import dev.qtremors.arcile.feature.storagecleaner.StorageCleanerState

private val cleanerPathSetSaver = Saver<Set<String>, ArrayList<String>>(
    save = { ArrayList(it) },
    restore = { it.toSet() }
)

@Composable
internal fun StorageCleanerGroupScreen(
    state: StorageCleanerState,
    type: CleanerGroupType,
    onNavigateBack: () -> Unit,
    onRefresh: () -> Unit,
    onCleanFiles: (List<String>, Boolean) -> Unit,
    onUndoClean: (List<String>) -> Unit = {},
    onClearMessages: () -> Unit,
    onOpenFile: (String) -> Unit = {},
    onOpenContainingFolder: (String) -> Unit = {},
    onUpdateSectionRule: (CleanerGroupType, CleanerSectionRule) -> Unit = { _, _ -> },
    onResetSectionRule: (CleanerGroupType) -> Unit = {},
    onIgnorePath: (String) -> Unit = {},
    onFeedback: (ArcileFeedbackEvent) -> Unit = {}
) {
    val group = state.group(type)
    val haptics = rememberArcileHaptics()
    var selectedPaths by rememberSaveable(type.name, stateSaver = cleanerPathSetSaver) {
        mutableStateOf(emptySet())
    }
    var confirmPaths by rememberSaveable(type.name, stateSaver = cleanerPathSetSaver) {
        mutableStateOf(emptySet())
    }
    var showDeleteConfirm by rememberSaveable(type.name) { mutableStateOf(false) }
    var highRiskAcknowledged by rememberSaveable(type.name) { mutableStateOf(false) }
    val isGroupLoaded = type in state.loadedGroups
    val isGroupScanning = type in state.scanningGroups

    LaunchedEffect(state.isScanning, isGroupLoaded, group.candidates) {
        if (!state.isScanning && isGroupLoaded) {
            val availablePaths = group.candidates.mapTo(hashSetOf()) { it.absolutePath }
            selectedPaths = selectedPaths.intersect(availablePaths)
            confirmPaths = confirmPaths.intersect(availablePaths)
            if (confirmPaths.isEmpty()) showDeleteConfirm = false
        }
    }
    LaunchedEffect(state.successMessage) {
        state.successMessage?.let { message ->
            haptics.success()
            onFeedback(
                ArcileFeedbackEvent(
                    message = UiText.PluralResource(R.plurals.clean_success, message.cleanedCount, listOf(message.cleanedCount)),
                    severity = ArcileFeedbackSeverity.Success,
                    actionLabel = message.undoTrashIds.takeIf { it.isNotEmpty() }?.let {
                        UiText.StringResource(R.string.undo)
                    },
                    onAction = message.undoTrashIds.takeIf { it.isNotEmpty() }?.let { ids ->
                        { onUndoClean(ids) }
                    }
                )
            )
            onClearMessages()
        }
    }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { message ->
            onFeedback(
                ArcileFeedbackEvent(
                    message = message.takeIf { it.isNotBlank() }?.let(UiText::Dynamic)
                        ?: UiText.StringResource(R.string.clean_failed),
                    severity = ArcileFeedbackSeverity.Error
                )
            )
            onClearMessages()
        }
    }

    CleanerDetailsScreen(
        group = group,
        isCleaning = state.isCleaning,
        isLoaded = isGroupLoaded,
        isScanning = isGroupScanning,
        isPullToRefreshing = state.isPullToRefreshing && state.scanningGroups == setOf(type),
        scanProgress = state.scanProgress,
        onRefresh = onRefresh,
        onNavigateBack = onNavigateBack,
        selectedFiles = selectedPaths,
        onSelectedFilesChange = { selectedPaths = it },
        onRequestClean = { paths ->
            confirmPaths = paths
            highRiskAcknowledged = false
            showDeleteConfirm = paths.isNotEmpty()
        },
        onOpenFile = onOpenFile,
        onOpenContainingFolder = onOpenContainingFolder,
        rules = state.rules,
        onUpdateSectionRule = onUpdateSectionRule,
        onResetSectionRule = onResetSectionRule,
        onIgnorePath = onIgnorePath
    )

    if (showDeleteConfirm) {
        val selectedCandidates = group.candidates.filter { it.absolutePath in confirmPaths }
        val validatedPaths = selectedCandidates.mapTo(hashSetOf()) { it.absolutePath }
        val isSelectionValidated = isGroupLoaded &&
            confirmPaths.isNotEmpty() &&
            validatedPaths == confirmPaths
        val hasHighRisk = selectedCandidates.any { it.riskLevel == CleanerRiskLevel.High }
        AlertDialog(
            onDismissRequest = {
                showDeleteConfirm = false
                confirmPaths = emptySet()
            },
            title = { Text(stringResource(R.string.clean_confirm_title)) },
            text = {
                CleanerConfirmContent(
                    selectedCandidates = selectedCandidates,
                    hasHighRisk = hasHighRisk,
                    highRiskAcknowledged = highRiskAcknowledged,
                    onHighRiskAcknowledgedChange = { highRiskAcknowledged = it }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (isSelectionValidated) {
                            showDeleteConfirm = false
                            onCleanFiles(selectedCandidates.map { it.absolutePath }, highRiskAcknowledged)
                            confirmPaths = emptySet()
                        }
                    },
                    enabled = isSelectionValidated && (!hasHighRisk || highRiskAcknowledged),
                    shape = ExpressiveShapes.medium,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        confirmPaths = emptySet()
                    },
                    shape = ExpressiveShapes.medium
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
