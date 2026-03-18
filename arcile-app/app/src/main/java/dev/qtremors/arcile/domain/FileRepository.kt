package dev.qtremors.arcile.domain

import android.content.IntentSender
import kotlinx.coroutines.flow.Flow

/**
 * Thrown when a file operation (like trash or restore) requires native OS confirmation.
 * The caller should launch [intentSender] to proceed.
 */
class NativeConfirmationRequiredException(val intentSender: android.content.IntentSender) : Exception("Native confirmation required")

/**
 * Thrown when restoring files from trash requires a destination directory to be selected
 * (e.g., if the original parent directory was deleted).
 */
class DestinationRequiredException(val trashIds: List<String>) : Exception("Destination directory required for restoration")



/**
 * Repository abstraction for all file system operations.
 *
 * The single concrete implementation is [dev.qtremors.arcile.data.LocalFileRepository], which
 * uses a combination of [java.io.File], [android.os.StatFs], and the Android MediaStore
 * ContentProvider.
 *
 * All operations that can fail return a [Result], allowing callers to handle errors
 * without try/catch at the call site. IO-bound operations must be called from a coroutine
 * and are dispatched to [kotlinx.coroutines.Dispatchers.IO] internally.
 */
interface FileRepository {

    // ─── Directory listing ───────────────────────────────────────────────────

    /**
     * Lists and sorts the contents of [path].
     *
     * Sorting: directories are listed before files; within each group entries are sorted
     * alphabetically (case-insensitive).
     *
     * @param path Absolute path of the directory to list.
     * @return [Result.success] with a sorted [List] of [FileModel], or [Result.failure] if
     *   [path] does not exist or cannot be read.
     */
    suspend fun listFiles(path: String): Result<List<FileModel>>

    // ─── File mutations ──────────────────────────────────────────────────────

    /**
     * Creates a new directory named [name] inside [parentPath].
     *
     * @return [Result.success] with a [FileModel] for the new directory, or [Result.failure]
     *   if creation fails (e.g. duplicate name, permission denied).
     */
    suspend fun createDirectory(parentPath: String, name: String): Result<FileModel>

    /**
     * Creates a new empty file named [name] inside [parentPath].
     *
     * @return [Result.success] with a [FileModel] for the new file, or [Result.failure]
     *   if creation fails.
     */
    suspend fun createFile(parentPath: String, name: String): Result<FileModel>

    /**
     * Soft-deletes the file or directory at [path] by moving it to the app Trash.
     *
     * Implementations may move the file/directory to Trash rather than permanently removing it.
     * [dev.qtremors.arcile.data.LocalFileRepository.deleteFile] is an example of soft-delete:
     * it delegates to [moveToTrash] and the item can be restored via [restoreFromTrash] or
     * permanently removed via [emptyTrash].
     *
     * No space reclamation is guaranteed until the Trash is emptied. Recursive removal of
     * directory contents is handled by the Trash subsystem.
     *
     * @return [Result.success] if the item was moved to Trash, [Result.failure] otherwise.
     */
    suspend fun deleteFile(path: String): Result<Unit>

    /**
     * Permanently deletes the files or directories at [paths].
     *
     * Unlike [deleteFile] or [moveToTrash], this bypasses the trash bin and 
     * immediately removes the files from the filesystem.
     *
     * @return [Result.success] if all items were successfully deleted, [Result.failure] otherwise.
     */
    suspend fun deletePermanently(paths: List<String>): Result<Unit>

    /**
     * Renames the file or directory at [path] to [newName].
     *
     * [newName] should be a bare name without path separators.
     *
     * @return [Result.success] with a [FileModel] for the renamed entry, or [Result.failure]
     *   if the rename failed.
     */
    suspend fun renameFile(path: String, newName: String): Result<FileModel>

    fun observeStorageVolumes(): Flow<List<StorageVolume>>

    /**
     * Returns a list of all currently available storage volumes.
     */
    suspend fun getStorageVolumes(): Result<List<StorageVolume>>

    /**
     * Resolves the storage volume that contains the given path.
     */
    suspend fun getVolumeForPath(path: String): Result<StorageVolume>

    /**
     * Returns a map of standard folders (e.g., DCIM, Downloads, Pictures).
     */
    fun getStandardFolders(): Map<String, String?>

    // ─── Queries ─────────────────────────────────────────────────────────────

    /**
     * Returns a list of recently modified files from the MediaStore.
     *
     * > **Performance:** Large [limit] values on devices with many files can be slow.
     * > Use a bounded limit (e.g. 100) for home screen previews. See TASKS.md C1.
     *
     * @param limit Maximum number of results to return. Defaults to `10`.
     * @param minTimestamp Only include files modified at or after this Unix epoch millisecond
     *   value. Defaults to `0` (no lower bound).
     * @return [Result.success] with a [List] of [FileModel] sorted by last-modified descending.
     */
    suspend fun getRecentFiles(
        scope: StorageScope = StorageScope.AllStorage,
        limit: Int = 10,
        minTimestamp: Long = 0L
    ): Result<List<FileModel>>

    /**
     * Returns storage information including all detected storage volumes.
     */
    suspend fun getStorageInfo(scope: StorageScope = StorageScope.AllStorage): Result<StorageInfo>

    /**
     * Returns per-category storage size information for the storage dashboard.
     *
     * Categories correspond to [FileCategories] (Images, Videos, Audio, Documents, etc.).
     *
     * > **Performance:** This queries the full MediaStore on each call. See TASKS.md C2.
     */
    suspend fun getCategoryStorageSizes(scope: StorageScope = StorageScope.AllStorage): Result<List<CategoryStorage>>

    /**
     * Returns all files belonging to [categoryName] from the MediaStore.
     *
     * @param categoryName A category name matching one of the keys in [FileCategories].
     */
    suspend fun getFilesByCategory(scope: StorageScope, categoryName: String): Result<List<FileModel>>

    /**
     * Searches for files matching [query] and optional [filters].
     *
     * Results are sourced from MediaStore and, if [pathScope] is provided, from a filesystem
     * walk of that directory.
     *
     * @param query Search term matched against file names.
     * @param pathScope Optional absolute path to constrain a filesystem-based search.
     *   `null` means MediaStore-wide search.
     * @param filters Optional [SearchFilters] to restrict results by type, size, or date.
     */
    suspend fun searchFiles(
        query: String,
        scope: StorageScope = StorageScope.AllStorage,
        filters: SearchFilters? = null
    ): Result<List<FileModel>>

    // ─── Clipboard operations ─────────────────────────────────────────────────

    /**
     * Detects name conflicts that would occur when pasting [sourcePaths] into [destinationPath].
     *
     * Returns a [FileConflict] for every source file whose name already exists at the
     * destination. Files that would be pasted to themselves (same-folder paste) are excluded
     * because they are always auto-renamed.
     *
     * @param sourcePaths Absolute paths of the files to paste.
     * @param destinationPath Absolute path of the target directory.
     */
    suspend fun detectCopyConflicts(sourcePaths: List<String>, destinationPath: String): Result<List<FileConflict>>

    /**
     * Copies files at [sourcePaths] to [destinationPath].
     *
     * When [resolutions] is provided, each conflicting file is handled according to its
     * [ConflictResolution]: KEEP_BOTH auto-renames, REPLACE overwrites, SKIP ignores.
     * Files without an entry in [resolutions] are copied normally (no overwrite).
     *
     * @param sourcePaths Absolute paths of the files to copy.
     * @param destinationPath Absolute path of the target directory.
     * @param resolutions Per-file conflict resolutions keyed by source absolute path.
     */
    suspend fun copyFiles(sourcePaths: List<String>, destinationPath: String, resolutions: Map<String, ConflictResolution> = emptyMap()): Result<Unit>

    /**
     * Moves files at [sourcePaths] to [destinationPath].
     *
     * Attempts an atomic rename first; falls back to copy-then-delete when crossing
     * filesystem boundaries. When [resolutions] is provided, conflicts are resolved
     * per [ConflictResolution].
     *
     * @param sourcePaths Absolute paths of the files to move.
     * @param destinationPath Absolute path of the target directory.
     * @param resolutions Per-file conflict resolutions keyed by source absolute path.
     */
    suspend fun moveFiles(sourcePaths: List<String>, destinationPath: String, resolutions: Map<String, ConflictResolution> = emptyMap()): Result<Unit>

    // ─── Trash subsystem ─────────────────────────────────────────────────────

    /**
     * Moves files at [paths] to the app trash (`.arcile/.trash` on external storage).
     *
     * Each moved file is paired with a JSON metadata sidecar so it can be restored or
     * identified later. The trash directory contains a `.nomedia` file to hide blobs from
     * media scanners.
     *
     * @param paths Absolute paths of the files or directories to trash.
     */
    suspend fun moveToTrash(paths: List<String>): Result<Unit>

    /**
     * Restores trashed items identified by [trashIds] to their original paths, or to
     * [destinationPath] if provided.
     *
     * @param trashIds List of [TrashMetadata.id] values identifying items to restore.
     * @param destinationPath Optional absolute path of the target directory. If not provided, items are restored to their original paths.
     */
    suspend fun restoreFromTrash(trashIds: List<String>, destinationPath: String? = null): Result<Unit>

    /**
     * Permanently deletes all items currently in the trash.
     */
    suspend fun emptyTrash(): Result<Unit>

    /**
     * Returns a list of all current trash entries parsed from their JSON metadata sidecar files.
     *
     * Orphaned metadata files (where the corresponding blob is missing) are silently removed.
     * See TASKS.md A4 for the associated correctness concern.
     */
    suspend fun getTrashFiles(): Result<List<TrashMetadata>>
}
