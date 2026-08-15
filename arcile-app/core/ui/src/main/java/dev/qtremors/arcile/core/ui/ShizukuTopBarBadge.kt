package dev.qtremors.arcile.core.ui

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap

val LocalShizukuTopBarBadgeVisible = compositionLocalOf { false }

@Composable
internal fun ShizukuTopBarBadge() {
    val description = stringResource(R.string.top_bar_shizuku_access_active)
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier
            .size(32.dp)
            .semantics { contentDescription = description }
    ) {
        Box(contentAlignment = Alignment.Center) {
            ShizukuManagerIcon()
        }
    }
}

@Composable
private fun ShizukuManagerIcon() {
    val context = LocalContext.current
    val painter = remember(context.packageManager, context.packageName) {
        context.findShizukuManagerIcon()
            ?.let { drawable ->
                runCatching {
                    BitmapPainter(drawable.toBitmap(width = 48, height = 48).asImageBitmap())
                }.getOrNull()
            }
    }
    if (painter != null) {
        Image(
            painter = painter,
            contentDescription = null,
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
        )
    } else {
        Icon(
            imageVector = Icons.Default.Bolt,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Suppress("DEPRECATION")
internal fun Context.findShizukuManagerIcon(): Drawable? {
    val managerPackages = buildList {
        add(SHIZUKU_MANAGER_PACKAGE)
        runCatching {
            packageManager.getPackagesHoldingPermissions(
                arrayOf(SHIZUKU_PERMISSION),
                PackageManager.MATCH_DISABLED_COMPONENTS
            )
        }.getOrDefault(emptyList())
            .asSequence()
            .map { it.packageName }
            .filter { it != packageName }
            .filter { packageManager.getLaunchIntentForPackage(it) != null }
            .forEach(::add)
    }.distinct()

    return managerPackages.firstNotNullOfOrNull { managerPackage ->
        runCatching { packageManager.getApplicationIcon(managerPackage) }.getOrNull()
    }
}

private const val SHIZUKU_MANAGER_PACKAGE = "moe.shizuku.privileged.api"
private const val SHIZUKU_PERMISSION = "moe.shizuku.manager.permission.API_V23"
