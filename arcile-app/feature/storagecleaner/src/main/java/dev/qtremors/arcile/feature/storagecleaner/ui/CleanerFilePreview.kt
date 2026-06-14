package dev.qtremors.arcile.feature.storagecleaner.ui

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import dev.qtremors.arcile.core.storage.domain.CleanerCandidate
import dev.qtremors.arcile.core.storage.domain.CleanerRiskReason
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.image.ThumbnailKey
import dev.qtremors.arcile.image.ThumbnailType
import dev.qtremors.arcile.shared.ui.getFileIconVector
import dev.qtremors.arcile.shared.ui.loadApplicationIconBitmap
import java.io.File

@Composable
internal fun CleanerFilePreview(
    file: CleanerCandidate,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp
) {
    val context = LocalContext.current
    val fileModel = remember(file) { file.toFileModel() }
    val thumbnailKey = remember(fileModel) { ThumbnailKey.from(fileModel) }
    val packageName = remember(file.absolutePath, file.riskReasons) {
        if (CleanerRiskReason.AppLikeFolder in file.riskReasons) {
            packageNameFromPath(file.absolutePath)
        } else {
            null
        }
    }
    val appIcon = remember(context, packageName) {
        packageName?.let { context.loadApplicationIconBitmap(it) }
    }
    val requestData = remember(fileModel, thumbnailKey) { thumbnailRequestData(fileModel, thumbnailKey) }
    val appRelatedDescription = stringResource(R.string.cleaner_app_related_badge)

    Box(
        modifier = modifier
            .size(size)
            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (requestData != null) {
            SubcomposeAsyncImage(
                model = requestData,
                contentDescription = file.name,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
                error = {
                    CleanerPreviewFallback(
                        fileModel = fileModel,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            )
        } else {
            CleanerPreviewFallback(
                fileModel = fileModel,
                modifier = Modifier.fillMaxSize()
            )
        }
        if (packageName != null) {
            CleanerAppBadge(
                packageName = packageName,
                appIcon = appIcon,
                contentDescription = appRelatedDescription,
                size = (size * 0.42f).coerceAtLeast(16.dp),
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }
    }
}

@Composable
private fun CleanerAppBadge(
    packageName: String,
    appIcon: android.graphics.Bitmap?,
    contentDescription: String,
    size: Dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface, CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.surface, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (appIcon != null) {
            Image(
                bitmap = appIcon.asImageBitmap(),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = getFileIconVector(remember(packageName) { packageName.toApkBadgeFileModel() }),
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size((size * 0.68f).coerceAtLeast(12.dp))
            )
        }
    }
}

@Composable
private fun CleanerPreviewFallback(
    fileModel: FileModel,
    modifier: Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Icon(
            imageVector = getFileIconVector(fileModel),
            contentDescription = fileModel.name,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

private fun CleanerCandidate.toFileModel(): FileModel {
    val extension = name.substringAfterLast('.', "").lowercase()
    return FileModel(
        name = name,
        absolutePath = absolutePath,
        size = size,
        lastModified = lastModified,
        isDirectory = isDirectory,
        extension = extension,
        isHidden = name.startsWith(".")
    )
}

private fun String.toApkBadgeFileModel(): FileModel =
    FileModel(
        name = "$this.apk",
        absolutePath = "$this.apk",
        size = 0L,
        lastModified = 0L,
        isDirectory = false,
        extension = "apk",
        isHidden = false
    )

private fun thumbnailRequestData(file: FileModel, key: ThumbnailKey): Any? =
    when (key.type) {
        ThumbnailType.Audio,
        ThumbnailType.Video,
        ThumbnailType.Pdf,
        ThumbnailType.Apk -> key
        ThumbnailType.Image -> file.nodeRef.contentUri?.takeIf { it.isNotBlank() }?.let(Uri::parse)
            ?: File(file.absolutePath)
        ThumbnailType.Unsupported -> null
    }

internal fun packageNameFromPath(path: String): String? =
    path.split('/', '\\')
        .dropLast(1)
        .firstOrNull { packageSegmentRegex.matches(it) }

private val packageSegmentRegex = Regex("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*){1,}")
