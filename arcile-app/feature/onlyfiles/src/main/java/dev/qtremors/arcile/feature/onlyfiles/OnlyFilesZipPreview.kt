package dev.qtremors.arcile.feature.onlyfiles

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.vault.domain.VaultNodeMetadata
import dev.qtremors.arcile.core.vault.domain.VaultNodeRef
import dev.qtremors.arcile.core.vault.domain.VaultSeekableReader
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.ClosedChannelException
import java.nio.channels.NonWritableChannelException
import java.nio.channels.SeekableByteChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.zip.ZipFile

@Composable
internal fun VaultZipPreview(
    node: VaultNodeMetadata,
    openReader: (VaultNodeRef) -> Result<VaultSeekableReader>
) {
    val contents by produceState<Result<List<String>>?>(null, node.ref, node.revision) {
        value = withContext(Dispatchers.IO) {
            val coroutineContext = currentCoroutineContext()
            runCatching {
                openReader(node.ref).getOrThrow().use { reader ->
                    VaultZipDirectoryChannel(reader) { coroutineContext.ensureActive() }.use { channel ->
                        ZipFile.builder().setSeekableByteChannel(channel).setIgnoreLocalFileHeader(true).get().use { zip ->
                            zip.entries.asSequence().take(MAX_PREVIEW_ENTRIES + 1).map { it.name }.toList()
                        }
                    }
                }
            }
        }
    }
    val names = contents?.getOrNull()
    Box(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(top = 72.dp),
        contentAlignment = Alignment.Center
    ) {
        when {
            contents == null -> CircularProgressIndicator()
            names == null -> Text(stringResource(R.string.onlyfiles_zip_unavailable), color = Color.White)
            names.isEmpty() -> Text(stringResource(R.string.onlyfiles_zip_empty), color = Color.White)
            else -> Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                if (names.size > MAX_PREVIEW_ENTRIES) {
                    Text(
                        stringResource(R.string.onlyfiles_zip_truncated, MAX_PREVIEW_ENTRIES),
                        color = Color.White,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                LazyColumn {
                    items(names.take(MAX_PREVIEW_ENTRIES)) { name ->
                        Text(
                            name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

private class VaultZipDirectoryChannel(
    private val reader: VaultSeekableReader,
    private val checkActive: () -> Unit
) : SeekableByteChannel {
    private var open = true
    private var offset = 0L
    private var remainingBudget = MAX_DIRECTORY_BYTES
    private val buffer = ByteArray(8192)

    override fun read(dst: ByteBuffer): Int {
        checkActive()
        if (!open) throw ClosedChannelException()
        if (!dst.hasRemaining()) return 0
        if (offset >= reader.sizeBytes) return -1
        if (remainingBudget <= 0) throw IOException("ZIP directory exceeds the preview limit")
        val count = minOf(dst.remaining(), buffer.size, remainingBudget, (reader.sizeBytes - offset).coerceAtMost(buffer.size.toLong()).toInt())
        val read = reader.readAt(offset, buffer, 0, count)
        if (read <= 0) throw IOException("ZIP directory is incomplete")
        dst.put(buffer, 0, read)
        offset += read
        remainingBudget -= read
        return read
    }

    override fun position(): Long = offset
    override fun position(newPosition: Long): SeekableByteChannel {
        if (!open) throw ClosedChannelException()
        require(newPosition >= 0L)
        offset = newPosition
        return this
    }

    override fun size(): Long = reader.sizeBytes
    override fun isOpen(): Boolean = open
    override fun close() {
        open = false
        buffer.fill(0)
    }
    override fun write(src: ByteBuffer): Int = throw NonWritableChannelException()
    override fun truncate(size: Long): SeekableByteChannel = throw NonWritableChannelException()
}

private const val MAX_PREVIEW_ENTRIES = 1_000
private const val MAX_DIRECTORY_BYTES = 2 * 1024 * 1024
