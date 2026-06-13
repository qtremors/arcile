package dev.qtremors.arcile.shared.ui

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import dev.qtremors.arcile.core.storage.domain.QuickAccessItem
import dev.qtremors.arcile.core.storage.domain.QuickAccessType

private const val WHATSAPP_MEDIA_QUICK_ACCESS_ID = "standard_whatsapp_media"
private const val WHATSAPP_PACKAGE_NAME = "com.whatsapp"
private const val WHATSAPP_BUSINESS_PACKAGE_NAME = "com.whatsapp.w4b"
private const val APP_ICON_BITMAP_SIZE_PX = 144

@Composable
fun QuickAccessAppIcon(
    item: QuickAccessItem,
    fallbackIcon: ImageVector,
    modifier: Modifier = Modifier,
    appIconModifier: Modifier = modifier,
    fallbackTint: Color = MaterialTheme.colorScheme.primary
) {
    val context = LocalContext.current
    val resolvedPackageName = remember(context, item.id, item.type, item.path) {
        packageNameForQuickAccessItem(item) ?: item.takeIf { it.opensInFilesApp() }
            ?.let { context.resolveFilesAppPackageName() }
    }
    val appIcon = remember(context, resolvedPackageName) {
        resolvedPackageName?.let { context.loadApplicationIconBitmap(it) }
    }
    if (appIcon != null) {
        Image(
            bitmap = appIcon.asImageBitmap(),
            contentDescription = item.label,
            modifier = appIconModifier
        )
    } else {
        Icon(
            imageVector = fallbackIcon,
            contentDescription = item.label,
            tint = fallbackTint,
            modifier = modifier
        )
    }
}

private fun QuickAccessItem.opensInFilesApp(): Boolean {
    return type == QuickAccessType.FILES_APP || type == QuickAccessType.EXTERNAL_HANDOFF
}

fun packageNameForQuickAccessItem(item: QuickAccessItem): String? {
    return when (item.id) {
        WHATSAPP_MEDIA_QUICK_ACCESS_ID -> when {
            item.path.contains(WHATSAPP_BUSINESS_PACKAGE_NAME) -> WHATSAPP_BUSINESS_PACKAGE_NAME
            else -> WHATSAPP_PACKAGE_NAME
        }
        else -> null
    }
}

fun Context.loadApplicationIconBitmap(packageName: String): android.graphics.Bitmap? {
    return runCatching {
        packageManager.getApplicationIcon(packageName).toBitmap(
            width = APP_ICON_BITMAP_SIZE_PX,
            height = APP_ICON_BITMAP_SIZE_PX
        )
    }.getOrNull()
}

fun Context.resolveFilesAppPackageName(): String? {
    return runCatching {
        val packageInfos = packageManager.getPackagesHoldingPermissions(
            arrayOf(android.Manifest.permission.MANAGE_DOCUMENTS),
            0
        )
        selectDocumentsUiPackageName(packageInfos.map { it.packageName })
    }.getOrNull()
}

fun selectDocumentsUiPackageName(packageNames: List<String>): String? {
    return packageNames.firstOrNull { it.endsWith(".documentsui") }
        ?: packageNames.firstOrNull()
}
