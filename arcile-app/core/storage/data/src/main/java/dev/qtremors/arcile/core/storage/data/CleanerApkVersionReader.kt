package dev.qtremors.arcile.core.storage.data

import java.io.File

internal object CleanerApkVersionReader {
    private val packageSegmentRegex = Regex("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*){1,}")

    fun parseManifestPackageAndVersion(bytes: ByteArray): Pair<String, Long>? {
        return runCatchingPreservingCancellation {
            if (bytes.size < 8) return@runCatchingPreservingCancellation null
            val buffer = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            val chunkType = buffer.short.toInt() and 0xFFFF
            if (chunkType != 0x0003) return@runCatchingPreservingCancellation null
            buffer.position(8)
            val poolType = buffer.short.toInt() and 0xFFFF
            if (poolType != 0x0001) return@runCatchingPreservingCancellation null
            buffer.position(buffer.position() + 2)
            buffer.int
            val stringCount = buffer.int
            buffer.int
            val flags = buffer.int
            val isUtf8 = (flags and (1 shl 8)) != 0
            val stringsStart = buffer.int
            buffer.int
            val stringOffsets = IntArray(stringCount)
            for (i in 0 until stringCount) {
                stringOffsets[i] = buffer.int
            }
            val strings = ArrayList<String>(stringCount)
            val stringsBase = 8 + stringsStart
            for (i in 0 until stringCount) {
                val pos = stringsBase + stringOffsets[i]
                if (pos >= bytes.size) continue
                if (isUtf8) {
                    var offset = pos
                    var charLen = bytes[offset].toInt() and 0xFF
                    offset++
                    if ((charLen and 0x80) != 0) {
                        charLen = ((charLen and 0x7F) shl 8) or (bytes[offset].toInt() and 0xFF)
                        offset++
                    }
                    var byteLen = bytes[offset].toInt() and 0xFF
                    offset++
                    if ((byteLen and 0x80) != 0) {
                        byteLen = ((byteLen and 0x7F) shl 8) or (bytes[offset].toInt() and 0xFF)
                        offset++
                    }
                    if (offset + byteLen <= bytes.size) {
                        strings.add(String(bytes, offset, byteLen, Charsets.UTF_8))
                    }
                } else {
                    var offset = pos
                    var charLen = (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
                    offset += 2
                    if ((charLen and 0x8000) != 0) {
                        val high = (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
                        charLen = ((charLen and 0x7FFF) shl 16) or high
                        offset += 2
                    }
                    val byteLen = charLen * 2
                    if (offset + byteLen <= bytes.size) {
                        strings.add(String(bytes, offset, byteLen, Charsets.UTF_16LE))
                    }
                }
            }
            val pkgName = strings.firstOrNull { it.contains('.') && packageSegmentRegex.matches(it) }
            if (pkgName != null) {
                pkgName to 0L
            } else null
        }.getOrNull()
    }

    fun extractApkPackageInfo(path: String): Pair<String, Long>? {
        return runCatchingPreservingCancellation {
            val file = File(path)
            if (!file.exists() || !file.name.endsWith(".apk", ignoreCase = true)) return@runCatchingPreservingCancellation null
            java.util.zip.ZipFile(file).use { zip ->
                val entry = zip.getEntry("AndroidManifest.xml") ?: return@runCatchingPreservingCancellation null
                zip.getInputStream(entry).use { stream ->
                    parseManifestPackageAndVersion(stream.readBytes())
                }
            }
        }.getOrNull()
    }
}
