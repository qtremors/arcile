package dev.qtremors.arcile.presentation.ui.apk

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import dev.qtremors.arcile.core.operation.android.apk.ApkInstallState
import dev.qtremors.arcile.core.operation.android.apk.ApkPackageDetails
import dev.qtremors.arcile.core.presentation.formatFileSize
import dev.qtremors.arcile.core.ui.theme.ExpressiveShapes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween

/* ============================================================================
 * APK INSTALLER DIALOG
 * ============================================================================
 * Material 3 Expressive dialog for single & split APK package installation.
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApkInstallerDialog(
    details: ApkPackageDetails?,
    installState: ApkInstallState,
    onInstall: () -> Unit,
    onGrantPermission: () -> Unit,
    onOpenApp: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = {
            if (installState !is ApkInstallState.Installing) {
                onDismiss()
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = true)
    ) {
        Surface(
            shape = ExpressiveShapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            modifier = Modifier.widthIn(max = 330.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (details == null) {
                    CircularProgressIndicator(modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Analyzing package metadata...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    val iconDrawable = details.icon
                    // App Icon Header
                    if (iconDrawable != null) {
                        val bitmap = iconDrawable.toBitmap(144, 144)
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = details.label,
                            modifier = Modifier.size(64.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Android,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // App Title
                    Text(
                        text = details.label,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Package Identifier
                    Text(
                        text = details.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Version & Split Information Cards
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Version Status Badge Header if installed
                            if (details.isInstalled) {
                                val (badgeText, badgeBg, badgeFg) = when {
                                    details.isUpdate -> Triple("Update Available", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                                    details.isDowngrade -> Triple("Older Version (Downgrade)", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
                                    else -> Triple("Already Installed", MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Surface(
                                    color = badgeBg,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = badgeText,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = badgeFg,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 5.dp, horizontal = 8.dp)
                                    )
                                }
                            }

                            // 1. APK File Version Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "APK Version",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "v${details.versionName} (${details.versionCode})",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }

                            // 2. Currently Installed Device Version Row
                            if (details.isInstalled) {
                                val installedVerName = details.installedVersionName ?: details.installedVersionCode.toString()
                                val installedVerCode = details.installedVersionCode ?: 0L

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Installed",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "v$installedVerName ($installedVerCode)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Medium,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                            }

                            // 3. Package Type
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Package Type",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = if (details.isSplitApk) "Split APK (${details.splitCount} components)" else "Single APK",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (details.isSplitApk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }

                            // 4. Total Size
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Total Size",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = formatFileSize(details.totalSizeBytes),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Installation Dynamic Status View
                    when (installState) {
                        ApkInstallState.Idle -> {
                            if (details.isDowngrade) {
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Error,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                        Text(
                                            text = "Android blocks installing older versions. Uninstall the current app first to install this version.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }
                            }
                        }

                        ApkInstallState.UnknownAppSourcesPermissionRequired -> {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Security,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Text(
                                        text = "Permission to install unknown apps from system settings is required.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }

                        is ApkInstallState.Installing -> {
                            val animatedProgress by animateFloatAsState(
                                targetValue = installState.progress,
                                animationSpec = tween(durationMillis = 300),
                                label = "ApkInstallProgress"
                            )
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                LinearWavyProgressIndicator(
                                    progress = { animatedProgress },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    text = installState.currentFile,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        is ApkInstallState.Success -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                val successText = when {
                                    details?.isUpdate == true -> "Application updated successfully!"
                                    details?.isSameVersion == true -> "Application reinstalled successfully!"
                                    else -> "Application installed successfully!"
                                }
                                Text(
                                    text = successText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        is ApkInstallState.Failed -> {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Error,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Text(
                                        text = installState.reason,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Bottom Action Bar
                val canOpenAndAction = details?.isInstalled == true && installState !is ApkInstallState.Installing && installState !is ApkInstallState.Success && !details.isDowngrade

                if (canOpenAndAction) {
                    // Two side-by-side buttons: Open App (Outlined) & Update/Reinstall (Primary)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { onOpenApp(details.packageName) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text("Open App")
                            }
                        }

                        val (actionIcon, actionLabel) = when {
                            details.isUpdate -> Icons.Default.SystemUpdate to "Update"
                            else -> Icons.Default.Refresh to "Reinstall"
                        }
                        Button(
                            onClick = onInstall,
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = actionIcon,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(actionLabel)
                            }
                        }
                    }
                } else {
                    // Single prominent full-width action button
                    when (installState) {
                        ApkInstallState.UnknownAppSourcesPermissionRequired -> {
                            Button(
                                onClick = onGrantPermission,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Security,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text("Grant Permission")
                                }
                            }
                        }
                        is ApkInstallState.Success -> {
                            Button(
                                onClick = { onOpenApp(installState.packageName) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text("Open App")
                                }
                            }
                        }
                        is ApkInstallState.Installing -> Unit
                        else -> {
                            val isDowngrade = details?.isDowngrade == true
                            if (isDowngrade) {
                                Button(
                                    onClick = { onOpenApp(details?.packageName.orEmpty()) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text("Open App")
                                    }
                                }
                            } else {
                                Button(
                                    onClick = onInstall,
                                    enabled = details != null,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Download,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text("Install")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
