package dev.qtremors.arcile.core.storage.data

import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import dev.qtremors.arcile.core.storage.data.source.FileSystemDataSource
import dev.qtremors.arcile.core.storage.domain.CachedStorageCleanerResult
import dev.qtremors.arcile.core.storage.domain.CleanerCandidate
import dev.qtremors.arcile.core.storage.domain.CleanerGroup
import dev.qtremors.arcile.core.storage.domain.CleanerGroupType
import dev.qtremors.arcile.core.storage.domain.CleanerRiskLevel
import dev.qtremors.arcile.core.storage.domain.CleanerRiskReason
import dev.qtremors.arcile.core.storage.domain.FileCategories
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.StorageCleanerResult
import dev.qtremors.arcile.core.storage.domain.StorageCleanerRules
import dev.qtremors.arcile.core.storage.domain.StorageCleanerScanLimits
import dev.qtremors.arcile.core.storage.domain.StorageCleanerScanner
import dev.qtremors.arcile.core.storage.domain.StorageCleanerScanPhase
import dev.qtremors.arcile.core.storage.domain.StorageCleanerScanProgress
import dev.qtremors.arcile.core.storage.domain.StorageCleanerScanUpdate
import dev.qtremors.arcile.core.storage.domain.StorageNodeRef
import dev.qtremors.arcile.core.storage.domain.isPrivileged
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.Locale
import java.util.PriorityQueue
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class DefaultStorageCleanerScanner @Inject constructor(
    private val dispatchers: ArcileDispatchers,
    private val snapshotStore: StorageCleanerSnapshotStore? = null,
    private val fileSystemDataSource: Provider<FileSystemDataSource>? = null
) : StorageCleanerScanner {
    override suspend fun cachedScan(
        rootPaths: List<String>,
        limits: StorageCleanerScanLimits,
        rules: StorageCleanerRules
    ): StorageCleanerResult? =
        cachedScanWithMetadata(rootPaths, limits, rules)?.result

    override suspend fun cachedScanWithMetadata(
        rootPaths: List<String>,
        limits: StorageCleanerScanLimits,
        rules: StorageCleanerRules
    ): CachedStorageCleanerResult? =
        cachedScanForGroups(rootPaths, CleanerGroupType.entries.toSet(), limits, rules)

    override suspend fun cachedScanForGroups(
        rootPaths: List<String>,
        groupTypes: Set<CleanerGroupType>,
        limits: StorageCleanerScanLimits,
        rules: StorageCleanerRules
    ): CachedStorageCleanerResult? {
        val store = snapshotStore ?: return null
        val allGroups = CleanerGroupType.entries.toSet()
        val cached = (
            store.get(rootPaths, limits, rules, groupTypes)
                ?: if (groupTypes != allGroups) store.get(rootPaths, limits, rules, allGroups) else null
            ) ?: return null
        return cached.copy(
            result = cached.result.copy(
                groups = cached.result.groups.filter { it.type in groupTypes }
            ).withoutStaleCandidates()
        )
    }

    override suspend fun cachedNodeScanWithMetadata(
        roots: List<StorageNodeRef>,
        limits: StorageCleanerScanLimits,
        rules: StorageCleanerRules
    ): CachedStorageCleanerResult? =
        cachedNodeScanForGroups(roots, CleanerGroupType.entries.toSet(), limits, rules)

    override suspend fun cachedNodeScanForGroups(
        roots: List<StorageNodeRef>,
        groupTypes: Set<CleanerGroupType>,
        limits: StorageCleanerScanLimits,
        rules: StorageCleanerRules
    ): CachedStorageCleanerResult? {
        requireExplicitProtectedRoots(roots)
        val store = snapshotStore ?: return null
        val allGroups = CleanerGroupType.entries.toSet()
        val cached = (
            store.getNodes(roots, limits, rules, groupTypes)
                ?: if (groupTypes != allGroups) store.getNodes(roots, limits, rules, allGroups) else null
            ) ?: return null
        return cached.copy(
            result = cached.result.copy(
                groups = cached.result.groups.filter { it.type in groupTypes }
            )
        )
    }

    override suspend fun scan(
        rootPaths: List<String>,
        now: Long,
        limits: StorageCleanerScanLimits,
        rules: StorageCleanerRules
    ): StorageCleanerResult = withContext(dispatchers.storage) {
        performScan(
            roots = CleanerScanRoots.Local(rootPaths),
            now = now,
            limits = limits,
            rules = rules,
            requestedGroups = CleanerGroupType.entries.toSet(),
            onUpdate = {}
        )
    }

    override suspend fun scanNodes(
        roots: List<StorageNodeRef>,
        now: Long,
        limits: StorageCleanerScanLimits,
        rules: StorageCleanerRules
    ): StorageCleanerResult = withContext(dispatchers.storage) {
        requireExplicitProtectedRoots(roots)
        performScan(
            roots = CleanerScanRoots.Nodes(roots),
            now = now,
            limits = limits,
            rules = rules,
            requestedGroups = CleanerGroupType.entries.toSet(),
            onUpdate = {}
        )
    }

    override fun scanUpdates(
        rootPaths: List<String>,
        now: Long,
        limits: StorageCleanerScanLimits,
        rules: StorageCleanerRules
    ): Flow<StorageCleanerScanUpdate> =
        scanGroupUpdates(rootPaths, CleanerGroupType.entries.toSet(), now, limits, rules)

    override fun scanGroupUpdates(
        rootPaths: List<String>,
        groupTypes: Set<CleanerGroupType>,
        now: Long,
        limits: StorageCleanerScanLimits,
        rules: StorageCleanerRules
    ): Flow<StorageCleanerScanUpdate> = flow {
        val requestedGroups = groupTypes.ifEmpty { CleanerGroupType.entries.toSet() }
        val result = performScan(
            CleanerScanRoots.Local(rootPaths),
            now,
            limits,
            rules,
            requestedGroups
        ) { update -> emit(update) }
        emit(
            StorageCleanerScanUpdate(
                progress = StorageCleanerScanProgress(
                    phase = StorageCleanerScanPhase.Complete,
                    scannedFiles = result.scannedFiles,
                    progressFraction = 1f,
                    estimatedRemainingMillis = 0L,
                    completedGroups = requestedGroups
                ),
                result = result
            )
        )
    }.flowOn(dispatchers.storage)

    override fun scanNodeUpdates(
        roots: List<StorageNodeRef>,
        now: Long,
        limits: StorageCleanerScanLimits,
        rules: StorageCleanerRules
    ): Flow<StorageCleanerScanUpdate> =
        scanNodeGroupUpdates(roots, CleanerGroupType.entries.toSet(), now, limits, rules)

    override fun scanNodeGroupUpdates(
        roots: List<StorageNodeRef>,
        groupTypes: Set<CleanerGroupType>,
        now: Long,
        limits: StorageCleanerScanLimits,
        rules: StorageCleanerRules
    ): Flow<StorageCleanerScanUpdate> = flow {
        requireExplicitProtectedRoots(roots)
        val requestedGroups = groupTypes.ifEmpty { CleanerGroupType.entries.toSet() }
        val result = performScan(
            CleanerScanRoots.Nodes(roots),
            now,
            limits,
            rules,
            requestedGroups
        ) { update -> emit(update) }
        emit(result.completeUpdate(requestedGroups))
    }.flowOn(dispatchers.storage)

    override suspend fun invalidateStorageCleaner(paths: Collection<String>) {
        snapshotStore?.invalidate(paths)
    }

    override suspend fun invalidateStorageCleanerNodes(nodes: Collection<StorageNodeRef>) {
        if (nodes.isEmpty()) {
            snapshotStore?.invalidateNodes(emptyList())
            return
        }
        val local = nodes.filterNot(StorageNodeRef::isPrivileged)
        if (local.isNotEmpty()) {
            snapshotStore?.invalidate(local.map { it.displayPath.absolutePath })
        }
        val protected = nodes.filter(StorageNodeRef::isPrivileged)
        if (protected.isNotEmpty()) snapshotStore?.invalidateNodes(protected)
    }

    private suspend fun performScan(
        roots: CleanerScanRoots,
        now: Long,
        limits: StorageCleanerScanLimits,
        rules: StorageCleanerRules,
        requestedGroups: Set<CleanerGroupType>,
        onUpdate: suspend (StorageCleanerScanUpdate) -> Unit
    ): StorageCleanerResult {
        val normalizedRules = rules.normalized()
        val requestedActiveGroups = requestedGroups.filterTo(linkedSetOf()) {
            normalizedRules.section(it).enabled
        }
        if (requestedActiveGroups.isEmpty()) {
            val emptyResult = currentResult(emptyMap(), requestedGroups, scannedFiles = 0, isPartial = false)
            storeSnapshot(roots, limits, rules, requestedGroups, emptyResult)
            return emptyResult
        }
        val lightweightGroups = CleanerGroupType.entries.toSet() - CleanerGroupType.Duplicates
        val workGroups = when {
            CleanerGroupType.Duplicates in requestedGroups -> requestedGroups
            else -> lightweightGroups
        }
        val activeGroups = workGroups.filterTo(linkedSetOf()) { normalizedRules.section(it).enabled }
        val classificationGroups = activeGroups - CleanerGroupType.Duplicates
        val needsDuplicates = CleanerGroupType.Duplicates in activeGroups
        val duplicateFiles = if (needsDuplicates) ArrayList<FileSnapshot>() else null
        val accumulators = activeGroups.associateWith { CandidateAccumulator(limits.maxCandidatesPerGroup) }
        val largeFileThreshold = normalizedRules.section(CleanerGroupType.LargeFiles)
            .largeFileThresholdBytes ?: limits.largeFileThresholdBytes
        val oldDownloadAgeMs = normalizedRules.section(CleanerGroupType.OldDownloads)
            .oldDownloadAgeMs ?: limits.oldDownloadAgeMs
        val startedAtNanos = System.nanoTime()
        var lastProgressNanos = startedAtNanos
        var monotonicInventoryProgress = 0f

        val walkResult = walk(
            roots = roots,
            limits = limits,
            includeDownloadClassification = CleanerGroupType.OldDownloads in activeGroups
        ) { snapshot, scannedFiles, visitedEntries, pendingEntries ->
            if (snapshot.absolutePath !in normalizedRules.ignoredPaths) {
                if (!snapshot.isDirectory) duplicateFiles?.add(snapshot)
                val matchingGroups = matchingGroups(
                    file = snapshot,
                    requestedGroups = classificationGroups,
                    rules = normalizedRules,
                    now = now,
                    largeFileThreshold = largeFileThreshold,
                    oldDownloadAgeMs = oldDownloadAgeMs
                )
                if (matchingGroups.isNotEmpty()) {
                    val risk = classifyRisk(snapshot)
                    val candidate = snapshot.toCandidate(matchingGroups, risk)
                    matchingGroups.forEach { accumulators.getValue(it).add(candidate) }
                }
            }

            val currentNanos = System.nanoTime()
            if (scannedFiles % PROGRESS_FILE_INTERVAL == 0 ||
                currentNanos - lastProgressNanos >= PROGRESS_TIME_INTERVAL_NANOS
            ) {
                val observedFraction = visitedEntries.toFloat() /
                    (visitedEntries + pendingEntries).coerceAtLeast(1).toFloat()
                monotonicInventoryProgress = maxOf(monotonicInventoryProgress, observedFraction)
                val progress = monotonicInventoryProgress.coerceIn(0f, 0.98f) *
                    if (needsDuplicates) INVENTORY_DUPLICATE_WEIGHT else INVENTORY_ONLY_WEIGHT
                onUpdate(
                    StorageCleanerScanUpdate(
                        progress = progress(
                            phase = StorageCleanerScanPhase.Discovering,
                            scannedFiles = scannedFiles,
                            fraction = progress,
                            startedAtNanos = startedAtNanos
                        ),
                        result = currentResult(accumulators, requestedGroups, scannedFiles, isPartial = false)
                    )
                )
                lastProgressNanos = currentNanos
            }
        }

        val completedWithoutDuplicates = (activeGroups - CleanerGroupType.Duplicates).intersect(requestedGroups)
        if (needsDuplicates) {
            onUpdate(
                StorageCleanerScanUpdate(
                    progress = progress(
                        phase = StorageCleanerScanPhase.CheckingDuplicates,
                        scannedFiles = walkResult.scannedFiles,
                        fraction = INVENTORY_DUPLICATE_WEIGHT,
                        startedAtNanos = startedAtNanos,
                        completedGroups = completedWithoutDuplicates
                    ),
                    result = currentResult(
                        accumulators,
                        requestedGroups,
                        walkResult.scannedFiles,
                        walkResult.isPartial
                    )
                )
            )
            val duplicateGroupKeys = findDuplicateGroupKeys(duplicateFiles.orEmpty()) { duplicateFraction ->
                val overallFraction = INVENTORY_DUPLICATE_WEIGHT +
                    (1f - INVENTORY_DUPLICATE_WEIGHT) * duplicateFraction.coerceIn(0f, 0.98f)
                onUpdate(
                    StorageCleanerScanUpdate(
                        progress = progress(
                            phase = StorageCleanerScanPhase.CheckingDuplicates,
                            scannedFiles = walkResult.scannedFiles,
                            fraction = overallFraction,
                            startedAtNanos = startedAtNanos,
                            completedGroups = completedWithoutDuplicates
                        ),
                        result = currentResult(
                            accumulators,
                            requestedGroups,
                            walkResult.scannedFiles,
                            walkResult.isPartial
                        )
                    )
                )
            }
            val duplicateCandidates = duplicateFiles.orEmpty().mapNotNull { file ->
                val duplicateKey = duplicateGroupKeys[file.identityKey] ?: return@mapNotNull null
                if (!normalizedRules.includes(CleanerGroupType.Duplicates, file)) return@mapNotNull null
                file.toCandidate(
                    groups = setOf(CleanerGroupType.Duplicates),
                    risk = classifyRisk(file),
                    duplicateGroupKey = duplicateKey
                )
            }.groupBy { it.duplicateGroupKey }
                .values
                .filter { it.size > 1 }
                .sortedByDescending { group -> group.first().size }
            var remainingCandidates = limits.maxCandidatesPerGroup
            duplicateCandidates.forEach { duplicateGroup ->
                if (remainingCandidates < 2) return@forEach
                val candidatesToAdd = duplicateGroup.take(remainingCandidates)
                if (candidatesToAdd.size < 2) return@forEach
                candidatesToAdd.forEach(accumulators.getValue(CleanerGroupType.Duplicates)::add)
                remainingCandidates -= candidatesToAdd.size
            }
        }

        val result = currentResult(
            accumulators = accumulators,
            requestedGroups = requestedGroups,
            scannedFiles = walkResult.scannedFiles,
            isPartial = walkResult.isPartial
        )
        storeSnapshot(roots, limits, rules, requestedGroups, result)
        val cachedWorkResult = currentResult(
            accumulators = accumulators,
            requestedGroups = workGroups,
            scannedFiles = walkResult.scannedFiles,
            isPartial = walkResult.isPartial
        )
        cachedWorkResult.groups.forEach { group ->
            storeSnapshot(
                roots,
                limits,
                rules,
                setOf(group.type),
                cachedWorkResult.copy(groups = listOf(group))
            )
        }
        return result
    }

    private suspend fun storeSnapshot(
        roots: CleanerScanRoots,
        limits: StorageCleanerScanLimits,
        rules: StorageCleanerRules,
        groupTypes: Set<CleanerGroupType>,
        result: StorageCleanerResult
    ) {
        val store = snapshotStore ?: return
        when (roots) {
            is CleanerScanRoots.Local -> store.put(
                rootPaths = roots.paths,
                limits = limits,
                rules = rules,
                groupTypes = groupTypes,
                result = result
            )
            is CleanerScanRoots.Nodes -> store.putNodes(
                roots = roots.nodes,
                limits = limits,
                rules = rules,
                groupTypes = groupTypes,
                result = result
            )
        }
    }

    private fun requireExplicitProtectedRoots(roots: List<StorageNodeRef>) {
        require(roots.isNotEmpty()) { "Choose a folder to scan" }
        val protected = roots.filter(StorageNodeRef::isPrivileged)
        if (protected.isEmpty()) return
        require(protected.size == roots.size) {
            "Protected cleaner scopes cannot be mixed with local folders"
        }
        require(protected.map(StorageNodeRef::backendId).distinct().size == 1) {
            "Protected cleaner scopes must use one connected backend"
        }
        require(protected.all { !it.backendIdentity.isNullOrBlank() }) {
            "Protected cleaner scope identity is missing"
        }
        requireNotNull(fileSystemDataSource) {
            "Backend-aware cleaner support is unavailable"
        }
    }

    private fun StorageCleanerResult.completeUpdate(
        requestedGroups: Set<CleanerGroupType>
    ) = StorageCleanerScanUpdate(
        progress = StorageCleanerScanProgress(
            phase = StorageCleanerScanPhase.Complete,
            scannedFiles = scannedFiles,
            progressFraction = 1f,
            estimatedRemainingMillis = 0L,
            completedGroups = requestedGroups
        ),
        result = this
    )

    private fun matchingGroups(
        file: FileSnapshot,
        requestedGroups: Set<CleanerGroupType>,
        rules: StorageCleanerRules,
        now: Long,
        largeFileThreshold: Long,
        oldDownloadAgeMs: Long
    ): Set<CleanerGroupType> = buildSet {
        if (file.isDirectory) {
            if (CleanerGroupType.EmptyFolders in requestedGroups) add(CleanerGroupType.EmptyFolders)
        } else if (isMarkerFile(file)) {
            if (CleanerGroupType.MarkerFiles in requestedGroups) add(CleanerGroupType.MarkerFiles)
        } else {
            if (CleanerGroupType.LargeFiles in requestedGroups && file.size >= largeFileThreshold) {
                add(CleanerGroupType.LargeFiles)
            }
            if (CleanerGroupType.OldDownloads in requestedGroups &&
                file.isInDownloads && now - file.lastModified >= oldDownloadAgeMs
            ) {
                add(CleanerGroupType.OldDownloads)
            }
            if (CleanerGroupType.Apks in requestedGroups && file.extension == "apk") add(CleanerGroupType.Apks)
            if (CleanerGroupType.Videos in requestedGroups && file.extension in videoExtensions) add(CleanerGroupType.Videos)
            if (CleanerGroupType.Junk in requestedGroups && isJunk(file)) add(CleanerGroupType.Junk)
        }
    }.filterTo(linkedSetOf()) { rules.includes(it, file) }

    private fun currentResult(
        accumulators: Map<CleanerGroupType, CandidateAccumulator>,
        requestedGroups: Set<CleanerGroupType>,
        scannedFiles: Int,
        isPartial: Boolean
    ): StorageCleanerResult = StorageCleanerResult(
        groups = CleanerGroupType.entries
            .filter { it in requestedGroups }
            .map { type -> CleanerGroup(type, accumulators[type]?.sorted().orEmpty()) },
        scannedFiles = scannedFiles,
        isPartial = isPartial
    )

    private fun progress(
        phase: StorageCleanerScanPhase,
        scannedFiles: Int,
        fraction: Float,
        startedAtNanos: Long,
        completedGroups: Set<CleanerGroupType> = emptySet()
    ): StorageCleanerScanProgress {
        val elapsedMillis = (System.nanoTime() - startedAtNanos) / 1_000_000L
        val etaMillis = if (elapsedMillis >= MIN_ETA_ELAPSED_MILLIS && fraction >= MIN_ETA_PROGRESS) {
            (elapsedMillis * (1f - fraction) / fraction).toLong().coerceAtLeast(0L)
        } else {
            null
        }
        return StorageCleanerScanProgress(
            phase = phase,
            scannedFiles = scannedFiles,
            progressFraction = fraction.coerceIn(0f, 1f),
            estimatedRemainingMillis = etaMillis,
            completedGroups = completedGroups
        )
    }

    private suspend fun findDuplicateGroupKeys(
        files: List<FileSnapshot>,
        onProgress: suspend (Float) -> Unit
    ): Map<String, String> {
        val duplicates = linkedMapOf<String, String>()
        val sameSizeGroups = HashMap<Long, MutableList<FileSnapshot>>()
        files.forEach { file ->
            if (file.size > 0L) sameSizeGroups.getOrPut(file.size) { ArrayList() }.add(file)
        }
        val possibleDuplicates = sameSizeGroups.values.filter { it.size > 1 }
        val sampleTotal = possibleDuplicates.sumOf { it.size }.coerceAtLeast(1)
        var sampled = 0
        val sampleMatches = ArrayList<List<FileSnapshot>>()

        possibleDuplicates.forEach { sameSizeFiles ->
            currentCoroutineContext().ensureActive()
            val hashes = HashMap<String, MutableList<FileSnapshot>>()
            sameSizeFiles.forEach { file ->
                sampleHash(file)?.let { hash ->
                    hashes.getOrPut(hash) { ArrayList() }.add(file)
                }
                sampled++
                if (sampled % HASH_PROGRESS_INTERVAL == 0) {
                    onProgress(SAMPLE_HASH_WEIGHT * sampled / sampleTotal.toFloat())
                }
            }
            hashes.values.filterTo(sampleMatches) { it.size > 1 }
        }

        val fullTotalBytes = sampleMatches.sumOf { group -> group.sumOf { it.size } }.coerceAtLeast(1L)
        var fullyHashedBytes = 0L
        sampleMatches.forEach { sampledFiles ->
            currentCoroutineContext().ensureActive()
            val hashes = HashMap<String, MutableList<FileSnapshot>>()
            sampledFiles.forEach { file ->
                fullHash(file) { hashedBytes ->
                    fullyHashedBytes += hashedBytes
                    onProgress(
                        SAMPLE_HASH_WEIGHT +
                            (1f - SAMPLE_HASH_WEIGHT) * fullyHashedBytes / fullTotalBytes.toFloat()
                    )
                }?.let { hash ->
                    hashes.getOrPut(hash) { ArrayList() }.add(file)
                }
            }
            hashes.forEach { (hash, matchingFiles) ->
                if (matchingFiles.size > 1) {
                    val groupKey = "${matchingFiles.first().size}:$hash"
                    matchingFiles.forEach { duplicates[it.identityKey] = groupKey }
                }
            }
        }
        onProgress(1f)
        return duplicates
    }

    private fun StorageCleanerRules.includes(type: CleanerGroupType, file: FileSnapshot): Boolean {
        val rule = section(type)
        if (!rule.enabled) return false
        if (rule.ignoredNamePatterns.isEmpty() && rule.ignoredPathPatterns.isEmpty()) return true
        val nameAllowed = rule.ignoredNamePatterns.isEmpty() || run {
            val lowerName = file.name.lowercase(Locale.ROOT)
            rule.ignoredNamePatterns.none { patternMatches(it, lowerName) }
        }
        val pathAllowed = rule.ignoredPathPatterns.isEmpty() || run {
            val lowerPath = file.absolutePath.lowercase(Locale.ROOT)
            rule.ignoredPathPatterns.none { patternMatches(it, lowerPath) }
        }
        return nameAllowed && pathAllowed
    }

    private fun patternMatches(pattern: String, lowerValue: String): Boolean {
        val lowerPattern = pattern.lowercase(Locale.ROOT)
        if ('*' !in lowerPattern && '?' !in lowerPattern) return lowerValue.contains(lowerPattern)
        val regex = buildString {
            append("^")
            lowerPattern.forEach { char ->
                when (char) {
                    '*' -> append(".*")
                    '?' -> append(".")
                    else -> append(Regex.escape(char.toString()))
                }
            }
            append("$")
        }.toRegex()
        return regex.matches(lowerValue)
    }

    private suspend fun sampleHash(file: FileSnapshot): String? = runCatchingPreservingCancellation {
        val digest = MessageDigest.getInstance("SHA-256")
        if (file.nodeRef == null) {
            val local = File(file.absolutePath)
            if (file.size <= SAMPLE_WINDOW_BYTES * 3L) {
                local.inputStream().use { input -> input.copyTo(DigestOutputStreamAdapter(digest)) }
            } else {
                RandomAccessFile(local, "r").use { input ->
                    val buffer = ByteArray(SAMPLE_WINDOW_BYTES)
                    updateDigestAt(input, digest, 0L, buffer)
                    updateDigestAt(
                        input,
                        digest,
                        (file.size / 2L - SAMPLE_WINDOW_BYTES / 2L).coerceAtLeast(0L),
                        buffer
                    )
                    updateDigestAt(
                        input,
                        digest,
                        (file.size - SAMPLE_WINDOW_BYTES).coerceAtLeast(0L),
                        buffer
                    )
                }
            }
        } else {
            openNodeInput(file.nodeRef).use { input ->
                updateDigestFromStream(input.stream, digest, file.size)
            }
        }
        digest.digest().toHex()
    }.getOrNull()

    private fun updateDigestAt(
        input: RandomAccessFile,
        digest: MessageDigest,
        offset: Long,
        buffer: ByteArray
    ) {
        input.seek(offset)
        val read = input.read(buffer)
        if (read > 0) digest.update(buffer, 0, read)
    }

    private fun updateDigestFromStream(
        input: InputStream,
        digest: MessageDigest,
        size: Long
    ) {
        if (size <= SAMPLE_WINDOW_BYTES * 3L) {
            input.copyTo(DigestOutputStreamAdapter(digest))
            return
        }
        val buffer = ByteArray(SAMPLE_WINDOW_BYTES)
        var position = 0L
        val offsets = listOf(
            0L,
            (size / 2L - SAMPLE_WINDOW_BYTES / 2L).coerceAtLeast(0L),
            (size - SAMPLE_WINDOW_BYTES).coerceAtLeast(0L)
        ).distinct().sorted()
        offsets.forEach { offset ->
            position += input.skipFully((offset - position).coerceAtLeast(0L))
            if (position < offset) return@forEach
            val read = input.readAtMost(buffer)
            if (read > 0) {
                digest.update(buffer, 0, read)
                position += read
            }
        }
    }

    private fun InputStream.skipFully(byteCount: Long): Long {
        var remaining = byteCount
        while (remaining > 0L) {
            val skipped = skip(remaining)
            if (skipped > 0L) {
                remaining -= skipped
            } else if (read() >= 0) {
                remaining -= 1L
            } else {
                break
            }
        }
        return byteCount - remaining
    }

    private fun InputStream.readAtMost(buffer: ByteArray): Int {
        var total = 0
        while (total < buffer.size) {
            val read = read(buffer, total, buffer.size - total)
            if (read < 0) break
            if (read == 0) continue
            total += read
        }
        return total
    }

    private suspend fun fullHash(file: FileSnapshot, onBytesHashed: suspend (Long) -> Unit): String? = try {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(FULL_HASH_BUFFER_BYTES)
        val input = if (file.nodeRef == null) {
            val stream = File(file.absolutePath).inputStream()
            dev.qtremors.arcile.core.storage.data.source.StorageNodeInput(stream)
        } else {
            openNodeInput(file.nodeRef)
        }
        input.use { opened ->
            var bytesSinceProgress = 0L
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = opened.stream.read(buffer)
                if (read < 0) break
                if (read == 0) continue
                digest.update(buffer, 0, read)
                bytesSinceProgress += read
                if (bytesSinceProgress >= HASH_BYTE_PROGRESS_INTERVAL) {
                    onBytesHashed(bytesSinceProgress)
                    bytesSinceProgress = 0L
                }
            }
            if (bytesSinceProgress > 0L) onBytesHashed(bytesSinceProgress)
        }
        digest.digest().toHex()
    } catch (error: Throwable) {
        error.rethrowIfCancellation()
        null
    }

    private suspend fun openNodeInput(node: StorageNodeRef) =
        requireNotNull(fileSystemDataSource) {
            "Backend-aware cleaner content access is unavailable"
        }.get().openNodeInput(node).getOrThrow()

    private class DigestOutputStreamAdapter(private val digest: MessageDigest) : java.io.OutputStream() {
        override fun write(b: Int) = digest.update(b.toByte())
        override fun write(b: ByteArray, off: Int, len: Int) = digest.update(b, off, len)
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private suspend fun walk(
        roots: CleanerScanRoots,
        limits: StorageCleanerScanLimits,
        includeDownloadClassification: Boolean,
        onSnapshot: suspend (FileSnapshot, scannedFiles: Int, visitedEntries: Int, pendingEntries: Int) -> Unit
    ): WalkResult = when (roots) {
        is CleanerScanRoots.Local -> walkLocal(
            roots.paths,
            limits,
            includeDownloadClassification,
            onSnapshot
        )
        is CleanerScanRoots.Nodes -> walkNodes(
            roots.nodes,
            limits,
            includeDownloadClassification,
            onSnapshot
        )
    }

    private suspend fun walkLocal(
        rootPaths: List<String>,
        limits: StorageCleanerScanLimits,
        includeDownloadClassification: Boolean,
        onSnapshot: suspend (FileSnapshot, scannedFiles: Int, visitedEntries: Int, pendingEntries: Int) -> Unit
    ): WalkResult {
        val pending = ArrayDeque<Pair<File, Int>>()
        rootPaths.map { File(it) }
            .distinctBy { it.absolutePath }
            .filter { it.exists() && it.isDirectory }
            .forEach { pending.add(it to 0) }
        var partial = false
        var scannedFiles = 0
        var visitedEntries = 0

        while (pending.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            if (scannedFiles >= limits.maxFiles) return WalkResult(scannedFiles, isPartial = true)

            val (current, depth) = pending.removeFirst()
            visitedEntries++
            if (shouldSkip(current)) continue

            if (current.isFile) {
                scannedFiles++
                onSnapshot(
                    current.toSnapshot(includeDownloadClassification = includeDownloadClassification),
                    scannedFiles,
                    visitedEntries,
                    pending.size
                )
                continue
            }
            if (!current.isDirectory) continue
            if (depth >= limits.maxDepth) {
                partial = true
                continue
            }

            val children = try {
                current.listFiles()
            } catch (error: Exception) {
                error.rethrowIfCancellation()
                null
            }
            if (children == null) {
                // Scoped-storage and filesystem permissions can make otherwise valid folders
                // unreadable. Skipping those expected boundaries does not make accessible
                // cleaner results incomplete.
                continue
            }
            if (children.isEmpty() && depth > 0) {
                scannedFiles++
                onSnapshot(
                    current.toSnapshot(
                        isDirectory = true,
                        includeDownloadClassification = includeDownloadClassification
                    ),
                    scannedFiles,
                    visitedEntries,
                    pending.size
                )
                continue
            }
            children.forEach { child -> pending.add(child to depth + 1) }
        }
        return WalkResult(scannedFiles, partial)
    }

    private suspend fun walkNodes(
        roots: List<StorageNodeRef>,
        limits: StorageCleanerScanLimits,
        includeDownloadClassification: Boolean,
        onSnapshot: suspend (FileSnapshot, scannedFiles: Int, visitedEntries: Int, pendingEntries: Int) -> Unit
    ): WalkResult {
        val source = requireNotNull(fileSystemDataSource) {
            "Backend-aware cleaner traversal is unavailable"
        }.get()
        val pending = ArrayDeque<Pair<FileModel, Int>>()
        roots.distinctBy { it.canonicalIdentity.value }.forEach { root ->
            require(root.capabilities.canRead) { "${root.displayPath.absolutePath} is not readable" }
            val model = source.inspectNode(root).getOrThrow()
            require(model.isDirectory) { "Cleaner scope must be a folder" }
            pending.add(model to 0)
        }
        val visitedDirectories = hashSetOf<String>()
        var partial = false
        var scannedFiles = 0
        var visitedEntries = 0

        while (pending.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            if (scannedFiles >= limits.maxFiles) return WalkResult(scannedFiles, isPartial = true)

            val (current, depth) = pending.removeFirst()
            visitedEntries++
            if (shouldSkip(current.name)) continue

            if (!current.isDirectory) {
                if (!current.nodeRef.capabilities.canRead || !current.nodeRef.capabilities.canArchive) continue
                scannedFiles++
                onSnapshot(
                    current.toSnapshot(includeDownloadClassification),
                    scannedFiles,
                    visitedEntries,
                    pending.size
                )
                continue
            }
            if (!visitedDirectories.add(current.nodeRef.canonicalIdentity.value)) {
                partial = true
                continue
            }
            if (depth >= limits.maxDepth) {
                partial = true
                continue
            }

            val children = source.listNodeFiles(current.nodeRef).getOrElse { error ->
                error.rethrowIfCancellation()
                if (depth == 0) throw error
                partial = true
                emptyList()
            }
            if (children.isEmpty() && depth > 0 && current.nodeRef.capabilities.canDelete) {
                scannedFiles++
                onSnapshot(
                    current.toSnapshot(includeDownloadClassification, isDirectory = true),
                    scannedFiles,
                    visitedEntries,
                    pending.size
                )
                continue
            }
            children.forEach { child -> pending.add(child to depth + 1) }
        }
        return WalkResult(scannedFiles, partial)
    }

    private fun File.toSnapshot(
        isDirectory: Boolean = false,
        includeDownloadClassification: Boolean
    ): FileSnapshot {
        val normalizedPath = absolutePath
        return FileSnapshot(
            name = name,
            absolutePath = normalizedPath,
            size = if (isDirectory) 0L else length().coerceAtLeast(0L),
            lastModified = lastModified(),
            extension = extension.lowercase(Locale.ROOT),
            isInDownloads = includeDownloadClassification && normalizedPath.pathSegments().any {
                it.equals("download", ignoreCase = true) || it.equals("downloads", ignoreCase = true)
            },
            isDirectory = isDirectory,
            nodeRef = null
        )
    }

    private fun FileModel.toSnapshot(
        includeDownloadClassification: Boolean,
        isDirectory: Boolean = this.isDirectory
    ): FileSnapshot = FileSnapshot(
        name = name,
        absolutePath = absolutePath,
        size = if (isDirectory) 0L else size.coerceAtLeast(0L),
        lastModified = lastModified,
        extension = extension.lowercase(Locale.ROOT),
        isInDownloads = includeDownloadClassification && absolutePath.pathSegments().any {
            it.equals("download", ignoreCase = true) || it.equals("downloads", ignoreCase = true)
        },
        isDirectory = isDirectory,
        nodeRef = nodeRef
    )

    private fun shouldSkip(file: File): Boolean {
        return shouldSkip(file.name)
    }

    private fun shouldSkip(name: String): Boolean =
        name.lowercase(Locale.ROOT) in skippedDirectoryNames

    private fun String.pathSegments(): List<String> =
        split('/', '\\').filter(String::isNotBlank)

    private fun isJunk(file: FileSnapshot): Boolean {
        val lowerName = file.name.lowercase(Locale.ROOT)
        return file.extension in junkExtensions || lowerName.endsWith(".tmp") || lowerName.endsWith(".temp")
    }

    private fun isMarkerFile(file: FileSnapshot): Boolean =
        file.name.lowercase(Locale.ROOT) in markerFileNames

    private fun classifyRisk(file: FileSnapshot): RiskClassification {
        val reasons = linkedSetOf<CleanerRiskReason>()
        val lowerSegments = file.absolutePath.pathSegments().map { it.lowercase(Locale.ROOT) }
        val lowerPath = file.absolutePath.lowercase(Locale.ROOT)
        val parentSegments = lowerSegments.dropLast(1)

        if (".arcile" in lowerSegments) reasons += CleanerRiskReason.ArcileInternal
        if (isAndroidSensitivePath(lowerSegments)) reasons += CleanerRiskReason.SystemOwnedPath
        if (parentSegments.any(::isPackageLikeSegment)) reasons += CleanerRiskReason.AppLikeFolder
        if (parentSegments.any { it in tempOrCacheFolderNames }) reasons += CleanerRiskReason.TemporaryOrCache
        if (parentSegments.any { it in userFolderNames }) reasons += CleanerRiskReason.UserFolder
        if (parentSegments.any { it in mediaFolderNames }) reasons += CleanerRiskReason.MediaFolder

        when (file.extension) {
            "log" -> reasons += CleanerRiskReason.LogFile
            "bak", "old" -> reasons += CleanerRiskReason.BackupFile
            "dmp" -> reasons += CleanerRiskReason.DumpFile
            "tmp", "temp" -> reasons += CleanerRiskReason.TemporaryOrCache
        }
        if (file.name.lowercase(Locale.ROOT).endsWith(".tmp") || lowerPath.endsWith(".temp")) {
            reasons += CleanerRiskReason.TemporaryOrCache
        }

        val level = when {
            reasons.any {
                it == CleanerRiskReason.ArcileInternal || it == CleanerRiskReason.SystemOwnedPath ||
                    it == CleanerRiskReason.AppLikeFolder
            } -> CleanerRiskLevel.High
            reasons.any {
                it == CleanerRiskReason.UserFolder || it == CleanerRiskReason.MediaFolder ||
                    it == CleanerRiskReason.LogFile || it == CleanerRiskReason.BackupFile ||
                    it == CleanerRiskReason.DumpFile
            } -> CleanerRiskLevel.Review
            else -> CleanerRiskLevel.Low
        }
        return RiskClassification(level, reasons)
    }

    private fun isAndroidSensitivePath(segments: List<String>): Boolean {
        val androidIndex = segments.indexOf("android")
        if (androidIndex < 0 || androidIndex == segments.lastIndex) return false
        return segments[androidIndex + 1] == "data" || segments[androidIndex + 1] == "obb"
    }

    private fun isPackageLikeSegment(segment: String): Boolean = packageSegmentRegex.matches(segment)

    private fun FileSnapshot.toCandidate(
        groups: Set<CleanerGroupType>,
        risk: RiskClassification,
        duplicateGroupKey: String? = null
    ) = CleanerCandidate(
        name = name,
        absolutePath = absolutePath,
        size = size,
        lastModified = lastModified,
        groupTypes = groups,
        riskLevel = risk.level,
        riskReasons = risk.reasons,
        isDirectory = isDirectory,
        duplicateGroupKey = duplicateGroupKey,
        nodeRef = nodeRef
    )

    private fun StorageCleanerResult.withoutStaleCandidates(): StorageCleanerResult = copy(
        groups = groups.map { group ->
            val existingCandidates = group.candidates.filter { candidate ->
                val file = File(candidate.absolutePath)
                file.exists() && file.isDirectory == candidate.isDirectory &&
                    (candidate.isDirectory || file.length().coerceAtLeast(0L) == candidate.size)
            }
            group.copy(
                candidates = if (group.type == CleanerGroupType.Duplicates) {
                    existingCandidates.groupBy { it.duplicateGroupKey ?: it.absolutePath }
                        .values
                        .filter { it.size > 1 }
                        .flatten()
                } else {
                    existingCandidates
                }
            )
        }
    )

    private data class FileSnapshot(
        val name: String,
        val absolutePath: String,
        val size: Long,
        val lastModified: Long,
        val extension: String,
        val isInDownloads: Boolean,
        val isDirectory: Boolean,
        val nodeRef: StorageNodeRef?
    ) {
        val identityKey: String
            get() = nodeRef?.canonicalIdentity?.value ?: absolutePath
    }

    private sealed interface CleanerScanRoots {
        data class Local(val paths: List<String>) : CleanerScanRoots
        data class Nodes(val nodes: List<StorageNodeRef>) : CleanerScanRoots
    }

    private data class RiskClassification(
        val level: CleanerRiskLevel,
        val reasons: Set<CleanerRiskReason>
    )

    private data class WalkResult(val scannedFiles: Int, val isPartial: Boolean)

    private class CandidateAccumulator(private val limit: Int) {
        private val candidates = PriorityQueue<CleanerCandidate>(
            compareBy<CleanerCandidate> { it.size }
                .thenByDescending { it.name.lowercase(Locale.ROOT) }
                .thenByDescending { it.absolutePath }
        )

        fun add(candidate: CleanerCandidate) {
            if (limit <= 0) return
            if (candidates.size < limit) {
                candidates.add(candidate)
                return
            }
            val lowestRanked = candidates.peek() ?: return
            val isBetter = candidate.size > lowestRanked.size ||
                (candidate.size == lowestRanked.size &&
                    candidate.name.lowercase(Locale.ROOT) < lowestRanked.name.lowercase(Locale.ROOT))
            if (isBetter) {
                candidates.poll()
                candidates.add(candidate)
            }
        }

        fun sorted(): List<CleanerCandidate> = candidates.sortedWith(
            compareByDescending<CleanerCandidate> { it.size }
                .thenBy { it.name.lowercase(Locale.ROOT) }
                .thenBy { it.absolutePath }
        )
    }

    private companion object {
        val videoExtensions = FileCategories.Videos.extensions
        val junkExtensions = setOf("tmp", "temp", "log", "bak", "old", "dmp")
        val markerFileNames = setOf(".nomedia", "desktop.ini", "thumbs.db", ".ds_store")
        val skippedDirectoryNames = setOf(".arcile", ".trash", ".thumbnails")
        val tempOrCacheFolderNames = setOf("temp", "tmp", "cache", "caches")
        val userFolderNames = setOf("download", "downloads", "documents", "document")
        val mediaFolderNames = setOf("dcim", "pictures", "picture", "movies", "movie", "videos", "video")
        val packageSegmentRegex = Regex("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*){1,}")
        const val SAMPLE_WINDOW_BYTES = 4096
        const val PROGRESS_FILE_INTERVAL = 256
        const val HASH_PROGRESS_INTERVAL = 16
        const val FULL_HASH_BUFFER_BYTES = 128 * 1024
        const val HASH_BYTE_PROGRESS_INTERVAL = 8L * 1024L * 1024L
        const val PROGRESS_TIME_INTERVAL_NANOS = 200_000_000L
        const val MIN_ETA_ELAPSED_MILLIS = 750L
        const val MIN_ETA_PROGRESS = 0.02f
        const val INVENTORY_DUPLICATE_WEIGHT = 0.72f
        const val INVENTORY_ONLY_WEIGHT = 0.98f
        const val SAMPLE_HASH_WEIGHT = 0.4f
    }
}
