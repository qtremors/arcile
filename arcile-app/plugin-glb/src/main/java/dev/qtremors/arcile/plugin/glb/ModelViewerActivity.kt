package dev.qtremors.arcile.plugin.glb

import android.content.ClipData
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dev.qtremors.arcile.plugin.api.PluginContract
import dev.qtremors.arcile.plugin.ui.ArcilePluginTheme

class ModelViewerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val target = resolvePluginModelTarget(intent)
        if (target == null) {
            Toast.makeText(this, getString(R.string.cannot_open_file, getString(R.string.unsupported_request)), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContent {
            ArcilePluginTheme {
                ModelViewerScreen(
                    reference = target.uri.toString(),
                    title = target.displayName,
                    sizeBytes = target.sizeBytes ?: 0L,
                    mimeType = target.mimeType,
                    onNavigateBack = ::finish,
                    onShare = { shareTarget(target) },
                    onOpenWith = { openTargetWithChooser(target) }
                )
            }
        }
    }

    internal fun resolvePluginModelTarget(source: Intent): PluginModelTarget? {
        if (source.action != PluginContract.ACTION_VIEW_FILE) return null
        val uri = source.data
            ?: source.getUriExtra(PluginContract.EXTRA_FILE_URI)
            ?: return null
        if (uri.scheme != "content") return null

        val displayName = source.getStringExtra(PluginContract.EXTRA_FILE_NAME)
            ?.takeIf { it.isNotBlank() }
            ?: queryColumn(uri, OpenableColumns.DISPLAY_NAME) { cursor, index -> cursor.getString(index) }
            ?: uri.lastPathSegment
            ?: "Model.glb"
        val mimeType = source.getStringExtra(PluginContract.EXTRA_MIME_TYPE)
            ?: source.type
            ?: contentResolver.getType(uri)
            ?: MODEL_GLB_MIME_TYPE
        if (!isSupportedPluginModelRequest(source.action, uri, mimeType, displayName)) return null

        val readable = runCatching {
            contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
        }.getOrDefault(false)
        if (!readable) return null

        return PluginModelTarget(
            uri = uri,
            displayName = displayName,
            mimeType = MODEL_GLB_MIME_TYPE,
            sizeBytes = queryColumn(uri, OpenableColumns.SIZE) { cursor, index -> cursor.getLong(index) }
        )
    }

    private fun shareTarget(target: PluginModelTarget) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = target.mimeType
            putExtra(Intent.EXTRA_STREAM, target.uri)
            clipData = ClipData.newUri(contentResolver, target.displayName, target.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching {
            startActivity(Intent.createChooser(sendIntent, getString(R.string.share)))
        }.onFailure {
            Toast.makeText(this, getString(R.string.no_app_found), Toast.LENGTH_SHORT).show()
        }
    }

    private fun openTargetWithChooser(target: PluginModelTarget) {
        val openIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(target.uri, target.mimeType)
            clipData = ClipData.newUri(contentResolver, target.displayName, target.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching {
            startActivity(Intent.createChooser(openIntent, getString(R.string.image_gallery_open_with)))
        }.onFailure {
            Toast.makeText(this, getString(R.string.no_app_found), Toast.LENGTH_SHORT).show()
        }
    }

    private fun <T> queryColumn(uri: Uri, column: String, read: (Cursor, Int) -> T): T? =
        runCatching {
            contentResolver.query(uri, arrayOf(column), null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val index = cursor.getColumnIndex(column)
                if (index < 0 || cursor.isNull(index)) null else read(cursor, index)
            }
        }.getOrNull()
}

@Suppress("DEPRECATION")
private fun Intent.getUriExtra(name: String): Uri? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(name, Uri::class.java)
    } else {
        getParcelableExtra(name)
    }

internal data class PluginModelTarget(
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long?
)

internal const val MODEL_GLB_MIME_TYPE = PluginContract.MIME_TYPE_GLB

internal fun isSupportedPluginModelRequest(
    action: String?,
    uri: Uri?,
    mimeType: String?,
    displayName: String?
): Boolean =
    action == PluginContract.ACTION_VIEW_FILE &&
        uri?.scheme == "content" &&
        (
            mimeType.equals(MODEL_GLB_MIME_TYPE, ignoreCase = true) ||
                displayName?.substringAfterLast('.', "")?.equals("glb", ignoreCase = true) == true
            )
