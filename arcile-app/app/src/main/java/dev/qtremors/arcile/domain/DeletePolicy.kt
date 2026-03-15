package dev.qtremors.arcile.domain

sealed class DeletePolicyResult {
    data object MixedSelection : DeletePolicyResult()
    data object PermanentDelete : DeletePolicyResult()
    data object Trash : DeletePolicyResult()
}

suspend fun evaluateDeletePolicy(paths: List<String>, repository: FileRepository): DeletePolicyResult {
    var supportsTrashCount = 0
    var permanentDeleteCount = 0

    for (path in paths) {
        val volume = repository.getVolumeForPath(path).getOrNull()
        if (volume != null && volume.kind.supportsTrash) {
            supportsTrashCount++
        } else {
            permanentDeleteCount++
        }
    }

    return when {
        supportsTrashCount > 0 && permanentDeleteCount > 0 -> DeletePolicyResult.MixedSelection
        permanentDeleteCount > 0 -> DeletePolicyResult.PermanentDelete
        else -> DeletePolicyResult.Trash
    }
}