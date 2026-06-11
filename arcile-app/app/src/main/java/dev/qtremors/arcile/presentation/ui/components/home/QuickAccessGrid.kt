package dev.qtremors.arcile.presentation.ui.components.home

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.storage.domain.QuickAccessItem
import dev.qtremors.arcile.core.storage.domain.QuickAccessType

private const val WHATSAPP_MEDIA_QUICK_ACCESS_ID = "standard_whatsapp_media"
private const val WHATSAPP_PACKAGE_NAME = "com.whatsapp"

@Composable
fun QuickAccessGrid(
    quickAccessItems: List<QuickAccessItem>,
    onOpenFileBrowser: () -> Unit,
    onNavigateToPath: (String) -> Unit,
    onNavigateToSaf: (String) -> Unit
) {
    fun getIconForItem(item: QuickAccessItem): ImageVector {
        if (item.type == QuickAccessType.FILES_APP) {
            return Icons.Outlined.Folder
        }
        if (item.type == QuickAccessType.EXTERNAL_HANDOFF) {
            return Icons.AutoMirrored.Outlined.OpenInNew
        }
        return when (item.label.lowercase()) {
            "dcim" -> Icons.Outlined.CameraAlt
            "downloads", "download" -> Icons.Outlined.Download
            "whatsapp" -> Icons.Outlined.Chat
            "pictures", "images" -> Icons.Outlined.Image
            "documents", "docs" -> Icons.Outlined.Description
            "music", "audio" -> Icons.Outlined.MusicNote
            "movies", "videos", "video" -> Icons.Outlined.Movie
            else -> Icons.Outlined.Folder
        }
    }


    val allFilesLabel = stringResource(R.string.all_files)
    // Include "All Files" as the final fallback action
    val allFilesItem = QuickAccessItem(
        id = "internal_all_files",
        label = allFilesLabel,
        path = "",
        type = QuickAccessType.STANDARD,
        isPinned = true,
        isEnabled = true
    )
    
    val folders = quickAccessItems.filter { it.isPinned } + allFilesItem

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val rows = folders.chunked(3)

        rows.forEach { rowFolders ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowFolders.forEach { folder ->
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        onClick = {
                            if (folder.id == "internal_all_files") {
                                onOpenFileBrowser()
                            } else if (folder.type == QuickAccessType.SAF_TREE || folder.type == QuickAccessType.EXTERNAL_HANDOFF || folder.type == QuickAccessType.FILES_APP) {
                                onNavigateToSaf(folder.path)
                            } else {
                                onNavigateToPath(folder.path)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            QuickAccessIcon(
                                item = folder,
                                packageName = packageNameForQuickAccessItem(folder),
                                fallbackIcon = getIconForItem(folder)
                            )
                            val displayLabel = folder.label.trimEnd('/').let { cleaned ->
                                val lastSlash = cleaned.lastIndexOf('/')
                                val lastColon = cleaned.lastIndexOf(':')
                                val index = maxOf(lastSlash, lastColon)
                                if (index != -1 && index < cleaned.length - 1) {
                                    cleaned.substring(index + 1)
                                } else {
                                    cleaned
                                }
                            }
                            Text(text = displayLabel, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                        }
                    }
                }
                
                if (rowFolders.size < 3) {
                    repeat(3 - rowFolders.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

internal fun packageNameForQuickAccessItem(item: QuickAccessItem): String? {
    return when (item.id) {
        WHATSAPP_MEDIA_QUICK_ACCESS_ID -> WHATSAPP_PACKAGE_NAME
        else -> null
    }
}

@Composable
private fun QuickAccessIcon(
    item: QuickAccessItem,
    packageName: String?,
    fallbackIcon: ImageVector
) {
    val context = LocalContext.current
    val appIcon = remember(context, packageName) {
        packageName?.let { context.loadApplicationIconBitmap(it) }
    }
    if (appIcon != null) {
        Image(
            bitmap = appIcon.asImageBitmap(),
            contentDescription = item.label,
            modifier = Modifier.size(20.dp)
        )
    } else {
        Icon(
            imageVector = fallbackIcon,
            contentDescription = item.label,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
    }
}

internal fun Context.loadApplicationIconBitmap(packageName: String): android.graphics.Bitmap? {
    return runCatching {
        packageManager.getApplicationIcon(packageName).toBitmap(width = 48, height = 48)
    }.getOrNull()
}
