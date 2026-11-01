package dev.qtremors.arcile.core.vault.crypto

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.ClosedChannelException
import java.nio.channels.SeekableByteChannel

/**
 * Operation-scoped seekable storage for encoders that rewrite their output. Only authenticated
 * ciphertext reaches disk. The random key and page versions live in memory and cannot be recovered
 * after close or process death. This is scratch space, never a committed vault object format.
 *
 * One plaintext page is retained. Each eviction uses the existing vault AEAD primitive with a fresh
 * nonce and authenticates this channel, page index, and write generation. The in-memory generation
 * prevents stale pages, including pages removed by truncate, from being substituted within a run.
 * Instances are confined to the operation's I/O thread, like the archive encoders they support.
 */
class VaultEncryptedScratchChannel private constructor(
    private val file: File,
    private val maximumBytes: Long,
    private val checkActive: () -> Unit
) : SeekableByteChannel {
    private val disk = RandomAccessFile(file, "rw")
    private val key = VaultCryptography.randomBytes(VaultCryptography.KEY_SIZE_BYTES)
    private val identity = VaultCryptography.randomBytes(32)
    private val page = ByteArray(PAGE_BYTES)
    private val versions = mutableMapOf<Long, Long>()
    private var generation = 0L
    private var cachedIndex = -1L
    private var dirty = false
    private var cursor = 0L
    private var length = 0L
    private var open = true

    override fun isOpen(): Boolean = open

    override fun position(): Long { requireOpen(); return cursor }

    override fun position(newPosition: Long): SeekableByteChannel {
        requireOpen()
        require(newPosition >= 0L)
        if (newPosition > maximumBytes) throw IOException("Protected output exceeds its size limit")
        cursor = newPosition
        return this
    }

    override fun size(): Long { requireOpen(); return length }

    override fun read(destination: ByteBuffer): Int {
        requireOpen()
        if (!destination.hasRemaining()) return 0
        if (cursor >= length) return -1
        val total = minOf(destination.remaining().toLong(), length - cursor).toInt()
        var remaining = total
        while (remaining > 0) {
            load(cursor / PAGE_BYTES)
            val offset = (cursor % PAGE_BYTES).toInt()
            val count = minOf(remaining, PAGE_BYTES - offset)
            destination.put(page, offset, count)
            cursor += count
            remaining -= count
        }
        return total
    }

    override fun write(source: ByteBuffer): Int {
        requireOpen()
        val total = source.remaining()
        if (total.toLong() > maximumBytes - cursor) throw IOException("Protected output exceeds its size limit")
        while (source.hasRemaining()) {
            load(cursor / PAGE_BYTES)
            val offset = (cursor % PAGE_BYTES).toInt()
            val count = minOf(source.remaining(), PAGE_BYTES - offset)
            source.get(page, offset, count)
            dirty = true
            cursor += count
            length = maxOf(length, cursor)
        }
        return total
    }

    override fun truncate(size: Long): SeekableByteChannel {
        requireOpen()
        require(size >= 0L)
        if (size >= length) return this
        // Keep the surviving partial page, but erase bytes that a later extension could expose.
        if (size % PAGE_BYTES != 0L) {
            load(size / PAGE_BYTES)
            page.fill(0, (size % PAGE_BYTES).toInt())
            dirty = true
        } else {
            if (cachedIndex >= 0L && cachedIndex < size / PAGE_BYTES) flushPage()
            page.fill(0)
            cachedIndex = -1L
            dirty = false
        }
        val retainedPages = (size + PAGE_BYTES - 1L) / PAGE_BYTES
        versions.keys.removeAll { it >= retainedPages }
        disk.setLength(retainedPages * SLOT_BYTES)
        length = size
        cursor = minOf(cursor, size)
        return this
    }

    private fun load(index: Long) {
        checkActive()
        if (cachedIndex == index) return
        flushPage()
        page.fill(0)
        cachedIndex = -1L
        val version = versions[index]
        if (version != null) {
            val nonce = ByteArray(VaultCryptography.NONCE_SIZE_BYTES)
            val ciphertext = ByteArray(PAGE_BYTES + VaultCryptography.GCM_TAG_SIZE_BYTES)
            var plaintext: ByteArray? = null
            try {
                disk.seek(index * SLOT_BYTES)
                disk.readFully(nonce)
                disk.readFully(ciphertext)
                plaintext = VaultCryptography.open(key, VaultSealedValue(nonce, ciphertext), associatedData(index, version))
                if (plaintext.size != PAGE_BYTES) throw IOException("Invalid protected scratch page")
                plaintext.copyInto(page)
            } finally {
                nonce.fill(0)
                ciphertext.fill(0)
                plaintext?.fill(0)
            }
        }
        cachedIndex = index
    }

    private fun flushPage() {
        if (!dirty) return
        checkActive()
        val nextGeneration = Math.addExact(generation, 1L)
        val sealed = VaultCryptography.seal(key, page, associatedData(cachedIndex, nextGeneration))
        try {
            disk.seek(cachedIndex * SLOT_BYTES)
            disk.write(sealed.nonce)
            disk.write(sealed.ciphertext)
            generation = nextGeneration
            versions[cachedIndex] = nextGeneration
            dirty = false
        } finally {
            sealed.nonce.fill(0)
            sealed.ciphertext.fill(0)
        }
    }

    private fun associatedData(index: Long, version: Long): ByteArray =
        ByteBuffer.allocate(identity.size + 16).put(identity).putLong(index).putLong(version).array()

    private fun requireOpen() {
        if (!open) throw ClosedChannelException()
        checkActive()
    }

    override fun close() {
        if (!open) return
        open = false
        // There is no need to flush discarded output. In particular, cancellation must still clear it.
        try { disk.close() } finally {
            key.fill(0)
            identity.fill(0)
            page.fill(0)
            versions.clear()
            file.delete()
        }
    }

    companion object {
        private const val PAGE_BYTES = 64 * 1024
        private const val SLOT_BYTES = PAGE_BYTES.toLong() + VaultCryptography.NONCE_SIZE_BYTES + VaultCryptography.GCM_TAG_SIZE_BYTES
        private const val MAXIMUM_BYTES = 2L * 1024 * 1024 * 1024
        private const val PREFIX = "protected-"
        private const val SUFFIX = ".scratch"

        fun create(directory: File, maximumBytes: Long = MAXIMUM_BYTES, checkActive: () -> Unit = {}): VaultEncryptedScratchChannel {
            require(maximumBytes in 1..MAXIMUM_BYTES)
            checkActive()
            check(directory.isDirectory || directory.mkdirs()) { "Unable to create protected scratch space" }
            val file = File.createTempFile(PREFIX, SUFFIX, directory)
            try { return VaultEncryptedScratchChannel(file, maximumBytes, checkActive) }
            catch (error: Throwable) { file.delete(); throw error }
        }

        /** Call before opening any channels in this dedicated directory, never during an operation. */
        fun discardAbandoned(directory: File) {
            directory.listFiles()?.forEach { file ->
                if (file.isFile && file.name.startsWith(PREFIX) && file.name.endsWith(SUFFIX)) file.delete()
            }
        }
    }
}
