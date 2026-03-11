package dev.qtremors.arcile.domain

import java.io.File

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
     * Renames the file or directory at [path] to [newName].
     *
     * [newName] should be a bare name without path separators.
     *
     * @return [Result.success] with a [FileModel] for the renamed entry, or [Result.failure]
     *   if the rename failed.
     */
    suspend fun renameFile(path: String, newName: String): Result<FileModel>

    /**
     * Returns the root directory of the primary external storage volume.
     *
     * Equivalent to [android.os.Environment.getExternalStorageDirectory].
     */
    suspend fun getRootDirectory(): File

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
    suspend fun getRecentFiles(limit: Int = 10, minTimestamp: Long = 0L): Result<List<FileModel>>

    /**
     * Returns total and free storage bytes for the primary external storage volume.
     */
    suspend fun getStorageInfo(): Result<StorageInfo>

    /**
     * Returns per-category storage size information for the storage dashboard.
     *
     * Categories correspond to [FileCategories] (Images, Videos, Audio, Documents, etc.).
     *
     * > **Performance:** This queries the full MediaStore on each call. See TASKS.md C2.
     */
    suspend fun getCategoryStorageSizes(): Result<List<CategoryStorage>>

    /**
     * Returns all files belonging to [categoryName] from the MediaStore.
     *
     * @param categoryName A category name matching one of the keys in [FileCategories].
     */
    suspend fun getFilesByCategory(categoryName: String): Result<List<FileModel>>

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
    suspend fun searchFiles(query: String, pathScope: String? = null, filters: SearchFilters? = null): Result<List<FileModel>>

    // ─── Clipboard operations ─────────────────────────────────────────────────

    /**
     * Copies files at [sourcePaths] to [destinationPath].
     *
     * > **Warning:** Existing files at the destination are silently overwritten. See TASKS.md A5.
     *
     * @param sourcePaths Absolute paths of the files to copy.
     * @param destinationPath Absolute path of the target directory.
     */
    suspend fun copyFiles(sourcePaths: List<String>, destinationPath: String): Result<Unit>

    /**
     * Moves files at [sourcePaths] to [destinationPath].
     *
     * Attempts an atomic rename first; falls back to copy-then-delete when crossing
     * filesystem boundaries. Partial failures on the fallback path are not automatically
     * reverted. See TASKS.md A6.
     *
     * @param sourcePaths Absolute paths of the files to move.
     * @param destinationPath Absolute path of the target directory.
     */
    suspend fun moveFiles(sourcePaths: List<String>, destinationPath: String): Result<Unit>

    // ─── Trash subsystem ─────────────────────────────────────────────────────

    /**
     * Moves files at [paths] to the app trash (`.arcile_trash/` on external storage).
     *
     * Each moved file is paired with a JSON metadata sidecar so it can be restored or
     * identified later. The trash directory contains a `.nomedia` file to hide blobs from
     * media scanners.
     *
     * @param paths Absolute paths of the files or directories to trash.
     */
    suspend fun moveToTrash(paths: List<String>): Result<Unit>

    /**
     * Restores trashed items identified by [trashIds] to their original paths.
     *
     * @param trashIds List of [TrashMetadata.id] values identifying items to restore.
     */
    suspend fun restoreFromTrash(trashIds: List<String>): Result<Unit>

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
