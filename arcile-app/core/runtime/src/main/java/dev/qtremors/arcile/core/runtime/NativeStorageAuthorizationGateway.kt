package dev.qtremors.arcile.core.runtime

import android.content.IntentSender
import dev.qtremors.arcile.core.storage.domain.StorageAuthorizationRequirement
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NativeStorageAuthorizationGateway @Inject constructor() {
    private val pendingSenders = ConcurrentHashMap<String, IntentSender>()

    fun register(
        requirement: StorageAuthorizationRequirement,
        intentSender: IntentSender
    ) {
        pendingSenders[requirement.requestId] = intentSender
    }

    fun resolve(requirement: StorageAuthorizationRequirement): IntentSender? =
        pendingSenders[requirement.requestId]

    fun consume(requirement: StorageAuthorizationRequirement): IntentSender? =
        pendingSenders.remove(requirement.requestId)

    fun complete(requirement: StorageAuthorizationRequirement) {
        complete(requirement.requestId)
    }

    fun complete(requestId: String) {
        pendingSenders.remove(requestId)
    }
}
