package dev.qtremors.arcile.domain

sealed class FileOperationException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class AccessDenied(message: String = "Permission denied", cause: Throwable? = null) : FileOperationException(message, cause)
    class IOError(message: String = "I/O error occurred", cause: Throwable? = null) : FileOperationException(message, cause)
    class NotFound(message: String = "File not found", cause: Throwable? = null) : FileOperationException(message, cause)
    class Unknown(message: String = "Unknown error occurred", cause: Throwable? = null) : FileOperationException(message, cause)
}
