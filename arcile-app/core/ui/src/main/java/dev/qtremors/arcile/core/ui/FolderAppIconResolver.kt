package dev.qtremors.arcile.core.ui

import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.os.UserManager
import android.provider.MediaStore
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import dev.qtremors.arcile.core.ui.theme.LocalFolderIconsEnabled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object FolderAppIconResolver {

    private const val ICON_SIZE_PX = 128
    private val iconCache = LruCache<String, Bitmap>(100)
    private val resolvedPackageCache = ConcurrentHashMap<String, String>()
    private val installedAppIndex = ConcurrentHashMap<String, String>()
    @Volatile private var indexBuilt = false
    @Volatile private var registeredApplicationContext: Context? = null

    private val packageChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            clearResolutionCaches()
        }
    }

    private val launcherAppsCallback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = clearResolutionCaches()
        override fun onPackageAdded(packageName: String, user: UserHandle) = clearResolutionCaches()
        override fun onPackageChanged(packageName: String, user: UserHandle) = clearResolutionCaches()
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) =
            clearResolutionCaches()
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) =
            clearResolutionCaches()
    }

    fun normalize(text: String): String {
        val nfkc = Normalizer.normalize(text, Normalizer.Form.NFKC)
        return nfkc.replace(Regex("[_\\-\\s]+"), " ").trim().lowercase(Locale.ROOT)
    }

    fun resolvePackageName(context: Context, folderName: String, path: String? = null): String? {
        ensureInvalidationObservers(context.applicationContext)
        val normalized = normalize(folderName)
        if (normalized.isEmpty()) return null
        val cacheKey = "$normalized|${path.orEmpty().replace('\\', '/').lowercase(Locale.ROOT)}"

        resolvedPackageCache[cacheKey]?.let { return it.takeIf { it.isNotBlank() } }

        resolveSystemFolderPackageName(context, folderName, path)?.let { packageName ->
            resolvedPackageCache[cacheKey] = packageName
            return packageName
        }

        // Check exact package name (if folder name resembles package ID like com.example.app)
        if (folderName.contains('.') && isPackageInstalled(context, folderName.trim())) {
            resolvedPackageCache[cacheKey] = folderName.trim()
            return folderName.trim()
        }

        // Check path components if path is provided (e.g. Android/data/com.whatsapp)
        if (path != null) {
            val segments = path.replace('\\', '/').split('/').filter(String::isNotBlank)
            val dataIndex = segments.indexOfFirst { it.equals("data", ignoreCase = true) }.takeIf { it >= 0 }
                ?: segments.indexOfFirst { it.equals("media", ignoreCase = true) }.takeIf { it >= 0 }
            if (dataIndex != null && dataIndex + 1 < segments.size) {
                val candidatePkg = segments[dataIndex + 1]
                if (isPackageInstalled(context, candidatePkg)) {
                    resolvedPackageCache[cacheKey] = candidatePkg
                    return candidatePkg
                }
            }
        }

        // Check dynamic index of installed apps
        ensureIndexBuilt(context)
        installedAppIndex[normalized]?.takeIf { it.isNotBlank() }?.let { pkg ->
            resolvedPackageCache[cacheKey] = pkg
            return pkg
        }

        resolvedPackageCache[cacheKey] = ""
        return null
    }

    fun resolveFolderAppIcon(context: Context, folderName: String, path: String? = null): Bitmap? {
        val packageName = resolvePackageName(context, folderName, path) ?: return null
        iconCache.get(packageName)?.let { return it }

        val bitmap = runCatching {
            context.packageManager.getApplicationIcon(packageName).toBitmap(
                width = ICON_SIZE_PX,
                height = ICON_SIZE_PX
            )
        }.getOrNull()

        if (bitmap != null) {
            iconCache.put(packageName, bitmap)
        }
        return bitmap
    }

    private fun isPackageInstalled(context: Context, packageName: String): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(packageName, 0)
        }
        true
    }.getOrDefault(false)

    private fun resolveSystemFolderPackageName(
        context: Context,
        folderName: String,
        path: String?
    ): String? {
        val intent = when (folderIconKind(folderName, path)) {
            FolderIconKind.Arcile -> return context.packageName
            FolderIconKind.Camera -> Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            FolderIconKind.Recordings -> Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION)
            else -> return null
        }
        val packageManager = context.packageManager
        val resolved = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.resolveActivity(
                    intent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            }
        }.getOrNull()
        resolved?.activityInfo?.packageName
            ?.takeUnless { it == "android" }
            ?.let { return it }

        val candidates: List<ResolveInfo> = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.queryIntentActivities(
                    intent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            }
        }.getOrDefault(emptyList())
        return candidates
            .sortedByDescending { resolveInfo ->
                if (
                    resolveInfo.activityInfo?.applicationInfo?.flags
                        ?.and(android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                ) 1 else 0
            }
            .firstNotNullOfOrNull { it.activityInfo?.packageName?.takeUnless { name -> name == "android" } }
    }

    private fun ensureIndexBuilt(context: Context) {
        if (indexBuilt) return
        synchronized(this) {
            if (indexBuilt) return
            buildInstalledAppIndex(context)
            indexBuilt = true
        }
    }

    private fun ensureInvalidationObservers(context: Context) {
        if (registeredApplicationContext === context) return
        synchronized(this) {
            if (registeredApplicationContext === context) return
            runCatching {
                val packageFilter = IntentFilter().apply {
                    addAction(Intent.ACTION_PACKAGE_ADDED)
                    addAction(Intent.ACTION_PACKAGE_REMOVED)
                    addAction(Intent.ACTION_PACKAGE_CHANGED)
                    addAction(Intent.ACTION_PACKAGE_REPLACED)
                    addDataScheme("package")
                }
                ContextCompat.registerReceiver(
                    context,
                    packageChangeReceiver,
                    packageFilter,
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )
            }
            runCatching {
                val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
                launcherApps?.registerCallback(launcherAppsCallback, Handler(Looper.getMainLooper()))
            }
            registeredApplicationContext = context
        }
    }

    private fun buildInstalledAppIndex(context: Context) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
                val userManager = context.getSystemService(Context.USER_SERVICE) as? UserManager
                if (launcherApps != null && userManager != null) {
                    val userProfiles = userManager.userProfiles
                    for (profile in userProfiles) {
                        val activities = launcherApps.getActivityList(null, profile)
                        for (activity in activities) {
                            val label = activity.label?.toString()
                            if (!label.isNullOrBlank()) {
                                val normalizedLabel = normalize(label)
                                recordLabel(normalizedLabel, activity.applicationInfo.packageName)
                            }
                        }
                    }
                    if (installedAppIndex.isNotEmpty()) return
                }
            }

            // Fallback for earlier versions or if LauncherApps unavailable
            val pm = context.packageManager
            @Suppress("DEPRECATION")
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            for (app in apps) {
                val label = pm.getApplicationLabel(app).toString()
                if (label.isNotBlank()) {
                    val normalizedLabel = normalize(label)
                    recordLabel(normalizedLabel, app.packageName)
                }
            }
        }
    }

    private fun recordLabel(normalizedLabel: String, packageName: String) {
        installedAppIndex.compute(normalizedLabel) { _, existing ->
            when {
                existing == null || existing == packageName -> packageName
                else -> ""
            }
        }
    }

    fun clearCache() {
        clearResolutionCaches()
    }

    private fun clearResolutionCaches() {
        iconCache.evictAll()
        resolvedPackageCache.clear()
        installedAppIndex.clear()
        indexBuilt = false
    }
}

@Composable
fun rememberFolderAppIcon(folderName: String, path: String? = null): Bitmap? {
    if (!LocalFolderIconsEnabled.current) return null
    val context = LocalContext.current.applicationContext
    val iconState by produceState<Bitmap?>(
        initialValue = null,
        context,
        folderName,
        path
    ) {
        value = withContext(FolderIconDispatcher) {
            FolderAppIconResolver.resolveFolderAppIcon(context, folderName, path)
        }
    }
    return iconState
}

private val FolderIconDispatcher = Dispatchers.IO.limitedParallelism(2)
