package dev.qtremors.arcile.core.storage.domain

fun interface AppVersionCodeProvider {
    fun currentVersionCode(): Int
}
