package dev.qtremors.arcile.core.vault.data

import android.content.Context
import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import dev.qtremors.arcile.core.vault.crypto.FileVaultDirectory
import dev.qtremors.arcile.core.vault.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal abstract class VaultCatalogLayer(
    context: Context,
    dispatchers: ArcileDispatchers,
    applicationScope: CoroutineScope,
    portableLocationResolver: VaultPortableLocationResolver
) : VaultRepositoryFoundation(context, dispatchers, applicationScope, portableLocationResolver),
    VaultCatalog,
    VaultRepository {
    override val vaults: StateFlow<List<VaultSummary>> = mutableVaults.asStateFlow()
    override val unlockedVaultIds: StateFlow<Set<VaultId>> = mutableUnlockedVaultIds.asStateFlow()

    override suspend fun list(): List<VaultSummary> = vaults.value
    override suspend fun refresh() = refreshVaults()

    override suspend fun create(request: VaultCreationRequest): Result<VaultId> = try {
        VaultPasswordPolicy.requireAccepted(request.password, request.weakPasswordConfirmed)
        when (val location = request.location) {
            is VaultLocation.AppPrivate -> createAppPrivateVault(request.label, request.password)
            is VaultLocation.Portable -> {
                val pointer = ExternalVaultPointer(
                    vaultId = "pending",
                    volumeId = location.volumeId,
                    relativePath = location.relativePath,
                    cachedName = request.label,
                    cachedCreatedAtMillis = 0L
                )
                val target = portableLocationResolver.resolve(pointer)
                createUserFolderVault(target.access.directory.path, request.label, request.password)
            }
        }
    } catch (error: Throwable) {
        request.password.fill('\u0000')
        if (error is CancellationException) throw error
        Result.failure(error)
    }

    override suspend fun attach(request: VaultAttachmentRequest): Result<VaultId> =
        withContext(dispatchers.io) {
            lifecycleMutex.withLock {
                var openedSecret: ByteArray? = null
                try {
                    val target = portableLocationResolver.resolve(
                        ExternalVaultPointer(
                            vaultId = "pending",
                            volumeId = request.volumeId,
                            relativePath = request.relativePath,
                            cachedName = "",
                            cachedCreatedAtMillis = 0L
                        )
                    )
                    val attached = externalManager.attach(target)
                    if (locations.containsKey(attached.id.value) || locationRegistry.find(attached.id) != null) {
                        throw VaultFailure.DuplicateVault(attached.id)
                    }
                    openedSecret = headerCodec.open(attached.access, request.password).getOrThrow().masterKey
                    externalManager.register(attached)
                    locations[attached.id.value] = VaultLocationRecord(
                        attached.access,
                        VaultLocationKind.PORTABLE,
                        attached.location
                    )
                    refreshVaults()
                    Result.success(attached.id)
                } catch (error: Throwable) {
                    if (error is CancellationException) throw error
                    Result.failure(error)
                } finally {
                    openedSecret?.fill(0)
                    request.password.fill('\u0000')
                }
            }
        }

    override suspend fun removeRegistration(vaultId: VaultId): Result<Unit> = withContext(dispatchers.io) {
        lifecycleMutex.withLock {
            catchingCancellation {
                val location = locations[vaultId.value]
                    ?: locationRegistry.find(vaultId)?.let { portableLocationResolver.resolve(it) }?.let {
                        VaultLocationRecord(it.access, VaultLocationKind.PORTABLE, it.location)
                    }
                    ?: throw VaultFailure.NotFound(vaultId)
                if (location.kind != VaultLocationKind.PORTABLE) {
                    throw VaultFailure.Unavailable("App-private vaults cannot be removed without deleting their data")
                }
                if (importReservations.containsKey(vaultId.value)) throw VaultFailure.OperationInProgress()
                requestLock(vaultId)
                biometricStore.remove(vaultId)
                locationRegistry.remove(vaultId)
                locations.remove(vaultId.value)
                refreshVaults()
            }
        }
    }

    override suspend fun deletePermanently(vaultId: VaultId, confirmation: String): Result<Unit> =
        withContext(dispatchers.io) {
            lifecycleMutex.withLock {
                catchingCancellation {
                    val summary = vaults.value.firstOrNull { it.id == vaultId }
                        ?: throw VaultFailure.NotFound(vaultId)
                    if (confirmation != summary.name) throw VaultFailure.DestructiveConfirmationRequired()
                    if (importReservations.containsKey(vaultId.value)) throw VaultFailure.OperationInProgress()
                    val location = locations[vaultId.value] ?: throw VaultFailure.NotFound(vaultId)
                    val publicHeader = headerCodec.readPublic(location.access).getOrThrow()
                    if (publicHeader.id != vaultId) throw VaultFailure.StaleRegistration()
                    requestLock(vaultId)
                    biometricStore.remove(vaultId)
                    when (location.kind) {
                        VaultLocationKind.APP_PRIVATE ->
                            (location.access as FileVaultDirectory).directory.deleteRecursively()
                        VaultLocationKind.PORTABLE,
                        VaultLocationKind.USER_FOLDER -> {
                            val directory = (location.access as FileVaultDirectory).directory
                            directory.listFiles().orEmpty().forEach { it.deleteRecursively() }
                            locationRegistry.remove(vaultId)
                        }
                    }
                    locations.remove(vaultId.value)
                    refreshVaults()
                }
            }
        }

    override suspend fun refreshVaults() = withContext(dispatchers.io) {
        vaultRoot.mkdirs()
        val appPrivate = vaultRoot.listFiles().orEmpty().asSequence()
            .filter { it.isDirectory && !it.name.startsWith(STAGING_PREFIX) }
            .mapNotNull { directory ->
                headerCodec.readPublic(directory).getOrNull()?.let { header ->
                    locations[header.id.value] = VaultLocationRecord(
                        FileVaultDirectory(directory),
                        VaultLocationKind.APP_PRIVATE,
                        VaultLocation.AppPrivate(directory.name)
                    )
                    VaultSummary(
                        id = header.id,
                        name = header.publicName,
                        locationKind = VaultLocationKind.APP_PRIVATE,
                        createdAtMillis = header.createdAtMillis,
                        isUnlocked = sessions.containsKey(header.id.value),
                        headerFingerprint = header.headerFingerprint
                    )
                }
            }.toList()
        val privateIds = appPrivate.mapTo(mutableSetOf()) { it.id.value }
        val unavailable = mutableSetOf<VaultId>()
        val portable = locationRegistry.load().mapNotNull { pointer ->
            val pointerId = runCatching { VaultId.of(pointer.vaultId) }.getOrNull()
                ?: return@mapNotNull null
            if (pointerId.value in privateIds) return@mapNotNull null
            runCatching {
                val resolved = portableLocationResolver.resolve(pointer)
                val header = headerCodec.readPublic(resolved.access).getOrThrow()
                if (header.id != pointerId) throw VaultFailure.StaleRegistration()
                if (pointer.headerFingerprint.isNotBlank() &&
                    pointer.headerFingerprint != header.headerFingerprint
                ) throw VaultFailure.StaleRegistration()
                locations[header.id.value] = VaultLocationRecord(
                    resolved.access,
                    VaultLocationKind.PORTABLE,
                    resolved.location
                )
                locationRegistry.put(
                    ExternalVaultPointer(
                        vaultId = pointer.vaultId,
                        volumeId = resolved.location.volumeId,
                        relativePath = resolved.location.relativePath,
                        cachedName = header.publicName,
                        cachedCreatedAtMillis = header.createdAtMillis,
                        headerFingerprint = header.headerFingerprint
                    )
                )
                VaultSummary(
                    id = header.id,
                    name = header.publicName,
                    locationKind = VaultLocationKind.PORTABLE,
                    createdAtMillis = header.createdAtMillis,
                    isUnlocked = sessions.containsKey(header.id.value),
                    headerFingerprint = header.headerFingerprint
                )
            }.getOrElse { failure ->
                unavailable += pointerId
                VaultSummary(
                    id = pointerId,
                    name = pointer.cachedName,
                    locationKind = VaultLocationKind.PORTABLE,
                    createdAtMillis = pointer.cachedCreatedAtMillis,
                    isUnlocked = false,
                    isAvailable = false,
                    availability = when (failure) {
                        is VaultFailure.RemovableStorageMissing -> VaultAvailability.VOLUME_MISSING
                        is VaultFailure.StaleRegistration -> VaultAvailability.STALE_REGISTRATION
                        is VaultFailure.IntegrityFailed -> VaultAvailability.DAMAGED_HEADER
                        else -> VaultAvailability.FOLDER_MISSING
                    },
                    headerFingerprint = pointer.headerFingerprint
                )
            }
        }
        val summaries = (appPrivate + portable).distinctBy { it.id }.sortedBy { it.name.lowercase() }
        val liveIds = summaries.filter(VaultSummary::isAvailable).mapTo(mutableSetOf()) { it.id.value }
        locations.keys.removeIf { it !in liveIds }
        unavailable.forEach(::requestLock)
        mutableVaults.value = summaries
        publishUnlockedIds()
    }

    override suspend fun createAppPrivateVault(name: String, password: CharArray): Result<VaultId> =
        withContext(dispatchers.io) {
            lifecycleMutex.withLock {
                catchingCancellation {
                    val created = appPrivateManager.create(name, password)
                    locations[created.id.value] = VaultLocationRecord(
                        created.access,
                        VaultLocationKind.APP_PRIVATE,
                        VaultLocation.AppPrivate(created.access.directory.name)
                    )
                    sessions[created.id.value] = VaultSessionRecord(
                        created.id,
                        created.access,
                        created.masterSecret
                    )
                    refreshVaults()
                    created.id
                }.also { password.fill('\u0000') }
            }
        }

    override suspend fun createUserFolderVault(
        path: String,
        name: String,
        password: CharArray
    ): Result<VaultId> = withContext(dispatchers.io) {
        lifecycleMutex.withLock {
            catchingCancellation {
                val target = portableLocationResolver.identify(path)
                val created = externalManager.create(target, name, password)
                val secret = requireNotNull(created.masterSecret)
                locations[created.id.value] = VaultLocationRecord(
                    created.access,
                    VaultLocationKind.PORTABLE,
                    created.location
                )
                sessions[created.id.value] = VaultSessionRecord(created.id, created.access, secret)
                refreshVaults()
                created.id
            }.also { password.fill('\u0000') }
        }
    }

    override suspend fun attachExistingVault(path: String): Result<VaultId> = withContext(dispatchers.io) {
        lifecycleMutex.withLock {
            catchingCancellation {
                val target = portableLocationResolver.identify(path)
                val attached = externalManager.attach(target)
                if (locations.containsKey(attached.id.value) || locationRegistry.find(attached.id) != null) {
                    throw VaultFailure.DuplicateVault(attached.id)
                }
                externalManager.register(attached)
                locations[attached.id.value] = VaultLocationRecord(
                    attached.access,
                    VaultLocationKind.PORTABLE,
                    attached.location
                )
                refreshVaults()
                attached.id
            }
        }
    }
}
