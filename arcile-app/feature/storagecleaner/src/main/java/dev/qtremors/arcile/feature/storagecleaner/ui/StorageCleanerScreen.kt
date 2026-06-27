package dev.qtremors.arcile.feature.storagecleaner.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.CopyAll
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.storage.domain.CleanerCandidate
import dev.qtremors.arcile.core.storage.domain.CleanerGroup
import dev.qtremors.arcile.core.storage.domain.CleanerGroupType
import dev.qtremors.arcile.core.storage.domain.CleanerRiskLevel
import dev.qtremors.arcile.core.storage.domain.CleanerSectionRule
import dev.qtremors.arcile.feature.storagecleaner.StorageCleanerState
import dev.qtremors.arcile.ui.theme.bodyLargeMedium
import dev.qtremors.arcile.ui.theme.bodyMediumBold
import dev.qtremors.arcile.ui.theme.spacing
import dev.qtremors.arcile.ui.theme.titleMediumBold
import dev.qtremors.arcile.ui.theme.ExpressiveShapes
import dev.qtremors.arcile.utils.formatFileSize
import coil.compose.SubcomposeAsyncImage
import androidx.compose.ui.layout.ContentScale
import dev.qtremors.arcile.core.storage.domain.FileCategories
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.shared.ui.getFileIconVector
import dev.qtremors.arcile.shared.ui.ArcileFeedbackEvent
import dev.qtremors.arcile.shared.ui.ArcileFeedbackSeverity
import dev.qtremors.arcile.core.presentation.UiText
import dev.qtremors.arcile.shared.ui.rememberDateFormatter
import java.io.File
import java.util.Date
import java.util.Locale
import dev.qtremors.arcile.ui.theme.bounceClickable
import dev.qtremors.arcile.shared.ui.rememberArcileHaptics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.ui.graphics.graphicsLayer

enum class StorageCleanerBackAction {
    DismissIgnoredItems,
    DismissDeleteConfirmation,
    DismissDetails,
    NavigateBack
}

fun resolveStorageCleanerBackAction(
    showIgnoredItems: Boolean,
    showDeleteConfirm: Boolean,
    hasActiveDetails: Boolean
): StorageCleanerBackAction = when {
    showIgnoredItems -> StorageCleanerBackAction.DismissIgnoredItems
    showDeleteConfirm -> StorageCleanerBackAction.DismissDeleteConfirmation
    hasActiveDetails -> StorageCleanerBackAction.DismissDetails
    else -> StorageCleanerBackAction.NavigateBack
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StorageCleanerScreen(
    state: StorageCleanerState,
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
    onUnignorePath: (String) -> Unit = {},
    onFeedback: (ArcileFeedbackEvent) -> Unit = {}
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var activeCleanerGroup by remember { mutableStateOf<CleanerGroupType?>(null) }
    var confirmCleanerGroup by remember { mutableStateOf<CleanerGroupType?>(null) }
    var selectedCleanerPaths by remember { mutableStateOf(emptySet<String>()) }
    var confirmCleanerPaths by remember { mutableStateOf(emptySet<String>()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showIgnoredItems by remember { mutableStateOf(false) }
    var highRiskAcknowledged by remember { mutableStateOf(false) }

    val haptics = rememberArcileHaptics()
    fun dismissActiveDetails() {
        activeCleanerGroup = null
        confirmCleanerGroup = null
        selectedCleanerPaths = emptySet()
        confirmCleanerPaths = emptySet()
        highRiskAcknowledged = false
    }

    fun dismissDeleteConfirmation() {
        showDeleteConfirm = false
        confirmCleanerGroup = null
        confirmCleanerPaths = emptySet()
    }

    var backProgress by remember { mutableStateOf(0f) }
    var isBackPredicting by remember { mutableStateOf(false) }

    val isBackHandlerEnabled = showIgnoredItems || showDeleteConfirm || activeCleanerGroup != null
    PredictiveBackHandler(enabled = isBackHandlerEnabled) { progressFlow ->
        isBackPredicting = true
        try {
            progressFlow.collect { backEvent ->
                backProgress = backEvent.progress
            }
            when (resolveStorageCleanerBackAction(showIgnoredItems, showDeleteConfirm, activeCleanerGroup != null)) {
                StorageCleanerBackAction.DismissIgnoredItems -> showIgnoredItems = false
                StorageCleanerBackAction.DismissDeleteConfirmation -> dismissDeleteConfirmation()
                StorageCleanerBackAction.DismissDetails -> dismissActiveDetails()
                StorageCleanerBackAction.NavigateBack -> onNavigateBack()
            }
        } catch (e: Exception) {
            // Cancelled
        } finally {
            isBackPredicting = false
            backProgress = 0f
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "refreshRotation")
    val rotation by if (state.isScanning) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "refreshRotationAngle"
        )
    } else {
        remember { mutableStateOf(0f) }
    }

    LaunchedEffect(state.successMessage) {
        state.successMessage?.let { message ->
            haptics.success()
            onFeedback(
                ArcileFeedbackEvent(
                    message = UiText.StringResource(R.string.clean_success, listOf(message.cleanedCount)),
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
            activeCleanerGroup = null
            confirmCleanerGroup = null
            selectedCleanerPaths = emptySet()
            confirmCleanerPaths = emptySet()
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

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.storage_cleaner_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.clip(CircleShape)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showIgnoredItems = true },
                        modifier = Modifier.clip(CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.cleaner_ignored_items)
                        )
                    }
                    IconButton(
                        onClick = onRefresh,
                        enabled = !state.isScanning && !state.isCleaning,
                        modifier = Modifier.clip(CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.refresh),
                            modifier = Modifier.graphicsLayer {
                                rotationZ = rotation
                            }
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
        ) {
            if (state.isScanning && state.scannedFiles == 0) {
                CleanerLoading()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = 12.dp,
                        bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + MaterialTheme.spacing.screenGutter
                    )
                ) {
                    if (state.isPartial) {
                        item {
                            Text(
                                text = stringResource(R.string.cleaner_partial_results),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }

                    item {
                        CleanerThumbnailCacheCard()
                    }

                    items(CleanerGroupType.entries, key = { it.name }) { type ->
                        CleanerCategoryCard(
                            group = state.group(type),
                            onClick = {
                                selectedCleanerPaths = emptySet()
                                confirmCleanerPaths = emptySet()
                                confirmCleanerGroup = null
                                activeCleanerGroup = type
                            }
                        )
                    }
                }
            }
        }
    }

    val activeGroup = activeCleanerGroup?.let(state::group)
    if (activeGroup != null) {
        CleanerDetailsSheet(
            group = activeGroup,
            isCleaning = state.isCleaning,
            selectedFiles = selectedCleanerPaths,
            onSelectedFilesChange = { selectedCleanerPaths = it },
            onDismiss = { dismissActiveDetails() },
            onRequestClean = { paths ->
                confirmCleanerGroup = activeCleanerGroup
                activeCleanerGroup = null
                selectedCleanerPaths = paths
                confirmCleanerPaths = paths
                highRiskAcknowledged = false
                showDeleteConfirm = true
            },
            onOpenFile = onOpenFile,
            onOpenContainingFolder = onOpenContainingFolder,
            rules = state.rules,
            onUpdateSectionRule = onUpdateSectionRule,
            onResetSectionRule = onResetSectionRule,
            onIgnorePath = onIgnorePath,
            backProgress = backProgress,
            isBackPredicting = isBackPredicting && activeCleanerGroup != null
        )
    }

    val confirmGroup = confirmCleanerGroup?.let(state::group)
    if (showDeleteConfirm && confirmGroup != null) {
        val selectedCandidates = confirmGroup.candidates.filter { it.absolutePath in confirmCleanerPaths }
        val hasHighRisk = selectedCandidates.any { it.riskLevel == CleanerRiskLevel.High }
        AlertDialog(
            onDismissRequest = {
                dismissDeleteConfirmation()
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
                        showDeleteConfirm = false
                        onCleanFiles(confirmCleanerPaths.toList(), highRiskAcknowledged)
                        confirmCleanerGroup = null
                        confirmCleanerPaths = emptySet()
                    },
                    enabled = !hasHighRisk || highRiskAcknowledged,
                    shape = ExpressiveShapes.medium,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { dismissDeleteConfirmation() },
                    shape = ExpressiveShapes.medium
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showIgnoredItems) {
        IgnoredItemsDialog(
            ignoredPaths = state.rules.ignoredPaths,
            onUnignorePath = onUnignorePath,
            onDismiss = { showIgnoredItems = false }
        )
    }
}

@Composable
private fun IgnoredItemsDialog(
    ignoredPaths: Set<String>,
    onUnignorePath: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cleaner_ignored_items)) },
        text = {
            if (ignoredPaths.isEmpty()) {
                Text(
                    text = stringResource(R.string.cleaner_no_ignored_items),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier.height(240.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(ignoredPaths.sorted(), key = { it }) { path ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = path,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            TextButton(
                                onClick = { onUnignorePath(path) },
                                shape = ExpressiveShapes.medium
                            ) {
                                Text(stringResource(R.string.cleaner_restore_item))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                shape = ExpressiveShapes.medium
            ) {
                Text(stringResource(R.string.ok))
            }
        }
    )
}

@Composable
internal fun CleanerConfirmContent(
    selectedCandidates: List<CleanerCandidate>,
    hasHighRisk: Boolean,
    highRiskAcknowledged: Boolean,
    onHighRiskAcknowledgedChange: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.clean_confirm_message, selectedCandidates.size))
        LazyColumn(
            modifier = Modifier.height(180.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(selectedCandidates, key = { it.absolutePath }) { candidate ->
                Column {
                    Text(
                        text = candidate.absolutePath,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(
                            R.string.cleaner_confirm_file_detail,
                            formatFileSize(candidate.size),
                            cleanerRiskLabel(candidate.riskLevel)
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = cleanerRiskColor(candidate.riskLevel)
                    )
                }
            }
        }
        if (hasHighRisk) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = highRiskAcknowledged,
                    onCheckedChange = onHighRiskAcknowledgedChange
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.cleaner_high_risk_acknowledge),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
