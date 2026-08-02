package dev.qtremors.arcile.core.ui.image

import android.content.Context
import android.os.Build
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ApkPresentationMetadata(
    val label: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long
)

object ApkPresentationMetadataReader {
    private const val MAX_CACHE_ENTRIES = 128
    private val cacheLock = Any()
    private val cache = object : LinkedHashMap<String, ApkPresentationMetadata?>(
        MAX_CACHE_ENTRIES,
        0.75f,
        true
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, ApkPresentationMetadata?>?
        ): Boolean = size > MAX_CACHE_ENTRIES
    }

    suspend fun read(context: Context, file: File): ApkPresentationMetadata? =
        withContext(Dispatchers.IO) {
            if (!file.isFile || file.length() > ThumbnailPolicy.MAX_APK_BYTES) {
                return@withContext null
            }
            val key = "${file.absolutePath}:${file.length()}:${file.lastModified()}"
            synchronized(cacheLock) {
                if (cache.containsKey(key)) return@withContext cache[key]
            }
            val metadata = try {
                ThumbnailWorkCoordinator.withExpensivePermit {
                    withApkPreviewFile(context, file) { apkFile ->
                        readPackageMetadata(context, apkFile)
                    }
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                null
            }
            synchronized(cacheLock) { cache[key] = metadata }
            metadata
        }

    @Suppress("DEPRECATION")
    private fun readPackageMetadata(context: Context, apkFile: File): ApkPresentationMetadata? {
        val packageManager = context.packageManager
        val packageInfo = packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
            ?: return null
        val appInfo = packageInfo.applicationInfo ?: return null
        appInfo.sourceDir = apkFile.absolutePath
        appInfo.publicSourceDir = apkFile.absolutePath
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            packageInfo.versionCode.toLong()
        }
        return ApkPresentationMetadata(
            label = appInfo.loadLabel(packageManager).toString()
                .ifBlank { packageInfo.packageName },
            packageName = packageInfo.packageName,
            versionName = packageInfo.versionName?.ifBlank { null } ?: versionCode.toString(),
            versionCode = versionCode
        )
    }
}

internal inline fun <T> withApkPreviewFile(
    context: Context,
    source: File,
    block: (File) -> T?
): T? {
    if (source.extension.equals("apk", ignoreCase = true)) return block(source)
    if (source.extension.lowercase() !in SPLIT_APK_CONTAINER_EXTENSIONS) return null

    val temporaryApk = File.createTempFile("arcile_apk_preview_", ".apk", context.cacheDir)
    return try {
        ZipFile(source).use { archive ->
            val entry = selectApkPreviewEntry(archive) ?: return null
            require(entry.size <= MAX_EMBEDDED_APK_BYTES || entry.size < 0L)
            require(
                entry.size < 0L ||
                    entry.compressedSize <= 0L ||
                    entry.size / entry.compressedSize <= MAX_COMPRESSION_RATIO
            )
            archive.getInputStream(entry).use { input ->
                FileOutputStream(temporaryApk).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var totalBytes = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        totalBytes += count
                        require(totalBytes <= MAX_EMBEDDED_APK_BYTES)
                        output.write(buffer, 0, count)
                    }
                }
            }
        }
        block(temporaryApk)
    } finally {
        temporaryApk.delete()
    }
}

private fun selectApkPreviewEntry(archive: ZipFile): ZipEntry? =
    archive.entries().asSequence()
        .filter { !it.isDirectory && it.name.endsWith(".apk", ignoreCase = true) }
        .minWithOrNull(
            compareBy<ZipEntry>(
                { apkPreviewEntryPriority(it.name) },
                { it.name.length },
                { it.name.lowercase() }
            )
        )

internal fun apkPreviewEntryPriority(entryName: String): Int {
    val name = File(entryName).name.lowercase()
    return when {
        name == "base.apk" -> 0
        name == "master.apk" -> 1
        name.contains("base-master") || name.contains("base_master") -> 2
        name.contains("base") -> 3
        name.contains("universal") || name.contains("standalone") -> 4
        else -> 5
    }
}

private val SPLIT_APK_CONTAINER_EXTENSIONS = setOf("apks", "apkm", "xapk")
private const val MAX_EMBEDDED_APK_BYTES = 128L * 1024L * 1024L
private const val MAX_COMPRESSION_RATIO = 200L
