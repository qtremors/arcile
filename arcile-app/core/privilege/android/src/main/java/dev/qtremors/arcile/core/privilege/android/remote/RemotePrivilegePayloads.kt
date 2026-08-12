package dev.qtremors.arcile.core.privilege.android.remote

import android.os.Parcel
import android.os.Parcelable

data class RemoteHandshake(
    val protocolVersion: Int,
    val effectiveUid: Int,
    val pid: Int,
    val transport: String,
    val selinuxContext: String?,
    val capabilities: List<String>,
    val maximumDirectoryPageSize: Int
) : Parcelable {
    private constructor(parcel: Parcel) : this(
        protocolVersion = parcel.readInt(),
        effectiveUid = parcel.readInt(),
        pid = parcel.readInt(),
        transport = parcel.readString().orEmpty(),
        selinuxContext = parcel.readString(),
        capabilities = parcel.createStringArrayList().orEmpty(),
        maximumDirectoryPageSize = parcel.readInt()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(protocolVersion)
        parcel.writeInt(effectiveUid)
        parcel.writeInt(pid)
        parcel.writeString(transport)
        parcel.writeString(selinuxContext)
        parcel.writeStringList(capabilities)
        parcel.writeInt(maximumDirectoryPageSize)
    }

    override fun describeContents(): Int = 0

    companion object {
        @JvmField
        val CREATOR = object : Parcelable.Creator<RemoteHandshake> {
            override fun createFromParcel(parcel: Parcel) = RemoteHandshake(parcel)
            override fun newArray(size: Int): Array<RemoteHandshake?> = arrayOfNulls(size)
        }
    }
}

data class RemoteFileEntry(
    val path: String,
    val canonicalIdentity: String,
    val displayName: String,
    val type: String,
    val size: Long,
    val modifiedAtMillis: Long,
    val mode: Int,
    val readable: Boolean,
    val writable: Boolean
) : Parcelable {
    private constructor(parcel: Parcel) : this(
        path = parcel.readString().orEmpty(),
        canonicalIdentity = parcel.readString().orEmpty(),
        displayName = parcel.readString().orEmpty(),
        type = parcel.readString().orEmpty(),
        size = parcel.readLong(),
        modifiedAtMillis = parcel.readLong(),
        mode = parcel.readInt(),
        readable = parcel.readInt() != 0,
        writable = parcel.readInt() != 0
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(path)
        parcel.writeString(canonicalIdentity)
        parcel.writeString(displayName)
        parcel.writeString(type)
        parcel.writeLong(size)
        parcel.writeLong(modifiedAtMillis)
        parcel.writeInt(mode)
        parcel.writeInt(if (readable) 1 else 0)
        parcel.writeInt(if (writable) 1 else 0)
    }

    override fun describeContents(): Int = 0

    companion object {
        @JvmField
        val CREATOR = object : Parcelable.Creator<RemoteFileEntry> {
            override fun createFromParcel(parcel: Parcel) = RemoteFileEntry(parcel)
            override fun newArray(size: Int): Array<RemoteFileEntry?> = arrayOfNulls(size)
        }
    }
}

data class RemoteDirectoryPage(
    val entries: List<RemoteFileEntry>,
    val nextPageToken: String?
) : Parcelable {
    private constructor(parcel: Parcel) : this(
        entries = parcel.createTypedArrayList(RemoteFileEntry.CREATOR).orEmpty(),
        nextPageToken = parcel.readString()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeTypedList(entries)
        parcel.writeString(nextPageToken)
    }

    override fun describeContents(): Int = 0

    companion object {
        @JvmField
        val CREATOR = object : Parcelable.Creator<RemoteDirectoryPage> {
            override fun createFromParcel(parcel: Parcel) = RemoteDirectoryPage(parcel)
            override fun newArray(size: Int): Array<RemoteDirectoryPage?> = arrayOfNulls(size)
        }
    }
}

data class RemoteFilesystemStats(
    val totalBytes: Long,
    val availableBytes: Long,
    val freeBytes: Long
) : Parcelable {
    private constructor(parcel: Parcel) : this(
        totalBytes = parcel.readLong(),
        availableBytes = parcel.readLong(),
        freeBytes = parcel.readLong()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeLong(totalBytes)
        parcel.writeLong(availableBytes)
        parcel.writeLong(freeBytes)
    }

    override fun describeContents(): Int = 0

    companion object {
        @JvmField
        val CREATOR = object : Parcelable.Creator<RemoteFilesystemStats> {
            override fun createFromParcel(parcel: Parcel) = RemoteFilesystemStats(parcel)
            override fun newArray(size: Int): Array<RemoteFilesystemStats?> = arrayOfNulls(size)
        }
    }
}

data class RemoteOperationProgress(
    val operationId: String,
    val completedItems: Long,
    val totalItems: Long,
    val hasTotalItems: Boolean,
    val completedBytes: Long,
    val totalBytes: Long,
    val hasTotalBytes: Boolean,
    val currentPath: String?
) : Parcelable {
    private constructor(parcel: Parcel) : this(
        operationId = parcel.readString().orEmpty(),
        completedItems = parcel.readLong(),
        totalItems = parcel.readLong(),
        hasTotalItems = parcel.readInt() != 0,
        completedBytes = parcel.readLong(),
        totalBytes = parcel.readLong(),
        hasTotalBytes = parcel.readInt() != 0,
        currentPath = parcel.readString()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(operationId)
        parcel.writeLong(completedItems)
        parcel.writeLong(totalItems)
        parcel.writeInt(if (hasTotalItems) 1 else 0)
        parcel.writeLong(completedBytes)
        parcel.writeLong(totalBytes)
        parcel.writeInt(if (hasTotalBytes) 1 else 0)
        parcel.writeString(currentPath)
    }

    override fun describeContents(): Int = 0

    companion object {
        @JvmField
        val CREATOR = object : Parcelable.Creator<RemoteOperationProgress> {
            override fun createFromParcel(parcel: Parcel) = RemoteOperationProgress(parcel)
            override fun newArray(size: Int): Array<RemoteOperationProgress?> = arrayOfNulls(size)
        }
    }
}
