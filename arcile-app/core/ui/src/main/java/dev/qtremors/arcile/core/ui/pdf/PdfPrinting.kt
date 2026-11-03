package dev.qtremors.arcile.core.ui.pdf

import android.content.Context
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.core.net.toUri
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream

internal fun printPdf(
    context: Context,
    reference: String,
    title: String
): Result<Unit> {
    val appContext = context.applicationContext
    return printPdf(context, title) { openReference(appContext, reference) }
}

/**
 * Prints the bytes returned by [openInput]. The print framework may request several write passes,
 * so private sources supply a fresh protected stream each time instead of a cached plaintext copy.
 */
internal fun printPdf(
    context: Context,
    title: String,
    openInput: () -> InputStream
): Result<Unit> = runCatching {
    val printManager = context.getSystemService(PrintManager::class.java)
        ?: error("Printing is unavailable")
    printManager.print(title, ExistingPdfPrintAdapter(title, openInput), PrintAttributes.Builder().build())
}

private fun openReference(context: Context, reference: String): InputStream = reference.toUri().let { uri ->
    if (uri.scheme == "content") {
        context.contentResolver.openInputStream(uri) ?: error("Unable to read PDF")
    } else {
        val file = if (uri.scheme == "file") File(uri.path.orEmpty()) else File(reference)
        FileInputStream(file)
    }
}

private class ExistingPdfPrintAdapter(
    private val title: String,
    private val openInput: () -> InputStream
) : PrintDocumentAdapter() {
    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal,
        callback: LayoutResultCallback,
        extras: Bundle?
    ) {
        if (cancellationSignal.isCanceled) {
            callback.onLayoutCancelled()
            return
        }
        callback.onLayoutFinished(
            PrintDocumentInfo.Builder(title)
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                .build(),
            oldAttributes != newAttributes
        )
    }

    override fun onWrite(
        pages: Array<out PageRange>,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal,
        callback: WriteResultCallback
    ) {
        Thread {
            runCatching {
                openInput().use { input ->
                    FileOutputStream(destination.fileDescriptor).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        try {
                            while (true) {
                                if (cancellationSignal.isCanceled) return@Thread callback.onWriteCancelled()
                                val count = input.read(buffer)
                                if (count < 0) break
                                output.write(buffer, 0, count)
                            }
                            output.flush()
                        } finally {
                            buffer.fill(0)
                        }
                    }
                }
            }.fold(
                onSuccess = { callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES)) },
                onFailure = { callback.onWriteFailed(it.localizedMessage ?: "Unable to print PDF") }
            )
        }.apply {
            name = "ArcilePdfPrint"
            start()
        }
    }
}
