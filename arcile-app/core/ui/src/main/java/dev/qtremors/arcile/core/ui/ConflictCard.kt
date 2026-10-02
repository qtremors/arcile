package dev.qtremors.arcile.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import dev.qtremors.arcile.core.presentation.formatFileSize
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import dev.qtremors.arcile.core.storage.domain.FileConflict
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.ui.lists.thumbnailRequestData
import java.text.SimpleDateFormat
import java.util.Date

internal val ConflictWideLayoutMinWidth = 600.dp

@Composable
fun ConflictCard(
    conflict: FileConflict,
    resolution: ConflictResolution?,
    formatter: SimpleDateFormat,
    onResolutionChange: (ConflictResolution) -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor by animateColorAsState(
        targetValue = when (resolution) {
            ConflictResolution.KEEP_BOTH -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
            ConflictResolution.REPLACE -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            ConflictResolution.SKIP -> MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
            null -> MaterialTheme.colorScheme.surfaceContainerHigh
        },
        label = "conflictCardColor"
    )

    val isSourceDirectory = conflict.sourceFile.isDirectory
    val isExistingDirectory = conflict.existingFile.isDirectory

    val isIdentical = !isSourceDirectory && !isExistingDirectory &&
        conflict.sourceFile.size == conflict.existingFile.size &&
        conflict.sourceFile.lastModified == conflict.existingFile.lastModified

    val isSourceNewer = !isIdentical && conflict.sourceFile.lastModified > conflict.existingFile.lastModified
    val isExistingNewer = !isIdentical && conflict.existingFile.lastModified > conflict.sourceFile.lastModified

    val isSourceLarger = !isSourceDirectory && !isExistingDirectory && conflict.sourceFile.size > conflict.existingFile.size
    val isExistingLarger = !isSourceDirectory && !isExistingDirectory && conflict.existingFile.size > conflict.sourceFile.size

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val isWide = maxWidth >= ConflictWideLayoutMinWidth
        val orientationTag = if (isWide) "conflict_orientation_side_by_side" else "conflict_orientation_stacked"

        Card(
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = containerColor),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(orientationTag)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Show identical files banner if matching
                if (isIdentical) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .testTag("conflict_identical_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.conflict_identical_files),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                // Comparison section (Responsive)
                if (isWide) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ConflictFileInfoPanel(
                            label = stringResource(R.string.conflict_new),
                            file = conflict.sourceFile,
                            formatter = formatter,
                            isNewer = isSourceNewer,
                            isLarger = isSourceLarger,
                            isIncoming = true,
                            modifier = Modifier.weight(1f)
                        )

                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(24.dp)
                        )

                        ConflictFileInfoPanel(
                            label = stringResource(R.string.conflict_existing),
                            file = conflict.existingFile,
                            formatter = formatter,
                            isNewer = isExistingNewer,
                            isLarger = isExistingLarger,
                            isIncoming = false,
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ConflictFileInfoPanel(
                            label = stringResource(R.string.conflict_new),
                            file = conflict.sourceFile,
                            formatter = formatter,
                            isNewer = isSourceNewer,
                            isLarger = isSourceLarger,
                            isIncoming = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(24.dp)
                        )

                        ConflictFileInfoPanel(
                            label = stringResource(R.string.conflict_existing),
                            file = conflict.existingFile,
                            formatter = formatter,
                            isNewer = isExistingNewer,
                            isLarger = isExistingLarger,
                            isIncoming = false,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Show resolution status label
                if (resolution != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = when (resolution) {
                            ConflictResolution.KEEP_BOTH -> stringResource(R.string.conflict_resolution_keep_both)
                            ConflictResolution.REPLACE -> stringResource(R.string.conflict_resolution_replace)
                            ConflictResolution.SKIP -> stringResource(R.string.conflict_resolution_skip)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("conflict_resolution_status")
                    )
                }
            }
        }
    }
}

@Composable
private fun ConflictFileInfoPanel(
    label: String,
    file: FileModel,
    formatter: SimpleDateFormat,
    isNewer: Boolean,
    isLarger: Boolean,
    isIncoming: Boolean,
    modifier: Modifier = Modifier
) {
    val panelTag = if (isIncoming) "conflict_incoming" else "conflict_existing"
    val thumbnailTag = if (isIncoming) "conflict_thumbnail_incoming" else "conflict_thumbnail_existing"
    val context = LocalContext.current

    val typeOrFolder = if (file.isDirectory) {
        stringResource(R.string.folder_label)
    } else {
        file.extension.uppercase().ifBlank { "FILE" }
    }

    val formattedDate = remember(file.lastModified, formatter) {
        formatter.format(Date(file.lastModified))
    }

    val formattedSize = remember(file.size, file.isDirectory, context) {
        if (file.isDirectory) null else formatFileSize(context, file.size)
    }

    val parentDisplay = remember(file.reference) {
        file.reference.substringBeforeLast('/', "").ifBlank { "/" }
    }

    Column(
        modifier = modifier
            .testTag(panelTag)
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.medium
            )
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Top: Round thumbnail + 2-row metadata header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ConflictFileThumbnail(
                file = file,
                sizeDp = 36,
                modifier = Modifier.testTag(thumbnailTag)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Row 1: New/Existing label + Newer/Larger badges + file extension
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )

                    if (isNewer) {
                        Box(
                            modifier = Modifier
                                .background(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = MaterialTheme.shapes.extraSmall
                                )
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.conflict_newer),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (isLarger) {
                        Box(
                            modifier = Modifier
                                .background(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = MaterialTheme.shapes.extraSmall
                                )
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.conflict_larger),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Text(
                        text = typeOrFolder,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Row 2: Size and Time / Date
                val sizeAndDateText = remember(formattedSize, formattedDate) {
                    buildList {
                        if (formattedSize != null) {
                            add(formattedSize)
                        }
                        add(formattedDate)
                    }.joinToString(" · ")
                }

                Text(
                    text = sizeAndDateText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Bottom: Name and Path
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = file.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = parentDisplay,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ConflictFileThumbnail(
    file: FileModel,
    sizeDp: Int,
    modifier: Modifier = Modifier
) {
    val requestData = file.thumbnailRequestData()

    Box(
        modifier = modifier
            .size(sizeDp.dp)
            .clip(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (requestData != null) {
            SubcomposeAsyncImage(
                model = requestData,
                contentDescription = stringResource(R.string.thumbnail),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                error = {
                    ThumbnailFallback(file = file, sizeDp = sizeDp)
                }
            )
        } else {
            ThumbnailFallback(file = file, sizeDp = sizeDp)
        }
    }
}

@Composable
private fun ThumbnailFallback(
    file: FileModel,
    sizeDp: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = getFileIconVector(
                file,
                dev.qtremors.arcile.core.ui.theme.LocalFolderIconsEnabled.current
            ),
            contentDescription = null,
            tint = if (file.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size((sizeDp / 2).coerceAtLeast(18).dp)
        )
    }
}
