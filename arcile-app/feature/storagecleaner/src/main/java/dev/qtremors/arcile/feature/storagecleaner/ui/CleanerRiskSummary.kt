package dev.qtremors.arcile.feature.storagecleaner.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.storage.domain.CleanerCandidate
import dev.qtremors.arcile.core.storage.domain.CleanerRiskReason
import dev.qtremors.arcile.core.ui.loadApplicationIconBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun CleanerRiskSummary(
    file: CleanerCandidate,
    appContext: CleanerAppContext? = null,
    modifier: Modifier = Modifier
) {
    val reasonLabels = buildList {
        file.riskReasons.take(2).forEach { reason ->
            if (reason != CleanerRiskReason.AppLikeFolder || appContext?.icon == null) {
                add(cleanerRiskReason(reason))
            }
        }
    }
    val reasons = reasonLabels.joinToString(", ")

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        appContext?.icon?.let { icon ->
            Image(
                bitmap = icon.asImageBitmap(),
                contentDescription = appContext.label,
                modifier = Modifier.size(18.dp).clip(CircleShape)
            )
        }
        Surface(
            shape = CircleShape,
            color = cleanerRiskColor(file.riskLevel).copy(alpha = 0.16f)
        ) {
            Text(
                text = cleanerRiskLabel(file.riskLevel),
                style = MaterialTheme.typography.labelSmall,
                color = cleanerRiskColor(file.riskLevel),
                modifier = Modifier.widthIn(min = 52.dp).padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        if (reasons.isNotBlank()) {
            Text(
                text = reasons,
                style = MaterialTheme.typography.labelSmall,
                color = cleanerRiskColor(file.riskLevel),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
        }
    }
}

@Composable
internal fun rememberCleanerAppContext(file: CleanerCandidate): CleanerAppContext? {
    val context = LocalContext.current
    val packageName = remember(file.absolutePath, file.riskReasons) {
        if (CleanerRiskReason.AppLikeFolder in file.riskReasons) {
            packageNameFromPath(file.absolutePath)
        } else {
            null
        }
    }
    val appContext by produceState<CleanerAppContext?>(initialValue = null, context, packageName) {
        value = packageName?.let { packageId ->
            withContext(Dispatchers.IO) {
                val label = runCatching {
                    val packageManager = context.packageManager
                    packageManager.getApplicationLabel(
                        packageManager.getApplicationInfo(packageId, 0)
                    ).toString()
                }.getOrNull()
                CleanerAppContext(
                    label = label,
                    icon = context.loadApplicationIconBitmap(packageId)
                )
            }
        }
    }
    return appContext
}

internal data class CleanerAppContext(
    val label: String?,
    val icon: Bitmap?
)

internal fun cleanFilePath(path: String): String = when {
    path == PRIMARY_STORAGE_ROOT -> ""
    path.startsWith("$PRIMARY_STORAGE_ROOT/") -> path.removePrefix("$PRIMARY_STORAGE_ROOT/")
    else -> path
}

private const val PRIMARY_STORAGE_ROOT = "/storage/emulated/0"
