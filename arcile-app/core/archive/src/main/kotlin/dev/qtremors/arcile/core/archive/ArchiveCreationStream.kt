package dev.qtremors.arcile.core.archive

import java.io.IOException
import java.io.InputStream

/** The same bounded source contract applies to local files and protected readers in every writer. */
internal fun copyArchiveEntry(
    size: Long,
    checkActive: () -> Unit,
    onBytes: (Int) -> Unit,
    open: () -> InputStream,
    write: (ByteArray, Int) -> Unit
) {
    checkActive()
    open().use { input ->
        val buffer = ByteArray(32 * 1024)
        try {
            var remaining = size
            while (remaining > 0L) {
                checkActive()
                val count = input.read(buffer, 0, minOf(remaining, buffer.size.toLong()).toInt())
                if (count <= 0) throw IOException("An archive source ended before its declared size")
                write(buffer, count)
                remaining -= count
                onBytes(count)
            }
            checkActive()
            if (input.read() != -1) throw IOException("An archive source grew while it was being read")
        } finally { buffer.fill(0) }
    }
}
