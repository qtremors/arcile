package dev.qtremors.arcile.core.privilege.android.remote;

import dev.qtremors.arcile.core.privilege.android.remote.RemoteOperationProgress;

oneway interface IRemoteOperationCallback {
    void onProgress(in RemoteOperationProgress progress);
}
