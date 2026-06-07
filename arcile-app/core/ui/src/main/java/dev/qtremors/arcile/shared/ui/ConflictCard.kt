package dev.qtremors.arcile.shared.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import dev.qtremors.arcile.core.storage.domain.FileCategories
import dev.qtremors.arcile.core.storage.domain.FileConflict
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.utils.formatFileSize
import dev.qtremors.arcile.core.ui.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

@Composable
fun ConflictCard(
    conflict: FileConflict,
    resolution: ConflictResolution?,
    formatter: SimpleDateFormat,
    onResolutionChange: (ConflictResolution) -> Unit
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

    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // File name header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                FileThumbnail(file = conflict.sourceFile, size = 32)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    conflict.sourceFile.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Show identical files banner if matching
            if (isIdentical) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
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

            // Side-by-side comparison
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Incoming file
                FileInfoColumn(
                    label = stringResource(R.string.conflict_new),
                    file = conflict.sourceFile,
                    formatter = formatter,
                    isNewer = isSourceNewer,
                    isLarger = isSourceLarger,
                    modifier = Modifier.weight(1f)
                )

                Icon(
                    Icons.Default.SwapHoriz,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .size(20.dp)
                )

                // Existing file
                FileInfoColumn(
                    label = stringResource(R.string.conflict_existing),
                    file = conflict.existingFile,
                    formatter = formatter,
                    isNewer = isExistingNewer,
                    isLarger = isExistingLarger,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(12.dp))

            // Removed segmented button row from here since it's global to the dialog now

            // Show resolution status label
            if (resolution != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    when (resolution) {
                        ConflictResolution.KEEP_BOTH -> stringResource(R.string.conflict_resolution_keep_both)
                        ConflictResolution.REPLACE -> stringResource(R.string.conflict_resolution_replace)
                        ConflictResolution.SKIP -> stringResource(R.string.conflict_resolution_skip)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun FileThumbnail(file: FileModel, size: Int) {
    val ext = file.name.substringAfterLast('.').lowercase()
    val hasPreview = FileCategories.Images.extensions.contains(ext) ||
            FileCategories.Videos.extensions.contains(ext)

    if (hasPreview) {
        SubcomposeAsyncImage(
            model = File(file.absolutePath),
            contentDescription = stringResource(R.string.thumbnail),
            modifier = Modifier
                .size(size.dp)
                .clip(MaterialTheme.shapes.extraLarge),
            contentScale = ContentScale.Crop,
            error = {
                Icon(
                    imageVector = dev.qtremors.arcile.shared.ui.getFileIconVector(file),
                    contentDescription = null,
                    tint = if (file.isDirectory) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(size.dp)
                )
            }
        )
    } else {
        Icon(
            imageVector = dev.qtremors.arcile.shared.ui.getFileIconVector(file),
            contentDescription = null,
            tint = if (file.isDirectory) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(size.dp)
        )
    }
}

@Composable
private fun FileInfoColumn(
    label: String,
    file: FileModel,
    formatter: SimpleDateFormat,
    isNewer: Boolean,
    isLarger: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.medium
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
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
                        .padding(horizontal = 6.dp, vertical = 2.dp)
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
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = stringResource(R.string.conflict_larger),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        if (!file.isDirectory) {
            Text(
                formatFileSize(file.size),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isLarger) FontWeight.Bold else FontWeight.Medium,
                color = if (isLarger) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface
            )
        } else {
            Text(
                stringResource(R.string.folder_label),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
        Text(
            formatter.format(Date(file.lastModified)),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isNewer) FontWeight.Bold else FontWeight.Normal,
            color = if (isNewer) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
