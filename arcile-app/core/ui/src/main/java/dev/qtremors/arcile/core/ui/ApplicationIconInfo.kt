package dev.qtremors.arcile.core.ui

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ApplicationIconInfo(
    val label: String?,
    val icon: Bitmap?
)

@Composable
fun rememberApplicationIconInfo(packageName: String?): ApplicationIconInfo? {
    val context = LocalContext.current.applicationContext
    val info by produceState<ApplicationIconInfo?>(
        initialValue = null,
        context,
        packageName
    ) {
        value = packageName?.let { packageId ->
            withContext(Dispatchers.IO) {
                val label = runCatching {
                    val packageManager = context.packageManager
                    packageManager.getApplicationLabel(
                        packageManager.getApplicationInfo(packageId, 0)
                    ).toString()
                }.getOrNull()
                val icon = context.loadApplicationIconBitmap(packageId)
                ApplicationIconInfo(label = label, icon = icon)
                    .takeIf { it.label != null || it.icon != null }
            }
        }
    }
    return info
}
