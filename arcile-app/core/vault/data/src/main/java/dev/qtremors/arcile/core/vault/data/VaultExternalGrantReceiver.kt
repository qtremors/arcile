package dev.qtremors.arcile.core.vault.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.qtremors.arcile.core.vault.domain.VaultExternalAccessManager

class VaultExternalGrantReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REVOKE_ALL) return
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            ReceiverEntryPoint::class.java
        ).manager().revokeAll()
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ReceiverEntryPoint {
        fun manager(): VaultExternalAccessManager
    }

    companion object {
        const val ACTION_REVOKE_ALL = "dev.qtremors.arcile.onlyfiles.REVOKE_EXTERNAL_ACCESS"
    }
}
