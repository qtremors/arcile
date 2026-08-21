package dev.qtremors.arcile.core.storage.domain

/** MIME families a user can explicitly assign when handing a file to another app. */
enum class FileOpenAsType(val mimeType: String) {
    IMAGE("image/*"),
    VIDEO("video/*"),
    AUDIO("audio/*"),
    DOCUMENT("application/*"),
    ARCHIVE("application/zip"),
    TEXT("text/plain"),
    OTHER("*/*")
}
