package dev.qtremors.arcile.core.privilege.android.remote;

import android.os.ParcelFileDescriptor;
import dev.qtremors.arcile.core.privilege.android.remote.IRemoteOperationCallback;
import dev.qtremors.arcile.core.privilege.android.remote.RemoteDirectoryPage;
import dev.qtremors.arcile.core.privilege.android.remote.RemoteFileEntry;
import dev.qtremors.arcile.core.privilege.android.remote.RemoteFilesystemStats;
import dev.qtremors.arcile.core.privilege.android.remote.RemoteHandshake;

interface IPrivilegedFileService {
    void destroy() = 16777114;
    RemoteHandshake handshake() = 1;
    RemoteFileEntry canonicalizeAndLstat(String path) = 2;
    RemoteDirectoryPage listDirectory(String path, String pageToken, int pageSize) = 3;
    RemoteFilesystemStats filesystemStats(String path) = 4;
    RemoteFileEntry createFile(String path) = 5;
    RemoteFileEntry createDirectory(String path) = 6;
    ParcelFileDescriptor openForReading(String path) = 7;
    ParcelFileDescriptor openForWriting(String path, boolean append) = 8;
    void rename(String sourcePath, String destinationPath) = 9;
    void copy(String sourcePath, String destinationPath, String operationId, IRemoteOperationCallback callback) = 10;
    void move(String sourcePath, String destinationPath, String operationId, IRemoteOperationCallback callback) = 11;
    void delete(String path) = 12;
    void deleteRecursively(String path, String operationId, IRemoteOperationCallback callback) = 13;
    void secureOverwrite(String path, String operationId, IRemoteOperationCallback callback) = 14;
    void updateTimestamps(String path, long accessedAtMillis, long modifiedAtMillis) = 15;
    void cancel(String operationId) = 16;
}
